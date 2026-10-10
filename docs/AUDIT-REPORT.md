# A4 Lekhak 1.1.0 — audit and hardening

Updated 10 October 2026. Project: `C:\A4 typing app\A4Lekhak`.

This is an incremental update of the existing Kotlin/Compose application. It is a physical-device test build, not a claim of Play Store or production readiness. No emulator was installed or started. No device was connected when `adb devices` was checked.

## Verified baseline

The repository contains Kotlin 2.0.21, Compose Material 3, Hilt, Room 2.6.1, DataStore, WorkManager, an origin-restricted AndroidX WebKit host and bundled Tiptap/ProseMirror 2.11.5. Room holds metadata; private AtomicFile JSON snapshots hold complete documents. The baseline build, nine JVM tests and lint passed. Original PDFs, browser results and the original pagination implementation were retained for comparison.

The old direct exporter constructed Android print result callbacks through `android.print.PdfCallbacks.java`. Those constructors are not a supported direct-export contract. This was a real implementation dependency, not just a reported possibility.

The Chromium comparator reproduces incorrect Hindi extraction for the exact ten requested samples in all three tested readers. ToUnicode mappings and ActualText alone did not make that output reliable. Raw input, extraction, font types, mapping counts and line mismatches are in `validation/audit/baseline-comparison.json`. Original pre-change fixtures also remain under `validation/baseline`.

## Issue-by-issue result

| Issue | Root cause | Applied change | Verification | Result |
|---|---|---|---|---|
| Template card immediately crashes | Bare closing braces in the placeholder pattern compile on desktop Java but fail in ICU, used by Android | Escape both closing braces; share one placeholder parser between preview, fields and document creation | Native Windows ICU reproducer: original error 66305, corrected pattern success; all-template JVM tests; Android card-to-preview/create regression compiled | Code fixed; user-device confirmation remains |
| Hindi copy/search corruption | Glyph-to-Unicode mapping/reader heuristics in original print output; a whole-line prototype also exceeded older reader mapping limits and triggered column ordering | HarfBuzz shapes visible graphemes; Fontkit supplies vector outlines; pdf-lib writes Type3 glyphs with logical ToUnicode sequences, explicit bounds/metrics and non-ASCII glyph codes | Ten exact samples, long paragraphs, marks, Serif/Mono fallback, tables and image fixtures extracted with pypdf, PyMuPDF and PDFium; search checked; rendered images inspected | Hindi/normalized fixtures pass; PDFium repeated-space test fails; Android viewer checks pending |
| Hidden print callback construction | Direct export invoked unsupported callback constructors | Delete shim; stream vector PDF bytes through acknowledged bounded chunks; use public PdfRenderer for validation | Kotlin compilation, source scan, browser chunk protocol test, Android integration test compiled | No shim/reflection bypass remains in app source |
| Dedicated Save PDF and native printing | Old pipeline coupled file creation to print adapter callbacks | WorkManager creates a private complete PDF; SAF Save, FileProvider Share and PrintManager use that same file | Build/static contract checks; actual native UI pending | Both workflows retained |
| Repeated pagination measurements | Re-cloned/re-measured current page prefixes on every preview; yield was skipped by common fast paths | Bounded exact HTML-prefix measurement cache, invalidation on settings/content changes, retained measurement DOM, time-budget yields | Original layout/text-loss cases; 1/5/20/50/100 page timing artifacts | Faster repeated changed previews; cold cost and memory trade-offs reported, not hidden |
| Table support | No structured table nodes or row pagination | Version-matched table extensions, row/column/header/border/alignment commands, resizing, horizontal cell merge/split; whole-row page continuation with repeated headers | 70 data rows across 3 pages, exact extraction, JSON reopen, row/column changes, overflow rejection | Stable subset shipped; tall single rows, nested tables and cross-page vertical merges rejected |
| Images/signatures | No durable private assets or picker flow | Android Photo Picker; bounded decode, orientation correction, private immutable JPEG/PNG, UUID references, width/alignment/delete controls | Browser insertion/resize/delete/undo/reopen; embedded PDF image; missing/oversize rejection; reference traversal tests | Feature built; Android picker/orientation/memory tests pending |
| Image/text overlap found during audit | Legacy HTML `align` attribute introduced float behavior; cloned image decode could reserve zero height | Remove legacy align attribute; use CSS alignment and reserve decoded aspect ratio during pagination | Re-rendered signature fixture and inspected text below image | Fixed in regression fixture |
| Autosave memory/order/failure risk | One full snapshot closure per keystroke; flush barrier ignored earlier save failure | Coalesce only consecutive pending saves for the same document; preserve FIFO barriers; propagate failed saves; drain on close | 1,000 queued edits coalesce; rename barrier order; failed write/retry; close drains | JVM queue tests pass |
| Late editor callback overwrites another document | Snapshot callbacks were not document-ID scoped | Require matching document ID for each edit/snapshot | Source review and build; rotation/process-death UI still pending | Guard added |
| Recovery/load errors | Recovery exception could keep loading state indefinitely | Finish recovery state in `finally`; surface errors; preserve snapshots | Build/review and existing recovery test source | Device fault-injection pending |
| Temporary export retention | Failed jobs and cancelled requests could accumulate | Delete failed request snapshots; retain restartable cancellations; remove week-old finished requests and day-old partial PDFs | Scoped private-directory review/build | Successful PDFs and document originals are retained |
| Tiptap dependency advisory | `mergeAttributes` accepted an own `__proto__` key | Lockfile-backed minimal 2.11.5 patch rejects the key before read/assignment | Advisory reproducer regression passes | Version-based audit still reports one moderate advisory; local mitigation is documented |

