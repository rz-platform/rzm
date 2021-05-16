import { initializeEditor } from './editor';
import { TurboEvent } from './types';
import { hashCode } from './hashcode';
import { toggleIsFolder, handleFileTree, addInlineInput, scrollToActiveFile } from './filetree';

function clickHandler(event: MouseEvent) {
  if (event && event.target) {
    const target = event.target as HTMLElement;
    if (target.classList.contains('add-file-button')) {
      addInlineInput(event, false);
      toggleIsFolder(false);
      event.preventDefault();
      return false;
    }
    if (target.classList.contains('add-folder-button')) {
      addInlineInput(event, true);
      toggleIsFolder(true);
      event.preventDefault();
      return false;
    }
    if (target.classList.contains('file-tree-show')) {
      const target = event.target as HTMLElement;
      handleFileTree(target);
    }
  }
}

let unsaved = false;
let fileSubmit: HTMLInputElement | null = null;

function onTypeCallback() {
  if (!unsaved && fileSubmit) {
    window.onbeforeunload = function () {
      return 'Data you have entered may not be saved';
      //if we return nothing here (just calling return;) then there will be no pop-up question at all
    };
    fileSubmit.value += '*';
    unsaved = true;
  }
}

function ready() {
  document.removeEventListener('click', clickHandler);

  document.addEventListener('click', clickHandler);

  const editorArea = <HTMLInputElement>document.getElementById('code');
  fileSubmit = <HTMLInputElement>document.getElementById('file-save-submit');

  if (editorArea && !editorArea.getAttribute('initialized')) {
    editorArea.setAttribute('initialized', '1');
    unsaved = false;
    initializeEditor(editorArea, onTypeCallback);
  }
}

document.addEventListener('turbolinks:load', ((turboEvent: TurboEvent) => {
  const url = turboEvent.data.url;
  (window as any).hashedUrl = hashCode(url);
  if (url && url.indexOf('repositories') > -1 && url.indexOf('tree') > -1) {
    ready();
  }
}) as EventListener);

// the script is meant to be executed after the document has been parsed, but before firing DOMContentLoaded.
// see: defer
ready();

/** Scroll to active file in file tree */
scrollToActiveFile();
