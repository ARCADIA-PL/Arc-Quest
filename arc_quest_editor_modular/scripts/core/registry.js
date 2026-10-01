import {cloneDocument} from './json-document.js';

export function createRegistry() {
    return {
        quests: Object.create(null), dialogues: Object.create(null), npcs: Object.create(null),
        shops: Object.create(null), gachas: Object.create(null), guides: Object.create(null),
        npcBindings: {dialogue: Object.create(null), npc: Object.create(null)}
    };
}

export function initRegistry(state) {
    state.registry = createRegistry();
}

function storeEntry(registry, collection, id, value) {
    const entries = registry[collection] ||= Object.create(null);
    Object.defineProperty(entries, id, {value, enumerable: true, writable: true, configurable: true});
}

export function importToRegistry(state, json, type) {
    json = cloneDocument(json);
    switch (type) {
        case 'quest':
            if (json && json.id) {
                storeEntry(state.registry, 'quests', json.id, extractQuestSummary(json));
            }
            break;
        case 'dialogue':
            if (json && json.id) {
                storeEntry(state.registry, 'dialogues', json.id, json);
            }
            break;
        case 'npc':
            if (json && json.entityType) {
                storeEntry(state.registry, 'npcs', json.entityType, json);
            }
            break;
        case 'trade':
            if (json && json.shopId) {
                storeEntry(state.registry, 'shops', json.shopId, json);
            }
            break;
        case 'gacha':
            if (json && json.shopId) {
                storeEntry(state.registry, 'gachas', json.shopId, json);
            }
            break;
        case 'guide':
        case 'guideCategory':
            if (json && json.id) {
                storeEntry(state.registry, 'guides', json.id, json);
            }
            break;
    }
}

export function importToRegistryOnly(state, json, type) {
    importToRegistry(state, json, type);
}

export function clearRegistry(state, type) {
    if (type) {
        if (!Object.hasOwn(state.registry, type)) throw new Error(`未知注册表类型: ${type}`);
        state.registry[type] = Object.create(null);
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
        initialPhaseIds: Array.isArray(json.initialPhaseIds) ? json.initialPhaseIds : [],
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
