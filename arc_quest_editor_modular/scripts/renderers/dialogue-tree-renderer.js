import {esc} from '../core/utils.js';

export function renderDialogueTree(state, target) {
    const nodes = state.dialogue.q.nodes || [];
    const selNodeId = state.dialogue.ui.selNodeId;
    const startNodeId = state.dialogue.q.startNodeId;

    let h = `<div class="sec">
    <h3>节点列表</h3>
    <div class="sub" style="margin-left:0; border:none; padding:0;">`;

    nodes.forEach((node, i) => {
        const isStart = node.nodeId === startNodeId;
        const isSelected = node.nodeId === selNodeId;
        const star = isStart ? '★ ' : '';
        const label = node.nodeId || `节点 #${i + 1}`;
        const choices = (node.choices || []).length;
        const condTexts = Object.keys(node.conditionalTexts || {}).length;

        h += `<div class="tree ${isSelected ? 'on' : ''}" data-node-id="${esc(node.nodeId)}">
          <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline></svg>
          <span style="flex:1; margin-left:8px;">${star}${esc(label)}</span>
          <span class="chip" style="font-size:10px">C:${choices} CT:${condTexts}</span>
        </div>`;
    });

    h += `</div>
    <div class="actions" style="margin-top:8px"><button id="addDialogueNodeBtn">+ 添加节点</button></div>
    </div>`;

    target.innerHTML = h;
}
