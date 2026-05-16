import {esc} from '../core/utils.js';
import {renderTextSpec} from './textspec-editor.js';
import {renderConditionTree} from './condition-editor.js';
import {renderColorInput} from './color-input.js';

function suggestInput(label, bind, value, suggestions, listId) {
    const opts = (suggestions || []).map(v => `<option value="${v}"></option>`).join('');
    return `<div class="f"><label>${label}</label><input data-b="${bind}" list="${listId}" value="${esc(value || '')}" placeholder="${label}"><datalist id="${listId}">${opts}</datalist></div>`;
}

export function renderTradeOverview(trade, registry, state) {
    const entryFold = !!(state?.trade?.ui?.entryFold);
    const entries = Object.entries(trade.entries || {});

    const entryCards = entries.map(([key, e]) => {
        const costCount = (e.costs || []).length;
        const rewardCount = (e.rewards || []).length;
        return `<div class="detail-card bind-card" data-trade-nav="entry" data-trade-ei="${key}">
          <div style="display:flex;align-items:center;gap:8px">
            <b class="small" style="flex:1">📦 ${esc(e.entryId || key)}</b>
            <span class="bind-priority-badge">${e.category || '—'}</span>
          </div>
          <div class="small" style="color:var(--text-mut);margin:4px 0">成本: ${costCount} · 奖励: ${rewardCount} · 排序: ${e.sortOrder ?? 0}</div>
          <div style="text-align:right">
            <button class="toolbar-btn small-btn" data-trade-nav="entry" data-trade-ei="${key}">编辑</button>
          </div>
        </div>`;
    }).join('');

    return `
    <div class="sec">
      <div class="breadcrumb">
        <span class="breadcrumb-current">⚙ 商店配置</span>
      </div>
      <h3 style="margin-top:4px">商店属性</h3>
      <div class="row">
        <div class="f"><label>商店 ID</label><input data-b="trade.shopId" value="${esc(trade.shopId || '')}" placeholder="namespace:shop_id"></div>
        <div class="f"><label>主题色</label>${renderColorInput('trade.themeColor', trade.themeColor ?? 0xE0C860)}</div>
      </div>
      ${renderTextSpec('trade.displayName', trade.displayName || {}, '显示名称')}
      ${renderTextSpec('trade.description', trade.description || {}, '描述', true)}
      <div class="row">
        <div class="f"><label>简化为简约模式</label><select data-b="trade.simpleMode"><option value="false" ${!trade.simpleMode ? 'selected' : ''}>完整模式</option><option value="true" ${trade.simpleMode ? 'selected' : ''}>简约模式</option></select></div>
        <div class="f"><label>打开音效</label><input data-b="trade.openSound" value="${esc(trade.openSound || '')}" placeholder="minecraft:block.chest.open"></div>
        <div class="f"><label>关闭音效</label><input data-b="trade.closeSound" value="${esc(trade.closeSound || '')}" placeholder="minecraft:block.chest.close"></div>
      </div>
    </div>

    <div class="sec">
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">
        <h3 style="margin:0">分类 (${(trade.categories || []).length})</h3>
        <button id="addTradeCatBtn" class="toolbar-btn small-btn">＋ 添加</button>
      </div>
      ${(trade.categories || []).length ? trade.categories.map((c, ci) => `
        <div class="card" style="margin-bottom:6px">
          <div class="row">
            <div class="f"><label>分类 ID</label><input data-b="trade.categories.${ci}.categoryId" value="${esc(c.categoryId || '')}" placeholder="weapons"></div>
            <div class="f"><label>排序</label><input type="number" data-b="trade.categories.${ci}.sortOrder" value="${c.sortOrder ?? 0}" min="0" max="100"></div>
            <div class="f"><label>颜色</label><input data-b="trade.categories.${ci}.formatting" value="${esc(c.formatting || '')}" placeholder="RED/GREEN/AQUA"></div>
          </div>
          ${renderTextSpec(`trade.categories.${ci}.displayName`, c.displayName || {}, '显示名称')}
          <div class="actions"><button data-dtradecat="${ci}">删除此分类</button></div>
        </div>
      `).join('') : '<div class="small" style="color:var(--text-mut)">暂无分类</div>'}
    </div>

    <div class="sec">
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">
        <h3 style="margin:0">条目列表 (${entries.length})</h3>
        <button id="addTradeEntryBtn" class="toolbar-btn small-btn">＋ 添加</button>
      </div>
      ${entryCards || '<div class="small" style="color:var(--text-mut)">暂无条目</div>'}
    </div>

    <div class="sec">
      <h3 style="margin:0">打开条件</h3>
      <div style="margin-top:8px">
        ${renderConditionTree('trade.openCond', trade.openCondition || {condition: 'arc_quest:always'}, registry, true)}
      </div>
    </div>
  `;
}
