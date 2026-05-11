import {
    uniqueId,
    makeQuestTitleKey,
    makeQuestDescKey,
    makePhaseTitleKey,
    makePhaseDescKey,
    makeObjectiveTextKey
} from '../../core/utils.js';

export function syncPhaseIdReferences(q, oldId, newId) {
    if (!oldId || !newId || oldId === newId) return;
    if (q.initialPhaseId === oldId) q.initialPhaseId = newId;
    (q.phases || []).forEach(phase => {
        phase.parallelPhaseIds = (phase.parallelPhaseIds || []).map(id => id === oldId ? newId : id);
        phase.choicePhaseIds = (phase.choicePhaseIds || []).map(id => id === oldId ? newId : id);
        phase.transitions = (phase.transitions || []).map(tr => ({
            ...tr,
            targetPhaseId: tr.targetPhaseId === oldId ? newId : tr.targetPhaseId
        }));
    });
}

export function removePhaseReferences(q, removedId) {
    if (!removedId) return;
    if (q.initialPhaseId === removedId) q.initialPhaseId = q.phases?.[0]?.id || '';
    (q.phases || []).forEach(phase => {
        phase.parallelPhaseIds = (phase.parallelPhaseIds || []).filter(id => id && id !== removedId);
        phase.choicePhaseIds = (phase.choicePhaseIds || []).filter(id => id && id !== removedId);
        phase.transitions = (phase.transitions || []).filter(tr => tr?.targetPhaseId && tr.targetPhaseId !== removedId);
    });
}

export function ensureUniquePhaseId(q, phaseIndex) {
    const phase = q.phases?.[phaseIndex];
    if (!phase) return;
    const others = (q.phases || []).filter((_, i) => i !== phaseIndex).map(p => p.id);
    phase.id = uniqueId(others, phase.id || `phase_${phaseIndex + 1}`);
}

export function ensureUniqueObjectiveId(q, phaseIndex, objectiveIndex) {
    const phase = q.phases?.[phaseIndex];
    const objective = phase?.objectives?.[objectiveIndex];
    if (!phase || !objective) return;
    const others = (phase.objectives || []).filter((_, i) => i !== objectiveIndex).map(o => o.id);
    objective.id = uniqueId(others, objective.id || `objective_${objectiveIndex + 1}`);
}

export function ensureUniqueCategoryId(q, categoryIndex) {
    const category = q.collectionConfig?.categories?.[categoryIndex];
    if (!category) return;
    const others = (q.collectionConfig?.categories || []).filter((_, i) => i !== categoryIndex).map(c => c.categoryId);
    category.categoryId = uniqueId(others, category.categoryId || `category_${categoryIndex + 1}`);
}

export function ensureUniqueCategoryNodeId(q, categoryIndex, nodeIndex) {
    const category = q.collectionConfig?.categories?.[categoryIndex];
    const node = category?.rewardNodes?.[nodeIndex];
    if (!category || !node) return;
    const others = (category.rewardNodes || []).filter((_, i) => i !== nodeIndex).map(n => n.nodeId);
    node.nodeId = uniqueId(others, node.nodeId || `reward_node_${nodeIndex + 1}`);
}

export function ensureUniqueTopNodeId(q, nodeIndex) {
    const node = q.collectionConfig?.rewardNodes?.[nodeIndex];
    if (!node) return;
    const others = (q.collectionConfig?.rewardNodes || []).filter((_, i) => i !== nodeIndex).map(n => n.nodeId);
    node.nodeId = uniqueId(others, node.nodeId || `quest_reward_node_${nodeIndex + 1}`);
}

export function autoGenerateQuestKeys(q) {
    if (q.titleMode !== 'literal') q.title = makeQuestTitleKey(q.id);
    if (q.descriptionMode !== 'literal') q.description = makeQuestDescKey(q.id);
}

export function autoGeneratePhaseKeys(q, phaseIndex) {
    const phase = q.phases?.[phaseIndex];
    if (!phase) return;
    if (phase.titleMode !== 'literal') phase.title = makePhaseTitleKey(q.id, phase.id);
    if (phase.descriptionMode !== 'literal') phase.description = makePhaseDescKey(q.id, phase.id);
    (phase.objectives || []).forEach(objective => {
        if (objective.textMode !== 'literal') objective.text = makeObjectiveTextKey(q.id, phase.id, objective.id);
    });
}

export function autoGenerateAllPhaseKeys(q) {
    (q.phases || []).forEach((_, index) => autoGeneratePhaseKeys(q, index));
}

export function applyAutoKeysByBind(q, bind) {
    if (bind === 'q.id' || bind === 'q.titleMode' || bind === 'q.descriptionMode') {
        autoGenerateQuestKeys(q);
        autoGenerateAllPhaseKeys(q);
        return;
    }
    if (bind.startsWith('ph.') && (bind.endsWith('.id') || bind.endsWith('.titleMode') || bind.endsWith('.descriptionMode'))) {
        const phaseIndex = Number(bind.split('.')[1]);
        autoGeneratePhaseKeys(q, phaseIndex);
        return;
    }
    if (bind.startsWith('ob.') && (bind.endsWith('.id') || bind.endsWith('.textMode'))) {
        const parts = bind.split('.');
        autoGeneratePhaseKeys(q, Number(parts[1]));
    }
}
