import {esc} from '../core/utils.js';
import {renderTextSpec} from './textspec-editor.js';
import {renderCooldownGroup} from './cooldown-editor.js';
import {renderDialogueNodeSummary} from './dialogue-node-summary.js';
import {renderDialogueSayEditor} from './dialogue-say-editor.js';
import {renderDialogueSayIfEditor} from './dialogue-sayif-editor.js';
import {renderDialogueChoiceEditor} from './dialogue-choice-editor.js';

export function renderDialogueConfig(dialogue, registry) {
    const nodeIds = (dialogue.nodes || []).map(n => n.nodeId).filter(Boolean);
    return `
    <div class="sec">
      <h3>对话顶层</h3>
      <div class="row">
        <div class="f"><label>对话 ID</label><input data-b="diag.id" value="${esc(dialogue.id || '')}"></div>
        <div class="f"><label>起始节点</label>
          <input data-b="diag.startNodeId" list="diag-startNode-list" value="${esc(dialogue.startNodeId || '')}" placeholder="startNodeId">
          <datalist id="diag-startNode-list">${nodeIds.map(v => `<option value="${v}"></option>`).join('')}</datalist>
        </div>
      </div>
      ${renderTextSpec('diag.defaultNpc', dialogue.defaultNpc || {}, '默认 NPC 名')}
      ${renderCooldownGroup('diag', dialogue)}
    </div>
    <div class="sec">
      <h3>绑定</h3>
      ${(dialogue.npcBindings || []).map((b, i) => `
        <div class="row">
          <div class="f"><label>NPC ID</label><input data-b="diag.npcBind.${i}.npcId" value="${esc(b.npcId || '')}" placeholder="namespace:npc_id"></div>
          <div class="f"><label>Dialogue ID</label><input data-b="diag.npcBind.${i}.dialogueId" value="${esc(b.dialogueId || '')}" placeholder="${esc(dialogue.id || '')}"></div>
        </div>
      `).join('') || '<div class="small" style="color:var(--text-mut)">暂无 NPC 绑定</div>'}
      ${(dialogue.entityBindings || []).map((b, i) => `
        <div class="row">
          <div class="f"><label>Entity Type</label><input data-b="diag.entityBind.${i}.entityType" value="${esc(b.entityType || '')}" placeholder="minecraft:villager"></div>
          <div class="f"><label>Dialogue ID</label><input data-b="diag.entityBind.${i}.dialogueId" value="${esc(b.dialogueId || '')}" placeholder="${esc(dialogue.id || '')}"></div>
        </div>
      `).join('') || '<div class="small" style="color:var(--text-mut)">暂无实体绑定</div>'}
    </div>
  `;
}

export function renderDialogueCenter(state) {
    const sel = state.dialogue.ui.sel;
    const dialogue = state.dialogue.q;
    const registry = state.registry;
    let html = '';

    if (sel.t === 'config') {
        html = renderDialogueConfig(dialogue, registry);
    } else {
        const ni = typeof sel.ni === 'number' ? sel.ni : -1;
        const node = dialogue.nodes[ni];
        if (!node) { html = renderDialogueConfig(dialogue, registry); }
        else if (sel.t === 'node') {
            html = renderDialogueNodeSummary(dialogue, node, ni, registry);
        } else if (sel.t === 'say') {
            html = renderDialogueSayEditor(node, ni, registry);
        } else if (sel.t === 'sayIf') {
            const sayIf = node.conditionalTexts?.[sel.key];
            html = sayIf ? renderDialogueSayIfEditor(node, sel.key, sayIf, ni, registry)
                : renderDialogueNodeSummary(dialogue, node, ni, registry);
        } else if (sel.t === 'choice') {
            const choice = node.choices?.[sel.ci];
            html = choice ? renderDialogueChoiceEditor(node, choice, ni, sel.ci, dialogue, registry)
                : renderDialogueNodeSummary(dialogue, node, ni, registry);
        } else {
            html = renderDialogueConfig(dialogue, registry);
        }
    }

    return html;
}
