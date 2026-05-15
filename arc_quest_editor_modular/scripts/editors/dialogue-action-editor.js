import {esc} from '../core/utils.js';
import {renderTextSpec} from './textspec-editor.js';
import {renderCooldownGroup} from './cooldown-editor.js';
import {renderConditionTree} from './condition-editor.js';

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
        set_flag: '设置 Flag', set_variable: '设置变量',
        open_trade: '打开交易', open_simple_trade: '打开简易交易', open_gacha: '打开抽卡',
        custom: '自定义动作'
    };
    return map[type] || type;
}

function renderActionFields(prefix, action, registry) {
    const questSuggestions = registry && registry.quests ? Object.keys(registry.quests) : [];
    const flagSuggestions = [];

    switch (action.type) {
        case 'start_quest':
        case 'complete_quest':
        case 'advance_phase':
            return `<div class="row">
              <div class="f"><label>Quest ID</label><input data-b="${prefix}.questId" list="${prefix}-quest-list" value="${esc(action.questId || '')}" placeholder="e.g. arc_quest:epic_prologue"><datalist id="${prefix}-quest-list">${questSuggestions.map(v => `<option value="${v}"></option>`).join('')}</datalist></div>
            </div>`;
        case 'give_xp':
            return `<div class="f"><label>数量</label><input type="number" data-b="${prefix}.amount" value="${action.amount ?? 0}" min="0"></div>`;
        case 'give_item':
            return `<div class="row">
              <div class="f"><label>Item ID</label><input data-b="${prefix}.itemId" value="${esc(action.itemId || '')}" placeholder="minecraft:diamond"></div>
              <div class="f"><label>数量</label><input type="number" data-b="${prefix}.count" value="${action.count ?? 1}" min="1" max="64"></div>
            </div>`;
        case 'notify_talk':
            return `<div class="f"><label>NPC ID</label><input data-b="${prefix}.npcId" value="${esc(action.npcId || '')}" placeholder="namespace:npc_id"></div>`;
        case 'notify_interact':
            return `<div class="f"><label>Target ID</label><input data-b="${prefix}.targetId" value="${esc(action.targetId || '')}" placeholder="namespace:npc_id"></div>`;
        case 'run_command':
            return `<div class="f"><label>命令</label><input data-b="${prefix}.command" value="${esc(action.command || '')}" placeholder="e.g. give @p diamond 1"></div>`;
        case 'set_flag':
            return `<div class="f"><label>Flag 名</label><input data-b="${prefix}.flagName" value="${esc(action.flagName || '')}" placeholder="namespace:flag_name"></div>`;
        case 'set_variable':
            return `<div class="row">
              <div class="f"><label>变量 Key</label><input data-b="${prefix}.key" value="${esc(action.key || '')}"></div>
              <div class="f"><label>值</label><input type="number" data-b="${prefix}.value" value="${action.value ?? 0}"></div>
            </div>`;
        case 'open_trade':
        case 'open_simple_trade':
        case 'open_gacha':
            return `<div class="row">
              <div class="f"><label>Shop ID</label><input data-b="${prefix}.shopId" value="${esc(action.shopId || '')}" placeholder="namespace:shop_id"></div>
              <div class="f"><label>恢复节点</label><input data-b="${prefix}.restoreNodeId" value="${esc(action.restoreNodeId || '')}" placeholder="可选"></div>
            </div>`;
        case 'custom':
            return `<div class="row">
              <div class="f"><label>自定义 Type ID</label><input data-b="${prefix}.customTypeId" value="${esc(action.customTypeId || '')}" placeholder="namespace:custom_action"></div>
            </div>`;
        case 'no_op':
        case 'close':
            return '<div class="small">该动作无需额外参数</div>';
        default:
            return '';
    }
}

export function renderActionEditor(prefix, action, registry) {
    return `
    <div class="card" style="border-color:rgba(255,255,255,.06)">
      <div class="row">
        <div class="f">
          <label>Action Type</label>
          <select data-b="${prefix}.type">
            ${ACTION_TYPES.map(t => `<option value="${t}" ${action.type === t ? 'selected' : ''}>${t} — ${actionSummary(t)}</option>`).join('')}
          </select>
        </div>
      </div>
      ${renderActionFields(prefix, action, registry)}
    </div>`;
}
