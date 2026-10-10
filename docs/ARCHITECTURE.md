# Architecture

Kotlin/Compose shell; Room index plus AtomicFile JSON originals; DataStore preferences. SaveQueue coalesces pending snapshots and preserves operation barriers. Repository validation, reference extraction and persistence run on IO. Room/JSON schema remains version 1.

EditorHost serves only a local HTTPS origin with WebViewAssetLoader. A short postWebMessage carries a per-load URL; a local response streams boot JSON. Tiptap/ProseMirror, fonts, WASM and scripts are bundled. WebMessageListener accepts main-frame messages only from the approved origin. Content callbacks retain document IDs to reject stale editor events.

AssetStore imports private UUID images with bounded decoding, EXIF orientation and metadata removal: JPEG 85 for opaque photos, PNG for alpha/signatures. Startup reference scanning protects all documents and export snapshots; old unreferenced images are collected only before live undo history exists. See REVIEW-FOUR-SUGGESTIONS.md for constraints.

ExportWorker persists a snapshot, renders through an offscreen EditorHost and streams ordered acknowledged PDF chunks. HarfBuzz shapes text; fontkit supplies glyph outlines; pdf-lib writes actual vector Type3 glyphs with grapheme ToUnicode maps, table graphics and PNG/JPEG images. Native PdfRenderer validates A4 pages. Save as/Share/Print consume the same PDF. Public PrintManager callback interfaces are used without hidden constructors.

Page preview is separate from the continuous editable view. Direct paginated editing is a documented proposal, not enabled. Desktop browser tests do not establish Android IME or lifecycle correctness.

Foreground export uses WorkManager dataSync notification/cancellation. Only the screen EditorHost follows Compose pause/resume; no global pauseTimers call is made. ExportRetention retains latest PDFs and removes earlier versions after seven days on startup recovery or another export. See REVIEW-EXPORT-LIFECYCLE.md.
