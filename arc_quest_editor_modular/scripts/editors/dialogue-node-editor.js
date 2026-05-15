import {esc} from '../core/utils.js';
import {renderTextSpec} from './textspec-editor.js';
import {renderCooldownGroup} from './cooldown-editor.js';
import {renderConditionTree} from './condition-editor.js';
import {renderActionEditor} from './dialogue-action-editor.js';

function suggestInput(label, bind, value, suggestions, listId) {
    const opts = (suggestions || []).map(v => `<option value="${v}"></option>`).join('');
    return `<div class="f"><label>${label}</label><input data-b="${bind}" list="${listId}" value="${esc(value || '')}" placeholder="${label}"><datalist id="${listId}">${opts}</datalist></div>`;
}

export function renderDialogueEditor(dialogue, registry, field, area) {
    const nodeSuggestions = (dialogue.nodes || []).map(n => n.nodeId).filter(Boolean);
    let html = `
    <div class="sec">
      <h3>对话顶层</h3>
      <div class="row">
        <div class="f"><label>对话 ID</label><input data-b="diag.id" value="${esc(dialogue.id || '')}"></div>
        <div class="f"><label>起始节点</label>
          <input data-b="diag.startNodeId" list="diag-startNode-list" value="${esc(dialogue.startNodeId || '')}" placeholder="startNodeId">
          <datalist id="diag-startNode-list">${nodeSuggestions.map(v => `<option value="${v}"></option>`).join('')}</datalist>
        </div>
      </div>
      ${renderTextSpec('diag.defaultNpc', dialogue.defaultNpc || {}, '默认 NPC 名')}
      ${renderCooldownGroup('diag', dialogue)}
    </div>
    `;
    return html;
}

