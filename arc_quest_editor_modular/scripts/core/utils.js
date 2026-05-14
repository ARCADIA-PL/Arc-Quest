export const qs = s => document.querySelector(s);
export const esc = s => String(s ?? '').replace(/[&<>"']/g, m => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
}[m]));
export const num = v => v === '' ? 0 : Number(v);
export const bool = v => v === true || v === 'true';
export const clr = v => /^#?[\da-fA-F]{6}$/.test(v || '') ? (v[0] === '#' ? v : '#' + v) : '#63c7ff';
export const splitList = v => String(v || '').split(',').map(x => x.trim()).filter(Boolean);
export const phaseModeColor = mode => mode === 'parallel' ? 'var(--parallel)' : mode === 'choice' ? 'var(--choice)' : 'var(--a)';
export const phaseModeBadge = mode => mode === 'parallel' ? '<span class="phase-parallel">parallel</span>' : mode === 'choice' ? '<span class="phase-choice">choice</span>' : '<span class="phase-normal">normal</span>';

export const slugId = value => String(value || '')
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9_]+/g, '_')
    .replace(/^_+|_+$/g, '') || 'unnamed';

export function uniqueId(existingIds, base) {
    const used = new Set((existingIds || []).filter(Boolean));
    if (!used.has(base)) return base;
    let i = 2;
    while (used.has(`${base}_${i}`)) i += 1;
    return `${base}_${i}`;
}

export const makeQuestTitleKey = questId => `arc_quest.quest.${slugId(questId)}.title`;
export const makeQuestDescKey = questId => `arc_quest.quest.${slugId(questId)}.description`;
export const makePhaseTitleKey = (questId, phaseId) => `arc_quest.phase.${slugId(questId)}.${slugId(phaseId)}.title`;
export const makePhaseDescKey = (questId, phaseId) => `arc_quest.phase.${slugId(questId)}.${slugId(phaseId)}.description`;
export const makeObjectiveTextKey = (questId, phaseId, objectiveId) => `arc_quest.objective.${slugId(questId)}.${slugId(phaseId)}.${slugId(objectiveId)}`;

function pushRef(refs, from, to, kind, path) {
    if (!to) return;
    refs.push({from, to, kind, path});
}

export function buildReferenceIndex(q) {
    const entities = [];
    const refs = [];

    entities.push({
        key: `quest:${q.id || 'quest'}`,
        type: 'quest',
        id: q.id || 'quest',
        path: 'quest',
        label: q.id || 'quest'
    });

    (q.phases || []).forEach((p, pi) => {
        const phaseKey = `phase:${p.id}`;
        entities.push({key: phaseKey, type: 'phase', id: p.id, path: `phase:${pi}`, label: p.id});
        if (q.initialPhaseId && p.id === q.initialPhaseId) {
            pushRef(refs, `quest:${q.id || 'quest'}`, phaseKey, 'initialPhaseId', 'quest');
        }
        (p.parallelPhaseIds || []).forEach(id => pushRef(refs, phaseKey, `phase:${id}`, 'parallelPhaseIds', `phase:${pi}`));
        (p.choicePhaseIds || []).forEach(id => pushRef(refs, phaseKey, `phase:${id}`, 'choicePhaseIds', `phase:${pi}`));
        (p.transitions || []).forEach((tr, ti) => pushRef(refs, phaseKey, `phase:${tr?.targetPhaseId || ''}`, `transition:${ti}`, `phase:${pi}`));
        const catId = p.collectionEntryConfig?.categoryId;
        if (catId) pushRef(refs, phaseKey, `category:${catId}`, 'collectionEntryConfig.categoryId', `phase:${pi}`);
    });

    (q.collectionConfig?.categories || []).forEach((cat, ci) => {
        const catKey = `category:${cat.categoryId}`;
        entities.push({key: catKey, type: 'category', id: cat.categoryId, path: 'quest', label: cat.categoryId});
        (cat.completionRules || []).forEach((rule, ri) => {
            if (rule?.refId) pushRef(refs, catKey, `category:${rule.refId}`, `categoryRule:${ri}.refId`, 'quest');
        });
        (cat.rewardNodes || []).forEach((node, ni) => {
            const nodeKey = `rewardNode:${node.nodeId}`;
            entities.push({key: nodeKey, type: 'rewardNode', id: node.nodeId, path: 'quest', label: node.nodeId});
            if (node.scopeRefId) {
                const to = (q.collectionConfig?.categories || []).some(c => c.categoryId === node.scopeRefId)
                    ? `category:${node.scopeRefId}`
                    : `phase:${node.scopeRefId}`;
                pushRef(refs, nodeKey, to, `scopeRefId(cat:${ci}/node:${ni})`, 'quest');
            }
            (node.completionRules || []).forEach((rule, ri) => {
                if (rule?.refId) pushRef(refs, nodeKey, `rewardNode:${rule.refId}`, `nodeRule:${ri}.refId`, 'quest');
            });
        });
    });

    (q.collectionConfig?.rewardNodes || []).forEach((node, ni) => {
        const nodeKey = `rewardNode:${node.nodeId}`;
        entities.push({key: nodeKey, type: 'rewardNode', id: node.nodeId, path: 'quest', label: node.nodeId});
        if (node.scopeRefId) pushRef(refs, nodeKey, `category:${node.scopeRefId}`, `topScopeRefId:${ni}`, 'quest');
        (node.completionRules || []).forEach((rule, ri) => {
            if (rule?.refId) pushRef(refs, nodeKey, `rewardNode:${rule.refId}`, `topNodeRule:${ri}.refId`, 'quest');
        });
    });

    if (q.initialPhaseId) pushRef(refs, `quest:${q.id || 'quest'}`, `phase:${q.initialPhaseId}`, 'initialPhaseId', 'quest');

    const entityMap = new Map(entities.filter(e => e.id).map(e => [e.key, e]));
    const incoming = new Map();
    const outgoing = new Map();
    refs.forEach(r => {
        if (!outgoing.has(r.from)) outgoing.set(r.from, []);
        outgoing.get(r.from).push(r);
        if (!incoming.has(r.to)) incoming.set(r.to, []);
        incoming.get(r.to).push(r);
    });

    const unresolved = refs.filter(r => !entityMap.has(r.to));
    return {entities, refs, entityMap, incoming, outgoing, unresolved};
}

export function resolveSelectionRefKey(state) {
    const s = state.quest.ui.sel;
    if (s.t === 'phase') {
        const id = state.quest.q.phases?.[s.pi]?.id;
        return id ? `phase:${id}` : '';
    }
    return `quest:${state.quest.q.id || 'quest'}`;
}

