import {normalizeCondition, exportCondition} from './condition-codec.js';
import {cloneDocument} from './json-document.js';
function normalizeText(spec) {
    if (!spec || typeof spec !== 'object') return {mode: 'literal', value: '', args: []};
    return {
        mode: spec.mode === 'translatable' ? 'translatable' : 'literal',
        value: spec.value || '',
        args: Array.isArray(spec.args) ? spec.args : []
    };
}

function exportText(spec) {
    return {mode: spec?.mode || 'literal', value: spec?.value || ''};
}

function normalizeOffer(offer) {
    const value = offer || {};
    return {
        type: value.type || 'item',
        itemId: value.itemId || '',
        itemTag: value.itemTag || '',
        count: value.count ?? 1,
        nbt: value.nbt || '',
        customIcon: value.customIcon || '',
        command: value.command || '',
        executeAs: value.executeAs || 'console',
        effectId: value.effectId || '',
        duration: value.duration ?? 0,
        amplifier: value.amplifier ?? 0,
        flagName: value.flagName || '',
        offers: Array.isArray(value.offers) ? value.offers.map(normalizeOffer) : []
    };
}

function exportOffer(offer) {
    const out = {type: offer?.type || 'item'};
    if (out.type === 'item') {
        if (offer.itemId) out.itemId = offer.itemId;
        if (offer.itemTag) out.itemTag = offer.itemTag;
        out.count = offer.count ?? 1;
        if (offer.nbt) out.nbt = offer.nbt;
        if (offer.customIcon) out.customIcon = offer.customIcon;
    } else if (out.type === 'command') {
        out.command = offer.command || '';
        if (offer.executeAs && offer.executeAs !== 'console') out.executeAs = offer.executeAs;
    } else if (out.type === 'effect') {
        out.effectId = offer.effectId || '';
        if (offer.duration > 0) out.duration = offer.duration;
        if (offer.amplifier > 0) out.amplifier = offer.amplifier;
    } else if (out.type === 'flag') {
        out.flagName = offer.flagName || '';
    } else if (out.type === 'composite') {
        out.offers = (offer.offers || []).map(exportOffer);
    }
    return out;
}

function normalizeItem(item) {
    return {
        itemId: item?.itemId || '',
        displayName: normalizeText(item?.displayName),
        item: item?.item || '',
        weight: item?.weight ?? 1,
        countsTowardsPity: item?.countsTowardsPity !== false,
        rarity: item?.rarity || 'RARE',
        minCount: item?.minCount ?? 1,
        maxCount: item?.maxCount ?? 1,
        sortOrder: item?.sortOrder ?? 0,
        rewardIcon: item?.rewardIcon || '',
        themeColor: item?.themeColor ?? -1,
        drawSuccessSound: item?.drawSuccessSound || '',
        visibleCondition: item?.visibleCondition == null ? null : normalizeCondition(item?.visibleCondition),
        reward: item?.reward ? normalizeOffer(item.reward) : null,
        weightModifiers: (item?.weightModifiers || []).map(modifier => ({
            condition: modifier?.condition == null ? null : normalizeCondition(modifier?.condition),
            weightDelta: modifier?.weightDelta ?? 0
        }))
    };
}

function exportItem(item) {
    const out = {
        itemId: item.itemId || '',
        displayName: exportText(item.displayName),
        item: item.item || ''
    };
    if (item.weight !== 1) out.weight = item.weight;
    if (item.countsTowardsPity === false) out.countsTowardsPity = false;
    if (item.rarity && item.rarity !== 'RARE') out.rarity = item.rarity;
    if (item.minCount !== 1) out.minCount = item.minCount;
    if (item.maxCount !== 1) out.maxCount = item.maxCount;
    if (item.sortOrder !== 0) out.sortOrder = item.sortOrder;
    if (item.rewardIcon) out.rewardIcon = item.rewardIcon;
    if (item.themeColor !== -1) out.themeColor = item.themeColor;
    if (item.drawSuccessSound) out.drawSuccessSound = item.drawSuccessSound;
    if (item.visibleCondition) out.visibleCondition = exportCondition(item.visibleCondition);
    if (item.reward) out.reward = exportOffer(item.reward);
    if (item.weightModifiers?.length) out.weightModifiers = item.weightModifiers.map(modifier => ({
        condition: modifier.condition == null ? null : exportCondition(modifier.condition),
        weightDelta: modifier.weightDelta ?? 0
    }));
    return out;
}

