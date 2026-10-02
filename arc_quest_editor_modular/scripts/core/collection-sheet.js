import {cloneJson} from './json-document.js';
import {isIconResourceId, setObjectiveIconField, validateObjectiveIcon} from './objective-icon.js';

const object = value => value && typeof value === 'object' && !Array.isArray(value);
const list = value => Array.isArray(value) ? value : [];
const text = value => ({mode: 'literal', value});
export const COLLECTION_SUBJECTS = ['ENTITY', 'ITEM', 'CUSTOM'];
export const COLLECTION_RECORD_TYPES = ['DISCOVERED', 'RESEARCH_COMPLETE', 'RESEARCH_STEP'];
export const COLLECTION_OBJECTIVE_TYPES = ['KILL', 'COLLECT', 'CRAFT', 'INTERACT', 'TALK', 'OFFER', 'DELIVER', 'REACH_LOCATION', 'CUSTOM'];

function unique(values, prefix) {
    let index = 1;
    while (values.includes(`${prefix}_${index}`)) index++;
    return `${prefix}_${index}`;
}
export function createCollectionEntry(q) {
    return {entryId: unique(list(q.collectionConfig?.entries).map(e => e.entryId), 'arc_quest:entry'),
        categoryId: q.collectionConfig?.categories?.[0]?.categoryId || '', displayName: text('新图鉴条目'),
        description: text(''), subjectKind: 'ITEM', subjectId: 'minecraft:iron_ingot', itemTag: '',
        icon: {type: 'arc_quest:auto'}, content: [], relatedItems: [],
        discoveryObjectives: [{id: 'discover', type: 'COLLECT', targetId: 'minecraft:iron_ingot', requiredCount: 1, displayText: text('获得铁锭')}],
        researchObjectives: [], visibilityMode: 'VISIBLE_BY_DEFAULT', hiddenPresentationMode: 'FULLY_HIDDEN',
        sortOrder: list(q.collectionConfig?.entries).length, researchAfterDiscovery: false};
}
export function createCollectionSheet() {
    return {completionPolicy: 'ALL', requiredCount: 0, countDistinctEntries: false, bindings: []};
}
export function createCollectionBinding(q, pi) {
    const phase = q.phases[pi];
    return {bindingId: unique(list(phase.collectionSheet?.bindings).map(b => b.bindingId), 'entry_goal'),
        entryId: q.collectionConfig?.entries?.[0]?.entryId || q.collectionConfig?.entryIds?.[0] || '',
        objectiveIds: [], recordRequirements: [{type: 'DISCOVERED', stepId: ''}], requirementMode: 'ALL',
        recordPolicy: 'EXISTING_RECORDS', optional: false, sortOrder: list(phase.collectionSheet?.bindings).length};
}
export function createCollectionContent(entry = {}) {
    return {blockId: unique(list(entry.content).map(b => b.blockId), 'content'),
        text: text(''), media: {type: 'image', texture: '', width: 180, height: 90},
        caption: text(''), fit: 'CONTAIN', zoomable: true, reveal: 'DISCOVERED', revealStepId: ''};
}
export function createRecordObjective(entry, kind) {
    const subject = entry.subjectKind === 'ENTITY' ? 'KILL' : 'COLLECT';
    return {id: unique([...list(entry.discoveryObjectives), ...list(entry.researchObjectives)].map(o => o.id), kind === 'discoveryObjectives' ? 'discover' : 'research'),
        type: subject, targetId: entry.subjectId || '', itemTag: entry.itemTag || '', requiredCount: 1,
        displayText: text(kind === 'discoveryObjectives' ? '记录发现' : '研究目标')};
}

// Clone the Java spec verbatim. Old reward node/rule fields and future extension data must survive export.
export function exportCollectionConfig(config) { return config == null ? undefined : cloneJson(config); }
export function exportCollectionSheet(sheet) { return sheet == null ? undefined : cloneJson(sheet); }

export function syncCollectionObjectiveReferences(q, pi, oldId, newId) {
    if (!oldId || !newId || oldId === newId) return;
    for (const binding of list(q.phases?.[pi]?.collectionSheet?.bindings)) {
        binding.objectiveIds = list(binding.objectiveIds).map(id => id === oldId ? newId : id);
    }
}

