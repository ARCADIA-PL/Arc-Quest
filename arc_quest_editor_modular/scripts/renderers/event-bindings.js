import {handleClickPrelude, handleNonDeleteButtonAction} from './bindings/editor-click-actions.js';
import {handleDeleteButtonAction} from './bindings/editor-delete-actions.js';
import {handleNpcClickPrelude, handleNpcNonDeleteButtonAction} from './bindings/npc-click-actions.js';
import {bindEditorInputs} from './bindings/editor-inputs.js';
import {getPhaseSuggestions} from '../core/suggestions.js';

export function bindTreeSelection(leftEl, state, rerender) {
    leftEl.onclick = e => {
        const id = e.target.closest('[data-id]')?.dataset.id;
        if (!id) return;
        if (id === 'quest' || id === 'visual' || id === 'rewards' || id === 'raw') state.quest.ui.sel = {t: id};
        else if (id.startsWith('phase-')) state.quest.ui.sel = {t: 'phase', pi: +id.split('-')[1]};
        else {
            const [, pi, oi] = id.split('-');
            state.quest.ui.sel = {t: 'obj', pi: +pi, oi: +oi};
        }
        rerender();
    };
}

function bindPhaseDatalistLinkage(midEl, state) {
    midEl.addEventListener('input', e => {
        const target = e.target;
        const bind = target.dataset.b;
        if (!bind) return;
        if (!bind.endsWith('.questId')) return;

        const bindBase = bind.replace(/\.questId$/, '');
        const datalistId = `${bindBase}-phase-list`;
        const phaseList = midEl.querySelector(`[id="${datalistId}"]`);
        if (!phaseList) return;

        const questId = target.value;
        const phaseIds = getPhaseSuggestions(state.registry, questId);
        phaseList.innerHTML = phaseIds
            .map(id => `<option value="${id}"></option>`)
            .join('');
    });
}

export function bindEditorActions(midEl, state, rerender, setByPath) {
    bindEditorInputs(midEl, state, rerender, setByPath);
    bindPhaseDatalistLinkage(midEl, state);

    midEl.onclick = e => {
        if (handleClickPrelude(e, midEl, state, rerender)) return;

        const btn = e.target.closest('button');
        if (!btn) return;

        if (handleNonDeleteButtonAction(btn, state)) {
            state.quest.meta.dirty = true;
            rerender();
            return;
        }

        const deleteResult = handleDeleteButtonAction(btn, state);
        if (deleteResult.handled) {
            if (deleteResult.message) {
                alert(deleteResult.message);
                return;
            }
            if (deleteResult.mutate) {
                deleteResult.apply();
                state.quest.meta.dirty = true;
                rerender();
            }
        }
    };
}

export function bindNpcEditorActions(midEl, state, rerender, setNpcByPath) {
    bindEditorInputs(midEl, state.npc, rerender, setNpcByPath);

    midEl.onclick = e => {
        if (handleNpcClickPrelude(e, midEl, state, rerender)) return;

        const btn = e.target.closest('button');
        if (!btn) return;

        if (handleNpcNonDeleteButtonAction(btn, state)) {
            state.npc.meta.dirty = true;
            rerender();
            return;
        }

        if (handleNonDeleteButtonAction(btn, state)) {
            state.npc.meta.dirty = true;
            rerender();
            return;
        }

        const deleteResult = handleDeleteButtonAction(btn, state);
        if (deleteResult.handled) {
            if (deleteResult.message) return;
            if (deleteResult.mutate) {
                deleteResult.apply();
                state.npc.meta.dirty = true;
                rerender();
            }
        }
    };
}
