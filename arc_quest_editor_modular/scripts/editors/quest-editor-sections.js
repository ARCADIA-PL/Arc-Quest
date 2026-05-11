function boolSelect(label, bind, value) {
    return `<div class="f"><label>${label}</label><select data-b="${bind}"><option value="false" ${!value ? 'selected' : ''}>False</option><option value="true" ${value ? 'selected' : ''}>True</option></select></div>`;
}

function modeSelect(label, bind, value) {
    return `<div class="f"><label>${label}</label><select data-b="${bind}"><option value="translatable" ${value === 'translatable' ? 'selected' : ''}>Translatable</option><option value="literal" ${value === 'literal' ? 'selected' : ''}>Literal</option></select></div>`;
}

function phaseSingleSelect(label, bind, value, phaseIds) {
    const options = ['<option value="">(未设置)</option>', ...phaseIds.map(id => `<option value="${id}" ${value === id ? 'selected' : ''}>${id}</option>`)];
    return `<div class="f"><label>${label}</label><select data-b="${bind}">${options.join('')}</select></div>`;
}

function suggestInput(label, bind, value, suggestions, listId, placeholder = '') {
    const opts = (suggestions || []).map(v => `<option value="${v}"></option>`).join('');
    return `<div class="f"><label>${label}</label><input data-b="${bind}" list="${listId}" value="${value || ''}" placeholder="${placeholder || label}"><datalist id="${listId}">${opts}</datalist></div>`;
}

function enumSelect(label, bind, value, options) {
    return `<div class="f"><label>${label}</label><select data-b="${bind}">${options.map(o => `<option value="${o}" ${value === o ? 'selected' : ''}>${o}</option>`).join('')}</select></div>`;
}

function refSelect(label, bind, value, options, empty = '(未设置)') {
    const deduped = Array.from(new Set((options || []).filter(Boolean)));
    const opts = [`<option value="">${empty}</option>`, ...deduped.map(o => `<option value="${o}" ${value === o ? 'selected' : ''}>${o}</option>`)];
    return `<div class="f"><label>${label}</label><select data-b="${bind}">${opts.join('')}</select></div>`;
}

function scopeRefOptions(scope, categoryIds, phaseIds, questId) {
    if (scope === 'CATEGORY') return categoryIds;
    if (scope === 'PHASE') return phaseIds;
    if (scope === 'QUEST') return [questId].filter(Boolean);
    return [...categoryIds, ...phaseIds];
}

function ruleEditor(rule, bindBase) {
    const type = rule?.type || 'completed_entry_count';
    const needsValue = type === 'completed_entry_count' || type === 'category_completed_count' || type === 'completed_entry_ratio' || type === 'category_completed_ratio';
    return `
    <div class="card" style="margin:8px 0; border-color: rgba(255,255,255,0.1)">
      <div class="row">
        ${enumSelect('Rule Type', `${bindBase}.type`, type, ['all_entries_complete', 'completed_entry_count', 'category_completed_count', 'completed_entry_ratio', 'category_completed_ratio', 'and', 'or', 'not'])}
        ${needsValue ? `<div class="f"><label>Value</label><input type="number" data-b="${bindBase}.value" value="${rule?.value ?? 1}"></div>` : '<div class="f"></div>'}
      </div>
      <div class="tiny">摘要：${type}${needsValue ? ` / value=${rule?.value ?? 1}` : ''}</div>
    </div>
  `;
}

function renderCollectionTreeView(q) {
    const categories = q.collectionConfig?.categories || [];
    const topNodes = q.collectionConfig?.rewardNodes || [];
    return `
    <div class="card">
      <div class="small"><b>Collection Tree View</b></div>
      <div class="tree-view-block" style="margin-top:8px">
        <div class="tree-view-item">collectionConfig</div>
        <div class="tree-view-children">
          <div class="tree-view-item">topRules: ${(q.collectionConfig?.completionRules || []).length}</div>
          <div class="tree-view-item">topRewardNodes: ${topNodes.length}</div>
          ${topNodes.map((n, i) => `<div class="tree-view-children"><div class="tree-view-item">[TopNode ${i}] ${n.nodeId || '(no-id)'} | rules=${(n.completionRules || []).length} rewards=${(n.rewards || []).length}</div></div>`).join('')}
          <div class="tree-view-item">categories: ${categories.length}</div>
          ${categories.map((c, i) => `<div class="tree-view-children"><div class="tree-view-item">[Category ${i}] ${c.categoryId || '(no-id)'} | rules=${(c.completionRules || []).length} nodes=${(c.rewardNodes || []).length}</div>${(c.rewardNodes || []).map((n, ni) => `<div class="tree-view-children"><div class="tree-view-item">[Node ${ni}] ${n.nodeId || '(no-id)'} | rules=${(n.completionRules || []).length} rewards=${(n.rewards || []).length}</div></div>`).join('')}</div>`).join('')}
        </div>
      </div>
    </div>
  `;
}

export {
    boolSelect,
    enumSelect,
    modeSelect,
    phaseSingleSelect,
    refSelect,
    renderCollectionTreeView,
    ruleEditor,
    scopeRefOptions,
    suggestInput
};
