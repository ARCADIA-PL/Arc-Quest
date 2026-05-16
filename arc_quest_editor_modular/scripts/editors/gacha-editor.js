import {esc} from '../core/utils.js';
import {renderTextSpec} from './textspec-editor.js';
import {renderColorInput} from './color-input.js';

const OFFER_TYPES = ['item', 'command', 'effect', 'flag', 'composite'];
const OFFER_LABELS = {item: '物品', command: '命令', effect: '效果', flag: 'Flag', composite: '组合'};

export function renderGachaOverview(gacha, registry, state) {

    const drawCostHtml = renderSingleOffer(gacha.drawCost, 'gacha.drawCost');

    const raritiesHtml = (gacha.rarities || []).map((r, i) => {
        return `<div class="card" style="margin-bottom:4px"><div style="display:flex;align-items:center;gap:8px">
          <select data-b="gacha.rarities.${i}.rarity" style="width:100px">
            ${['LEGENDARY','EPIC','RARE','UNCOMMON','COMMON'].map(v => `<option value="${v}" ${r.rarity===v?'selected':''}>${v}</option>`).join('')}
          </select>
          ${renderColorInput(`gacha.rarities.${i}.color`, r.color ?? 0xFFFFFF)}
          <button data-dgachararity="${i}" class="toolbar-btn small-btn" style="color:var(--danger)">✕</button>
        </div></div>`;
    }).join('');

    const pityHtml = gacha.pity ? `
      <div class="card" style="margin-bottom:4px"><div style="display:flex;align-items:center;gap:8px">
        <label class="tiny">保底阈值</label><input type="number" data-b="gacha.pity.threshold" value="${gacha.pity.threshold ?? 10}" min="1" max="999" style="width:60px">
        <label class="tiny">目标稀有度</label><select data-b="gacha.pity.targetRarity" style="width:100px">
          ${['LEGENDARY','EPIC','RARE','UNCOMMON','COMMON'].map(v => `<option value="${v}" ${gacha.pity.targetRarity===v?'selected':''}>${v}</option>`).join('')}
        </select>
        <label class="tiny">提前中重置</label><input type="checkbox" data-b="gacha.pity.resetOnEarlyTrigger" value="true" ${gacha.pity.resetOnEarlyTrigger!==false?'checked':''}>
      </div></div>` : '';

    const allItems = (gacha.pools || []).flatMap(p => (p.items || []).map((it, ii) => ({...it, _pi: (gacha.pools || []).indexOf(p), _ii: ii})));
    const itemCards = allItems.map(it => {
        const w = it.weight ?? 1;
        return `<div class="detail-card bind-card" data-gacha-nav="item" data-gacha-pi="${it._pi}" data-gacha-ii="${it._ii}">
          <div style="display:flex;align-items:center;gap:8px">
            <b class="small" style="flex:1">🎲 ${esc(it.itemId || '?')}</b>
            <span class="bind-priority-badge">${it.rarity || 'RARE'} · W:${w}</span>
          </div>
          <div class="small" style="color:var(--text-mut);margin:4px 0">${esc(it.item || '')} ×${it.minCount ?? 1}~${it.maxCount ?? 1}</div>
          <div style="text-align:right"><button class="toolbar-btn small-btn" data-gacha-nav="item" data-gacha-pi="${it._pi}" data-gacha-ii="${it._ii}">编辑</button></div>
        </div>`;
    }).join('');

    return `
    <div class="sec">
      <div class="breadcrumb"><span class="breadcrumb-current">🎰 抽奖配置</span></div>
      <h3 style="margin-top:4px">商店属性</h3>
      <div class="row">
        <div class="f"><label>商店 ID</label><input data-b="gacha.shopId" value="${esc(gacha.shopId || '')}" placeholder="namespace:shop_id"></div>
        <div class="f"><label>主题色</label>${renderColorInput('gacha.themeColor', gacha.themeColor ?? 0xFFD700)}</div>
      </div>
      ${renderTextSpec('gacha.displayName', gacha.displayName || {}, '显示名称')}
      ${renderTextSpec('gacha.description', gacha.description || {}, '描述', true)}
    </div>
    <div class="sec">
      <h3>抽奖成本</h3>
      ${drawCostHtml}
    </div>
    <div class="sec">
      <div class="row">
        <div class="f"><label>最大抽数</label><input type="number" data-b="gacha.maxDraws" value="${gacha.maxDraws ?? -1}" min="-1"><span class="tiny" style="opacity:.5">-1=无限</span></div>
        <div class="f"><label>冷却类型</label><select data-b="gacha.cooldownType"><option value="NONE" ${gacha.cooldownType==='NONE'?'selected':''}>无冷却</option><option value="SECONDS" ${gacha.cooldownType==='SECONDS'?'selected':''}>秒</option><option value="GAME_DAY" ${gacha.cooldownType==='GAME_DAY'?'selected':''}>游戏日</option><option value="GAME_TICK" ${gacha.cooldownType==='GAME_TICK'?'selected':''}>游戏刻</option></select></div>
        <div class="f"><label>冷却值</label><input type="number" data-b="gacha.cooldownValue" value="${gacha.cooldownValue ?? 0}" min="0"></div>
        <div class="f"><label>重置刻</label><input type="number" data-b="gacha.resetTimeTicks" value="${gacha.resetTimeTicks ?? 0}" min="0" max="24000"></div>
      </div>
    </div>
    <div class="sec">
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">
        <h3 style="margin:0">稀有度配置 (${(gacha.rarities || []).length})</h3>
        <button id="addGachaRarityBtn" class="toolbar-btn small-btn">＋ 添加</button>
      </div>
      ${raritiesHtml || '<div class="small" style="color:var(--text-mut)">暂无稀有度</div>'}
    </div>
    <div class="sec">
      <h3 style="margin:0">保底配置</h3>
      ${pityHtml || '<div class="small" style="color:var(--text-mut);margin-top:4px">无保底</div>'}
      ${!gacha.pity ? '<div style="margin-top:4px"><button id="addGachaPityBtn" class="toolbar-btn small-btn">＋ 添加保底</button></div>' : ''}
    </div>
    <div class="sec">
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">
        <h3 style="margin:0">物品池 (${allItems.length})</h3>
        <button id="addGachaItemBtn" class="toolbar-btn small-btn">＋ 添加物品</button>
      </div>
      ${itemCards || '<div class="small" style="color:var(--text-mut)">暂无物品</div>'}
    </div>
  `;
}

