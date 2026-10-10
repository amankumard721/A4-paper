import { harness,settings } from './harness.mjs';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
const samples=JSON.parse(await readFile('../validation/audit/hindi-input.json','utf8'));
const h=await harness();const errors=[];h.page.on('pageerror',e=>errors.push(e.message));
try{
 const content={type:'doc',content:samples.map(text=>({type:'paragraph',content:[{type:'text',text}]}))};
 await h.page.evaluate(async payload=>window.Lekhak.boot(payload),{schemaVersion:1,page:settings,content});
 const result=await h.page.evaluate(async()=>{const r=await window.Lekhak.exportPdf();return{pages:r.pages,milliseconds:r.milliseconds,data:Array.from(r.bytes)};});
 await writeFile('../validation/audit/vector-integration.pdf',new Uint8Array(result.data));
 console.log(JSON.stringify({pages:result.pages,milliseconds:result.milliseconds,errors}));
}finally{await h.close();}
