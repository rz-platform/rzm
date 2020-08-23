import * as CodeMirror from 'codemirror/lib/codemirror.js'
import 'codemirror/mode/stex/stex.js'
import 'codemirror/addon/selection/active-line'
import 'codemirror/addon/edit/matchbrackets'

var editor = CodeMirror.fromTextArea(document.getElementById("code"), {
    lineNumbers: true,
    styleActiveLine: true,
    matchBrackets: true,
    mode: "text/x-stex",
    autofocus: true,
    lineWrapping: true,
    theme: 'idea'
});

// Where referenceNode is the node you want to put newNode after.
// If referenceNode is the last child within its parent element, that's fine, because referenceNode.nextSibling will be null
// and insertBefore handles that case by adding to the end of the list.
function insertAfter(newNode, referenceNode) {
    referenceNode.parentNode.insertBefore(newNode, referenceNode.nextSibling);
}

function parentByLevel(el, level) {
    let parent = el;
    for (let i = 0; i < level; i++) {
        el = el.parentElement;
    }
    return el;
}

const creationElId = 'rz-creation';
const creationFormId = 'rz-creation-form';
const creationInputId = 'rz-creation-form-input';

const addFilesButtonList = document.getElementsByClassName('add-file-button')

function getFileInputField(depth) {
    const creationForm = document.getElementById(creationFormId);
    if (creationForm) {
        creationForm.remove(); // remove if exists
    }

    let el = document.createElement("div");
    el.className = 'rz-menu-item';
    el.setAttribute("id", creationElId);
    el.innerHTML =
    '<div class="rz-menu-link">' +
    '<div class="rz-menu-file-tree-depth-' + depth + '">' +
    '<div class="rz-menu-file-tree-depth-' + depth + '-content">' +
    '<form id="' + creationFormId + '">' +
    '<input type="text" placeholder="New file name" id="' + creationInputId +'" />' +
    '</form></div></div>';
    return el
}

function submitFileCreation(e) {
    if (e.preventDefault) e.preventDefault();
    console.log(e);
    // You must return false to prevent the default form behavior
    return false;
}

for (let item of addFilesButtonList) {
    item.addEventListener('click', function (e) {
        const parent = parentByLevel(e.currentTarget, 4);
        const depth = parseInt(parent.getAttribute("depth"));
        insertAfter(getFileInputField(depth+1), parent);
        document.getElementById(creationInputId).focus();
        const form = document.getElementById(creationFormId);
        if (form.attachEvent) {
            form.attachEvent("submit", submitFileCreation);
        } else {
            form.addEventListener("submit", submitFileCreation);
        }
    })
}
