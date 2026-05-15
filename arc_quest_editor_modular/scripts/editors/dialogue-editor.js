import {esc} from '../core/utils.js';
import {renderDialogueNode} from './dialogue-node-editor.js';

export function renderDialogueCenterEditor(state) {
    const dialogue = state.dialogue.q;
    const registry = state.registry;

    let html = '';

    html += `
    <div class="fade-in">
      <div class="sec">
        <h3>对话顶层</h3>
        <div class="row">
          <div class="f"><label>对话 ID</label><input data-b="diag.id" value="${esc(dialogue.id || '')}"></div>
          <div class="f"><label>起始节点</label>
            <input data-b="diag.startNodeId" list="diag-startNode-list" value="${esc(dialogue.startNodeId || '')}" placeholder="startNodeId">
            <datalist id="diag-startNode-list">${(dialogue.nodes || []).map(n => `<option value="${esc(n.nodeId)}"></option>`).join('')}</datalist>
          </div>
        </div>
      </div>
    </div>`;

    const selNodeId = state.dialogue.ui.selNodeId;
    if (selNodeId) {
        const nodeIdx = dialogue.nodes.findIndex(n => n.nodeId === selNodeId);
        if (nodeIdx >= 0) {
            html += `<div class="fade-in">${renderDialogueNode(dialogue, dialogue.nodes[nodeIdx], nodeIdx, registry, null, null)}</div>`;
        }
    }

    return html;
}
