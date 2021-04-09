import { getHashedUrl } from './hashcode';

const storageOffsetPostfix = '_offset';

function setDocOffset(offset: number | null): void {
  if (offset) {
    localStorage.setItem(getHashedUrl() + storageOffsetPostfix, offset.toString());
  }
}

function getDocOffset(): number | null {
  const offsetInStorage = localStorage.getItem(getHashedUrl() + storageOffsetPostfix);

  if (offsetInStorage) {
    const offset = parseInt(offsetInStorage, 10);
    if (typeof offset == 'number' && !isNaN(offset)) {
      return offset;
    }
  }
  return null;
}

export function initializeEditor(textarea: HTMLInputElement, callback: () => void): void {
  textarea.focus();
  textarea.setSelectionRange(getDocOffset(), getDocOffset());

  textarea.onclick = textarea.oncontextmenu = function () {
    setDocOffset(textarea.selectionStart);
  };

  textarea.oninput = function () {
    setDocOffset(textarea.selectionStart);
    callback();
  };

  textarea.onkeyup = ({ key }) => {
    if (['Arrow', 'Page', 'Home', 'End'].some(type => key.substring(0, type.length) === type)) {
      setDocOffset(textarea.selectionStart);
    }
  };
}
