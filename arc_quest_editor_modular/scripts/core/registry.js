export function initRegistry(state) {
    state.registry = {
        quests: {},
        dialogues: {},
        npcs: {},
        npcBindings: {
            dialogue: {},
            npc: {}
        }
    };
}

export function importToRegistry(state, json, type) {
    switch (type) {
        case 'quest':
            if (json && json.id) {
                state.registry.quests[json.id] = extractQuestSummary(json);
            }
            break;
        case 'dialogue':
            if (json && json.id) {
                state.registry.dialogues[json.id] = json;
            }
            break;
        case 'npc':
            if (json && json.entityType) {
                state.registry.npcs[json.entityType] = json;
            }
            break;
        case 'trade':
            if (json && json.shopId) {
                if (!state.registry.shops) state.registry.shops = {};
                state.registry.shops[json.shopId] = json;
            }
            break;
        case 'gacha':
            if (json && json.shopId) {
                if (!state.registry.gachas) state.registry.gachas = {};
                state.registry.gachas[json.shopId] = json;
            }
            break;
        case 'guide':
        case 'guideCategory':
            if (json && json.id) {
                if (!state.registry.guides) state.registry.guides = {};
                state.registry.guides[json.id] = json;
            }
            break;
    }
}

export function importToRegistryOnly(state, json, type) {
    importToRegistry(state, json, type);
}

export function clearRegistry(state, type) {
    if (type) {
        state.registry[type] = {};
    } else {
        initRegistry(state);
    }
}

function extractQuestSummary(json) {
    const flags = new Set();
    for (const phase of (json.phases || [])) {
        for (const f of (phase.flagsToSetOnEnter || [])) flags.add(f);
        for (const f of (phase.flagsToSetOnComplete || [])) flags.add(f);
    }
    for (const f of (json.flagsToSetOnAccept || [])) flags.add(f);
    for (const f of (json.flagsToSetOnComplete || [])) flags.add(f);

    return {
        id: json.id,
        category: json.category || '',
        mode: json.mode || 'PROGRESSION',
        repeatable: !!json.repeatable,
        initialPhaseId: json.initialPhaseId || '',
        completionPolicy: json.completionPolicy || 'ALL',
        phases: (json.phases || []).map(p => ({
            id: p.phaseId || p.id || '',
            mode: p.mode || 'normal',
            flagsToSetOnEnter: p.flagsToSetOnEnter || [],
            flagsToSetOnComplete: p.flagsToSetOnComplete || [],
        })),
        flagsToSetOnAccept: json.flagsToSetOnAccept || [],
        flagsToSetOnComplete: json.flagsToSetOnComplete || [],
        allFlags: [...flags],
    };
}
