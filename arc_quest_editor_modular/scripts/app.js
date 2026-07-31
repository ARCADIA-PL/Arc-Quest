import '../styles/editor.css';
import {state, createBlankQuest} from './core/state.js';
import {createNpcSkeleton, createDialogueSkeleton, createTradeSkeleton, createGachaSkeleton} from './core/factories.js';
import {getDomRefs} from './core/dom.js';
import {setByPath, ensureQuestShape} from './core/quest-shape.js';
import {setNpcByPath} from './core/npc-shape.js';
import {setDialogueByPath} from './core/dialogue-shape.js';
import {setTradeByPath} from './core/trade-shape.js';
import {setGachaByPath} from './core/gacha-shape.js';
import {validateQuest} from './core/validators.js';
import {validateNpc} from './core/npc-validators.js';
import {validateDialogue} from './core/dialogue-validators.js';
import {validateTrade} from './core/trade-validators.js';
import {validateGacha} from './core/gacha-validators.js';
import {validateGuide} from './core/guide-validators.js';
import {createGuideSkeleton, createGuideCategorySkeleton} from './core/guide-normalizer.js';
import {renderGuideEditor, bindGuideEditor} from './editors/guide-editor.js';
import {exportJson, importJson, bindDragAndDropImport} from './app/import-export.js';
import {validateCrossReferences} from './core/cross-validator.js';
import {applyPaneLayout, bindPaneResizers} from './app/layout.js';
import {ensureValidSelection, navigateToPath} from './app/navigation.js';
import {renderTree} from './renderers/tree-renderer.js';
import {renderDialogueTree, bindDialogueTreeSelection} from './renderers/dialogue-tree-renderer.js';
import {renderCenterEditor, renderNpcCenter, renderDiagCenter, renderTradeCenter, renderGachaCenter} from './renderers/center-renderer.js';
import {renderSidePanel} from './renderers/side-panel-renderer.js';
import {renderStatus} from './renderers/status-renderer.js';
import {renderNpcTree, bindNpcTreeSelection} from './renderers/npc-tree-renderer.js';
import {renderTradeTree, bindTradeTreeSelection} from './renderers/trade-tree-renderer.js';
import {renderGachaTree, bindGachaTreeSelection} from './renderers/gacha-tree-renderer.js';
import {bindTreeSelection, bindEditorActions, bindNpcEditorActions, bindDialogueEditorActions, bindTradeEditorActions, bindGachaEditorActions} from './renderers/event-bindings.js';
import {bindDialogueDirectoryClicks} from './renderers/dialogue-side-panel.js';
import RegistryClient from './core/registry-client.js';
import {bindAll as bindAllAutocomplete, unbindAll as unbindAllAutocomplete} from './core/registry-autocomplete.js';

const dom = getDomRefs();

RegistryClient.init();

RegistryClient.onStateChange(event => {
    if (event === 'data') rerender();
});

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
    ensureValidNpcSelection();
    validateNpc(state.npc.q);
    state.npc.diag = validateNpc(state.npc.q);
    applyPaneLayout(state, dom);
    renderNpcTree(state, dom.left);
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
    bindNpcTreeSelection(dom.left, state, rerender);
    bindNpcEditorActions(dom.mid, state, rerender, setNpcByPath);
}

function ensureValidNpcSelection() {
    const sel = state.npc.ui.sel;
    const bindings = state.npc.q.bindings || [];
    if (sel.t === 'overview' || sel.t === 'commands') return;
    if (sel.t === 'binding') {
        if (typeof sel.bi !== 'number' || sel.bi >= bindings.length) {
            state.npc.ui.sel = {t: 'overview'};
        }
    }
}

function ensureValidDialogueSelection() {
    const sel = state.dialogue.ui.sel;
    const nodes = state.dialogue.q.nodes || [];
    if (sel.t === 'config') return;
    if (typeof sel.ni !== 'number' || sel.ni >= nodes.length) {
        state.dialogue.ui.sel = {t: 'config'};
        return;
    }
    const node = nodes[sel.ni];
    if (sel.t === 'sayIf' && sel.key && sel.key !== ':default' && !node?.conditionalTexts?.[sel.key]) {
        state.dialogue.ui.sel = {t: 'node', ni: sel.ni};
    }
    if (sel.t === 'choice' && typeof sel.ci === 'number' && sel.ci >= (node?.choices || []).length) {
        state.dialogue.ui.sel = {t: 'node', ni: sel.ni};
    }
}

