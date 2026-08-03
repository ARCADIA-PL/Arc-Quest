import {renderObjectiveCommonSection, renderObjectiveExtra} from './shared.js';
import {renderMarkEditor} from './mark-editor.js';

function modeSelect(label, bind, value) {
    return `<div class="f"><label>${label}</label><select data-b="${bind}"><option value="translatable" ${value === 'translatable' ? 'selected' : ''}>translatable</option><option value="literal" ${value === 'literal' ? 'selected' : ''}>literal</option></select></div>`;
}

function objectiveTypeSelect(bind, value) {
    const types = [
        {id: 'KILL', label: 'KILL（击杀）'},
        {id: 'COLLECT', label: 'COLLECT（收集）'},
        {id: 'TALK', label: 'TALK（对话）'},
        {id: 'INTERACT', label: 'INTERACT（交互）'},
        {id: 'REACH_LOCATION', label: 'REACH_LOCATION（坐标）'},
        {id: 'DELIVER', label: 'DELIVER（交付）'},
        {id: 'CRAFT', label: 'CRAFT（制作）'},
        {id: 'OFFER', label: 'OFFER（提交）'},
        {id: 'CUSTOM', label: 'CUSTOM（自定义）'},
        {id: 'NULL', label: 'NULL（空目标/手动确认）'}
    ];
    return `<div class="f"><label>类型</label><select data-b="${bind}">${types.map(t => `<option value="${t.id}" ${value === t.id ? 'selected' : ''}>${t.label}</option>`).join('')}</select></div>`;
}

function renderTypeSummary(type) {
    const map = {
        KILL: '字段：targetId(entityType) + count',
        COLLECT: '字段：targetId(itemId 或 itemTag) + count',
        TALK: '字段：npcId / targetId，一次触发',
        INTERACT: '字段：targetId，一次触发',
        REACH_LOCATION: '字段：x/y/z/radius，一次触发',
        DELIVER: '字段：targetId(itemId) + npcId + count',
        CRAFT: '字段：targetId(itemId) + count',
        OFFER: '字段：targetId(itemId 或 itemTag) + count',
        CUSTOM: '字段：targetId，可附带额外数据',
        NULL: '字段：无目标，可配合手动确认推进使用'
    };
    return `<div class="small" style="margin-top:6px">${map[type] || '字段：按标准 ObjectiveSpec 渲染'}</div>`;
}

export function renderObjectiveEditor(state, field, area) {
    const s = state.quest.ui.sel;
    const o = state.quest.q.phases[s.pi].objectives[s.oi];
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
      ${renderObjectiveCommonSection(o, base, field)}
      ${area('extraData JSON', `${base}.extraData`, o.extraData ? JSON.stringify(o.extraData, null, 2) : '')}
      ${renderMarkEditor(o.relatedMarks, `${base}.relatedMarks`, 'Objective 相关标记')}
      <div class="card" style="margin-top:10px; border-color: rgba(255,255,255,0.12)">
        ${renderObjectiveExtra(o, base, field)}
      </div>
    </div>
  `;
}
