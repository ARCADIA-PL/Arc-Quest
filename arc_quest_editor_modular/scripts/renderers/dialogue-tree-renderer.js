import {esc} from '../core/utils.js';
import {handleDialogueNonDeleteButtonAction} from './bindings/dialogue-click-actions.js';

export function renderDialogueTree(state, target) {
    const dialogue = state.dialogue.q;
    const nodes = dialogue.nodes || [];
    const sel = state.dialogue.ui.sel;
    const startNodeId = dialogue.startNodeId;

    const icConfig = `<svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>`;
    const icFile = `<svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>`;

    const treeItem = (data, label, active, icon = '', extra = '') =>
        `<div class="tree ${active ? 'on' : ''}" ${data}>${icon}<span style="flex:1;margin-left:8px">${label}</span>${extra}</div>`;

    let h = `<div class="sec">`;

    h += treeItem('data-t="config"', '⚙ 对话配置', sel.t === 'config', icConfig);
    h += `<h3 style="margin-top:16px">📋 节点列表</h3>`;

    nodes.forEach((node, ni) => {
        const isStart = node.nodeId === startNodeId;
        const star = isStart ? ' ★' : '';
        const sayIfCount = Object.keys(node.conditionalTexts || {}).length;
        const choiceCount = (node.choices || []).length;
        const selThis = sel.t !== 'config' && sel.ni === ni;

        h += treeItem(
            `data-t="node" data-ni="${ni}"`,
            `${esc(node.nodeId || `#${ni + 1}`)}${star}`,
            selThis && sel.t === 'node',
            icFile,
            `<span class="chip" style="font-size:9px">S:${sayIfCount} C:${choiceCount}</span>`
        );
    });

    h += `<div class="actions" style="margin-top:8px"><button id="addDialogueNodeBtn">＋ 添加节点</button></div>`;
    h += `</div>`;

    target.innerHTML = h;
}

export function bindDialogueTreeSelection(leftEl, state, rerender) {
    leftEl.onclick = e => {
        const nav = e.target.closest('[data-t]');
        if (nav) {
            const t = nav.dataset.t;
            if (t === 'node' && nav.dataset.ni !== undefined) {
                state.dialogue.ui.sel = {t: 'node', ni: +nav.dataset.ni};
            } else if (t === 'config') {
                state.dialogue.ui.sel = {t: 'config'};
            }
            rerender();
            return;
        }

        const btn = e.target.closest('button');
        if (!btn) return;

        if (handleDialogueNonDeleteButtonAction(btn, state)) {
            state.dialogue.meta.dirty = true;
            rerender();
        }
    };
}
