# Validation — 10 October 2026

Final build: validation/audit/home-ux-gradle.log — BUILD SUCCESSFUL. Debug APK, test APK and unsigned release AAB built. Version 1.1.2 (4), min SDK 26, target SDK 35.

- 21 JVM tests passed: DocumentTest 10, AssetReferenceTest 6, SaveQueueTest 3, ExportRetentionTest 2; zero failures/errors.
- Android lint: 0 errors, 22 warnings. Warnings remain; this is not a warning-free production certification.
- Existing desktop layout, formatting, security and hardening browser tests passed. Seven PDF fixture exports include Hindi, long paragraphs, styles, Mono, Serif, tables and image/signature.
- Follow-up browser test passed: short-message/local-fetch boot, JPEG preview and direct JPEG PDF embedding. JPEG stream fixture is saved in validation/audit/jpeg-streamed-boot.pdf.
- Template parser reproduced with native ICU: old unescaped pattern fails, corrected pattern succeeds. All 16 template-card instrumentation cases compile but have NOT run on Android.
- Seven final PDF fixtures pass NFC/layout-whitespace-normalized comparisons in pypdf, PyMuPDF and PDFium; Hindi search checks pass. Ten exact Hindi sample checks are retained in the baseline comparison report.
- **Known failure:** strict repeated-space extraction assertion fails for PDFium, which collapses two consecutive spaces into one. pypdf/PyMuPDF preserve them. Full raw results and failing verification log are retained; the full strict PDF suite is NOT green.
- 1/5/20/50/100-page desktop pagination/export measurements are in PERFORMANCE.md. 100-page long tasks remain. No Android latency, process RAM, GC or low-end-device claim is made.
- Version-based dependency audit still reports the Tiptap 2.11.5 moderate advisory. Its __proto__ mitigation is backported via a pnpm patch and regression-tested. Notices/inventory are bundled; release dependency/license review remains appropriate.

No emulator was launched and no APK was installed. Physical-device verification is pending for templates, native WebView message delivery, keyboards, picker/EXIF behavior, lifecycle/storage pressure, Android PDF viewers and Print page ranges. Use DEVICE-TEST-CHECKLIST.md.

Delivered debug APK is development-signed; release AAB is unsigned. Source zip excludes build caches, node_modules, local SDK paths and temporary work. Root checksums identify the final files; previous root deliverables were backed up before replacement.

Follow-up: foreground service manifest/permissions merged correctly; retention and accessibility regressions passed. Browser result: validation/audit/lifecycle-browser.json. Physical notification, background execution, lifecycle/IME and TalkBack checks remain pending. See REVIEW-EXPORT-LIFECYCLE.md.

Home UX review: white-paper/browser smoke checks passed; native screen visual testing pending. See REVIEW-HOME-UX.md.
Follow-up: foreground service manifest/permissions merged correctly; retention and accessibility regressions passed. Browser result: validation/audit/lifecycle-browser.json. Physical notification, background execution, lifecycle/IME and TalkBack checks remain pending. See REVIEW-EXPORT-LIFECYCLE.md.
