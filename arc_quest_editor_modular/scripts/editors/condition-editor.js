import {getQuestSuggestions, getPhaseSuggestions, getFlagSuggestions} from '../core/suggestions.js';

function enumSelect(label, bind, value, options) {
    return `<div class="f"><label>${label}</label><select data-b="${bind}">${options.map(option => `<option value="${option}" ${value === option ? 'selected' : ''}>${option}</option>`).join('')}</select></div>`;
}

function suggestInput(label, bind, value, suggestions, listId, placeholder = '') {
    const opts = (suggestions || []).map(v => `<option value="${v}"></option>`).join('');
    return `<div class="f"><label>${label}</label><input data-b="${bind}" list="${listId}" value="${value || ''}" placeholder="${placeholder || label}"><datalist id="${listId}">${opts}</datalist></div>`;
}

function plainInput(label, bind, value, placeholder = '') {
    return `<div class="f"><label>${label}</label><input data-b="${bind}" value="${value || ''}" placeholder="${placeholder || label}"></div>`;
}

function numberInput(label, bind, value) {
    return `<div class="f"><label>${label}</label><input type="number" data-b="${bind}" value="${value ?? 0}"></div>`;
}

const CONDITION_TYPE_OPTIONS = [
    'arc_quest:always',
    'arc_quest:quest_completed',
    'arc_quest:quest_accepted',
    'arc_quest:quest_not_started',
    'arc_quest:has_quest',
    'arc_quest:quest_phase',
    'arc_quest:quest_phase_completed',
    'arc_quest:quest_phase_reached',
    'arc_quest:phase_enterable',
    'arc_quest:phase_before',
    'arc_quest:phase_after',
    'arc_quest:phase_between',
    'arc_quest:any_active_in_range',
    'arc_quest:all_completed_in_range',
    'arc_quest:has_flag',
    'arc_quest:not_has_flag',
    'arc_quest:hold_item',
    'arc_quest:variable_check',
    'arc_quest:entity_nbt',
    'arc_quest:entity_name',
    'arc_quest:dialogue_completed',
    'arc_quest:dialogue_on_cooldown',
    'arc_quest:node_visited',
    'arc_quest:node_on_cooldown',
    'arc_quest:choice_selected',
    'arc_quest:choice_on_cooldown',
    'arc_quest:game_time_in_range',
    'arc_quest:and',
    'arc_quest:or',
    'arc_quest:not',
    'minecraft:entity_properties'
];

function conditionTypeSelect(bind, value) {
    return enumSelect('条件类型', bind, value || 'arc_quest:always', CONDITION_TYPE_OPTIONS);
}

function appendButtons(bindBase, type) {
    if (type !== 'arc_quest:and' && type !== 'arc_quest:or') return '';
    return `
    <div class="actions" style="margin-top:8px;">
      <button type="button" data-cond-append="${bindBase}">+ 追加子条件</button>
    </div>
  `;
}

function deleteButton(bindBase, deletable) {
    if (!deletable) return '';
    return `<div class="actions" style="margin-top:8px;"><button type="button" data-cond-delete="${bindBase}" class="danger">删除当前子条件</button></div>`;
}