// Namespaced paths separate raw Java Collection specs from the editor's ordinary Objective model.
export function setCollectionSheetField(q, bind, value, inputType) {
    const parts = bind.split('.');
    let root, path;
    if (parts[0] === 'q' && parts[1] === 'ce') {
        root = q.collectionConfig?.entries?.[Number(parts[2])]; path = parts.slice(3);
    } else if (parts[0] === 'q' && parts[1] === 'entryIds') {
        q.collectionConfig ||= {};
        q.collectionConfig.entryIds = String(value || '').split(/[\n,]/).map(s => s.trim()).filter(Boolean);
        return true;
    } else if (parts[0] === 'ph' && parts[2] === 'cs') {
        root = q.phases?.[Number(parts[1])]?.collectionSheet; path = parts.slice(3);
    } else return false;
    if (!root || !path.length || path.some(k => ['__proto__', 'constructor', 'prototype'].includes(k))) return true;
    const iconIndex = path.indexOf('icon');
    if (iconIndex >= 0) {
        const owner = path.slice(0, iconIndex).reduce((node, key) => node?.[key], root);
        if (owner) setObjectiveIconField(owner, path.slice(iconIndex + 1), value, inputType);
        return true;
    }
    let cursor = root;
    for (let i = 0; i < path.length - 1; i++) {
        if (!object(cursor[path[i]]) && !Array.isArray(cursor[path[i]])) cursor[path[i]] = /^\d+$/.test(path[i + 1]) ? [] : {};
        cursor = cursor[path[i]];
    }
    const leaf = path.at(-1), oldId = cursor[leaf];
    if (leaf === 'objectiveIds' || leaf === 'relatedItems') cursor[leaf] = String(value || '').split(/[\n,]/).map(s => s.trim()).filter(Boolean);
    else if (['optional', 'countDistinctEntries', 'researchAfterDiscovery', 'zoomable', 'hidden'].includes(leaf)) cursor[leaf] = value === 'true' || value === true;
    else if (inputType === 'number') cursor[leaf] = value === '' ? null : Number(value);
    else if (leaf === 'extraData') {
        try {
            const decoded = JSON.parse(value || '{}');
            cursor[leaf] = cloneJson(decoded);
        } catch { cursor[leaf] = value; }
    } else cursor[leaf] = value;
    if (leaf === 'completionPolicy' && parts[2] === 'cs') cursor.requiredCount = value === 'ALL' ? 0 : Math.max(1, Number(cursor.requiredCount) || 1);
    if (leaf === 'type' && path.includes('recordRequirements') && value !== 'RESEARCH_STEP') cursor.stepId = '';
    if (leaf === 'reveal' && value !== 'RESEARCH_STEP') cursor.revealStepId = '';
    if (leaf === 'entryId' && parts[1] === 'ce' && oldId && oldId !== value) {
        for (const phase of list(q.phases)) for (const binding of list(phase.collectionSheet?.bindings)) {
            if (binding.entryId === oldId) binding.entryId = value;
        }
    }
    if (leaf === 'id' && ['researchObjectives', 'discoveryObjectives'].includes(path[0]) && oldId && oldId !== value) {
        for (const block of list(root.content)) if (block.revealStepId === oldId) block.revealStepId = value;
        for (const phase of list(q.phases)) for (const binding of list(phase.collectionSheet?.bindings)) {
            if (binding.entryId === root.entryId) for (const requirement of list(binding.recordRequirements)) {
                if (requirement.type === 'RESEARCH_STEP' && requirement.stepId === oldId) requirement.stepId = value;
            }
        }
    }
    return true;
}

