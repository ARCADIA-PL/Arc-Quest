function boolSelect(label, bind, value) {
  return `<div class="f"><label>${label}</label><select data-b="${bind}"><option value="false" ${!value ? 'selected' : ''}>false</option><option value="true" ${value ? 'selected' : ''}>true</option></select></div>`;
}

function enumSelect(label, bind, value, options) {
  return `<div class="f"><label>${label}</label><select data-b="${bind}">${options.map(option => `<option value="${option}" ${value === option ? 'selected' : ''}>${option}</option>`).join('')}</select></div>`;
}

function renderCompletionRulesEditor(rules, bindBase, field, addAttr, deleteAttrBuilder) {
  if (!rules?.length) return `<div class="tiny">当前没有 completionRules</div><div class="actions"><button ${addAttr}>+ 新增 Completion Rule</button></div>`;
  return `${rules.map((rule, ri) => `
    <div class="card">
      <div class="row">
        ${enumSelect('Rule Type', `${bindBase}.cr.${ri}.type`, rule.type || 'completed_entry_count', ['completed_entry_count', 'category_completed_count', 'all_entries_complete'])}
        ${field('Value', `${bindBase}.cr.${ri}.value`, rule.value ?? 1, 'number')}
      </div>
      <div class="row">
        ${field('Ref ID', `${bindBase}.cr.${ri}.refId`, rule.refId || '')}
      </div>
      <div class="actions"><button ${deleteAttrBuilder(ri)}>删除 Completion Rule</button></div>
    </div>
  `).join('')}<div class="actions"><button ${addAttr}>+ 新增 Completion Rule</button></div>`;
}

function renderTopLevelRewardItemsEditor(node, rewardNodeIndex, field) {
  const rewards = node.rewards || [];
  if (!rewards.length) return `<div class="tiny">当前没有 rewards</div><div class="actions"><button data-atrr="${rewardNodeIndex}">+ 新增 Reward</button></div>`;
  return `${rewards.map((rw, rwi) => `
    <div class="card">
      <div class="row">
        ${enumSelect('Type', `q.trn.${rewardNodeIndex}.rw.${rwi}.type`, rw.type || 'item', ['item'])}
        ${field('Item ID', `q.trn.${rewardNodeIndex}.rw.${rwi}.itemId`, rw.itemId || '')}
      </div>
      <div class="row">
        ${field('Count', `q.trn.${rewardNodeIndex}.rw.${rwi}.count`, rw.count ?? 1, 'number')}
      </div>
      <div class="actions"><button data-dtrr="${rewardNodeIndex}:${rwi}">删除 Reward</button></div>
    </div>
  `).join('')}<div class="actions"><button data-atrr="${rewardNodeIndex}">+ 新增 Reward</button></div>`;
}

function renderTopLevelRewardNodeEditor(q, field) {
  const nodes = q.collectionConfig?.rewardNodes || [];
  if (!nodes.length) return '<div class="tiny">当前没有顶层 rewardNodes</div><div class="actions"><button id="addTopRewardNodeBtn">+ 新增 Top Reward Node</button></div>';
  return `${nodes.map((node, ri) => `
    <div class="card">
      <div class="row">
        ${field('Node ID', `q.trn.${ri}.nodeId`, node.nodeId || '')}
        ${enumSelect('Scope', `q.trn.${ri}.scope`, node.scope || 'QUEST', ['QUEST', 'CATEGORY'])}
      </div>
      <div class="row">
        ${enumSelect('Grant Mode', `q.trn.${ri}.grantMode`, node.grantMode || 'AUTO', ['AUTO', 'MANUAL'])}
        ${field('Scope Ref ID', `q.trn.${ri}.scopeRefId`, node.scopeRefId || '')}
      </div>
      <div class="tiny">rewards: ${(node.rewards || []).length} | completionRules: ${(node.completionRules || []).length}</div>
      ${renderCompletionRulesEditor(node.completionRules || [], `q.trn.${ri}`, field, `data-atrcr="${ri}"`, cri => `data-dtrcr="${ri}:${cri}"`)}
      ${renderTopLevelRewardItemsEditor(node, ri, field)}
      <div class="actions"><button data-dtrn="${ri}">删除 Top Reward Node</button></div>
    </div>
  `).join('')}<div class="actions"><button id="addTopRewardNodeBtn">+ 新增 Top Reward Node</button></div>`;
}

