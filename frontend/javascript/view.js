"use strict";

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
