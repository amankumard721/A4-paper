import { chromium } from 'playwright';
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { resolve, extname } from 'node:path';
export const settings={landscape:false,marginTop:15,marginRight:15,marginBottom:15,marginLeft:15,fontFamily:'Noto Sans',fontSize:12,lineHeight:1.5,paragraphSpacing:8};
export async function harness(){
 const root=resolve('../app/src/main/assets/editor');
 const server=createServer(async(req,res)=>{try{const file=resolve(root,'.'+decodeURIComponent(req.url==='/'?'/index.html':req.url));if(!file.startsWith(root))throw Error();res.setHeader('Content-Type',({'.html':'text/html','.js':'application/javascript','.css':'text/css','.ttf':'font/ttf','.wasm':'application/wasm','.png':'image/png'})[extname(file)]||'text/plain');res.end(await readFile(req.url==='/editor.js'&&process.env.EDITOR_BUNDLE?process.env.EDITOR_BUNDLE:file));}catch{res.statusCode=404;res.end();}});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const browser=await chromium.launch({executablePath:process.env.CHROME_PATH||'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
 const page=await browser.newPage({viewport:{width:1000,height:1000}});
 await page.goto(`http://127.0.0.1:${server.address().port}/`);await page.waitForFunction(()=>!!window.Lekhak);
 return {page,browser,close:async()=>{await browser.close();server.close();}};
}
