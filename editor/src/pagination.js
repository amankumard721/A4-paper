// Layout uses the browser's shaping and line measurement. No canvas rasterization.
const MM = 96 / 25.4;
// Cache exact measured page prefixes, not estimates of shaped paragraph heights.
// Any settings change invalidates the cache. IDs bound retained HTML and keys.
let cacheSettings='',nextId=0;
const blockIds=new Map(),fitCache=new Map();
function blockId(node){const html=node.outerHTML;if(!blockIds.has(html)){if(blockIds.size>=2500){blockIds.clear();fitCache.clear();}blockIds.set(html,++nextId);}return blockIds.get(html);}
export function applySettings(settings) {
  const root = document.documentElement.style;
  const family = `'${settings.fontFamily}','Noto Devanagari',sans-serif`;
  Object.entries({'--pw':`${settings.landscape?297:210}mm`,'--ph':`${settings.landscape?210:297}mm`,'--mt':`${settings.marginTop}mm`,'--mr':`${settings.marginRight}mm`,'--mb':`${settings.marginBottom}mm`,'--ml':`${settings.marginLeft}mm`,'--doc-font':family,'--doc-size':`${settings.fontSize}pt`,'--line':settings.lineHeight,'--space':`${settings.paragraphSpacing}pt`}).forEach(([k,v])=>root.setProperty(k,v));
  let style = document.getElementById('print-size');
  if (!style) { style=document.createElement('style'); style.id='print-size'; document.head.append(style); }
  style.textContent=`@page{size:${settings.landscape?'297mm 210mm':'210mm 297mm'};margin:0}`;
}

function textPoints(element) {
  const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT);
  const nodes=[];let total=0,node;
  while((node=walker.nextNode())) { nodes.push({node,start:total,end:total+node.length});total+=node.length; }
  return {nodes,total};
}
function slice(element,start,end) {
  const {nodes,total}=textPoints(element);const clone=element.cloneNode(false);
  if(!total){if(start===0)clone.append(...Array.from(element.childNodes).map(n=>n.cloneNode(true)));return clone;}
  const first=nodes.find(n=>n.end>start)||nodes.at(-1);const last=nodes.find(n=>n.end>=end)||nodes.at(-1);
  const range=document.createRange();range.setStart(first.node,Math.max(0,start-first.start));range.setEnd(last.node,Math.min(last.node.length,end-last.start));clone.append(range.cloneContents());
  return clone;
}
function boundaries(text) {
  // Word boundaries never split a Hindi conjunct. Grapheme fallback handles long unbroken tokens.
  if(typeof Intl.Segmenter !== 'function') throw new Error('Update Android System WebView to enable reliable Hindi pagination.');
  const words=[...new Intl.Segmenter('hi',{granularity:'word'}).segment(text)].map(x=>x.index+x.segment.length);
  const graphemes=[...new Intl.Segmenter('hi',{granularity:'grapheme'}).segment(text)].map(x=>x.index+x.segment.length);
  return {words,graphemes};
}
function flatten(element) {
  if(element.matches('ul,ol')) {
    const start=Number(element.getAttribute('start')||1);
    return Array.from(element.children).flatMap((li,index)=>{
      const wrapper=element.cloneNode(false);if(element.tagName==='OL')wrapper.setAttribute('start',String(start+index));
      wrapper.append(li.cloneNode(true));return [wrapper];
    });
  }
  return [element];
}