function renderDialogue() {
    ensureValidDialogueSelection();
    validateDialogue(state.dialogue.q);
    state.dialogue.diag = validateDialogue(state.dialogue.q);
    applyPaneLayout(state, dom);
    renderDialogueTree(state, dom.left);
    renderDiagCenter(state, dom.mid);
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
    bindDialogueEditorActions(dom.mid, state, rerender, setDialogueByPath);
    bindDialogueDirectoryClicks(dom.right, state, rerender);
    bindDialogueTreeSelection(dom.left, state, rerender);
}

function renderTrade() {
    ensureValidTradeSelection();
    validateTrade(state.trade.q);
    state.trade.diag = validateTrade(state.trade.q);
    applyPaneLayout(state, dom);
    renderTradeTree(state, dom.left);
    renderTradeCenter(state, dom.mid);
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
    bindTradeTreeSelection(dom.left, state, rerender);
    bindTradeEditorActions(dom.mid, state, rerender, setTradeByPath);
}

function ensureValidTradeSelection() {
    const sel = state.trade.ui.sel;
    const entries = state.trade.q.entries || {};
    if (sel.t === 'overview') return;
    if (sel.t === 'entry') {
        if (typeof sel.ei !== 'string' || !entries[sel.ei]) {
            state.trade.ui.sel = {t: 'overview'};
        }
    }
}

function renderGacha() {
    ensureValidGachaSelection();
    validateGacha(state.gacha.q);
    state.gacha.diag = validateGacha(state.gacha.q);
    applyPaneLayout(state, dom);
    renderGachaTree(state, dom.left);
    renderGachaCenter(state, dom.mid);
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
    bindGachaTreeSelection(dom.left, state, rerender);
    bindGachaEditorActions(dom.mid, state, rerender, setGachaByPath);
}

function ensureValidGachaSelection() {
    const sel = state.gacha.ui.sel;
    if (sel.t === 'overview') return;
    if (sel.t === 'item') {
        const pools = state.gacha.q.pools || [];
        if (typeof sel.pi !== 'number' || typeof sel.ii !== 'number'
            || !pools[sel.pi] || !pools[sel.pi].items[sel.ii]) {
            state.gacha.ui.sel = {t: 'overview'};
        }
    }
}

function renderGuide() {
    state.guide.diag = validateGuide(state.guide.q, state.guide.kind);
    applyPaneLayout(state, dom);
    renderGuideEditor(state, dom);
    bindGuideEditor(state, rerender, dom.mid);
}

let _prevUiState = null;

function getUiState(state) {
    if (state.mode === 'dialogue') return state.dialogue.ui.sel;
    if (state.mode === 'npc') return state.npc.ui.sel;
    if (state.mode === 'trade') return state.trade.ui.sel;
    if (state.mode === 'gacha') return state.gacha.ui.sel;
    if (state.mode === 'guide') return state.guide.ui.sel;
    return state.quest.ui.sel;
}

function computeNavDir(prev, next) {
    if (!prev || !next) return null;
    if (prev.t === next.t) return null;
    const depth = {
        config: 0, quest: 0, visual: 0, rewards: 0, overview: 0,
        node: 1, phase: 1, entry: 1,
        sayIf: 2, choice: 2, obj: 2
    };
    const prevDepth = depth[prev.t] ?? 0;
    const nextDepth = depth[next.t] ?? 0;
    if (nextDepth > prevDepth) return 'forward';
    if (nextDepth < prevDepth) return 'back';
    return 'tab';
}

function saveStripScrollPositions() {
    const map = {};
    document.querySelectorAll('[data-strip-id]').forEach(el => {
        map[el.dataset.stripId] = el.scrollLeft;
    });
    return map;
}

function restoreStripScrollPositions(map) {
    requestAnimationFrame(() => {
        Object.entries(map).forEach(([id, left]) => {
            const el = document.querySelector(`[data-strip-id="${id}"]`);
            if (el) el.scrollLeft = left;
        });
    });
}

