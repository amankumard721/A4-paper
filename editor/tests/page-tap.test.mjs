import {harness,settings} from './harness.mjs';
import assert from 'node:assert/strict';
const h=await harness();
try {
 for(const height of [720,320]) {
  await h.page.setViewportSize({width:360,height});
  await h.page.evaluate(settings=>window.Lekhak.boot({schemaVersion:1,page:settings,content:{type:'doc',content:[{type:'paragraph'}]}}),settings);
  for(const [x,y] of [[3,3],[357,3],[3,height-3],[357,height-3],[180,height-30]]) {
   await h.page.evaluate(()=>document.activeElement.blur());
   await h.page.mouse.click(x,y);
   assert.equal(await h.page.evaluate(()=>document.activeElement===document.querySelector('.tiptap')),true,`focus ${x},${y} at height ${height}`);
  }
  await h.page.keyboard.insertText('Hello');
  assert.match(JSON.stringify(await h.page.evaluate(()=>window.Lekhak.getJSON())),/Hello/);
  const before=await h.page.evaluate(()=>window.Lekhak.getJSON());
  await h.page.mouse.click(3,height-3);
  assert.deepEqual(await h.page.evaluate(()=>window.Lekhak.getJSON()),before,'blank tap must not insert spaces or paragraphs');
  await h.page.evaluate(()=>window.Lekhak.mode('pages'));
  await h.page.mouse.click(180,100);
  assert.equal(await h.page.evaluate(()=>document.activeElement===document.querySelector('.tiptap')),false);
 }
 console.log('PASS: white-page corners and center focus at phone/keyboard heights; typing works; no content mutation on tap; preview remains read-only');
} finally { await h.close(); }