function summarizeCondition(node) {
    const c = node || {condition: 'arc_quest:always'};
    const cond = c.condition || 'arc_quest:always';
    switch (cond) {
        case 'arc_quest:always': return 'always';
        case 'arc_quest:quest_completed': return `quest_completed(${c.questId || '?'})`;
        case 'arc_quest:quest_accepted': return `quest_accepted(${c.questId || '?'})`;
        case 'arc_quest:quest_not_started': return `quest_not_started(${c.questId || '?'})`;
        case 'arc_quest:has_quest': return `has_quest(${c.questId || '?'})`;
        case 'arc_quest:quest_phase': return `quest_phase(${c.questId || '?'}, ${c.phaseId || '?'})`;
        case 'arc_quest:quest_phase_completed': return `quest_phase_completed(${c.questId || '?'}, ${c.phaseId || '?'})`;
        case 'arc_quest:quest_phase_reached': return `quest_phase_reached(${c.questId || '?'}, ${c.phaseId || '?'})`;
        case 'arc_quest:phase_enterable': return `phase_enterable(${c.questId || '?'}, ${c.phaseId || '?'})`;
        case 'arc_quest:phase_before': return `phase_before(${c.questId || '?'}, ${c.targetPhaseId || '?'})`;
        case 'arc_quest:phase_after': return `phase_after(${c.questId || '?'}, ${c.targetPhaseId || '?'})`;
        case 'arc_quest:phase_between': return `phase_between(${c.questId || '?'}, ${c.fromPhaseId || '?'}, ${c.toPhaseId || '?'})`;
        case 'arc_quest:any_active_in_range': return `any_active(${c.questId || '?'}, ${c.fromPhaseId || '?'}-${c.toPhaseId || '?'})`;
        case 'arc_quest:all_completed_in_range': return `all_completed(${c.questId || '?'}, ${c.fromPhaseId || '?'}-${c.toPhaseId || '?'})`;
        case 'arc_quest:has_flag': return `has_flag(${c.flag || '?'})`;
        case 'arc_quest:not_has_flag': return `not_has_flag(${c.flag || '?'})`;
        case 'arc_quest:hold_item': return `hold_item(${c.itemId || '?'}, ${c.count || 1}, ${c.itemSource || 'hands'})`;
        case 'arc_quest:variable_check': return `variable_check(${c.key || '?'}, ${c.op || 'EQUAL'}, ${c.value ?? 0})`;
        case 'arc_quest:entity_nbt': return `entity_nbt(${c.nbtKey || '?'}, ${c.nbtValue || '?'})`;
        case 'arc_quest:entity_name': return `entity_name(${c.namePattern || '?'})`;
        case 'arc_quest:dialogue_completed': return `dialogue_completed(${c.dialogueId || '?'})`;
        case 'arc_quest:dialogue_on_cooldown': return `dialogue_on_cooldown(${c.dialogueId || '?'})`;
        case 'arc_quest:node_visited': return `node_visited(${c.nodeId || '?'})`;
        case 'arc_quest:node_on_cooldown': return `node_on_cooldown(${c.nodeId || '?'})`;
        case 'arc_quest:choice_selected': return `choice_selected(${c.choiceId || '?'})`;
        case 'arc_quest:choice_on_cooldown': return `choice_on_cooldown(${c.choiceId || '?'})`;
        case 'arc_quest:game_time_in_range': return `game_time(${c.startTick || 0}-${c.endTick || 0})`;
        case 'arc_quest:not': return `NOT(${summarizeCondition(c.inner)})`;
        case 'arc_quest:and': return `AND(${(c.conditions || []).map(summarizeCondition).join(', ')})`;
        case 'arc_quest:or': return `OR(${(c.conditions || []).map(summarizeCondition).join(', ')})`;
        case 'minecraft:entity_properties': return `predicate(${c.predicate ? '...' : '?'})`;
        default: return cond;
    }
}

function summaryBadge(cond) {
    if (cond === 'arc_quest:and' || cond === 'arc_quest:or' || cond === 'arc_quest:not') return {className: 'logic', label: cond.split(':')[1].toUpperCase(), nodeClass: 'logic'};
    if (cond === 'arc_quest:has_flag' || cond === 'arc_quest:not_has_flag') return {className: 'flag', label: 'FLAG', nodeClass: 'flag'};
    if (cond === 'arc_quest:hold_item') return {className: 'item', label: 'ITEM', nodeClass: 'item'};
    if (cond.startsWith('arc_quest:quest_') || cond === 'arc_quest:has_quest') return {className: 'quest', label: 'QUEST', nodeClass: 'quest'};
    if (cond.startsWith('arc_quest:phase_')) return {className: 'phase', label: 'PHASE', nodeClass: 'phase'};
    if (cond === 'arc_quest:variable_check') return {className: 'variable', label: 'VAR', nodeClass: 'variable'};
    if (cond === 'arc_quest:entity_nbt' || cond === 'arc_quest:entity_name') return {className: 'entity', label: 'ENT', nodeClass: 'entity'};
    if (cond === 'arc_quest:dialogue_completed' || cond === 'arc_quest:dialogue_on_cooldown') return {className: 'dialogue', label: 'DLG', nodeClass: 'dialogue'};
    if (cond === 'arc_quest:node_visited' || cond === 'arc_quest:node_on_cooldown'
        || cond === 'arc_quest:choice_selected' || cond === 'arc_quest:choice_on_cooldown') return {className: 'node', label: 'NODE', nodeClass: 'node'};
    if (cond === 'arc_quest:game_time_in_range') return {className: 'time', label: 'TIME', nodeClass: 'time'};
    if (cond === 'minecraft:entity_properties') return {className: 'predicate', label: 'PRED', nodeClass: 'predicate'};
    return {className: 'neutral', label: 'BASE', nodeClass: 'neutral'};
}

