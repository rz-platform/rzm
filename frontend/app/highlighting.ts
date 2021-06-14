function applyHighlights(t: string): string {
  return t.replace(/\n$/g, '\n\n').replace(/\\[A-za-z0–9_].*?\b/g, '<mark>$&</mark>');
}

export function handleInput(textarea: HTMLInputElement, highlights: HTMLElement): void {
  highlights.innerHTML = applyHighlights(textarea.value);
}

export function handleScroll(textarea: HTMLInputElement, backdrop: HTMLElement): void {
  backdrop.scrollTop = textarea.scrollTop;
}
