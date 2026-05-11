export function createNodeReward() {
    return {type: 'item', itemId: 'minecraft:iron_ingot', count: 1};
}

export function createTopRewardNode(index) {
    return {
        nodeId: `arc_quest:quest_reward_node_${index + 1}`,
        scope: 'QUEST',
        grantMode: 'MANUAL',
        rewards: [],
        completionRules: [],
        scopeRefId: ''
    };
}

export function normalizeObjectiveByType(obj) {
    const base = {
        type: obj.type,
        id: obj.id || '',
        text: obj.text || '',
        textMode: obj.textMode || 'translatable',
        count: obj.count ?? 1,
        targetId: obj.targetId || '',
        hidden: !!obj.hidden,
        optional: !!obj.optional,
        npcId: obj.npcId || '',
        itemTag: obj.itemTag || '',
        x: obj.x ?? null,
        y: obj.y ?? null,
        z: obj.z ?? null,
        radius: obj.radius ?? null,
        extraData: obj.extraData || {}
    };
    if (obj.type === 'KILL') return {...base, targetId: obj.targetId || 'minecraft:zombie'};
    if (obj.type === 'COLLECT') return {...base, targetId: obj.targetId || 'minecraft:iron_ingot'};
    if (obj.type === 'TALK') return {...base, npcId: obj.npcId || obj.targetId || 'arc_quest:npc_guard'};
    if (obj.type === 'INTERACT') return {...base, targetId: obj.targetId || 'minecraft:crafting_table'};
    if (obj.type === 'OFFER') return {...base, targetId: obj.targetId || 'minecraft:iron_ingot'};
    if (obj.type === 'DELIVER') return {
        ...base,
        targetId: obj.targetId || 'minecraft:iron_ingot',
        npcId: obj.npcId || 'arc_quest:npc_guard'
    };
    if (obj.type === 'REACH_LOCATION') return {
        ...base,
        x: obj.x ?? 0,
        y: obj.y ?? 64,
        z: obj.z ?? 0,
        radius: obj.radius ?? 4
    };
    if (obj.type === 'CRAFT') return {...base, targetId: obj.targetId || 'minecraft:torch'};
    return {...base, targetId: obj.targetId || 'arc_quest:custom_target'};
}

export function normalizeRewardByType(reward) {
    const type = reward?.type || 'item';
    if (type === 'command') return {type, command: reward?.command || ''};
    if (type === 'flag_set' || type === 'flag_clear') return {type, flag: reward?.flag || ''};
    if (type === 'var_set' || type === 'var_add' || type === 'var_subtract' || type === 'var_multiply') {
        return {type, variable: reward?.variable || '', value: reward?.value ?? 0};
    }
    return {type: 'item', itemId: reward?.itemId || 'minecraft:iron_ingot', count: reward?.count ?? 1};
}

export function addChipValue(q, key, value) {
    const v = String(value || '').trim();
    if (!v) return;
    if (key === 'q.tags') {
        q.tags ||= [];
        if (!q.tags.includes(v)) q.tags.push(v);
    }
    if (key === 'q.flagsToSetOnAccept') {
        q.flagsToSetOnAccept ||= [];
        if (!q.flagsToSetOnAccept.includes(v)) q.flagsToSetOnAccept.push(v);
    }
    if (key === 'q.flagsToSetOnComplete') {
        q.flagsToSetOnComplete ||= [];
        if (!q.flagsToSetOnComplete.includes(v)) q.flagsToSetOnComplete.push(v);
    }
    if (key.startsWith('ph.') && (key.endsWith('.flagsToSetOnEnter') || key.endsWith('.flagsToSetOnComplete'))) {
        const [, phaseIndex, fieldName] = key.split('.');
        q.phases ||= [];
        q.phases[+phaseIndex] ||= {};
        q.phases[+phaseIndex][fieldName] ||= [];
        if (!q.phases[+phaseIndex][fieldName].includes(v)) q.phases[+phaseIndex][fieldName].push(v);
    }
}

