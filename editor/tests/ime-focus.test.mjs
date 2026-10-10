import {harness,settings} from './harness.mjs';
import assert from 'node:assert/strict';
const h=await harness();
try{
 await h.page.evaluate(settings=>window.Lekhak.boot({schemaVersion:1,page:settings,content:{type:'doc',content:[{type:'paragraph',content:[{type:'text',text:'abcd'}]}]}}),settings);
 await h.page.locator('.tiptap').click();await h.page.keyboard.press('Home');await h.page.keyboard.press('ArrowRight');await h.page.keyboard.press('ArrowRight');
 const before=await h.page.evaluate(()=>({json:window.Lekhak.getJSON(),offset:window.getSelection().anchorOffset}));
 await h.page.evaluate(()=>document.querySelector('.tiptap').blur());
 assert.equal(await h.page.evaluate(()=>window.Lekhak.restoreInputFocus()),true);
 assert.deepEqual(await h.page.evaluate(()=>window.Lekhak.getJSON()),before.json);
 assert.equal(await h.page.evaluate(()=>window.getSelection().anchorOffset),before.offset);
 await h.page.keyboard.insertText('क');assert.match(JSON.stringify(await h.page.evaluate(()=>window.Lekhak.getJSON())),/abकcd/);
 await h.page.evaluate(()=>window.Lekhak.mode('pages'));assert.equal(await h.page.evaluate(()=>window.Lekhak.restoreInputFocus()),false);
 console.log('PASS: focus recovery preserves caret/content, accepts Hindi, and does not focus page preview');
}finally{await h.close();}
