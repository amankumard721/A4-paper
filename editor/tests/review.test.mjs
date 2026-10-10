import {harness,settings} from './harness.mjs';
import assert from 'node:assert/strict';
import {writeFile} from 'node:fs/promises';
const h=await harness();
try {
 const id='33333333-3333-3333-3333-333333333333.jpg';
 const jpg=await h.page.evaluate(()=>{const c=document.createElement('canvas');c.width=320;c.height=200;const x=c.getContext('2d');x.fillStyle='#99aabb';x.fillRect(0,0,320,200);return c.toDataURL('image/jpeg',.85).split(',')[1];});
 await h.page.route('**/document-assets/'+id,r=>r.fulfill({contentType:'image/jpeg',body:Buffer.from(jpg,'base64')}));
 const token='44444444-4444-4444-4444-444444444444';
 const payload={schemaVersion:1,page:settings,content:{type:'doc',content:[{type:'paragraph',content:[{type:'text',text:'JPEG photo and streamed document'}]},{type:'image',attrs:{assetId:id,width:50,align:'center'}}]}};
 await h.page.route('**/boot-payload/'+token,r=>r.fulfill({contentType:'application/json',body:JSON.stringify(payload)}));
 await h.page.evaluate(token=>window.dispatchEvent(new MessageEvent('message',{origin:'',data:JSON.stringify({type:'boot-url',url:location.origin+'/boot-payload/'+token})})),token);
 for(let i=0;i<100;i++){if(await h.page.evaluate(()=>window.__messages?.some(m=>m.type==='ready')))break;await new Promise(r=>setTimeout(r,50));}
 assert.ok(await h.page.evaluate(()=>window.__messages?.some(m=>m.type==='ready')));
 assert.equal(await h.page.locator('#editor img').count(),1);
 const result=await h.page.evaluate(async()=>{const r=await window.Lekhak.exportPdf();return {pages:r.pages,bytes:[...r.bytes]};});
 assert.equal(result.pages,1);
 assert.ok(Buffer.from(result.bytes).includes(Buffer.from('/DCTDecode')),'JPEG embedded directly without PNG conversion');
 await writeFile('../validation/audit/jpeg-streamed-boot.pdf',Buffer.from(result.bytes));
 await writeFile('../validation/audit/review-browser.json',JSON.stringify({jpegEmbeddedDirectly:true,streamedBoot:true,pages:result.pages,bytes:result.bytes.length,browser:await h.browser.version()},null,2));
 await h.page.evaluate(settings=>window.Lekhak.boot({schemaVersion:1,page:settings,language:'hi',content:{type:'doc',content:[{type:'paragraph'}]}}),settings);
 const textbox=h.page.getByRole('textbox',{name:'दस्तावेज़ संपादक',exact:true});
 assert.equal(await textbox.count(),1);
 assert.equal(await textbox.getAttribute('aria-multiline'),'true');
 assert.equal(await textbox.getAttribute('lang'),'hi');
 assert.equal(await textbox.getAttribute('aria-describedby'),'editor-help');
 assert.equal(await h.page.locator('#measure').getAttribute('aria-hidden'),'true');
 console.log('PASS: streamed boot, direct JPEG embedding, Hindi editor accessibility attributes');
} finally {await h.close();}

