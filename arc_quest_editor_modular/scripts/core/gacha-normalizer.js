export function normalizeImportedGacha(input) {
    return {
        shopId: input.shopId || '',
        displayName: normalizeGachaText(input.displayName),
        description: input.description ? normalizeGachaText(input.description) : null,
        categories: (input.categories || []).map(c => ({
            categoryId: c.categoryId || '',
            displayName: normalizeGachaText(c.displayName),
            sortOrder: c.sortOrder ?? 0,
            formatting: c.formatting || ''
        })),
        openCondition: input.openCondition || null,
        themeColor: typeof input.themeColor === 'number' ? input.themeColor : 0xFFD700,
        simpleMode: input.simpleMode !== undefined ? input.simpleMode : false,
        openSound: input.openSound || '',
        closeSound: input.closeSound || '',
        drawCost: normalizeGachaOffer(input.drawCost),
        maxDraws: input.maxDraws ?? -1,
        cooldownType: input.cooldownType || 'NONE',
        cooldownValue: input.cooldownValue ?? 0,
        resetTimeTicks: input.resetTimeTicks ?? 0,
        rarities: (input.rarities || []).map(r => ({
            rarity: r.rarity || 'RARE',
            color: r.color ?? 0xFFFFFF,
            drawSuccessSound: r.drawSuccessSound || ''
        })),
        pity: input.pity ? {
            threshold: input.pity.threshold ?? 10,
            targetRarity: input.pity.targetRarity || 'LEGENDARY',
            guaranteedItemId: input.pity.guaranteedItemId || '',
            resetOnEarlyTrigger: input.pity.resetOnEarlyTrigger !== false
        } : null,
        pools: (input.pools || []).map(p => ({
            poolId: p.poolId || 'default',
            items: (p.items || []).map(it => ({
                itemId: it.itemId || '',
                displayName: normalizeGachaText(it.displayName),
                item: it.item || '',
                weight: it.weight ?? 1,
                rarity: it.rarity || 'RARE',
                minCount: it.minCount ?? 1,
                maxCount: it.maxCount ?? 1,
                sortOrder: it.sortOrder ?? 0,
                rewardIcon: it.rewardIcon || '',
                themeColor: it.themeColor ?? -1,
                drawSuccessSound: it.drawSuccessSound || ''
            }))
        }))
    };
}

function normalizeGachaOffer(o) {
    if (!o) return {type: 'item', itemId: '', count: 1, nbt: '', command: '', executeAs: 'console', effectId: '', duration: 0, amplifier: 0, flagName: '', offers: []};
    return {
        type: o.type || 'item',
        itemId: o.itemId || '',
        count: o.count ?? 1,
        nbt: o.nbt || '',
        command: o.command || '',
        executeAs: o.executeAs || 'console',
        effectId: o.effectId || '',
        duration: o.duration ?? 0,
        amplifier: o.amplifier ?? 0,
        flagName: o.flagName || '',
        offers: Array.isArray(o.offers) ? o.offers.map(c => normalizeGachaOffer(c)) : []
    };
}

function normalizeGachaText(spec) {
    if (!spec || typeof spec !== 'object') return {mode: 'literal', value: '', args: []};
    return {mode: spec.mode === 'translatable' ? 'translatable' : 'literal', value: spec.value || '', args: Array.isArray(spec.args) ? spec.args : []};
}

function exportGachaOffer(o) {
    const out = {type: o.type || 'item'};
    if (o.type === 'item' || !o.type || o.type === 'item') {
        out.itemId = o.itemId || '';
        out.count = o.count ?? 1;
        if (o.nbt) out.nbt = o.nbt;
    } else if (o.type === 'command') {
        out.command = o.command || '';
        if (o.executeAs && o.executeAs !== 'console') out.executeAs = o.executeAs;
    } else if (o.type === 'effect') {
        out.effectId = o.effectId || '';
        if (o.duration > 0) out.duration = o.duration;
        if (o.amplifier > 0) out.amplifier = o.amplifier;
    } else if (o.type === 'flag') {
        out.flagName = o.flagName || '';
    }
    if (o.offers?.length) out.offers = o.offers.map(exportGachaOffer);
    return out;
}

function exportGachaItem(it) {
    const out = {itemId: it.itemId || ''};
    if (it.weight !== 1) out.weight = it.weight;
    if (it.rarity && it.rarity !== 'RARE') out.rarity = it.rarity;
    if (it.minCount !== 1) out.minCount = it.minCount;
    if (it.maxCount !== 1) out.maxCount = it.maxCount;
    if (it.sortOrder > 0) out.sortOrder = it.sortOrder;
    if (it.displayName) out.displayName = {mode: it.displayName.mode || 'translatable', value: it.displayName.value || ''};
    if (it.item) out.item = it.item;
    if (it.rewardIcon) out.rewardIcon = it.rewardIcon;
    if (it.themeColor !== -1) out.themeColor = it.themeColor;
    if (it.drawSuccessSound) out.drawSuccessSound = it.drawSuccessSound;
    return out;
}

export function exportGachaToDatapack(gacha) {
    const out = {
        shopId: gacha.shopId || '',
        pools: (gacha.pools || []).map(p => ({
            poolId: p.poolId || 'default',
            items: (p.items || []).map(exportGachaItem)
        }))
    };
    if (gacha.displayName) out.displayName = {mode: gacha.displayName.mode || 'translatable', value: gacha.displayName.value || ''};
    if (gacha.drawCost) out.drawCost = exportGachaOffer(gacha.drawCost);
    if (gacha.categories?.length) out.categories = gacha.categories.map(c => {
        const co = {categoryId: c.categoryId || ''};
        if (c.displayName) co.displayName = {mode: c.displayName.mode || 'translatable', value: c.displayName.value || ''};
        if (c.sortOrder > 0) co.sortOrder = c.sortOrder;
        if (c.formatting) co.formatting = c.formatting;
        return co;
    });
    if (gacha.rarities?.length) out.rarities = gacha.rarities.map(r => {
        const ro = {rarity: r.rarity || 'RARE'};
        if (r.color && r.color !== 0xFFFFFF) ro.color = r.color;
        if (r.drawSuccessSound) ro.drawSuccessSound = r.drawSuccessSound;
        return ro;
    });
    if (gacha.pity) out.pity = {
        threshold: gacha.pity.threshold ?? 10,
        targetRarity: gacha.pity.targetRarity || 'LEGENDARY'
    };
    if (gacha.maxDraws !== -1) out.maxDraws = gacha.maxDraws;
    if (gacha.cooldownType && gacha.cooldownType !== 'NONE') out.cooldownType = gacha.cooldownType;
    if (gacha.cooldownValue > 0) out.cooldownValue = gacha.cooldownValue;
    if (gacha.resetTimeTicks > 0) out.resetTimeTicks = gacha.resetTimeTicks;
    if (gacha.openCondition) out.openCondition = gacha.openCondition;
    if (gacha.description) out.description = {mode: gacha.description.mode || 'literal', value: gacha.description.value || ''};
    if (gacha.simpleMode === true) out.simpleMode = true;
    if (gacha.themeColor && gacha.themeColor !== 0xFFD700) out.themeColor = gacha.themeColor;
    if (gacha.openSound) out.openSound = gacha.openSound;
    if (gacha.closeSound) out.closeSound = gacha.closeSound;
    return out;
}
