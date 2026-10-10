# Review of accumulated changes — 10 October 2026

Scope: the current source includes the recent search popup, document-level export actions and unsaved blank-document behavior, as well as templates, assets, pagination/PDF, lifecycle, notifications, theme and release changes. Those new flows were preserved. This is a code/build/desktop review, not proof of behavior on a physical Android device.

## Findings fixed

|Severity|Finding and trigger|Fix|Evidence / limit|
|---|---|---|---|
|P1|Rapidly type into a new document and clear it before the Room list emits: UI checks a stale documents list and may skip deletion while a prior save remains queued.|Every edit snapshot goes through the ordered save queue. Blank/default-title classification and deletion happen under the repository IO mutex after JSON validation.|Blank classifier JVM tests; actual queue/repository Android regression added and compiled, execution pending.|
|P1|Delete the current document while its PDF export finishes: the retained current object lets export metadata save resurrect the deleted original.|Clear the matching current document before queuing its delete, so late editor/export callbacks cannot persist it.|Code path review; physical race test pending.|
|P1|Malformed JSON was treated as blank and could trigger draft deletion for a default title.|Malformed content is never classified as disposable blank; repository validates before modifying disk. Classification is depth-bounded.|Malformed/wrong-root/invalid-child JVM cases; original-preservation Android regression compiled.|
|P2|Blank detection reintroduced full JSON parsing in the per-keystroke UI edit path.|Moved that classification into the background repository transaction, reusing the serialization JSON object.|Code path review; Android typing latency still unmeasured.|
|P2|PDF preview recycled its previous bitmap before Compose necessarily stopped drawing it; cancellation was shown as a preview failure.|Removed premature recycle, leave displayed bitmap lifetime to GC, and rethrow coroutine cancellation.|Build/code inspection; repeated page navigation requires device validation.|
|P2|Oversized or malformed editor messages were silently ignored, risking unnoticed unsaved edits.|Explicit error messages; bridge envelope allowance is slightly larger than the document content limit.|Code review; very large Android IPC/IME behavior remains a device gate.|
|Release blocker|Target SDK 35 no longer meets current new-app submission target.|Compile/target API 36; debug and signed release builds validated separately.|API 36 behavior still needs device testing.|

## Reviewed without wholesale replacement

Template brace escaping remains centralized. Private asset filenames, MIME handling and direct JPEG embedding are consistent; alpha/signatures remain PNG. Image cleanup fails closed if a reference scan fails. PDF retention preserves latest/recent exports; saved external copies are outside cleanup. Foreground export has a notification/cancel action and dataSync declarations; it is not immune to OEM restrictions. Screen WebView lifecycle does not globally pause timers. Search popup/direct export flows and white document paper were retained. Release uses environment-provided signing values; no key/password belongs in source archives. Privacy/support identify AMAN KUMAR and the exact supplied email.

## Remaining limitations

- Native tests were compiled, not run: template taps, rapid clear/save/delete/export races, Gboard/other IMEs, lifecycle, notification permission, TalkBack, native printing and low storage/memory.
- Known strict PDFium extraction failure remains: repeated spaces can collapse. This is recorded, not hidden as a passing strict suite.
- Full JSON messages still cross the editor-to-native bridge; removing duplicate validation is not zero-cost main-thread processing.
- Version-based Tiptap audit still flags the locally mitigated moderate advisory. The regression patch is retained; no comprehensive dependency vulnerability certification is claimed.
- Signed AAB preparation is not Play publication/approval. Public privacy URL, real screenshots/FGS evidence, developer verification and applicable closed testing must be completed in Play Console.

See TEST-RESULTS.md and validation/audit/play-final-signed-gradle.log for final build evidence, and the Play-Store-Release folder for store material.
