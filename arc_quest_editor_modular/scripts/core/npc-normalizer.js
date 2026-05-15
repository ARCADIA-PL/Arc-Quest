export function normalizeImportedNpc(input) {
    const npc = {...input};
    return {
        entityType: npc.entityType || '',
        bindings: (npc.bindings || []).map((b, i) => ({
            bindingId: b.bindingId || `binding_${i + 1}`,
            dialogueId: b.dialogueId || '',
            dialogueIdFromNbt: b.dialogueIdFromNbt || '',
            condition: b.condition || null,
            priority: b.priority ?? 0
        })),
        cancelVanillaInteract: npc.cancelVanillaInteract !== false,
        dialogueDistance: typeof npc.dialogueDistance === 'number' ? npc.dialogueDistance : 8.0,
        shouldLookAtPlayer: npc.shouldLookAtPlayer !== false,
        shouldStopMoving: npc.shouldStopMoving !== false,
        interactCondition: npc.interactCondition || null,
        onDialogueStartCommands: npc.onDialogueStartCommands || [],
        onDialogueEndCommands: npc.onDialogueEndCommands || []
    };
}

function cleanEmptyFields(obj) {
    if (obj == null) return;
    if (Array.isArray(obj)) {
        for (const item of obj) cleanEmptyFields(item);
        return;
    }
    if (typeof obj !== 'object') return;
    for (const key of Object.keys(obj)) {
        const v = obj[key];
        if (v === null || v === undefined || v === '' ||
            (Array.isArray(v) && v.length === 0) ||
            (typeof v === 'object' && !Array.isArray(v) && Object.keys(v).length === 0)) {
            delete obj[key];
        } else if (typeof v === 'object') {
            cleanEmptyFields(v);
        }
    }
}

export function exportNpcToDatapack(npc) {
    const exported = JSON.parse(JSON.stringify(npc));
    cleanEmptyFields(exported);
    return exported;
}
