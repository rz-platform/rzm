import * as CodeMirror from 'codemirror/lib/codemirror.js';
import 'codemirror/mode/stex/stex.js';
import 'codemirror/addon/selection/active-line';
import 'codemirror/addon/edit/matchbrackets';

/**  JS helpers */

// https://stackoverflow.com/a/7616484/1064115
function hashCode(s: string): string {
  let hash = 0;
  if (s.length == 0) return hash.toString();
  for (let i = 0; i < s.length; i++) {
    hash = (hash << 5) - hash + s.charCodeAt(i);
    hash = hash & hash; // Convert to 32bit integer
  }
  return hash.toString();
}

function getYOffset(el: HTMLElement): number {
  let yOffset = 0;
  while (el && !isNaN(el.offsetLeft) && !isNaN(el.offsetTop)) {
    yOffset += el.offsetTop - el.scrollTop;
    if (el.offsetParent) {
      el = <HTMLElement>el.offsetParent;
    } else {
      break;
    }
  }
  return yOffset;
}

// Where referenceNode is the node you want to put newNode after.
// If referenceNode is the last child within its parent element, that's fine,
// because referenceNode.nextSibling will be null
// and insertBefore handles that case by adding to the end of the list.
function insertAfter(newNode: HTMLElement, referenceNode: HTMLElement): void {
  if (referenceNode.parentNode) {
    referenceNode.parentNode.insertBefore(newNode, referenceNode.nextSibling);
  }
}

function parentByLevel(el: HTMLElement, level: number): HTMLElement {
  for (let i = 0; i < level; i++) {
    if (el.parentElement) {
      el = el.parentElement;
    } else {
      break;
    }
  }
  return el;
}

/** Constants */

const maxDepthInFileTree = 4;
const creationElId = 'rz-creation';
const creationFormId = 'rz-creation-form';
const creationInputId = 'rz-creation-form-input';

interface Cursor {
  line: number; // Number of line, starts from zero
  ch: number; // Number of character
}

function saveCursor(documentHash: string, cursor: Cursor): void {
  localStorage.setItem(documentHash + '_line', cursor.line.toString());
  localStorage.setItem(documentHash + '_ch', cursor.ch.toString());
}

function getSavedCursor(documentHash: string): Cursor | void {
  const lineInStorage = localStorage.getItem(documentHash + '_line');
  const characterInStorage = localStorage.getItem(documentHash + '_ch');

  if (lineInStorage && characterInStorage) {
    const line = parseInt(lineInStorage, 10);
    const ch = parseInt(characterInStorage, 10);
    if (typeof line == 'number' && typeof ch == 'number' && !isNaN(line) && !isNaN(ch)) {
      return { ch, line };
    }
  }
}

/** Inline file and folder creation */

function buildInlineInputField(iconSrc: string, depth: number): HTMLElement {
  const d = depth.toString();
  const creationElement = document.getElementById(creationElId);
  if (creationElement) {
    creationElement.remove(); // remove if exists
  }
  const el = document.createElement('div');
  el.className = 'rz-menu-item';
  el.setAttribute('id', creationElId);
  el.innerHTML =
    '<div class="rz-menu-link">' +
    '<div class="rz-menu-file-tree-depth-' +
    d +
    '">' +
    '<div class="rz-menu-file-tree-depth-' +
    d +
    '-content">' +
    '<form id="' +
    creationFormId +
    '">' +
    '<img class="svg-icon" src="' +
    iconSrc +
    '" />' +
    '<input type="text" id="' +
    creationInputId +
    '" ' +
    'autocomplete="off"' +
    '/>' +
    '</form></div></div>';
  return el;
}

function submitFileCreation(e: Event): boolean {
  if (e.preventDefault) e.preventDefault();
  const creationInput = <HTMLInputElement>document.getElementById(creationInputId);
  const newItem = <HTMLInputElement>document.getElementById('new-item-form-name');
  const newItemForm = <HTMLFormElement>document.getElementById('new-item-form');
  if (creationInput && newItem && newItemForm) {
    const val = creationInput.value;
    newItem.value = val;
    if (val) {
      newItemForm.submit();
    }
  }
  // must return false to prevent the default form behavior
  return false;
}

function nextDepth(depth: number): number {
  return depth >= maxDepthInFileTree ? maxDepthInFileTree : depth + 1;
}

