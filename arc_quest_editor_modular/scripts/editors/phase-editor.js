import { renderRewardList } from './reward-editor.js';
import { renderPhaseModeSummary, renderIdsHint } from './shared.js';

function modeSelect(label, bind, value) {
  return `<div class="f"><label>${label}</label><select data-b="${bind}"><option value="translatable" ${value === 'translatable' ? 'selected' : ''}>translatable</option><option value="literal" ${value === 'literal' ? 'selected' : ''}>literal</option></select></div>`;
}

function enumSelect(label, bind, value, options) {
  return `<div class="f"><label>${label}</label><select data-b="${bind}">${options.map(option => `<option value="${option}" ${value === option ? 'selected' : ''}>${option}</option>`).join('')}</select></div>`;
}

function renderCollectionEntryEditor(phase, field, index) {
  const c = phase.collectionEntryConfig;
  if (!c) return '';
  return `
    <h4>Collection Entry Config</h4>
    <div class="row">
      ${field('Category ID', `ph.${index}.cec.categoryId`, c.categoryId || '')}
      ${enumSelect('Visibility Mode', `ph.${index}.cec.visibilityMode`, c.visibilityMode || 'VISIBLE_BY_DEFAULT', ['VISIBLE_BY_DEFAULT', 'HIDDEN_BY_DEFAULT'])}
    </div>
    <div class="row">
      ${enumSelect('Hidden Presentation Mode', `ph.${index}.cec.hiddenPresentationMode`, c.hiddenPresentationMode || 'FULLY_HIDDEN', ['FULLY_HIDDEN', 'PLACEHOLDER', 'SHOW_TEXT_ONLY'])}
      ${enumSelect('Counting Mode', `ph.${index}.cec.countingMode`, c.countingMode || 'BINARY', ['BINARY', 'ACCUMULATE', 'UNIQUE_SET'])}
    </div>
    <div class="row">
      ${field('Completion Target', `ph.${index}.cec.completionTarget`, c.completionTarget ?? 1, 'number')}
      ${field('Sort Order', `ph.${index}.cec.sortOrder`, c.sortOrder ?? 0, 'number')}
    </div>
    <div class="row">
      ${field('Max Count', `ph.${index}.cec.maxCount`, c.maxCount ?? 0, 'number')}
      ${enumSelect('Reward Grant Mode', `ph.${index}.cec.rewardGrantMode`, c.rewardGrantMode || 'AUTO', ['AUTO', 'MANUAL'])}
    </div>
    <div class="f"><label>Show In Tracker By Default</label><select data-b="ph.${index}.cec.showInTrackerByDefault"><option value="false" ${!c.showInTrackerByDefault ? 'selected' : ''}>false</option><option value="true" ${c.showInTrackerByDefault ? 'selected' : ''}>true</option></select></div>
  `;
}

function renderConditionNode(node, bindBase, field, label = 'Condition Type') {
  const current = node || { type: 'always' };
  const type = current.type || 'always';
  const lines = [
    '<div class="card">',
    '<div class="row">',
    enumSelect(label, `${bindBase}.type`, type, ['always', 'flag_set', 'and', 'or']),
    '</div>'
  ];
  if (type === 'flag_set') {
    lines.push('<div class="row">');
    lines.push(field('Flag', `${bindBase}.flag`, current.flag || ''));
    lines.push('</div>');
  }
  if (type === 'and' || type === 'or') {
    lines.push('<div class="tiny">Left</div>');
    lines.push(renderConditionNode(current.left, `${bindBase}.left`, field));
    lines.push('<div class="tiny">Right</div>');
    lines.push(renderConditionNode(current.right, `${bindBase}.right`, field));
  }
  lines.push('</div>');
  return lines.join('');
}

function renderEnterConditionEditor(phase, field, index) {
  return `
    <h4>Enter Condition</h4>
    ${renderConditionNode(phase.rawEnterCondition, `ph.${index}.ec`, field)}
  `;
}

function renderTransitionEditor(phase, field, index) {
  return `
    <h4>Transitions</h4>
    ${(phase.transitions || []).map((tr, ti) => `
      <div class="card">
        <div class="row">
          ${field('Target Phase ID', `ph.${index}.tr.${ti}.targetPhaseId`, tr.targetPhaseId || '')}
        </div>
        ${renderConditionNode(tr.condition, `ph.${index}.tr.${ti}.c`, field, 'Transition Condition Type')}
        <div class="actions"><button data-dt="${ti}">删除 Transition</button></div>
      </div>
    `).join('')}
    <div class="actions"><button id="addTransitionBtn">+ 新增 Transition</button></div>
  `;
}

