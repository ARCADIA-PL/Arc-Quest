import { renderObjectiveExtra } from './shared.js';

function modeSelect(label, bind, value) {
  return `<div class="f"><label>${label}</label><select data-b="${bind}"><option value="translatable" ${value === 'translatable' ? 'selected' : ''}>translatable</option><option value="literal" ${value === 'literal' ? 'selected' : ''}>literal</option></select></div>`;
}

function boolSelect(label, bind, value) {
  return `<div class="f"><label>${label}</label><select data-b="${bind}"><option value="false" ${!value ? 'selected' : ''}>false</option><option value="true" ${value ? 'selected' : ''}>true</option></select></div>`;
}

export function renderObjectiveEditor(state, field) {
  const s = state.ui.sel;
  const o = state.q.phases[s.pi].objectives[s.oi];
  const base = `ob.${s.pi}.${s.oi}`;
  return `
    <div class="sec">
      <h3>Objective 编辑</h3>
      <div class="f"><label>类型</label><select data-b="${base}.type">${['kill','collect','talk','reach','interact','submit','custom_counter'].map(t => `<option value="${t}" ${o.type === t ? 'selected' : ''}>${t}</option>`).join('')}</select></div>
      ${field('ID', `${base}.id`, o.id || '')}
      <div class="row">
        ${field('文案 Key', `${base}.text`, o.text || '')}
        ${modeSelect('Text Mode', `${base}.textMode`, o.textMode || 'translatable')}
      </div>
      <div class="row">
        ${field('目标数量', `${base}.count`, o.count ?? 1, 'number')}
        ${field('targetId（原始目标）', `${base}.targetId`, o.targetId || '')}
      </div>
      <div class="row">
        ${boolSelect('Hidden', `${base}.hidden`, !!o.hidden)}
        ${boolSelect('Optional', `${base}.optional`, !!o.optional)}
      </div>
      ${renderObjectiveExtra(o, base, field)}
    </div>
  `;
}
