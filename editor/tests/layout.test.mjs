import { chromium } from 'playwright';
import { createServer } from 'node:http';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { resolve, extname } from 'node:path';
import assert from 'node:assert/strict';
const root=resolve('../app/src/main/assets/editor');
const out=resolve('../validation');await mkdir(out,{recursive:true});
const server=createServer(async(req,res)=>{try{const file=resolve(root,'.'+decodeURIComponent(req.url.split('?')[0]==='/'?'/index.html':req.url.split('?')[0]));if(!file.startsWith(root))throw Error();res.setHeader('Content-Type',({'.html':'text/html','.js':'application/javascript','.css':'text/css','.ttf':'font/ttf'})[extname(file)]||'text/plain');res.end(await readFile(file));}catch{res.statusCode=404;res.end();}});
await new Promise(r=>server.listen(0,'127.0.0.1',r));
const browser=await chromium.launch({executablePath:process.env.CHROME_PATH||'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
const page=await browser.newPage({viewport:{width:1000,height:1000}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
const settings={landscape:false,marginTop:15,marginRight:15,marginBottom:15,marginLeft:15,fontFamily:'Noto Sans',fontSize:12,lineHeight:1.5,paragraphSpacing:8};
const hindi='हिंदी लेखन परीक्षण: क्षत्रिय, प्रार्थना, विद्यालय, दृष्टि, श्रद्धा, राष्ट्र, स्वास्थ्य, स्वतंत्रता, कृपया, फ़ाइल।';
const mixed=hindi+' A4 Lekhak preserves Hindi and English together. The quick brown fox jumps over the lazy dog. ';
const paragraph=(text,marks=[],attrs={})=>({type:'paragraph',attrs,content:text?[{type:'text',text,marks}]:[]});
const cases=[
  {name:'mixed-script',content:[paragraph('A4 Lekhak — हिंदी और English',[{type:'bold'}],{textAlign:'center'}),paragraph(hindi),paragraph('Bold, italic and underline',[{type:'bold'},{type:'italic'},{type:'underline'}]),paragraph(mixed,[{type:'textStyle',attrs:{fontSize:'16pt',color:'#2563eb'}}]),paragraph('Right aligned text',[],{textAlign:'right'})]},
  {name:'long-paragraph',content:[paragraph(mixed.repeat(100))]},
  {name:'ten-plus-pages',content:Array.from({length:180},(_,i)=>paragraph(`${i+1}. ${mixed.repeat(2)}`))},
  {name:'manual-breaks',content:[{type:'pageBreak'},paragraph('First page '+hindi),{type:'pageBreak'},{type:'pageBreak'},paragraph('Second page '+hindi),{type:'pageBreak'}],expected:2},
  {name:'landscape',content:Array.from({length:18},()=>paragraph(mixed.repeat(2))),page:{...settings,landscape:true,marginTop:20,marginRight:25,marginBottom:20,marginLeft:25}},
  {name:'lists',content:[{type:'orderedList',attrs:{start:3},content:Array.from({length:25},(_,i)=>({type:'listItem',content:[paragraph(`${i+3}. ${mixed.repeat(i===3?20:1)}`)]}))}]},
  {name:'empty',content:[paragraph('')],expected:1}
];
const results=[];
try {
 for(const test of cases){
  await page.goto(`http://127.0.0.1:${server.address().port}/`);await page.waitForFunction(()=>!!window.Lekhak);
  const content={type:'doc',content:test.content};
  await page.evaluate(async p=>{await window.Lekhak.boot(p);await window.Lekhak.mode('pages');}, {schemaVersion:1,content,page:test.page||settings});
  const layout=await page.evaluate(()=>({pages:document.querySelectorAll('.page').length,text:[...document.querySelectorAll('.page-content')].map(e=>e.textContent).join(''),overflow:[...document.querySelectorAll('.page-content')].some(e=>e.scrollHeight>e.clientHeight+2||e.scrollWidth>e.clientWidth+2),fonts:document.fonts.check('12px "Noto Devanagari"')}));
  const expectedText=await page.evaluate(()=>{const d=document.createElement('div');d.innerHTML=window.Lekhak.getHTML();return d.textContent;});
  assert.equal(layout.text,expectedText,`${test.name}: lost/duplicated text`);assert.equal(layout.overflow,false,`${test.name}: overflow`);assert.equal(layout.fonts,true);
  if(test.expected)assert.equal(layout.pages,test.expected);
  if(test.name==='ten-plus-pages')assert.ok(layout.pages>=10);
  await page.pdf({path:resolve(out,`${test.name}.pdf`),preferCSSPageSize:true,printBackground:true,displayHeaderFooter:false,margin:{top:0,right:0,bottom:0,left:0}});
  if(test.name==='mixed-script'){await page.screenshot({path:resolve(out,'page-preview.png'),fullPage:true});}
  results.push({case:test.name,...layout,text:undefined});console.log(`${test.name}: ${layout.pages} preview pages, no text loss or clipping`);
 }
 // Editing/formatting/JSON reopen roundtrip through the real editor.
 await page.evaluate(async p=>{await window.Lekhak.boot(p);await window.Lekhak.command('selectAll');await window.Lekhak.command('bold');await window.Lekhak.command('align','center');await window.Lekhak.command('fontSize','18');},{schemaVersion:1,content:{type:'doc',content:[paragraph(mixed)]},page:settings});
 const before=await page.evaluate(()=>window.Lekhak.getJSON());
 assert.ok(before.content[0].content[0].marks.some(m=>m.type==='bold'));
 assert.equal(before.content[0].attrs.textAlign,'center');
 await page.evaluate(async p=>window.Lekhak.boot(p),{schemaVersion:1,content:before,page:settings});
 assert.deepEqual(await page.evaluate(()=>window.Lekhak.getJSON()),before);
 await page.evaluate(()=>window.Lekhak.command('paste','<script>alert(1)</script>'));
 assert.ok((await page.evaluate(()=>window.Lekhak.getHTML())).includes('&lt;script&gt;'));
 assert.deepEqual(errors,[]);
 await writeFile(resolve(out,'browser-results.json'),JSON.stringify({browser:await browser.version(),results,roundtrip:'passed',pasteSafety:'passed',errors},null,2));
}finally{await browser.close();server.close();}
