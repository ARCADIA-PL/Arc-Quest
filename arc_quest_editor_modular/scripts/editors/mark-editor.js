import {esc} from '../core/utils.js';

const TARGET_TYPES = ['pos', 'dimension_pos', 'block', 'entity_type_nearest', 'entity_npc_id', 'structure_nearest', 'custom'];
const MARKER_TYPES = ['QUEST_MAIN', 'QUEST_SIDE', 'QUEST_PHASE', 'QUEST_OBJECTIVE', 'NPC_INTERACT', 'ENEMY_TARGET', 'LOCATION', 'CUSTOM'];

function triggerOptions(prefix) {
    if (prefix.startsWith('q.relatedMarks')) return ['CONTINUOUS', 'QUEST_ACCEPTED'];
    if (prefix.startsWith('ph.')) return ['CONTINUOUS', 'PHASE_ENTERED', 'PHASE_COMPLETED', 'PHASE_ADVANCED'];
    if (prefix.startsWith('ob.')) return ['CONTINUOUS', 'OBJECTIVE_COMPLETED'];
    if (prefix.includes('.ch.')) return ['DIALOGUE_CHOICE_SELECTED'];
    if (prefix.startsWith('diag.node.')) return ['DIALOGUE_NODE_ENTERED'];
    return ['CONTINUOUS'];
}

function select(label, bind, value, options) {
    return `<div class="f"><label>${label}</label><select data-b="${bind}">${options.map(option => `<option value="${option}" ${value === option ? 'selected' : ''}>${option}</option>`).join('')}</select></div>`;
}

function input(label, bind, value, type = 'text') {
    return `<div class="f"><label>${label}</label><input data-b="${bind}" type="${type}" value="${esc(value ?? '')}" placeholder="${label}"></div>`;
}

function jsonArea(label, bind, value) {
    return `<div class="f"><label>${label}</label><textarea data-b="${bind}" placeholder="${label}">${esc(value ? JSON.stringify(value, null, 2) : '')}</textarea></div>`;
}

function targetFields(marker, base) {
    const target = marker.target || {};
    const type = target.type || 'pos';
    if (type === 'pos' || type === 'block' || type === 'dimension_pos') return `<div class="row">${type === 'dimension_pos' ? input('Dimension', `${base}.target.dimension`, target.dimension) : ''}${input('X', `${base}.target.x`, target.x, 'number')}${input('Y', `${base}.target.y`, target.y, 'number')}${input('Z', `${base}.target.z`, target.z, 'number')}</div>`;
    if (type === 'entity_type_nearest') return `<div class="row">${input('Entity Type', `${base}.target.entityType`, target.entityType)}${input('Search Radius', `${base}.target.searchRadius`, target.searchRadius, 'number')}</div>`;
    if (type === 'entity_npc_id') return `<div class="row">${input('NPC ID', `${base}.target.npcId`, target.npcId)}${input('Search Radius', `${base}.target.searchRadius`, target.searchRadius, 'number')}</div>`;
    if (type === 'structure_nearest') return `<div class="row">${input('Structure Tag', `${base}.target.structureTag`, target.structureTag)}${input('Search Radius', `${base}.target.searchRadius`, target.searchRadius, 'number')}</div>`;
    return `<div class="row">${input('Resolver ID', `${base}.target.resolverId`, target.resolverId)}${jsonArea('Resolver Args JSON', `${base}.target.args`, target.args)}</div>`;
}

export function renderMarkEditor(marks, prefix, title = '相关标记') {
    const list = Array.isArray(marks) ? marks : [];
    return `<div class="card marker-editor"><h4>${title}</h4>
      <div class="small">标记由服务端解析；Custom target 需要附属模组注册同名 MarkTargetResolver。</div>
      ${list.map((marker, index) => { const base = `${prefix}.${index}`; return `<div class="card" style="margin-top:8px;border-color:rgba(255,255,255,.14)">
        <div class="row">${input('Marker ID', `${base}.id`, marker.id)}${select('Marker Type', `${base}.markerType`, marker.markerType, MARKER_TYPES)}${select('Target Type', `${base}.target.type`, marker.target?.type || 'pos', TARGET_TYPES)}</div>
        ${targetFields(marker, base)}
        <div class="row">${input('Priority', `${base}.priority`, marker.priority, 'number')}${input('Max Distance', `${base}.maxDistance`, marker.maxDistance, 'number')}${input('Refresh Ticks', `${base}.refreshTicks`, marker.refreshTicks, 'number')}</div>
        <div class="row">${select('触发时机', `${base}.trigger`, marker.trigger || 'CONTINUOUS', triggerOptions(prefix))}${input('显示时长（tick）', `${base}.durationTicks`, marker.durationTicks ?? 100, 'number')}</div>
        <div class="tiny">CONTINUOUS 保持旧版持续显示语义；事件触发型 Marker 默认显示 100 tick，并在服务端到期后自动清理。</div>
        <div class="row">${select('Track Moving Entity', `${base}.trackMovingEntity`, String(marker.trackMovingEntity !== false), ['true','false'])}${select('One Shot', `${base}.oneShot`, String(!!marker.oneShot), ['false','true'])}</div>
        <div class="row">${jsonArea('Activate When JSON', `${base}.activateWhen`, marker.activateWhen)}${jsonArea('Deactivate When JSON', `${base}.deactivateWhen`, marker.deactivateWhen)}</div>
        ${jsonArea('Style Hints JSON', `${base}.styleHints`, marker.styleHints)}
        <div class="tiny">styleHints 支持 label、color、showDistance、allowOffscreenArrow、scale、opacity。</div>
        <div class="actions"><button class="danger" data-marker-delete="${prefix}:${index}">删除标记</button></div>
      </div>`; }).join('') || '<div class="tiny">暂无标记配置</div>'}
      <div class="actions"><button data-marker-add="${prefix}">＋ 添加标记</button></div>
    </div>`;
}