function rerender() {
    const prevUi = _prevUiState;
    const stripScrollMap = saveStripScrollPositions();

    updateNewBtnLabel();

    unbindAllAutocomplete(dom.mid);

    if (state.mode === 'npc') renderNpc();
    else if (state.mode === 'dialogue') renderDialogue();
    else if (state.mode === 'trade') renderTrade();
    else if (state.mode === 'gacha') renderGacha();
    else if (state.mode === 'guide') renderGuide();
    else renderQuest();

    bindAllAutocomplete(dom.mid);

    _prevUiState = getUiState(state);

    restoreStripScrollPositions(stripScrollMap);

    const navDir = computeNavDir(prevUi, _prevUiState);
    if (navDir) {
        dom.mid.dataset.navDir = navDir;
        dom.mid.addEventListener('animationend', () => {
            delete dom.mid.dataset.navDir;
        }, {once: true});
    }
}

dom.newBtn.onclick = () => {
    if (state.mode === 'npc') {
        state.npc.q = createNpcSkeleton();
        state.npc.meta = {file: 'new_npc.json', dirty: false};
        state.npc.ui.sel = {t: 'overview'};
        state.npc.ui.condFold = false;
        state.npc.ui.cmdFold = false;
    } else if (state.mode === 'dialogue') {
        state.dialogue.q = createDialogueSkeleton();
        state.dialogue.meta = {file: 'new_dialogue.json', dirty: false};
    } else if (state.mode === 'trade') {
        state.trade.q = createTradeSkeleton();
        state.trade.meta = {file: 'new_shop.json', dirty: false};
        state.trade.ui.sel = {t: 'overview'};
    } else if (state.mode === 'gacha') {
        state.gacha.q = createGachaSkeleton();
        state.gacha.meta = {file: 'new_gacha.json', dirty: false};
        state.gacha.ui.sel = {t: 'overview'};
    } else if (state.mode === 'guide') {
        state.guide.q = state.guide.kind === 'guideCategory' ? createGuideCategorySkeleton() : createGuideSkeleton();
        state.guide.meta = {file: state.guide.kind === 'guideCategory' ? 'new_guide_category.json' : 'new_guide.json', dirty: false};
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
    } else if (state.mode === 'dialogue') {
        state.dialogue.diag = validateDialogue(state.dialogue.q);
    } else if (state.mode === 'trade') {
        state.trade.diag = validateTrade(state.trade.q);
    } else if (state.mode === 'gacha') {
        state.gacha.diag = validateGacha(state.gacha.q);
    } else if (state.mode === 'guide') {
        state.guide.diag = validateGuide(state.guide.q, state.guide.kind);
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

dom.left.addEventListener('click', e => {
    if (state.mode !== 'dialogue') return;
    const treeItem = e.target.closest('.tree');
    if (!treeItem) return;
    const ds = treeItem.dataset;
    if (ds.t === 'config') { state.dialogue.ui.sel = {t: 'config'}; rerender(); return; }
    if (ds.t === 'node' && ds.ni !== undefined) { state.dialogue.ui.sel = {t: 'node', ni: +ds.ni}; rerender(); return; }
});

if (dom.modeBar) {
    dom.modeBar.onclick = e => {
        const tab = e.target.closest('[data-mode]');
        if (!tab) return;
        state.mode = tab.dataset.mode;
        if (state.mode === 'dialogue') state.quest.ui.tab = 'node';
        if (state.mode === 'trade') state.quest.ui.tab = 'validate';
        if (state.mode === 'gacha') state.quest.ui.tab = 'validate';
        dom.modeBar.querySelectorAll('.mode-tab').forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        rerender();
    };
};

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

const MODE_LABELS = {quest: 'Quest', npc: 'NPC', dialogue: '对话', trade: '商店', gacha: '抽奖'};

MODE_LABELS.guide = 'Guide';

function updateNewBtnLabel() {
    const label = dom.newBtnLabel;
    if (label) label.textContent = '新建 ' + (MODE_LABELS[state.mode] || '');
}

rerender();