function addInlineInput(e: Event, isFolder: boolean): void {
  if (e.target) {
    const target = e.target as HTMLElement;
    const parent = parentByLevel(target, 4);

    const folder = document.getElementById('folder-icon');
    const file = document.getElementById('file-icon');

    if (parent && folder && file) {
      const iconSrc = isFolder ? folder.getAttribute('src') : file.getAttribute('src');
      if (iconSrc) {
        const depth = parseInt(<string>parent.getAttribute('depth'), 10);
        insertAfter(buildInlineInputField(iconSrc, nextDepth(depth)), parent);

        const form = <HTMLFormElement>document.getElementById(creationFormId);
        const newItemForm = <HTMLFormElement>document.getElementById('new-item-form');
        const newItemPath = <HTMLFormElement>document.getElementById('new-item-form-path');
        const creationInput = <HTMLInputElement>document.getElementById(creationInputId);
        if (form && newItemForm && creationInput && newItemPath) {
          // form.attachEvent
          const parent_path = parent.getAttribute('path');
          newItemPath.value = parent_path ? parent_path : '.';
          creationInput.focus();

          form.addEventListener('submit', submitFileCreation);
          // form.attachEvent('submit', submitFileCreation);
        }
      }
    }
  }
}

function toggleIsFolder(value: boolean): void {
  const isFolder = <HTMLInputElement>document.getElementById('new-item-form-is-folder');
  if (isFolder) {
    isFolder.checked = value;
  }
}

/** File tree hide and show buttons */

function toggleSubTree(el: HTMLElement) {
  const menuItems = document.getElementsByClassName('rz-menu-item');
  const hash = el.getAttribute('hash');
  if (hash) {
    for (let i = 0; i < menuItems.length; i++) {
      const item = menuItems[i];
      if (item.getAttribute('parent') === hash) {
        if (!item.classList.contains('hidden')) {
          item.classList.add('hidden');
        } else {
          item.classList.remove('hidden');
        }
        toggleSubTree(<HTMLElement>item);
      }
    }
  }
}

function clickHandler(event: MouseEvent) {
  if (event && event.target) {
    const target = event.target as HTMLElement;
    if (target.classList.contains('add-file-button')) {
      addInlineInput(event, false);
      toggleIsFolder(false);
    }
    if (target.classList.contains('add-folder-button')) {
      addInlineInput(event, true);
      toggleIsFolder(true);
    }
    if (target.classList.contains('file-tree-show')) {
      const el = event.target as HTMLElement;
      const parent = parentByLevel(el, 4);
      if (el && parent) {
        const src = el.getAttribute('src');
        if (src && src.indexOf('down') > -1) {
          el.setAttribute('src', src.replace('down', 'next'));
          toggleSubTree(parent);
        }
        if (src && src.indexOf('down') == -1) {
          el.setAttribute('src', src.replace('next', 'down'));
          toggleSubTree(parent);
        }
      }
    }
  }
}

function ready() {
  document.removeEventListener('click', clickHandler);

  document.addEventListener('click', clickHandler);

  const editorArea = document.getElementById('code');
  const fileSubmit = <HTMLInputElement>document.getElementById('file-save-submit');
  const currentDocumentHash = hashCode(window.location.href);

  if (editorArea && !editorArea.getAttribute('initialized')) {
    editorArea.setAttribute('initialized', '1');
    let unsaved = false;
    const editor = CodeMirror.fromTextArea(editorArea, {
      lineNumbers: true,
      styleActiveLine: true,
      matchBrackets: true,
      mode: 'text/x-stex',
      autofocus: true,
      lineWrapping: true,
      theme: 'rzm',
      smartIndent: false,
      tabSize: 4,
      indentWithTabs: false,
    });
    const cursor = getSavedCursor(currentDocumentHash);
    if (cursor) {
      editor.setCursor(cursor);
    }
    editor.on('cursorActivity', () => {
      saveCursor(currentDocumentHash, editor.getCursor());
    });
    editor.on('change', () => {
      if (!unsaved) {
        window.onbeforeunload = function () {
          return 'Data you have entered may not be saved';
          //if we return nothing here (just calling return;) then there will be no pop-up question at all
        };
        fileSubmit.value += '*';
        unsaved = true;
      }
    });
  }
}

interface TurboEvent extends Event {
  readonly data: {
    url: string;
  };
}

document.addEventListener('turbolinks:load', ((turboEvent: TurboEvent) => {
  const url = turboEvent.data.url;
  if (url && url.indexOf('repositories') > -1 && url.indexOf('tree') > -1) {
    ready();
  }
}) as EventListener);

// the script is meant to be executed after the document has been parsed, but before firing DOMContentLoaded.
// see: defer
ready();

/** Scroll to active file in file tree */
const fileTree = document.getElementById('rz-sidebar-filetree');
const fileTreeChosen = document.getElementById('rz-menu-file-tree-chosen');

if (fileTree && fileTreeChosen) {
  fileTree.scrollTo(0, getYOffset(fileTreeChosen) - fileTreeChosen.offsetHeight * 2);
}
