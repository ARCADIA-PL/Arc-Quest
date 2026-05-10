function enumSelect(label, bind, value, options) {
  return `<div class="f"><label>${label}</label><select data-b="${bind}">${options.map(option => `<option value="${option}" ${value === option ? 'selected' : ''}>${option}</option>`).join('')}</select></div>`;
}

function suggestInput(label, bind, value, suggestions, listId, placeholder = '') {
  const opts = (suggestions || []).map(v => `<option value="${v}"></option>`).join('');
  return `<div class="f"><label>${label}</label><input data-b="${bind}" list="${listId}" value="${value || ''}" placeholder="${placeholder || label}"><datalist id="${listId}">${opts}</datalist></div>`;
}

function conditionTypeSelect(bind, value) {
  return enumSelect('条件类型', bind, value || 'always', ['always', 'flag_set', 'flag_not_set', 'quest_completed', 'variable', 'and', 'or', 'not']);
}

export function renderConditionTree(bindBase, condition, options = {}) {
  const c = condition || { type: 'always' };
  const type = c.type || 'always';
  const flagSuggestions = options.flagSuggestions || [];
  const questSuggestions = options.questSuggestions || [];
  let body = '';

  if (type === 'flag_set' || type === 'flag_not_set') {
    body = `<div class="row">${suggestInput('Flag', `${bindBase}.flag`, c.flag || '', flagSuggestions, `${bindBase}-flag-list`, 'namespace:flag_name')}</div>`;
  } else if (type === 'quest_completed') {
    body = `<div class="row">${suggestInput('Quest ID', `${bindBase}.questId`, c.questId || '', questSuggestions, `${bindBase}-quest-list`, 'namespace:quest_id')}</div>`;
  } else if (type === 'variable') {
    body = `
      <div class="row">
        <div class="f"><label>Variable</label><input data-b="${bindBase}.variable" value="${c.variable || ''}"></div>
        ${enumSelect('CompareOp', `${bindBase}.compareOp`, c.compareOp || 'EQUAL', ['EQUAL', 'NOT_EQUAL', 'GREATER', 'GREATER_OR_EQUAL', 'LESS', 'LESS_OR_EQUAL'])}
        <div class="f"><label>Value</label><input type="number" data-b="${bindBase}.value" value="${c.value ?? 0}"></div>
      </div>
    `;
  } else if (type === 'not') {
    body = `
      <div class="card" style="margin-top:8px; border-color: rgba(255,255,255,0.08)">
        <div class="small"><b>Inner</b></div>
        ${renderConditionTree(`${bindBase}.left`, c.left || { type: 'always' }, options)}
      </div>
    `;
  } else if (type === 'and' || type === 'or') {
    body = `
      <div class="card" style="margin-top:8px; border-color: rgba(255,255,255,0.08)">
        <div class="small"><b>Left</b></div>
        ${renderConditionTree(`${bindBase}.left`, c.left || { type: 'always' }, options)}
        <div class="small" style="margin-top:8px"><b>Right</b></div>
        ${renderConditionTree(`${bindBase}.right`, c.right || { type: 'always' }, options)}
      </div>
    `;
  }

  return `
    <div class="condition-node">
      <div class="row">${conditionTypeSelect(`${bindBase}.type`, type)}</div>
      ${body}
    </div>
  `;
}
