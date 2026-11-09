# Settings and image import — 2 November 2026

Settings now uses an inset-aware pinned top app bar, its own scroll state, bounded content above the navigation dock, a compact app/version header, icon section headers and wrapping48dp language/theme choices. Existing settings, privacy controls and support actions remain connected. Colours follow the current light/dark theme.

Home Import & Convert > Image opens Android Photo Picker (images only). A selected image is copied through the existing bounded AssetStore (20MB input cap, orientation correction, resize, metadata-free JPEG/PNG), then saved in a new A4 document and opened in review mode. The user can edit/resize it and export/save/share a PDF. Image aspect ratio and available page dimensions determine initial width. Very tall images that cannot fit within the editor minimum width are rejected with a crop instruction. Cancelling the picker creates no document; import failure reports an error. No broad photo-storage permission added.

Android debug compilation/packaging passed. Initial full tests:44total,43passed,1Windows symlink skip. Final aspect-ratio guard and layout-boundary changes compiled and lint passed with0errors/35warnings. APK signature and16KB ZIP alignment verified. Physical photo-picker, touch, light/dark, large-font and on-device screenshot review remain to be checked on the user's phone.
