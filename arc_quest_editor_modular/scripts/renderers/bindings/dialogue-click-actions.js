import {createDialogueNode, createConditionalSay, createDialogueChoice, createDialogueAction} from '../../core/factories.js';
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
        const skipRerender = appendCond.dataset.condSkipRerender === 'true';
        if (bindConditionEditorClicks(state, appendCond.dataset.condAppend, 'append', skipRerender)) {
            if (!skipRerender) state.dialogue.meta.dirty = true;
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

    const stripScrollBtn = e.target.closest('[data-strip-scroll]');
    if (stripScrollBtn) {
        const ds = stripScrollBtn.dataset;
        const [prefix, dir] = ds.stripScroll.split('-');
        const ni = state.dialogue.ui.sel.ni;
        const strip = document.querySelector(`[data-strip-id="${prefix}-${ni}"]`);
        if (strip) {
            strip.scrollTo({left: dir === 'left' ? 0 : strip.scrollWidth, behavior: 'smooth'});
        }
        return true;
    }

    const stripCard = e.target.closest('.strip-card');
    if (stripCard) {
        const ds = stripCard.dataset;
        if (ds.jumpSayif !== undefined) {
        if (ds.jumpSayif.includes('::default')) {
            const ni = ds.jumpSayif.split('::')[0];
            state.dialogue.ui.sel = {t: 'sayIf', ni: +ni, key: ':default'};
        } else {
            const [ni, key] = ds.jumpSayif.split(':');
            state.dialogue.ui.sel = {t: 'sayIf', ni: +ni, key};
        }
            state.dialogue.meta.dirty = true;
            rerender();
        } else if (ds.jumpChoice !== undefined) {
            const [ni, ci] = ds.jumpChoice.split(':');
            state.dialogue.ui.sel = {t: 'choice', ni: +ni, ci: +ci};
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
        const newNi = state.dialogue.q.nodes.length - 1;
        state.dialogue.ui.sel = {t: 'node', ni: newNi};
        return true;
    }

    if (id && id.startsWith('addCondTextBtn_')) {
        const nodeIdx = parseInt(id.replace('addCondTextBtn_', ''), 10);
        const node = state.dialogue.q.nodes[nodeIdx];
        if (!node) return true;
        let keyNum = Object.keys(node.conditionalTexts || {}).length + 1;
        let key = `conditional_${keyNum}`;
        while (node.conditionalTexts[key]) { keyNum++; key = `conditional_${keyNum}`; }
        node.conditionalTexts[key] = createConditionalSay();
        return true;
    }

    if (d.t !== undefined && d.ni !== undefined) {
        const ni = +d.ni;
        const t = d.t;
        if (t === 'node') { state.dialogue.ui.sel = {t: 'node', ni}; return true; }
        if (t === 'config') { state.dialogue.ui.sel = {t: 'config'}; return true; }
    }

    if (d.gotoConfig !== undefined) {
        state.dialogue.ui.sel = {t: 'config'};
        return true;
    }

    if (d.gotoNode !== undefined) {
        state.dialogue.ui.sel = {t: 'node', ni: +d.gotoNode};
        return true;
    }

    if (d.navSayif !== undefined) {
        if (d.navSayif.includes('::default')) {
            const ni = d.navSayif.split('::')[0];
            state.dialogue.ui.sel = {t: 'sayIf', ni: +ni, key: ':default'};
        } else {
            const [ni, key] = d.navSayif.split(':');
            state.dialogue.ui.sel = {t: 'sayIf', ni: +ni, key};
        }
        return true;
    }

    if (d.jumpSayif !== undefined) {
        if (d.jumpSayif.includes('::default')) {
            const ni = d.jumpSayif.split('::')[0];
            state.dialogue.ui.sel = {t: 'sayIf', ni: +ni, key: ':default'};
        } else {
            const [ni, key] = d.jumpSayif.split(':');
            state.dialogue.ui.sel = {t: 'sayIf', ni: +ni, key};
        }
        return true;
    }

    if (d.navChoice !== undefined) {
        const [ni, ci] = d.navChoice.split(':');
        state.dialogue.ui.sel = {t: 'choice', ni: +ni, ci: +ci};
        return true;
    }

    if (d.jumpChoice !== undefined) {
        const [ni, ci] = d.jumpChoice.split(':');
        state.dialogue.ui.sel = {t: 'choice', ni: +ni, ci: +ci};
        return true;
    }

    if (d.editSayif !== undefined) {
        if (d.editSayif.includes('::default')) {
            const ni = d.editSayif.split('::')[0];
            state.dialogue.ui.sel = {t: 'sayIf', ni: +ni, key: ':default'};
        } else {
            const [ni, key] = d.editSayif.split(':');
            state.dialogue.ui.sel = {t: 'sayIf', ni: +ni, key};
        }
        return true;
    }

    if (d.editChoice !== undefined) {
        const [ni, ci] = d.editChoice.split(':');
        state.dialogue.ui.sel = {t: 'choice', ni: +ni, ci: +ci};
        return true;
    }

    if (d.toggleSayifFold !== undefined) {
        state.dialogue.ui.sayIfFold = !state.dialogue.ui.sayIfFold;
        return true;
    }

    if (d.toggleChoiceFold !== undefined) {
        state.dialogue.ui.choiceFold = !state.dialogue.ui.choiceFold;
        return true;
    }

    if (d.ddnode !== undefined) {
        const idx = +d.ddnode;
        state.dialogue.q.nodes.splice(idx, 1);
        state.dialogue.ui.sel = state.dialogue.q.nodes.length > 0
            ? {t: 'node', ni: Math.min(idx, state.dialogue.q.nodes.length - 1)}
            : {t: 'config'};
        return true;
    }

    if (d.ddcondtext !== undefined) {
        const [ni, key] = d.ddcondtext.split(':');
        const node = state.dialogue.q.nodes[+ni];
        if (node?.conditionalTexts) delete node.conditionalTexts[key];
        state.dialogue.ui.sel = {t: 'node', ni: +ni};
        return true;
    }

    if (d.ddcondtextCard !== undefined) {
        const [ni, key] = d.ddcondtextCard.split(':');
        const node = state.dialogue.q.nodes[+ni];
        if (node?.conditionalTexts) delete node.conditionalTexts[key];
        return true;
    }

    if (d.dchoiceDel !== undefined) {
        const [ni, ci] = d.dchoiceDel.split(':');
        const node = state.dialogue.q.nodes[+ni];
        if (node?.choices) node.choices.splice(+ci, 1);
        state.dialogue.ui.sel = {t: 'node', ni: +ni};
        return true;
    }

    if (d.dchoiceDelCard !== undefined) {
        const [ni, ci] = d.dchoiceDelCard.split(':');
        const node = state.dialogue.q.nodes[+ni];
        if (node?.choices) node.choices.splice(+ci, 1);
        return true;
    }

    if (d.choiceAdd !== undefined) {
        const nodeIdx = +d.choiceAdd;
        const node = state.dialogue.q.nodes[nodeIdx];
        if (!node) return true;
        node.choices = node.choices || [];
        node.choices.push(createDialogueChoice());
        return true;
    }

    if (d.actionAdd !== undefined) {
        const [nodeIdxStr, ciStr] = d.actionAdd.split(':');
        const nodeIdx = +nodeIdxStr;
        const ci = +ciStr;
        const node = state.dialogue.q.nodes[nodeIdx];
        if (!node?.choices?.[ci]) return true;
        node.choices[ci].actions = node.choices[ci].actions || [];
        node.choices[ci].actions.push(createDialogueAction());
        return true;
    }

    if (d.dactionDel !== undefined) {
        const parts = d.dactionDel.split('.ch.');
        const nodeIdx = +parts[0];
        const restParts = parts[1].split('.actions.');
        const ci = +restParts[0];
        const ai = +restParts[1];
        const node = state.dialogue.q.nodes[nodeIdx];
        if (!node?.choices?.[ci]) return true;
        node.choices[ci].actions.splice(ai, 1);
        return true;
    }

    return false;
}