export function applyCollectionEditorAction(q, action) {
    const [verb, a, b] = String(action || '').split(':');
    const pi = Number(a), index = Number(b);
    if (verb === 'add-entry') {
        q.collectionConfig ||= {}; q.collectionConfig.entries ||= [];
        q.collectionConfig.entries.push(createCollectionEntry(q)); return true;
    }
    if (verb === 'delete-entry') { q.collectionConfig?.entries?.splice(pi, 1); return true; }
    if (verb === 'add-sheet') { q.phases[pi].collectionSheet = createCollectionSheet(); return true; }
    if (verb === 'delete-sheet') { delete q.phases[pi].collectionSheet; return true; }
    if (verb === 'add-binding') { (q.phases[pi].collectionSheet.bindings ||= []).push(createCollectionBinding(q, pi)); return true; }
    if (verb === 'delete-binding') { q.phases[pi].collectionSheet.bindings.splice(index, 1); return true; }
    if (verb === 'add-record') {
        const requirements = q.phases[pi].collectionSheet.bindings[index].recordRequirements ||= [];
        const nextType = ['DISCOVERED','RESEARCH_COMPLETE','RESEARCH_STEP'].find(type => !requirements.some(r => r.type === type)) || 'RESEARCH_STEP';
        requirements.push({type: nextType, stepId: ''}); return true;
    }
    if (verb === 'delete-record') { q.phases[pi].collectionSheet.bindings[index].recordRequirements.splice(Number(action.split(':')[3]), 1); return true; }
    const entry = q.collectionConfig?.entries?.[pi];
    if (!entry) return false;
    if (verb === 'add-content') { (entry.content ||= []).push(createCollectionContent(entry)); return true; }
    if (verb === 'delete-content') { entry.content.splice(index, 1); return true; }
    for (const [short, kind] of [['discovery', 'discoveryObjectives'], ['research', 'researchObjectives']]) {
        if (verb === `add-${short}`) { (entry[kind] ||= []).push(createRecordObjective(entry, kind)); return true; }
        if (verb === `delete-${short}`) { entry[kind].splice(index, 1); return true; }
    }
    return false;
}

