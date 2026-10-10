import { PDFDocument, rgb } from 'pdf-lib';
import * as fontkit from 'fontkit';
import createModule from 'harfbuzzjs/hb.js';
import hbjs from 'harfbuzzjs/hbjs.js';
import { WordFonts } from './word-font.js';

let harfbuzz;
async function shaper(){
  if(!harfbuzz)harfbuzz=fetch('hb.wasm').then(r=>{if(!r.ok)throw Error('Offline text shaper is missing');return r.arrayBuffer();}).then(wasmBinary=>createModule({wasmBinary})).then(hbjs);
  return harfbuzz;
}
const nextFrame=()=>new Promise(resolve=>setTimeout(resolve,0));
const color=text=>{const m=text.match(/[\d.]+/g)||['0','0','0'];return m.slice(0,3).map(Number).map(v=>v/255);};

/** Draw measured graphemes as real, visible Type3 text with exact logical Unicode.
 * Layout and line breaks come from the preview DOM; no second pagination engine.
 */
export async function createVectorPdf(settings,{title='Document',progress=()=>{}}={}){
  const started=performance.now(),hb=await shaper(),pdf=await PDFDocument.create();
  pdf.setTitle(title);pdf.setProducer('A4 Lekhak · HarfBuzz / vector Unicode PDF');
  const fonts=new Map(),embeddedImages=new Map(),runs=new WordFonts(pdf);
  const pagesElement=document.getElementById('pages'),oldZoom=pagesElement.style.zoom;
  pagesElement.style.zoom='1';
  const getFont=async (style,text='')=>{
    const needsFallback=/[\u0900-\u097f]/u.test(text);
    const family=style.fontFamily.includes('Noto Serif')?(needsFallback?'NotoSansDevanagari':'NotoSerif'):style.fontFamily.toLowerCase().includes('monospace')?(needsFallback?'NotoSansDevanagari':'NotoSansMono'):'NotoSans';
    const name=family+(Number(style.fontWeight)>=600?'-Bold':'-Regular');
    if(!fonts.has(name)){
      const response=await fetch(`fonts/${name}.ttf`);if(!response.ok)throw Error(`Offline font missing: ${name}`);
      const bytes=new Uint8Array(await response.arrayBuffer()),font=fontkit.create(bytes),blob=hb.createBlob(bytes),face=hb.createFace(blob,0),hbfont=hb.createFont(face);
      font.lekhakName=name;
      font.shape=text=>{const buffer=hb.createBuffer();try{buffer.addText(text);buffer.guessSegmentProperties();hb.shape(hbfont,buffer);const glyphs=buffer.json();return{glyphs:glyphs.map(g=>font.getGlyph(g.g)),positions:glyphs.map(g=>({xAdvance:g.ax,yAdvance:g.ay,xOffset:g.dx,yOffset:g.dy}))};}finally{buffer.destroy();}};
      fonts.set(name,{font,dispose:()=>{hbfont.destroy();face.destroy();blob.destroy();}});
    }
    return fonts.get(name).font;
  };
  try{
    const pageNodes=[...pagesElement.querySelectorAll('.page')];
    for(let pageIndex=0;pageIndex<pageNodes.length;pageIndex++){
      const node=pageNodes[pageIndex],rect=node.getBoundingClientRect();
      const height=settings.landscape?210:297,width=settings.landscape?297:210;
      const page=pdf.addPage([width*72/25.4,height*72/25.4]);
      const body=node.querySelector('.page-content');
      if(body.scrollWidth>body.clientWidth+2||body.scrollHeight>body.clientHeight+2)throw Error('Content overflows a printable page. Export stopped.');
      // Borders and images are vector / original image data, never page screenshots.
      for(const cell of body.querySelectorAll('td,th')){
        const bounds=cell.getBoundingClientRect(),style=getComputedStyle(cell);
        if(style.backgroundColor!=='rgba(0, 0, 0, 0)')page.drawRectangle({x:(bounds.left-rect.left)*.75,y:page.getHeight()-(bounds.bottom-rect.top)*.75,width:bounds.width*.75,height:bounds.height*.75,color:rgb(...color(style.backgroundColor))});
        if(parseFloat(style.borderTopWidth)>0)page.drawRectangle({x:(bounds.left-rect.left)*.75,y:page.getHeight()-(bounds.bottom-rect.top)*.75,width:bounds.width*.75,height:bounds.height*.75,borderColor:rgb(...color(style.borderTopColor)),borderWidth:parseFloat(style.borderTopWidth)*.75});
      }
      for(const image of body.querySelectorAll('img')){
        if(!image.complete||!image.naturalWidth)throw Error('An image is missing. Reinsert it before exporting.');
        if(!embeddedImages.has(image.src)){const response=await fetch(image.src);if(!response.ok)throw Error('Image cannot be read');embeddedImages.set(image.src,await (new URL(image.src).pathname.endsWith('.jpg') ? pdf.embedJpg(await response.arrayBuffer()) : pdf.embedPng(await response.arrayBuffer())));}
        const r=image.getBoundingClientRect();page.drawImage(embeddedImages.get(image.src),{x:(r.left-rect.left)*.75,y:page.getHeight()-(r.bottom-rect.top)*.75,width:r.width*.75,height:r.height*.75});
      }
      const walker=document.createTreeWalker(body,NodeFilter.SHOW_TEXT);let textNode;const lines=[];
      while((textNode=walker.nextNode())){
        const style=getComputedStyle(textNode.parentElement),size=parseFloat(style.fontSize),text=textNode.textContent;
        const decorations=new Set();for(let el=textNode.parentElement;el&&el!==body;el=el.parentElement)for(const d of getComputedStyle(el).textDecorationLine.split(' '))decorations.add(d);
        // Range token measurement reads layout only; it never mutates editor content.
        const tokens=/monospace|Noto Serif/i.test(style.fontFamily)?/[\u0900-\u097f\u200c\u200d]+|[^\u0900-\u097f\u200c\u200d\s]+|\s+/gu:/\S+|\s+/gu;
        for(const match of text.matchAll(tokens)){
          const font=await getFont(style,match[0]);
          const start=match.index,end=start+match[0].length,range=document.createRange();range.setStart(textNode,start);range.setEnd(textNode,end);
          let parts=[{text:match[0],range}];
          {parts=[];for(const segment of new Intl.Segmenter('hi',{granularity:'grapheme'}).segment(match[0])){const r=document.createRange();r.setStart(textNode,start+segment.index);r.setEnd(textNode,start+segment.index+segment.segment.length);parts.push({text:segment.segment,range:r});}}
          for(const part of parts){const r=part.range.getBoundingClientRect();if(!r.height)continue;
            const key=[font.lekhakName,size,style.fontStyle,style.color,[...decorations].join(',')].join('|');
            let line=lines.at(-1);
            const parentBlock=textNode.parentElement.closest('p,h1,h2,h3,li,td,th');
            line={key,font,size,style,decorations,top:r.top,bottom:r.bottom,x:r.left,right:r.right,text:'',segments:[],block:parentBlock};lines.push(line);
            line.text+=part.text;line.segments.push({text:part.text,x:r.left-line.x,width:r.width});line.right=Math.max(line.right,r.right);
          }
        }
      }
      const marked=new Set(),lastInCell=new Map();
      for(const line of lines){const cell=line.block?.closest('td,th');if(cell&&line.text.trim())lastInCell.set(cell,line);}
      for(const line of lines){
        if(!line.text.length||line.right-line.x<=0)continue;
        const baseline=line.top+line.font.ascent/line.font.unitsPerEm*line.size;
        const x=(line.x-rect.left)*.75,y=page.getHeight()-(baseline-rect.top)*.75;
        const li=line.block?.closest('li');
        if(li&&!marked.has(li)){
          marked.add(li);const parent=li.parentElement;
          if(getComputedStyle(parent).listStyleType!=='none'){
            const marker=parent.tagName==='OL'?String(Number(parent.getAttribute('start')||1)+[...parent.children].indexOf(li))+'.':'•';
            runs.draw(page,line.font,marker,{x:(li.getBoundingClientRect().left-rect.left)*.75-line.size*.9,y,size:line.size*.75});
          }
        }
        const cell=line.block?.closest('td,th');
        const logical=line.text+(cell&&cell.nextElementSibling&&lastInCell.get(cell)===line?' ':'');
        runs.draw(page,line.font,logical,{x,y,size:line.size*.75,color:color(line.style.color),width:(line.right-line.x)*.75,italic:line.style.fontStyle==='italic',layout:{segments:line.segments,size:line.size,width:line.right-line.x}});
        for(const decoration of ['underline','line-through'])if(line.decorations.has(decoration)){const offset=decoration==='underline'?-1.5:line.size*.23;page.drawLine({start:{x,y:y+offset},end:{x:x+(line.right-line.x)*.75,y:y+offset},thickness:Math.max(.5,line.size*.035),color:rgb(...color(line.style.color))});}
      }
      progress(pageIndex+1,pageNodes.length);await nextFrame();
    }
    runs.finish();const bytes=await pdf.save({useObjectStreams:true});return{bytes,pages:pageNodes.length,milliseconds:performance.now()-started};
  }finally{pagesElement.style.zoom=oldZoom;for(const f of fonts.values())f.dispose();}
}
