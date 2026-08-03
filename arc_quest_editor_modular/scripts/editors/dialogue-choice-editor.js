import {esc} from '../core/utils.js';
import {renderTextSpec} from './textspec-editor.js';
import {renderCooldownGroup} from './cooldown-editor.js';
import {renderConditionTree} from './condition-editor.js';
import {renderCardStrip} from './dialogue-sayif-editor.js';
import {renderMarkEditor} from './mark-editor.js';

const ACTION_TYPES = [
    'start_quest', 'complete_quest', 'advance_phase',
    'give_xp', 'give_item', 'notify_talk', 'notify_interact',
    'no_op', 'close', 'run_command', 'set_flag', 'set_variable',
    'open_trade', 'open_simple_trade', 'open_gacha', 'custom'
];

function actionSummary(type) {
    const map = {
        start_quest: '接取任务', complete_quest: '完成任务', advance_phase: '推进阶段',
        give_xp: '给予经验', give_item: '给予物品', notify_talk: '对话通知', notify_interact: '交互通知',
        no_op: '空操作', close: '关闭对话', run_command: '执行命令',
        set_flag: '设置Flag', set_variable: '设置变量',
        open_trade: '打开交易', open_simple_trade: '打开简易交易', open_gacha: '打开抽卡',
        custom: '自定义'
    };
    return map[type] || type;
}

function renderActionFields(prefix, action, registry) {
    const quests = registry && registry.quests ? Object.keys(registry.quests) : [];
    switch (action.type) {
        case 'start_quest': case 'complete_quest': case 'advance_phase':
            return `<div class="f"><label>Quest ID</label><input data-b="${prefix}.questId" list="${prefix}-qlist" value="${esc(action.questId || '')}" placeholder="arc_quest:epic_prologue"><datalist id="${prefix}-qlist">${quests.map(v => `<option value="${v}"></option>`).join('')}</datalist></div>`;
        case 'give_xp':
            return `<div class="f"><label>Amount</label><input type="number" data-b="${prefix}.amount" value="${action.amount ?? 0}" min="0"></div>`;
        case 'give_item':
            return `<div class="row"><div class="f"><label>Item ID</label><input data-b="${prefix}.itemId" value="${esc(action.itemId || '')}" placeholder="minecraft:diamond"></div><div class="f"><label>Count</label><input type="number" data-b="${prefix}.count" value="${action.count ?? 1}" min="1" max="64"></div></div>`;
        case 'notify_talk':
            return `<div class="f"><label>NPC ID</label><input data-b="${prefix}.npcId" value="${esc(action.npcId || '')}" placeholder="namespace:npc_id"></div>`;
        case 'notify_interact':
            return `<div class="f"><label>Target ID</label><input data-b="${prefix}.targetId" value="${esc(action.targetId || '')}" placeholder="namespace:npc_id"></div>`;
        case 'run_command':
            return `<div class="f"><label>Command</label><input data-b="${prefix}.command" value="${esc(action.command || '')}" placeholder="give @p diamond 1"></div>`;
        case 'set_flag':
            return `<div class="f"><label>Flag Name</label><input data-b="${prefix}.flagName" value="${esc(action.flagName || '')}" placeholder="namespace:flag_name"></div>`;
        case 'set_variable':
            return `<div class="row"><div class="f"><label>Key</label><input data-b="${prefix}.key" value="${esc(action.key || '')}"></div><div class="f"><label>Value</label><input type="number" data-b="${prefix}.value" value="${action.value ?? 0}"></div></div>`;
        case 'open_trade': case 'open_simple_trade': case 'open_gacha':
            return `<div class="row"><div class="f"><label>Shop ID</label><input data-b="${prefix}.shopId" value="${esc(action.shopId || '')}" placeholder="namespace:shop_id"></div><div class="f"><label>Restore Node</label><input data-b="${prefix}.restoreNodeId" value="${esc(action.restoreNodeId || '')}" placeholder="可选"></div></div>`;
        case 'custom':
            return `<div class="f"><label>Custom Type ID</label><input data-b="${prefix}.customTypeId" value="${esc(action.customTypeId || '')}" placeholder="namespace:custom_action"></div>`;
        default:
            return '<div class="small">该动作无需额外参数</div>';
    }
}

