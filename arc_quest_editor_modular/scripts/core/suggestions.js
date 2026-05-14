export function getQuestSuggestions(registry) {
    if (!registry || !registry.quests) return [];
    return Object.keys(registry.quests);
}

export function getPhaseSuggestions(registry, questId) {
    if (!registry || !registry.quests || !questId) return [];
    const entry = registry.quests[questId];
    if (!entry || !entry.phases) return [];
    return entry.phases.map(p => p.id).filter(Boolean);
}

export function getFlagSuggestions(registry) {
    if (!registry || !registry.quests) return [];
    const flags = new Set();
    for (const entry of Object.values(registry.quests)) {
        for (const f of (entry.allFlags || [])) flags.add(f);
        for (const f of (entry.flagsToSetOnAccept || [])) flags.add(f);
        for (const f of (entry.flagsToSetOnComplete || [])) flags.add(f);
    }
    return [...flags];
}

export function getDialogueSuggestions(registry) {
    if (!registry || !registry.dialogues) return [];
    return Object.keys(registry.dialogues);
}
