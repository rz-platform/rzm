/** Constants */

const maxDepthInFileTree = 4;
const creationElId = 'rz-creation';
const creationFormId = 'rz-creation-form';
const creationInputId = 'rz-creation-form-input';

/** Utilities */

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
    '" class="rz-form">' +
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

export function addInlineInput(e: Event, isFolder: boolean): void {
  if (e.target) {
    const target = e.target as HTMLElement;
    let parentLevel = 4;
    if (e.target.tagName.toLowerCase() == 'img') {
      parentLevel += 1;
    }
    const parent = parentByLevel(target, parentLevel);
    const folder = document.getElementById('folder-icon');
    const file = document.getElementById('file-icon');

    if (parent && folder && file) {
      const iconSrc = isFolder ? folder.getAttribute('src') : file.getAttribute('src');
      if (iconSrc) {
        const depth = parseInt(<string>parent.getAttribute('depth'), 10) || 0;
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
        }
      }
    }
  }
}

export function toggleIsFolder(value: boolean): void {
  const isFolder = <HTMLInputElement>document.getElementById('new-item-form-is-folder');
  if (isFolder) {
    isFolder.checked = value;
  }
}

/** File tree hide and show buttons */

function changeTogglerIcon(target: HTMLElement, src: string, isDown: boolean) {
  if (isDown) {
    target.setAttribute('src', src.replace('down', 'next'));
  } else {
    target.setAttribute('src', src.replace('next', 'down'));
  }
}

function toggleSubTree(el: HTMLElement, isDown: boolean) {
  const menuItems = document.getElementsByClassName('rz-menu-item');
  const hash = el.getAttribute('hash');
  if (hash) {
    for (let i = 0; i < menuItems.length; i++) {
      const item = menuItems[i];
      if (item.getAttribute('parent') === hash) {
        if (isDown) {
          item.classList.add('hidden');
        } else {
          item.classList.remove('hidden');
        }
        const icon = document.getElementById('icon-' + hash);
        if (icon) {
          const src = icon.getAttribute('src');
          if (src) {
            changeTogglerIcon(icon, src, isDown);
          }
        }
        toggleSubTree(<HTMLElement>item, isDown);
      }
    }
  }
}

export function handleFileTree(target: HTMLElement): void {
  const parent = parentByLevel(target, 4);
  if (target && parent) {
    const src = target.getAttribute('src');
    if (src && src.indexOf('down') > -1) {
      changeTogglerIcon(target, src, true);
      toggleSubTree(parent, true);
    }
    if (src && src.indexOf('down') == -1) {
      changeTogglerIcon(target, src, false);
      toggleSubTree(parent, false);
    }
  }
}

export function scrollToActiveFile(): void {
  const fileTree = document.getElementById('rz-sidebar-filetree');
  const fileTreeChosen = document.getElementById('rz-menu-file-tree-chosen');

  if (fileTree && fileTreeChosen) {
    fileTree.scrollTo(0, getYOffset(fileTreeChosen) - fileTreeChosen.offsetHeight * 2);
  }
}
