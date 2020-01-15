import * as CodeMirror from 'codemirror/lib/codemirror.js'
import 'codemirror/mode/textile/textile.js'

var editor = CodeMirror.fromTextArea(document.getElementById("code"), {
    lineNumbers: true,
    styleActiveLine: true,
    matchBrackets: true,
    mode: "text/x-tex",
});