function renderSingleOffer(o, bind) {
    if (!o) return '<div class="small" style="color:var(--text-mut)">未设置成本</div>';
    const typeOptions = OFFER_TYPES.map(t => `<option value="${t}" ${o.type===t?'selected':''}>${OFFER_LABELS[t]}</option>`).join('');
    let fields = '';
    switch (o.type) {
        case 'item': fields = `<input data-b="${bind}.itemId" value="${esc(o.itemId||'')}" placeholder="minecraft:stone" style="flex:1"><input type="number" data-b="${bind}.count" value="${o.count??1}" min="1" style="width:60px">`; break;
        case 'command': fields = `<input data-b="${bind}.command" value="${esc(o.command||'')}" placeholder="say hello" style="flex:1">`; break;
        case 'effect': fields = `<input data-b="${bind}.effectId" value="${esc(o.effectId||'')}" placeholder="minecraft:regeneration" style="flex:1"><input type="number" data-b="${bind}.duration" value="${o.duration??0}" min="0" style="width:60px">`; break;
        case 'flag': fields = `<input data-b="${bind}.flagName" value="${esc(o.flagName||'')}" placeholder="flag_name" style="flex:1">`; break;
        case 'composite': fields = `<span class="tiny" style="opacity:.5;flex:1">组合报价</span>`; break;
    }
    return `<div class="card" style="margin-bottom:4px"><div style="display:flex;align-items:center;gap:8px">
      <select data-b="${bind}.type" style="width:80px">${typeOptions}</select>
      ${fields}
    </div></div>`;
}

export function renderGachaItemEditor(gacha, pi, ii) {
    const pool = (gacha.pools || [])[pi];
    if (!pool) return renderGachaFallback();
    const item = (pool.items || [])[ii];
    if (!item) return renderGachaFallback();

    const prefix = `gacha.pools.${pi}.items.${ii}`;

    return `
    <div class="sec">
      <div class="card-strip-bar">
        <button data-gacha-nav="overview" class="toolbar-btn small-btn" style="font-weight:700;color:var(--accent)">← 返回</button>
        <span class="breadcrumb">
          <span data-gacha-nav="overview" class="breadcrumb-link">🎰 抽奖配置</span>
          <span class="breadcrumb-sep">›</span>
          <span class="breadcrumb-current">${esc(item.itemId || '?')}</span>
        </span>
      </div>
      <div class="row">
        <div class="f"><label>物品 ID</label><input data-b="${prefix}.itemId" value="${esc(item.itemId || '')}" placeholder="unique_id"></div>
        <div class="f"><label>物品类型</label><input data-b="${prefix}.item" value="${esc(item.item || '')}" placeholder="minecraft:diamond_sword"></div>
      </div>
      ${renderTextSpec(`${prefix}.displayName`, item.displayName || {}, '显示名称')}
      <div class="row">
        <div class="f"><label>权重</label><input type="number" data-b="${prefix}.weight" value="${item.weight ?? 1}" min="1" max="9999"></div>
        <div class="f"><label>稀有度</label><select data-b="${prefix}.rarity">
          ${['LEGENDARY','EPIC','RARE','UNCOMMON','COMMON'].map(v => `<option value="${v}" ${item.rarity===v?'selected':''}>${v}</option>`).join('')}
        </select></div>
      </div>
      <div class="row">
        <div class="f"><label>最小数量</label><input type="number" data-b="${prefix}.minCount" value="${item.minCount ?? 1}" min="1" max="999"></div>
        <div class="f"><label>最大数量</label><input type="number" data-b="${prefix}.maxCount" value="${item.maxCount ?? 1}" min="1" max="999"></div>
        <div class="f"><label>排序</label><input type="number" data-b="${prefix}.sortOrder" value="${item.sortOrder ?? 0}" min="0" max="999"></div>
      </div>
    </div>
    <div class="sec">
      <div class="row">
        <div class="f"><label>奖励图标</label><input data-b="${prefix}.rewardIcon" value="${esc(item.rewardIcon||'')}" placeholder="texture_path"></div>
        <div class="f"><label>主题色</label>${renderColorInput(`${prefix}.themeColor`, item.themeColor ?? -1, true)}</div>
        <div class="f"><label>抽中音效</label><input data-b="${prefix}.drawSuccessSound" value="${esc(item.drawSuccessSound||'')}" placeholder="minecraft:entity.player.levelup"></div>
      </div>
    </div>
    <div class="actions" style="margin-top:12px">
      <button data-dgachaitem="${pi}.${ii}">删除此物品</button>
    </div>
  `;
}

function renderGachaFallback() {
    return `<div class="sec"><div class="breadcrumb"><span class="breadcrumb-current">🎰 抽奖配置</span></div>
      <div class="small" style="color:var(--text-mut)">物品不存在，请返回概览</div>
      <div style="margin-top:8px"><button data-gacha-nav="overview" class="toolbar-btn small-btn">← 返回概览</button></div></div>`;
}
