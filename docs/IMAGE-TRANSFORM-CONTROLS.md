# Image resize, stretch and rotation

Updated2 November2026. Source:C:/A4 typing app/A4Lekhak.

Selected images expose8 corner/side grips and a freely draggable round rotation handle. Corners preserve proportions by default, side grips stretch independently, and the native Insert&adjust > Image sheet offers a proportion-lock switch, continuous0–360degree slider, angle presets, width/alignment and reset. Each completed drag is one undo step; pointer cancellation restores the original state. Rotation, stretch ratio and proportion lock are document attributes and survive reopening. Existing documents without these attributes retain natural image proportions.

The frame reserves rotated bounding-box space in normal document flow. Preview pagination uses the same dimensions, and PDF export retains the original image bytes with PDF rotation/scaling transforms, rather than rasterizing the whole page. It remains a flow-based document editor: arbitrary floating text-wrap layouts and an entire Word feature set are not added by this change. Oversized content is still subject to the existing A4 overflow checks.

Verification: browser regression checks8 handles, independent side stretching, real rotation-handle drag, arbitrary37.5degree angle, persistence, reset, undo and PDF export. Existing layout/security/hardening suites pass, including multi-page Hindi/text/table and legacy image exports. The generated stretched/rotated PDF was rendered with PDFium and visually inspected; following text remained searchable and unclipped. Android debug APK, JVM tests and lint passed. Physical Android touch/IME/pinch interaction remains for device testing.
