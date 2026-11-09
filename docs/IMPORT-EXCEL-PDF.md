# Excel and PDF import — A4 Paper Typing 1.6.0 (10)

## Available workflows

Home → Excel opens XLSX, legacy XLS or UTF-8 CSV. Every nonempty visible worksheet becomes a named editable table section. Formatting includes bold/italic/underline, text and cell colors, alignment, proportional column widths, horizontal merged cells, line breaks, and common number/date formats. After reviewing, edit with the existing table tools and export/save/share/print as PDF. Imported copies appear in Documents.

Home → PDF opens an original-layout viewer with page navigation, Go to page, pinch zoom, bounded pan, double-tap zoom, and Save/Share/Print. A private byte-preserving original is kept in the PDF library, independent of the source provider's permission. The library supports search, rename, duplicate and confirmed delete.

In the PDF viewer's menu:
- **Edit text copy** extracts selectable text into a new editable document. The original PDF stays in the library. Images, fonts, tables and exact layout are not reproduced in this text copy. Scanned files show a clear no-selectable-text message; OCR is not included.
- **Manage pages** creates a new PDF from selected pages, in the entered order, with optional clockwise rotation. Example: `3,1-2` places page 3 first. Blank means every page. Repeated or invalid pages are rejected. Omit unwanted pages to remove them from the new copy. Original page resources, crop boxes and existing rotations are retained.

The Documents import button and Android Open with/Share accept Word, Excel and PDF. Processing is offline, without Internet/storage permissions. Import progress can be cancelled. Native parsing and file operations run away from the UI thread. Damaged, unsupported, oversized or encrypted files report an error.

## Deliberate limits

Excel uses saved formula results; it never executes formulas, macros or external links. Missing formula caches require recalculating/saving in Excel first. Charts/drawings are not converted. Hidden sheets/rows/columns are omitted with a notice. Vertical merged cells are expanded for page continuation. Wide tables are split into groups of up to eight columns; more than five columns defaults to landscape A4. Original spreadsheet files are unchanged.

Spreadsheet limits: XLSX 20 MB compressed/32 MB expanded, individual ZIP part 12 MB; XLS 8 MB; CSV 4 MB. Up to 20 sheets, 2000 used rows, 64 columns and 20000 cells across the workbook/converted table area. Complex or very sparse files may exceed the bounds. No silent data truncation is used.

Original PDFs: 30 MB/300 pages. Text copies: 100 pages/1 million characters/20000 lines, with per-page limits. Password-protected/encrypted PDFs require an unlocked copy. Text extraction order/encoding depends on the source PDF. Forms, annotations, bookmarks and digital signatures are not editing features in this release; page-copy operations create a new document and do not promise interactive structure/signature preservation.

Imported originals are stored separately from temporary exports. Deletion uses a recoverable tombstone; metadata is written atomically. Stale staging files are cleaned after a grace period. Save valuable copies outside the app before uninstalling.

## Implementation references

- [Android PdfRenderer](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer): original-page rendering using the platform renderer; created and used on a worker dispatcher.
- [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android): text extraction and copying page objects, with bounded scratch storage and output size.
- [JExcelAPI source](https://sourceforge.net/projects/jexcelapi/): legacy binary XLS values/styles. XLSX/CSV use a bounded local reader; ZIP paths, XML entities, document complexity and used ranges are validated.

## Verification

- All 28 JVM/Robolectric tests passed: actual zipped XLSX, binary XLS and quoted UTF-8 CSV, styles/formula caches, malformed files and bounds, durable PDF library operations, actual PDF text extraction, reordered/rotated page streams and cancellation. Rotated-page text extraction is normalized in memory without modifying retained PDF bytes.
- Browser integration passed: converted Excel → editor schema → save/reload → actual vector PDF, including Hindi, merged cells and cell fills. Existing DOCX-import and power-editor suites also passed.
- Excel and reordered/rotated PDF outputs were rasterized and visually inspected. Artifacts are under `validation/imports/`.
- Version 1.6.0 / code 10 debug APK built; signature and copied-phone SHA-256 verified. Signing certificate matches the previous test APK. Final lint passed with zero errors (33 warnings); release R8 shrinking passed. PDFBox's optional JPEG-2000 decoder/encoder are not shipped or used for these workflows; specific R8 rules mark those optional types, while Android renders original PDF pages.
- APK: `C:\A4 typing app\A4-Paper-Typing-1.6.0-excel-pdf-debug.apk` (33,711,181 bytes). Copied to the OnePlus storage root with the same filename. SHA-256: `8A06A73102C1388182FF7EF433A173E95D8D4860FAE0A8DA26E52441638E0466`.

Physical phone checks remain necessary for document providers, native PDF renderer compatibility, keyboard/touch interactions and the system share/print dialogs.
