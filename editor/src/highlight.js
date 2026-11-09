import { Mark } from '@tiptap/core';

export const Highlight = Mark.create({
  name: 'highlight',
  addAttributes() { return { color: {
    default: '#fff59d',
    parseHTML: element => element.getAttribute('data-highlight') || '#fff59d',
    renderHTML: attrs => /^#[0-9a-f]{6}$/i.test(attrs.color) ? { 'data-highlight': attrs.color, style: `background-color:${attrs.color};color:inherit;` } : {},
  } }; },
  parseHTML() { return [{ tag: 'mark[data-highlight]' }]; },
  renderHTML({ HTMLAttributes }) { return ['mark', HTMLAttributes, 0]; },
});
