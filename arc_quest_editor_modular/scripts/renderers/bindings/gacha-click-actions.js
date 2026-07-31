import {createGachaItemSkeleton, createTradeOfferSkeleton} from '../../core/factories.js';

export function handleGachaClickPrelude(e, midEl, state, rerender) {
    const nav = e.target.closest('[data-gacha-nav]');
    if (nav) {
        const t = nav.dataset.gachaNav;
        if (t === 'overview') state.gacha.ui.sel = {t: 'overview'};
        else if (t === 'item' && nav.dataset.gachaPi !== undefined) {
            state.gacha.ui.sel = {t: 'item', pi: +nav.dataset.gachaPi, ii: +nav.dataset.gachaIi};
        }
        rerender();
        return true;
    }
    return false;
}

export function handleGachaNonDeleteButtonAction(btn, state) {
    const d = btn.dataset;
    const id = btn.id;

    if (id === 'addGachaItemBtn') {
        if (!state.gacha.q.pools || state.gacha.q.pools.length === 0) {
            state.gacha.q.pools = [{poolId: 'default', items: []}];
        }
        const pool = state.gacha.q.pools[0];
        pool.items = pool.items || [];
        pool.items.push(createGachaItemSkeleton());
        const ii = pool.items.length - 1;
        state.gacha.ui.sel = {t: 'item', pi: 0, ii};
        return true;
    }

    if (id === 'addGachaCostBtn') {
        state.gacha.q.drawCosts ||= [];
        state.gacha.q.drawCosts.push(createTradeOfferSkeleton());
        return true;
    }

    if (d.dgachacost !== undefined) {
        state.gacha.q.drawCosts?.splice(+d.dgachacost, 1);
        return true;
    }

    if (id === 'addGachaRarityBtn') {
        state.gacha.q.rarities = state.gacha.q.rarities || [];
        state.gacha.q.rarities.push({rarity: 'RARE', color: 0xFFFFFF, drawSuccessSound: ''});
        return true;
    }

    if (id === 'addGachaPityBtn') {
        state.gacha.q.pity = {threshold: 10, targetRarity: 'LEGENDARY', guaranteedItemId: '', resetOnEarlyTrigger: true, resetCooldownType: 'NONE', resetCooldownValue: 0, resetCondition: null, resetOnTrigger: true};
        return true;
    }

    if (d.dgachararity !== undefined) {
        const idx = +d.dgachararity;
        if (state.gacha.q.rarities) state.gacha.q.rarities.splice(idx, 1);
        return true;
    }

    if (d.dgachaitem !== undefined) {
        const [pi, ii] = d.dgachaitem.split('.').map(Number);
        if (state.gacha.q.pools && state.gacha.q.pools[pi] && state.gacha.q.pools[pi].items) {
            state.gacha.q.pools[pi].items.splice(ii, 1);
        }
        state.gacha.ui.sel = {t: 'overview'};
        return true;
    }

    return false;
}
