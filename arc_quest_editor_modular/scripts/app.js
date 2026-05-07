import { state, createBlankQuest } from './core/state.js';
import { getDomRefs } from './core/dom.js';
import { setByPath, ensureQuestShape } from './core/quest-shape.js';
import { validateQuest } from './core/validators.js';
import { normalizeImportedQuest } from './core/import-normalizer.js';
import { exportQuestToDatapack } from './core/export-normalizer.js';
import { renderTree } from './renderers/tree-renderer.js';
import { renderCenterEditor } from './renderers/center-renderer.js';
import { renderSidePanel } from './renderers/side-panel-renderer.js';
import { renderStatus } from './renderers/status-renderer.js';
import { bindTreeSelection, bindEditorActions } from './renderers/event-bindings.js';

const dom = getDomRefs();

function ensureValidSelection() {
  const sel = state.ui.sel || { t: 'quest' };
  const phaseCount = state.q.phases?.length || 0;
  if (sel.t === 'phase' || sel.t === 'obj') {
    if (!phaseCount) {
      state.ui.sel = { t: 'quest' };
      return;
    }
    if (!Number.isInteger(sel.pi) || sel.pi < 0 || sel.pi >= phaseCount) {
      state.ui.sel = { t: 'phase', pi: Math.max(0, Math.min(phaseCount - 1, Number(sel.pi) || 0)) };
      return;
    }
  }
  if (sel.t === 'obj') {
    const objectives = state.q.phases[sel.pi]?.objectives || [];
    if (!objectives.length) {
      state.ui.sel = { t: 'phase', pi: sel.pi };
      return;
    }
    if (!Number.isInteger(sel.oi) || sel.oi < 0 || sel.oi >= objectives.length) {
      state.ui.sel = { t: 'phase', pi: sel.pi };
    }
  }
}

function navigateToPath(path) {
  if (!path) return;
  if (path === 'quest' || path === 'visual' || path === 'rewards' || path === 'raw') {
    state.ui.sel = { t: path };
    return rerender();
  }
  if (path.startsWith('phase:')) {
    state.ui.sel = { t: 'phase', pi: Number(path.split(':')[1]) };
    return rerender();
  }
  if (path.startsWith('objective:')) {
    const [, pi, oi] = path.split(':');
    state.ui.sel = { t: 'obj', pi: Number(pi), oi: Number(oi) };
    return rerender();
  }
}

function rerender() {
  ensureQuestShape(state.q);
  ensureValidSelection();
  validateQuest(state);
  renderTree(state, dom.left);
  renderCenterEditor(state, dom.mid);
  renderSidePanel(
    state,
    dom.tabs,
    dom.right,
    tab => { state.ui.tab = tab; rerender(); },
    navigateToPath,
    pi => { state.ui.sel = { t: 'phase', pi }; rerender(); }
  );
  renderStatus(state, dom.status);
  bindTreeSelection(dom.left, state, rerender);
  bindEditorActions(dom.mid, state, rerender, setByPath);
}

function exportJson() {
  const exported = exportQuestToDatapack(state.q);
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([JSON.stringify(exported, null, 2)], { type: 'application/json' }));
  a.download = state.meta.file;
  a.click();
  state.meta.dirty = false;
  rerender();
}

function importJson(file) {
  const r = new FileReader();
  r.onload = () => {
    try {
      state.q = normalizeImportedQuest(JSON.parse(r.result));
      state.meta.file = file.name;
      state.meta.dirty = false;
      state.ui.sel = { t: 'quest' };
      rerender();
    } catch (err) {
      alert('JSON 解析失败: ' + err.message);
    }
  };
  r.readAsText(file, 'utf-8');
}

dom.newBtn.onclick = () => { state.q = createBlankQuest(); state.meta = { file: 'new_quest.json', dirty: false }; state.ui.sel = { t: 'quest' }; rerender(); };
dom.validateBtn.onclick = () => { state.ui.tab = 'validate'; rerender(); };
dom.exportBtn.onclick = exportJson;
dom.fileInput.onchange = e => { const f = e.target.files[0]; if (f) importJson(f); };

rerender();
