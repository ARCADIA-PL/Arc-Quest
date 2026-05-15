import {esc, clr} from '../core/utils.js';
import {renderConditionTree} from './condition-editor.js';
import {chipEditor} from './chip-editor.js';

function suggestInput(label, bind, value, suggestions, listId) {
    const opts = (suggestions || []).map(v => `<option value="${v}"></option>`).join('');
    return `<div class="f"><label>${label}</label><input data-b="${bind}" list="${listId}" value="${esc(value || '')}" placeholder="${label}"><datalist id="${listId}">${opts}</datalist></div>`;
}

function boolSelect(label, bind, value) {
    return `<div class="f"><label>${label}</label><select data-b="${bind}"><option value="false" ${!value ? 'selected' : ''}>false</option><option value="true" ${value ? 'selected' : ''}>true</option></select></div>`;
}

export function renderNpcEditor(npc, registry, field, area) {
    const dialogueSuggestions = registry && registry.dialogues ? Object.keys(registry.dialogues) : [];
    const entityTypeSuggestions = registry && registry.npcs ? Object.keys(registry.npcs) : [];

    let html = `
    <div class="sec">
      <h3>NPC 实体属性</h3>
      <div class="row">
        ${suggestInput('实体类型 (entityType)', 'npc.entityType', npc.entityType, entityTypeSuggestions, 'npc-entityType-list')}
      </div>
      <div class="row">
        ${field('对话距离', 'npc.dialogueDistance', npc.dialogueDistance ?? 8.0, 'number')}
        <div class="f"></div>
        <div class="f"></div>
      </div>
      <div class="row">
        ${boolSelect('取消原版交互', 'npc.cancelVanillaInteract', npc.cancelVanillaInteract !== false)}
        ${boolSelect('注视玩家', 'npc.shouldLookAtPlayer', npc.shouldLookAtPlayer !== false)}
        ${boolSelect('停止移动', 'npc.shouldStopMoving', npc.shouldStopMoving !== false)}
      </div>
    </div>

    <div class="sec">
      <h3>交互条件</h3>
      ${renderConditionTree('npc.interactCond', npc.interactCondition || {condition: 'arc_quest:always'}, registry, true)}
    </div>

    <div class="sec">
      <h3>绑定列表</h3>
      ${(npc.bindings || []).map((b, i) => `
        <div class="card">
          <div class="small"><b>Binding #${i + 1}</b> ${b.bindingId ? `(${esc(b.bindingId)})` : ''}</div>
          <div class="row">
            <div class="f"><label>绑定 ID</label><input data-b="npc.bind.${i}.bindingId" value="${esc(b.bindingId || '')}" placeholder="bindingId"></div>
            <div class="f"><label>优先级</label><input type="number" data-b="npc.bind.${i}.priority" value="${b.priority ?? 0}" min="0" max="100"></div>
          </div>
          <div class="row">
            ${suggestInput('对话 ID', `npc.bind.${i}.dialogueId`, b.dialogueId || '', dialogueSuggestions, `npc-bind-${i}-dialogue-list`)}
          </div>
          <div class="row">
            <div class="f"><label>NBT 对话 Key</label><input data-b="npc.bind.${i}.dialogueIdFromNbt" value="${esc(b.dialogueIdFromNbt || '')}" placeholder="ArcQuestDialogueId"></div>
          </div>
          <div style="margin-top:8px">
            <div class="small" style="margin-bottom:4px"><b>绑定条件</b></div>
            ${renderConditionTree(`npc.bind.${i}.condition`, b.condition || {condition: 'arc_quest:always'}, registry, true)}
          </div>
          <div class="actions" style="margin-top:8px"><button data-dnpcbind="${i}">删除此绑定</button></div>
        </div>
      `).join('')}
      <div class="actions"><button id="addNpcBindBtn">+ 添加绑定</button></div>
    </div>

    <div class="sec">
      <h3>命令列表</h3>
      ${chipEditor('对话开始命令', npc.onDialogueStartCommands || [], 'npcStartCmd', 'npcStartCmd', 'e.g. say 你好')}
      ${chipEditor('对话结束命令', npc.onDialogueEndCommands || [], 'npcEndCmd', 'npcEndCmd', 'e.g. give @p diamond 1')}
    </div>
  `;
    return html;
}
