# BUILD A4 LEKHAK — COMPLETE ANDROID APPLICATION

## YOUR ROLE

Act as an expert autonomous Android development team specializing in:

- Kotlin and Jetpack Compose
- Material Design 3
- Tiptap and ProseMirror rich-text editing
- Android WebView integration
- Document pagination and typography
- Unicode Hindi and English text rendering
- PDF generation and printing
- Offline database architecture
- Android security, performance and accessibility
- Automated testing and production builds

**Your task is to BUILD the actual application, not merely describe it.**

Create a complete Android Studio project named:

**A4 Lekhak — Document Maker & PDF Creator**

Package: `com.a4lekhak.documentmaker`

## 1. TECHNOLOGY STACK

Use the following architecture:

- Language: Kotlin
- Android UI: Jetpack Compose
- Design system: Material 3
- Rich-text editor: Tiptap + ProseMirror
- Editor host: Android WebView
- Editor-to-Android communication: Secure JavaScript bridge
- Data format: Versioned ProseMirror-compatible JSON
- Database: Room
- Preferences: DataStore
- Architecture: Clean Architecture + MVVM
- Async tasks: Kotlin Coroutines + Flow
- Dependency injection: Hilt
- Document layout: Dedicated A4 pagination engine
- PDF generation: Evaluated and tested PDF rendering solution
- PDF preview: Android PdfRenderer or suitable tested renderer
- File saving: Storage Access Framework
- File sharing: Android Sharesheet + FileProvider
- Printing: Android Print Framework

Use compatible stable dependency versions verified against the project build environment.

Do not blindly choose a PDF library. Test whether it supports correct Devanagari text shaping, accurate pagination and text extraction.

## 2. MAIN PURPOSE

Create an offline-first mobile application where users can:

1. Type documents.
2. Format paragraphs and text.
3. Create applications, resumes, letters and notices.
4. Select templates.
5. View documents on real A4-sized pages.
6. Edit and save documents.
7. Generate high-quality printable PDFs.
8. Share PDFs using WhatsApp and other apps.
9. Print documents through Android.
10. Reopen and edit previously saved documents.

The application must support Hindi Unicode, English and mixed-language text.

No mandatory login.

No mandatory internet connection.

No watermark on exported PDFs.

## 3. UI/UX REQUIREMENTS

Create a premium, modern, simple productivity application.

Color palette:

Primary Blue: #2563EB  
Accent Teal: #0F766E  
Light Background: #F1F5F9  
Dark Background: #0B1220  
Document Paper: #FFFFFF

Support light and dark themes.

The printable document must retain a white paper background by default, regardless of the app theme.

Use responsive, accessible Material 3 components.

### Required Screens

1. Splash and onboarding
2. Home dashboard
3. Template library
4. Template preview
5. Document editor
6. A4 page preview
7. Page setup
8. PDF preview
9. PDF export/share
10. My Documents
11. Settings

### Home Dashboard

Include:

- Create Blank Document
- Popular Templates
- Recent Documents
- Search
- Bottom navigation: Home, Templates, Documents, Settings

Make all buttons functional.

## 4. DOCUMENT EDITOR

Use Tiptap/ProseMirror inside a securely configured Android WebView.

Bundle all editor assets locally. Do not require loading the editor from a remote CDN.

Support:

- English typing
- Hindi Unicode typing
- Mixed Hindi-English typing
- Bold, italic and underline
- Font size
- Font family
- Text color
- Left, center, right and justified alignment
- Paragraph spacing
- Line spacing
- Bulleted and numbered lists
- Undo and redo
- Copy, cut and paste
- Select all
- Insert date
- Insert page break
- Clear formatting

Create an ergonomic formatting toolbar using Jetpack Compose.

The Android keyboard must work correctly with the editor.

Preserve cursor position, text selection, formatting and document state during normal interactions.

Use secure communication between Compose and WebView.

Never expose the JavaScript bridge to untrusted websites.

## 5. TWO EDITOR MODES

### Write Mode

Optimized for comfortable mobile typing.

Features:

- Full-height text editor
- Keyboard-aware toolbar
- Editable document title
- Autosave indicator
- Undo/redo
- Document settings
- PDF preview shortcut

### Page Mode

Display content in A4 paper format.

Features:

- White pages
- Real A4 aspect ratio
- Page boundaries
- Zoom controls
- Correct margins
- Page count
- Automatic page breaks
- Multi-page scrolling

If accurate direct editing on paginated pages is not feasible initially, provide a reliable preview-only Page Mode.

Never claim that the visual page is fully editable unless the functionality really exists.

## 6. A4 DOCUMENT ENGINE

Default A4 dimensions:

210 × 297 mm

PDF dimensions approximately:

595.28 × 841.89 points.

Default margins:

15 mm on all sides.

Provide custom margins, orientation, line spacing and font settings.

Build a dedicated document pagination layer.

Requirements:

- Automatic multi-page documents
- Correct word wrapping
- Accurate text measurement
- Manual page breaks
- No text clipping
- No unexpected empty pages
- Correct Hindi conjuncts and matras
- Stable text layout
- Consistent paragraph formatting

The page preview and final PDF must use the same validated layout information whenever possible.

Create tests to compare preview pagination against actual PDF pagination.

Never export an entire document by taking a low-resolution screenshot.

## 7. PDF ENGINE

PDF quality is a critical requirement.

Implement:

- Genuine A4 paper dimensions
- Correct page count
- Printable output
- Hindi and English text
- Accurate margins
- Bold, italic and underline
- Paragraph alignment
- Multipage support
- High-quality text rendering
- No unwanted cropping
- No watermark

Prefer correctly encoded, selectable and searchable text where supported.

