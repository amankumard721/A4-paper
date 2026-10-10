# Home and editor UX review — 10 October 2026

Version 1.1.2 (4).

- Home retains its Create blank document hero as the single large creation action. The floating New document button is now exclusive to Documents. Home no longer reserves the unnecessary FAB-sized bottom gap.
- Home LazyColumn has no horizontal outer content padding. Hero, headings, empty state and document cards receive their own 20 dp insets. Its template carousel is a full-width LazyRow with 20 dp contentPadding, 12 dp spacing and stable template IDs, allowing cards to scroll to the viewport edge. Other tabs retain their existing inset layouts.
- Home and Documents search fields live in the Scaffold topBar below TopAppBar, outside the scrolling list. Filtering behavior remains unchanged. Templates search retains its existing position.
- Document menu has Rename, Duplicate and Delete icons; Delete text and icon use the theme error color. The existing deletion confirmation remains.
- Editing paper now remains white with dark default text in both light and dark themes. App chrome continues following the chosen theme. Explicit text colors are preserved in the editor and PDF. Automatically replacing stored black text with white was rejected because white would disappear on the white PDF page. Existing imported/user-specified white text is not silently recolored.

Verification: Kotlin/Compose build, unit tests and lint; browser computed-color checks for white paper and explicit dark text in dark mode; screenshot inspected; one-page PDF export; existing streamed boot, JPEG export and accessibility smoke checks. Native Home scrolling, pinned search, menu colors and large-font/small-screen layout still require physical-device visual validation; no emulator was used. These cosmetic changes do not establish that the previously documented PDFium repeated-space issue is resolved.
