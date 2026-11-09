import { layoutImage, imageStyles, imageGeometry, imageAngle } from './image-geometry.js';
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
export function getRowPositions(table) {
 const rows=[];
 let pos=table.pos+1;
 for(let r=0;r<table.node.childCount;r++){
  const child=table.node.child(r);
  rows.push({node:child,pos,index:r});
  pos+=child.nodeSize;
 }
 return rows;
}
export const TouchResize=Extension.create({
 name:'touchResize',
 addProseMirrorPlugins(){let lastWarning=0;return [new Plugin({filterTransaction(tr,state){
  if(!tr.docChanged)return true;
  const css=getComputedStyle(document.documentElement),mm=name=>parseFloat(css.getPropertyValue(name));
  const printableWidth=mm('--pw')-mm('--ml')-mm('--mr'),printableHeight=mm('--ph')-mm('--mt')-mm('--mb');
  if(!(printableWidth>0&&printableHeight>0))return true;
  let invalid=false;
  tr.doc.descendants((node,pos)=>{
    if(node.type.name!=='image')return;
    const before=pos<=state.doc.content.size?state.doc.nodeAt(pos):null;
    if(before?.type.name==='image'&&JSON.stringify(before.attrs)===JSON.stringify(node.attrs))return;
    const img=[...document.querySelectorAll('#editor img')].find(i=>i.dataset.assetId===node.attrs.assetId||i.src.endsWith('/'+node.attrs.assetId));
    const ratio=node.attrs.stretchRatio??(img?.naturalWidth?img.naturalHeight/img.naturalWidth:1);
    const g=imageGeometry(node.attrs.rotation,ratio),width=Number(node.attrs.width)||50;
    if(width>100||printableWidth*width/100*g.y/g.x>printableHeight-.8)invalid=true;
  });
  if(invalid&&Date.now()-lastWarning>1500){lastWarning=Date.now();const message=JSON.stringify({type:'error',message:'This image exceeds the printable page. Reduce its size or rotation.'});if(window.LekhakBridge)window.LekhakBridge.postMessage(message);else window.__messages=(window.__messages||[]).concat(JSON.parse(message));}
  return !invalid;
},props:{handleDOMEvents:{pointerdown(view,event){if(!view.editable)return false;const frame=event.target.closest?.('[data-image-frame]');if(!frame||!view.dom.contains(frame))return false;let position=null;view.state.doc.descendants((node,pos)=>{if(node.type.name==='image'&&view.nodeDOM(pos)===frame)position=pos;});if(position===null)return false;event.preventDefault();view.focus();view.dispatch(view.state.tr.setSelection(NodeSelection.create(view.state.doc,position)));return true;}},handleClickOn(view,pos,node,nodePos){if(!view.editable||node.type.name!=='image')return false;view.dispatch(view.state.tr.setSelection(NodeSelection.create(view.state.doc,nodePos)));return true;}},view(view){
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
   view.dom.querySelectorAll('[data-image-frame]').forEach(layoutImage);
   if(!view.editable||!view.dom.isConnected||document.body.classList.contains('pages'))return;
   const hindi=document.documentElement.lang==='hi';
   const selection=view.state.selection;
   if(selection instanceof NodeSelection&&selection.node.type.name==='image'){
    const frame=view.nodeDOM(selection.from),img=frame?.querySelector?.('img');if(!img?.naturalWidth)return;
    const rect=frame.getBoundingClientRect(),parent=frame.parentElement,css=getComputedStyle(parent);
    const available=parent.clientWidth-parseFloat(css.paddingLeft)-parseFloat(css.paddingRight),scale=rect.width/frame.offsetWidth;
    if(rect.width<1||rect.height<1||available<=0||!Number.isFinite(scale))return;
    const attrs=selection.node.attrs,natural=img.naturalHeight/img.naturalWidth,g=imageGeometry(attrs.rotation,attrs.stretchRatio??natural);
    const w=frame.offsetWidth/g.x,h=w*g.ratio,t=g.angle*Math.PI/180,cx=rect.left+rect.width/2,cy=rect.top+rect.height/2;
    outline(rect);
    const save=values=>commit(view,view.state.tr.setNodeMarkup(selection.from,null,{...attrs,...values}));
    const position=(sx,sy)=>({x:cx+(sx*w/2*Math.cos(t)-sy*h/2*Math.sin(t))*scale,y:cy+(sx*w/2*Math.sin(t)+sy*h/2*Math.cos(t))*scale});
    for(const [name,sx,sy] of [['nw',-1,-1],['n',0,-1],['ne',1,-1],['e',1,0],['se',1,1],['s',0,1],['sw',-1,1],['w',-1,0]]){
     const point=position(sx,sy),b=button(hindi?'चित्र का आकार बदलें':'Resize or stretch image',`image-grip ${name}`,point.x,point.y);
     b.onpointerdown=e=>{
      if(!e.isPrimary||e.button!==0||!view.editable)return;
      const live=document.createElement('style');layer.append(live);const selector=elementSelector(frame,view.dom),x=e.clientX,y=e.clientY;
      let values={};
      begin(e,p=>{
       const dx=((p.clientX-x)*Math.cos(t)+(p.clientY-y)*Math.sin(t))/scale;
       const dy=(-(p.clientX-x)*Math.sin(t)+(p.clientY-y)*Math.cos(t))/scale;
       let nw=Math.max(16,w+dx*sx),nh=Math.max(16,h+dy*sy);
       if(sx&&sy&&attrs.aspectLocked!==false){const change=Math.abs(dx/w)>=Math.abs(dy/h)?dx*sx/w:dy*sy/h;const factor=Math.max(16/w,16/h,1+change);nw=w*factor;nh=h*factor;}
       const ratio=Math.max(.05,Math.min(20,nh/nw)),next=imageGeometry(g.angle,ratio);
       values={width:clamp(nw*next.x/available*100,10,100),stretchRatio:ratio};
       const styles=imageStyles({...attrs,...values},natural);
       live.textContent=`${selector}{${styles.frame.replaceAll(';','!important;')}!important}${selector}>img{${styles.image.replaceAll(';','!important;')}!important}`;
      },()=>{if(Object.keys(values).length)save(values);});
     };
     b.onkeydown=e=>{if(['ArrowLeft','ArrowRight','ArrowUp','ArrowDown'].includes(e.key)){
      e.preventDefault();const amount=e.key==='ArrowRight'||e.key==='ArrowDown'?1:-1;
      if(sx&&sy)save({width:clamp(Number(attrs.width)+amount*2,10,100)});
      else save({stretchRatio:clamp(g.ratio*(1+amount*.05),.05,20)});
     }};
    }
    const point=position(0,-1),rot=button(hindi?'चित्र घुमाएँ':'Rotate image freely','rotation-grip',clamp(point.x+Math.sin(t)*38,24,window.innerWidth-24),clamp(point.y-Math.cos(t)*38<24?rect.bottom+38:point.y-Math.cos(t)*38,24,(window.visualViewport?.height||window.innerHeight)-24));rot.textContent='↻';
    rot.onpointerdown=e=>{
     if(!e.isPrimary||e.button!==0||!view.editable)return;
     const live=document.createElement('style');layer.append(live);const selector=elementSelector(frame,view.dom);
     const start=Math.atan2(e.clientY-cy,e.clientX-cx);let rotation=g.angle;
     begin(e,p=>{rotation=imageAngle(g.angle+(Math.atan2(p.clientY-cy,p.clientX-cx)-start)*180/Math.PI);
      if(p.shiftKey)rotation=Math.round(rotation/15)*15;
      const styles=imageStyles({...attrs,rotation},natural);
      live.textContent=`${selector}{${styles.frame.replaceAll(';','!important;')}!important}${selector}>img{${styles.image.replaceAll(';','!important;')}!important}`;
     },()=>save({rotation}));
    };
    rot.onkeydown=e=>{if(['ArrowLeft','ArrowRight'].includes(e.key)){e.preventDefault();save({rotation:imageAngle(g.angle+(e.key==='ArrowRight'?1:-1))});}};
   }
   const table=selectedTable(view.state);if(!table)return;
   const node=view.nodeDOM(table.pos),el=node?.matches?.('table')?node:node?.querySelector?.('table');if(!el)return;
   const rect=el.getBoundingClientRect(), {map,widths}=columnWidths(view,table,el);
   const rows=[...el.querySelectorAll(':scope > tbody > tr, :scope > tr')];
   const rowPositions=getRowPositions(table);
   outline(rect);
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
   // Row resizing by dragging vertical row grips
   const applyRowHeight=(rowIndex,height)=>{
    const target=rowPositions[rowIndex];if(!target)return;
    const tr=view.state.tr;
    tr.setNodeMarkup(target.pos,null,{...target.node.attrs,height:Math.max(28,Math.round(height))});
    commit(view,tr);
   };
   const cellDOM=view.domAtPos(selection.from).node;
   const cell=(cellDOM.nodeType===1?cellDOM:cellDOM.parentElement)?.closest?.('td,th');
   const cellRect=cell?.getBoundingClientRect();
   const rowGripX=clamp((cellRect?cellRect.left:rect.left)+16,rect.left+16,rect.right-16);

   for(let r=0;r<rows.length;r++){
    const rowBox=rows[r].getBoundingClientRect();
    const startHeight=rowBox.height;
    const b=button(hindi?`पंक्ति ${r+1} की ऊँचाई`:`Resize row ${r+1}`,'row-grip',rowGripX,rowBox.bottom);
    b.textContent='↕';
    b.onpointerdown=e=>{if(!e.isPrimary||e.button!==0||!view.editable)return;
     const live=document.createElement('style');layer.append(live);const selector=elementSelector(el,view.dom);
     const y=e.clientY,startTop=parseFloat(b.style.top);let newHeight=startHeight;
     const line=document.createElement('div');line.className='row-guide';Object.assign(line.style,{left:`${rect.left}px`,top:`${startTop}px`,width:`${rect.width}px`});layer.append(line);
     begin(e,p=>{
      const delta=p.clientY-y;
      newHeight=Math.max(28,Math.round(startHeight+delta));
      live.textContent=`${selector} > tbody > tr:nth-child(${r+1}), ${selector} > tr:nth-child(${r+1}){height:${newHeight}px!important}`;
      line.style.top=`${startTop+newHeight-startHeight}px`;
     },()=>{if(Math.abs(newHeight-startHeight)>1)applyRowHeight(r,newHeight);});
    };
    b.onkeydown=e=>{if(['ArrowUp','ArrowDown'].includes(e.key)){e.preventDefault();const current=Number(rowPositions[r]?.node.attrs.height)||startHeight;applyRowHeight(r,Math.max(28,current+(e.key==='ArrowDown'?8:-8)));}};
   }

   // Corner table resizing: adjust columns and rows proportionally from bottom-right corner just like an image
   const applyTableDimensions=(colVals,rowVals)=>{
    const tr=view.state.tr;
    for(const offset of new Set(map.map)){
     const cell=table.node.nodeAt(offset),range=map.findCell(offset);
     tr.setNodeMarkup(table.pos+1+offset,null,{...cell.attrs,colwidth:colVals.slice(range.left,range.right).map(Math.round)});
    }
    for(let r=0;r<rowPositions.length;r++){
     const target=rowPositions[r];
     tr.setNodeMarkup(target.pos,null,{...target.node.attrs,height:Math.max(28,Math.round(rowVals[r]))});
    }
    commit(view,tr);
   };

   const parent=el.parentElement, css=getComputedStyle(parent);
   const available=parent.clientWidth-parseFloat(css.paddingLeft)-parseFloat(css.paddingRight);
   const rowHeights=rows.map(r=>r.getBoundingClientRect().height);
   const corner=button(hindi?'तालिका का आकार बदलें':'Resize table','table-grip se',rect.right,rect.bottom);
   corner.onpointerdown=e=>{if(!e.isPrimary||e.button!==0||!view.editable)return;
    const live=document.createElement('style');layer.append(live);const selector=elementSelector(el,view.dom);
    const startX=e.clientX,startY=e.clientY,startW=rect.width,startH=rect.height;
    const ghost=outline(rect);ghost.classList.add('resize-ghost');
    let targetWidths=[...widths],targetHeights=[...rowHeights];
    begin(e,p=>{
     const dx=p.clientX-startX,dy=p.clientY-startY;
     const targetW=clamp(startW+dx,widths.length*36,available);
     const targetH=Math.max(rows.length*28,startH+dy);
     const scaleX=targetW/startW,scaleY=targetH/startH;
     targetWidths=widths.map(w=>Math.max(36,Math.round(w*scaleX)));
     targetHeights=rowHeights.map(h=>Math.max(28,Math.round(h*scaleY)));
     ghost.style.width=`${targetW}px`;ghost.style.height=`${targetH}px`;
     corner.style.left=`${rect.left+targetW}px`;corner.style.top=`${rect.top+targetH}px`;
     live.textContent=`${selector}{width:${targetW}px!important}`+
      targetWidths.map((w,i)=>`${selector} > colgroup > col:nth-child(${i+1}){width:${w}px!important}`).join('')+
      targetHeights.map((h,i)=>`${selector} > tbody > tr:nth-child(${i+1}), ${selector} > tr:nth-child(${i+1}){height:${h}px!important}`).join('');
    },()=>{
     const wChanged=targetWidths.some((w,i)=>Math.abs(w-widths[i])>.5);
     const hChanged=targetHeights.some((h,i)=>Math.abs(h-rowHeights[i])>1);
     if(wChanged||hChanged)applyTableDimensions(targetWidths,targetHeights);
    });
   };
   corner.onkeydown=e=>{
    if(['ArrowRight','ArrowLeft','ArrowDown','ArrowUp'].includes(e.key)){
     e.preventDefault();
     const dw=e.key==='ArrowRight'?8:e.key==='ArrowLeft'?-8:0;
     const dh=e.key==='ArrowDown'?8:e.key==='ArrowUp'?-8:0;
     const targetW=clamp(rect.width+dw,widths.length*36,available);
     const targetH=Math.max(rows.length*28,rect.height+dh);
     const targetWidths=widths.map(w=>Math.max(36,Math.round(w*(targetW/rect.width))));
     const targetHeights=rowHeights.map(h=>Math.max(28,Math.round(h*(targetH/rect.height))));
     applyTableDimensions(targetWidths,targetHeights);
    }
   };
  }
  window.addEventListener('resize',schedule);document.addEventListener('scroll',schedule,true);
  window.visualViewport?.addEventListener('resize',schedule);window.visualViewport?.addEventListener('scroll',schedule);
  view.dom.addEventListener('load',schedule,true);
  const observer=new ResizeObserver(schedule);observer.observe(view.dom);schedule();
  return {update: schedule,destroy(){destroyed=true;drag=null;cancelAnimationFrame(frame);observer.disconnect();layer.remove();window.removeEventListener('resize',schedule);document.removeEventListener('scroll',schedule,true);window.visualViewport?.removeEventListener('resize',schedule);window.visualViewport?.removeEventListener('scroll',schedule);view.dom.removeEventListener('load',schedule,true);}};
 }})];}
});
