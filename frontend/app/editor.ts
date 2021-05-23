import { getHashedUrl } from './hashcode';

const storageOffsetPostfix = '_offset';

const baseSize = 24;
const newLine = '\n';
let newLines: number[] = [];

function setDocOffset(offset: number): void {
  localStorage.setItem(getHashedUrl() + storageOffsetPostfix, offset.toString());
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

function setLineHighlight(
  textarea: HTMLInputElement,
  backdrop: HTMLInputElement,
  currentLine: number,
  maxHeight: number
) {
  const scrollTop = textarea.scrollTop;
  const offset = baseSize * ((currentLine || 1) - 1) - scrollTop;
  if (offset >= maxHeight || offset < 0) {
    backdrop.style.display = 'none';
  } else {
    backdrop.style.display = 'block';
    backdrop.style.top = offset + 'px';
  }
}

function calculateNewLines(str: string) {
  newLines = [];
  let index = -2;
  while (index != -1) {
    index = str.indexOf(newLine, index + 1);
    if (index != -1) newLines.push(index);
  }
}

function calculateCurrentLine(selection: number): number {
  let i = 0;
  const l = newLines.length;
  while (i < l) {
    if (selection <= newLines[i]) {
      return i + 1;
    }
    i += 1;
  }
  return l + 1;
}

let currentLine: number = 0;
let maxHeight: number = 0;
export function initializeEditor(textarea: HTMLInputElement, callback: () => void): void {
  textarea.focus();
  textarea.setSelectionRange(getDocOffset(), getDocOffset());

  const backdrop = <HTMLInputElement>document.getElementById('backdrop');
  backdrop.style.left = textarea.offsetWidth * 0.08 + 'px';
  maxHeight = textarea.offsetHeight;

  calculateNewLines(textarea.value);
  currentLine = calculateCurrentLine(textarea.selectionEnd || 0);
  setLineHighlight(textarea, backdrop, currentLine, maxHeight);
  function update(e) {
    if (e.type == 'input') {
      callback();
      calculateNewLines(textarea.value);
    }
    currentLine = calculateCurrentLine(textarea.selectionEnd || 0);
    setLineHighlight(textarea, backdrop, currentLine, maxHeight);
    if (e.type != 'scoll') {
      setDocOffset(textarea.selectionEnd || 0);
    }
  }

  ['keyup', 'click', 'scroll', 'select', 'mousedown', 'keydown', 'input', 'keypress'].forEach(event => {
    textarea.addEventListener(event, update);
  });
}
