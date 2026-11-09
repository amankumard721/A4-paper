import { harness } from './harness.mjs';
import assert from 'node:assert/strict';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { PDFDocument, PDFRawStream, decodePDFRawStream } from 'pdf-lib';

// Produced by ExcelReaderTest from an actual zipped XLSX package.
const document = JSON.parse(await readFile('../validation/imports/excel-document.json', 'utf8'));
const h = await harness(), p = h.page, errors = [];
p.on('pageerror', error => errors.push(error.message));
try {
  await p.evaluate(data => window.Lekhak.boot({schemaVersion:1, ...data}), document);
  assert.match(await p.locator('.tiptap').innerText(), /Revenue नमस्ते/);
  assert.match(await p.locator('.tiptap').innerText(), /₹ 5\.00/);
  assert.equal(await p.locator('.tiptap td[colspan="2"]').count(), 1);
  const fill = p.locator('.tiptap td[data-cell-fill="#FFFF00"]').first();
  assert.ok(await fill.count());
  assert.equal(await fill.evaluate(e => getComputedStyle(e).backgroundColor), 'rgb(255, 255, 0)');
  const snapshot = await p.evaluate(() => window.Lekhak.getJSON());
  await p.evaluate(data => window.Lekhak.boot(data), {schemaVersion:1, ...document, content:snapshot});
  assert.deepEqual(await p.evaluate(() => window.Lekhak.getJSON()), snapshot);
  const result = await p.evaluate(async () => {
    const pdf = await window.Lekhak.exportPdf();
    return {pages:pdf.pages, bytes:Array.from(pdf.bytes)};
  });
  assert.ok(result.pages >= 1 && result.bytes.length > 1000);
  const bytes = Buffer.from(result.bytes), pdf = await PDFDocument.load(bytes);
  let operations = '';
  for (const [, object] of pdf.context.enumerateIndirectObjects()) {
    if (object instanceof PDFRawStream) {
      try { operations += Buffer.from(decodePDFRawStream(object).decode()).toString('latin1'); } catch {}
    }
  }
  assert.match(operations, /1 1 0 rg/, 'Imported Excel cell fill must reach the real PDF');
  await mkdir('../validation/imports', {recursive:true});
  await writeFile('../validation/imports/imported-excel.pdf', bytes);
  assert.deepEqual(errors, []);
  console.log('PASS: XLSX → editable editor tables → save/reload → vector PDF, including Hindi, merged cells, cached values and fill colors');
} finally { await h.close(); }