function renderRewardItemsEditor(node, catIndex, rewardNodeIndex, field) {
  const rewards = node.rewards || [];
  if (!rewards.length) return '<div class="tiny">当前没有 rewards</div><div class="actions"><button data-arr="' + catIndex + ':' + rewardNodeIndex + '">+ 新增 Reward</button></div>';
  return `${rewards.map((rw, rwi) => `
    <div class="card">
      <div class="row">
        ${enumSelect('Type', `q.cat.${catIndex}.rn.${rewardNodeIndex}.rw.${rwi}.type`, rw.type || 'item', ['item'])}
        ${field('Item ID', `q.cat.${catIndex}.rn.${rewardNodeIndex}.rw.${rwi}.itemId`, rw.itemId || '')}
      </div>
      <div class="row">
        ${field('Count', `q.cat.${catIndex}.rn.${rewardNodeIndex}.rw.${rwi}.count`, rw.count ?? 1, 'number')}
      </div>
      <div class="actions"><button data-drr="${catIndex}:${rewardNodeIndex}:${rwi}">删除 Reward</button></div>
    </div>
  `).join('')}<div class="actions"><button data-arr="${catIndex}:${rewardNodeIndex}">+ 新增 Reward</button></div>`;
}

function renderRewardNodeEditor(cat, catIndex, field) {
  const nodes = cat.rewardNodes || [];
  if (!nodes.length) return '<div class="tiny">当前没有 rewardNodes</div><div class="actions"><button data-arn="' + catIndex + '">+ 新增 Reward Node</button></div>';
  return `${nodes.map((node, ri) => `
    <div class="card">
      <div class="row">
        ${field('Node ID', `q.cat.${catIndex}.rn.${ri}.nodeId`, node.nodeId || '')}
        ${enumSelect('Scope', `q.cat.${catIndex}.rn.${ri}.scope`, node.scope || 'CATEGORY', ['CATEGORY', 'QUEST'])}
      </div>
      <div class="row">
        ${enumSelect('Grant Mode', `q.cat.${catIndex}.rn.${ri}.grantMode`, node.grantMode || 'AUTO', ['AUTO', 'MANUAL'])}
        ${field('Scope Ref ID', `q.cat.${catIndex}.rn.${ri}.scopeRefId`, node.scopeRefId || '')}
      </div>
      <div class="tiny">rewards: ${(node.rewards || []).length} | completionRules: ${(node.completionRules || []).length}</div>
      ${renderCompletionRulesEditor(node.completionRules || [], `q.cat.${catIndex}.rn.${ri}`, field, `data-arcr="${catIndex}:${ri}"`, cri => `data-drcr="${catIndex}:${ri}:${cri}"`)}
      ${renderRewardItemsEditor(node, catIndex, ri, field)}
      <div class="actions"><button data-drn="${catIndex}:${ri}">删除 Reward Node</button></div>
    </div>
  `).join('')}<div class="actions"><button data-arn="${catIndex}">+ 新增 Reward Node</button></div>`;
}

function renderCategoryEditor(q, field) {
  const cats = q.collectionConfig?.categories || [];
  if (!cats.length) return '<div class="tiny">当前没有 categories</div><div class="actions"><button id="addCategoryBtn">+ 新增 Category</button></div>';
  return `<h4>Categories</h4>${cats.map((cat, i) => `
    <div class="card">
      <div class="row">
        ${field('Category ID', `q.cat.${i}.categoryId`, cat.categoryId || '')}
        ${field('Sort Order', `q.cat.${i}.sortOrder`, cat.sortOrder ?? i, 'number')}
      </div>
      <div class="row">
        ${field('Display Name', `q.cat.${i}.displayValue`, cat.displayName?.value || '')}
        <div class="f"><label>Display Mode</label><select data-b="q.cat.${i}.displayMode"><option value="translatable" ${(cat.displayName?.mode || 'translatable') === 'translatable' ? 'selected' : ''}>translatable</option><option value="literal" ${cat.displayName?.mode === 'literal' ? 'selected' : ''}>literal</option></select></div>
      </div>
      <div class="tiny">completionRules: ${(cat.completionRules || []).length} | rewardNodes: ${(cat.rewardNodes || []).length}</div>
      ${renderCompletionRulesEditor(cat.completionRules || [], `q.cat.${i}`, field, `data-acr="${i}"`, cri => `data-dcr="${i}:${cri}"`)}
      ${renderRewardNodeEditor(cat, i, field)}
      <div class="actions"><button data-dc="${i}">删除 Category</button></div>
    </div>
  `).join('')}<div class="actions"><button id="addCategoryBtn">+ 新增 Category</button></div>`;
}

