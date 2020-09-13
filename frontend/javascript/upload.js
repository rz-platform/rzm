const inputFile = document.getElementById("filepicker");

document.getElementById("single").addEventListener('change', function() {
    inputFile.webkitdirectory = false;
    inputFile.mozdirectory = false;
    inputFile.multiple = true;
    document.getElementById("listing").innerHTML = "";
    inputFile.value = "";
})

document.getElementById("directory").addEventListener('change', function() {
    inputFile.webkitdirectory = true;
    inputFile.mozdirectory = true;
    inputFile.multiple = false;
    document.getElementById("listing").innerHTML = "";
    inputFile.value = "";
})

inputFile.addEventListener("change", function(event) {
    let output = document.getElementById("listing");
    let files = event.target.files;

    for (let i=0; i < files.length; i++) {
        let item = document.createElement("li");
        console.log(files[i])
        let name = files[i].name;
        if (files[i].webkitRelativePath && files[i].webkitRelativePath != "") {
            name = files[i].webkitRelativePath;
        }
        item.innerHTML = name;
        output.appendChild(item);
    };
}, false);
