import {harness} from './harness.mjs';
import assert from 'node:assert/strict';
import {readFile, mkdir, writeFile} from 'node:fs/promises';
// Fixture is produced by DocxReaderTest from an actual zipped Word package.
const document=JSON.parse(await readFile('../app/build/docx-fixture.json','utf8'));
const h=await harness(),p=h.page,errors=[];
p.on('pageerror',error=>errors.push(error.message));
try{
 const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=','base64');
 await p.route('**/document-assets/*.png',r=>r.fulfill({contentType:'image/png',body:png}));
 await p.evaluate(data=>window.Lekhak.boot(data),document);
 assert.match(await p.locator('.tiptap').innerText(),/Word → PDF नमस्ते/);
 assert.equal(await p.locator('.tiptap ol').getAttribute('start'),'3');
 assert.equal(await p.locator('.tiptap td[rowspan="2"]').count(),1);
 assert.equal(await p.locator('.tiptap img').count(),1);
 const snapshot=await p.evaluate(()=>window.Lekhak.getJSON());
 await p.evaluate(data=>window.Lekhak.boot(data),{...document,content:snapshot});
 assert.deepEqual(await p.evaluate(()=>window.Lekhak.getJSON()),snapshot);
 const result=await p.evaluate(async()=>{const pdf=await window.Lekhak.exportPdf();return {pages:pdf.pages,bytes:Array.from(pdf.bytes)};});
 assert.ok(result.pages>=1&&result.bytes.length>1000);
 assert.equal(Buffer.from(result.bytes).subarray(0,4).toString(),'%PDF');
 await mkdir('../validation/word-import',{recursive:true});
 await writeFile('../validation/word-import/imported-word.pdf',Buffer.from(result.bytes));
 assert.deepEqual(errors,[]);
 console.log('PASS: native DOCX conversion → editor schema → save/reload → PDF, including Hindi, lists, merged table and image');
}finally{await h.close();}