function renderCollectionSection(q, area, field) {
  if (!q.collectionConfig && q.mode !== 'COLLECTION') return '';
  return `
    <h4>Collection Config</h4>
    <div class="tiny">categories: ${(q.collectionConfig?.categories || []).length} | rewardNodes: ${(q.collectionConfig?.rewardNodes || []).length}</div>
    <h4>Presentation / Tracker</h4>
    <div class="row">
      ${enumSelect('Tracker Presentation Mode', 'q.cc.trackerPresentationMode', q.collectionConfig?.trackerPresentationMode || 'SUMMARY_WITH_FEED', ['SUMMARY_WITH_FEED', 'SUMMARY_ONLY', 'HIDDEN'])}
      ${enumSelect('Collection Presentation Mode', 'q.cc.collectionPresentationMode', q.collectionConfig?.collectionPresentationMode || 'LIST', ['LIST', 'GRID', 'HIDDEN'])}
    </div>
    <div class="row">
      ${boolSelect('Allow Category Collapse', 'q.cc.allowCategoryCollapse', q.collectionConfig?.allowCategoryCollapse)}
      ${boolSelect('Show Completed Entries', 'q.cc.showCompletedEntries', q.collectionConfig?.showCompletedEntries)}
      ${boolSelect('Show Progress In Tracker', 'q.cc.showProgressInTracker', q.collectionConfig?.showProgressInTracker)}
    </div>
    <h4>Top-level Completion Rules</h4>
    ${renderCompletionRulesEditor(q.collectionConfig?.completionRules || [], 'q.tcr', field, 'id="addTopCompletionRuleBtn"', ri => `data-dtcr="${ri}"`)}
    <h4>Top-level Reward Nodes</h4>
    ${renderTopLevelRewardNodeEditor(q, field)}
    ${renderCategoryEditor(q, field)}
    ${area('collectionConfig JSON', 'q.collectionConfig', q.collectionConfig ? JSON.stringify(q.collectionConfig, null, 2) : '{}')}
  `;
}

function modeSelect(label, bind, value) {
  return `<div class="f"><label>${label}</label><select data-b="${bind}"><option value="translatable" ${value === 'translatable' ? 'selected' : ''}>translatable</option><option value="literal" ${value === 'literal' ? 'selected' : ''}>literal</option></select></div>`;
}

export function renderQuestEditor(state, field, area) {
  const q = state.q;
  return `
    <div class="sec">
      <h3>Quest 基础信息</h3>
      ${field('Quest ID', 'q.id', q.id)}
      <div class="row">
        ${field('标题 Key', 'q.title', q.title)}
        ${modeSelect('Title Mode', 'q.titleMode', q.titleMode || 'translatable')}
      </div>
      <div class="row">
        ${area('描述 Key', 'q.description', q.description)}
        ${modeSelect('Description Mode', 'q.descriptionMode', q.descriptionMode || 'translatable')}
      </div>
      <div class="row">
        ${field('排序', 'q.sortOrder', q.sortOrder, 'number')}
        <div class="f"><label>可重复</label><select data-b="q.repeatable"><option value="false" ${!q.repeatable ? 'selected' : ''}>false</option><option value="true" ${q.repeatable ? 'selected' : ''}>true</option></select></div>
      </div>
      ${field('标签（逗号分隔）', 'q.tags', q.tags.join(', '))}
      <h4>Datapack 顶层字段</h4>
      <div class="row">
        ${field('Category', 'q.category', q.category || '')}
        ${field('Mode', 'q.mode', q.mode || '')}
      </div>
      ${field('Initial Phase ID', 'q.initialPhaseId', q.initialPhaseId || '')}
      ${field('Icon Texture', 'q.iconTexture', q.iconTexture || '')}
      ${field('Chapter Shop ID', 'q.chapterShopId', q.chapterShopId || '')}
      <div class="row">
        ${field('Chapter Shop Type', 'q.chapterShopType', q.chapterShopType || '')}
        <div class="f"><label>Chapter Shop Persistent</label><select data-b="q.chapterShopPersistent"><option value="false" ${!q.chapterShopPersistent ? 'selected' : ''}>false</option><option value="true" ${q.chapterShopPersistent ? 'selected' : ''}>true</option></select></div>
      </div>
      ${field('flagsToSetOnComplete（逗号分隔）', 'q.flagsToSetOnComplete', (q.flagsToSetOnComplete || []).join(', '))}
      ${renderCollectionSection(q, area, field)}
      <div class="actions"><button id="addPhaseBtn">+ 新增 Phase</button></div>
    </div>
  `;
}
