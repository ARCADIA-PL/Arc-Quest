import {esc} from '../core/utils.js';
import {renderTextSpec} from './textspec-editor.js';
import {renderConditionTree} from './condition-editor.js';

const OFFER_TYPES = [
    {v: 'item', l: '物品'},
    {v: 'command', l: '命令'},
    {v: 'effect', l: '效果'},
    {v: 'flag', l: 'Flag'},
    {v: 'composite', l: '组合'}
];

export function renderTradeEntryEditor(trade, entryKey, registry) {
    const e = trade.entries[entryKey];
    if (!e) return renderTradeEntryFallback();

    const prefix = `trade.entries.${entryKey}`;
    const costs = e.costs || [];
    const rewards = e.rewards || [];

    const costsHtml = renderOfferList(costs, `${prefix}.costs`);
    const rewardsHtml = renderOfferList(rewards, `${prefix}.rewards`);

    return `
    <div class="sec">
      <div class="card-strip-bar">
        <button data-trade-nav="overview" class="toolbar-btn small-btn" style="font-weight:700;color:var(--accent)">← 返回</button>
        <span class="breadcrumb">
          <span data-trade-nav="overview" class="breadcrumb-link">⚙ 商店配置</span>
          <span class="breadcrumb-sep">›</span>
          <span class="breadcrumb-current">${esc(e.entryId || entryKey)}</span>
        </span>
      </div>

      <div class="row">
        <div class="f"><label>条目 ID</label><input data-b="${prefix}.entryId" value="${esc(e.entryId || '')}" placeholder="entryId"></div>
        <div class="f"><label>分类</label><input data-b="${prefix}.category" value="${esc(e.category || '')}" placeholder="categoryId" list="trade-entry-cat-list"><datalist id="trade-entry-cat-list">${(trade.categories || []).map(c => `<option value="${esc(c.categoryId)}"></option>`).join('')}</datalist></div>
      </div>
      ${renderTextSpec(`${prefix}.displayName`, e.displayName || {}, '显示名称')}
      ${renderTextSpec(`${prefix}.description`, e.description || {}, '描述', true)}
      <div class="row">
        <div class="f"><label>排序</label><input type="number" data-b="${prefix}.sortOrder" value="${e.sortOrder ?? 0}" min="0" max="1000"></div>
        <div class="f"><label>主题色</label><input type="number" data-b="${prefix}.themeColor" value="${e.themeColor ?? -1}" min="-1"><span class="tiny" style="opacity:.5">-1=使用商店默认</span></div>
      </div>
    </div>

    <div class="sec">
      <h3>成本 (Costs)</h3>
      ${costsHtml}
      <div class="actions"><button data-trade-offer-add="${prefix}.costs">＋ 添加成本</button></div>
    </div>

    <div class="sec">
      <h3>奖励 (Rewards)</h3>
      ${rewardsHtml}
      <div class="actions"><button data-trade-offer-add="${prefix}.rewards">＋ 添加奖励</button></div>
    </div>

    <div class="sec">
      <div class="row">
        <div class="f"><label>冷却类型</label><select data-b="${prefix}.cooldownType"><option value="NONE" ${e.cooldownType === 'NONE' ? 'selected' : ''}>无冷却</option><option value="SECONDS" ${e.cooldownType === 'SECONDS' ? 'selected' : ''}>秒</option><option value="GAME_DAY" ${e.cooldownType === 'GAME_DAY' ? 'selected' : ''}>游戏日</option><option value="GAME_TICK" ${e.cooldownType === 'GAME_TICK' ? 'selected' : ''}>游戏刻</option></select></div>
        <div class="f"><label>冷却值</label><input type="number" data-b="${prefix}.cooldownValue" value="${e.cooldownValue ?? 0}" min="0"></div>
        <div class="f"><label>重置刻</label><input type="number" data-b="${prefix}.resetTimeTicks" value="${e.resetTimeTicks ?? 0}" min="0" max="24000"></div>
      </div>
      <div class="row">
        <div class="f"><label>最大购买次数</label><input type="number" data-b="${prefix}.maxPurchases" value="${e.maxPurchases ?? 0}" min="0"><span class="tiny" style="opacity:.5">0=无限制</span></div>
      </div>
    </div>

    <div class="sec">
      <div class="row">
        <div class="f"><label>奖励图标</label><input data-b="${prefix}.rewardIcon" value="${esc(e.rewardIcon || '')}" placeholder="namespace:texture_path"></div>
        <div class="f"><label>成本图标</label><input data-b="${prefix}.costIcon" value="${esc(e.costIcon || '')}" placeholder="namespace:texture_path"></div>
      </div>
    </div>

    <div class="sec">
      <div class="row">
        <div class="f"><label>购买成功音效</label><input data-b="${prefix}.purchaseSuccessSound" value="${esc(e.purchaseSuccessSound || '')}" placeholder="minecraft:entity.player.levelup"></div>
        <div class="f"><label>购买失败音效</label><input data-b="${prefix}.purchaseFailSound" value="${esc(e.purchaseFailSound || '')}"></div>
      </div>
      <div class="row">
        <div class="f"><label>冷却中音效</label><input data-b="${prefix}.cooldownSound" value="${esc(e.cooldownSound || '')}"></div>
        <div class="f"><label>限购满音效</label><input data-b="${prefix}.limitReachedSound" value="${esc(e.limitReachedSound || '')}"></div>
        <div class="f"><label>条件不满足音效</label><input data-b="${prefix}.conditionFailSound" value="${esc(e.conditionFailSound || '')}"></div>
      </div>
    </div>

    <div class="sec">
      <h3>可见条件</h3>
      <div style="margin-top:8px">
        ${renderConditionTree(`${prefix}.visibleCond`, e.visibleCondition || {condition: 'arc_quest:always'}, registry, true)}
      </div>
      <h3 style="margin-top:12px">购买条件</h3>
      <div style="margin-top:8px">
        ${renderConditionTree(`${prefix}.canBuyCond`, e.canBuyCondition || {condition: 'arc_quest:always'}, registry, true)}
      </div>
    </div>

    <div class="actions" style="margin-top:12px">
      <button data-dtrade-entry="${entryKey}">删除此条目</button>
    </div>
  `;
}

