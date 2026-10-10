# Keyboard language-switch review

The supplied 16.87-second video shows keyboard appearing/disappearing while the document remains open (notably around 5.6 and 8.4 seconds in sampled frames). The user identifies language switching as the trigger. The recording alone does not establish which Android focus callback causes it.

Found a source-level race in EditorHost: evaluateJavascript requested DOM focus asynchronously while showSoftInput ran immediately. Native keyboard show could occur before the editable element regained focus. The recovery now waits for a JavaScript confirmation, then requests IME on the next frame. It uses hasFocus (including descendants), checks attachment/window focus/disposal, and cancels recovery on actual screen pause. No forced-show loop or document reload is introduced. Existing START/STOP lifecycle handling is retained.

The new restoreInputFocus method uses ProseMirror focus restoration, preserving current selection/content, and refuses to run in page-preview mode. Browser regression passes for caret preservation, unchanged content, Hindi insertion at the original caret, and refusing focus in preview. This is not an Android IME test. Physical Gboard English/Hindi switching, manual keyboard dismissal, backgrounding and dialogs still require retesting.

Only editor assets and Kotlin compilation were requested for this change. No APK/AAB packaging was requested or run in this keyboard-fix step. Existing installed APK does not contain this source change yet.
