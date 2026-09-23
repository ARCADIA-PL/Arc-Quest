import {validateQuestCondition as validateConditionNode} from './condition-codec.js';
const VALID_RARITIES = new Set(['LEGENDARY', 'EPIC', 'RARE', 'UNCOMMON', 'COMMON']);
const VALID_COOLDOWN_TYPES = new Set(['NONE', 'SECONDS', 'GAME_DAY', 'GAME_TICK']);

export function validateGacha(gacha) {
    const issues = [];
    validateConditionNode(gacha?.openCondition, 'openCondition', issues);
    validateConditionNode(gacha?.drawCondition, 'drawCondition', issues);
    validateConditionNode(gacha?.resetCondition, 'resetCondition', issues);
    validateConditionNode(gacha?.pity?.resetCondition, 'pity.resetCondition', issues);
    const error = (path, msg) => issues.push({lvl: 'err', path, msg});
    const warn = (path, msg) => issues.push({lvl: 'warn', path, msg});
    if (!gacha?.shopId?.trim()) error('shopId', 'shopId is required');
    if (!gacha?.displayName?.value?.trim()) error('displayName', 'displayName is required');
    if (!Array.isArray(gacha?.drawCosts) || !gacha.drawCosts.length) error('drawCosts', 'at least one draw cost is required');
    (gacha?.drawCosts || []).forEach((offer, index) => validateOffer(offer, `drawCosts[${index}]`, true, error));
    if (!VALID_COOLDOWN_TYPES.has(gacha?.cooldownType || 'NONE')) error('cooldownType', 'invalid cooldown type');
    if (gacha?.pity && !VALID_COOLDOWN_TYPES.has(gacha.pity.resetCooldownType || 'NONE')) error('pity.resetCooldownType', 'invalid reset cooldown type');
    const rarities = gacha?.rarities || [];
    if (!rarities.length) error('rarities', 'at least one rarity is required');
    const rarityNames = new Set();
    rarities.forEach((rarity, index) => {
        if (!VALID_RARITIES.has(rarity.rarity)) error(`rarities[${index}].rarity`, 'invalid rarity');
        else if (rarityNames.has(rarity.rarity)) error(`rarities[${index}].rarity`, 'duplicate rarity');
        rarityNames.add(rarity.rarity);
    });
    const pools = gacha?.pools || [];
    if (!pools.length) error('pools', 'at least one pool is required');
    if (pools.length > 1) warn('pools', 'runtime merges all pools; poolId is metadata only');
    const poolIds = new Set();
    const itemIds = new Set();
    pools.forEach((pool, poolIndex) => {
        if (!pool.poolId) error(`pools[${poolIndex}].poolId`, 'poolId is required');
        else if (poolIds.has(pool.poolId)) error(`pools[${poolIndex}].poolId`, 'duplicate poolId');
        poolIds.add(pool.poolId);
        (pool.items || []).forEach((item, itemIndex) => {
            const path = `pools[${poolIndex}].items[${itemIndex}]`;
            validateConditionNode(item.visibleCondition, `${path}.visibleCondition`, issues);
            (item.weightModifiers || []).forEach((modifier, index) =>
                validateConditionNode(modifier.condition, `${path}.weightModifiers[${index}].condition`, issues));
            if (!item.itemId) error(`${path}.itemId`, 'itemId is required');
            else if (itemIds.has(item.itemId)) error(`${path}.itemId`, 'duplicate itemId');
            itemIds.add(item.itemId);
            if (!item.item) error(`${path}.item`, 'preview item is required');
            if ((item.weight ?? 0) <= 0) error(`${path}.weight`, 'weight must be positive');
            if ((item.minCount ?? 0) <= 0 || item.maxCount < item.minCount) error(`${path}.minCount`, 'invalid count range');
            if (item.rarity && rarityNames.size && !rarityNames.has(item.rarity)) warn(`${path}.rarity`, 'rarity is not configured');
            if (item.reward) validateOffer(item.reward, `${path}.reward`, false, error);
        });
    });
    return issues;
}

function validateOffer(offer, path, isCost, error) {
    if (!offer?.type) return error(`${path}.type`, 'offer type is required');
    if (offer.type === 'item') {
        if (!!offer.itemId === !!offer.itemTag) error(path, 'exactly one of itemId or itemTag is required');
        if (!isCost && offer.itemTag) error(`${path}.itemTag`, 'itemTag is only supported for costs');
    } else if (offer.type === 'command' && !offer.command) error(`${path}.command`, 'command is required');
    else if (offer.type === 'effect' && !offer.effectId) error(`${path}.effectId`, 'effectId is required');
    else if (offer.type === 'flag' && !offer.flagName) error(`${path}.flagName`, 'flagName is required');
    else if (offer.type === 'composite') (offer.offers || []).forEach((child, index) => validateOffer(child, `${path}.offers[${index}]`, isCost, error));
}