export function renderDialogueNode(dialogue, node, nodeIndex, registry, field, area) {
    const nodeSuggestions = (dialogue.nodes || []).map(n => n.nodeId).filter(Boolean);
    const prefix = `diag.node.${nodeIndex}`;
    let html = `
    <div class="sec">
      <h3>节点: ${esc(node.nodeId || `#${nodeIndex + 1}`)}</h3>
      <div class="row">
        <div class="f"><label>Node ID</label><input data-b="${prefix}.nodeId" value="${esc(node.nodeId || '')}"></div>
        <div class="f"><label>延迟 (ms)</label><input type="number" data-b="${prefix}.delayMs" value="${node.delayMs ?? 0}" min="0" max="30000"></div>
        <div class="f"><label>自动 Next ID</label>
          <input data-b="${prefix}.autoNextId" list="${prefix}-autoNext-list" value="${esc(node.autoNextId || '')}" placeholder="可选">
          <datalist id="${prefix}-autoNext-list">${nodeSuggestions.map(v => `<option value="${v}"></option>`).join('')}</datalist>
        </div>
      </div>
      <div class="row">
        <div class="f"><label>进入音效</label><input data-b="${prefix}.nodeEnterSound" value="${esc(node.nodeEnterSound || '')}" placeholder="minecraft:entity.villager.yes"></div>
      </div>
      ${renderTextSpec(`${prefix}.speaker`, node.speaker || {}, 'Speaker')}
      ${renderTextSpec(`${prefix}.text`, node.text || {}, '默认文本')}
      ${renderCooldownGroup(prefix, node)}
    </div>

    <div class="sec">
      <h3>Conditional Texts</h3>
      ${Object.entries(node.conditionalTexts || {}).map(([key, say], sIdx) => {
          if (!say) return '';
          const sp = `${prefix}.condText.${key}`;
          return `<div class="card">
            <div class="small"><b>${esc(key)}</b></div>
            <div class="row">
              <div class="f"><label>标识名</label>
                <input data-b="${sp}-key" value="${esc(key)}" disabled style="opacity:.6" title="Map key，不可通过输入框修改">
              </div>
              <div class="f"><label>Say ID</label><input data-b="${sp}.sayId" value="${esc(say.sayId || '')}"></div>
              <div class="f"><label>优先级</label><input type="number" data-b="${sp}.priority" value="${say.priority ?? 0}" min="0"></div>
            </div>
            <div class="row">
              <div class="f"><label>音效</label><input data-b="${sp}.soundEvent" value="${esc(say.soundEvent || '')}" placeholder="minecraft:entity.villager.yes"></div>
            </div>
            ${renderTextSpec(`${sp}.text`, say.text || {}, '条件文本')}
            <div style="margin-top:8px">
              <div class="small" style="margin-bottom:4px"><b>Conditions</b></div>
              ${(say.conditions || []).map((cond, condIdx) => {
                  return renderConditionTree(`${sp}.cond.${condIdx}`, cond, registry, true);
              }).join('')}
              <div class="actions"><button data-cond-append="${sp}">+ 添加条件</button></div>
            </div>
            <div class="actions" style="margin-top:8px"><button data-ddcondtext="${nodeIndex}:${key}">删除此 conditional text</button></div>
          </div>`;
      }).join('')}
      <div class="actions"><button id="addCondTextBtn_${nodeIndex}">+ 添加 Conditional Text</button></div>
    </div>

    <div class="sec">
      <h3>Choices</h3>
      ${(node.choices || []).map((choice, ci) => {
          const cp = `${prefix}.ch.${ci}`;
          return `<div class="card">
            <div class="small"><b>Choice #${ci + 1}</b> ${choice.choiceId ? `(${esc(choice.choiceId)})` : ''}</div>
            <div class="row">
              <div class="f"><label>Choice ID</label><input data-b="${cp}.choiceId" value="${esc(choice.choiceId || '')}"></div>
              <div class="f"><label>优先级</label><input type="number" data-b="${cp}.priority" value="${choice.priority ?? 0}" min="0"></div>
            </div>
            <div class="row">
              <div class="f"><label>目标节点</label>
                <input data-b="${cp}.nextNodeId" list="${cp}-nextNode-list" value="${esc(choice.nextNodeId || '')}" placeholder="nextNodeId">
                <datalist id="${cp}-nextNode-list">${nodeSuggestions.map(v => `<option value="${v}"></option>`).join('')}</datalist>
              </div>
              <div class="f"><label>恢复节点</label>
                <input data-b="${cp}.restoreNodeId" list="${cp}-restoreNode-list" value="${esc(choice.restoreNodeId || '')}" placeholder="可选（open_trade 后恢复）">
                <datalist id="${cp}-restoreNode-list">${nodeSuggestions.map(v => `<option value="${v}"></option>`).join('')}</datalist>
              </div>
            </div>
            <div class="row">
              <div class="f"><label>选中音效</label><input data-b="${cp}.selectSound" value="${esc(choice.selectSound || '')}" placeholder="可选"></div>
            </div>
            ${renderTextSpec(`${cp}.text`, choice.text || {}, '选项文本')}
            ${renderCooldownGroup(cp, choice)}
            <div style="margin-top:8px">
              <div class="small" style="margin-bottom:4px"><b>Conditions</b></div>
              ${(choice.conditions || []).map((cond, condIdx) => {
                  return renderConditionTree(`${cp}.cond.${condIdx}`, cond, registry, true);
              }).join('')}
              <div class="actions"><button data-cond-append="${cp}">+ 添加条件</button></div>
            </div>
            <div style="margin-top:8px">
              <div class="small" style="margin-bottom:4px"><b>Actions</b></div>
              ${(choice.actions || []).map((action, ai) => {
                  return `<div style="margin-bottom:8px">
                    ${renderActionEditor(`${cp}.actions.${ai}`, action, registry)}
                    <div class="actions"><button data-daction-del="${cp}.actions.${ai}">删除此动作</button></div>
                  </div>`;
              }).join('')}
              <div class="actions"><button data-action-add="${cp}">+ 添加动作</button></div>
            </div>
          </div>`;
      }).join('')}
      <div class="actions"><button data-choice-add="${nodeIndex}">+ 添加 Choice</button></div>
    </div>
  `;
    return html;
}