export async function paginate(html,settings) {
  applySettings(settings);
  await Promise.all(['Noto Sans','Noto Devanagari','Noto Serif','Monospace'].flatMap(family=>[400,700].map(weight=>document.fonts.load(`${weight} ${settings.fontSize}px "${family}"`))));
  await document.fonts.ready;
  const signature=JSON.stringify(settings);
  if(signature!==cacheSettings){blockIds.clear();fitCache.clear();cacheSettings=signature;}
  const width=((settings.landscape?297:210)-settings.marginLeft-settings.marginRight)*MM;
  // Small safety tolerance absorbs Chromium print conversion rounding, not a second layout.
  const height=((settings.landscape?210:297)-settings.marginTop-settings.marginBottom)*MM-1;
  const measure=document.getElementById('measure');measure.style.width=`${width}px`;
  const source=document.createElement('div');source.innerHTML=html;
  await Promise.all([...source.querySelectorAll('img')].map(image=>image.decode().catch(()=>{throw Error('An image is missing or unreadable. Reinsert it before preview or export.');})));
  // Clones may decode asynchronously; reserve the known intrinsic aspect ratio now.
  for(const image of source.querySelectorAll('img'))image.style.aspectRatio=`${image.naturalWidth} / ${image.naturalHeight}`;
  for(const table of source.querySelectorAll('table')){
    if(table.querySelector('table'))throw Error('Nested tables are not supported in A4 preview.');
    const cols=[...table.querySelectorAll(':scope > colgroup > col')];
    const widths=cols.map(c=>parseFloat(c.style.width)||36),sum=widths.reduce((a,b)=>a+b,0);
    table.style.width=Math.min(parseFloat(table.style.width)||width,width)+'px';table.style.minWidth='0';
    cols.forEach((c,i)=>{c.style.width=(widths[i]/sum*100)+'%';c.style.minWidth='0';});
  }
  const output=document.getElementById('pages');output.innerHTML='';
  let body;let count=0;let contentOnPage=false;let prefix=[];
  const newPage=()=>{const page=document.createElement('section');page.className='page';page.setAttribute('aria-label',`Page ${++count}`);body=document.createElement('div');body.className='page-content';page.append(body);output.append(page);contentOnPage=false;prefix=[];measure.replaceChildren();};
  newPage();
  const fits=node=>{const key=[...prefix,blockId(node)].join(',');if(fitCache.has(key))return fitCache.get(key);const clone=node.cloneNode(true);measure.append(clone);const result=measure.getBoundingClientRect().height<=height+0.1;clone.remove();if(fitCache.size>=5000)fitCache.delete(fitCache.keys().next().value);fitCache.set(key,result);return result;};
  const append=node=>{prefix.push(blockId(node));measure.append(node.cloneNode(true));body.append(node);contentOnPage=true;};
  const blocks=Array.from(source.children).flatMap(flatten);
  let lastYield=performance.now();
  for(let blockIndex=0;blockIndex<blocks.length;blockIndex++) {
    if(performance.now()-lastYield>8){await new Promise(r=>setTimeout(r,0));lastYield=performance.now();}
    let block=blocks[blockIndex];
    if(block.matches('[data-page-break]')){if(contentOnPage && blocks.slice(blockIndex+1).some(x=>!x.matches('[data-page-break]')))newPage();continue;}
    if(fits(block)){append(block);continue;}
    if(block.matches('table')&&!block.querySelector('[rowspan]:not([rowspan="1"])')){
      const rows=[...block.querySelectorAll(':scope > tbody > tr')];
      const header=rows[0]?.children.length&&[...rows[0].children].every(c=>c.tagName==='TH')?rows.shift():null;
      const chunk=()=>{const t=block.cloneNode(false);const cg=block.querySelector('colgroup');if(cg)t.append(cg.cloneNode(true));const tb=document.createElement('tbody');if(header)tb.append(header.cloneNode(true));t.append(tb);return t;};
      let table=chunk(),rowCount=0;
      for(const row of rows){
        table.lastElementChild.append(row.cloneNode(true));
        if(!fits(table)){
          table.lastElementChild.lastElementChild.remove();
          if(rowCount){append(table);newPage();}else if(contentOnPage)newPage();
          table=chunk();table.lastElementChild.append(row.cloneNode(true));rowCount=0;
          if(!fits(table))throw Error('A table row is taller than a page. Shorten the row or reduce its font size.');
        }
        rowCount++;
        if(performance.now()-lastYield>8){await new Promise(r=>setTimeout(r,0));lastYield=performance.now();}
      }
      if(rowCount||header){if(!fits(table))throw Error('Table header is taller than a page.');append(table);}continue;
    }
    // Move short blocks intact; split only blocks larger than an empty page.
    const previous=[...measure.children];measure.replaceChildren(block.cloneNode(true));const aloneHeight=measure.getBoundingClientRect().height;measure.replaceChildren(...previous);
    if(contentOnPage && aloneHeight<=height){newPage();append(block);continue;}
    if(block.matches('table,img'))throw Error(block.matches('table')?'A table with vertically merged cells must fit on one page. Split the merged cells.':'The image is taller than a page. Select it and reduce its width.');
    const text=block.textContent;const {words,graphemes}=boundaries(text);let start=0;
    if(!text.length){if(contentOnPage)newPage();if(!fits(block))throw new Error('A block is larger than the printable page. Reduce its size.');append(block);continue;}
    while(start<text.length){
      const findEnd=points=>{const candidates=points.filter(n=>n>start);let lo=0,hi=candidates.length-1,best=start;while(lo<=hi){const mid=(lo+hi)>>1;const node=slice(block,start,candidates[mid]);if(start>0){node.setAttribute('data-continuation','true');if(node.matches('ul,ol'))node.style.listStyleType='none';}if(fits(node)){best=candidates[mid];lo=mid+1;}else hi=mid-1;}return best;};
      let end=findEnd(words);if(end===start)end=findEnd(graphemes);
      if(end===start){if(contentOnPage){newPage();continue;}throw new Error('A text line is taller than the printable area. Reduce font size or margins.');}
      const fragment=slice(block,start,end);if(start>0){fragment.setAttribute('data-continuation','true');if(fragment.matches('ul,ol'))fragment.style.listStyleType='none';}
      append(fragment);start=end;if(start<text.length)newPage();
    }
  }
  measure.replaceChildren();
  return {pageCount:count,widthMm:settings.landscape?297:210,heightMm:settings.landscape?210:297};
}
