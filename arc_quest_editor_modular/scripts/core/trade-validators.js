const VALID_OFFER_TYPES = new Set(['item', 'command', 'effect', 'flag', 'composite']);
const VALID_COOLDOWN_TYPES = new Set(['NONE', 'SECONDS', 'GAME_DAY', 'GAME_TICK']);

function validateConditionNode(node, path, d) {
    if (!node) return;
    if (node.condition === 'arc_quest:always') return;
    if (node.inner) validateConditionNode(node.inner, `${path}.inner`, d);
    if (node.conditions) node.conditions.forEach((c, i) => validateConditionNode(c, `${path}.conditions.${i}`, d));
}

export function validateTrade(trade) {
    const d = [];

    if (!trade.shopId || !trade.shopId.trim()) {
        d.push({lvl: 'err', path: 'shopId', msg: '商店 ID 不能为空'});
    }
    if (!trade.displayName || !trade.displayName.value || !trade.displayName.value.trim()) {
        d.push({lvl: 'err', path: 'displayName', msg: '显示名称不能为空'});
    }

    const entries = trade.entries || {};
    const entryIds = Object.keys(entries);
    if (entryIds.length === 0) {
        d.push({lvl: 'warn', path: 'entries', msg: '商店尚无条目'});
    }

    const seenIds = new Set();
    for (const key of entryIds) {
        const e = entries[key];
        if (!e) continue;
        const prefix = `entries.${key}`;

        if (!e.entryId || !e.entryId.trim()) {
            d.push({lvl: 'err', path: `${prefix}.entryId`, msg: '条目 ID 不能为空'});
        } else if (seenIds.has(e.entryId)) {
            d.push({lvl: 'err', path: `${prefix}.entryId`, msg: `重复的条目 ID: ${e.entryId}`});
        } else {
            seenIds.add(e.entryId);
        }

        if (!e.rewards || e.rewards.length === 0) {
            d.push({lvl: 'err', path: `${prefix}.rewards`, msg: '至少需要一个奖励'});
        }

        if (e.costs && e.costs.length > 0) {
            validateOffers(e.costs, `${prefix}.costs`, d);
        }
        if (e.rewards && e.rewards.length > 0) {
            validateOffers(e.rewards, `${prefix}.rewards`, d);
        }

        if (e.visibleCondition) {
            validateConditionNode(e.visibleCondition, `${prefix}.visibleCondition`, d);
        }
        if (e.canBuyCondition) {
            validateConditionNode(e.canBuyCondition, `${prefix}.canBuyCondition`, d);
        }
        if (e.cooldownType && !VALID_COOLDOWN_TYPES.has(e.cooldownType)) {
            d.push({lvl: 'err', path: `${prefix}.cooldownType`, msg: `无效的冷却类型: ${e.cooldownType}`});
        }
    }

    if (trade.openCondition) {
        validateConditionNode(trade.openCondition, 'openCondition', d);
    }

    return d;
}

function validateOffers(offers, prefix, d) {
    offers.forEach((o, i) => {
        const p = `${prefix}[${i}]`;
        if (!VALID_OFFER_TYPES.has(o.type)) {
            d.push({lvl: 'err', path: `${p}.type`, msg: `无效的 offer 类型: ${o.type}`});
            return;
        }
        if (o.type === 'item' && (!o.itemId || !o.itemId.trim())) {
            d.push({lvl: 'err', path: `${p}.itemId`, msg: '物品 ID 不能为空'});
        }
        if (o.type === 'command' && (!o.command || !o.command.trim())) {
            d.push({lvl: 'err', path: `${p}.command`, msg: '命令不能为空'});
        }
        if (o.type === 'effect' && (!o.effectId || !o.effectId.trim())) {
            d.push({lvl: 'err', path: `${p}.effectId`, msg: '效果 ID 不能为空'});
        }
        if (o.type === 'flag' && (!o.flagName || !o.flagName.trim())) {
            d.push({lvl: 'err', path: `${p}.flagName`, msg: 'Flag 名称不能为空'});
        }
        if (o.type === 'composite') {
            if (!o.offers || o.offers.length === 0) {
                d.push({lvl: 'err', path: `${p}.offers`, msg: '组合报价至少需要一个子项'});
            } else {
                validateOffers(o.offers, `${p}.offers`, d);
            }
        }
    });
}
