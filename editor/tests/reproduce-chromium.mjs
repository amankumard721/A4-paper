// Diagnostic comparator only. Production exports use exportPdf(), never page.pdf().
import {harness,settings} from './harness.mjs';
import {readFile} from 'node:fs/promises';
const h=await harness();
try{
 const samples=JSON.parse(await readFile('../validation/audit/hindi-input.json','utf8'));
 await h.page.evaluate(async({samples,settings})=>{await window.Lekhak.boot({schemaVersion:1,page:settings,content:{type:'doc',content:samples.map(text=>({type:'paragraph',content:[{type:'text',text}]}))}});await window.Lekhak.mode('pages');},{samples,settings});
 await h.page.pdf({path:'../validation/audit/chromium-comparator.pdf',preferCSSPageSize:true,printBackground:true});
}finally{await h.close();}
