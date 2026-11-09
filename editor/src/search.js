import { Extension } from '@tiptap/core';
import { Plugin, PluginKey, TextSelection } from '@tiptap/pm/state';
import { Decoration, DecorationSet } from '@tiptap/pm/view';
import { closeHistory } from '@tiptap/pm/history';

const key = new PluginKey('documentSearch');
const empty = () => ({ query: '', matchCase: false, wholeWord: false, matches: [], index: 0, limited: false, decorations: DecorationSet.empty });
const wordChar = /[\p{L}\p{N}\p{M}_\u200c\u200d]/u;
function scan(doc, options) {
  const matches = [];
  if (!options.query) return { matches, limited: false };
  const literal = options.query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const re = new RegExp(literal, options.matchCase ? 'gu' : 'giu');
  let limited = false;
  doc.descendants((node, pos) => {
    if (limited) return false;
    if (!node.isTextblock) return;
    let text = '';
    node.forEach(child => { text += child.isText ? child.text : '\ufffc'.repeat(child.nodeSize); });
    re.lastIndex = 0;
    for (const match of text.matchAll(re)) {
      const start = match.index, end = start + match[0].length;
      if (options.wholeWord) {
        // Inspect exactly the preceding code point, including astral characters.
        const before = [...text.slice(Math.max(0, start - 2), start)].at(-1) || '';
        const after = text.slice(end).match(/^./u)?.[0] || '';
        if (wordChar.test(before) || wordChar.test(after)) continue;
      }
      if (matches.length === 5000) { limited = true; break; }
      matches.push({ from: pos + 1 + start, to: pos + 1 + end });
    }
    return false;
  });
  return { matches, limited };
}

export function searchExtension(send) {
  return Extension.create({ name: 'documentSearch', addProseMirrorPlugins() {
    return [new Plugin({
      key,
      state: {
        init: empty,
        apply(tr, previous) {
          const action = tr.getMeta(key);
          if (action?.close) return empty();
          if (!action && !tr.docChanged) return previous;
          const next = { ...previous, ...action };
          if (tr.docChanged || action?.query !== undefined || action?.matchCase !== undefined || action?.wholeWord !== undefined) {
            Object.assign(next, scan(tr.doc, next));
          }
          next.index = next.matches.length ? Math.max(0, Math.min(next.index, next.matches.length - 1)) : 0;
          next.decorations = DecorationSet.create(tr.doc, next.matches.map((m, i) => Decoration.inline(m.from, m.to, {
            class: i === next.index ? 'search-match-current' : 'search-match',
            style: `background-color:${i === next.index ? '#ffb74d' : '#fff59d'};color:#111827;`,
          })));
          return next;
        },
      },
      props: { decorations(state) {
        return key.getState(state).decorations;
      } },
      view(view) {
        let last;
        const publish = () => {
          const s = key.getState(view.state);
          const value = JSON.stringify([s.query, s.matchCase, s.wholeWord, s.matches.length, s.index, s.limited]);
          if (value !== last) { last = value; send('search', { query: s.query, matchCase: s.matchCase, wholeWord: s.wholeWord, total: s.matches.length, index: s.matches.length ? s.index + 1 : 0, limited: s.limited }); }
        };
        publish();
        return { update: publish };
      },
    })];
  } });
}

function reveal(editor) {
  const s = key.getState(editor.state), match = s.matches[s.index];
  if (!match) return;
  // Do not focus the WebView: the native Find field must keep its keyboard.
  editor.view.dispatch(editor.state.tr.setSelection(TextSelection.create(editor.state.doc, match.from, match.to)).scrollIntoView().setMeta('addToHistory', false));
}

export function searchCommand(editor, name, value, editable) {
  if (!['find', 'findNext', 'findPrevious', 'findClose', 'replaceOne', 'replaceAll'].includes(name)) return false;
  let s = key.getState(editor.state);
  if (name === 'findClose') editor.view.dispatch(editor.state.tr.setMeta(key, { close: true }).setMeta('addToHistory', false));
  else if (name === 'find') {
    const options = JSON.parse(value || '{}');
    editor.view.dispatch(editor.state.tr.setMeta(key, { query: String(options.query || '').slice(0, 256), matchCase: !!options.matchCase, wholeWord: !!options.wholeWord, index: 0 }).setMeta('addToHistory', false));
    reveal(editor);
  } else if (name === 'findNext' || name === 'findPrevious') {
    if (s.matches.length) {
      editor.view.dispatch(editor.state.tr.setMeta(key, { index: (s.index + (name === 'findNext' ? 1 : -1) + s.matches.length) % s.matches.length }).setMeta('addToHistory', false));
      reveal(editor);
    }
  } else if (editable && s.matches.length) {
    if (s.limited && name === 'replaceAll') throw Error('Too many matches. Refine the search before replacing all.');
    const replacement = String(value ?? '').slice(0, 10000);
    const matches = name === 'replaceAll' ? s.matches : [s.matches[s.index]];
    let tr = closeHistory(editor.state.tr);
    for (const m of [...matches].reverse()) {
      const marks = editor.state.doc.nodeAt(m.from)?.marks || editor.state.doc.resolve(m.from).marks();
      if (replacement) tr.replaceWith(m.from, m.to, editor.schema.text(replacement, marks));
      else tr.delete(m.from, m.to);
    }
    editor.view.dispatch(tr);
    editor.view.dispatch(closeHistory(editor.state.tr).setMeta('addToHistory', false));
    reveal(editor);
  }
  return true;
}
