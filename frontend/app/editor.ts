import { getHashedUrl } from './hashcode';

const storageOffsetPostfix = '_offset';

const baseSize = 24;

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

function setLineHighlight(textarea: HTMLInputElement, backdrop: HTMLInputElement, currentLine: number) {
  const scrollTop = textarea.scrollTop;
  const offset = baseSize * (currentLine - 1) - scrollTop;

  backdrop.style.top = offset + "px";
}

function calculateCurrentLine(textarea: HTMLInputElement): number {
  const selection = textarea.selectionStart;
  if (selection) {
    return textarea.value.substr(0, selection).split("\n").length || 0;    
  } else {
    return 0;
  }
}

let currentLine: number = 0;

export function initializeEditor(textarea: HTMLInputElement, callback: () => void): void {
  textarea.focus();
  textarea.setSelectionRange(getDocOffset(), getDocOffset());

  const backdrop = <HTMLInputElement>document.getElementById('backdrop');
  backdrop.style.width = textarea.offsetWidth + "px";

  currentLine = calculateCurrentLine(textarea);
  setLineHighlight(textarea, backdrop, currentLine);
  textarea.onclick = textarea.oncontextmenu = function () {
    currentLine = calculateCurrentLine(textarea);
    setLineHighlight(textarea, backdrop, currentLine);
    setDocOffset(textarea.selectionStart);
  };

  textarea.oninput = function (e) {
    currentLine = calculateCurrentLine(textarea);
    setLineHighlight(textarea, backdrop, currentLine);
    setDocOffset(textarea.selectionStart);
    callback();
  };

  textarea.onkeyup = ({ key }) => {
    setLineHighlight(textarea, backdrop, currentLine);
    if (['Arrow', 'Page', 'Home', 'End'].some(type => key.substring(0, type.length) === type)) {
      setDocOffset(textarea.selectionStart);
    }
  };
  textarea.onscroll = function (e) {
    setLineHighlight(textarea, backdrop, currentLine);
  }
}
