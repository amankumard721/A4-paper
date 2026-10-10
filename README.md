# A4 Paper Typing — Android document editor

Version 1.2.0 (code 5), package com.a4sizepaper.typing.texttopdf. Source is in C:\A4 typing app\A4Lekhak. Debug APK is for your physical-device testing; it is not a production-certified release.

Install the updated A4-Lekhak-debug.apk from the parent folder over the previous build when signing permits. Do not uninstall to update: uninstalling removes private documents. Save important PDFs outside the app. Android 8+ with an updated Android System WebView is required. No emulator or device installation was performed here.

The template-card crash was caused by closing braces not escaped in a regex accepted by desktop Java but rejected by Android ICU. The parser is corrected and shared by preview and document creation. All 16 cards have a compiled instrumentation regression; physical-device execution remains pending.

Features: offline Hindi/English document editing, templates, A4 preview, local save/duplicate, tables, private images/signatures, searchable vector PDF export, Save as, Share and Android Print. Page mode remains read-only preview. Images now use JPEG 85 for opaque photos, PNG for transparency/signatures, and conservative orphan cleanup at next process startup. Document validation/saving runs off the UI thread and boot data is fetched through a private local stream.

Read docs/TEST-RESULTS.md, docs/AUDIT-REPORT.md, docs/REVIEW-FOUR-SUGGESTIONS.md and docs/DEVICE-TEST-CHECKLIST.md. Known desktop PDF limitation: PDFium collapses repeated spaces on extraction; exact Hindi samples pass. Android keyboard, lifecycle, printing and reader checks remain device gates.

## Build

Use JDK 17 and Android SDK. From editor/, run pnpm install --frozen-lockfile and pnpm build; the pnpm workspace includes the Tiptap security patch. From project root run gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest :app:bundleRelease. Run pnpm test and node tests/review.test.mjs in editor/. Python verification needs pypdf, PyMuPDF and pypdfium2; its strict repeated-space assertion currently fails in PDFium and must not be presented as a full pass.

Release AAB is unsigned and requires your release signing configuration before publishing. Documents stay in private device storage. There is no account, network document upload, watermark, hidden print callback shim, or rasterized-text PDF export.

Export/lifecycle follow-up: foreground PDF notification with cancellation, conservative PDF retention, per-editor pause/resume and accessibility hints are implemented. See docs/REVIEW-EXPORT-LIFECYCLE.md for limits and device checks.

Home UX follow-up: one creation action on Home, edge-to-edge template carousel, pinned Home/Documents search, destructive Delete styling and white editing paper in dark mode. See docs/REVIEW-HOME-UX.md.
