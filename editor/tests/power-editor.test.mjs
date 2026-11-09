import { harness, settings } from './harness.mjs';
import assert from 'node:assert/strict';
import { PDFDocument, PDFRawStream, decodePDFRawStream } from 'pdf-lib';
import { mkdir, writeFile } from 'node:fs/promises';

const h = await harness(), p = h.page, errors = [];
p.on('pageerror', e => errors.push(e.message));
const text = (value, marks) => ({ type: 'text', text: value, ...(marks ? { marks } : {}) });
const paragraph = (...content) => ({ type: 'paragraph', content });
const boot = content => p.evaluate(({ content, settings }) => window.Lekhak.boot({ schemaVersion: 1, page: settings, content: { type: 'doc', content } }), { content, settings });
const command = (name, value) => p.evaluate(({ name, value }) => window.Lekhak.command(name, value), { name, value });
const search = (query, options = {}) => command('find', JSON.stringify({ query, ...options }));
const last = type => p.evaluate(type => window.__messages.filter(m => m.type === type).at(-1), type);
const json = () => p.evaluate(() => window.Lekhak.getJSON());
const html = () => p.evaluate(() => window.Lekhak.getHTML());
try {
  await boot([paragraph(text('Hel', [{ type: 'bold' }]), text('lo hello HELLO shello')), paragraph(text('नमस्ते नमस्ते। नमस्तेका [a+b]'))]);
  const original = await json(), originalHtml = await html();
  const metrics = await last('selection');
  assert.equal(metrics.wordCount, 8, 'Paragraph boundaries separate words');
  assert.equal(metrics.lineHeight, 1.5, 'Page defaults must appear in toolbar');
  assert.equal(metrics.paragraphSpacing, 8);
  await search('hello');
  assert.equal((await last('search')).total, 4, 'Search spans styled text runs');
  assert.equal(await p.locator('.search-match-current').count(), 2, 'Active styled match has two decorated runs');
  assert.deepEqual(await json(), original);
  assert.equal(await html(), originalHtml, 'Search decorations never persist in exported HTML');
  await search('hello', { wholeWord: true });
  assert.equal((await last('search')).total, 3);
  await search('hello', { matchCase: true, wholeWord: true });
  assert.equal((await last('search')).total, 1);
  await search('नमस्ते', { wholeWord: true });
  assert.equal((await last('search')).total, 2, 'Hindi combining marks count as word characters');
  await command('findNext'); assert.equal((await last('search')).index, 2);
  await command('findNext'); assert.equal((await last('search')).index, 1);
  await command('findPrevious'); assert.equal((await last('search')).index, 2);
  await search('[a+b]'); assert.equal((await last('search')).total, 1, 'Query is literal, not regex');
  await search('hello', { wholeWord: true });
  await command('replaceAll', 'Welcome');
  const replaced = await json();
  assert.equal((await p.locator('.tiptap').innerText()).match(/Welcome/g).length, 3);
  assert.ok(replaced.content[0].content[0].marks.some(m => m.type === 'bold'), 'Replacement retains first character formatting');
  await command('findClose');
  await command('undo'); assert.deepEqual(await json(), original, 'Replace all is one undo step');
  await command('redo'); assert.deepEqual(await json(), replaced);
  await search('Welcome'); await command('replaceOne', '');
  assert.equal((await last('search')).total, 2, 'Blank replacement deletes text');

  const table = { type: 'table', content: [{ type: 'tableRow', content: [
    { type: 'tableCell', content: [paragraph(text('cell target'))] },
    { type: 'tableCell', content: [paragraph(text('target cell'))] },
  ] }] };
  await boot([table, paragraph(text('target'))]);
  await search('target'); assert.equal((await last('search')).total, 3);
  await command('replaceAll', 'found'); assert.equal((await last('search')).total, 0);
  await p.evaluate(() => window.Lekhak.review(true));
  await search('found'); assert.equal((await last('search')).total, 3);
  const readOnly = await json();
  await command('replaceAll', 'broken'); assert.deepEqual(await json(), readOnly, 'Review search must not allow edits');
  await command('findClose');
  assert.equal(await p.locator('.search-match-current').count(), 0);

  await boot([paragraph(text('bold ', [{ type: 'bold' }]), text('normal target'))]);
  await search('normal'); await command('replaceOne', 'plain');
  assert.equal((await json()).content[0].content.at(-1).marks, undefined, 'A match after bold text keeps its own unstyled marks');

  await boot([paragraph(text('Highlighted नमस्ते text')), paragraph(text('Second paragraph'))]);
  await command('selectAll');
  await command('highlight', '#fff59d');
  const highlighted = await json();
  assert.ok(highlighted.content[0].content[0].marks.some(m => m.type === 'highlight'));
  assert.equal(await p.locator('.tiptap mark').first().evaluate(e => getComputedStyle(e).backgroundColor), 'rgb(255, 245, 157)');
  await p.evaluate(({ content, settings }) => window.Lekhak.boot({ schemaVersion: 1, page: settings, content }), { content: highlighted, settings });
  assert.deepEqual(await json(), highlighted, 'Highlight survives save/reload');
  await command('selectAll'); await command('highlight', 'none');
  assert.equal(await p.locator('.tiptap mark').count(), 0);
  await command('undo'); assert.deepEqual(await json(), highlighted);
  await search('नमस्ते');
  const result = await p.evaluate(async () => { const result = await window.Lekhak.exportPdf(); return { pages: result.pages, bytes: Array.from(result.bytes) }; });
  assert.ok(result.pages >= 1);
  assert.equal(await p.locator('#pages .search-match-current').count(), 0, 'Search UI is excluded from PDF layout');
  const pdfBytes = Uint8Array.from(result.bytes), pdf = await PDFDocument.load(pdfBytes);
  const streams = pdf.context.enumerateIndirectObjects().filter(([, v]) => v instanceof PDFRawStream).flatMap(([, stream]) => { try { return Buffer.from(decodePDFRawStream(stream).decode()).toString(); } catch { return ''; } }).join('\n');
  const fills = [...streams.matchAll(/([\d.]+) ([\d.]+) ([\d.]+) rg/g)].map(m => m.slice(1).map(Number));
  assert.ok(fills.some(([r,g,b]) => Math.abs(r - 1) < .01 && Math.abs(g - 245/255) < .01 && Math.abs(b - 157/255) < .01), 'PDF contains the actual yellow highlight fill');
  await p.evaluate(() => window.Lekhak.mode('write'));
  await command('findClose'); await command('selectAll');
  await command('heading', '1'); await command('heading', '1');
  assert.equal((await json()).content[0].type, 'heading', 'Selecting current heading must not toggle it off');
  await mkdir('../validation/power-editor', { recursive: true });
  await writeFile('../validation/power-editor/highlight.pdf', pdfBytes);
  await p.setViewportSize({ width: 360, height: 740 });
  await p.evaluate(() => { document.activeElement?.blur(); window.getSelection()?.removeAllRanges(); });
  await p.screenshot({ path: '../validation/power-editor/highlight.png' });
  assert.deepEqual(errors, []);
  console.log('PASS: literal Unicode/styled/table search, case/whole-word, navigation, replace & undo, review safety, saved highlight, vector PDF fill and heading idempotence');
} finally { await h.close(); }