export function normalizeImportedGacha(input) {
    input = cloneDocument(input);
    const drawCosts = Array.isArray(input.drawCosts) && input.drawCosts.length
        ? input.drawCosts.map(normalizeOffer)
        : (input.drawCost ? [normalizeOffer(input.drawCost)] : []);
    return {
        shopId: input.shopId || '',
        displayName: normalizeText(input.displayName),
        description: input.description ? normalizeText(input.description) : null,
        categories: (input.categories || []).map(category => ({
            categoryId: category.categoryId || '',
            displayName: normalizeText(category.displayName),
            sortOrder: category.sortOrder ?? 0,
            formatting: category.formatting || ''
        })),
        openCondition: input.openCondition == null ? null : normalizeCondition(input.openCondition),
        themeColor: typeof input.themeColor === 'number' ? input.themeColor : 0xFFD700,
        simpleMode: input.simpleMode === true,
        openSound: input.openSound || '',
        closeSound: input.closeSound || '',
        drawCost: null,
        drawCosts,
        maxDraws: input.maxDraws ?? -1,
        cooldownType: input.cooldownType || 'NONE',
        cooldownValue: input.cooldownValue ?? 0,
        resetTimeTicks: input.resetTimeTicks ?? 0,
        drawCondition: input.drawCondition == null ? null : normalizeCondition(input.drawCondition),
        resetCondition: input.resetCondition == null ? null : normalizeCondition(input.resetCondition),
        resetOnLimitReached: input.resetOnLimitReached !== false,
        resetPityOnEarlyTrigger: input.resetPityOnEarlyTrigger !== false,
        drawCooldownSound: input.drawCooldownSound || '',
        drawLimitReachedSound: input.drawLimitReachedSound || '',
        drawConditionFailSound: input.drawConditionFailSound || '',
        drawFailSound: input.drawFailSound || '',
        rarities: (input.rarities || []).map(rarity => ({
            rarity: rarity.rarity || 'RARE',
            color: rarity.color ?? 0xFFFFFF,
            drawSuccessSound: rarity.drawSuccessSound || ''
        })),
        pity: input.pity ? {
            threshold: input.pity.threshold ?? 10,
            targetRarity: input.pity.targetRarity || 'LEGENDARY',
            guaranteedItemId: input.pity.guaranteedItemId || '',
            resetOnEarlyTrigger: input.pity.resetOnEarlyTrigger !== false,
            resetCooldownType: input.pity.resetCooldownType || 'NONE',
            resetCooldownValue: input.pity.resetCooldownValue ?? 0,
            resetCondition: input.pity.resetCondition == null ? null : normalizeCondition(input.pity.resetCondition),
            resetOnTrigger: input.pity.resetOnTrigger !== false
        } : null,
        pools: (input.pools || []).map(pool => ({
            poolId: pool.poolId || 'default',
            items: (pool.items || []).map(normalizeItem)
        }))
    };
}

export function exportGachaToDatapack(gacha) {
    gacha = cloneDocument(gacha);
    const out = {
        shopId: gacha.shopId || '',
        displayName: exportText(gacha.displayName),
        drawCosts: (gacha.drawCosts || []).map(exportOffer),
        pools: (gacha.pools || []).map(pool => ({
            poolId: pool.poolId || 'default',
            items: (pool.items || []).map(exportItem)
        }))
    };
    if (gacha.description) out.description = exportText(gacha.description);
    if (gacha.categories?.length) out.categories = gacha.categories.map(category => ({
        categoryId: category.categoryId || '',
        displayName: exportText(category.displayName),
        sortOrder: category.sortOrder ?? 0,
        formatting: category.formatting || ''
    }));
    if (gacha.openCondition) out.openCondition = exportCondition(gacha.openCondition);
    if (gacha.themeColor !== 0xFFD700) out.themeColor = gacha.themeColor;
    if (gacha.simpleMode) out.simpleMode = true;
    if (gacha.openSound) out.openSound = gacha.openSound;
    if (gacha.closeSound) out.closeSound = gacha.closeSound;
    if (gacha.maxDraws !== -1) out.maxDraws = gacha.maxDraws;
    if (gacha.cooldownType !== 'NONE') out.cooldownType = gacha.cooldownType;
    if (gacha.cooldownValue > 0) out.cooldownValue = gacha.cooldownValue;
    if (gacha.resetTimeTicks > 0) out.resetTimeTicks = gacha.resetTimeTicks;
    if (gacha.drawCondition) out.drawCondition = exportCondition(gacha.drawCondition);
    if (gacha.resetCondition) out.resetCondition = exportCondition(gacha.resetCondition);
    if (gacha.resetOnLimitReached === false) out.resetOnLimitReached = false;
    if (gacha.resetPityOnEarlyTrigger === false) out.resetPityOnEarlyTrigger = false;
    for (const field of ['drawCooldownSound', 'drawLimitReachedSound', 'drawConditionFailSound', 'drawFailSound']) {
        if (gacha[field]) out[field] = gacha[field];
    }
    if (gacha.rarities?.length) out.rarities = gacha.rarities.map(rarity => ({
        rarity: rarity.rarity || 'RARE',
        color: rarity.color ?? 0xFFFFFF,
        drawSuccessSound: rarity.drawSuccessSound || ''
    }));
    if (gacha.pity) {
        out.pity = {
            threshold: gacha.pity.threshold ?? 10,
            targetRarity: gacha.pity.targetRarity || 'LEGENDARY',
            guaranteedItemId: gacha.pity.guaranteedItemId || '',
            resetCooldownType: gacha.pity.resetCooldownType || 'NONE',
            resetCooldownValue: gacha.pity.resetCooldownValue ?? 0,
            resetOnTrigger: gacha.pity.resetOnTrigger !== false
        };
        if (gacha.pity.resetCondition) out.pity.resetCondition = exportCondition(gacha.pity.resetCondition);
        if (gacha.pity.resetOnEarlyTrigger === false) out.pity.resetOnEarlyTrigger = false;
    }
    return out;
}
