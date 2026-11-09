# PDF Scanner — A4 Paper Typing 1.7.0 (11)

Prepared 1 November 2026. Historical scanner verification for version 1.7.0; updated 2 November 2026 to record source placement and the subsequent ads change.

## User flow

Home now has a distinct Scan to PDF card with a paper preview and Scan pages button. The scanner landing page gives a prominent scan action, four short steps and a way to open an existing PDF. Capture uses Google ML Kit Document Scanner full mode: camera or gallery, page-edge adjustment, rotation/filters/cleanup and up to 50 pages in one scan. SDK UI capabilities depend on the delivered Google Play services module.

Only PDF output is requested. Completed scans are validated and copied into the existing private PDF library, then opened in the PDF preview with zoom, page navigation, Save, Share, Print and Manage pages. Library labels now say Saved PDFs. Rename/duplicate/delete are available from document cards. Temporary file-backed scanner output is deleted only after durable saving succeeds. Scanned PDFs are image based; no OCR or searchable-text promise is added.

## Platform and privacy

Google Play services is required, with first-use internet for component download when not installed. The SDK requires at least 1.7 GB device RAM. Normal document editing/import stays available if scanner startup fails. The scanner-only 1.7.0 build requested no Camera, broad storage or Internet permission; its merged-manifest checks are recorded below. Version 1.7.1 adds Internet access for advertising; see [ADS-SETUP.md](ADS-SETUP.md) for the updated SDK and privacy scope. Google components can process technical usage/device/diagnostic data under their terms, so previous blanket no-analytics language has been replaced with a specific disclosure. Scan content is processed on-device; this app has no document upload server.

The PDF library retains its 30 MB cap, even for a 50-page scan. Oversized scans must be made in smaller batches. The result launcher is registered at app scope, module preparation waits for a resumed Activity, pending preparation cancels on navigation, duplicate launches are blocked, and user cancellation returns quietly.

Scanner file URIs are accepted only through the scanner-specific path and only inside canonical app cache. Traversal/host/query/symlink escapes are rejected. Ordinary external imports remain content-only. PDF structure/native compatibility and actual page count are checked before publication.

## Verification of scanner-only version 1.7.0

JVM/Robolectric: 33 tests, zero failures, one skipped Windows symlink test (32 passed). The new scanner checks cover cache file reading, outside/traversal/authority rejection, preserving content-only external import, and page-count mismatch/bounds. Android compilation and debug APK packaging passed. Native ELF LOAD alignment is 16384 for all eight bundled libraries; APK zip alignment and signature verification passed, and the signing certificate matches the previous 1.6.0 debug build. Final lint and debug APK build passed with zero errors (33 lint warnings). Camera is explicitly optional; final merged manifest contains no app Camera, Internet or broad-storage permission. No runtime code changed after tests. Camera capture, first-use Google module download, gallery crop/filter controls, process/background transitions and physical sharing need a supported physical device. No camera scan has been performed from this development environment.

## File placement

Scanner changes were prepared and tested in the staged workspace. The 1.7.0 debug APK was copied to OnePlus phone storage with a matching hash. On 2 November 2026, the 12 scanner source/configuration/doc files were applied to C:\A4 typing app\A4Lekhak after verifying the original file hashes. Subsequent ads work targets that original project. No signing store or credentials are bundled.

## Primary references

- [ML Kit scanner setup and requirements](https://developers.google.com/ml-kit/vision/doc-scanner/android)
- [Scanner capabilities](https://developers.google.com/ml-kit/vision/doc-scanner)
- [Scanner PDF URI contract](https://developers.google.com/android/reference/com/google/mlkit/vision/documentscanner/GmsDocumentScanningResult.Pdf)
- [ML Kit terms](https://developers.google.com/ml-kit/terms)
- [Android SDK data disclosure](https://developers.google.com/ml-kit/android-data-disclosure)
