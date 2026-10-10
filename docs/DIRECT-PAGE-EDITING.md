# Direct A4 editing — feasibility, 10 October 2026

Decision: preserve the single editable Write Mode and read-only Page Mode. No experimental page editor is enabled in this APK.

The current preview consists of cloned and sometimes split DOM nodes. Making those clones contenteditable would create multiple sources of truth: editing a fragment would no longer map directly to the original ProseMirror position, history or IME composition. It is not a safe incremental switch.

## Available approach and licensing

Tiptap's current [Pages documentation](https://tiptap.dev/docs/pages/getting-started/overview) describes a beta Pro extension available through its private registry with a Team subscription. It requires related schema/table packages; it is not a drop-in addition to this pinned Tiptap 2.11.5 application. The documented browser-print limitations also mean the existing offline export must be evaluated separately. No commercial package, account, subscription or cloud conversion has been added.

## Bounded prototype plan

Use a separate development fixture, one ProseMirror document and one EditorView, with page gaps represented by decorations rather than separate editable documents. Keep pagination transactions out of undo history. Map each decoration through transaction mappings. Suspend decoration updates from compositionstart through compositionend, then recompute the first affected page. Selection and document content must remain owned by ProseMirror. This is a design proposal, not a completed prototype or a demonstrated device implementation.

Compare this design with a licensed Pages evaluation only after confirming offline redistribution terms and exact package compatibility. Do not put registry tokens in an APK.

## Risks and acceptance gates

- Gboard Hindi composition, conjunct formation, dead keys, selection handles and backspace across a page boundary must preserve exact JSON text.
- Cursor movement and touch selection must cross visual gaps without losing focus or producing duplicate paragraphs.
- Undo/redo, paste across pages, table cell selection and image node selection must operate as single logical transactions.
- A4 geometry must agree with the shipped vector PDF exporter after zoom, font loading, rotation and margin changes.
- Long rows, oversized images and unavailable fonts must fail safely rather than repeatedly reflowing forever.
- Measure 1/5/20/50/100 pages on a low-memory physical Android device before enabling the feature.

Migration would remain opt-in behind a feature flag. Existing version-1 snapshots stay unchanged; keep a reversible snapshot before enabling new editor nodes. Start with plain paragraphs, then formatting, lists, tables and images. Roll back the visual editing layer without rewriting documents if any gate fails. Physical-device PDF/IME validation and a reliable page-editor prototype are still prerequisites.
