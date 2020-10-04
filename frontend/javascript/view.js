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

function getYOffset(el) {
    var yOffset = 0;
    while (el && !isNaN(el.offsetLeft) && !isNaN(el.offsetTop)) {
        yOffset += el.offsetTop - el.scrollTop;
        el = el.offsetParent;
    }
    return yOffset;
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
const creationElId = 'rz-creation';
const creationFormId = 'rz-creation-form';
const creationInputId = 'rz-creation-form-input';


function saveCursor(documentHash, cursor) {
    localStorage.setItem(documentHash + '_line', cursor.line);
    localStorage.setItem(documentHash + '_ch', cursor.ch);
}

function getSavedCursor(documentHash) {
    const line = parseInt(localStorage.getItem(documentHash + '_line'));
    const ch = parseInt(localStorage.getItem(documentHash + '_ch'));
    if (typeof line == 'number' && typeof ch == 'number' && !isNaN(line) && !isNaN(ch)) {
        return {'ch': ch, 'line': line}
    }
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
    document.getElementById('new-item-form-name').value = val;

    if (val) {
        document.getElementById('new-item-form').submit();
    }
    // must return false to prevent the default form behavior
    return false;
}

function nextDepth(depth) {
    return depth >= maxDepthInFileTree ? maxDepthInFileTree : depth + 1;
}

function addInlineInput(e, isFolder) {
    const iconSrc = isFolder ?
     document.getElementById('folder-icon').getAttribute("src")
     : document.getElementById('file-icon').getAttribute("src");
    const parent = parentByLevel(e.target, 4);
    console.log(parent);
    const depth = parseInt(parent.getAttribute("depth"));
    document.getElementById('new-item-form-path').value = parent.getAttribute("path");
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
    document.getElementById('new-item-form-is-folder').checked = value;
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

function ready() {
    console.log('fired')
    const menuItems = document.getElementsByClassName('rz-menu-item');

    const mainForm = document.getElementById('new-item-form');
    const mainFormNameInput = document.getElementById('new-item-form-name');

    const fileSubmit = document.getElementById('file-save-submit');

    const addFilesButtonList = document.getElementsByClassName('add-file-button');
    const addFoldersButtonList = document.getElementsByClassName('add-folder-button');

    const showSubTreeButtonList = document.getElementsByClassName('file-tree-show');

    const currentDocumentHash = window.location.href.hashCode();

    for (let item of showSubTreeButtonList) {
//        item.removeEventListener('click');
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

//    document.removeEventListener("click");
    document.addEventListener("click", event => { // TODO: move into function
      if (event.target.classList.contains("add-file-button")) {
        addInlineInput(event, false);
        toggleIsFolder(false);
      }
      if (event.target.classList.contains("add-folder-button")) {
        addInlineInput(event, true);
        toggleIsFolder(true);
      }
      if (event.target.classList.contains("file-tree-show")) {
        const el = event.target;
        const parent = parentByLevel(el, 4);
        if (el.getAttribute("src").includes("down")) {
          el.setAttribute("src", el.getAttribute("src").replace("down", "next"));
          hideSubTree(parent);
        } else {
          el.setAttribute("src", el.getAttribute("src").replace("next", "down"));
          showSubTree(parent);
        }
      }
    });

    const editorArea = document.getElementById("code");
    if (editorArea && !editorArea.getAttribute("initialized")) {
        editorArea.setAttribute("initialized", "1");
        let unsaved = false;
        const editor = CodeMirror.fromTextArea(editorArea, {
            lineNumbers: true,
            styleActiveLine: true,
            matchBrackets: true,
            mode: "text/x-stex",
            autofocus: true,
            lineWrapping: true,
            theme: 'rzm',
            smartIndent: false,
            tabSize: 4,
            indentWithTabs: false,
        });
        const cursor = getSavedCursor(currentDocumentHash);
        if (cursor) {
            editor.setCursor(cursor);
        }
        editor.on("cursorActivity", function () {
            saveCursor(currentDocumentHash, editor.getCursor());
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
}

document.addEventListener("turbolinks:load", function(event) {
    const url = event.data.url;
    if(url.indexOf('repositories') > -1 && url.indexOf('tree') > -1) {
        ready();
    }
});

// the script is meant to be executed after the document has been parsed, but before firing DOMContentLoaded.
// see: defer
ready();

/** Scroll to active file in file tree */
const fileTree = document.getElementById('rz-sidebar-filetree');
const fileTreeChosen = document.getElementById('rz-menu-file-tree-chosen');

if (fileTree && fileTreeChosen) {
    fileTree.scrollTo(0, getYOffset(fileTreeChosen) - fileTreeChosen.offsetHeight * 2)
}