export function removeChipValue(q, key, index) {
    const i = Number(index);
    if (!Number.isInteger(i) || i < 0) return;
    if (key === 'q.tags') q.tags?.splice(i, 1);
    if (key === 'q.flagsToSetOnAccept') q.flagsToSetOnAccept?.splice(i, 1);
    if (key === 'q.flagsToSetOnComplete') q.flagsToSetOnComplete?.splice(i, 1);
    if (key.startsWith('ph.') && (key.endsWith('.flagsToSetOnEnter') || key.endsWith('.flagsToSetOnComplete'))) {
        const [, phaseIndex, fieldName] = key.split('.');
        q.phases?.[+phaseIndex]?.[fieldName]?.splice(i, 1);
    }
}

export function markInputValidity(el, bind) {
    if (!el || !bind) return;
    const v = String(el.value || '').trim();
    let invalid = false;
    if (bind.endsWith('itemId')) invalid = !!v && !v.includes(':');
    if (bind.endsWith('entityType')) invalid = !!v && !v.includes(':');
    if (bind.endsWith('npcId')) invalid = !!v && !v.includes(':');
    if (bind.endsWith('dialogueId')) invalid = !!v && !v.includes(':');
    if (bind === 'q.iconTexture') invalid = !!v && !v.includes(':');
    el.classList.toggle('input-invalid', invalid);
}

export function fixCollectionReferences(q) {
    q.collectionConfig ||= {categories: [], rewardNodes: [], completionRules: []};
    const phaseIds = new Set((q.phases || []).map(p => p.id).filter(Boolean));
    const categoryIds = new Set((q.collectionConfig.categories || []).map(c => c.categoryId).filter(Boolean));
    const nodeIds = new Set([
        ...(q.collectionConfig.rewardNodes || []).map(n => n.nodeId),
        ...(q.collectionConfig.categories || []).flatMap(c => (c.rewardNodes || []).map(n => n.nodeId))
    ].filter(Boolean));
    const allowRef = new Set([...phaseIds, ...categoryIds, ...nodeIds]);

    (q.collectionConfig.completionRules || []).forEach(rule => {
        if (rule?.refId && !allowRef.has(rule.refId)) rule.refId = '';
    });

    (q.collectionConfig.rewardNodes || []).forEach(node => {
        if (node?.scopeRefId && !allowRef.has(node.scopeRefId)) node.scopeRefId = '';
        (node.completionRules || []).forEach(rule => {
            if (rule?.refId && !allowRef.has(rule.refId)) rule.refId = '';
        });
    });

    (q.collectionConfig.categories || []).forEach(cat => {
        (cat.completionRules || []).forEach(rule => {
            if (rule?.refId && !allowRef.has(rule.refId)) rule.refId = '';
        });
        (cat.rewardNodes || []).forEach(node => {
            if (node?.scopeRefId && !allowRef.has(node.scopeRefId)) node.scopeRefId = '';
            (node.completionRules || []).forEach(rule => {
                if (rule?.refId && !allowRef.has(rule.refId)) rule.refId = '';
            });
        });
    });
}

export function fixCollectionRuleDefaults(q) {
    q.collectionConfig ||= {categories: [], rewardNodes: [], completionRules: []};
    const fixRule = rule => {
        if (!rule) return;
        rule.type ||= 'completed_entry_count';
        if (rule.value === undefined || rule.value === null || Number.isNaN(Number(rule.value))) rule.value = 1;
        if ((rule.type === 'and' || rule.type === 'or' || rule.type === 'not')) {
            rule.left ||= {type: 'all_entries_complete', value: 1};
            if (rule.type !== 'not') rule.right ||= {type: 'all_entries_complete', value: 1};
        }
    };
    (q.collectionConfig.completionRules || []).forEach(fixRule);
    (q.collectionConfig.rewardNodes || []).forEach(node => (node.completionRules || []).forEach(fixRule));
    (q.collectionConfig.categories || []).forEach(cat => {
        (cat.completionRules || []).forEach(fixRule);
        (cat.rewardNodes || []).forEach(node => (node.completionRules || []).forEach(fixRule));
    });
}
