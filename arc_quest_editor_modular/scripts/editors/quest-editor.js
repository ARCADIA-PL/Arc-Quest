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

function chipEditor(label, list, addKey, removeKey, placeholder) {
  const chips = (list || []).map((v, i) => `<span class="chip-item">${v}<button type="button" class="chip-remove" data-chip-remove="${removeKey}:${i}">×</button></span>`).join('');
  return `
    <div class="f">
      <label>${label}</label>
      <div class="chip-editor" data-chip-add-wrap="${addKey}">
        <div class="chip-list">${chips || '<span class="tiny">暂无</span>'}</div>
        <div class="chip-input-row">
          <input type="text" data-chip-add-input="${addKey}" placeholder="${placeholder}">
          <button type="button" data-chip-add="${addKey}">添加</button>
        </div>
      </div>
    </div>
  `;
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
  const isComposite = type === 'and' || type === 'or';
  const hasSingleLeft = type === 'not';
  const needsValue = type === 'completed_entry_count' || type === 'category_completed_count' || type === 'completed_entry_ratio' || type === 'category_completed_ratio';
  return `
    <div class="card" style="margin:8px 0; border-color: rgba(255,255,255,0.1)">
      <div class="row">
        ${enumSelect('Rule Type', `${bindBase}.type`, type, ['all_entries_complete', 'completed_entry_count', 'category_completed_count', 'completed_entry_ratio', 'category_completed_ratio', 'and', 'or', 'not'])}
        ${needsValue ? `<div class="f"><label>Value</label><input type="number" data-b="${bindBase}.value" value="${rule?.value ?? 1}"></div>` : '<div class="f"></div>'}
      </div>
      ${isComposite ? `
      <div class="card" style="margin-top:8px; border-color: rgba(255,255,255,0.08)">
        <div class="small"><b>Left Rule</b></div>
        ${enumSelect('Left Type', `${bindBase}.left.type`, rule?.left?.type || 'all_entries_complete', ['all_entries_complete', 'completed_entry_count', 'category_completed_count', 'completed_entry_ratio', 'category_completed_ratio'])}
        <div class="f"><label>Left Value</label><input type="number" data-b="${bindBase}.left.value" value="${rule?.left?.value ?? 1}"></div>
        <div class="small" style="margin-top:8px"><b>Right Rule</b></div>
        ${enumSelect('Right Type', `${bindBase}.right.type`, rule?.right?.type || 'all_entries_complete', ['all_entries_complete', 'completed_entry_count', 'category_completed_count', 'completed_entry_ratio', 'category_completed_ratio'])}
        <div class="f"><label>Right Value</label><input type="number" data-b="${bindBase}.right.value" value="${rule?.right?.value ?? 1}"></div>
      </div>
      ` : ''}
      ${hasSingleLeft ? `
      <div class="card" style="margin-top:8px; border-color: rgba(255,255,255,0.08)">
        <div class="small"><b>Inner Rule</b></div>
        ${enumSelect('Inner Type', `${bindBase}.left.type`, rule?.left?.type || 'all_entries_complete', ['all_entries_complete', 'completed_entry_count', 'category_completed_count', 'completed_entry_ratio', 'category_completed_ratio'])}
        <div class="f"><label>Inner Value</label><input type="number" data-b="${bindBase}.left.value" value="${rule?.left?.value ?? 1}"></div>
      </div>
      ` : ''}
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

function renderCollectionWorkspace(state, q, field) {
  q.collectionConfig ||= { categories: [], rewardNodes: [], completionRules: [] };
  const categories = q.collectionConfig.categories || [];
  const topNodes = q.collectionConfig.rewardNodes || [];
  const categoryIds = categories.map(c => c.categoryId).filter(Boolean);
  const allNodeIds = [
    ...topNodes.map(n => n.nodeId),
    ...categories.flatMap(c => (c.rewardNodes || []).map(n => n.nodeId))
  ].filter(Boolean);
  const phaseIds = (q.phases || []).map(p => p.id).filter(Boolean);

  return `
    <h4>Collection Workspace</h4>
    <div class="actions" style="margin:8px 0 12px;">
      <button type="button" data-collection-view="card" class="${state.ui.collectionView !== 'tree' ? 'primary' : ''}">Card View</button>
      <button type="button" data-collection-view="tree" class="${state.ui.collectionView === 'tree' ? 'primary' : ''}">Tree View</button>
      <button type="button" id="fixCollectionRefsBtn">一键修复引用</button>
      <button type="button" id="fixCollectionRulesBtn">补齐 Rule 默认值</button>
    </div>
    ${state.ui.collectionView === 'tree' ? renderCollectionTreeView(q) : `
    <div class="card">
      <div class="row">
        ${boolSelect('allowCategoryCollapse', 'q.cc.allowCategoryCollapse', !!q.collectionConfig.allowCategoryCollapse)}
        ${boolSelect('showCompletedEntries', 'q.cc.showCompletedEntries', !!q.collectionConfig.showCompletedEntries)}
      </div>
      <div class="row">
        ${boolSelect('showProgressInTracker', 'q.cc.showProgressInTracker', !!q.collectionConfig.showProgressInTracker)}
        ${enumSelect('trackerPresentationMode', 'q.cc.trackerPresentationMode', q.collectionConfig.trackerPresentationMode || 'DETAILED', ['DETAILED', 'COMPACT'])}
      </div>
      <div class="row">
        ${enumSelect('collectionPresentationMode', 'q.cc.collectionPresentationMode', q.collectionConfig.collectionPresentationMode || 'GROUPED', ['GROUPED', 'FLAT'])}
        <div class="f"></div>
      </div>
    </div>

    <h4>Top Completion Rules</h4>
    <div class="card">
      ${(q.collectionConfig.completionRules || []).map((rule, ri) => `
        ${ruleEditor(rule, `q.tcr.${ri}`, [...categoryIds, ...allNodeIds, ...phaseIds])}
        <div class="actions"><button data-dtcr="${ri}" class="danger">删除 Rule</button></div>
      `).join('')}
      <div class="actions"><button id="addTopCompletionRuleBtn">+ 添加 Top Rule</button></div>
    </div>

    <h4>Top Reward Nodes</h4>
    <div class="card">
      ${topNodes.map((node, ni) => `
        <div class="card" style="margin:10px 0; border-color: rgba(255,255,255,0.1)">
          <div class="row">
            ${field('Node ID', `q.trn.${ni}.nodeId`, node.nodeId || '')}
            ${enumSelect('Scope', `q.trn.${ni}.scope`, node.scope || 'QUEST', ['QUEST', 'CATEGORY', 'PHASE'])}
          </div>
          <div class="row">
            ${enumSelect('Grant Mode', `q.trn.${ni}.grantMode`, node.grantMode || 'MANUAL', ['MANUAL', 'AUTO'])}
            ${refSelect('scopeRefId', `q.trn.${ni}.scopeRefId`, node.scopeRefId || '', scopeRefOptions(node.scope, categoryIds, phaseIds, q.id))}
          </div>
          <div class="small">Completion Rules</div>
          ${(node.completionRules || []).map((rule, ri) => `${ruleEditor(rule, `q.trn.${ni}.cr.${ri}`, [...allNodeIds, ...categoryIds, ...phaseIds])}<div class="actions"><button data-dtrcr="${ni}:${ri}" class="danger">删除 Rule</button></div>`).join('')}
          <div class="actions"><button data-atrcr="${ni}">+ 添加 Rule</button></div>
          <div class="small" style="margin-top:10px">Rewards</div>
          ${(node.rewards || []).map((rw, rwi) => `
            <div class="row">
              ${enumSelect('Type', `q.trn.${ni}.rw.${rwi}.type`, rw.type || 'item', ['item', 'flag_set', 'flag_clear', 'command', 'var_set', 'var_add', 'var_subtract', 'var_multiply'])}
              ${field('itemId', `q.trn.${ni}.rw.${rwi}.itemId`, rw.itemId || '')}
              ${field('count', `q.trn.${ni}.rw.${rwi}.count`, rw.count ?? 1, 'number')}
            </div>
            <div class="actions"><button data-dtrr="${ni}:${rwi}" class="danger">删除 Reward</button></div>
          `).join('')}
          <div class="actions"><button data-atrr="${ni}">+ 添加 Reward</button></div>
          <div class="actions"><button data-dtrn="${ni}" class="danger">删除 Top Node</button></div>
        </div>
      `).join('')}
      <div class="actions"><button id="addTopRewardNodeBtn">+ 添加 Top Reward Node</button></div>
    </div>

    <h4>Categories</h4>
    <div class="card">
      ${categories.map((cat, ci) => `
        <div class="card" style="margin:10px 0; border-color: rgba(255,255,255,0.1)">
          <div class="row">
            ${field('Category ID', `q.cat.${ci}.categoryId`, cat.categoryId || '')}
            ${field('Sort Order', `q.cat.${ci}.sortOrder`, cat.sortOrder ?? ci, 'number')}
          </div>
          <div class="row">
            ${enumSelect('Display Mode', `q.cat.${ci}.displayMode`, cat.displayName?.mode || 'translatable', ['translatable', 'literal'])}
            ${field('Display Value', `q.cat.${ci}.displayValue`, cat.displayName?.value || '')}
          </div>

          <div class="small">Category Completion Rules</div>
          ${(cat.completionRules || []).map((rule, ri) => `${ruleEditor(rule, `q.cat.${ci}.cr.${ri}`, [...categoryIds, ...allNodeIds, ...phaseIds])}<div class="actions"><button data-dcr="${ci}:${ri}" class="danger">删除 Rule</button></div>`).join('')}
          <div class="actions"><button data-acr="${ci}">+ 添加 Category Rule</button></div>

          <div class="small" style="margin-top:10px">Reward Nodes</div>
          ${(cat.rewardNodes || []).map((node, ni) => `
            <div class="card" style="margin:8px 0; border-color: rgba(255,255,255,0.08)">
              <div class="row">
                ${field('Node ID', `q.cat.${ci}.rn.${ni}.nodeId`, node.nodeId || '')}
                ${enumSelect('Scope', `q.cat.${ci}.rn.${ni}.scope`, node.scope || 'CATEGORY', ['CATEGORY', 'PHASE', 'QUEST'])}
              </div>
              <div class="row">
                ${enumSelect('Grant Mode', `q.cat.${ci}.rn.${ni}.grantMode`, node.grantMode || 'AUTO', ['AUTO', 'MANUAL'])}
                ${refSelect('scopeRefId', `q.cat.${ci}.rn.${ni}.scopeRefId`, node.scopeRefId || '', scopeRefOptions(node.scope, categoryIds, phaseIds, q.id))}
              </div>

              <div class="small">Node Completion Rules</div>
              ${(node.completionRules || []).map((rule, nri) => `${ruleEditor(rule, `q.cat.${ci}.rn.${ni}.cr.${nri}`, [...allNodeIds, ...categoryIds, ...phaseIds])}<div class="actions"><button data-drcr="${ci}:${ni}:${nri}" class="danger">删除 Rule</button></div>`).join('')}
              <div class="actions"><button data-arcr="${ci}:${ni}">+ 添加 Node Rule</button></div>

              <div class="small" style="margin-top:10px">Node Rewards</div>
              ${(node.rewards || []).map((rw, rwi) => `
                <div class="row">
                  ${enumSelect('Type', `q.cat.${ci}.rn.${ni}.rw.${rwi}.type`, rw.type || 'item', ['item', 'flag_set', 'flag_clear', 'command', 'var_set', 'var_add', 'var_subtract', 'var_multiply'])}
                  ${field('itemId', `q.cat.${ci}.rn.${ni}.rw.${rwi}.itemId`, rw.itemId || '')}
                  ${field('count', `q.cat.${ci}.rn.${ni}.rw.${rwi}.count`, rw.count ?? 1, 'number')}
                </div>
                <div class="actions"><button data-drr="${ci}:${ni}:${rwi}" class="danger">删除 Reward</button></div>
              `).join('')}
              <div class="actions"><button data-arr="${ci}:${ni}">+ 添加 Node Reward</button></div>

              <div class="actions"><button data-drn="${ci}:${ni}" class="danger">删除 Node</button></div>
            </div>
          `).join('')}
          <div class="actions"><button data-arn="${ci}">+ 添加 Reward Node</button></div>
          <div class="actions"><button data-dc="${ci}" class="danger">删除 Category</button></div>
        </div>
      `).join('')}
      <div class="actions"><button id="addCategoryBtn">+ 添加 Category</button></div>
    </div>
    `}
  `;
}

export function renderQuestEditor(state, field, area) {
  const q = state.q;
  const phaseIds = (q.phases || []).map(p => p.id).filter(Boolean);

  return `
    <div class="sec">
      <h3 style="font-size:20px; font-weight:700; color:var(--text-main); margin-bottom: 20px;">架构参数设定 (Quest Info)</h3>

      <div class="card">
        ${field('任务唯一标识 (Quest ID)', 'q.id', q.id)}
        <div class="row">
          ${field('标题键值 (Title Key)', 'q.title', q.title)}
          ${modeSelect('文本模式 (Title Mode)', 'q.titleMode', q.titleMode || 'translatable')}
        </div>
        <div class="row">
          ${area('描述键值 (Description Key)', 'q.description', q.description)}
          ${modeSelect('文本模式 (Desc Mode)', 'q.descriptionMode', q.descriptionMode || 'translatable')}
        </div>
        <div class="row">
          ${field('排序权重 (Sort Order)', 'q.sortOrder', q.sortOrder, 'number')}
          ${boolSelect('允许重复执行 (Repeatable)', 'q.repeatable', q.repeatable)}
        </div>
        <div class="row">
          ${chipEditor('标签池 (Tags)', q.tags || [], 'q.tags', 'q.tags', '输入 tag 后点击添加')}
          ${chipEditor('接取时触发标记 (flagsToSetOnAccept)', q.flagsToSetOnAccept || [], 'q.flagsToSetOnAccept', 'q.flagsToSetOnAccept', '输入 flag 后点击添加')}
        </div>
      </div>

      <h4>Datapack 顶层设置 (Top-level Specs)</h4>
      <div class="card">
        <div class="row">
          ${field('分类 (Category)', 'q.category', q.category || '')}
          ${field('模式 (Mode)', 'q.mode', q.mode || '')}
        </div>
        <div class="row">
          ${enumSelect('完成策略 (completionPolicy)', 'q.completionPolicy', q.completionPolicy || 'ALL', ['ALL', 'ANY', 'N_OF_M', 'SPECIFIC_PHASE'])}
          ${field('完成数量阈值 (completionRequiredCount)', 'q.completionRequiredCount', q.completionRequiredCount ?? 1, 'number')}
        </div>
        <div class="row">
          ${phaseSingleSelect('完成目标阶段 (completionTargetPhaseId)', 'q.completionTargetPhaseId', q.completionTargetPhaseId || '', phaseIds)}
          ${enumSelect('时限类型 (timeLimitType)', 'q.timeLimitType', q.timeLimitType || 'REAL_SECONDS', ['REAL_SECONDS', 'GAME_DAY_TIME'])}
        </div>
        <div class="row">
          ${field('时限值 (timeLimitValue)', 'q.timeLimitValue', q.timeLimitValue ?? 0, 'number')}
          ${phaseSingleSelect('初始阶段ID (Initial Phase ID)', 'q.initialPhaseId', q.initialPhaseId || '', phaseIds)}
        </div>
        <div class="row">
          ${field('商店ID (Chapter Shop ID)', 'q.chapterShopId', q.chapterShopId || '')}
          ${field('商店类型 (Chapter Shop Type)', 'q.chapterShopType', q.chapterShopType || '')}
        </div>
        <div class="row">
          ${suggestInput('图标路径 (Icon Texture)', 'q.iconTexture', q.iconTexture || '', ['minecraft:textures/item/iron_ingot.png', 'minecraft:textures/item/diamond.png', 'arc_quest:textures/gui/quest.png'], 'iconTextureSuggest', 'namespace:path/to/texture.png')}
          ${field('章节开始音效 (chapterStartSound)', 'q.chapterStartSound', q.chapterStartSound || '')}
        </div>
        <div class="row">
          ${field('章节失败音效 (chapterFailSound)', 'q.chapterFailSound', q.chapterFailSound || '')}
          ${field('章节完成音效 (chapterCompleteSound)', 'q.chapterCompleteSound', q.chapterCompleteSound || '')}
        </div>
        <div class="row">
          ${boolSelect('商店常驻 (Persistent)', 'q.chapterShopPersistent', q.chapterShopPersistent)}
          ${chipEditor('完成时触发标记 (flagsToSetOnComplete)', q.flagsToSetOnComplete || [], 'q.flagsToSetOnComplete', 'q.flagsToSetOnComplete', '输入 flag 后点击添加')}
        </div>
        <div class="row">
          ${area('解锁条件 (unlockConditions JSON)', 'q.unlockConditions', q.unlockConditions ? JSON.stringify(q.unlockConditions, null, 2) : '')}
          ${area('相关标记 (relatedMarks JSON)', 'q.relatedMarks', q.relatedMarks ? JSON.stringify(q.relatedMarks, null, 2) : '')}
        </div>
      </div>

      ${renderCollectionWorkspace(state, q, field)}

      <div class="actions" style="margin-top: 24px;">
        <button id="addPhaseBtn" class="primary">
          <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2" fill="none"><path d="M12 5v14M5 12h14"/></svg> 新增执行阶段 (Phase)
        </button>
      </div>
    </div>
  `;
}
