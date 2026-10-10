import {harness,settings} from './harness.mjs';
import {writeFile,readFile,mkdir} from 'node:fs/promises';
import assert from 'node:assert/strict';
const h=await harness(),out='../validation/audit',results=[],errors=[];
h.page.on('pageerror',e=>errors.push(e.message));
const samples=JSON.parse(await readFile(out+'/hindi-input.json','utf8'));
const p=(text,marks=[])=>({type:'paragraph',content:text?[{type:'text',text,marks}]:[]});
const id='11111111-1111-1111-1111-111111111111.png';
// Generated test pattern, not a real signature or personal photo.
const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVQImWP4z8DwH4QZGBgYGMAAAE8ABf4G0WAAAAAASUVORK5CYII=','base64');
await h.page.route('**/document-assets/'+id,route=>route.fulfill({contentType:'image/png',body:png}));
const boot=async(content,page=settings)=>h.page.evaluate(p=>window.Lekhak.boot(p),{schemaVersion:1,page,content:{type:'doc',content}});
async function exportCase(name,content,page=settings){
 await boot(content,page);
 const before=await h.page.evaluate(()=>window.Lekhak.getJSON());
 const result=await h.page.evaluate(async()=>{const r=await window.Lekhak.exportPdf();return{bytes:[...r.bytes],milliseconds:r.milliseconds,pages:r.pages,overflow:[...document.querySelectorAll('.page-content')].some(e=>e.scrollWidth>e.clientWidth+2||e.scrollHeight>e.clientHeight+2),text:[...document.querySelectorAll('.page-content')].map(e=>e.innerText).join('\n')};});
 assert.equal(result.overflow,false,name);assert.deepEqual(await h.page.evaluate(()=>window.Lekhak.getJSON()),before);
 await boot(before.content,page);assert.deepEqual(await h.page.evaluate(()=>window.Lekhak.getJSON()),before);
 await writeFile(`${out}/${name}.pdf`,Buffer.from(result.bytes));
 results.push({name,...result,bytes:result.bytes.length});console.log(name,result.pages,result.milliseconds.toFixed(1)+'ms');
 return result;
}
try{
 await mkdir(out,{recursive:true});
 await exportCase('unicode-samples',samples.map(x=>p(x)));
 await exportCase('long-hindi',[p(samples.join(' ').repeat(70))]);
 await exportCase('styles',[p('प्रधानाचार्य महोदय',[{type:'bold'},{type:'italic'},{type:'underline'}]),{type:'paragraph',attrs:{textAlign:'justify'},content:[{type:'text',text:'हिन्दी और '},{type:'text',text:'English',marks:[{type:'bold'}]},{type:'text',text:' दोनों। कृपया मेरा आवेदन स्वीकार करें।'}]},p('Two  spaces remain.')]);
 await exportCase('mono',[p('Hello 123 हिन्दी और English दोनों')],{...settings,fontFamily:'Monospace'});
 await exportCase('serif',samples.map(x=>p(x)),{...settings,fontFamily:'Noto Serif',landscape:true});
 const cell=(text,header=false)=>({type:header?'tableHeader':'tableCell',content:[p(text)]});
 const rows=[{type:'tableRow',content:[cell('नाम',true),cell('विवरण',true),cell('क्रम',true)]},...Array.from({length:70},(_,i)=>({type:'tableRow',content:[cell('विद्यार्थी '+i),cell('क्षेत्रीय कार्यालय'),cell(String(i))]}))];
 const table=await exportCase('multipage-table',[{type:'table',content:rows}]);assert.ok(table.pages>1);
 for(let i=0;i<70;i++)assert.equal((table.text.match(new RegExp('विद्यार्थी '+i+'\\n','g'))||[]).length,1);
 // Use a browser-generated PNG so the fixture itself is validated by its decoder.
 const imageBytes=await h.page.evaluate(()=>{const c=document.createElement('canvas');c.width=320;c.height=120;const x=c.getContext('2d');x.fillStyle='#e2e8f0';x.fillRect(0,0,320,120);x.strokeStyle='#1d4ed8';x.lineWidth=6;x.beginPath();x.moveTo(20,90);x.bezierCurveTo(100,0,140,150,290,25);x.stroke();return c.toDataURL().split(',')[1];});
 await h.page.unroute('**/document-assets/'+id);await h.page.route('**/document-assets/'+id,r=>r.fulfill({contentType:'image/png',body:Buffer.from(imageBytes,'base64')}));
 await exportCase('image-signature',[p('हस्ताक्षर / Signature'),{type:'image',attrs:{assetId:id,src:'/document-assets/'+id,width:50,align:'right',alt:'Test signature'}},p('प्रधानाचार्य महोदय')]);
 // Real command, selected node resize, undo/redo and round trip.
 await boot([p('')]);await h.page.evaluate(()=>window.Lekhak.command('table'));
 assert.equal((await h.page.evaluate(()=>window.Lekhak.getJSON())).content[0].type,'table');
 await h.page.evaluate(async()=>{await window.Lekhak.command('addRowAfter');await window.Lekhak.command('addColumnAfter');});
 let json=await h.page.evaluate(()=>window.Lekhak.getJSON());assert.equal(json.content[0].content.length,4);assert.equal(json.content[0].content[0].content.length,4);
 await h.page.evaluate(async()=>{await window.Lekhak.command('deleteRow');await window.Lekhak.command('deleteColumn');await window.Lekhak.command('cellBorder','2');});
 json=await h.page.evaluate(()=>window.Lekhak.getJSON());assert.equal(json.content[0].content.length,3);
 await boot(json.content);assert.deepEqual(await h.page.evaluate(()=>window.Lekhak.getJSON()),json);
 await boot([p('')]);await h.page.evaluate(async id=>window.Lekhak.command('image',JSON.stringify({assetId:id,signature:true})),id);
 await h.page.locator('#editor img').click();await h.page.evaluate(async()=>{await window.Lekhak.command('imageWidth','75');await window.Lekhak.command('imageAlign','center');});
 json=await h.page.evaluate(()=>window.Lekhak.getJSON());assert.equal(json.content[0].attrs.width,75);assert.equal(json.content[0].attrs.align,'center');
 await h.page.evaluate(()=>window.Lekhak.command('deleteImage'));assert.ok(!(await h.page.evaluate(()=>window.Lekhak.getJSON())).content.some(n=>n.type==='image'));
 await h.page.evaluate(()=>window.Lekhak.command('undo'));assert.ok((await h.page.evaluate(()=>window.Lekhak.getJSON())).content.some(n=>n.type==='image'));
 // Native protocol: one chunk at a time, sequential acknowledgements, exact byte count.
 const stream=await h.page.evaluate(async({settings,samples})=>{
  const messages=[],chunks=[];window.LekhakBridge={postMessage(raw){const m=JSON.parse(raw);messages.push({...m,data:undefined});if(m.type==='pdfChunk'){chunks.push(m.data);setTimeout(()=>window.Lekhak.ack(m.index),1);}}};
  await window.Lekhak.boot({schemaVersion:1,page:settings,renderOnly:true,content:{type:'doc',content:samples.map(text=>({type:'paragraph',content:[{type:'text',text}]}))}});
  return{messages,chunks};
 },{settings,samples});
 const bytes=Buffer.concat(stream.chunks.map(x=>Buffer.from(x,'base64')));assert.equal(bytes.length,stream.messages.find(m=>m.type==='pdfBegin').length);assert.equal(stream.messages.at(-1).type,'pdfEnd');assert.equal(stream.messages.at(-1).chunks,stream.chunks.length);await writeFile(out+'/native-stream.pdf',bytes);
 await assert.rejects(()=>exportCase('missing-asset',[{type:'image',attrs:{assetId:'22222222-2222-2222-2222-222222222222.png',src:'/document-assets/missing',width:50}}]),/missing|unreadable/);
 await assert.rejects(()=>exportCase('oversized-row',[{type:'table',content:[{type:'tableRow',content:[cell(samples.join(' ').repeat(50))]}]}]),/row is taller/);
 assert.deepEqual(errors,[]);
 await writeFile(out+'/hardening-browser.json',JSON.stringify({browser:await h.browser.version(),results,commands:'pass',nativeStream:{bytes:bytes.length,chunks:stream.chunks.length},missingAsset:'rejected',oversizedRow:'rejected',errors},null,2));
}finally{await h.close();}
