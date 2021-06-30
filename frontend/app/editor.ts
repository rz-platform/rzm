import { getHashedUrl } from './hashcode';
import { handleInput, handleScroll } from './highlighting';

const ua = window.navigator.userAgent.toLowerCase();
const isIE = !!ua.match(/msie|trident\/7|edge/);
const isWinPhone = ua.indexOf('windows phone') !== -1;
const isIOS = !isWinPhone && !!ua.match(/ipad|iphone|ipod/);
const isAndroid = ua.indexOf('android') > -1;

const isHighlightingEnabled = !(isIE || isWinPhone || isIOS || isAndroid);

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

function autosave(form: HTMLFormElement) {
  const xhr = new XMLHttpRequest();
  xhr.open(form.method, form.action, true);
  xhr.setRequestHeader('Content-type', 'application/x-www-form-urlencoded');

  xhr.onloadend = function (response: any) {
    if (response && response.target && response.target.status === 200) {
      console.log('form has been submitted successfully');
    }
  };
  const queryString = new URLSearchParams(new FormData(form) as any).toString();
  xhr.send(queryString);
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

  const lineCursor = <HTMLInputElement>document.getElementById('current-line');
  lineCursor.style.left = textarea.offsetWidth * 0.08 + 'px';
  maxHeight = textarea.offsetHeight;

  const backdrop = <HTMLElement>document.getElementById('backdrop');
  const highlights = <HTMLElement>document.getElementById('highlights');

  const form = document.getElementById('code-form');
  if (form) {
    // setInterval(function () { autosave(<HTMLFormElement>form) }, 10000);
    autosave(<HTMLFormElement>form);
  }

  calculateNewLines(textarea.value);
  currentLine = calculateCurrentLine(textarea.selectionEnd || 0);
  setLineHighlight(textarea, lineCursor, currentLine, maxHeight);
  handleInput(textarea, highlights);
  function update(e) {
    isHighlightingEnabled && handleScroll(textarea, backdrop);
    if (e.type == 'input') {
      isHighlightingEnabled && handleInput(textarea, highlights);
      callback();
      calculateNewLines(textarea.value);
    }
    currentLine = calculateCurrentLine(textarea.selectionEnd || 0);
    setLineHighlight(textarea, lineCursor, currentLine, maxHeight);
    if (e.type != 'scoll') {
      setDocOffset(textarea.selectionEnd || 0);
    }
  }

  ['keyup', 'click', 'scroll', 'select', 'mousedown', 'keydown', 'input', 'keypress'].forEach(event => {
    textarea.addEventListener(event, update);
  });
}
