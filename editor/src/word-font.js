import { PDFName } from 'pdf-lib';

const number = n => Number(n.toFixed(5)).toString();
const unicodeHex = text => [...Array(text.length)].map((_,i)=>text.charCodeAt(i).toString(16).padStart(4,'0')).join('').toUpperCase();

/** Visible Type 3 glyphs represent shaped graphemes, with exact ToUnicode maps.
 * There is no invisible text layer and no rasterized text. Grapheme mappings keep
 * Indic consonants/matras together without triggering reader column heuristics.
 */
export class WordFonts {
  constructor(pdf) { this.pdf=pdf; this.groups=[]; this.cache=new Map(); this.pageOps=new Map(); }
  word(font,text,italic=false,layout=null) {
    const key=(font.lekhakName||font.postscriptName)+'|'+italic+'|'+text+'|'+(layout?JSON.stringify(layout):'');
    if(this.cache.has(key))return this.cache.get(key);
    let run=font.shape ? font.shape(text) : font.layout(text);const scale=1000/font.unitsPerEm;
    if(layout){const glyphs=[],positions=[];let cursor=0;for(const segment of layout.segments){const shaped=font.shape(segment.text);const shift=segment.x/layout.size*font.unitsPerEm-cursor;for(let i=0;i<shaped.glyphs.length;i++){glyphs.push(shaped.glyphs[i]);positions.push({...shaped.positions[i],xOffset:shaped.positions[i].xOffset+shift});cursor+=shaped.positions[i].xAdvance;}}run={glyphs,positions};}
    let x=0,y=0;const paths=[];let minX=0,minY=0,maxX=0,maxY=0;
    for(let i=0;i<run.glyphs.length;i++) {
      const glyph=run.glyphs[i],pos=run.positions[i];
      if(glyph.id===0 && text.trim())throw new Error('The selected font cannot render a character in this document.');
      let cx=0,cy=0;
      const point=(a,b)=>[(a+x+pos.xOffset+(italic?0.21256*(b+y+pos.yOffset):0))*scale,(b+y+pos.yOffset)*scale];
      const emit=(points,operator)=>{for(let j=0;j<points.length;j+=2){minX=Math.min(minX,points[j]);maxX=Math.max(maxX,points[j]);minY=Math.min(minY,points[j+1]);maxY=Math.max(maxY,points[j+1]);}paths.push(points.map(number).join(' ')+' '+operator);};
      for(const c of glyph.path.commands){const a=c.args;
        if(c.command==='moveTo'){emit(point(a[0],a[1]),'m');cx=a[0];cy=a[1];}
        else if(c.command==='lineTo'){emit(point(a[0],a[1]),'l');cx=a[0];cy=a[1];}
        else if(c.command==='quadraticCurveTo'){const c1=[cx+2/3*(a[0]-cx),cy+2/3*(a[1]-cy)],c2=[a[2]+2/3*(a[0]-a[2]),a[3]+2/3*(a[1]-a[3])];emit([...point(...c1),...point(...c2),...point(a[2],a[3])],'c');cx=a[2];cy=a[3];}
        else if(c.command==='bezierCurveTo'){emit([...point(a[0],a[1]),...point(a[2],a[3]),...point(a[4],a[5])],'c');cx=a[4];cy=a[5];}
        else if(c.command==='closePath')paths.push('h');
      }
      if(glyph.path.commands.length)paths.push('f');x+=pos.xAdvance;y+=pos.yAdvance;
    }
    const width=layout ? layout.width/layout.size*1000 : x*scale;
    if(text.length>32)throw Error('An unusually long combining sequence cannot be exported safely.');
    let group=this.groups.at(-1);if(!group || group.entries.length===128){group={name:'W'+this.groups.length,entries:[],ref:this.pdf.context.nextRef()};this.groups.push(group);}
    const code=group.entries.length+128;
    const bbox=[minX,Math.min(minY,font.descent*scale),Math.max(width,maxX),Math.max(maxY,font.ascent*scale)];
    const entry={code,text,width,bbox,stream:`${number(width)} 0 ${bbox.map(number).join(' ')} d1\n${paths.join('\n')}`};
    group.entries.push(entry);
    const result={group,code,width};this.cache.set(key,result);return result;
  }
  finish() {
    const context=this.pdf.context;
    for(const group of this.groups){
      const charProcs={};for(const e of group.entries)charProcs['g'+e.code]=context.register(context.flateStream(e.stream));
      const mappings=group.entries.map(e=>`<${e.code.toString(16).padStart(2,'0')}> <${unicodeHex(e.text)}>`);
      const chunks=[];for(let i=0;i<mappings.length;i+=100){const chunk=mappings.slice(i,i+100);chunks.push(`${chunk.length} beginbfchar\n${chunk.join('\n')}\nendbfchar`);}
      const cmap=`/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n/CMapName /Lekhak-Unicode def\n/CMapType 2 def\n1 begincodespacerange\n<00> <FF>\nendcodespacerange\n${chunks.join('\n')}\nendcmap\nCMapName currentdict /CMap defineresource pop\nend\nend`;
      const bbox=[Math.min(...group.entries.map(e=>e.bbox[0])),Math.min(...group.entries.map(e=>e.bbox[1])),Math.max(...group.entries.map(e=>e.bbox[2])),Math.max(...group.entries.map(e=>e.bbox[3]))];
      const descriptor=context.register(context.obj({Type:'FontDescriptor',FontName:group.name,Flags:4,FontBBox:bbox,ItalicAngle:0,Ascent:bbox[3],Descent:bbox[1],CapHeight:bbox[3],StemV:80,MissingWidth:Math.max(...group.entries.map(e=>e.width))}));
      context.assign(group.ref,context.obj({Type:'Font',Subtype:'Type3',Name:group.name,FontBBox:bbox,FontDescriptor:descriptor,FontMatrix:[0.001,0,0,0.001,0,0],CharProcs:charProcs,Encoding:{Type:'Encoding',Differences:[128,...group.entries.map(e=>PDFName.of('g'+e.code))]},FirstChar:128,LastChar:127+group.entries.length,Widths:group.entries.map(e=>e.width),Resources:{},ToUnicode:context.register(context.flateStream(cmap))}));
    }
    for(const [page,ops] of this.pageOps)page.node.addContentStream(context.register(context.flateStream('q\n'+ops.join('\n')+'\nQ')));
  }
  draw(page,font,text,{x,y,size,color=[0,0,0],width,italic=false,layout=null}) {
    const entry=this.word(font,text,italic,layout);const scale=width!=null && entry.width>0 ? width/(entry.width*size/1000) : 1;
    page.node.setFontDictionary(PDFName.of(entry.group.name),entry.group.ref);
    const stream=`BT\n${color.map(number).join(' ')} rg\n/${entry.group.name} ${number(size)} Tf\n${number(scale)} 0 0 1 ${number(x)} ${number(y)} Tm\n<${entry.code.toString(16).padStart(2,'0')}> Tj\nET`;
    if(!this.pageOps.has(page))this.pageOps.set(page,[]);this.pageOps.get(page).push(stream);
  }
}
