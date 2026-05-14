function enumSelect(label, bind, value, options) {
    return `<div class="f"><label>${label}</label><select data-b="${bind}">${options.map(option => `<option value="${option}" ${value === option ? 'selected' : ''}>${option}</option>`).join('')}</select></div>`;
}

function suggestInput(label, bind, value, suggestions, listId, placeholder = '') {
    const opts = (suggestions || []).map(v => `<option value="${v}"></option>`).join('');
    return `<div class="f"><label>${label}</label><input data-b="${bind}" list="${listId}" value="${value || ''}" placeholder="${placeholder || label}"><datalist id="${listId}">${opts}</datalist></div>`;
}

const CONDITION_TYPE_OPTIONS = [
    'arc_quest:always',
    'arc_quest:quest_completed',
    'arc_quest:quest_accepted',
    'arc_quest:quest_not_started',
    'arc_quest:quest_phase',
    'arc_quest:quest_phase_completed',
    'arc_quest:quest_phase_reached',
    'arc_quest:has_flag',
    'arc_quest:not_has_flag',
    'arc_quest:variable_check',
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
    if (cond === 'arc_quest:always') return 'always';
    if (cond === 'arc_quest:quest_completed') return `quest_completed(${c.quest_id || '?'})`;
    if (cond === 'arc_quest:quest_accepted') return `quest_accepted(${c.quest_id || '?'})`;
    if (cond === 'arc_quest:quest_not_started') return `quest_not_started(${c.quest_id || '?'})`;
    if (cond === 'arc_quest:quest_phase') return `quest_phase(${c.quest_id || '?'}, ${c.phase_id || '?'})`;
    if (cond === 'arc_quest:quest_phase_completed') return `quest_phase_completed(${c.quest_id || '?'}, ${c.phase_id || '?'})`;
    if (cond === 'arc_quest:quest_phase_reached') return `quest_phase_reached(${c.quest_id || '?'}, ${c.phase_id || '?'})`;
    if (cond === 'arc_quest:has_flag') return `has_flag(${c.flag || '?'})`;
    if (cond === 'arc_quest:not_has_flag') return `not_has_flag(${c.flag || '?'})`;
    if (cond === 'arc_quest:variable_check') return `variable_check(${c.key || '?'}, ${c.op || 'EQUAL'}, ${c.value ?? 0})`;
    if (cond === 'arc_quest:not') return `NOT(${summarizeCondition(c.inner)})`;
    if (cond === 'arc_quest:and') return `AND(${(c.conditions || []).map(summarizeCondition).join(', ')})`;
    if (cond === 'arc_quest:or') return `OR(${(c.conditions || []).map(summarizeCondition).join(', ')})`;
    if (cond === 'minecraft:entity_properties') return `predicate(${c.predicate ? '...' : '?'})`;
    return cond;
}

function summaryBadge(cond) {
    if (cond === 'arc_quest:and' || cond === 'arc_quest:or' || cond === 'arc_quest:not') return {
        className: 'logic',
        label: cond.split(':')[1].toUpperCase(),
        nodeClass: 'logic'
    };
    if (cond === 'arc_quest:has_flag' || cond === 'arc_quest:not_has_flag') return {className: 'flag', label: 'FLAG', nodeClass: 'flag'};
    if (cond.startsWith('arc_quest:quest_')) return {className: 'quest', label: 'QUEST', nodeClass: 'quest'};
    if (cond === 'arc_quest:variable_check') return {className: 'variable', label: 'VAR', nodeClass: 'variable'};
    if (cond === 'minecraft:entity_properties') return {className: 'predicate', label: 'PRED', nodeClass: 'predicate'};
    return {className: 'neutral', label: 'BASE', nodeClass: 'neutral'};
}

export function renderConditionTree(bindBase, condition, options = {}) {
    const c = condition || {condition: 'arc_quest:always'};
    const cond = c.condition || 'arc_quest:always';
    const flagSuggestions = options.flagSuggestions || [];
    const questSuggestions = options.questSuggestions || [];
    const phaseSuggestions = options.phaseSuggestions || [];
    const deletable = !!options.deletable;
    const badge = summaryBadge(cond);
    let body = '';

    if (cond === 'arc_quest:has_flag' || cond === 'arc_quest:not_has_flag') {
        body = `<div class="row">${suggestInput('Flag', `${bindBase}.flag`, c.flag || '', flagSuggestions, `${bindBase}-flag-list`, 'namespace:flag_name')}</div>`;
    } else if (cond === 'arc_quest:quest_completed' || cond === 'arc_quest:quest_accepted' || cond === 'arc_quest:quest_not_started') {
        body = `<div class="row">${suggestInput('Quest ID', `${bindBase}.quest_id`, c.quest_id || '', questSuggestions, `${bindBase}-quest-list`, 'namespace:quest_id')}</div>`;
    } else if (cond === 'arc_quest:quest_phase' || cond === 'arc_quest:quest_phase_completed' || cond === 'arc_quest:quest_phase_reached') {
        body = `
      <div class="row">
        ${suggestInput('Quest ID', `${bindBase}.quest_id`, c.quest_id || '', questSuggestions, `${bindBase}-quest-list`, 'namespace:quest_id')}
        ${suggestInput('Phase ID', `${bindBase}.phase_id`, c.phase_id || '', phaseSuggestions, `${bindBase}-phase-list`, 'phase_id')}
      </div>
    `;
    } else if (cond === 'arc_quest:variable_check') {
        body = `
      <div class="row">
        <div class="f"><label>Key</label><input data-b="${bindBase}.key" value="${c.key || ''}"></div>
        ${enumSelect('Op', `${bindBase}.op`, c.op || 'EQUAL', ['EQUAL', 'NOT_EQUAL', 'GREATER', 'GREATER_OR_EQUAL', 'LESS', 'LESS_OR_EQUAL'])}
        <div class="f"><label>Value</label><input type="number" data-b="${bindBase}.value" value="${c.value ?? 0}"></div>
      </div>
    `;
    } else if (cond === 'arc_quest:not') {
        body = `
      <div class="card" style="margin-top:8px; border-color: rgba(255,255,255,0.08)">
        <div class="small"><b>Inner</b></div>
        ${renderConditionTree(`${bindBase}.inner`, c.inner || {condition: 'arc_quest:always'}, {...options, deletable: true})}
      </div>
    `;
    } else if (cond === 'arc_quest:and' || cond === 'arc_quest:or') {
        const subs = c.conditions || [];
        const items = subs.map((sub, idx) => `
        <div class="card" style="margin-top:8px; border-color: rgba(255,255,255,0.08)">
          <div class="small"><b>#${idx + 1}</b></div>
          ${renderConditionTree(`${bindBase}.conditions.${idx}`, sub, {...options, deletable: true})}
        </div>
      `).join('');
        body = `
      <div class="condition-children">${items}</div>
      ${appendButtons(bindBase, cond)}
    `;
    } else if (cond === 'minecraft:entity_properties') {
        const predicateStr = c.predicate ? JSON.stringify(c.predicate) : '';
        body = `
      <div class="row">
        <div class="f" style="flex:1">
          <label>Predicate JSON</label>
          <textarea data-b="${bindBase}.predicate" rows="4" style="width:100%;font-family:monospace;font-size:11px;" placeholder='{"location":{"biome":"minecraft:desert"}}'>${predicateStr}</textarea>
        </div>
      </div>
    `;
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