function resolveSuggestions(registry) {
    const quests = registry ? getQuestSuggestions(registry) : [];
    const flags = registry ? getFlagSuggestions(registry) : [];
    return {quests, flags};
}

function resolvePhaseListId(bindBase) {
    return `${bindBase}-phase-list`;
}

export function renderConditionTree(bindBase, condition, registry = null, deletable = false) {
    const c = condition || {condition: 'arc_quest:always'};
    const cond = c.condition || 'arc_quest:always';
    const {quests: questSuggestions, flags: flagSuggestions} = resolveSuggestions(registry);
    const phaseSuggestions = (registry && c.questId) ? getPhaseSuggestions(registry, c.questId) : [];
    const badge = summaryBadge(cond);
    let body = '';

    switch (cond) {
        case 'arc_quest:has_flag':
        case 'arc_quest:not_has_flag':
            body = `<div class="row">${suggestInput('Flag', `${bindBase}.flag`, c.flag || '', flagSuggestions, `${bindBase}-flag-list`, 'namespace:flag_name')}</div>`;
            break;

        case 'arc_quest:hold_item':
            body = `
      <div class="row">
        <div class="f"><label>Item ID</label><input data-b="${bindBase}.itemId" data-ac-registry="items" value="${c.itemId || ''}" placeholder="minecraft:diamond"></div>
        ${enumSelect('Item Source', `${bindBase}.itemSource`, c.itemSource || 'hands', ['hands', 'inventory'])}
        ${numberInput('Min Count', `${bindBase}.count`, c.count ?? 1)}
      </div>`;
            break;

        case 'arc_quest:quest_completed':
        case 'arc_quest:quest_accepted':
        case 'arc_quest:quest_not_started':
        case 'arc_quest:has_quest':
            body = `<div class="row">${suggestInput('Quest ID', `${bindBase}.questId`, c.questId || '', questSuggestions, `${bindBase}-quest-list`, 'namespace:quest_id')}</div>`;
            break;

        case 'arc_quest:quest_phase':
        case 'arc_quest:quest_phase_completed':
        case 'arc_quest:quest_phase_reached':
        case 'arc_quest:phase_enterable':
            body = `
      <div class="row">
        ${suggestInput('Quest ID', `${bindBase}.questId`, c.questId || '', questSuggestions, `${bindBase}-quest-list`, 'namespace:quest_id')}
        ${suggestInput('Phase ID', `${bindBase}.phaseId`, c.phaseId || '', phaseSuggestions, resolvePhaseListId(bindBase), 'phase_id')}
      </div>`;
            break;

        case 'arc_quest:phase_before':
        case 'arc_quest:phase_after':
            body = `
      <div class="row">
        ${suggestInput('Quest ID', `${bindBase}.questId`, c.questId || '', questSuggestions, `${bindBase}-quest-list`, 'namespace:quest_id')}
        ${suggestInput('Target Phase ID', `${bindBase}.targetPhaseId`, c.targetPhaseId || '', phaseSuggestions, resolvePhaseListId(bindBase), 'target_phase_id')}
      </div>`;
            break;

        case 'arc_quest:phase_between':
        case 'arc_quest:any_active_in_range':
        case 'arc_quest:all_completed_in_range':
            body = `
      <div class="row">
        ${suggestInput('Quest ID', `${bindBase}.questId`, c.questId || '', questSuggestions, `${bindBase}-quest-list`, 'namespace:quest_id')}
      </div>
      <div class="row">
        ${plainInput('From Phase ID', `${bindBase}.fromPhaseId`, c.fromPhaseId || '', 'from_phase_id')}
        ${plainInput('To Phase ID', `${bindBase}.toPhaseId`, c.toPhaseId || '', 'to_phase_id')}
      </div>`;
            break;

        case 'arc_quest:variable_check':
            body = `
      <div class="row">
        ${plainInput('Key', `${bindBase}.key`, c.key || '', 'variable_key')}
        ${enumSelect('Op', `${bindBase}.op`, c.op || 'EQUAL', ['EQUAL', 'NOT_EQUAL', 'GREATER', 'GREATER_OR_EQUAL', 'LESS', 'LESS_OR_EQUAL'])}
        <div class="f"><label>Value</label><input type="number" data-b="${bindBase}.value" value="${c.value ?? 0}"></div>
      </div>`;
            break;

        case 'arc_quest:entity_nbt':
            body = `
      <div class="row">
        ${enumSelect('NBT Scope', `${bindBase}.nbtScope`, c.nbtScope || 'default', ['default', 'full'])}
        ${plainInput('NBT Key', `${bindBase}.nbtKey`, c.nbtKey || '', 'nbt_key')}
        ${plainInput('NBT Value', `${bindBase}.nbtValue`, c.nbtValue || '', 'nbt_value')}
      </div>`;
            break;

        case 'arc_quest:entity_name':
            body = `<div class="row">${plainInput('Name Pattern', `${bindBase}.namePattern`, c.namePattern || '', 'name_pattern')}</div>`;
            break;

        case 'arc_quest:dialogue_completed':
        case 'arc_quest:dialogue_on_cooldown':
            body = `<div class="row">${plainInput('Dialogue ID', `${bindBase}.dialogueId`, c.dialogueId || '', 'namespace:dialogue_id')}</div>`;
            break;

        case 'arc_quest:node_visited':
        case 'arc_quest:node_on_cooldown':
            body = `<div class="row">${plainInput('Node ID', `${bindBase}.nodeId`, c.nodeId || '', 'node_id')}</div>`;
            break;

        case 'arc_quest:choice_selected':
        case 'arc_quest:choice_on_cooldown':
            body = `<div class="row">${plainInput('Choice ID', `${bindBase}.choiceId`, c.choiceId || '', 'choice_id')}</div>`;
            break;

        case 'arc_quest:game_time_in_range':
            body = `
      <div class="row">
        ${numberInput('Start Tick', `${bindBase}.startTick`, c.startTick ?? 0)}
        ${numberInput('End Tick', `${bindBase}.endTick`, c.endTick ?? 0)}
      </div>`;
            break;

        case 'arc_quest:not':
            body = `
      <div class="card" style="margin-top:8px; border-color: rgba(255,255,255,0.08)">
        <div class="small"><b>Inner</b></div>
        ${renderConditionTree(`${bindBase}.inner`, c.inner || {condition: 'arc_quest:always'}, registry, true)}
      </div>`;
            break;

        case 'arc_quest:and':
        case 'arc_quest:or':
            const subs = c.conditions || [];
            const items = subs.map((sub, idx) => `
        <div class="card" style="margin-top:8px; border-color: rgba(255,255,255,0.08)">
          <div class="small"><b>#${idx + 1}</b></div>
          ${renderConditionTree(`${bindBase}.conditions.${idx}`, sub, registry, true)}
        </div>
      `).join('');
            body = `
      <div class="condition-children">${items}</div>
      ${appendButtons(bindBase, cond)}
    `;
            break;

        case 'minecraft:entity_properties':
            const predicateStr = c.predicate ? JSON.stringify(c.predicate) : '';
            body = `
      <div class="row">
        <div class="f" style="flex:1">
          <label>Predicate JSON</label>
          <textarea data-b="${bindBase}.predicate" rows="4" style="width:100%;font-family:monospace;font-size:11px;" placeholder='{"location":{"biome":"minecraft:desert"}}'>${predicateStr}</textarea>
        </div>
      </div>`;
            break;
    }

    return `
    <div class="condition-node condition-node-${badge.nodeClass}">
      <div class="row">${conditionTypeSelect(`${bindBase}.condition`, cond)}</div>
      <div class="condition-summary">
        <span class="condition-badge ${badge.className}">${badge.label}</span>
        <span class="condition-summary-text">${summarizeCondition(c)}</span>
      </div>
      ${body}
      ${deleteButton(bindBase, deletable)}
    </div>
  `;
}
