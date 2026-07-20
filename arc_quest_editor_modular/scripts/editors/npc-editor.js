import {esc} from '../core/utils.js';
import {renderConditionTree} from './condition-editor.js';

function suggestInput(label, bind, value, suggestions, listId) {
    const opts = (suggestions || []).map(v => `<option value="${v}"></option>`).join('');
    return `<div class="f"><label>${label}</label><input data-b="${bind}" list="${listId}" value="${esc(value || '')}" placeholder="${label}"><datalist id="${listId}">${opts}</datalist></div>`;
}

function boolSelect(label, bind, value) {
    return `<div class="f"><label>${label}</label><select data-b="${bind}"><option value="false" ${!value ? 'selected' : ''}>false</option><option value="true" ${value ? 'selected' : ''}>true</option></select></div>`;
}

function condSummary(condition) {
    if (!condition || condition.condition === 'arc_quest:always') return 'always';
    return condition.condition.replace('arc_quest:', '').substring(0, 30);
}

export function renderNpcOverview(npc, registry, state) {
    const dialogueSuggestions = registry?.dialogues ? Object.keys(registry.dialogues) : [];
    const entityTypeSuggestions = registry?.npcs ? Object.keys(registry.npcs) : [];
    const condFold = !!(state?.npc?.ui?.condFold);
    const cmdFold = !!(state?.npc?.ui?.cmdFold);
    const bindings = npc.bindings || [];

    const startCmdCount = (npc.onDialogueStartCommands || []).length;
    const endCmdCount = (npc.onDialogueEndCommands || []).length;

    const bindCards = bindings.map((b, i) => {
        const dialogueLabel = b.dialogueId || (b.dialogueIdFromNbt ? `NBT:${b.dialogueIdFromNbt}` : '未设置');
        const cond = condSummary(b.condition);
        return `<div class="detail-card bind-card" data-npc-nav="binding" data-npc-bi="${i}">
          <div style="display:flex;align-items:center;gap:8px">
            <b class="small" style="flex:1">🔗 ${esc(b.bindingId || `#${i + 1}`)}</b>
            <span class="bind-priority-badge">pri:${b.priority ?? 0}</span>
          </div>
          <div class="small" style="color:var(--text-mut);margin:4px 0">→ ${esc(dialogueLabel.substring(0, 40))}</div>
          <div class="tiny" style="opacity:.5">cond: ${esc(cond)}</div>
          <div style="text-align:right;margin-top:4px">
            <button class="toolbar-btn small-btn" data-npc-nav="binding" data-npc-bi="${i}">编辑</button>
          </div>
        </div>`;
    }).join('');

    return `
    <div class="sec">
      <div class="breadcrumb">
        <span class="breadcrumb-current">⚙ NPC 配置</span>
      </div>
      <h3 style="margin-top:4px">实体属性</h3>
      <div class="row">
        <div class="f"><label>&#22810;&#20154;&#20132;&#20114;&#31574;&#30053;</label><select data-b="npc.interactionPolicy">
          ${['PARALLEL_PRIVATE', 'EXCLUSIVE'].map(policy => `<option value="${policy}" ${(npc.interactionPolicy || 'PARALLEL_PRIVATE') === policy ? 'selected' : ''}>${policy}</option>`).join('')}
        </select></div>
      </div>
      <div class="row">
        ${suggestInput('实体类型 (entityType)', 'npc.entityType', npc.entityType, entityTypeSuggestions, 'npc-entityType-list')}
        <div class="f"><label>对话距离</label><input type="number" data-b="npc.dialogueDistance" value="${npc.dialogueDistance ?? 8.0}" min="1" max="64" step="0.5"></div>
      </div>
      <div class="row">
        ${boolSelect('取消原版交互', 'npc.cancelVanillaInteract', npc.cancelVanillaInteract !== false)}
        ${boolSelect('注视玩家', 'npc.shouldLookAtPlayer', npc.shouldLookAtPlayer !== false)}
        ${boolSelect('停止移动', 'npc.shouldStopMoving', npc.shouldStopMoving !== false)}
      </div>
    </div>

    <div class="sec">
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">
        <h3 style="margin:0">交互条件</h3>
        <button data-toggle-npc-cond class="toolbar-btn small-btn" style="margin-left:auto">${condFold ? '▾ 展开' : '▴ 收起'}</button>
      </div>
      ${!condFold ? renderConditionTree('npc.interactCond', npc.interactCondition || {condition: 'arc_quest:always'}, registry, true) : `<div class="tiny" style="opacity:.5">${condSummary(npc.interactCondition)}</div>`}
    </div>

    <div class="sec">
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">
        <h3 style="margin:0">绑定列表 (${bindings.length})</h3>
        <button id="addNpcBindBtn" class="toolbar-btn small-btn">＋ 添加</button>
      </div>
      ${bindCards || '<div class="small" style="color:var(--text-mut)">暂无绑定</div>'}
    </div>

    <div class="sec">
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">
        <h3 style="margin:0">命令</h3>
        <button data-toggle-npc-cmd class="toolbar-btn small-btn" style="margin-left:auto">${cmdFold ? '▾ 展开' : '▴ 收起'}</button>
      </div>
      ${!cmdFold ? `
        <div class="row">
          <div class="f">
            <label>对话开始命令 (${startCmdCount})</label>
            <textarea data-b="npc.onDialogueStartCommands" data-b-array="true" rows="3" placeholder="每行一条命令">${(npc.onDialogueStartCommands || []).join('\n')}</textarea>
          </div>
          <div class="f">
            <label>对话结束命令 (${endCmdCount})</label>
            <textarea data-b="npc.onDialogueEndCommands" data-b-array="true" rows="3" placeholder="每行一条命令">${(npc.onDialogueEndCommands || []).join('\n')}</textarea>
          </div>
        </div>
      ` : `<div class="tiny" style="opacity:.5">开始: ${startCmdCount} 条 · 结束: ${endCmdCount} 条</div>`}
    </div>
  `;
}
