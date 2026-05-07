// (完全保持你原始的导入和事件派发逻辑，此处无需做业务变更，只需保持原样即可运行最新的 UI)
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

function applyPaneLayout() {
  if (!dom.mainLayout) return;
  const left = Math.max(220, Math.min(520, Number(state.ui.paneSizes?.left) || 280));
  const right = Math.max(280, Math.min(720, Number(state.ui.paneSizes?.right) || 380));
  dom.mainLayout.style.gridTemplateColumns = `${left}px 8px minmax(420px, 1fr) 8px ${right}px`;
}

function bindPaneResizers() {
  if (!dom.mainLayout || !dom.resizerLeft || !dom.resizerRight) return;

  const minLeft = 220;
  const maxLeft = 520;
  const minRight = 280;
  const maxRight = 720;
  const minCenter = 420;

  const startDrag = side => event => {
    event.preventDefault();
    const rect = dom.mainLayout.getBoundingClientRect();
    const startX = event.clientX;
    const startLeft = Number(state.ui.paneSizes.left) || 280;
    const startRight = Number(state.ui.paneSizes.right) || 380;

    document.body.classList.add('is-resizing-panes');

    const onMove = moveEvent => {
      const dx = moveEvent.clientX - startX;
      if (side === 'left') {
        const nextLeft = Math.max(minLeft, Math.min(maxLeft, startLeft + dx));
        const centerWidth = rect.width - nextLeft - startRight - 16;
        if (centerWidth >= minCenter) {
          state.ui.paneSizes.left = nextLeft;
          applyPaneLayout();
        }
      } else {
        const nextRight = Math.max(minRight, Math.min(maxRight, startRight - dx));
        const centerWidth = rect.width - startLeft - nextRight - 16;
        if (centerWidth >= minCenter) {
          state.ui.paneSizes.right = nextRight;
          applyPaneLayout();
        }
      }
    };

    const onUp = () => {
      document.body.classList.remove('is-resizing-panes');
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    };

    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  };

  dom.resizerLeft.onmousedown = startDrag('left');
  dom.resizerRight.onmousedown = startDrag('right');
}

function rerender() {
  ensureQuestShape(state.q);
  ensureValidSelection();
  validateQuest(state);
  applyPaneLayout();
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

function showToast(title, message, kind = 'info', duration = 2400) {
  if (!dom.toastStack) return;
  const toast = document.createElement('div');
  toast.className = `toast ${kind}`;
  toast.innerHTML = `<div class="toast-title">${title}</div><div class="toast-msg">${message}</div>`;
  dom.toastStack.appendChild(toast);
  window.setTimeout(() => {
    toast.remove();
  }, duration);
}

function setDropOverlayVisible(visible, message = '松开以导入 Arc Quest 任务文件') {
  if (dom.dropOverlay) {
    dom.dropOverlay.classList.toggle('visible', visible);
    dom.dropOverlay.setAttribute('aria-hidden', visible ? 'false' : 'true');
  }
  if (dom.mainLayout) dom.mainLayout.classList.toggle('drag-target', visible);
  if (dom.dropSubtitle) dom.dropSubtitle.textContent = message;
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
      showToast('导入成功', `已载入 ${file.name}`, 'success');
    } catch (err) {
      showToast('导入失败', `JSON 解析失败：${err.message}`, 'error', 3600);
      alert('JSON 解析失败: ' + err.message);
    }
  };
  r.readAsText(file, 'utf-8');
}

function getFirstSupportedFile(fileList) {
  return [...(fileList || [])].find(item => item?.name?.toLowerCase().endsWith('.json') || item?.type === 'application/json') || null;
}

function importFirstSupportedFile(fileList) {
  const file = getFirstSupportedFile(fileList);
  if (!file) {
    showToast('无法导入', '请拖入 quest JSON 文件，而不是其它格式。', 'error', 3200);
    alert('请拖入 quest JSON 文件。');
    return;
  }
  importJson(file);
}

function bindDragAndDropImport() {
  if (!dom.appRoot) return;
  let dragDepth = 0;

  window.addEventListener('dragenter', e => {
    e.preventDefault();
    dragDepth += 1;
    const hasSupportedFile = getFirstSupportedFile(e.dataTransfer?.files);
    setDropOverlayVisible(true, hasSupportedFile ? '松开以导入 Arc Quest 任务文件' : '请拖入 .json quest 文件');
  });

  window.addEventListener('dragover', e => {
    e.preventDefault();
    if (e.dataTransfer) e.dataTransfer.dropEffect = 'copy';
    const hasSupportedFile = getFirstSupportedFile(e.dataTransfer?.files);
    setDropOverlayVisible(true, hasSupportedFile ? '松开以导入 Arc Quest 任务文件' : '请拖入 .json quest 文件');
  });

  window.addEventListener('dragleave', e => {
    e.preventDefault();
    dragDepth = Math.max(0, dragDepth - 1);
    if (dragDepth === 0) setDropOverlayVisible(false);
  });

  window.addEventListener('drop', e => {
    e.preventDefault();
    dragDepth = 0;
    setDropOverlayVisible(false);
    importFirstSupportedFile(e.dataTransfer?.files);
  });
}

dom.newBtn.onclick = () => {
  state.q = createBlankQuest();
  state.meta = { file: 'new_quest.json', dirty: false };
  state.ui.sel = { t: 'quest' };
  rerender();
};
dom.validateBtn.onclick = () => { state.ui.tab = 'validate'; rerender(); };
dom.exportBtn.onclick = exportJson;
dom.fileInput.onchange = e => {
  const f = e.target.files[0];
  if (f) importJson(f);
  e.target.value = '';
};

bindPaneResizers();
bindDragAndDropImport();
rerender();
