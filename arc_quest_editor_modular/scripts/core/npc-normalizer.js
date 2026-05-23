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

export function exportNpcToDatapack(npc) {
    const out = {
        entityType: npc.entityType || '',
        bindings: (npc.bindings || []).map(b => {
            const bo = {
                bindingId: b.bindingId || '',
                dialogueId: b.dialogueId || ''
            };
            if (b.dialogueIdFromNbt) bo.dialogueIdFromNbt = b.dialogueIdFromNbt;
            if (b.condition) bo.condition = b.condition;
            if (b.priority > 0) bo.priority = b.priority;
            return bo;
        })
    };
    if (npc.cancelVanillaInteract === false) out.cancelVanillaInteract = false;
    if (npc.dialogueDistance !== 8.0) out.dialogueDistance = npc.dialogueDistance;
    if (npc.shouldLookAtPlayer === false) out.shouldLookAtPlayer = false;
    if (npc.shouldStopMoving === false) out.shouldStopMoving = false;
    if (npc.interactCondition) out.interactCondition = npc.interactCondition;
    if (npc.onDialogueStartCommands?.length) out.onDialogueStartCommands = npc.onDialogueStartCommands;
    if (npc.onDialogueEndCommands?.length) out.onDialogueEndCommands = npc.onDialogueEndCommands;
    return out;
}
