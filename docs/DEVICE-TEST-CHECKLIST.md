# Physical-device checks

Install the new debug APK as an update; keep saved documents. No device tests were run here.

- Tap each of 16 template cards, preview fields, use template, back and reopen. Confirm the original crash is gone.
- Type Hindi with Gboard and a second IME: composition, matras, cursor, selection, paste, undo/redo, rotation and app background/restore.
- Reopen large documents through the new boot stream; confirm no blank editor and exports still start. Test 1/5/20/50/100 pages on your actual device.
- Import an opaque camera photo, transparent logo and signature; rotate EXIF photo, resize/align, delete, undo and reopen. Check PDF quality and file sizes.
- Duplicate a document with images; delete one copy; ensure the other opens/exports. Cleanup occurs only after restart and 24-hour age grace. Pending export images must survive.
- Test tables: row/column insert/delete, resize, merge/split, borders, multi-page header repetition and explicit oversized-row error.
- Export Hindi/English mixed content. Search and copy matras/nukta/joiners in Android PDF readers. Repeated spaces can collapse in PDFium; report other character changes.
- Save as, share, print page ranges/orientation, cancellation, low storage and app/process interruption. Confirm originals remain editable.
- Confirm portrait/landscape preview and exported page count; the page preview itself is read-only.

## Export/lifecycle follow-up

- Start a long export, immediately minimize, switch apps and lock/unlock the phone. Check progress notification, completion and explicit timeout/failure behavior.
- Cancel from notification and from app; verify no finalized partial PDF and that the original remains editable.
- On Android 13+, test notifications allowed and denied. Export must remain available in both cases.
- Background/resume the editor during Hindi composition and during an export; check selection, content and Undo are intact.
- With TalkBack enabled, find the named editor, enter/edit/select Hindi and English, use native toolbar buttons, navigate tables/images, and distinguish read-only preview. Check no duplicate measurement text is spoken.
- Retention: keep the latest PDF for each document; old earlier versions past seven days are removed on a later cleanup. Saved external PDFs must remain.

## Home UX follow-up

- Home has only the hero creation action; Documents retains New document FAB.
- Swipe the Home template carousel in both directions: cards should move to the screen edge; initial/end insets should remain 20 dp.
- Scroll Home/Documents while searching: the search field stays under the top bar and filters results.
- Check red Delete icon/text, existing confirmation, and Hindi/English labels.
- With large font and a small screen, check search and bottom navigation do not hide scrollable content.
- In dark theme, writing paper stays white; black and colored text remain readable and retain their colors in PDF.
