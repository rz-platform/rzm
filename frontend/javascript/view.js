import * as CodeMirror from 'codemirror/lib/codemirror.js'
import 'codemirror/mode/stex/stex.js'
import 'codemirror/addon/selection/active-line'
import 'codemirror/addon/edit/matchbrackets'

// Constants

const maxDepthInFileTree = 4;

const mainForm = document.getElementById('new-item-form');
const mainFormPathInput = document.getElementById('new-item-form-path');
const mainFormNameInput = document.getElementById('new-item-form-name');
const mainFormIsFolder = document.getElementById('new-item-form-is-folder');

const documentIconSrc = document.getElementById('file-icon').getAttribute("src");
const folderIconSrc = document.getElementById('folder-icon').getAttribute("src");

const creationElId = 'rz-creation';
const creationFormId = 'rz-creation-form';
const creationInputId = 'rz-creation-form-input';

const addFilesButtonList = document.getElementsByClassName('add-file-button')
const addFoldersButtonList = document.getElementsByClassName('add-folder-button')

const codeArea = document.getElementById("code");
if (codeArea) {
    CodeMirror.fromTextArea(codeArea, {
        lineNumbers: true,
        styleActiveLine: true,
        matchBrackets: true,
        mode: "text/x-stex",
        autofocus: true,
        lineWrapping: true,
        theme: 'idea'
    });
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

function buildInputField(iconSrc, depth) {
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
    // You must return false to prevent the default form behavior
    return false;
}

function nextDepth(depth) {
    return depth >= maxDepthInFileTree ? maxDepthInFileTree : depth + 1;
}

function addInput(e, isFolder) {
    const iconSrc = isFolder ? folderIconSrc : documentIconSrc;
    const parent = parentByLevel(e.currentTarget, 4);
    const depth = parseInt(parent.getAttribute("depth"));
    mainFormPathInput.value = parent.getAttribute("path");
    insertAfter(buildInputField(iconSrc, nextDepth(depth)), parent);
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
        addInput(e, false);
        toggleIsFolder(false);
    })
}

for (let item of addFoldersButtonList) {
    item.addEventListener('click', function (e) {
        addInput(e, true);
        toggleIsFolder(true);
    })
}
