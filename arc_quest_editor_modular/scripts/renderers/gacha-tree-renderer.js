import {esc} from '../core/utils.js';

export function renderGachaTree(state, leftEl) {
    const gacha = state.gacha.q;
    const sel = state.gacha.ui.sel;
    const allItems = (gacha.pools || []).flatMap((p, pi) => (p.items || []).map((it, ii) => ({...it, _pi: pi, _ii: ii})));

    const itemItems = allItems.map(it => {
        const isActive = sel.t === 'item' && sel.pi === it._pi && sel.ii === it._ii;
        const w = it.weight ?? 1;
        return `<div class="tree-item ${isActive ? 'active' : ''}" data-gacha-nav="item" data-gacha-pi="${it._pi}" data-gacha-ii="${it._ii}">
          <span>🎲 ${esc(it.itemId || '?')}</span>
          <span class="tree-item-hint">W:${w} ${it.rarity}</span>
        </div>`;
    }).join('');

    leftEl.innerHTML = `
      <div class="tree-list">
        <div class="tree-header">抽奖: ${esc(gacha.shopId || '未设置')}</div>
        <div class="tree-item ${sel.t === 'overview' ? 'active' : ''}" data-gacha-nav="overview">
          📋 概览
        </div>
        <div class="tree-section">
          <div class="tree-section-header">📦 物品池 (${allItems.length})</div>
          ${itemItems || '<div class="tree-item-hint">暂无物品</div>'}
        </div>
      </div>
      <div style="padding:8px">
        <button id="addGachaItemBtn" class="toolbar-btn small-btn" style="width:100%">＋ 添加物品</button>
      </div>
    `;
}

export function bindGachaTreeSelection(leftEl, state, rerender) {
    leftEl.onclick = e => {
        const nav = e.target.closest('[data-gacha-nav]');
        if (!nav) return;
        const t = nav.dataset.gachaNav;
        if (t === 'overview') state.gacha.ui.sel = {t: 'overview'};
        else if (t === 'item' && nav.dataset.gachaPi !== undefined) {
            state.gacha.ui.sel = {t: 'item', pi: +nav.dataset.gachaPi, ii: +nav.dataset.gachaIi};
        }
        rerender();
    };
}
