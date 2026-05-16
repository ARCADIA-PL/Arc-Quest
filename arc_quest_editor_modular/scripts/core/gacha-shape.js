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

    if (bind.startsWith('gacha.drawCost.')) {
        const field = bind.split('.')[2];
        if (!target.drawCost) target.drawCost = {type: 'item', itemId: '', count: 1, nbt: '', command: '', executeAs: 'console', effectId: '', duration: 0, amplifier: 0, flagName: '', offers: []};
        if (['itemId', 'command', 'effectId', 'flagName', 'nbt', 'executeAs'].includes(field)) target.drawCost[field] = value;
        else if (['count', 'duration', 'amplifier'].includes(field)) target.drawCost[field] = Number(value) || 0;
        else if (field === 'type') target.drawCost.type = value;
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
        if (!target.pity) target.pity = {threshold: 10, targetRarity: 'LEGENDARY', resetOnEarlyTrigger: true};
        if (field === 'threshold') target.pity.threshold = Number(value) || 10;
        else if (field === 'resetOnEarlyTrigger') target.pity.resetOnEarlyTrigger = value === 'true';
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
            else if (['weight', 'minCount', 'maxCount', 'sortOrder', 'themeColor'].includes(field)) item[field] = Number(value) || 0;
            return;
        }
        if (parts[3] === 'items' && parts.length === 4) return;
        return;
    }
}