function renderOfferList(offers, prefix) {
    if (!offers || offers.length === 0) return '<div class="small" style="color:var(--text-mut);margin-bottom:8px">暂无</div>';

    return offers.map((o, i) => {
        const typeOptions = OFFER_TYPES.map(t =>
            `<option value="${t.v}" ${o.type === t.v ? 'selected' : ''}>${t.l}</option>`
        ).join('');

        let fieldsHtml = '';
        switch (o.type) {
            case 'item':
                fieldsHtml = `
                  <input data-b="${prefix}.${i}.itemId" value="${esc(o.itemId || '')}" placeholder="minecraft:stone" style="flex:1">
                  <input type="number" data-b="${prefix}.${i}.count" value="${o.count ?? 1}" min="1" max="9999" style="width:60px" title="数量">`;
                break;
            case 'command':
                fieldsHtml = `
                  <input data-b="${prefix}.${i}.command" value="${esc(o.command || '')}" placeholder="say hello" style="flex:1">
                  <select data-b="${prefix}.${i}.executeAs" style="width:90px"><option value="console" ${o.executeAs === 'console' ? 'selected' : ''}>控制台</option><option value="player" ${o.executeAs === 'player' ? 'selected' : ''}>玩家</option></select>`;
                break;
            case 'effect':
                fieldsHtml = `
                  <input data-b="${prefix}.${i}.effectId" value="${esc(o.effectId || '')}" placeholder="minecraft:regeneration" style="flex:1">
                  <input type="number" data-b="${prefix}.${i}.duration" value="${o.duration ?? 0}" min="0" max="999999" style="width:70px" title="时长(ticks)">
                  <input type="number" data-b="${prefix}.${i}.amplifier" value="${o.amplifier ?? 0}" min="0" max="255" style="width:50px" title="等级">`;
                break;
            case 'flag':
                fieldsHtml = `<input data-b="${prefix}.${i}.flagName" value="${esc(o.flagName || '')}" placeholder="flag_name" style="flex:1">`;
                break;
            case 'composite':
                fieldsHtml = `<span class="tiny" style="opacity:.5;flex:1">${(o.offers || []).length} 个子项</span>`;
                break;
        }

        return `<div class="card" style="margin-bottom:4px">
          <div style="display:flex;align-items:center;gap:8px">
            <select data-b="${prefix}.${i}.type" style="width:80px">${typeOptions}</select>
            ${fieldsHtml}
            <button data-trade-offer-del="${prefix}.${i}" class="toolbar-btn small-btn" style="color:var(--danger)" title="删除">✕</button>
          </div>
        </div>`;
    }).join('');
}

function renderTradeEntryFallback() {
    return `<div class="sec">
      <div class="breadcrumb"><span class="breadcrumb-current">⚙ 商店配置</span></div>
      <div class="small" style="color:var(--text-mut)">条目不存在，请返回概览</div>
      <div style="margin-top:8px"><button data-trade-nav="overview" class="toolbar-btn small-btn">← 返回概览</button></div>
    </div>`;
}
