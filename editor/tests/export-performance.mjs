import {harness,settings} from './harness.mjs';
import {writeFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
const h=await harness(),results=[];
try{
 const session=await h.page.context().newCDPSession(h.page);await session.send('Performance.enable');
 for(const count of [1,5,20,50,100]){
  const result=await h.page.evaluate(async({count,settings})=>{
   const blocks=[],text='प्रधानाचार्य महोदय। विद्यालय, क्षेत्रीय कार्यालय। कृपया मेरा आवेदन स्वीकार करें। हिन्दी और English दोनों। ';
   for(let i=0;i<count;i++){if(i)blocks.push({type:'pageBreak'});for(let j=0;j<5;j++)blocks.push({type:'paragraph',content:[{type:'text',text:`${i+1}.${j+1} `+text.repeat(2)}]});}
   window.LekhakBridge={postMessage:()=>{}};
   await window.Lekhak.boot({schemaVersion:1,page:settings,content:{type:'doc',content:blocks}});
   const longTasks=[];const observer=new PerformanceObserver(list=>longTasks.push(...list.getEntries().map(e=>e.duration)));observer.observe({type:'longtask'});
   const before=performance.memory?.usedJSHeapSize;const t=performance.now();const pdf=await window.Lekhak.exportPdf();const elapsed=performance.now()-t;
   await new Promise(r=>setTimeout(r,20));observer.disconnect();
   return{requestedPages:count,pages:pdf.pages,totalMs:elapsed,vectorExportMs:pdf.milliseconds,pdfBytes:pdf.bytes.length,longTaskCount:longTasks.length,maxLongTaskMs:Math.max(0,...longTasks),heapBefore:before,heapAfter:performance.memory?.usedJSHeapSize,bytes:count===100?[...pdf.bytes]:undefined};
  },{count,settings});
  assert.equal(result.pages,count);if(result.bytes){await writeFile('../validation/audit/100-page-export.pdf',Buffer.from(result.bytes));delete result.bytes;}
  const {metrics}=await session.send('Performance.getMetrics');result.browserMetrics=Object.fromEntries(metrics.filter(m=>['LayoutCount','LayoutDuration','RecalcStyleCount','RecalcStyleDuration','JSHeapUsedSize','JSHeapTotalSize'].includes(m.name)).map(m=>[m.name,m.value]));results.push(result);console.log(JSON.stringify(result));
 }
 await writeFile('../validation/audit/export-performance.json',JSON.stringify({browser:await h.browser.version(),note:'Desktop Chrome, single run. Long tasks >=50 ms. Heap and cumulative CDP counters are not Android WebView/process RSS or GC measurements.',results},null,2));
}finally{await h.close();}
