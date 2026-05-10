import { renderConditionTree } from './condition-editor.js';
import { chipEditor } from './quest-editor-sections.js';

function modeSelect(label, bind, value) {
  return `<div class="f"><label>${label}</label><select data-b="${bind}"><option value="translatable" ${value === 'translatable' ? 'selected' : ''}>Translatable</option><option value="literal" ${value === 'literal' ? 'selected' : ''}>Literal</option></select></div>`;
}

function transitionTargetSelect(label, bind, value, phaseIds, selfId) {
  const options = ['<option value="">(未设置)</option>', ...phaseIds
    .filter(id => id && id !== selfId)
    .map(id => `<option value="${id}" ${value === id ? 'selected' : ''}>${id}</option>`)];
  return `<div class="f"><label>${label}</label><select data-b="${bind}">${options.join('')}</select></div>`;
}

export function renderPhaseFlowSection(s, p, phaseIds, field, area) {
  const isParallel = p.mode === 'parallel';
  const isChoice = p.mode === 'choice';
  return `
    <h4>流程与执行策略 (Phase Flow)</h4>
    <div class="card">
      <div class="row">
        <div class="f"><label>运行模式 (Mode)</label><select data-b="ph.${s.pi}.mode"><option value="normal" ${p.mode === 'normal' ? 'selected' : ''}>Normal (常规)</option><option value="parallel" ${isParallel ? 'selected' : ''}>Parallel (平行)</option><option value="choice" ${isChoice ? 'selected' : ''}>Choice (选择)</option></select></div>
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
        ${chipEditor('接取时触发标记 (setFlagOnEnter)', p.flagsToSetOnEnter || [], `ph.${s.pi}.flagsToSetOnEnter`, `ph.${s.pi}.flagsToSetOnEnter`, '输入 flag 后点击添加')}
        ${chipEditor('完成时触发标记 (setFlagOnComplete)', p.flagsToSetOnComplete || [], `ph.${s.pi}.flagsToSetOnComplete`, `ph.${s.pi}.flagsToSetOnComplete`, '输入 flag 后点击添加')}
      </div>
      <div class="small">${isParallel ? '并行阶段请直接在下方“过渡连接”中维护多个目标阶段。' : isChoice ? 'Choice 阶段请直接在下方“阶段分支选项”中维护分支文本与目标。' : '当前为普通阶段，不显示并行/分支子配置。'}</div>
    </div>

    <h4>Phase 附加结构</h4>
    <div class="card">
      ${area('relatedMarks JSON', `ph.${s.pi}.relatedMarks`, p.relatedMarks ? JSON.stringify(p.relatedMarks, null, 2) : '')}
      ${area('visualConfig JSON', `ph.${s.pi}.visualConfig`, p.visualConfig ? JSON.stringify(p.visualConfig, null, 2) : '')}
    </div>
  `;
}

export function renderPhaseTransitionsSection(s, p, phaseIds, conditionOptions) {
  const title = p.mode === 'choice' ? '阶段去向 (Choice Targets / Transitions)' : '过渡连接 (Transitions)';
  return `
    <h4>${title}</h4>
    <div class="card">
      ${(p.transitions || []).map((t, ti) => `
        <div class="card" style="margin:8px 0; border-color: rgba(255,255,255,0.1);">
          <div class="row">
            ${transitionTargetSelect('目标阶段 (targetPhaseId)', `ph.${s.pi}.tr.${ti}.targetPhaseId`, t.targetPhaseId || '', phaseIds, p.id)}
          </div>
          ${renderConditionTree(`ph.${s.pi}.tr.${ti}.c`, t.condition, conditionOptions)}
          <div class="actions"><button data-dt="${ti}" class="danger">删除过渡</button></div>
        </div>
      `).join('')}
      <div class="actions"><button id="addTransitionBtn">+ 添加过渡</button></div>
    </div>
  `;
}

export function renderPhaseChoicesSection(s, p, phaseIds, field, conditionOptions) {
  if (p.mode !== 'choice') return '';
  return `
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
          </div>
          ${renderConditionTree(`ph.${s.pi}.ch.${ci}.vc`, c.visibleCondition, conditionOptions)}
          <div class="actions"><button data-dch="${ci}" class="danger">删除 Choice</button></div>
        </div>
      `).join('')}
      <div class="actions"><button id="addChoiceBtn">+ 添加 Choice</button></div>
    </div>
  `;
}

export function renderPhaseCollectionSection(s, p, field, area) {
  return `
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
  `;
}

export function renderPhaseHeaderSection(s, p, field, area) {
  return `
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
  `;
}
