import { initializeEditor, clearAutoInterval } from './editor';
import { TurboEvent } from './types';
import { hashCode } from './hashcode';
import { toggleIsFolder, handleFileTree, addInlineInput, scrollToActiveFile } from './filetree';

function clickHandler(event: MouseEvent) {
  if (event && event.target) {
    const target = event.target as HTMLElement;
    if (target.classList.contains('add-file-button')) {
      event.preventDefault();
      addInlineInput(event, false);
      toggleIsFolder(false);
      return false;
    }
    if (target.classList.contains('add-folder-button')) {
      event.preventDefault();
      addInlineInput(event, true);
      toggleIsFolder(true);
      return false;
    }
    if (target.classList.contains('file-tree-show')) {
      const target = event.target as HTMLElement;
      handleFileTree(target);
    }
  }
}

(window as any).unsaved = false;

function onTypeCallback() {
  (window as any).unsaved = true;
  window.onbeforeunload = function () {
    if ((window as any).autosave) {
      (window as any).autosave();
    }
    return 'Data you have entered may not be saved';
    //if we return nothing here (just calling return;) then there will be no pop-up question at all
  };
}

function ready() {
  document.removeEventListener('click', clickHandler);

  document.addEventListener('click', clickHandler);

  const editorArea = <HTMLInputElement>document.getElementById('code');

  if (editorArea && !editorArea.getAttribute('initialized')) {
    editorArea.setAttribute('initialized', '1');
    (window as any).unsaved = false;
    initializeEditor(editorArea, onTypeCallback);
  }
}

document.addEventListener('turbolinks:load', ((turboEvent: TurboEvent) => {
  const url = turboEvent.data.url;
  (window as any).hashedUrl = hashCode(url);
  if (url && url.indexOf('repositories') > -1 && url.indexOf('tree') > -1) {
    ready();
  } else {
    clearAutoInterval();
  }
}) as EventListener);

// fires before Turbolinks issues a network request to fetch the next page
document.addEventListener('turbolinks:request-start', ((turboEvent: TurboEvent) => {
  if ((window as any).autosave) {
    (window as any).autosave();
  }
}) as EventListener);

// the script is meant to be executed after the document has been parsed, but before firing DOMContentLoaded.
// see: defer
ready();

/** Scroll to active file in file tree */
scrollToActiveFile();
