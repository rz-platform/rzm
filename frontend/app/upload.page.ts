interface WebkitFile extends File {
  webkitRelativePath: string;
}

interface WebkitInputFile extends HTMLInputElement {
  webkitdirectory: boolean;
  mozdirectory: boolean;
}

const fileListing = <WebkitInputFile>document.getElementById('listing');
const inputFile = <WebkitInputFile>document.getElementById('filepicker');

const checkboxSingleFile = document.getElementById('single');
const checkboxDirectory = document.getElementById('directory');

if (checkboxSingleFile && checkboxDirectory && fileListing && inputFile) {
  checkboxSingleFile.addEventListener('change', () => {
    inputFile.webkitdirectory = false;
    inputFile.mozdirectory = false;
    inputFile.multiple = true;
    fileListing.innerHTML = '';
    inputFile.value = '';
  });

  checkboxDirectory.addEventListener('change', () => {
    inputFile.webkitdirectory = true;
    inputFile.mozdirectory = true;
    inputFile.multiple = false;
    fileListing.innerHTML = '';
    inputFile.value = '';
  });

  inputFile.addEventListener(
    'change',
    (event: Event) => {
      const target = event.target as HTMLInputElement;
      const files = target.files;

      if (files && files.length) {
        for (let i = 0; i < files.length; i++) {
          const item = document.createElement('li');
          const file = <WebkitFile>files[i];
          let name = file.name;
          if (file.webkitRelativePath && file.webkitRelativePath != '') {
            name = file.webkitRelativePath;
          }
          item.innerHTML = name;
          fileListing.appendChild(item);
        }
      }
    },
    false
  );
}
