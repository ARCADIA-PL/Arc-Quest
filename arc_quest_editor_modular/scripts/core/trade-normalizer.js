export function normalizeImportedTrade(input) {
    const t = {...input};
    return {
        shopId: t.shopId || '',
        displayName: normalizeTextSpec(t.displayName),
        description: t.description ? normalizeTextSpec(t.description) : null,
        categories: (t.categories || []).map(c => ({
            categoryId: c.categoryId || '',
            displayName: normalizeTextSpec(c.displayName),
            sortOrder: c.sortOrder ?? 0,
            formatting: c.formatting || ''
        })),
        entries: normalizeEntriesMap(t.entries),
        openCondition: t.openCondition || null,
        simpleMode: t.simpleMode !== undefined ? t.simpleMode : false,
        themeColor: typeof t.themeColor === 'number' ? t.themeColor : 0xE0C860,
        openSound: t.openSound || '',
        closeSound: t.closeSound || ''
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
        count: o.count ?? 1,
        nbt: o.nbt || '',
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

function cleanEmptyFields(obj) {
    if (obj == null) return;
    if (Array.isArray(obj)) {
        for (const item of obj) cleanEmptyFields(item);
        return;
    }
    if (typeof obj !== 'object') return;
    for (const key of Object.keys(obj)) {
        const v = obj[key];
        if (v === null || v === undefined || v === '' ||
            (Array.isArray(v) && v.length === 0) ||
            (typeof v === 'object' && !Array.isArray(v) && Object.keys(v).length === 0)) {
            delete obj[key];
        } else if (typeof v === 'object') {
            cleanEmptyFields(v);
        }
    }
}

export function exportTradeToDatapack(trade) {
    const exported = JSON.parse(JSON.stringify(trade));
    cleanEmptyFields(exported);
    return exported;
}
