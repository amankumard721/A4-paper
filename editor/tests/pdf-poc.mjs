import { PDFDocument } from 'pdf-lib';
import * as fontkit from 'fontkit';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { WordFonts } from '../src/word-font.js';
import hbPromise from 'harfbuzzjs';
export const samples=['प्रधानाचार्य महोदय','विद्यालय','क्षेत्रीय कार्यालय','कृपया मेरा आवेदन स्वीकार करें।','शिक्षक प्रशिक्षण','क्ष त्र ज्ञ श्र','हिन्दी और English दोनों','दिनांक 10/10/2026','श्रीमान प्रधानाचार्य महोदय','मेरा नाम अमित कुमार है।'];
await mkdir('../validation/audit',{recursive:true});
const pdf=await PDFDocument.create(),page=pdf.addPage([595.27559,841.88976]);
const font=fontkit.create(await readFile('../app/src/main/assets/editor/fonts/NotoSans-Regular.ttf'));
const hb=await hbPromise;const blob=hb.createBlob(await readFile('../app/src/main/assets/editor/fonts/NotoSans-Regular.ttf'));const face=hb.createFace(blob,0);const hbfont=hb.createFont(face);
font.shape=text=>{const buffer=hb.createBuffer();buffer.addText(text);buffer.guessSegmentProperties();hb.shape(hbfont,buffer);const glyphs=buffer.json();buffer.destroy();return{glyphs:glyphs.map(g=>font.getGlyph(g.g)),positions:glyphs.map(g=>({xAdvance:g.ax,yAdvance:g.ay,xOffset:g.dx,yOffset:g.dy}))};};
const fonts=new WordFonts(pdf);
samples.forEach((text,i)=>{let x=42.52;for(const token of [text]){fonts.draw(page,font,token,{x,y:780-i*36,size:16});x+=fonts.word(font,token).width*16/1000;}});
fonts.finish();await writeFile('../validation/audit/word-font-poc.pdf',await pdf.save());await writeFile('../validation/audit/hindi-input.json',JSON.stringify(samples,null,2));
console.log('Created visible-vector Unicode word-font proof of concept.');
hbfont.destroy();face.destroy();blob.destroy();

