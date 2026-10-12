import {harness,settings} from './harness.mjs';
import assert from 'node:assert/strict';
const h=await harness();
const context=await h.browser.newContext({viewport:{width:360,height:640},isMobile:true,hasTouch:true});
try {
 const page=await context.newPage();await page.goto(h.page.url());
 await page.waitForFunction(()=>!!window.Lekhak);
 await page.evaluate(settings=>window.Lekhak.boot({schemaVersion:1,page:settings,content:{type:'doc',content:[{type:'paragraph',content:[{type:'text',text:'Pinch zoom — हिंदी typing'}]}]}}),settings);
 const read=()=>page.evaluate(()=>({json:window.Lekhak.getJSON(),font:getComputedStyle(document.querySelector('.tiptap')).fontSize,width:document.querySelector('.tiptap').clientWidth}));
 const before=await read();const layoutBefore=await page.evaluate(()=>window.Lekhak.preview());
 const cdp=await context.newCDPSession(page);
 // Headless Chrome does not reliably dispatch native Android pinch gestures.
 // Emulate their viewport scaling to verify content/layout invariants.
 await cdp.send('Emulation.setPageScaleFactor',{pageScaleFactor:2});
 assert.ok(await page.evaluate(()=>visualViewport.scale>1.5), "pinch must magnify viewport");
 const zoomIn=await page.evaluate(()=>visualViewport.scale);
 assert.deepEqual(await read(),before,'zoom must not modify text, font size or layout width');
 assert.deepEqual(await page.evaluate(()=>window.Lekhak.preview()),layoutBefore,'pagination must stay unchanged');
 await cdp.send('Emulation.setPageScaleFactor',{pageScaleFactor:1});
 assert.ok(await page.evaluate(()=>visualViewport.scale)<zoomIn, "pinch must shrink viewport");
 assert.deepEqual(await read(),before);
 assert.equal(await page.evaluate(()=>window.Lekhak.restoreInputFocus()),true);
 await page.keyboard.insertText('क');
 assert.match(JSON.stringify(await page.evaluate(()=>window.Lekhak.getJSON())),/क/);
 console.log('PASS: mobile-browser viewport zoom in/out (Android pinch requires device check); document/font/pagination unchanged; Hindi input works afterwards');
} finally {await context.close();await h.close();}
