export function normalizeImportedGacha(input) {
    const g = {...input};
    return {
        shopId: g.shopId || '',
        displayName: normalizeGachaText(g.displayName),
        description: g.description ? normalizeGachaText(g.description) : null,
        categories: (g.categories || []).map(c => ({
            categoryId: c.categoryId || '',
            displayName: normalizeGachaText(c.displayName),
            sortOrder: c.sortOrder ?? 0,
            formatting: c.formatting || ''
        })),
        openCondition: g.openCondition || null,
        themeColor: typeof g.themeColor === 'number' ? g.themeColor : 0xFFD700,
        simpleMode: g.simpleMode !== undefined ? g.simpleMode : false,
        openSound: g.openSound || '',
        closeSound: g.closeSound || '',
        drawCost: normalizeGachaOffer(g.drawCost),
        maxDraws: g.maxDraws ?? -1,
        cooldownType: g.cooldownType || 'NONE',
        cooldownValue: g.cooldownValue ?? 0,
        resetTimeTicks: g.resetTimeTicks ?? 0,
        rarities: (g.rarities || []).map(r => ({
            rarity: r.rarity || 'RARE',
            color: r.color ?? 0xFFFFFF,
            drawSuccessSound: r.drawSuccessSound || ''
        })),
        pity: g.pity ? {
            threshold: g.pity.threshold ?? 10,
            targetRarity: g.pity.targetRarity || 'LEGENDARY',
            guaranteedItemId: g.pity.guaranteedItemId || '',
            resetOnEarlyTrigger: g.pity.resetOnEarlyTrigger !== false
        } : null,
        pools: (g.pools || []).map(p => ({
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

function cleanEmpty(obj) {
    if (obj == null) return;
    if (Array.isArray(obj)) { for (const v of obj) cleanEmpty(v); return; }
    if (typeof obj !== 'object') return;
    for (const k of Object.keys(obj)) {
        const v = obj[k];
        if (v === null || v === undefined || v === '' || (Array.isArray(v) && v.length === 0) || (typeof v === 'object' && !Array.isArray(v) && Object.keys(v).length === 0)) {
            delete obj[k];
        } else if (typeof v === 'object') cleanEmpty(v);
    }
}

export function exportGachaToDatapack(gacha) {
    const exported = JSON.parse(JSON.stringify(gacha));
    cleanEmpty(exported);
    return exported;
}
