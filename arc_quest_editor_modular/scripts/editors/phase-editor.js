import { renderRewardList } from './reward-editor.js';
import { renderPhaseModeSummary } from './shared.js';

function modeSelect(label, bind, value) {
  return `<div class="f"><label>${label}</label><select data-b="${bind}"><option value="translatable" ${value === 'translatable' ? 'selected' : ''}>Translatable</option><option value="literal" ${value === 'literal' ? 'selected' : ''}>Literal</option></select></div>`;
}

function multiPhaseSelect(label, bind, selectedIds, phaseIds, selfId) {
  const selected = new Set(selectedIds || []);
  const options = phaseIds
    .filter(id => id && id !== selfId)
    .map(id => `<option value="${id}" ${selected.has(id) ? 'selected' : ''}>${id}</option>`)
    .join('');
  return `<div class="f"><label>${label}</label><select data-b="${bind}" multiple size="5">${options}</select></div>`;
}

function transitionTargetSelect(label, bind, value, phaseIds, selfId) {
  const options = ['<option value="">(未设置)</option>', ...phaseIds
    .filter(id => id && id !== selfId)
    .map(id => `<option value="${id}" ${value === id ? 'selected' : ''}>${id}</option>`)];
  return `<div class="f"><label>${label}</label><select data-b="${bind}">${options.join('')}</select></div>`;
}

function transitionConditionTypeSelect(bind, value) {
  const options = ['always', 'flag_set', 'flag_not_set', 'quest_completed', 'variable', 'and', 'or', 'not'];
  return `<div class="f"><label>条件类型</label><select data-b="${bind}">${options.map(op => `<option value="${op}" ${value === op ? 'selected' : ''}>${op}</option>`).join('')}</select></div>`;
}

function conditionField(label, bind, value, type = 'text') {
  return `<div class="f"><label>${label}</label><input data-b="${bind}" type="${type}" value="${value ?? ''}"></div>`;
}

function renderConditionEditor(bindBase, condition) {
  const c = condition || { type: 'always' };
  const type = c.type || 'always';
  if (type === 'flag_set' || type === 'flag_not_set') {
    return `<div class="row">${conditionField('Flag', `${bindBase}.flag`, c.flag || '')}</div>`;
  }
  if (type === 'quest_completed') {
    return `<div class="row">${conditionField('Quest ID', `${bindBase}.questId`, c.questId || '')}</div>`;
  }
  if (type === 'variable') {
    return `
      <div class="row">
        ${conditionField('Variable', `${bindBase}.variable`, c.variable || '')}
        <div class="f"><label>CompareOp</label><select data-b="${bindBase}.compareOp">${['EQUAL','NOT_EQUAL','GREATER','GREATER_OR_EQUAL','LESS','LESS_OR_EQUAL'].map(op => `<option value="${op}" ${c.compareOp === op ? 'selected' : ''}>${op}</option>`).join('')}</select></div>
        ${conditionField('Value', `${bindBase}.value`, c.value ?? 0, 'number')}
      </div>
    `;
  }
  if (type === 'and' || type === 'or') {
    return `
      <div class="card" style="margin-top:8px; border-color: rgba(255,255,255,0.08)">
        <div class="small"><b>Left</b></div>
        <div class="row">${transitionConditionTypeSelect(`${bindBase}.left.type`, c.left?.type || 'always')}</div>
        ${renderConditionEditor(`${bindBase}.left`, c.left)}
        <div class="small" style="margin-top:8px"><b>Right</b></div>
        <div class="row">${transitionConditionTypeSelect(`${bindBase}.right.type`, c.right?.type || 'always')}</div>
        ${renderConditionEditor(`${bindBase}.right`, c.right)}
      </div>
    `;
  }
  if (type === 'not') {
    return `
      <div class="card" style="margin-top:8px; border-color: rgba(255,255,255,0.08)">
        <div class="small"><b>Inner</b></div>
        <div class="row">${transitionConditionTypeSelect(`${bindBase}.left.type`, c.left?.type || 'always')}</div>
        ${renderConditionEditor(`${bindBase}.left`, c.left)}
      </div>
    `;
  }
  return '';
}

