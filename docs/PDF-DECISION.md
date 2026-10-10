# PDF decision

The final direct exporter uses bundled HarfBuzz, fontkit and pdf-lib to emit visible vector grapheme glyphs with Unicode mappings. It replaces the hidden Android Print callback constructor shim and Chromium's problematic extraction output. Page text is not a bitmap and no invisible text overlay is added. Layout positions come from the same A4 preview DOM. Images are embedded as PNG or JPEG, preserving JPEG compression.

Ten exact Hindi samples pass raw extraction in pypdf, PyMuPDF and PDFium. Seven fixtures pass NFC/whitespace-normalized comparisons, including long Hindi, styles, fonts, tables and signatures. The strict repeated-space test fails in PDFium (two spaces become one); it passes in pypdf/PyMuPDF. This is an unresolved compatibility limitation, not a fully green PDF suite. Search checks pass in PyMuPDF; Android viewers and printing still need physical tests. Excessively long combining clusters and oversized table rows produce explicit errors.

See validation/audit raw PDFs and extraction JSON, AUDIT-REPORT.md and PERFORMANCE.md. Desktop results do not guarantee every external reader's extraction behavior.
