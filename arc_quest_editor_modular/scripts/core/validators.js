import {ensureQuestShape} from '../core/quest-shape.js';

const OBJECTIVE_TYPES = new Set(['KILL', 'COLLECT', 'TALK', 'INTERACT', 'REACH_LOCATION', 'DELIVER', 'CRAFT', 'OFFER', 'CUSTOM']);
const REWARD_TYPES = new Set(['item', 'flag_set', 'flag_clear', 'command', 'var_set', 'var_add', 'var_subtract', 'var_multiply']);
const CONDITION_TYPES = new Set([
    'arc_quest:always',
    'arc_quest:quest_completed',
    'arc_quest:quest_accepted',
    'arc_quest:quest_not_started',
    'arc_quest:quest_phase',
    'arc_quest:quest_phase_completed',
    'arc_quest:quest_phase_reached',
    'arc_quest:has_flag',
    'arc_quest:not_has_flag',
    'arc_quest:hold_item',
    'arc_quest:variable_check',
    'arc_quest:and',
    'arc_quest:or',
    'arc_quest:not',
    'minecraft:entity_properties'
]);

function hasLegacyObjectiveShape(obj) {
    return ['entityType', 'itemId', 'targetType', 'counterId', 'dialogueId', 'consumeOnSubmit'].some(k => Object.prototype.hasOwnProperty.call(obj || {}, k));
}

function validateConditionNode(node, path, d) {
    if (!node) return;
    const cond = node.condition || 'arc_quest:always';
    if (!CONDITION_TYPES.has(cond)) {
        d.push({lvl: 'err', path, msg: `Condition type 非法: ${cond}`});
        return;
    }
    if ((cond === 'arc_quest:has_flag' || cond === 'arc_quest:not_has_flag') && !node.flag) {
        d.push({lvl: 'warn', path, msg: `${cond} 缺少 flag`});
    }
    if ((cond === 'arc_quest:quest_completed' || cond === 'arc_quest:quest_accepted' || cond === 'arc_quest:quest_not_started') && !node.quest_id) {
        d.push({lvl: 'warn', path, msg: `${cond} 缺少 quest_id`});
    }
    if ((cond === 'arc_quest:quest_phase' || cond === 'arc_quest:quest_phase_completed' || cond === 'arc_quest:quest_phase_reached')) {
        if (!node.quest_id) d.push({lvl: 'warn', path, msg: `${cond} 缺少 quest_id`});
        if (!node.phase_id) d.push({lvl: 'warn', path, msg: `${cond} 缺少 phase_id`});
    }
    if (cond === 'arc_quest:variable_check') {
        if (!node.key) d.push({lvl: 'warn', path, msg: 'variable_check 缺少 key'});
        if (!['EQUAL', 'NOT_EQUAL', 'GREATER', 'GREATER_OR_EQUAL', 'LESS', 'LESS_OR_EQUAL'].includes(node.op || '')) d.push({
            lvl: 'warn',
            path,
            msg: 'variable_check.op 非标准'
        });
    }
    if (cond === 'arc_quest:and' || cond === 'arc_quest:or') {
        (node.conditions || []).forEach((sub, idx) => {
            validateConditionNode(sub, `${path}.conditions.${idx}`, d);
        });
    }
    if (cond === 'arc_quest:not') {
        validateConditionNode(node.inner, `${path}.inner`, d);
    }
    if (cond === 'minecraft:entity_properties' && !node.predicate) {
        d.push({lvl: 'warn', path, msg: 'entity_properties 缺少 predicate'});
    }
}