function renderImportedExtras(phase, field, area, index) {
  const bits = [];
  if (phase.story !== undefined) bits.push(`<div class="row">${area('Story', `ph.${index}.story`, phase.story || '')}${modeSelect('Story Mode', `ph.${index}.storyMode`, phase.storyMode || 'literal')}</div>`);
  bits.push(field('Intel Scene ID', `ph.${index}.intelSceneId`, phase.intelSceneId || ''));
  bits.push(field('flagsToSetOnEnter（逗号分隔）', `ph.${index}.flagsToSetOnEnter`, (phase.flagsToSetOnEnter || []).join(', ')));
  bits.push(field('flagsToSetOnComplete（逗号分隔）', `ph.${index}.flagsToSetOnComplete`, (phase.flagsToSetOnComplete || []).join(', ')));
  bits.push(renderEnterConditionEditor(phase, field, index));
  bits.push(area('Enter Condition JSON', `ph.${index}.rawEnterCondition`, phase.rawEnterCondition ? JSON.stringify(phase.rawEnterCondition, null, 2) : ''));
  bits.push(renderTransitionEditor(phase, field, index));
  bits.push(area('Transitions JSON', `ph.${index}.transitions`, phase.transitions ? JSON.stringify(phase.transitions, null, 2) : '[]'));
  if (phase.collectionEntryConfig) bits.push(renderCollectionEntryEditor(phase, field, index));
  if (phase.collectionEntryConfig) bits.push(area('Collection Entry Config JSON', `ph.${index}.collectionEntryConfig`, JSON.stringify(phase.collectionEntryConfig, null, 2)));
  return bits.join('');
}

export function renderPhaseEditor(state, field, area) {
  const s = state.ui.sel;
  const p = state.q.phases[s.pi];
  return `
    <div class="sec">
      <h3>Phase 编辑</h3>
      ${renderPhaseModeSummary(p)}
      ${renderIdsHint(p)}
      ${renderImportedExtras(p, field, area, s.pi)}
      ${field('Phase ID', `ph.${s.pi}.id`, p.id)}
      <div class="row">
        ${field('标题 Key', `ph.${s.pi}.title`, p.title || '')}
        ${modeSelect('Title Mode', `ph.${s.pi}.titleMode`, p.titleMode || 'translatable')}
      </div>
      <div class="row">
        ${area('描述 Key', `ph.${s.pi}.description`, p.description || '')}
        ${modeSelect('Description Mode', `ph.${s.pi}.descriptionMode`, p.descriptionMode || 'translatable')}
      </div>
      <h4>Phase Flow</h4>
      <div class="row">
        <div class="f"><label>Mode</label><select data-b="ph.${s.pi}.mode"><option value="normal" ${p.mode === 'normal' ? 'selected' : ''}>normal</option><option value="parallel" ${p.mode === 'parallel' ? 'selected' : ''}>parallel</option><option value="choice" ${p.mode === 'choice' ? 'selected' : ''}>choice</option></select></div>
        <div class="f"><label>autoStart</label><select data-b="ph.${s.pi}.autoStart"><option value="false" ${!p.autoStart ? 'selected' : ''}>false</option><option value="true" ${p.autoStart ? 'selected' : ''}>true</option></select></div>
      </div>
      ${field('parallelPhaseIds（逗号分隔）', `ph.${s.pi}.parallelPhaseIds`, (p.parallelPhaseIds || []).join(', '))}
      ${field('choicePhaseIds（逗号分隔）', `ph.${s.pi}.choicePhaseIds`, (p.choicePhaseIds || []).join(', '))}
      <h4>Objectives</h4>
      ${(p.objectives || []).map((o, oi) => `<div class="card"><b>${oi + 1}. ${o.type}</b><div class="small">${o.id || ''}</div><div class="tiny">${o.text || '-'}</div><div class="actions"><button data-open="${oi}">编辑</button><button data-do="${oi}">删除</button></div></div>`).join('')}
      <div class="actions"><button id="addObjectiveBtn">+ 新增 Objective</button></div>
      <h4>Phase Rewards</h4>
      ${renderRewardList(p.rewards, 'phase', s.pi, field)}
      <div class="actions"><button id="addPhaseRewardBtn">+ 新增 Phase Reward</button><button id="movePhaseUpBtn">↑ 上移 Phase</button><button id="movePhaseDownBtn">↓ 下移 Phase</button><button id="deletePhaseBtn" class="danger">删除当前 Phase</button></div>
    </div>
  `;
}
