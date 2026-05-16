import {createTradeEntrySkeleton, createTradeOfferSkeleton} from '../../core/factories.js';
import {bindConditionEditorClicks} from '../../renderers/bindings/editor-click-actions-condition.js';

export function handleTradeClickPrelude(e, midEl, state, rerender) {
    const nav = e.target.closest('[data-trade-nav]');
    if (nav) {
        const t = nav.dataset.tradeNav;
        if (t === 'overview') state.trade.ui.sel = {t: 'overview'};
        else if (t === 'entry' && nav.dataset.tradeEi !== undefined) {
            state.trade.ui.sel = {t: 'entry', ei: nav.dataset.tradeEi};
        }
        rerender();
        return true;
    }

    const appendCond = e.target.closest('[data-cond-append]');
    if (appendCond) {
        if (bindConditionEditorClicks(state, appendCond.dataset.condAppend, 'append')) {
            state.trade.meta.dirty = true;
            rerender();
        }
        return true;
    }

    const deleteCond = e.target.closest('[data-cond-delete]');
    if (deleteCond) {
        if (bindConditionEditorClicks(state, deleteCond.dataset.condDelete, 'delete')) {
            state.trade.meta.dirty = true;
            rerender();
        }
        return true;
    }

    return false;
}

export function handleTradeNonDeleteButtonAction(btn, state) {
    const d = btn.dataset;
    const id = btn.id;

    if (id === 'addTradeEntryBtn') {
        let keyNum = Object.keys(state.trade.q.entries || {}).length + 1;
        let key = `entry_${keyNum}`;
        while (state.trade.q.entries[key]) { keyNum++; key = `entry_${keyNum}`; }
        state.trade.q.entries[key] = createTradeEntrySkeleton();
        state.trade.q.entries[key].entryId = key;
        state.trade.ui.sel = {t: 'entry', ei: key};
        return true;
    }

    if (id === 'addTradeCatBtn') {
        state.trade.q.categories = state.trade.q.categories || [];
        state.trade.q.categories.push({categoryId: '', displayName: {mode: 'literal', value: '', args: []}, sortOrder: state.trade.q.categories.length, formatting: ''});
        return true;
    }

    if (d.dtradecat !== undefined) {
        const idx = +d.dtradecat;
        if (state.trade.q.categories) state.trade.q.categories.splice(idx, 1);
        return true;
    }

    if (d.dtradeEntry !== undefined) {
        delete state.trade.q.entries[d.dtradeEntry];
        state.trade.ui.sel = {t: 'overview'};
        return true;
    }

    if (d.tradeOfferAdd !== undefined) {
        const parts = d.tradeOfferAdd.split('.');
        const entryKey = parts[2];
        const listName = parts[3];
        const entry = state.trade.q.entries[entryKey];
        if (!entry) return true;
        entry[listName] = entry[listName] || [];
        entry[listName].push(createTradeOfferSkeleton());
        return true;
    }

    if (d.tradeOfferDel !== undefined) {
        const parts = d.tradeOfferDel.split('.');
        const entryKey = parts[2];
        const listName = parts[3];
        const oi = +parts[4];
        const entry = state.trade.q.entries[entryKey];
        if (!entry) return true;
        const list = entry[listName];
        if (list && oi >= 0 && oi < list.length) list.splice(oi, 1);
        return true;
    }

    return false;
}
