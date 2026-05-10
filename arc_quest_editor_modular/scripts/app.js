import { state, createBlankQuest } from './core/state.js';
import { getDomRefs } from './core/dom.js';
import { setByPath, ensureQuestShape } from './core/quest-shape.js';
import { validateQuest } from './core/validators.js';
import { exportJson, importJson, bindDragAndDropImport } from './app/import-export.js';
import { showToast, setDropOverlayVisible } from './app/toast.js';
import { applyPaneLayout, bindPaneResizers } from './app/layout.js';
import { ensureValidSelection, navigateToPath } from './app/navigation.js';
import { renderTree } from './renderers/tree-renderer.js';
import { renderCenterEditor } from './renderers/center-renderer.js';
import { renderSidePanel } from './renderers/side-panel-renderer.js';
import { renderStatus } from './renderers/status-renderer.js';
import { bindTreeSelection, bindEditorActions } from './renderers/event-bindings.js';

const dom = getDomRefs();

function rerender() {
  ensureQuestShape(state.q);
  ensureValidSelection(state);
  validateQuest(state);
  applyPaneLayout(state, dom);
  renderTree(state, dom.left);
  renderCenterEditor(state, dom.mid);
  renderSidePanel(
    state,
    dom.tabs,
    dom.right,
    tab => { state.ui.tab = tab; rerender(); },
    path => navigateToPath(state, rerender, path),
    pi => { state.ui.sel = { t: 'phase', pi }; rerender(); }
  );
  renderStatus(state, dom.status);
  bindTreeSelection(dom.left, state, rerender);
  bindEditorActions(dom.mid, state, rerender, setByPath);
}

dom.newBtn.onclick = () => {
  state.q = createBlankQuest();
  state.meta = { file: 'new_quest.json', dirty: false };
  state.ui.sel = { t: 'quest' };
  rerender();
};
dom.validateBtn.onclick = () => { state.ui.tab = 'validate'; rerender(); };
dom.exportBtn.onclick = () => exportJson(state, rerender, dom);
dom.fileInput.onchange = e => {
  const f = e.target.files[0];
  if (f) importJson(state, rerender, dom, f);
  e.target.value = '';
};

bindPaneResizers(state, dom);
bindDragAndDropImport(state, rerender, dom);
rerender();










