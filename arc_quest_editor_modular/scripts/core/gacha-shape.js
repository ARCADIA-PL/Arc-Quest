export function setGachaByPath(target, bind, value) {
    if (bind === 'gacha.shopId') { target.shopId = value; return; }
    if (bind === 'gacha.simpleMode') { target.simpleMode = value === 'true'; return; }
    if (bind === 'gacha.themeColor') { target.themeColor = Number(value) || 0xFFD700; return; }
    if (bind === 'gacha.openSound') { target.openSound = value; return; }
    if (bind === 'gacha.closeSound') { target.closeSound = value; return; }
    if (bind === 'gacha.maxDraws') { target.maxDraws = Number(value) || -1; return; }
    if (bind === 'gacha.cooldownType') { target.cooldownType = value; return; }
    if (bind === 'gacha.cooldownValue') { target.cooldownValue = Number(value) || 0; return; }
    if (bind === 'gacha.resetTimeTicks') { target.resetTimeTicks = Number(value) || 0; return; }
    if (bind === 'gacha.resetOnLimitReached') { target.resetOnLimitReached = value === 'true'; return; }
    if (bind === 'gacha.resetPityOnEarlyTrigger') { target.resetPityOnEarlyTrigger = value === 'true'; return; }
    if (['gacha.drawCooldownSound', 'gacha.drawLimitReachedSound', 'gacha.drawConditionFailSound', 'gacha.drawFailSound'].includes(bind)) {
        target[bind.split('.')[1]] = value;
        return;
    }

    if (bind.startsWith('gacha.displayName.')) {
        const field = bind.split('.')[2];
        if (!target.displayName) target.displayName = {mode: 'literal', value: '', args: []};
        if (field === 'value' || field === 'mode') target.displayName[field] = value;
        return;
    }
    if (bind.startsWith('gacha.description.')) {
        const field = bind.split('.')[2];
        if (!target.description) target.description = {mode: 'literal', value: '', args: []};
        if (field === 'value' || field === 'mode') target.description[field] = value;
        return;
    }

    if (bind.startsWith('gacha.drawCosts.')) {
        const parts = bind.split('.');
        const index = Number(parts[2]);
        const field = parts[3];
        if (!target.drawCosts?.[index]) return;
        if (['itemId', 'itemTag', 'customIcon', 'command', 'effectId', 'flagName', 'nbt', 'executeAs'].includes(field)) target.drawCosts[index][field] = value;
        else if (['count', 'duration', 'amplifier'].includes(field)) target.drawCosts[index][field] = Number(value) || 0;
        else if (field === 'type') target.drawCosts[index].type = value;
        return;
    }

    if (bind.startsWith('gacha.rarities.')) {
        const parts = bind.split('.');
        const ri = Number(parts[2]);
        if (!target.rarities || !target.rarities[ri]) return;
        target.rarities[ri][parts[3]] = parts[3] === 'color' ? (Number(value) || 0xFFFFFF) : value;
        return;
    }

    if (bind.startsWith('gacha.pity.')) {
        const field = bind.split('.')[2];
        if (!target.pity) target.pity = {threshold: 10, targetRarity: 'LEGENDARY', guaranteedItemId: '', resetOnEarlyTrigger: true, resetCooldownType: 'NONE', resetCooldownValue: 0, resetCondition: null, resetOnTrigger: true};
        if (field === 'threshold') target.pity.threshold = Number(value) || 10;
        else if (field === 'resetCooldownValue') target.pity.resetCooldownValue = Number(value) || 0;
        else if (field === 'resetOnEarlyTrigger' || field === 'resetOnTrigger') target.pity[field] = value === 'true';
        else target.pity[field] = value;
        return;
    }

    if (bind.startsWith('gacha.pools.')) {
        const parts = bind.split('.');
        const pi = Number(parts[2]);
        if (!target.pools || !target.pools[pi]) return;
        if (parts[3] === 'poolId') { target.pools[pi].poolId = value; return; }
        if (parts[3] === 'items' && parts.length >= 5) {
            const ii = Number(parts[4]);
            const field = parts[5];
            const item = target.pools[pi].items[ii];
            if (!item) return;
            if (['itemId', 'item', 'rarity', 'rewardIcon', 'drawSuccessSound'].includes(field)) item[field] = value;
            else if (field === 'countsTowardsPity') item.countsTowardsPity = value === 'true';
            else if (field === 'visibleConditionJson') item.visibleCondition = parseJson(value, null);
            else if (field === 'rewardJson') item.reward = parseJson(value, null);
            else if (field === 'weightModifiersJson') item.weightModifiers = parseJson(value, []);
            else if (['weight', 'minCount', 'maxCount', 'sortOrder', 'themeColor'].includes(field)) item[field] = Number(value) || 0;
            return;
        }
        if (parts[3] === 'items' && parts.length === 4) return;
        return;
    }
}

function parseJson(value, fallback) {
    if (!value || !String(value).trim()) return fallback;
    try { return JSON.parse(value); } catch { return fallback; }
}
