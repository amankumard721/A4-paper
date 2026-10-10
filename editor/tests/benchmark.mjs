import {harness,settings} from './harness.mjs';
import {writeFile,mkdir} from 'node:fs/promises';
const h=await harness(),results=[];
try{
 for(const pages of [1,5,20,50,100]){
  const result=await h.page.evaluate(async({pages,settings})=>{
   window.LekhakBridge={postMessage:()=>{}};
   const text='प्रधानाचार्य महोदय। कृपया मेरा आवेदन स्वीकार करें। हिन्दी और English दोनों। विद्यालय, क्षेत्रीय कार्यालय, शिक्षक प्रशिक्षण। ';
   const blocks=[];for(let p=0;p<pages;p++){if(p)blocks.push({type:'pageBreak'});for(let j=0;j<5;j++)blocks.push({type:'paragraph',content:[{type:'text',text:`${p+1}.${j+1} `+text.repeat(2)}]});}
   await window.Lekhak.boot({schemaVersion:1,page:settings,content:{type:'doc',content:blocks}});
   const typing=[];for(let i=0;i<20;i++){const t=performance.now();await window.Lekhak.command('date');typing.push(performance.now()-t);}
   const t=performance.now();await window.Lekhak.mode('pages');const cold=performance.now()-t;
   const warmStart=performance.now();await window.Lekhak.preview();const warm=performance.now()-warmStart;
   await window.Lekhak.mode('write');await window.Lekhak.command('date');const editStart=performance.now();await window.Lekhak.mode('pages');const changed=performance.now()-editStart;
   typing.sort((a,b)=>a-b);
   return {requestedPages:pages,actualPages:document.querySelectorAll('.page').length,coldMs:cold,cachedMs:warm,oneEditMs:changed,commandP95Ms:typing[Math.floor(typing.length*.95)],heapBytes:performance.memory?.usedJSHeapSize??null};
  },{pages,settings});results.push(result);console.log(JSON.stringify(result));
 }
 await mkdir('../validation/audit',{recursive:true});await writeFile(`../validation/audit/benchmark-${process.argv[2]||'current'}.json`,JSON.stringify({browser:await h.browser.version(),note:'Desktop Chromium. JS command duration, not Android IME latency. Five long paragraphs per explicit page.',results},null,2));
}finally{await h.close();}