## PDF design and limits

Text is visible vector text, not page screenshots or an invisible overlay. Every shaped grapheme has a Unicode mapping. The reader can select the grapheme as a unit; individual matra/consonant selection inside that cluster is viewer-dependent. Long combining sequences above the supported mapping bound fail clearly. This build targets Hindi and English; unsupported font characters fail rather than silently exporting missing glyphs.

Coordinates and page breaks come from the same measured preview DOM. Fonts are local Noto regular/bold files, with a bundled Mono font and Devanagari fallback. Italic is synthesized. Table borders are vectors; imported pictures are embedded PNGs or JPEGs. Export is streamed with per-chunk acknowledgement, a 100 MB bound and a five-minute timeout. Android PdfRenderer checks A4 geometry and page count before the `.partial` file becomes a `.pdf`.

`pdf-regression.json` retains all extracted text. General multi-line comparisons use NFC and collapse layout whitespace; they do not remove intraword spaces. `baseline-comparison.json` compares the ten sample lines exactly after NFC and edge trimming. An additional strict repeated-space check remains **failing in PDFium**: `Two  spaces remain.` becomes `Two spaces remain.`; pypdf and PyMuPDF preserve both spaces. The PDF contains both positioned Unicode spaces. Neither ActualText nor separate space glyphs changed PDFium's behavior. The strict verifier still exits nonzero for this documented limitation; it writes the full matrix before asserting. Poppler pdftotext was unavailable; no result is claimed for it. Desktop PDFium extraction is not the same as testing Android PdfRenderer or a third-party Android viewer.

## Security and storage

The manifest has no Internet or broad photo/storage permission. Native picker grants are used only while copying the chosen URI. Images up to 20 MB are decoded with sampling to at most 1600 pixels on the longest side; EXIF orientation is applied and metadata is not carried into the re-encoded image. Assets live in app-private `files/assets`, outside FileProvider's exposed exports directory. Filename and canonical-parent checks prevent traversal.

WebView file/content access, mixed content, remote navigation and network loads remain disabled. The message listener checks local HTTPS origin and main frame. CSP allows local scripts/fonts/images/font fetching and WASM compilation; remote content, frames, forms and inline JavaScript are blocked. No upload, analytics or remote editor was added. This is evidence of the configured boundaries, not a claim that every possible leak has been tested.

Room schema remains version 1 with no destructive migration. New table/image nodes are additive to the existing JSON envelope. Do not downgrade to the old text-only APK after creating media documents. Duplicate documents share immutable assets. Removing an image reference does not aggressively delete its underlying file, avoiding broken duplicates/undo; unreferenced asset garbage collection remains future work. Uninstall still removes private documents and assets.

The available npm production audit found one moderate Tiptap advisory, no high/critical findings. The patch and its test are retained. A complete Android/transitive native CVE audit was not available; lint and build validation do not replace one. See `pnpm-audit.json` and the bundled third-party notices.

## Remaining release gates

- Run the supplied debug APK on the user's physical device, starting with every template card.
- Test API 26 and a recent Android release: Gboard Hindi IME, rotation, background/restore, Save As, sharing grants, cancelled export, native print page ranges, low storage and low memory.
- Verify PDF copying/search in the user's actual viewers, plus visually compare pages containing small fonts, dense tables and long mixed text.
- Complete low-memory-device typing/scroll/GC profiling. Desktop timings and heap counters are not Android latency or RAM guarantees.
- Obtain a release signing key and complete store-policy review separately.

Direct page editing remains a documented future investigation in `DIRECT-PAGE-EDITING.md`; the working editor was not replaced.

Sources: [Android Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker), [Tiptap table API](https://tiptap.dev/docs/editor/extensions/nodes/table) (installed 2.11.5 source was checked separately), [Tiptap advisory](https://github.com/advisories/GHSA-cp6q-959q-f8rh), [ICU regular expressions](https://unicode-org.github.io/icu/userguide/strings/regexp.html).


## Follow-up review

The four user suggestions were reviewed and addressed after the original audit. Photo compression, conservative startup asset collection, single background content validation and streamed boot loading supersede earlier retention/PNG-only notes. See REVIEW-FOUR-SUGGESTIONS.md for the implementation and reasons for rejecting regex-only validation and unconditional image deletion.
