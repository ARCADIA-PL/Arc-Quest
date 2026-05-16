const VALID_RARITIES = new Set(['LEGENDARY', 'EPIC', 'RARE', 'UNCOMMON', 'COMMON']);
const VALID_COOLDOWN_TYPES = new Set(['NONE', 'SECONDS', 'GAME_DAY', 'GAME_TICK']);

export function validateGacha(gacha) {
    const d = [];

    if (!gacha.shopId || !gacha.shopId.trim()) {
        d.push({lvl: 'err', path: 'shopId', msg: '商店 ID 不能为空'});
    }
    if (!gacha.displayName || !gacha.displayName.value || !gacha.displayName.value.trim()) {
        d.push({lvl: 'err', path: 'displayName', msg: '显示名称不能为空'});
    }

    if (!gacha.drawCost) {
        d.push({lvl: 'err', path: 'drawCost', msg: '抽奖成本不能为空'});
    } else if (gacha.drawCost.type === 'item' && (!gacha.drawCost.itemId || !gacha.drawCost.itemId.trim())) {
        d.push({lvl: 'err', path: 'drawCost.itemId', msg: '成本物品 ID 不能为空'});
    }

    if (gacha.cooldownType && !VALID_COOLDOWN_TYPES.has(gacha.cooldownType)) {
        d.push({lvl: 'err', path: 'cooldownType', msg: `无效的冷却类型: ${gacha.cooldownType}`});
    }

    const rarities = gacha.rarities || [];
    if (rarities.length === 0) {
        d.push({lvl: 'err', path: 'rarities', msg: '至少需要一个稀有度'});
    }
    const seenRarities = new Set();
    rarities.forEach((r, i) => {
        if (!VALID_RARITIES.has(r.rarity)) {
            d.push({lvl: 'err', path: `rarities[${i}].rarity`, msg: `无效的稀有度: ${r.rarity}`});
        } else if (seenRarities.has(r.rarity)) {
            d.push({lvl: 'err', path: `rarities[${i}].rarity`, msg: `重复的稀有度: ${r.rarity}`});
        } else {
            seenRarities.add(r.rarity);
        }
    });

    if (gacha.pity && !gacha.pity.threshold) {
        d.push({lvl: 'err', path: 'pity.threshold', msg: '保底阈值必须 > 0'});
    }

    const pools = gacha.pools || [];
    if (pools.length === 0 || pools.every(p => !p.items || p.items.length === 0)) {
        d.push({lvl: 'err', path: 'pools', msg: '至少需要一个有物品的奖池'});
    }

    const rarityNames = new Set(rarities.map(r => r.rarity));
    const seenItemIds = new Set();
    pools.forEach((pool, pi) => {
        (pool.items || []).forEach((item, ii) => {
            const prefix = `pools[${pi}].items[${ii}]`;
            if (!item.itemId || !item.itemId.trim()) {
                d.push({lvl: 'err', path: `${prefix}.itemId`, msg: '物品 ID 不能为空'});
            } else if (seenItemIds.has(item.itemId)) {
                d.push({lvl: 'err', path: `${prefix}.itemId`, msg: `重复的物品 ID: ${item.itemId}`});
            } else {
                seenItemIds.add(item.itemId);
            }
            if (!item.item || !item.item.trim()) {
                d.push({lvl: 'err', path: `${prefix}.item`, msg: '物品类型不能为空'});
            }
            if (!item.weight || item.weight <= 0) {
                d.push({lvl: 'err', path: `${prefix}.weight`, msg: '权重必须 > 0'});
            }
            if (rarityNames.size > 0 && !rarityNames.has(item.rarity)) {
                d.push({lvl: 'warn', path: `${prefix}.rarity`, msg: `稀有度 '${item.rarity}' 不在稀有度列表中`});
            }
        });
    });

    return d;
}
