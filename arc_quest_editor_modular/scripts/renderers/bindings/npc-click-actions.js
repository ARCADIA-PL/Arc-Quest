import {createNpcBinding} from '../../core/factories.js';
import {bindConditionEditorClicks} from '../../renderers/bindings/editor-click-actions-condition.js';

export function handleNpcClickPrelude(e, midEl, state, rerender) {
    const nav = e.target.closest('[data-npc-nav]');
    if (nav) {
        const t = nav.dataset.npcNav;
        if (t === 'overview') state.npc.ui.sel = {t: 'overview'};
        else if (t === 'binding' && nav.dataset.npcBi !== undefined) {
            state.npc.ui.sel = {t: 'binding', bi: +nav.dataset.npcBi};
        }
        else if (t === 'commands') state.npc.ui.sel = {t: 'commands'};
        rerender();
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
        const newBi = state.npc.q.bindings.length - 1;
        state.npc.ui.sel = {t: 'binding', bi: newBi};
        return true;
    }

    if (d.dnpcbind !== undefined) {
        state.npc.q.bindings.splice(+d.dnpcbind, 1);
        state.npc.ui.sel = {t: 'overview'};
        return true;
    }

    if (d.toggleNpcCond !== undefined) {
        state.npc.ui.condFold = !state.npc.ui.condFold;
        return true;
    }

    if (d.toggleNpcCmd !== undefined) {
        state.npc.ui.cmdFold = !state.npc.ui.cmdFold;
        return true;
    }

    return false;
}
