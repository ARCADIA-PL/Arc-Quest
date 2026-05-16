export function setTradeByPath(target, bind, value) {
    if (bind === 'trade.shopId') {
        target.shopId = value;
        return;
    }
    if (bind === 'trade.simpleMode') {
        target.simpleMode = value === 'true';
        return;
    }
    if (bind === 'trade.themeColor') {
        target.themeColor = Number(value) || 0xE0C860;
        return;
    }
    if (bind === 'trade.openSound') {
        target.openSound = value;
        return;
    }
    if (bind === 'trade.closeSound') {
        target.closeSound = value;
        return;
    }

    if (bind.startsWith('trade.displayName.')) {
        const field = bind.split('.')[2];
        if (!target.displayName) target.displayName = {mode: 'literal', value: '', args: []};
        if (field === 'value' || field === 'mode') target.displayName[field] = value;
        return;
    }

    if (bind.startsWith('trade.description.')) {
        const field = bind.split('.')[2];
        if (!target.description) target.description = {mode: 'literal', value: '', args: []};
        if (field === 'value' || field === 'mode') target.description[field] = value;
        return;
    }

    if (bind.startsWith('trade.entries.')) {
        const parts = bind.split('.');
        const entryKey = parts[2];
        const rest = parts.slice(3);
        if (!target.entries) target.entries = {};
        if (!target.entries[entryKey]) return;

        if (rest.length === 1) {
            const field = rest[0];
            if (field === 'entryId' || field === 'category' ||
                field === 'rewardIcon' || field === 'costIcon' ||
                field === 'purchaseSuccessSound' || field === 'purchaseFailSound' ||
                field === 'cooldownSound' || field === 'limitReachedSound' || field === 'conditionFailSound') {
                target.entries[entryKey][field] = value;
            } else if (field === 'sortOrder' || field === 'maxPurchases' || field === 'resetTimeTicks' || field === 'themeColor' || field === 'cooldownValue') {
                target.entries[entryKey][field] = Number(value) || 0;
            } else if (field === 'cooldownType') {
                target.entries[entryKey].cooldownType = value;
            }
            return;
        }

        if (rest[0] === 'displayName' && rest.length === 2) {
            const entry = target.entries[entryKey];
            if (!entry.displayName) entry.displayName = {mode: 'literal', value: '', args: []};
            entry.displayName[rest[1]] = value;
            return;
        }

        if (rest[0] === 'description' && rest.length === 2) {
            const entry = target.entries[entryKey];
            if (!entry.description) entry.description = {mode: 'literal', value: '', args: []};
            entry.description[rest[1]] = value;
            return;
        }

        if (rest[0] === 'costs' || rest[0] === 'rewards') {
            const offerList = rest[0];
            const oi = Number(rest[1]);
            const field = rest[2];
            const offers = target.entries[entryKey][offerList];
            if (!offers || !offers[oi]) return;
            if (field === 'type') {
                const oldType = offers[oi].type;
                offers[oi] = {...offers[oi]};
                offers[oi].type = value;
                if (value !== 'composite' && oldType === 'composite') {
                    delete offers[oi].offers;
                }
            } else if (['itemId', 'command', 'effectId', 'flagName', 'nbt', 'executeAs'].includes(field)) {
                offers[oi][field] = value;
            } else if (['count', 'duration', 'amplifier'].includes(field)) {
                offers[oi][field] = Number(value) || 0;
            }
            return;
        }
    }

    if (bind.startsWith('trade.categories.')) {
        const parts = bind.split('.');
        const ci = Number(parts[2]);
        if (!target.categories || !target.categories[ci]) return;
        const rest = parts.slice(3);
        if (rest[0] === 'categoryId' || rest[0] === 'formatting') {
            target.categories[ci][rest[0]] = value;
        } else if (rest[0] === 'sortOrder') {
            target.categories[ci].sortOrder = Number(value) || 0;
        } else if (rest[0] === 'displayName' && rest.length === 2) {
            if (!target.categories[ci].displayName) target.categories[ci].displayName = {mode: 'literal', value: '', args: []};
            target.categories[ci].displayName[rest[1]] = value;
        }
        return;
    }
}