Evaluate potential approaches including browser print layout, dedicated PDF libraries and native Android PDF APIs.

Do not assume any approach guarantees correct Hindi text.

Select the final PDF implementation only after verifying:

1. Hindi rendering
2. Mixed-script rendering
3. Text extraction
4. Automatic page breaks
5. Actual A4 PDF dimensions
6. Preview consistency

Generate PDFs locally without transmitting document contents to external services.

## 8. DOCUMENT TEMPLATES

Build an editable template system.

Include at least:

- School leave application
- College leave application
- Formal application
- Bank request letter
- Electricity complaint
- General office letter
- Cover letter
- Basic resume
- Biodata
- Declaration
- Notice
- Meeting notes
- General agreement draft
- Invoice
- Quotation
- Assignment cover page

Templates must be fully editable.

Use structured content with fillable placeholders.

Do not create official-looking government documents or misleading claims of certification.

## 9. LOCAL DOCUMENT MANAGEMENT

Use Room for metadata and reliable app-private storage for document content and assets.

Every document must have:

- Unique ID
- Title
- Created date
- Modified date
- Document JSON
- Formatting data
- Page settings
- Schema version
- Referenced assets
- Export metadata

Support:

- Create
- Save
- Autosave
- Open
- Rename
- Duplicate
- Delete
- Search
- Sort
- Re-edit after PDF export

Implement safe saving and draft recovery.

Changes must not be silently lost when the app is backgrounded or restarted.

## 10. PDF SHARE, SAVE AND PRINT

Use Android-native integrations.

### Save

Open the system file picker and save a valid PDF to the user's selected destination.

### Share

Use content URIs and Android Sharesheet.

Provide temporary read access to receiving applications.

### Print

Open Android's native Print interface.

Support A4 and correct document orientation.

Handle unavailable printers and cancellations gracefully.

All three actions must work with the generated document.

## 11. SETTINGS

Include:

- App language: Hindi / English
- Light / Dark / System theme
- Default paper size
- Default margins
- Default font
- Default font size
- Default line spacing
- Autosave preference
- Document management
- Privacy policy
- App information

Save settings persistently.

## 12. PERFORMANCE AND SECURITY

Requirements:

- Smooth mobile typing
- No long-running tasks on UI thread
- Incremental or cached pagination where practical
- Efficient PDF page rendering
- Reliable background exports
- No unnecessary permissions
- No compulsory internet
- Secure local files
- No logging of private document contents
- Proper error handling
- Accessible UI
- Responsive tablet support where practical

## 13. DEVELOPMENT PLAN

Implement in this order:

### Phase 1 — Android Foundation
Create Android Studio project, themes, navigation and Home UI.

### Phase 2 — Rich Text Editor
Integrate locally bundled Tiptap/ProseMirror with Compose and build a working formatting toolbar.

### Phase 3 — Offline Storage
Implement document creation, structured saving, autosave, reopening and draft recovery.

### Phase 4 — A4 Layout
Implement margins, page measurements, pagination and Page Mode.

### Phase 5 — PDF Export
Build and validate real multi-page A4 PDF generation, including Hindi.

### Phase 6 — Printing and Sharing
Implement PDF preview, Save As, Sharesheet and Android printing.

### Phase 7 — Templates
Add the editable template library and document manager.

### Phase 8 — Testing and Release
Fix build errors, validate key functionality, optimize performance and prepare release configurations.

## 14. ACCEPTANCE TESTS

The app must pass a real end-to-end workflow:

1. Launch the app.
2. Create a blank document.
3. Enter Hindi and English text.
4. Apply bold, alignment and font changes.
5. Save and reopen the document.
6. Verify formatting is preserved.
7. Preview the document as A4 pages.
8. Generate a PDF.
9. Open the actual PDF.
10. Verify A4 size and page count.
11. Verify Hindi renders correctly.
12. Verify content is not clipped.
13. Save the PDF to the device.
14. Share using Android Sharesheet.
15. Open Android Print.
16. Edit the original document again.

Test multi-page documents, including long paragraphs and at least a 10-page example.

Document limitations and test failures clearly.

## 15. BUILD AND DELIVERY REQUIREMENTS

Create a real importable Android Studio project.

Include:

- Complete Kotlin source
- Jetpack Compose screens
- Tiptap/ProseMirror assets and integration
- Gradle configuration
- Manifest
- Room database
- Document models
- A4 pagination engine
- PDF implementation
- Save, Share and Print integration
- Templates
- Resources
- Tests
- README
- Build instructions

Run available Gradle builds and tests.

Correct actual compilation errors.

If Android SDK and build tools are available, produce a working debug APK.

Prepare release configuration and AAB generation instructions, but never fabricate signing keys.

Do not claim a build or test passed unless it actually ran successfully.

## 16. STRICT AI AGENT RULES

- Do not stop after creating UI mockups.
- Do not create nonfunctional buttons.
- Do not replace important features with TODO comments.
- Do not falsely claim perfect PDF fidelity.
- Do not use fake data to demonstrate completed functionality.
- Do not bypass Hindi rendering tests.
- Do not deliver only architecture diagrams.
- Do not call the project production-ready without verification.
- Keep the implementation modular and maintainable.
- Verify each phase before progressing.
- If a feature cannot be completed, identify it precisely rather than hiding the limitation.

## FINAL INSTRUCTION

**START BUILDING THE ACTUAL APPLICATION.**

Inspect the working environment, create the project and implement the system module by module.

Prioritize a fully working offline A4 document editor with reliable Hindi-English PDF export, saving, sharing and printing.

Finish with the complete project, available build artifacts, test results and a clear list of any remaining limitations.