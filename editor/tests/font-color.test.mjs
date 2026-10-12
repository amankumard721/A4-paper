import {harness,settings} from './harness.mjs';
import assert from 'node:assert/strict';
const h=await harness();
const currentColor=()=>h.page.evaluate(()=>window.__messages.filter(x=>x.type==='selection').at(-1).color);
try {
 await h.page.evaluate(settings=>window.Lekhak.boot({schemaVersion:1,page:settings,content:{type:'doc',content:[{type:'paragraph',content:[{type:'text',text:'abcd'}]}]}}),settings);
 assert.equal(await currentColor(),'#111827');
 await h.page.evaluate(()=>window.Lekhak.command('selectAll'));
 await h.page.evaluate(()=>document.activeElement.blur());
 await h.page.evaluate(()=>window.Lekhak.command('color','#ff0000'));
 assert.equal(await currentColor(),'#ff0000');
 const json=await h.page.evaluate(()=>window.Lekhak.getJSON());
 assert.equal(json.content[0].content[0].marks.find(m=>m.type==='textStyle').attrs.color,'#ff0000');
 await h.page.keyboard.press('ArrowRight');
 await h.page.evaluate(()=>window.Lekhak.command('color','#2563eb'));
 await h.page.keyboard.insertText('क');
 assert.equal(await currentColor(),'#2563eb');
 const typed=await h.page.evaluate(()=>window.Lekhak.getJSON());
 assert.equal(typed.content[0].content.at(-1).text,'क');
 assert.equal(typed.content[0].content.at(-1).marks.find(m=>m.type==='textStyle').attrs.color,'#2563eb');
 await h.page.evaluate(()=>window.Lekhak.command('clear'));
 assert.equal(await currentColor(),'#111827');
 console.log('PASS: selected text keeps selection across picker blur, new typing takes chosen color, toolbar state follows color/reset');
} finally {await h.close();}
