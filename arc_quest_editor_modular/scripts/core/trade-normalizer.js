export function normalizeImportedTrade(input) {
    return {
        shopId: input.shopId || '',
        displayName: normalizeTextSpec(input.displayName),
        description: input.description ? normalizeTextSpec(input.description) : null,
        categories: (input.categories || []).map(c => ({
            categoryId: c.categoryId || '',
            displayName: normalizeTextSpec(c.displayName),
            sortOrder: c.sortOrder ?? 0,
            formatting: c.formatting || ''
        })),
        entries: normalizeEntriesMap(input.entries),
        openCondition: input.openCondition || null,
        simpleMode: input.simpleMode !== undefined ? input.simpleMode : false,
        themeColor: typeof input.themeColor === 'number' ? input.themeColor : 0xE0C860,
        openSound: input.openSound || '',
        closeSound: input.closeSound || ''
    };
}

function normalizeEntriesMap(entries) {
    if (!entries || typeof entries !== 'object') return {};
    const result = {};
    for (const [key, e] of Object.entries(entries)) {
        if (!e) continue;
        result[key] = {
            entryId: e.entryId || key,
            displayName: normalizeTextSpec(e.displayName),
            description: e.description ? normalizeTextSpec(e.description) : null,
            costs: normalizeOffers(e.costs),
            rewards: normalizeOffers(e.rewards),
            category: e.category || '',
            visibleCondition: e.visibleCondition || null,
            canBuyCondition: e.canBuyCondition || null,
            purchaseResetCondition: e.purchaseResetCondition || null,
            cooldownType: e.cooldownType || 'NONE',
            cooldownValue: e.cooldownValue ?? 0,
            resetTimeTicks: e.resetTimeTicks ?? 0,
            maxPurchases: e.maxPurchases ?? -1,
            rewardIcon: e.rewardIcon || '',
            costIcon: e.costIcon || '',
            sortOrder: e.sortOrder ?? 0,
            themeColor: typeof e.themeColor === 'number' ? e.themeColor : -1,
            purchaseSuccessSound: e.purchaseSuccessSound || '',
            purchaseFailSound: e.purchaseFailSound || '',
            cooldownSound: e.cooldownSound || '',
            limitReachedSound: e.limitReachedSound || '',
            conditionFailSound: e.conditionFailSound || ''
        };
    }
    return result;
}

function normalizeOffers(offers) {
    if (!Array.isArray(offers)) return [];
    return offers.map(o => ({
        type: o.type || 'item',
        itemId: o.itemId || '',
        itemTag: o.itemTag || '',
        count: o.count ?? 1,
        nbt: o.nbt || '',
        customIcon: o.customIcon || '',
        command: o.command || '',
        executeAs: o.executeAs || 'console',
        effectId: o.effectId || '',
        duration: o.duration ?? 0,
        amplifier: o.amplifier ?? 0,
        flagName: o.flagName || '',
        offers: Array.isArray(o.offers) ? normalizeOffers(o.offers) : []
    }));
}

function normalizeTextSpec(spec) {
    if (!spec || typeof spec !== 'object') return {mode: 'literal', value: '', args: []};
    return {
        mode: spec.mode === 'translatable' ? 'translatable' : 'literal',
        value: spec.value || '',
        args: Array.isArray(spec.args) ? spec.args : []
    };
}

function exportOffer(o) {
    const out = {type: o.type || 'item'};
    if (o.type === 'item' || !o.type || o.type === 'item') {
        if (o.itemId) out.itemId = o.itemId;
        if (o.itemTag) out.itemTag = o.itemTag;
        out.count = o.count ?? 1;
        if (o.nbt) out.nbt = o.nbt;
        if (o.customIcon) out.customIcon = o.customIcon;
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
    if (o.offers?.length) out.offers = o.offers.map(exportOffer);
    return out;
}

function exportEntry(key, e) {
    const out = {
        entryId: e.entryId || key,
        costs: (e.costs || []).map(exportOffer),
        rewards: (e.rewards || []).map(exportOffer)
    };
    if (e.displayName) out.displayName = {mode: e.displayName.mode || 'translatable', value: e.displayName.value || ''};
    if (e.category) out.category = e.category;
    if (e.sortOrder > 0) out.sortOrder = e.sortOrder;
    if (e.maxPurchases !== -1) out.maxPurchases = e.maxPurchases;
    if (e.cooldownType && e.cooldownType !== 'NONE') out.cooldownType = e.cooldownType;
    if (e.cooldownValue > 0) out.cooldownValue = e.cooldownValue;
    if (e.resetTimeTicks > 0) out.resetTimeTicks = e.resetTimeTicks;
    if (e.visibleCondition) out.visibleCondition = e.visibleCondition;
    if (e.canBuyCondition) out.canBuyCondition = e.canBuyCondition;
    if (e.purchaseResetCondition) out.purchaseResetCondition = e.purchaseResetCondition;
    if (e.description) out.description = {mode: e.description.mode || 'literal', value: e.description.value || ''};
    if (e.rewardIcon) out.rewardIcon = e.rewardIcon;
    if (e.costIcon) out.costIcon = e.costIcon;
    if (e.themeColor !== -1) out.themeColor = e.themeColor;
    if (e.purchaseSuccessSound) out.purchaseSuccessSound = e.purchaseSuccessSound;
    if (e.purchaseFailSound) out.purchaseFailSound = e.purchaseFailSound;
    if (e.cooldownSound) out.cooldownSound = e.cooldownSound;
    if (e.limitReachedSound) out.limitReachedSound = e.limitReachedSound;
    if (e.conditionFailSound) out.conditionFailSound = e.conditionFailSound;
    return out;
}

export function exportTradeToDatapack(trade) {
    const out = {
        shopId: trade.shopId || '',
        entries: {}
    };
    if (trade.entries) {
        for (const [key, e] of Object.entries(trade.entries)) {
            out.entries[key] = exportEntry(key, e);
        }
    }
    if (trade.displayName) out.displayName = {mode: trade.displayName.mode || 'translatable', value: trade.displayName.value || ''};
    if (trade.categories?.length) out.categories = trade.categories.map(c => {
        const co = {categoryId: c.categoryId || ''};
        if (c.displayName) co.displayName = {mode: c.displayName.mode || 'translatable', value: c.displayName.value || ''};
        if (c.sortOrder > 0) co.sortOrder = c.sortOrder;
        if (c.formatting) co.formatting = c.formatting;
        return co;
    });
    if (trade.openCondition) out.openCondition = trade.openCondition;
    if (trade.description) out.description = {mode: trade.description.mode || 'literal', value: trade.description.value || ''};
    if (trade.simpleMode === true) out.simpleMode = true;
    if (trade.themeColor && trade.themeColor !== 0xE0C860) out.themeColor = trade.themeColor;
    if (trade.openSound) out.openSound = trade.openSound;
    if (trade.closeSound) out.closeSound = trade.closeSound;
    return out;
}
