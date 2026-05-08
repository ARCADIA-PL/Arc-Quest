import { renderObjectiveExtra } from './shared.js';

function modeSelect(label, bind, value) {
  return `<div class="f"><label>${label}</label><select data-b="${bind}"><option value="translatable" ${value === 'translatable' ? 'selected' : ''}>translatable</option><option value="literal" ${value === 'literal' ? 'selected' : ''}>literal</option></select></div>`;
}

function boolSelect(label, bind, value) {
  return `<div class="f"><label>${label}</label><select data-b="${bind}"><option value="false" ${!value ? 'selected' : ''}>false</option><option value="true" ${value ? 'selected' : ''}>true</option></select></div>`;
}

function objectiveTypeSelect(bind, value) {
  const types = [
    { id: 'KILL', label: 'KILL（击杀）' },
    { id: 'COLLECT', label: 'COLLECT（收集）' },
    { id: 'TALK', label: 'TALK（对话）' },
    { id: 'INTERACT', label: 'INTERACT（交互）' },
    { id: 'REACH_LOCATION', label: 'REACH_LOCATION（坐标）' },
    { id: 'DELIVER', label: 'DELIVER（交付）' },
    { id: 'CRAFT', label: 'CRAFT（制作）' },
    { id: 'OFFER', label: 'OFFER（提交）' },
    { id: 'CUSTOM', label: 'CUSTOM（自定义）' }
  ];
  return `<div class="f"><label>类型</label><select data-b="${bind}">${types.map(t => `<option value="${t.id}" ${value === t.id ? 'selected' : ''}>${t.label}</option>`).join('')}</select></div>`;
}

function renderTypeSummary(type) {
  const map = {
    KILL: '字段：targetId(entityType) + count',
    COLLECT: '字段：targetId(itemId 或 itemTag) + count',
    TALK: '字段：targetId/npcId',
    INTERACT: '字段：targetId',
    REACH_LOCATION: '字段：targetId + x/y/z/radius',
    DELIVER: '字段：targetId(itemId) + npcId + count',
    CRAFT: '字段：targetId(itemId) + count',
    OFFER: '字段：targetId(itemId 或 itemTag) + count',
    CUSTOM: '字段：targetId + count (+ extraData)'
  };
  return `<div class="small" style="margin-top:6px">${map[type] || '字段：按标准 ObjectiveSpec 渲染'}</div>`;
}

export function renderObjectiveEditor(state, field, area) {
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
        ${(o.type === 'INTERACT' || o.type === 'TALK' || o.type === 'DELIVER') ? field('targetId（兼容字段）', `${base}.targetId`, o.targetId || '') : '<div class="f"></div>'}
      </div>
      <div class="row">
        ${boolSelect('Hidden', `${base}.hidden`, !!o.hidden)}
        ${boolSelect('Optional', `${base}.optional`, !!o.optional)}
      </div>
      <div class="row">
        ${enumSelect('countMode', `${base}.countMode`, o.countMode || 'fixed', ['fixed', 'level_scale', 'variable'])}
        ${field('countBase', `${base}.countBase`, o.countBase ?? o.count ?? 1, 'number')}
      </div>
      <div class="row">
        ${field('countPerLevel', `${base}.countPerLevel`, o.countPerLevel ?? 0, 'number')}
        ${field('countMin', `${base}.countMin`, o.countMin ?? 1, 'number')}
      </div>
      <div class="row">
        ${field('countMax', `${base}.countMax`, o.countMax ?? -1, 'number')}
        ${field('itemTag', `${base}.itemTag`, o.itemTag || '')}
      </div>
      ${area('extraData JSON', `${base}.extraData`, o.extraData ? JSON.stringify(o.extraData, null, 2) : '')}
      ${area('relatedMarks JSON', `${base}.relatedMarks`, o.relatedMarks ? JSON.stringify(o.relatedMarks, null, 2) : '')}
      <div class="card" style="margin-top:10px; border-color: rgba(255,255,255,0.12)">
        ${renderObjectiveExtra(o, base, field)}
      </div>
    </div>
  `;
}
