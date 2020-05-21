"use strict";

import * as CodeMirror from 'codemirror/lib/codemirror.js'
import 'codemirror/mode/stex/stex.js'

var editor = CodeMirror.fromTextArea(document.getElementById("code"), {
    lineNumbers: true,
    styleActiveLine: true,
    matchBrackets: true,
    mode: "text/x-stex",
});
