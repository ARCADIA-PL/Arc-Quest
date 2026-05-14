import {
    boolSelect,
    enumSelect,
    refSelect,
    renderCollectionTreeView,
    ruleEditor,
    scopeRefOptions
} from '../quest-editor-sections.js';

export function renderQuestCollectionWorkspace(state, q, field, phaseIds) {
    const isCollectionQuest = q.mode === 'COLLECTION';
    q.collectionConfig ||= {categories: [], rewardNodes: [], completionRules: []};
    const categories = q.collectionConfig.categories || [];
    const topNodes = q.collectionConfig.rewardNodes || [];
    const categoryIds = categories.map(c => c.categoryId).filter(Boolean);

    if (!isCollectionQuest) {
        return `
      <h4>Collection Workspace</h4>
      <div class="card"><div class="small">当前 Quest Mode 为 PROGRESSION，Collection 专属配置已隐藏。切换到 COLLECTION 后才显示。</div></div>
    `;
    }

    return `
    <h4>Collection Workspace</h4>
    <div class="actions" style="margin:8px 0 12px;">
      <button type="button" data-collection-view="card" class="${state.quest.ui.collectionView !== 'tree' ? 'primary' : ''}">Card View</button>
      <button type="button" data-collection-view="tree" class="${state.quest.ui.collectionView === 'tree' ? 'primary' : ''}">Tree View</button>
      <button type="button" id="fixCollectionRefsBtn">一键修复引用</button>
      <button type="button" id="fixCollectionRulesBtn">补齐 Rule 默认值</button>
    </div>
    ${state.quest.ui.collectionView === 'tree' ? renderCollectionTreeView(q) : `
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
        ${ruleEditor(rule, `q.tcr.${ri}`)}
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
          ${(node.completionRules || []).map((rule, ri) => `${ruleEditor(rule, `q.trn.${ni}.cr.${ri}`)}<div class="actions"><button data-dtrcr="${ni}:${ri}" class="danger">删除 Rule</button></div>`).join('')}
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
          ${(cat.completionRules || []).map((rule, ri) => `${ruleEditor(rule, `q.cat.${ci}.cr.${ri}`)}<div class="actions"><button data-dcr="${ci}:${ri}" class="danger">删除 Rule</button></div>`).join('')}
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
              ${(node.completionRules || []).map((rule, nri) => `${ruleEditor(rule, `q.cat.${ci}.rn.${ni}.cr.${nri}`)}<div class="actions"><button data-drcr="${ci}:${ni}:${nri}" class="danger">删除 Rule</button></div>`).join('')}
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