export function validateCollectionSheets(q, diagnostics = []) {
    const error = (path, msg) => diagnostics.push({lvl: 'err', path, msg});
    const warn = (path, msg) => diagnostics.push({lvl: 'warn', path, msg});
    const cc = q.collectionConfig || {}, entries = list(cc.entries), ids = new Set();
    const categories = new Set(list(cc.categories).map(c => c?.categoryId));
    for (const key of ['entries', 'entryIds']) if (cc[key] != null && !Array.isArray(cc[key])) error(`collectionConfig.${key}`, `${key} 必须是数组，原始配置已保留`);
    entries.forEach((entry, i) => {
        const path = `collectionConfig.entries[${i}]`;
        if (!object(entry)) { error(path, '条目必须是对象'); return; }
        if (!isIconResourceId(entry.entryId)) error(`${path}.entryId`, 'entryId 必须是稳定资源 ID');
        if (ids.has(entry.entryId)) error(`${path}.entryId`, `entryId 重复: ${entry.entryId}`);
        ids.add(entry.entryId);
        if (entry.categoryId && !categories.has(entry.categoryId)) error(`${path}.categoryId`, `分类不存在: ${entry.categoryId}`);
        if (!COLLECTION_SUBJECTS.includes(entry.subjectKind || 'CUSTOM')) error(`${path}.subjectKind`, '主体必须为 ENTITY、ITEM 或 CUSTOM');
        if (['ENTITY', 'ITEM'].includes(entry.subjectKind) && !isIconResourceId(entry.subjectId) && !isIconResourceId(entry.itemTag)) error(`${path}.subjectId`, '物品/生物主体需要有效 subjectId；物品也可使用 itemTag');
        validateObjectiveIcon(entry.icon, `${path}.icon`, diagnostics);
        if (entry.relatedItems != null && !Array.isArray(entry.relatedItems)) error(`${path}.relatedItems`, '关联物品必须是数组');
        list(entry.relatedItems).forEach(id => { if (!isIconResourceId(id)) error(`${path}.relatedItems`, `关联物品 ID 不合法: ${id}`); });
        for (const kind of ['discoveryObjectives', 'researchObjectives']) {
            if (entry[kind] != null && !Array.isArray(entry[kind])) error(`${path}.${kind}`, '目标必须为数组');
            const objectiveIds = new Set();
            list(entry[kind]).forEach((objective, oi) => {
                const op = `${path}.${kind}[${oi}]`;
                if (!objective?.id || objectiveIds.has(objective.id)) error(`${op}.id`, '目标 ID 不能为空或重复');
                objectiveIds.add(objective?.id);
                const canonicalType = String(objective?.type || '').toUpperCase().replace(/^ARC_QUEST:/, '');
                if (!COLLECTION_OBJECTIVE_TYPES.includes(canonicalType)) error(`${op}.type`, '不支持的 Objective 类型');
                if (!Number.isInteger(objective?.requiredCount ?? 1) || (objective?.requiredCount ?? 1) < 1) error(`${op}.requiredCount`, '目标数量必须为正整数');
                if (['KILL', 'COLLECT', 'CRAFT', 'OFFER', 'DELIVER'].includes(objective?.type) && !objective.targetId && !objective.itemTag) error(`${op}.targetId`, '此目标需要 targetId 或 itemTag');
                if (objective?.extraData != null && !object(objective.extraData)) error(`${op}.extraData`, 'Objective 参数必须是 JSON 对象');
                if (objective?.type === 'REACH_LOCATION') {
                    for (const key of ['x','y','z']) if (!Number.isFinite(objective[key] ?? objective.extraData?.[key])) error(`${op}.${key}`, '到达位置需要有效坐标');
                    if (!(Number(objective.radius ?? objective.extraData?.radius) > 0)) error(`${op}.radius`, '到达位置需要正检测半径');
                }
                validateObjectiveIcon(objective?.icon, `${op}.icon`, diagnostics);
            });
        }
        if (entry.content != null && !Array.isArray(entry.content)) error(`${path}.content`, '图文内容必须是数组');
        const blockIds = new Set();
        list(entry.content).forEach((block, bi) => {
            const bp = `${path}.content[${bi}]`;
            if (!object(block)) { error(bp, '图文块必须是对象'); return; }
            if (!block.blockId || blockIds.has(block.blockId)) error(`${bp}.blockId`, 'blockId 不能为空或重复');
            blockIds.add(block.blockId);
            if (!['ALWAYS', 'DISCOVERED', 'RESEARCH_COMPLETE', 'RESEARCH_STEP'].includes(block.reveal || 'DISCOVERED')) error(`${bp}.reveal`, '资料公开时机不合法');
            if (block.reveal === 'RESEARCH_STEP' && !list(entry.researchObjectives).some(o => o.id === block.revealStepId)) error(`${bp}.revealStepId`, '资料解锁的研究步骤不存在');
            if (!['CONTAIN', 'COVER'].includes(block.fit || 'CONTAIN')) error(`${bp}.fit`, '图片适应必须为 CONTAIN 或 COVER');
            if (block.media?.texture) {
                if (!isIconResourceId(block.media.texture)) error(`${bp}.media.texture`, '配图必须是资源 ID，不能是磁盘路径或网址');
                for (const key of ['width', 'height']) if (!Number.isInteger(block.media[key]) || block.media[key] < 1 || block.media[key] > 4096) error(`${bp}.media.${key}`, '显示尺寸必须为 1 到 4096 的整数');
            }
        });
    });
    for (const ref of list(cc.entryIds)) {
        if (!isIconResourceId(ref)) error('collectionConfig.entryIds', `共享条目引用不是合法资源 ID: ${ref}`);
        ids.add(ref);
    }
    list(q.phases).forEach((phase, pi) => {
        const sheet = phase.collectionSheet;
        if (sheet == null) return;
        const path = `phases[${pi}].collectionSheet`;
        if (!object(sheet)) { error(path, '调查章节配置必须是对象'); return; }
        if (!['ALL', 'QUOTA'].includes(sheet.completionPolicy || 'ALL')) error(`${path}.completionPolicy`, '门槛必须为 ALL 或 QUOTA');
        if (!Array.isArray(sheet.bindings) || !sheet.bindings.length) error(`${path}.bindings`, '调查章节至少需要一个条目绑定');
        const bindingIds = new Set(), objectives = new Set(list(phase.objectives).map(o => o?.id));
        list(sheet.bindings).forEach((binding, bi) => {
            const bp = `${path}.bindings[${bi}]`;
            if (!object(binding)) { error(bp, '条目绑定必须是对象'); return; }
            if (!binding.bindingId || bindingIds.has(binding.bindingId)) error(`${bp}.bindingId`, 'bindingId 不能为空或重复');
            bindingIds.add(binding.bindingId);
            if (!ids.has(binding.entryId)) error(`${bp}.entryId`, `条目不存在: ${binding.entryId}`);
            if (!['ALL', 'ANY'].includes(binding.requirementMode || 'ALL')) error(`${bp}.requirementMode`, '要求组合必须为 ALL 或 ANY');
            if (!['EXISTING_RECORDS', 'NEW_DISCOVERIES'].includes(binding.recordPolicy || 'EXISTING_RECORDS')) error(`${bp}.recordPolicy`, '记录策略不合法；本轮行动使用 objectiveIds 引用阶段目标');
            for (const key of ['objectiveIds', 'recordRequirements']) if (binding[key] != null && !Array.isArray(binding[key])) error(`${bp}.${key}`, `${key} 必须是数组`);
            if (!list(binding.objectiveIds).length && !list(binding.recordRequirements).length) error(bp, '至少配置一个行动目标或记录要求');
            if (new Set(list(binding.objectiveIds)).size !== list(binding.objectiveIds).length) error(`${bp}.objectiveIds`, '行动目标引用不能重复');
            list(binding.objectiveIds).forEach(id => { if (!objectives.has(id)) error(`${bp}.objectiveIds`, `本阶段没有 Objective: ${id}`); });
            const entry = entries.find(e => e?.entryId === binding.entryId);
            list(binding.recordRequirements).forEach((requirement, ri) => {
                const rp = `${bp}.recordRequirements[${ri}]`;
                if (!COLLECTION_RECORD_TYPES.includes(requirement?.type)) error(`${rp}.type`, '记录要求类型不合法');
                if (requirement?.type === 'RESEARCH_STEP' && (!requirement.stepId || (entry && !list(entry.researchObjectives).some(o => o.id === requirement.stepId)))) error(`${rp}.stepId`, '研究步骤不存在或没有指定 stepId');
                if (requirement?.type !== 'RESEARCH_STEP' && requirement?.stepId) error(`${rp}.stepId`, '只有 RESEARCH_STEP 可以指定步骤');
                if (binding.recordPolicy === 'NEW_DISCOVERIES' && requirement?.type !== 'DISCOVERED') error(`${rp}.type`, '新发现策略只支持 DISCOVERED');
            });
            if (new Set(list(binding.recordRequirements).map(r => `${r?.type}:${r?.stepId || ''}`)).size !== list(binding.recordRequirements).length) error(`${bp}.recordRequirements`, '记录要求不能重复');
            if (binding.recordPolicy === 'NEW_DISCOVERIES' && !list(binding.recordRequirements).length) error(`${bp}.recordPolicy`, '新发现策略需要 DISCOVERED 记录要求');
            if (q.repeatable && !list(binding.objectiveIds).length && list(binding.recordRequirements).length && binding.recordPolicy !== 'NEW_DISCOVERIES') warn(bp, '重复任务仅认可长期记录会再次直接达成；建议绑定本轮行动 Objective');
        });
        if (sheet.completionPolicy === 'QUOTA') {
            const candidates = list(sheet.bindings).filter(b => object(b) && !b.optional);
            const total = sheet.countDistinctEntries ? new Set(candidates.map(b => b.entryId)).size : candidates.length;
            if (!Number.isInteger(sheet.requiredCount) || sheet.requiredCount < 1 || sheet.requiredCount > total) error(`${path}.requiredCount`, `配额必须为 1 到 ${total}；可选条目不计入门槛`);
        }
        if ((sheet.completionPolicy || 'ALL') === 'ALL' && Number(sheet.requiredCount || 0) !== 0) error(`${path}.requiredCount`, 'ALL 门槛不应配置非零 requiredCount');
        if (phase.collectionEntryConfig) warn(path, '此阶段同时有旧条目配置；新 collectionSheet 优先，旧配置保留供兼容');
        const mandatory = list(sheet.bindings).filter(b => object(b) && !b.optional);
        if (!mandatory.length) error(`${path}.bindings`, '调查章节不能只有可选条目，至少保留一个必需条目');
    });
    return diagnostics;
}
