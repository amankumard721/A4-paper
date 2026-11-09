import { searchExtension, searchCommand } from './search.js';
import { Highlight } from './highlight.js';
import { Editor, Extension, Node, getSchema } from '@tiptap/core';
import { selectedTable } from './resize.js';
import { Selection } from '@tiptap/pm/state';
import { closeHistory } from '@tiptap/pm/history';
import { mediaExtensions, assetPattern, assetUrl } from './media.js';
import StarterKit from '@tiptap/starter-kit';
import Underline from '@tiptap/extension-underline';
import TextStyle from '@tiptap/extension-text-style';
import Color from '@tiptap/extension-color';
import FontFamily from '@tiptap/extension-font-family';
import TextAlign from '@tiptap/extension-text-align';
import { paginate, applySettings } from './pagination.js';
import { createVectorPdf } from './vector-pdf.js';

const PageBreak=Node.create({name:'pageBreak',group:'block',atom:true,parseHTML:()=>[{tag:'hr[data-page-break]'}],renderHTML:()=>['hr',{'data-page-break':'true'}]});
const Typography=Extension.create({name:'typography',addGlobalAttributes(){return [
  {types:['textStyle'],attributes:{fontSize:{default:null,parseHTML:e=>e.style.fontSize,renderHTML:a=>a.fontSize?{style:`font-size:${a.fontSize}`}:{}}}},
  {types:['paragraph','heading'],attributes:{lineHeight:{default:null,parseHTML:e=>e.style.lineHeight,renderHTML:a=>a.lineHeight?{style:`line-height:${a.lineHeight}`}:{}} ,paragraphSpacing:{default:null,parseHTML:e=>e.style.marginBottom,renderHTML:a=>a.paragraphSpacing?{style:`margin-bottom:${a.paragraphSpacing}`}:{}}}}
];}});
let editor,settings,revision=0,mode='write',savedSelection=null,layoutTail=Promise.resolve(),layoutKey='',layoutResult=null;
let chunkAck=null;
const send=(type,data={})=>{const message=JSON.stringify({type,...data});if(window.LekhakBridge)window.LekhakBridge.postMessage(message);else window.__messages=(window.__messages||[]).concat(JSON.parse(message));};
const report=()=>send('change',{content:editor.getJSON(),revision:++revision});
function currentTextColor(){
  const marks=editor.state.storedMarks ?? (editor.state.selection.empty ? editor.state.selection.$from.marks() : null);
  return (marks ? marks.find(mark=>mark.type.name==='textStyle')?.attrs.color : editor.getAttributes('textStyle').color)||'#111827';
}
let metricsDoc=null,metrics=null;
function documentMetrics(){
  if(metricsDoc===editor.state.doc)return metrics;
  metricsDoc=editor.state.doc;
  const docText=metricsDoc.textBetween(0,metricsDoc.content.size,'\n',' ')||'';
  const trimmed=docText.trim();
  const wordCount=trimmed?trimmed.split(/\s+/).length:0;
  const charCount=docText.length;
  let paragraphCount=0;
  editor.state.doc.descendants(n=>{if(n.type.name==='paragraph'||n.type.name==='heading')paragraphCount++;});
  if(!paragraphCount&&charCount)paragraphCount=1;
  metrics={wordCount,charCount,paragraphCount};return metrics;
}
function state(){
  const {wordCount,charCount,paragraphCount}=documentMetrics();
  const heading=editor.isActive('heading',{level:1})?1:editor.isActive('heading',{level:2})?2:editor.isActive('heading',{level:3})?3:0;
  const align=editor.isActive({textAlign:'center'})?'center':editor.isActive({textAlign:'right'})?'right':editor.isActive({textAlign:'justify'})?'justify':'left';
  const textAttrs=editor.getAttributes('textStyle');
  const paraAttrs=editor.getAttributes('paragraph');
  const headAttrs=editor.getAttributes('heading');
  const fontSize=parseInt(textAttrs.fontSize||settings.fontSize||'12',10)||12;
  const fontFamily=(textAttrs.fontFamily||settings.fontFamily||'Noto Sans').replace(/['"]/g,'').split(',')[0].trim();
  const lineHeight=parseFloat(paraAttrs.lineHeight||headAttrs.lineHeight||settings.lineHeight||1.5)||1.15;
  const paragraphSpacing=parseInt(paraAttrs.paragraphSpacing??headAttrs.paragraphSpacing??settings.paragraphSpacing??'0',10)||0;
  send('selection',{
    bold:editor.isActive('bold'),
    italic:editor.isActive('italic'),
    underline:editor.isActive('underline'),
    strike:editor.isActive('strike'),
    color:currentTextColor(),
    highlight:editor.getAttributes('highlight').color||'',
    align,
    heading,
    bulletList:editor.isActive('bulletList'),
    orderedList:editor.isActive('orderedList'),
    fontSize,
    fontFamily,
    lineHeight,
    paragraphSpacing,
    wordCount,
    charCount,
    paragraphCount,
    isTable:!!selectedTable(editor.state),
    canMerge:!!editor.state.selection.$anchorCell&&!!editor.state.selection.$headCell&&editor.state.selection.$anchorCell.start(-1)===editor.state.selection.$headCell.start(-1)&&editor.can().mergeCells(),
    canSplit:editor.can().splitCell(),
    isImage:editor.state.selection.node?.type.name==='image',
    imageWidth:editor.getAttributes('image').width||50,
    imageAlign:editor.getAttributes('image').align||'left',
    imageRotation:editor.getAttributes('image').rotation||0,
    imageAspectLocked:editor.getAttributes('image').aspectLocked!==false,
    cellBorder:editor.getAttributes('tableCell').borderWidth??editor.getAttributes('tableHeader').borderWidth??1,
    canUndo:editor.can().undo(),
    canRedo:editor.can().redo()
  });
}
const extensions=[StarterKit.configure({codeBlock:false,code:false,horizontalRule:false,blockquote:false}),Underline,TextStyle,Color,FontFamily,TextAlign.configure({types:['heading','paragraph']}),Typography,PageBreak,Highlight,searchExtension(send),...mediaExtensions];
function sanitizePaste(html){const d=new DOMParser().parseFromString(html,'text/html');d.querySelectorAll('script,iframe,img,svg,object,embed,style,link,meta').forEach(e=>e.remove());d.querySelectorAll('*').forEach(e=>{for(const a of [...e.attributes])if(a.name.startsWith('on')||['href','src','srcset'].includes(a.name))e.removeAttribute(a.name);});return d.body.innerHTML;}
function preview(){const html=editor.getHTML(),page={...settings};const key=html+JSON.stringify(page);const next=layoutTail.then(async()=>{if(key!==layoutKey){layoutResult=await paginate(html,page);layoutKey=key;}send('layout',layoutResult);return layoutResult;});layoutTail=next.catch(()=>{});return next;}
window.Lekhak={
  async boot(payload){
    if(payload.schemaVersion!==1)throw new Error('Unsupported document schema');getSchema(extensions).nodeFromJSON(payload.content).check();settings=payload.page;
    document.documentElement.lang=payload.language||'en';document.getElementById('editor-help').textContent=payload.language==='hi'?'दस्तावेज़ संपादक। लिखने के लिए सक्रिय करें। पेज मोड केवल पूर्वावलोकन है।':'Document editor. Activate to edit text. Page mode is a read-only preview.';document.getElementById('pages').setAttribute('aria-label',payload.language==='hi'?'A4 पेज पूर्वावलोकन':'A4 page preview');document.body.classList.toggle('dark',!!payload.dark);applySettings(settings);
    editor?.destroy();editor=new Editor({element:document.getElementById('editor'),extensions,content:payload.content,autofocus:false,
      editorProps:{attributes:{role:'textbox','aria-multiline':'true','aria-describedby':'editor-help','tabindex':'0','lang':payload.language||'en','aria-label':payload.language==='hi'?'दस्तावेज़ संपादक':'Document editor',spellcheck:'true'},transformPastedHTML:sanitizePaste},
      onUpdate:()=>{report();state();},onSelectionUpdate:()=>{savedSelection=editor.state.selection.toJSON();state();}});
    savedSelection=null;revision=0;layoutKey='';mode='write';document.body.classList.remove('pages');await document.fonts.ready;send('ready');state();
    if(payload.renderOnly){
      const result=await this.exportPdf();send('pdfBegin',{pages:result.pages,length:result.bytes.length});
      let index=0;for(let offset=0;offset<result.bytes.length;offset+=98304){const chunk=result.bytes.subarray(offset,offset+98304);let binary='';for(let i=0;i<chunk.length;i+=8192)binary+=String.fromCharCode(...chunk.subarray(i,i+8192));await new Promise((resolve,reject)=>{const timer=setTimeout(()=>{chunkAck=null;reject(Error('PDF storage did not respond.'))},30000);chunkAck={index,resolve:()=>{clearTimeout(timer);resolve();}};send('pdfChunk',{index,data:btoa(binary)});});index++;}send('pdfEnd',{chunks:index});
    }
  },
  review(enabled){
    if(!editor || editor.isDestroyed)return false;
    mode=enabled?'review':'write';
    if(enabled)editor.commands.blur();
    editor.setEditable(!enabled,false);
    document.body.classList.remove('pages');
    return true;
  },
  restoreInputFocus(){
    if(!editor || mode!=='write' || editor.isDestroyed)return false;
    // ProseMirror restores its current selection without rebuilding the document or undo history.
    editor.view.focus();
    return document.activeElement===editor.view.dom;
  },
  async command(name,value){
    if(!editor)return;
    if(searchCommand(editor,name,value,mode==='write'))return;
    if(mode!=='write')return;
    if(savedSelection){
      const tr=editor.state.tr;
      // Resolve against this transaction's document (table normalization may have changed it).
      try{tr.setSelection(Selection.fromJSON(tr.doc,savedSelection));editor.view.dispatch(tr);}
      catch(error){if(!(error instanceof RangeError))throw error;savedSelection=editor.state.selection.toJSON();}
    }
    if(['image','deleteImage','table','deleteTable'].includes(name))editor.view.dispatch(closeHistory(editor.state.tr));
    let chain=editor.chain().focus();
    switch(name){
      case 'bold':chain.toggleBold().run();break;case 'italic':chain.toggleItalic().run();break;case 'underline':chain.toggleUnderline().run();break;case 'strike':chain.toggleStrike().run();break;
      case 'undo':chain.undo().run();break;case 'redo':chain.redo().run();break;
      case 'bulletList':chain.toggleBulletList().run();break;case 'orderedList':chain.toggleOrderedList().run();break;
      case 'heading':if(+value>=1&&+value<=3)chain.setHeading({level:+value}).run();else chain.setParagraph().run();break;
      case 'align':if(['left','center','right','justify'].includes(value))chain.setTextAlign(value).run();break;
      case 'fontSize':if(+value>=8&&+value<=72)chain.setMark('textStyle',{fontSize:`${+value}pt`}).run();break;
      case 'fontFamily':if(['Noto Sans','Noto Serif','Monospace'].includes(value))chain.setFontFamily(`'${value}','Noto Devanagari'`).run();break;
      case 'highlight':if(!value||value==='none')chain.unsetMark('highlight').run();else if(/^#[0-9a-f]{6}$/i.test(value))chain.setMark('highlight',{color:value}).run();break;
      case 'color':if(/^#[0-9a-f]{6}$/i.test(value))chain.setColor(value).run();break;
      case 'lineHeight':if(+value>=1&&+value<=2.5)chain.updateAttributes('paragraph',{lineHeight:+value}).updateAttributes('heading',{lineHeight:+value}).run();break;
      case 'paragraphSpacing':if(+value>=0&&+value<=30)chain.updateAttributes('paragraph',{paragraphSpacing:`${+value}pt`}).updateAttributes('heading',{paragraphSpacing:`${+value}pt`}).run();break;
      case 'selectAll':chain.selectAll().run();break;
      case 'copy':case 'cut':{const {from,to}=editor.state.selection;send('clipboard',{text:editor.state.doc.textBetween(from,to,'\n'),cut:name==='cut'});if(name==='cut'&&from!==to)chain.deleteSelection().run();break;}
      case 'paste':if(typeof value==='string')chain.insertContent(value.split('\n').map(t=>({type:'paragraph',content:t?[{type:'text',text:t}]:[]}))).run();break;
      case 'date':chain.insertContent(new Date().toLocaleDateString(document.documentElement.lang,{day:'numeric',month:'long',year:'numeric'})).run();break;
      case 'pageBreak':chain.insertContent([{type:'pageBreak'},{type:'paragraph'}]).run();break;
      case 'clear':chain.clearNodes().resetAttributes('paragraph',['textAlign','lineHeight','paragraphSpacing']).unsetAllMarks().run();if(editor.state.selection.empty)editor.view.dispatch(editor.state.tr.setStoredMarks([]));break;
      case 'snapshot':report();break;
      case 'table':{
        const v=JSON.parse(value||'{}');
        chain.insertTable({rows:Math.max(1,Math.min(20,+v.rows||3)),cols:Math.max(1,Math.min(8,+v.cols||3)),withHeaderRow:true}).run();
        if(editor.state.doc.lastChild?.type.name==='table'){
          // Insertion maps the current cell selection onto the new document automatically.
          editor.view.dispatch(editor.state.tr.insert(editor.state.doc.content.size,editor.state.schema.nodes.paragraph.create()));
        }
        break;
      }
      case 'addRowAfter':case 'deleteRow':case 'addColumnAfter':case 'deleteColumn':case 'deleteTable':case 'toggleHeaderRow':case 'splitCell':chain[name]().run();break;
      case 'mergeCells':{
        const s=editor.state.selection;
        if(!s.$anchorCell||!s.$headCell||s.$anchorCell.start(-1)!==s.$headCell.start(-1))throw Error('Select cells within one row to merge. Vertical merges are not supported across pages.');
        chain.mergeCells().run();break;
      }
      case 'cellBorder':chain.setCellAttribute('borderWidth',Math.max(0,Math.min(2,+value||0))).run();break;
      case 'image':{const v=JSON.parse(value);if(!assetPattern.test(v.assetId))throw Error('Invalid private image');chain.setImage({assetId:v.assetId,src:assetUrl(v.assetId),alt:v.signature?'Signature':'Document image',width:v.signature?30:60,align:'left'}).run();break;}
      case 'imageWidth':chain.updateAttributes('image',{width:Math.max(10,Math.min(100,+value||50))}).run();break;
      case 'imageRotation':{const n=Number(value);if(Number.isFinite(n))chain.updateAttributes('image',{rotation:((n%360)+360)%360}).run();break;}
      case 'imageAspectLocked':chain.updateAttributes('image',{aspectLocked:value!=='false'}).run();break;
      case 'imageReset':chain.updateAttributes('image',{rotation:0,stretchRatio:null,aspectLocked:true}).run();break;
      case 'imageAlign':if(['left','center','right'].includes(value))chain.updateAttributes('image',{align:value}).run();break;
      case 'deleteImage':if(editor.state.selection.node?.type.name==='image')chain.deleteSelection().run();else chain.deleteNode('image').run();break;
      default:throw new Error('Unknown editor action');
    }
    savedSelection=editor.state.selection.toJSON();state();
  },
  async mode(next){mode=next;if(next==='pages'){editor.commands.blur();await preview();document.body.classList.add('pages');this.zoom(0);}else{document.body.classList.remove('pages');}return mode;},
  async settings(value){settings=value;applySettings(settings);if(mode==='pages')await preview();},
  theme(dark){document.body.classList.toggle('dark',dark);},
  zoom(value){document.getElementById('pages').style.zoom=value||Math.min(1,(window.innerWidth-20)/((settings.landscape?297:210)*96/25.4));},
  async exportPdf(){await this.mode('pages');return createVectorPdf(settings);},
  ack(index){if(chunkAck?.index===index){const callback=chunkAck.resolve;chunkAck=null;callback();}},
  getJSON(){return editor.getJSON();},getHTML(){return editor.getHTML();},preview
};
// Focus synchronously within the user gesture so Android can open the IME.
// Text, table, image and selection gestures retain ProseMirror's native handling.
document.getElementById('write').addEventListener('click',event=>{
  if(!editor || editor.isDestroyed || mode!=='write')return;
  if(event.target.closest('td,th,p,h1,h2,h3,ul,ol,li,blockquote,pre,img,.resize-tools,button'))return;
  const view=editor.view,doc=view.state.doc;
  const tables=[...view.dom.querySelectorAll('table')];
  if(tables.length){
    const lastTable=tables[tables.length-1],lastRect=lastTable.getBoundingClientRect();
    if(event.clientY>lastRect.bottom){
      event.preventDefault();
      if(doc.lastChild?.type.name==='table'){
        const insertPos=doc.content.size;
        const tr=view.state.tr.insert(insertPos,view.state.schema.nodes.paragraph.create());
        tr.setSelection(Selection.near(tr.doc.resolve(insertPos+1)));
        view.dispatch(tr);
      }else{
        view.dispatch(view.state.tr.setSelection(Selection.atEnd(doc)));
      }
      view.focus();
      return;
    }
    const firstTable=tables[0],firstRect=firstTable.getBoundingClientRect();
    if(event.clientY<firstRect.top){
      event.preventDefault();
      if(doc.firstChild?.type.name==='table'){
        const tr=view.state.tr.insert(0,view.state.schema.nodes.paragraph.create());
        tr.setSelection(Selection.near(tr.doc.resolve(1)));
        view.dispatch(tr);
      }else{
        view.dispatch(view.state.tr.setSelection(Selection.atStart(doc)));
      }
      view.focus();
      return;
    }
  }
  const hit=view.posAtCoords({left:event.clientX,top:event.clientY});
  if(hit){
    const $pos=doc.resolve(hit.pos);
    let inTable=false;
    for(let d=$pos.depth;d>0;d--){
      if($pos.node(d).type.name==='table'){inTable=true;break;}
    }
    const insideAnyTable=tables.some(t=>{
      const r=t.getBoundingClientRect();
      return event.clientX>=r.left&&event.clientX<=r.right&&event.clientY>=r.top&&event.clientY<=r.bottom;
    });
    if(!insideAnyTable && inTable){
      // Hit resolved inside table cell but click was outside table
      if(doc.lastChild?.type.name==='table'){
        const insertPos=doc.content.size;
        const tr=view.state.tr.insert(insertPos,view.state.schema.nodes.paragraph.create());
        tr.setSelection(Selection.near(tr.doc.resolve(insertPos+1)));
        view.dispatch(tr);
      }else{
        view.dispatch(view.state.tr.setSelection(Selection.atEnd(doc)));
      }
      view.focus();
      return;
    }
  }
  const selection=hit ? Selection.near(doc.resolve(hit.pos)) : Selection.atEnd(doc);
  view.dispatch(view.state.tr.setSelection(selection));
  view.focus();
});
window.addEventListener('error',()=>send('error',{message:'The editor encountered an error. Your last saved draft is retained.'}));
window.addEventListener('unhandledrejection',event=>send('error',{message:event.reason?.message||'Layout could not be completed.'}));
window.addEventListener('pagehide',()=>{if(editor)report();});
send('loaded');

// Native sends only a short local URL; the document is streamed by WebViewAssetLoader.
let nativeBootStarted=false;
window.addEventListener('message', async event=>{
  if(nativeBootStarted || (event.origin!=='' && event.origin!==location.origin) || typeof event.data!=='string')return;
  let message;try{message=JSON.parse(event.data);}catch{return;}
  if(message.type!=='boot-url')return;
  try{
    const url=new URL(message.url);
    if(url.origin!==location.origin || !/^\/boot-payload\/[0-9a-f-]{36}$/.test(url.pathname) || url.search || url.hash)throw Error('Invalid document source');
    nativeBootStarted=true;
    const response=await fetch(url,{cache:'no-store'});
    if(!response.ok)throw Error('The document could not be loaded');
    await window.Lekhak.boot(await response.json());
  }catch(e){send('error',{message:e.message});}
});
