import {setConditionNodeField} from './quest-shape-core.js';

export function setNpcByPath(target, bind, value, inputType) {
    if (bind === 'npc.entityType') {
        target.entityType = value;
        return;
    }
    if (bind === 'npc.cancelVanillaInteract') {
        target.cancelVanillaInteract = value === 'true';
        return;
    }
    if (bind === 'npc.dialogueDistance') {
        target.dialogueDistance = Number(value) || 8.0;
        return;
    }
    if (bind === 'npc.shouldLookAtPlayer') {
        target.shouldLookAtPlayer = value === 'true';
        return;
    }
    if (bind === 'npc.shouldStopMoving') {
        target.shouldStopMoving = value === 'true';
        return;
    }

    if (bind.startsWith('npc.bind.')) {
        const parts = bind.split('.');
        const bi = Number(parts[2]);
        const field = parts[3];
        if (!target.bindings[bi]) return;
        if (field === 'dialogueId' || field === 'bindingId' || field === 'dialogueIdFromNbt') {
            target.bindings[bi][field] = value;
        } else if (field === 'priority') {
            target.bindings[bi].priority = Number(value) || 0;
        } else if (field === 'condition') {
            const condPath = parts.slice(4);
            if (condPath.length > 0) {
                setConditionNodeField(target.bindings[bi].condition || {}, condPath, value);
            } else {
                target.bindings[bi].condition = value;
            }
        }
        return;
    }

    if (bind.startsWith('npc.interactCond')) {
        const cond = target.interactCondition || (target.interactCondition = {condition: 'arc_quest:always'});
        const condPath = bind.split('.').slice(2);
        if (condPath.length > 0) {
            setConditionNodeField(cond, condPath, value);
        }
        return;
    }

    if (bind.startsWith('npc.startCmd')) {
        const idx = Number(bind.split('.')[2]);
        if (Number.isNaN(idx)) return;
        target.onDialogueStartCommands[idx] = value;
        return;
    }
    if (bind.startsWith('npc.endCmd')) {
        const idx = Number(bind.split('.')[2]);
        if (Number.isNaN(idx)) return;
        target.onDialogueEndCommands[idx] = value;
        return;
    }
}
