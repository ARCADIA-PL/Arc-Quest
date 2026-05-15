import '../styles/editor.css';
import {state, createBlankQuest} from './core/state.js';
import {createNpcSkeleton, createDialogueSkeleton} from './core/factories.js';
import {getDomRefs} from './core/dom.js';
import {setByPath, ensureQuestShape} from './core/quest-shape.js';
import {setNpcByPath} from './core/npc-shape.js';
import {validateQuest} from './core/validators.js';
import {validateNpc} from './core/npc-validators.js';
import {exportJson, importJson, bindDragAndDropImport} from './app/import-export.js';
import {validateCrossReferences} from './core/cross-validator.js';
import {applyPaneLayout, bindPaneResizers} from './app/layout.js';
import {ensureValidSelection, navigateToPath} from './app/navigation.js';
import {renderTree} from './renderers/tree-renderer.js';
import {renderCenterEditor, renderNpcCenter} from './renderers/center-renderer.js';
import {renderSidePanel} from './renderers/side-panel-renderer.js';
import {renderStatus} from './renderers/status-renderer.js';
import {bindTreeSelection, bindEditorActions, bindNpcEditorActions} from './renderers/event-bindings.js';

const dom = getDomRefs();

function renderQuest() {
    ensureQuestShape(state.quest.q);
    ensureValidSelection(state);
    validateQuest(state);
    applyPaneLayout(state, dom);
    renderTree(state, dom.left);
    renderCenterEditor(state, dom.mid);
    renderSidePanel(
        state,
        dom.tabs,
        dom.right,
        tab => {
            state.quest.ui.tab = tab;
            rerender();
        },
        path => navigateToPath(state, rerender, path),
        pi => {
            state.quest.ui.sel = {t: 'phase', pi};
            rerender();
        }
    );
    renderStatus(state, dom.status);
    bindTreeSelection(dom.left, state, rerender);
    bindEditorActions(dom.mid, state, rerender, setByPath);
}

function renderNpc() {
    validateNpc(state.npc.q);
    state.npc.diag = validateNpc(state.npc.q);
    applyPaneLayout(state, dom);
    dom.left.innerHTML = '';
    renderNpcCenter(state, dom.mid);
    renderSidePanel(
        state,
        dom.tabs,
        dom.right,
        tab => {
            state.quest.ui.tab = tab;
            rerender();
        },
        path => navigateToPath(state, rerender, path),
        pi => {
            state.quest.ui.sel = {t: 'phase', pi};
            rerender();
        }
    );
    renderStatus(state, dom.status);
    bindNpcEditorActions(dom.mid, state, rerender, setNpcByPath);
}

function renderDialogue() {
    dom.mid.innerHTML = `<div class="fade-in sec" style="text-align:center;padding:60px 20px;">
        <div style="font-size:48px;margin-bottom:16px;opacity:.3;">—</div>
        <h3 style="font-size:18px;color:var(--text-main);">对话树编辑器</h3>
        <div class="small" style="margin-top:8px;">即将推出</div>
    </div>`;
    dom.left.innerHTML = '';
    dom.right.innerHTML = '';
    dom.status.innerHTML = '<span style="color:var(--text-mut)">Dialogue Mode · Coming Soon</span>';
}

function rerender() {
    if (state.mode === 'npc') return renderNpc();
    if (state.mode === 'dialogue') return renderDialogue();
    renderQuest();
}

dom.newBtn.onclick = () => {
    if (state.mode === 'npc') {
        state.npc.q = createNpcSkeleton();
        state.npc.meta = {file: 'new_npc.json', dirty: false};
    } else if (state.mode === 'dialogue') {
        state.dialogue.q = createDialogueSkeleton();
        state.dialogue.meta = {file: 'new_dialogue.json', dirty: false};
    } else {
        state.quest.q = createBlankQuest();
        state.quest.meta = {file: 'new_quest.json', dirty: false};
        state.quest.ui.sel = {t: 'quest'};
    }
    rerender();
};
dom.validateBtn.onclick = () => {
    if (state.mode === 'npc') {
        state.npc.diag = validateNpc(state.npc.q);
    }
    state.quest.ui.tab = 'validate';
    rerender();
};
dom.exportBtn.onclick = () => exportJson(state, rerender, dom);
dom.fileInput.onchange = e => {
    const f = e.target.files[0];
    if (f) importJson(state, rerender, dom, f);
    e.target.value = '';
};

bindPaneResizers(state, dom);
bindDragAndDropImport(state, rerender, dom);

if (dom.modeBar) {
    dom.modeBar.onclick = e => {
        const tab = e.target.closest('[data-mode]');
        if (!tab) return;
        state.mode = tab.dataset.mode;
        dom.modeBar.querySelectorAll('.mode-tab').forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        rerender();
    };
}

if (dom.importLibBtn) {
    dom.importLibBtn.onclick = () => {
        dom.fileInput.click();
        dom.fileInput.dataset.libraryImport = 'true';
    };
}

if (dom.crossBtn) {
    dom.crossBtn.onclick = () => {
        state.mode = 'quest';
        state.quest.crossResults = validateCrossReferences(state.registry, state.quest.q);
        state.quest.ui.tab = 'cross';
        dom.modeBar.querySelectorAll('.mode-tab').forEach(t => t.classList.remove('active'));
        const questTab = dom.modeBar.querySelector('[data-mode="quest"]');
        if (questTab) questTab.classList.add('active');
        rerender();
    };
}

rerender();