export function validateQuest(state) {
    const q = state.quest.q;
    ensureQuestShape(q);
    const d = [];
    if (!q.id?.trim()) d.push({lvl: 'err', path: 'quest', msg: 'Quest id 不能为空'});
    if (!q.phases.length) d.push({lvl: 'err', path: 'phases', msg: '至少需要一个 phase'});
    const ids = new Set();
    q.phases.forEach((p, pi) => {
        if (!p.id?.trim()) d.push({lvl: 'err', path: `phase:${pi}`, msg: `Phase ${pi + 1} 缺少 id`});
        if (ids.has(p.id)) d.push({lvl: 'err', path: `phase:${pi}`, msg: `Phase id 重复: ${p.id}`});
        ids.add(p.id);
        if (!['normal', 'parallel', 'choice'].includes(p.mode)) d.push({
            lvl: 'warn',
            path: `phase:${pi}`,
            msg: `Phase ${p.id} mode 非法`
        });
        if (p.mode !== 'normal') d.push({
            lvl: 'warn',
            path: `phase:${pi}`,
            msg: `Phase.mode(${p.mode}) 为历史模型字段，建议迁移至 transitions/choices`
        });
        if (!p.objectives.length) d.push({lvl: 'warn', path: `phase:${pi}`, msg: `Phase ${p.id} 没有 objective`});
        if (q.mode === 'COLLECTION' && p.collectionEntryConfig && !p.collectionEntryConfig.categoryId) d.push({
            lvl: 'warn',
            path: `phase:${pi}`,
            msg: `Collection phase ${p.id} 缺少 categoryId`
        });
        if (q.mode === 'COLLECTION' && p.collectionEntryConfig && !(p.collectionEntryConfig.completionTarget > 0)) d.push({
            lvl: 'warn',
            path: `phase:${pi}`,
            msg: `Collection phase ${p.id} completionTarget 应大于 0`
        });
        (p.parallelPhaseIds || []).forEach(id => {
            if (p.mode === 'parallel' && !q.phases.some(x => x.id === id)) d.push({
                lvl: 'err',
                path: `phase:${pi}`,
                msg: `parallelPhaseIds 引用了不存在的 phase: ${id}`
            });
        });
        (p.choicePhaseIds || []).forEach(id => {
            if (p.mode === 'choice' && !q.phases.some(x => x.id === id)) d.push({
                lvl: 'err',
                path: `phase:${pi}`,
                msg: `choicePhaseIds 引用了不存在的 phase: ${id}`
            });
        });
        (p.transitions || []).forEach((tr, ti) => {
            if (tr?.targetPhaseId && !q.phases.some(x => x.id === tr.targetPhaseId)) {
                d.push({
                    lvl: 'err',
                    path: `phase:${pi}`,
                    msg: `transition[${ti}] 引用了不存在的 phase: ${tr.targetPhaseId}`
                });
            }
            validateConditionNode(tr?.condition, `phase:${pi}:transition:${ti}:condition`, d);
        });
        (p.choices || []).forEach((ch, ci) => {
            if (ch?.targetPhaseId && !q.phases.some(x => x.id === ch.targetPhaseId)) {
                d.push({
                    lvl: 'err',
                    path: `phase:${pi}`,
                    msg: `choice[${ci}] 引用了不存在的 phase: ${ch.targetPhaseId}`
                });
            }
            validateConditionNode(ch?.visibleCondition, `phase:${pi}:choice:${ci}:visibleCondition`, d);
        });
        const objectiveIds = new Set();
        p.objectives.forEach((o, oi) => {
            if (!o.id?.trim()) d.push({lvl: 'err', path: `objective:${pi}:${oi}`, msg: 'Objective missing stable id'});
            if (objectiveIds.has(o.id)) d.push({lvl: 'err', path: `objective:${pi}:${oi}`, msg: `Duplicate objective id: ${o.id}`});
            objectiveIds.add(o.id);
            if (!o.type) d.push({lvl: 'err', path: `objective:${pi}:${oi}`, msg: 'Objective 缺少 type'});
            if (!OBJECTIVE_TYPES.has(o.type)) d.push({
                lvl: 'err',
                path: `objective:${pi}:${oi}`,
                msg: `Objective type 非法: ${o.type}`
            });
            if (!o.text) d.push({lvl: 'warn', path: `objective:${pi}:${oi}`, msg: 'Objective 缺少文案 key'});
            if (hasLegacyObjectiveShape(o)) d.push({
                lvl: 'warn',
                path: `objective:${pi}:${oi}`,
                msg: '检测到旧 objective 字段（entityType/itemId/targetType/...），建议迁移'
            });
            if (o.type === 'KILL' && !o.targetId) d.push({
                lvl: 'err',
                path: `objective:${pi}:${oi}`,
                msg: 'KILL objective 缺少 targetId(entityType)'
            });
            if (o.type === 'COLLECT' && !o.targetId && !o.itemTag) d.push({
                lvl: 'err',
                path: `objective:${pi}:${oi}`,
                msg: 'COLLECT objective 缺少 targetId/itemTag'
            });
            if (o.type === 'TALK' && !o.npcId && !o.targetId) d.push({
                lvl: 'warn',
                path: `objective:${pi}:${oi}`,
                msg: 'TALK objective 建议填写 npcId 或 targetId'
            });
            if (o.type === 'INTERACT' && !o.targetId) d.push({
                lvl: 'warn',
                path: `objective:${pi}:${oi}`,
                msg: 'INTERACT objective 缺少 targetId'
            });
            if (o.type === 'OFFER' && !o.targetId && !o.itemTag) d.push({
                lvl: 'warn',
                path: `objective:${pi}:${oi}`,
                msg: 'OFFER objective 缺少 targetId/itemTag'
            });
            if (o.type === 'DELIVER' && (!o.targetId || !o.npcId)) d.push({
                lvl: 'warn',
                path: `objective:${pi}:${oi}`,
                msg: 'DELIVER objective 建议填写 targetId 与 npcId'
            });
            if (o.type === 'REACH_LOCATION' && (o.x === null || o.y === null || o.z === null)) d.push({
                lvl: 'warn',
                path: `objective:${pi}:${oi}`,
                msg: 'REACH_LOCATION objective 建议填写 x/y/z'
            });
        });
        (p.rewards || []).forEach((r, ri) => {
            const type = r?.type || 'item';
            if (!REWARD_TYPES.has(type)) d.push({
                lvl: 'err',
                path: `phase:${pi}`,
                msg: `Phase reward[${ri}] type 非法: ${type}`
            });
            if (type === 'item' && !r.itemId) d.push({
                lvl: 'err',
                path: `phase:${pi}`,
                msg: `Phase reward[${ri}] item 缺少 itemId`
            });
            if ((type === 'var_set' || type === 'var_add' || type === 'var_subtract' || type === 'var_multiply') && !r.variable) d.push({
                lvl: 'warn',
                path: `phase:${pi}`,
                msg: `Phase reward[${ri}] ${type} 建议填写 variable`
            });
            if (type === 'command' && !r.command) d.push({
                lvl: 'warn',
                path: `phase:${pi}`,
                msg: `Phase reward[${ri}] command 为空`
            });
            if ((type === 'flag_set' || type === 'flag_clear') && !r.flag) d.push({
                lvl: 'warn',
                path: `phase:${pi}`,
                msg: `Phase reward[${ri}] ${type} flag 为空`
            });
        });
    });
    if (q.initialPhaseId && !q.phases.some(p => p.id === q.initialPhaseId)) {
        d.push({lvl: 'err', path: 'quest', msg: `initialPhaseId 引用了不存在的 phase: ${q.initialPhaseId}`});
    }
    if (q.unlockConditions) validateConditionNode(q.unlockConditions, 'quest:unlockConditions', d);
    const sp = new Set((q.visualConfig.splashes || []).map(x => x.eventType));
    if (!sp.has('QUEST_ACQUIRED')) d.push({lvl: 'warn', path: 'visual', msg: '未配置 QUEST_ACQUIRED splash'});
    if (!sp.has('QUEST_COMPLETED')) d.push({lvl: 'warn', path: 'visual', msg: '未配置 QUEST_COMPLETED splash'});
    if (q.mode === 'COLLECTION' && !q.collectionConfig) d.push({
        lvl: 'warn',
        path: 'quest',
        msg: 'Collection quest 缺少 collectionConfig'
    });
    if (q.collectionConfig) {
        const categoryIds = new Set();
        (q.collectionConfig.categories || []).forEach((cat, ci) => {
            if (!cat?.categoryId) return;
            if (categoryIds.has(cat.categoryId)) d.push({
                lvl: 'err',
                path: 'quest',
                msg: `categoryId 重复: ${cat.categoryId} (index ${ci})`
            });
            categoryIds.add(cat.categoryId);
            const nodeIds = new Set();
            (cat.rewardNodes || []).forEach((node, ni) => {
                if (!node?.nodeId) return;
                if (nodeIds.has(node.nodeId)) d.push({
                    lvl: 'err',
                    path: 'quest',
                    msg: `rewardNode nodeId 重复: ${node.nodeId} (category ${cat.categoryId}, index ${ni})`
                });
                nodeIds.add(node.nodeId);
            });
        });
        const topNodeIds = new Set();
        (q.collectionConfig.rewardNodes || []).forEach((node, ni) => {
            if (!node?.nodeId) return;
            if (topNodeIds.has(node.nodeId)) d.push({
                lvl: 'err',
                path: 'quest',
                msg: `top rewardNode nodeId 重复: ${node.nodeId} (index ${ni})`
            });
            topNodeIds.add(node.nodeId);
        });
    }
    (q.rewards || []).forEach((r, ri) => {
        const type = r?.type || 'item';
        if (!REWARD_TYPES.has(type)) d.push({lvl: 'err', path: 'quest', msg: `Quest reward[${ri}] type 非法: ${type}`});
        if (type === 'item' && !r.itemId) d.push({
            lvl: 'err',
            path: 'quest',
            msg: `Quest reward[${ri}] item 缺少 itemId`
        });
        if ((type === 'var_set' || type === 'var_add' || type === 'var_subtract' || type === 'var_multiply') && !r.variable) d.push({
            lvl: 'warn',
            path: 'quest',
            msg: `Quest reward[${ri}] ${type} 建议填写 variable`
        });
        if (type === 'command' && !r.command) d.push({
            lvl: 'warn',
            path: 'quest',
            msg: `Quest reward[${ri}] command 为空`
        });
        if ((type === 'flag_set' || type === 'flag_clear') && !r.flag) d.push({
            lvl: 'warn',
            path: 'quest',
            msg: `Quest reward[${ri}] ${type} flag 为空`
        });
    });
    state.quest.diag = d;
}
