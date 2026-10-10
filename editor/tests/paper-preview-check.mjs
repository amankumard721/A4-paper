import {harness,settings} from './harness.mjs';
const h=await harness();
try{
 await h.page.setViewportSize({width:360,height:480});
 await h.page.evaluate(settings=>window.Lekhak.boot({schemaVersion:1,page:settings,content:{type:'doc',content:[{type:'paragraph',content:[{type:'text',text:'Application for leave'}]},{type:'paragraph',content:[{type:'text',text:'सेवा में, प्रधानाचार्य महोदय'}]},{type:'paragraph',content:[{type:'text',text:'This white page is ready for your next document.'}]}]}}),settings);
 await h.page.screenshot({path:'../validation/pdf-paper-editor.png'});
 await h.page.evaluate(()=>window.Lekhak.theme(true));
 await h.page.screenshot({path:'../validation/pdf-paper-editor-dark.png'});
}finally{await h.close();}
