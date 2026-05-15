import {createDialogueNode, createConditionalSay} from '../../core/factories.js';
import {addChipValue, removeChipValue} from './editor-helpers.js';
import {bindConditionEditorClicks} from './editor-click-actions-condition.js';

export function handleDialogueClickPrelude(e, midEl, state, rerender) {
    const removeTarget = e.target.closest('[data-chip-remove]');
    if (removeTarget) {
        const [key, index] = String(removeTarget.dataset.chipRemove || '').split(':');
        removeChipValue(state.dialogue.q, key, parseInt(index, 10));
        state.dialogue.meta.dirty = true;
        rerender();
        return true;
    }

    const addTarget = e.target.closest('[data-chip-add]');
    if (addTarget) {
        const key = addTarget.dataset.chipAdd;
        const input = midEl.querySelector(`[data-chip-add-input="${key}"]`);
        if (input) {
            addChipValue(state.dialogue.q, key, input.value);
            input.value = '';
            state.dialogue.meta.dirty = true;
            rerender();
        }
        return true;
    }

    const appendCond = e.target.closest('[data-cond-append]');
    if (appendCond) {
        if (bindConditionEditorClicks(state, appendCond.dataset.condAppend, 'append')) {
            state.dialogue.meta.dirty = true;
            rerender();
        }
        return true;
    }

    const deleteCond = e.target.closest('[data-cond-delete]');
    if (deleteCond) {
        if (bindConditionEditorClicks(state, deleteCond.dataset.condDelete, 'delete')) {
            state.dialogue.meta.dirty = true;
            rerender();
        }
        return true;
    }

    return false;
}

export function handleDialogueNonDeleteButtonAction(btn, state) {
    const d = btn.dataset;
    const id = btn.id;

    if (id === 'addDialogueNodeBtn') {
        const newNode = createDialogueNode();
        newNode.nodeId = 'node_' + (state.dialogue.q.nodes.length + 1);
        state.dialogue.q.nodes.push(newNode);
        if (state.dialogue.q.nodes.length === 1) {
            state.dialogue.q.startNodeId = newNode.nodeId;
        }
        state.dialogue.ui.selNodeId = newNode.nodeId;
        return true;
    }

    if (id && id.startsWith('addCondTextBtn_')) {
        const nodeIdx = parseInt(id.replace('addCondTextBtn_', ''), 10);
        const node = state.dialogue.q.nodes[nodeIdx];
        if (!node) return true;
        let keyNum = Object.keys(node.conditionalTexts || {}).length + 1;
        let key = `conditional_${keyNum}`;
        while (node.conditionalTexts[key]) {
            keyNum++;
            key = `conditional_${keyNum}`;
        }
        node.conditionalTexts[key] = createConditionalSay();
        return true;
    }

    if (d.ddnode !== undefined) {
        const idx = +d.ddnode;
        const removed = state.dialogue.q.nodes.splice(idx, 1)[0];
        if (removed && removed.nodeId === state.dialogue.ui.selNodeId) {
            state.dialogue.ui.selNodeId = state.dialogue.q.nodes[0]?.nodeId || '';
        }
        return true;
    }

    if (d.ddcondtext !== undefined) {
        const [ni, key] = d.ddcondtext.split(':');
        const node = state.dialogue.q.nodes[+ni];
        if (node && node.conditionalTexts) {
            delete node.conditionalTexts[key];
        }
        return true;
    }

    if (d.dnodeId !== undefined) {
        state.dialogue.ui.selNodeId = d.dnodeId;
        return true;
    }

    return false;
}
