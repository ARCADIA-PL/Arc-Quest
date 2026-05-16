import {esc} from '../core/utils.js';

export function renderTradeTree(state, leftEl) {
    const trade = state.trade.q;
    const sel = state.trade.ui.sel;
    const entries = Object.entries(trade.entries || {});

    const entryItems = entries.map(([key, e]) => {
        const isActive = sel.t === 'entry' && sel.ei === key;
        const costCount = (e.costs || []).length;
        const rewardCount = (e.rewards || []).length;
        return `<div class="tree-item ${isActive ? 'active' : ''}" data-trade-nav="entry" data-trade-ei="${key}">
          <span>📦 ${esc(e.entryId || key)}</span>
          <span class="tree-item-hint">C:${costCount} R:${rewardCount}</span>
        </div>`;
    }).join('');

    leftEl.innerHTML = `
      <div class="tree-list">
        <div class="tree-header">商店: ${esc(trade.shopId || '未设置')}</div>
        <div class="tree-item ${sel.t === 'overview' ? 'active' : ''}" data-trade-nav="overview">
          📋 概览
        </div>
        <div class="tree-section">
          <div class="tree-section-header">📦 条目 (${entries.length})</div>
          ${entryItems || '<div class="tree-item-hint">暂无条目</div>'}
        </div>
      </div>
      <div style="padding:8px">
        <button id="addTradeEntryBtn" class="toolbar-btn small-btn" style="width:100%">＋ 添加条目</button>
      </div>
    `;
}

export function bindTradeTreeSelection(leftEl, state, rerender) {
    leftEl.onclick = e => {
        const nav = e.target.closest('[data-trade-nav]');
        if (!nav) return;
        const t = nav.dataset.tradeNav;
        if (t === 'overview') state.trade.ui.sel = {t: 'overview'};
        else if (t === 'entry' && nav.dataset.tradeEi !== undefined) {
            state.trade.ui.sel = {t: 'entry', ei: nav.dataset.tradeEi};
        }
        rerender();
    };
}
