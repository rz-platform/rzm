import { EditorView, keymap } from '@codemirror/view';
import { EditorState } from '@codemirror/state';
import { history, historyKeymap } from '@codemirror/history';
import { defaultKeymap } from '@codemirror/commands';
import { defaultHighlightStyle } from '@codemirror/highlight';
import { stex } from '@codemirror/legacy-modes/mode/stex';
import { StreamLanguage } from '@codemirror/stream-parser';

function editorFromTextArea(textarea, extensions) {
  let view = new EditorView({
    state: EditorState.create({ doc: textarea.value, extensions }),
  });
  textarea.parentNode.insertBefore(view.dom, textarea);
  textarea.style.display = 'none';
  if (textarea.form)
    textarea.form.addEventListener('submit', () => {
      textarea.value = view.state.doc.toString();
    });
  return view;
}

const extensions = [
  defaultHighlightStyle,
  history(),
  keymap.of([...defaultKeymap, ...historyKeymap]),
  StreamLanguage.define(stex),
];

const editor = editorFromTextArea(<HTMLElement>document.getElementById('code'), extensions);
editor.focus();
