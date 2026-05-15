import {createNpcBinding} from '../../core/factories.js';
import {
    addChipValue,
    removeChipValue
} from '../../renderers/bindings/editor-helpers.js';
import {bindConditionEditorClicks} from '../../renderers/bindings/editor-click-actions-condition.js';

export function handleNpcClickPrelude(e, midEl, state, rerender) {
    const removeTarget = e.target.closest('[data-chip-remove]');
    if (removeTarget) {
        const [key, index] = String(removeTarget.dataset.chipRemove || '').split(':');
        const cmdKey = key === 'npcStartCmd' ? 'onDialogueStartCommands' : 'onDialogueEndCommands';
        removeChipValue(state.npc.q, cmdKey, index);
        state.npc.meta.dirty = true;
        rerender();
        return true;
    }

    const addTarget = e.target.closest('[data-chip-add]');
    if (addTarget) {
        const key = addTarget.dataset.chipAdd;
        const input = midEl.querySelector(`[data-chip-add-input="${key}"]`);
        if (input) {
            const cmdKey = key === 'npcStartCmd' ? 'onDialogueStartCommands' : 'onDialogueEndCommands';
            addChipValue(state.npc.q, cmdKey, input.value);
            input.value = '';
            state.npc.meta.dirty = true;
            rerender();
        }
        return true;
    }

    const appendCond = e.target.closest('[data-cond-append]');
    if (appendCond) {
        if (bindConditionEditorClicks(state, appendCond.dataset.condAppend, 'append')) {
            state.npc.meta.dirty = true;
            rerender();
        }
        return true;
    }

    const deleteCond = e.target.closest('[data-cond-delete]');
    if (deleteCond) {
        if (bindConditionEditorClicks(state, deleteCond.dataset.condDelete, 'delete')) {
            state.npc.meta.dirty = true;
            rerender();
        }
        return true;
    }

    return false;
}

export function handleNpcNonDeleteButtonAction(btn, state) {
    const d = btn.dataset;
    const id = btn.id;

    if (id === 'addNpcBindBtn') {
        state.npc.q.bindings.push(createNpcBinding());
        return true;
    }
    if (d.dnpcbind !== undefined) {
        state.npc.q.bindings.splice(+d.dnpcbind, 1);
        return true;
    }

    return false;
}
