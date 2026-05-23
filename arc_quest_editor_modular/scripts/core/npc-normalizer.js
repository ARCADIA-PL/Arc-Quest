export function normalizeImportedNpc(input) {
    return {
        entityType: input.entityType || '',
        bindings: (input.bindings || []).map((b, i) => ({
            bindingId: b.bindingId || `binding_${i + 1}`,
            dialogueId: b.dialogueId || '',
            dialogueIdFromNbt: b.dialogueIdFromNbt || '',
            condition: b.condition || null,
            priority: b.priority ?? 0
        })),
        cancelVanillaInteract: input.cancelVanillaInteract !== false,
        dialogueDistance: typeof input.dialogueDistance === 'number' ? input.dialogueDistance : 8.0,
        shouldLookAtPlayer: input.shouldLookAtPlayer !== false,
        shouldStopMoving: input.shouldStopMoving !== false,
        interactCondition: input.interactCondition || null,
        onDialogueStartCommands: input.onDialogueStartCommands || [],
        onDialogueEndCommands: input.onDialogueEndCommands || []
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