export function renderDialogueChoiceEditor(node, choice, ni, ci, dialogue, registry) {
    const cp = `diag.node.${ni}.ch.${ci}`;
    const nodeIds = (dialogue.nodes || []).map(n => n.nodeId).filter(Boolean);
    const choices = node.choices || [];
    const total = choices.length;

    const sayIfEntries = Object.entries(node.conditionalTexts || {});
    const sayIfCardsHtml = sayIfEntries.map(([k]) => {
        return `<span class="strip-card" data-jump-sayif="${ni}:${k}">${esc(k)}</span>`;
    }).join('');

    const choiceCardsHtml = choices.map((ch, i) => {
        const isActive = i === ci;
        return `<span class="strip-card ${isActive ? 'on' : ''}" data-jump-choice="${ni}:${i}">${esc(ch.choiceId || `#${i + 1}`)}</span>`;
    }).join('');

    return `
    <div class="sec">
      <div class="card-strip-bar">
        <button data-goto-node="${ni}" class="toolbar-btn small-btn" style="font-weight:700;color:var(--accent)">← 节点</button>
        <span class="breadcrumb">
          <span data-goto-config class="breadcrumb-link">⚙ 对话配置</span>
          <span class="breadcrumb-sep">›</span>
          <span data-goto-node="${ni}" class="breadcrumb-link">${esc(node.nodeId || '')}</span>
          <span class="breadcrumb-sep">›</span>
          <span class="breadcrumb-current">${esc(choice.choiceId || `#${ci + 1}`)}</span>
        </span>
      </div>
      ${renderCardStrip({cardsHtml: sayIfCardsHtml, count: sayIfEntries.length, stripPrefix: 'sayif', ni, countText: `${sayIfEntries.length}`, label: '🔀 SayIf'})}
      ${renderCardStrip({cardsHtml: choiceCardsHtml, count: total, stripPrefix: 'choice', ni, countText: `${ci + 1}/${total}`, label: '🎯 Choice'})}
      <div class="row">
        <div class="f"><label>Choice ID</label><input data-b="${cp}.choiceId" value="${esc(choice.choiceId || '')}"></div>
        <div class="f"><label>Priority</label><input type="number" data-b="${cp}.priority" value="${choice.priority ?? 0}" min="0"></div>
      </div>
      <div class="row">
        <div class="f"><label>Next Node</label>
          <input data-b="${cp}.nextNodeId" list="${cp}-nextN-list" value="${esc(choice.nextNodeId || '')}" placeholder="nextNodeId">
          <datalist id="${cp}-nextN-list">${nodeIds.map(v => `<option value="${v}"></option>`).join('')}</datalist>
        </div>
        <div class="f"><label>Restore Node</label>
          <input data-b="${cp}.restoreNodeId" list="${cp}-restN-list" value="${esc(choice.restoreNodeId || '')}" placeholder="open_trade 后恢复">
          <datalist id="${cp}-restN-list">${nodeIds.map(v => `<option value="${v}"></option>`).join('')}</datalist>
        </div>
      </div>
      <div class="f"><label>Select Sound</label><input data-b="${cp}.selectSound" value="${esc(choice.selectSound || '')}" placeholder="可选"></div>
      ${renderTextSpec(`${cp}.text`, choice.text || {}, '选项文本')}
      ${renderCooldownGroup(cp, choice)}
      ${renderMarkEditor(choice.relatedMarks, `${cp}.relatedMarks`, '选择选项后触发的 Marker')}
      <div style="margin-top:8px">
        <div class="small" style="margin-bottom:4px"><b>Conditions</b></div>
        ${(choice.conditions || []).map((cond, cdi) =>
            renderConditionTree(`${cp}.cond.${cdi}`, cond, registry, true)
        ).join('')}
        <div class="actions"><button data-cond-append="${cp}" data-cond-skip-rerender="true">＋ 添加条件</button></div>
      </div>
      <div style="margin-top:12px">
        <div style="display:flex;align-items:center;gap:8px;margin-bottom:4px">
          <b class="small">Actions</b>
          <button data-action-add="${ni}:${ci}" class="toolbar-btn small-btn">＋ 添加动作</button>
        </div>
        ${(choice.actions || []).map((action, ai) => `
          <div class="card" style="border-color:rgba(255,255,255,.06);margin-bottom:8px">
            <div class="row">
              <div class="f">
                <label>Type</label>
                <select data-b="${cp}.actions.${ai}.type">
                  ${ACTION_TYPES.map(t => `<option value="${t}" ${action.type === t ? 'selected' : ''}>${t} — ${actionSummary(t)}</option>`).join('')}
                </select>
              </div>
            </div>
            ${renderActionFields(`${cp}.actions.${ai}`, action, registry)}
            <div class="actions"><button data-daction-del="${cp}.actions.${ai}">删除此动作</button></div>
          </div>
        `).join('')}
      </div>
      <div class="actions" style="margin-top:12px">
        <button data-dchoice-del="${ni}:${ci}">删除此 Choice</button>
      </div>
    </div>
  `;
}
