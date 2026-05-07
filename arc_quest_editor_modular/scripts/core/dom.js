export function getDomRefs() {
  return {
    left: document.querySelector('#left'),
    mid: document.querySelector('#mid'),
    right: document.querySelector('#right'),
    tabs: document.querySelector('#tabs'),
    status: document.querySelector('#status'),
    newBtn: document.querySelector('#newBtn'),
    validateBtn: document.querySelector('#validateBtn'),
    exportBtn: document.querySelector('#exportBtn'),
    fileInput: document.querySelector('#fileInput'),
    appRoot: document.querySelector('#appRoot'),
    mainLayout: document.querySelector('#mainLayout'),
    rightContainer: document.querySelector('#right-container'),
    resizerLeft: document.querySelector('#resizerLeft'),
    resizerRight: document.querySelector('#resizerRight'),
    dropOverlay: document.querySelector('#dropOverlay'),
    dropSubtitle: document.querySelector('#dropSubtitle'),
    toastStack: document.querySelector('#toastStack')
  };
}
