import {harness,settings} from './harness.mjs';
import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
const h=await harness(),p=h.page;const errors=[];p.on('pageerror',e=>errors.push(e.message));
const id='11111111-1111-1111-1111-111111111111.png';
const para=text=>({type:'paragraph',content:text?[{type:'text',text}]:[]});
const boot=content=>p.evaluate(({content,settings})=>window.Lekhak.boot({schemaVersion:1,page:settings,content:{type:'doc',content}}),{content,settings});
const json=()=>p.evaluate(()=>window.Lekhak.getJSON());
const settle=()=>p.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))));
async function drag(selector,dx,dy=0){const box=await p.locator(selector).boundingBox();assert.ok(box,selector);const x=box.x+box.width/2,y=box.y+box.height/2;await p.mouse.move(x,y);await p.mouse.down();await p.mouse.move(x+dx,y+dy,{steps:8});await p.mouse.up();await settle();}
try{
 await p.setViewportSize({width:360,height:740});
 const bytes=await p.evaluate(()=>{const c=document.createElement('canvas');c.width=320;c.height=160;const x=c.getContext('2d');x.fillStyle='#dbeafe';x.fillRect(0,0,320,160);x.fillStyle='#0f6cbd';x.font='32px sans-serif';x.fillText('Image preview',32,90);return c.toDataURL().split(',')[1];});
 await p.route('**/document-assets/'+id,r=>r.fulfill({contentType:'image/png',body:Buffer.from(bytes,'base64')}));
 await boot([para('Resize image'),{type:'image',attrs:{assetId:id,src:'/document-assets/'+id,width:50,align:'left'}},para('After image')]);
 await p.locator('#editor img').click();await settle();
 assert.equal(await p.locator('.image-grip').count(),4);
 const original=await json();
 // Touch resize is live without writing a document revision until release; cancellation rolls back.
 const touch=await p.context().newCDPSession(p);let grip=await p.locator('.image-grip.se').boundingBox();
 let tx=grip.x+16,ty=grip.y+16;const imageBefore=await p.locator('#editor img').boundingBox();
 await touch.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:tx,y:ty,id:1}]});
 await touch.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:tx+25,y:ty+12,id:1}]});
 assert.ok((await p.locator('#editor img').boundingBox()).width>imageBefore.width);
 assert.deepEqual(await json(),original);
 await touch.send('Input.dispatchTouchEvent',{type:'touchCancel',touchPoints:[]});await settle();
 assert.deepEqual(await json(),original);assert.equal((await p.locator('#editor img').boundingBox()).width,imageBefore.width);
 await drag('.image-grip.se',60,30);
 const resized=await json();assert.ok(resized.content[1].attrs.width>50);assert.equal(resized.content[1].attrs.align,'left');
 const ratio=await p.locator('#editor img').evaluate(e=>e.clientWidth/e.clientHeight);assert.ok(Math.abs(ratio-2)<.05);
 await p.evaluate(()=>window.Lekhak.command('undo'));assert.deepEqual(await json(),original);
 await p.evaluate(()=>window.Lekhak.command('redo'));assert.deepEqual(await json(),resized);
 await settle();await drag('.image-grip.se',1000,500);assert.equal((await json()).content[1].attrs.width,100);
 await drag('.image-grip.se',-1000,-500);assert.equal((await json()).content[1].attrs.width,10);
 await p.evaluate(()=>window.Lekhak.command('imageWidth','65'));await settle();
 await mkdir('../validation/media-tools',{recursive:true});await p.screenshot({path:'../validation/media-tools/image-handles.png'});
 assert.ok(!(await p.evaluate(()=>window.Lekhak.getHTML())).includes('resize-grip'));
 // Review mode must hide controls and preserve the document.
 await p.evaluate(()=>window.Lekhak.review(true));await settle();assert.equal(await p.locator('.resize-grip').count(),0);assert.equal(await p.locator('.tiptap').getAttribute('contenteditable'),'false');
 await p.evaluate(()=>window.Lekhak.review(false));await settle();
 // Persistent widths across all rows, fixed total width and one-step undo.
 await boot([para('Table columns'),{type:'table',content:Array.from({length:3},(_,r)=>({type:'tableRow',content:Array.from({length:3},(_,c)=>({type:r===0?'tableHeader':'tableCell',content:[para(`Cell ${r+1}.${c+1}`)]}))}))},para('End')]);
 await p.locator('#editor td').first().click();await settle();
 assert.equal(await p.locator('.column-grip').count(),2);const before=await json();
 const tableBefore=await p.locator('#editor table').boundingBox();await drag('.column-grip >> nth=0',35);
 const after=await json(),first=after.content[1].content[0].content;
 assert.ok(first[0].attrs.colwidth[0]>first[1].attrs.colwidth[0]);
 for(const row of after.content[1].content)assert.deepEqual(row.content.map(c=>c.attrs.colwidth),first.map(c=>c.attrs.colwidth));
 assert.ok(Math.abs((await p.locator('#editor table').boundingBox()).width-tableBefore.width)<3);
 await p.evaluate(()=>window.Lekhak.command('undo'));assert.deepEqual(await json(),before);
 await p.evaluate(()=>window.Lekhak.command('redo'));assert.deepEqual(await json(),after);
 await boot(after.content);assert.deepEqual(await json(),after);
 await p.locator('#editor td').first().click();await settle();
 // Real touch PointerEvents through Chrome's touch input path.
 const session=await p.context().newCDPSession(p);const box=await p.locator('.column-grip').first().boundingBox();const x=box.x+16,y=box.y+16;
 await session.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x,y,id:1}]});
 await session.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:x-15,y,id:1}]});
 await session.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});await settle();
 assert.notDeepEqual(await json(),after,'touch drag changes widths');
 await p.screenshot({path:'../validation/media-tools/table-grips.png'});
 // Horizontal merged header updates every covered column and preserves spans.
 await boot([{type:'table',content:[{type:'tableRow',content:[{type:'tableHeader',attrs:{colspan:2,colwidth:[100,100]},content:[para('Merged')]},{type:'tableHeader',attrs:{colwidth:[100]},content:[para('Third')]}]},
 {type:'tableRow',content:[0,1,2].map(i=>({type:'tableCell',attrs:{colwidth:[100]},content:[para('Cell '+i)]}))}]}]);
 await p.locator('#editor td').first().click();await settle();await drag('.column-grip >> nth=0',20);
 const merged=await json(),mergedCells=merged.content[0].content;
 assert.equal(mergedCells[0].content[0].attrs.colspan,2);
 assert.deepEqual(mergedCells[0].content[0].attrs.colwidth,mergedCells[1].content.slice(0,2).map(c=>c.attrs.colwidth[0]));
 const exportResult=await p.evaluate(async()=>{const r=await window.Lekhak.exportPdf();return {pages:r.pages,length:r.bytes.length};});assert.ok(exportResult.pages>=1&&exportResult.length>1000);
 assert.deepEqual(errors,[]);console.log('PASS: image aspect ratio/clamps/undo; column drag mouse+touch/all rows/reload/undo; review hides handles; resized table PDF export');
}finally{await h.close();}
