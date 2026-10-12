import { Extension } from '@tiptap/core';
import { Plugin, NodeSelection } from '@tiptap/pm/state';
import { TableMap } from '@tiptap/pm/tables';
import { closeHistory } from '@tiptap/pm/history';

function elementSelector(element,root){
 const path=[];for(let e=element;e&&e!==root;e=e.parentElement)path.unshift(`${e.tagName.toLowerCase()}:nth-child(${[...e.parentElement.children].indexOf(e)+1})`);
 return '.tiptap > '+path.join(' > ');
}
const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
export function selectedTable(state) {
 const {$from}=state.selection;
 for(let d=$from.depth;d>0;d--) if($from.node(d).type.name==='table')
  return {node:$from.node(d),pos:$from.before(d)};
 if(state.selection.node?.type.name==='table')return {node:state.selection.node,pos:state.selection.from};
 return null;
}
function commit(view,tr) {
 view.dispatch(closeHistory(tr));
 view.dispatch(closeHistory(view.state.tr));
}
function columnWidths(view,table,element) {
 const map=TableMap.get(table.node), rect=element.getBoundingClientRect(), edges=new Map([[0,0],[map.width,rect.width]]);
 for(const offset of new Set(map.map)) {
  const cell=view.nodeDOM(table.pos+1+offset), box=cell?.getBoundingClientRect();
  if(!box)continue;
  const range=map.findCell(offset);
  edges.set(range.left,box.left-rect.left);edges.set(range.right,box.right-rect.left);
 }
 const points=[...edges.keys()].sort((a,b)=>a-b), widths=Array(map.width);
 for(let k=0;k<points.length-1;k++){
  const a=points[k],b=points[k+1],w=(edges.get(b)-edges.get(a))/(b-a);
  for(let c=a;c<b;c++)widths[c]=w;
 }
 return {map,widths};
}
export const TouchResize=Extension.create({
 name:'touchResize',
 addProseMirrorPlugins(){return [new Plugin({view(view){
  const layer=document.createElement('div');layer.className='resize-tools';document.body.append(layer);
  let frame=0,drag=null,destroyed=false;
  const schedule=()=>{if(!frame&&!destroyed)frame=requestAnimationFrame(()=>{frame=0;render();});};
  const button=(label,kind,x,y)=>{
   const b=document.createElement('button');b.type='button';b.className=`resize-grip ${kind}`;
   b.setAttribute('aria-label',label);b.style.left=`${x}px`;b.style.top=`${y}px`;layer.append(b);return b;
  };
  const outline=rect=>{const e=document.createElement('div');e.className='resize-outline';Object.assign(e.style,{left:`${rect.left}px`,top:`${rect.top}px`,width:`${rect.width}px`,height:`${rect.height}px`});layer.append(e);return e;};
  function begin(event,preview,apply){
   if(!event.isPrimary||event.button!==0||!view.editable)return;
   event.preventDefault();event.stopPropagation();
   const handle=event.currentTarget;handle.setPointerCapture(event.pointerId);
   drag={id:event.pointerId,doc:view.state.doc,preview,apply,handle};
  }
  function end(event,cancel=false){
   if(!drag||event.pointerId!==drag.id)return;
   event.preventDefault();const current=drag;drag=null;layer.querySelectorAll('style').forEach(e=>e.remove());
   if(current.handle.hasPointerCapture(event.pointerId))current.handle.releasePointerCapture(event.pointerId);
   if(!cancel&&view.editable&&view.state.doc===current.doc)current.apply();
   schedule();
  }
  const move=e=>{if(drag&&e.pointerId===drag.id){e.preventDefault();drag.preview(e);}};
  const up=e=>end(e);const cancel=e=>end(e,true);
  layer.addEventListener('pointermove',move);layer.addEventListener('pointerup',up);layer.addEventListener('pointercancel',cancel);
  layer.addEventListener('click',e=>{e.preventDefault();e.stopPropagation();});
  function render(){
   if(drag)return;
   layer.replaceChildren();
   if(!view.editable||!view.dom.isConnected||document.body.classList.contains('pages'))return;
   const hindi=document.documentElement.lang==='hi';
   const selection=view.state.selection;
   if(selection instanceof NodeSelection&&selection.node.type.name==='image'){
    const img=view.nodeDOM(selection.from);if(!(img instanceof HTMLImageElement))return;
    const rect=img.getBoundingClientRect(), parent=img.parentElement;
    const css=getComputedStyle(parent), available=parent.clientWidth-parseFloat(css.paddingLeft)-parseFloat(css.paddingRight);
    if(rect.width<1||rect.height<1||available<=0)return;
    outline(rect);
    for(const [name,sx,sy] of [['nw',-1,-1],['ne',1,-1],['sw',-1,1],['se',1,1]]){
     const b=button(hindi?'चित्र का आकार बदलें':'Resize image',`image-grip ${name}`,sx<0?rect.left:rect.right,sy<0?rect.top:rect.bottom);
     b.onpointerdown=e=>{
      if(!e.isPrimary||e.button!==0||!view.editable)return;
      const live=document.createElement('style');layer.append(live);const selector=elementSelector(img,view.dom);
      let width=Number(selection.node.attrs.width)||50;
      const x=e.clientX,y=e.clientY;
      const ghost=outline(rect);ghost.classList.add('resize-ghost');
      begin(e,p=>{
       const dx=(p.clientX-x)*sx,dy=(p.clientY-y)*sy*rect.width/rect.height;
       const delta=Math.abs(dx)>=Math.abs(dy)?dx:dy;
       width=Math.round(clamp((rect.width+delta)/available*100,10,100));
       live.textContent=`${selector}{width:${width}%!important}`;
       const box=img.getBoundingClientRect();
       Object.assign(ghost.style,{width:`${box.width}px`,height:`${box.height}px`,left:`${box.left}px`,top:`${box.top}px`});
       for(const handle of layer.querySelectorAll('.image-grip')){handle.style.left=`${handle.classList.contains('nw')||handle.classList.contains('sw')?box.left:box.right}px`;handle.style.top=`${handle.classList.contains('nw')||handle.classList.contains('ne')?box.top:box.bottom}px`;}
      },()=>{if(width!==selection.node.attrs.width)commit(view,view.state.tr.setNodeMarkup(selection.from,null,{...selection.node.attrs,width}));});
     };
     b.onkeydown=e=>{if(['ArrowLeft','ArrowRight'].includes(e.key)){e.preventDefault();const width=clamp((Number(selection.node.attrs.width)||50)+(e.key==='ArrowRight'?5:-5),10,100);commit(view,view.state.tr.setNodeMarkup(selection.from,null,{...selection.node.attrs,width}));}};
    }
   }
   const table=selectedTable(view.state);if(!table)return;
   const node=view.nodeDOM(table.pos),el=node?.matches?.('table')?node:node?.querySelector?.('table');if(!el)return;
   const rect=el.getBoundingClientRect(), {map,widths}=columnWidths(view,table,el);
   // Pairwise resizing holds the overall table width, including merged-cell tables.
   const applyWidths=values=>{
    const tr=view.state.tr;
    for(const offset of new Set(map.map)){
     const cell=table.node.nodeAt(offset),range=map.findCell(offset);
     tr.setNodeMarkup(table.pos+1+offset,null,{...cell.attrs,colwidth:values.slice(range.left,range.right).map(Math.round)});
    }
    commit(view,tr);
   };
   let left=rect.left;
   for(let c=0;c<widths.length-1;c++){
    left+=widths[c];
    const cellDOM=view.domAtPos(selection.from).node;
    const cell=(cellDOM.nodeType===1?cellDOM:cellDOM.parentElement)?.closest?.('td,th');
    const y=clamp(cell?.getBoundingClientRect().top??rect.top,rect.top,rect.bottom);
    const b=button(hindi?`कॉलम ${c+1} की चौड़ाई`:`Resize column ${c+1}`,'column-grip',left,y+14);
    b.textContent='↔';
    const resize=delta=>{const v=[...widths],limit=widths[c]+widths[c+1];v[c]=clamp(widths[c]+delta,36,Math.max(36,limit-36));v[c+1]=limit-v[c];return v;};
    b.onpointerdown=e=>{if(!e.isPrimary||e.button!==0||!view.editable)return;
     const live=document.createElement('style');layer.append(live);const selector=elementSelector(el,view.dom);
     const x=e.clientX,startLeft=parseFloat(b.style.left);let values=[...widths];
     const line=document.createElement('div');line.className='column-guide';Object.assign(line.style,{left:`${startLeft}px`,top:`${rect.top}px`,height:`${rect.height}px`});layer.append(line);
     begin(e,p=>{values=resize(p.clientX-x);live.textContent=values.map((w,i)=>`${selector} > colgroup > col:nth-child(${i+1}){width:${w}px!important}`).join('');line.style.left=`${startLeft+values[c]-widths[c]}px`;},()=>{if(values.some((v,i)=>Math.abs(v-widths[i])>.5))applyWidths(values);});
    };
    b.onkeydown=e=>{if(['ArrowLeft','ArrowRight'].includes(e.key)){e.preventDefault();applyWidths(resize(e.key==='ArrowRight'?8:-8));}};
   }
  }
  window.addEventListener('resize',schedule);document.addEventListener('scroll',schedule,true);
  window.visualViewport?.addEventListener('resize',schedule);window.visualViewport?.addEventListener('scroll',schedule);
  view.dom.addEventListener('load',schedule,true);
  const observer=new ResizeObserver(schedule);observer.observe(view.dom);schedule();
  return {update: schedule,destroy(){destroyed=true;drag=null;cancelAnimationFrame(frame);observer.disconnect();layer.remove();window.removeEventListener('resize',schedule);document.removeEventListener('scroll',schedule,true);window.visualViewport?.removeEventListener('resize',schedule);window.visualViewport?.removeEventListener('scroll',schedule);view.dom.removeEventListener('load',schedule,true);}};
 }})];}
});
