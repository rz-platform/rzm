import * as CodeMirror from 'codemirror/lib/codemirror.js'
import 'codemirror/mode/stex/stex.js'
import 'codemirror/addon/selection/active-line'
import 'codemirror/addon/edit/matchbrackets'

/**  JS helpers */

// https://stackoverflow.com/a/7616484/1064115
String.prototype.hashCode = function(){
    let hash = 0;
    if (this.length == 0) return hash;
    for (let i = 0; i < this.length; i++) {
        let char = this.charCodeAt(i);
        hash = ((hash<<5)-hash)+char;
        hash = hash & hash; // Convert to 32bit integer
    }
    return hash.toString();
}

// Where referenceNode is the node you want to put newNode after.
// If referenceNode is the last child within its parent element, that's fine,
// because referenceNode.nextSibling will be null
// and insertBefore handles that case by adding to the end of the list.
function insertAfter(newNode, referenceNode) {
    referenceNode.parentNode.insertBefore(newNode, referenceNode.nextSibling);
}

function parentByLevel(el, level) {
    for (let i = 0; i < level; i++) {
        el = el.parentElement;
    }
    return el;
}

/** Constants */

const maxDepthInFileTree = 4;

const editorArea = document.getElementById("code");

const menuItems = document.getElementsByClassName('rz-menu-item');

const mainForm = document.getElementById('new-item-form');
const mainFormPathInput = document.getElementById('new-item-form-path');
const mainFormNameInput = document.getElementById('new-item-form-name');
const mainFormIsFolder = document.getElementById('new-item-form-is-folder');

const fileSubmit = document.getElementById('file-save-submit');

const documentIconSrc = document.getElementById('file-icon').getAttribute("src");
const folderIconSrc = document.getElementById('folder-icon').getAttribute("src");

const creationElId = 'rz-creation';
const creationFormId = 'rz-creation-form';
const creationInputId = 'rz-creation-form-input';

const addFilesButtonList = document.getElementsByClassName('add-file-button');
const addFoldersButtonList = document.getElementsByClassName('add-folder-button');

const showSubTreeButtonList = document.getElementsByClassName('file-tree-show');

const currentDocumentHash = window.location.href.hashCode();

function saveCursor(cursor) {
    localStorage.setItem(currentDocumentHash + '_line', cursor.line);
    localStorage.setItem(currentDocumentHash + '_ch', cursor.ch);
}

function getSavedCursor() {
    const line = parseInt(localStorage.getItem(currentDocumentHash + '_line'));
    const ch = parseInt(localStorage.getItem(currentDocumentHash + '_ch'));
    if (typeof line == 'number' && typeof ch == 'number' && !isNaN(line) && !isNaN(ch)) {
        return {'ch': ch, 'line': line}
    }
}

// in case of binary file page will not contain textarea
if (editorArea) {
    let unsaved = false;
    const editor = CodeMirror.fromTextArea(editorArea, {
        lineNumbers: true,
        styleActiveLine: true,
        matchBrackets: true,
        mode: "text/x-stex",
        autofocus: true,
        lineWrapping: true,
        theme: 'idea',
        smartIndent: false,
        tabSize: 4,
        indentWithTabs: false,
    });
    const cursor = getSavedCursor();
    if (cursor) {
        editor.setCursor(cursor);
    }
    editor.on("cursorActivity", function () {
        saveCursor(editor.getCursor());
    });
    editor.on('change', function(){
        if (!unsaved) {
            window.onbeforeunload = function() {
                return "Data you have entered may not be saved";
                //if we return nothing here (just calling return;) then there will be no pop-up question at all
            };
            fileSubmit.value += "*";
            unsaved = true;
        }
    });
}

/** Inline file and folder creation */

function buildInlineInputField(iconSrc, depth) {
    const creationElement = document.getElementById(creationElId);
    if (creationElement) {
        creationElement.remove(); // remove if exists
    }
    let el = document.createElement("div");
    el.className = 'rz-menu-item';
    el.setAttribute("id", creationElId);
    el.innerHTML =
        '<div class="rz-menu-link">' +
        '<div class="rz-menu-file-tree-depth-' + depth + '">' +
        '<div class="rz-menu-file-tree-depth-' + depth + '-content">' +
        '<form id="' + creationFormId + '">' +
        '<img class="svg-icon" src="' + iconSrc + '" />' +
        '<input type="text" id="' + creationInputId +'" />' +
        '</form></div></div>';
    return el
}

function submitFileCreation(e) {
    if (e.preventDefault) e.preventDefault();
    const val = document.getElementById(creationInputId).value
    mainFormNameInput.value = val;

    if (val) {
        mainForm.submit();
    }
    // must return false to prevent the default form behavior
    return false;
}

function nextDepth(depth) {
    return depth >= maxDepthInFileTree ? maxDepthInFileTree : depth + 1;
}

function addInlineInput(e, isFolder) {
    const iconSrc = isFolder ? folderIconSrc : documentIconSrc;
    const parent = parentByLevel(e.currentTarget, 4);
    const depth = parseInt(parent.getAttribute("depth"));
    mainFormPathInput.value = parent.getAttribute("path");
    insertAfter(buildInlineInputField(iconSrc, nextDepth(depth)), parent);
    document.getElementById(creationInputId).focus();
    const form = document.getElementById(creationFormId);
    if (form.attachEvent) {
        form.attachEvent("submit", submitFileCreation);
    } else {
        form.addEventListener("submit", submitFileCreation);
    }
}

function toggleIsFolder(value) {
    mainFormIsFolder.checked = value;
}

for (let item of addFilesButtonList) {
    item.addEventListener('click', function (e) {
        addInlineInput(e, false);
        toggleIsFolder(false);
    })
}

for (let item of addFoldersButtonList) {
    item.addEventListener('click', function (e) {
        addInlineInput(e, true);
        toggleIsFolder(true);
    })
}

/** File tree hide and show buttons */

function hideSubTree(el) {
    const hash = el.getAttribute("hash");
    for (let item of menuItems) {
        if (item.getAttribute("parent") === hash && !item.classList.contains("hidden")) {
            item.classList.add("hidden");
            hideSubTree(item);
        }
    }
}

function showSubTree(el) {
    const hash = el.getAttribute("hash");
    for (let item of menuItems) {
        if (item.getAttribute("parent") === hash && item.classList.contains("hidden")) {
            item.classList.remove("hidden");
            showSubTree(item);
        }
    }
}


for (let item of showSubTreeButtonList) {
    item.addEventListener('click', function (e) {
        const el = e.currentTarget;
        const parent = parentByLevel(el, 4);
        if (el.getAttribute("src").includes("down")) {
            el.setAttribute("src", el.getAttribute("src").replace("down", "next"));
            hideSubTree(parent);
        } else {
            el.setAttribute("src", el.getAttribute("src").replace("next", "down"));
            showSubTree(parent);
        }
    })
}