export function renderPhaseEditor(state, field, area) {
  const s = state.ui.sel;
  const p = state.q.phases[s.pi];
  const phaseIds = (state.q.phases || []).map(x => x.id).filter(Boolean);

  return `
    <div class="sec">
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom: 20px;">
        <h3 style="font-size:20px; font-weight:700; color:var(--text-main); margin:0;">阶段节点配置 (Phase: ${s.pi})</h3>
        ${renderPhaseModeSummary(p)}
      </div>

      <div class="card">
        ${field('阶段标识 (Phase ID)', `ph.${s.pi}.id`, p.id)}
        <div class="row">
          ${field('标题 (Title)', `ph.${s.pi}.title`, p.title || '')}
          ${modeSelect('Title Mode', `ph.${s.pi}.titleMode`, p.titleMode || 'translatable')}
        </div>
        <div class="row">
          ${area('描述 (Description)', `ph.${s.pi}.description`, p.description || '')}
          ${modeSelect('Description Mode', `ph.${s.pi}.descriptionMode`, p.descriptionMode || 'translatable')}
        </div>
      </div>

      <h4>流程与执行策略 (Phase Flow)</h4>
      <div class="card">
        <div class="row">
          <div class="f"><label>运行模式 (Mode)</label><select data-b="ph.${s.pi}.mode"><option value="normal" ${p.mode === 'normal' ? 'selected' : ''}>Normal (常规)</option><option value="parallel" ${p.mode === 'parallel' ? 'selected' : ''}>Parallel (平行)</option><option value="choice" ${p.mode === 'choice' ? 'selected' : ''}>Choice (选择)</option></select></div>
          <div class="f"><label>自动启动 (Auto Start)</label><select data-b="ph.${s.pi}.autoStart"><option value="false" ${!p.autoStart ? 'selected' : ''}>False</option><option value="true" ${p.autoStart ? 'selected' : ''}>True</option></select></div>
        </div>
        <div class="row">
          ${field('tradeShopId', `ph.${s.pi}.tradeShopId`, p.tradeShopId || '')}
          ${field('intelSceneId', `ph.${s.pi}.intelSceneId`, p.intelSceneId || '')}
        </div>
        <div class="row">
          ${field('phaseStartSound', `ph.${s.pi}.phaseStartSound`, p.phaseStartSound || '')}
          ${field('phaseCompleteSound', `ph.${s.pi}.phaseCompleteSound`, p.phaseCompleteSound || '')}
        </div>
        <div class="row">
          ${multiPhaseSelect('平行阶段关联 (parallelPhaseIds)', `ph.${s.pi}.parallelPhaseIds`, p.parallelPhaseIds || [], phaseIds, p.id)}
          ${multiPhaseSelect('选择阶段关联 (choicePhaseIds)', `ph.${s.pi}.choicePhaseIds`, p.choicePhaseIds || [], phaseIds, p.id)}
        </div>
        <div class="small">提示：按住 Ctrl / Cmd 可多选，当前 phase 不会出现在候选列表中。</div>
      </div>

      <h4>Phase 附加结构</h4>
      <div class="card">
        ${area('relatedMarks JSON', `ph.${s.pi}.relatedMarks`, p.relatedMarks ? JSON.stringify(p.relatedMarks, null, 2) : '')}
        ${area('visualConfig JSON', `ph.${s.pi}.visualConfig`, p.visualConfig ? JSON.stringify(p.visualConfig, null, 2) : '')}
      </div>

      <h4>过渡连接 (Transitions)</h4>
      <div class="card">
        ${(p.transitions || []).map((t, ti) => `
          <div class="card" style="margin:8px 0; border-color: rgba(255,255,255,0.1);">
            <div class="row">
              ${transitionTargetSelect('目标阶段 (targetPhaseId)', `ph.${s.pi}.tr.${ti}.targetPhaseId`, t.targetPhaseId || '', phaseIds, p.id)}
              ${transitionConditionTypeSelect(`ph.${s.pi}.tr.${ti}.conditionType`, t.condition?.type || 'always')}
            </div>
            ${renderConditionEditor(`ph.${s.pi}.tr.${ti}.c`, t.condition)}
            <div class="actions"><button data-dt="${ti}" class="danger">删除过渡</button></div>
          </div>
        `).join('')}
        <div class="actions"><button id="addTransitionBtn">+ 添加过渡</button></div>
      </div>

      <h4>阶段分支选项 (Choices)</h4>
      <div class="card">
        ${(p.choices || []).map((c, ci) => `
          <div class="card" style="margin:8px 0; border-color: rgba(255,255,255,0.1);">
            <div class="row">
              ${field('text', `ph.${s.pi}.ch.${ci}.text`, c.text || '')}
              ${transitionTargetSelect('targetPhaseId', `ph.${s.pi}.ch.${ci}.targetPhaseId`, c.targetPhaseId || '', phaseIds, p.id)}
            </div>
            <div class="row">
              ${field('flagToSet', `ph.${s.pi}.ch.${ci}.flagToSet`, c.flagToSet || '')}
              ${transitionConditionTypeSelect(`ph.${s.pi}.ch.${ci}.vc.type`, c.visibleCondition?.type || 'always')}
            </div>
            ${renderConditionEditor(`ph.${s.pi}.ch.${ci}.vc`, c.visibleCondition)}
            <div class="actions"><button data-dch="${ci}" class="danger">删除 Choice</button></div>
          </div>
        `).join('')}
        <div class="actions"><button id="addChoiceBtn">+ 添加 Choice</button></div>
      </div>

      <h4>阶段目标 (Objectives)</h4>
      <div class="card" style="background: rgba(0,0,0,0.2)">
        ${(p.objectives || []).map((o, oi) => `
          <div class="card" style="margin: 8px 0; border-color: rgba(255,255,255,0.1);">
            <div style="display:flex; justify-content:space-between; align-items:center;">
              <div>
                <b style="color:var(--accent); font-size:14px;">${oi + 1}. ${o.type}</b>
                <div class="small" style="margin-top:4px; font-family:monospace">${o.id || ''}</div>
              </div>
              <div class="actions" style="margin-top:0;">
                <button data-open="${oi}">编辑配置</button>
                <button data-do="${oi}" class="danger">移除</button>
              </div>
            </div>
          </div>
        `).join('')}
        <div class="actions"><button id="addObjectiveBtn" class="primary">+ 添加新目标 (Objective)</button></div>
      </div>

      <h4>Collection Entry Config</h4>
      <div class="card">
        <div class="row">
          ${field('categoryId', `ph.${s.pi}.cec.categoryId`, p.collectionEntryConfig?.categoryId || '')}
          <div class="f"><label>visibilityMode</label><select data-b="ph.${s.pi}.cec.visibilityMode"><option value="VISIBLE_BY_DEFAULT" ${(p.collectionEntryConfig?.visibilityMode || 'VISIBLE_BY_DEFAULT') === 'VISIBLE_BY_DEFAULT' ? 'selected' : ''}>VISIBLE_BY_DEFAULT</option><option value="HIDDEN_BY_DEFAULT" ${p.collectionEntryConfig?.visibilityMode === 'HIDDEN_BY_DEFAULT' ? 'selected' : ''}>HIDDEN_BY_DEFAULT</option><option value="LOCKED" ${p.collectionEntryConfig?.visibilityMode === 'LOCKED' ? 'selected' : ''}>LOCKED</option></select></div>
        </div>
        <div class="row">
          <div class="f"><label>hiddenPresentationMode</label><select data-b="ph.${s.pi}.cec.hiddenPresentationMode"><option value="FULLY_HIDDEN" ${(p.collectionEntryConfig?.hiddenPresentationMode || 'FULLY_HIDDEN') === 'FULLY_HIDDEN' ? 'selected' : ''}>FULLY_HIDDEN</option><option value="PLACEHOLDER" ${p.collectionEntryConfig?.hiddenPresentationMode === 'PLACEHOLDER' ? 'selected' : ''}>PLACEHOLDER</option></select></div>
          <div class="f"><label>countingMode</label><select data-b="ph.${s.pi}.cec.countingMode"><option value="BINARY" ${(p.collectionEntryConfig?.countingMode || 'BINARY') === 'BINARY' ? 'selected' : ''}>BINARY</option><option value="ACCUMULATE" ${p.collectionEntryConfig?.countingMode === 'ACCUMULATE' ? 'selected' : ''}>ACCUMULATE</option><option value="UNIQUE_SET" ${p.collectionEntryConfig?.countingMode === 'UNIQUE_SET' ? 'selected' : ''}>UNIQUE_SET</option></select></div>
        </div>
        <div class="row">
          ${field('completionTarget', `ph.${s.pi}.cec.completionTarget`, p.collectionEntryConfig?.completionTarget ?? 1, 'number')}
          ${field('maxCount', `ph.${s.pi}.cec.maxCount`, p.collectionEntryConfig?.maxCount ?? 1, 'number')}
        </div>
        <div class="row">
          <div class="f"><label>rewardGrantMode</label><select data-b="ph.${s.pi}.cec.rewardGrantMode"><option value="AUTO" ${(p.collectionEntryConfig?.rewardGrantMode || 'AUTO') === 'AUTO' ? 'selected' : ''}>AUTO</option><option value="MANUAL" ${p.collectionEntryConfig?.rewardGrantMode === 'MANUAL' ? 'selected' : ''}>MANUAL</option></select></div>
          ${field('sortOrder', `ph.${s.pi}.cec.sortOrder`, p.collectionEntryConfig?.sortOrder ?? s.pi, 'number')}
        </div>
        <div class="row">
          <div class="f"><label>showInTrackerByDefault</label><select data-b="ph.${s.pi}.cec.showInTrackerByDefault"><option value="true" ${p.collectionEntryConfig?.showInTrackerByDefault ? 'selected' : ''}>true</option><option value="false" ${!p.collectionEntryConfig?.showInTrackerByDefault ? 'selected' : ''}>false</option></select></div>
          <div class="f"><label>repeatableProgress</label><select data-b="ph.${s.pi}.cec.repeatableProgress"><option value="true" ${p.collectionEntryConfig?.repeatableProgress ? 'selected' : ''}>true</option><option value="false" ${!p.collectionEntryConfig?.repeatableProgress ? 'selected' : ''}>false</option></select></div>
        </div>
        <div class="row">
          <div class="f"><label>repeatableCompletion</label><select data-b="ph.${s.pi}.cec.repeatableCompletion"><option value="true" ${p.collectionEntryConfig?.repeatableCompletion ? 'selected' : ''}>true</option><option value="false" ${!p.collectionEntryConfig?.repeatableCompletion ? 'selected' : ''}>false</option></select></div>
          <div class="f"></div>
        </div>
        ${area('visibilityConditions JSON', `ph.${s.pi}.cec.visibilityConditions`, p.collectionEntryConfig?.visibilityConditions ? JSON.stringify(p.collectionEntryConfig.visibilityConditions, null, 2) : '')}
        ${area('rewardNodes JSON', `ph.${s.pi}.cec.rewardNodes`, p.collectionEntryConfig?.rewardNodes ? JSON.stringify(p.collectionEntryConfig.rewardNodes, null, 2) : '')}
      </div>

      <h4>阶段奖励 (Phase Rewards)</h4>
      <div class="card">
        ${renderRewardList(p.rewards, 'phase', s.pi, field)}
        <div class="actions"><button id="addPhaseRewardBtn">+ 添加奖励</button></div>
      </div>

      <div class="actions" style="margin-top:40px; padding-top:20px; border-top:1px dashed var(--card-border);">
        <button id="movePhaseUpBtn">↑ 上移节点</button>
        <button id="movePhaseDownBtn">↓ 下移节点</button>
        <button id="deletePhaseBtn" class="danger" style="margin-left:auto;">
          <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg> 删除此阶段 (Phase)
        </button>
      </div>
    </div>
  `;
}
