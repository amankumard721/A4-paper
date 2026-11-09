# Mobile editor research and implementation

Updated 15 October 2026. App: A4 Paper Typing 1.5.0 (9).

## Design decisions

The goal is familiar document tools with room to write on a phone. This update does not claim complete Microsoft Word or Google Docs compatibility.

- Keep the writing canvas visible, with a compact quick toolbar for bold, italic and underline, plus explicit Format and Insert actions. Word mobile uses an expandable ribbon to expose additional commands without permanently consuming the document area. [Microsoft guidance](https://support.microsoft.com/en-us/word/show-or-hide-the-ribbon-in-word-for-mobile-devices)
- Separate detailed tools into Text, Paragraph, Insert and Page tabs. Google Docs Android similarly distinguishes text formatting and paragraph formatting. [Google Docs formatting](https://support.google.com/docs/answer/1663349?co=GENIE.Platform%3DAndroid&hl=en)
- Show table/image adjustments when the relevant object is selected. Preserve the existing drag resizing and table builder instead of mixing every object action into text formatting.
- Provide 48 dp quick actions, labeled controls and scrollable bounded tool panels. [Android accessibility guidance](https://support.google.com/accessibility/android/answer/7101858?community_post=true&hl=en)
- Keep page setup and review/export discoverable separately from typing controls. [Google Docs Android page settings](https://support.google.com/docs/answer/10296604?co=GENIE.Platform%3DAndroid&hl=en-GB)

## Delivered

1. Compact toolbar and organized formatting sheet; simplified editor header and contextual table/image adjustment entry.
2. Inline Find & Replace: next/previous, case matching, whole-word matching, replace one/all and empty replacement for deletion. Literal searches support Hindi and text split across formatting runs. Searches do not cross paragraph boundaries. Searches are capped at 5,000 matches; bulk replacement is disabled when results exceed this limit.
3. Replacement preserves the starting text style and Replace All is one undo step. Review/page modes reject editing commands. Search decorations do not become saved content or PDF formatting.
4. Persistent text highlight, including measured highlight backgrounds in vector PDF export.
5. Correct save-state labels, cached word/character statistics, paragraph boundaries in counts, and heading selection that does not toggle an already selected heading off.

## Verification

- Browser power-editor suite passed: Unicode search, styled runs, case/whole-word matching, navigation, replacements, undo/redo, readonly safety, highlight persistence/removal, PDF highlight and heading behavior.
- Existing font-color, media-resize and hardening suites passed. These cover touch/mouse column resizing, image bounds and aspect ratio, undo/reload, readonly handles, Hindi, multipage tables and vector export.
- Exported highlighted Hindi/English text was rasterized from the actual PDF and visually inspected; background fills align with text.
- Final Android debug compilation, lint and APK build passed after formatting and search-response guarding. Lint retains a dependency-update warning; no errors. APK signature verified, and the copy in OnePlus storage matches the build's SHA-256.

## Remaining limits and phone checks

Physical-device UX has not been verified in this development environment. On the OnePlus, check keyboard language switching, small-screen/landscape tool panels, selection retention while applying formats, drag resizing, pinch zoom, and saved PDF sharing.

This is an offline document editor, not full Word parity. Live collaboration, track changes/comments, equations, footnotes, DOCX export and full Word layout fidelity are not delivered by this update. Existing DOCX import is retained; advanced imported layouts still require review before PDF export.
