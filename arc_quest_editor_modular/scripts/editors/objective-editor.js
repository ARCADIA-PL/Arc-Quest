import { renderObjectiveExtra } from './shared.js';

function modeSelect(label, bind, value) {
  return `<div class="f"><label>${label}</label><select data-b="${bind}"><option value="translatable" ${value === 'translatable' ? 'selected' : ''}>translatable</option><option value="literal" ${value === 'literal' ? 'selected' : ''}>literal</option></select></div>`;
}

function boolSelect(label, bind, value) {
  return `<div class="f"><label>${label}</label><select data-b="${bind}"><option value="false" ${!value ? 'selected' : ''}>false</option><option value="true" ${value ? 'selected' : ''}>true</option></select></div>`;
}

function objectiveTypeSelect(bind, value) {
  const types = [
    { id: 'kill', label: 'kill（击杀）' },
    { id: 'collect', label: 'collect（收集）' },
    { id: 'submit', label: 'submit（提交）' },
    { id: 'talk', label: 'talk（对话）' },
    { id: 'interact', label: 'interact（交互）' },
    { id: 'custom_counter', label: 'custom_counter（计数）' },
    { id: 'reach', label: 'reach（坐标）' }
  ];
  return `<div class="f"><label>类型</label><select data-b="${bind}">${types.map(t => `<option value="${t.id}" ${value === t.id ? 'selected' : ''}>${t.label}</option>`).join('')}</select></div>`;
}

function renderTypeSummary(type) {
  const map = {
    kill: '字段：entityType + count',
    collect: '字段：itemId + count',
    submit: '字段：itemId + count + consumeOnSubmit',
    talk: '字段：npcId + dialogueId',
    interact: '字段：targetType + targetId',
    custom_counter: '字段：counterId + count',
    reach: '字段：x + y + z + count'
  };
  return `<div class="small" style="margin-top:6px">${map[type] || '字段：按 fallback 渲染'}</div>`;
}

export function renderObjectiveEditor(state, field) {
  const s = state.ui.sel;
  const o = state.q.phases[s.pi].objectives[s.oi];
  const base = `ob.${s.pi}.${s.oi}`;
  return `
    <div class="sec">
      <h3>Objective 编辑</h3>
      ${objectiveTypeSelect(`${base}.type`, o.type)}
      ${renderTypeSummary(o.type)}
      ${field('ID', `${base}.id`, o.id || '')}
      <div class="row">
        ${field('文案 Key', `${base}.text`, o.text || '')}
        ${modeSelect('Text Mode', `${base}.textMode`, o.textMode || 'translatable')}
      </div>
      <div class="row">
        ${field('目标数量', `${base}.count`, o.count ?? 1, 'number')}
        ${(o.type === 'interact' || o.type === 'talk') ? field('targetId（兼容字段）', `${base}.targetId`, o.targetId || '') : '<div class="f"></div>'}
      </div>
      <div class="row">
        ${boolSelect('Hidden', `${base}.hidden`, !!o.hidden)}
        ${boolSelect('Optional', `${base}.optional`, !!o.optional)}
      </div>
      <div class="card" style="margin-top:10px; border-color: rgba(255,255,255,0.12)">
        ${renderObjectiveExtra(o, base, field)}
      </div>
    </div>
  `;
}
