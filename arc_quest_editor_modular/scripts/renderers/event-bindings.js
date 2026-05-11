import {handleClickPrelude, handleNonDeleteButtonAction} from './bindings/editor-click-actions.js';
import {handleDeleteButtonAction} from './bindings/editor-delete-actions.js';
import {bindEditorInputs} from './bindings/editor-inputs.js';

export function bindTreeSelection(leftEl, state, rerender) {
    leftEl.onclick = e => {
        const id = e.target.closest('[data-id]')?.dataset.id;
        if (!id) return;
        if (id === 'quest' || id === 'visual' || id === 'rewards' || id === 'raw') state.ui.sel = {t: id};
        else if (id.startsWith('phase-')) state.ui.sel = {t: 'phase', pi: +id.split('-')[1]};
        else {
            const [, pi, oi] = id.split('-');
            state.ui.sel = {t: 'obj', pi: +pi, oi: +oi};
        }
        rerender();
    };
}

export function bindEditorActions(midEl, state, rerender, setByPath) {
    bindEditorInputs(midEl, state, rerender, setByPath);

    midEl.onclick = e => {
        if (handleClickPrelude(e, midEl, state, rerender)) return;

        const btn = e.target.closest('button');
        if (!btn) return;

        if (handleNonDeleteButtonAction(btn, state)) {
            state.meta.dirty = true;
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
                state.meta.dirty = true;
                rerender();
            }
        }
    };
}
