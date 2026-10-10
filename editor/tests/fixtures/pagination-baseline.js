// Layout uses the browser's shaping and line measurement. No canvas rasterization.
const MM = 96 / 25.4;
export function applySettings(settings) {
  const root = document.documentElement.style;
  const family = settings.fontFamily === 'Monospace' ? "monospace,'Noto Devanagari'" : `'${settings.fontFamily}','Noto Devanagari',sans-serif`;
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
  await Promise.all(['Noto Sans','Noto Devanagari','Noto Serif'].flatMap(family=>[400,700].map(weight=>document.fonts.load(`${weight} ${settings.fontSize}px "${family}"`))));
  await document.fonts.ready;
  const width=((settings.landscape?297:210)-settings.marginLeft-settings.marginRight)*MM;
  // Small safety tolerance absorbs Chromium print conversion rounding, not a second layout.
  const height=((settings.landscape?210:297)-settings.marginTop-settings.marginBottom)*MM-1;
  const measure=document.getElementById('measure');measure.style.width=`${width}px`;
  const source=document.createElement('div');source.innerHTML=html;
  const output=document.getElementById('pages');output.innerHTML='';
  let body;let count=0;let contentOnPage=false;
  const newPage=()=>{const page=document.createElement('section');page.className='page';page.setAttribute('aria-label',`Page ${++count}`);body=document.createElement('div');body.className='page-content';page.append(body);output.append(page);contentOnPage=false;};
  newPage();
  const fits=node=>{measure.replaceChildren(...Array.from(body.children).map(x=>x.cloneNode(true)),node.cloneNode(true));const children=Array.from(measure.children);if(children.length)children.at(-1).style.marginBottom='0';return measure.getBoundingClientRect().height<=height+0.1;};
  const append=node=>{body.append(node);contentOnPage=true;};
  const blocks=Array.from(source.children).flatMap(flatten);
  for(let blockIndex=0;blockIndex<blocks.length;blockIndex++) {
    let block=blocks[blockIndex];
    if(block.matches('[data-page-break]')){if(contentOnPage && blocks.slice(blockIndex+1).some(x=>!x.matches('[data-page-break]')))newPage();continue;}
    if(fits(block)){append(block);continue;}
    // Move short blocks intact; split only blocks larger than an empty page.
    measure.replaceChildren(block.cloneNode(true));measure.lastElementChild.style.marginBottom='0';
    if(contentOnPage && measure.getBoundingClientRect().height<=height){newPage();append(block);continue;}
    const text=block.textContent;const {words,graphemes}=boundaries(text);let start=0;
    if(!text.length){if(contentOnPage)newPage();if(!fits(block))throw new Error('A block is larger than the printable page. Reduce its size.');append(block);continue;}
    while(start<text.length){
      const findEnd=points=>{const candidates=points.filter(n=>n>start);let lo=0,hi=candidates.length-1,best=start;while(lo<=hi){const mid=(lo+hi)>>1;const node=slice(block,start,candidates[mid]);if(start>0){node.setAttribute('data-continuation','true');if(node.matches('ul,ol'))node.style.listStyleType='none';}if(fits(node)){best=candidates[mid];lo=mid+1;}else hi=mid-1;}return best;};
      let end=findEnd(words);if(end===start)end=findEnd(graphemes);
      if(end===start){if(contentOnPage){newPage();continue;}throw new Error('A text line is taller than the printable area. Reduce font size or margins.');}
      const fragment=slice(block,start,end);if(start>0){fragment.setAttribute('data-continuation','true');if(fragment.matches('ul,ol'))fragment.style.listStyleType='none';}
      append(fragment);start=end;if(start<text.length)newPage();
    }
    // Yield between blocks so large documents do not monopolize the UI thread.
    if(blockIndex%8===0)await new Promise(r=>setTimeout(r,0));
  }
  measure.replaceChildren();
  return {pageCount:count,widthMm:settings.landscape?297:210,heightMm:settings.landscape?210:297};
}
