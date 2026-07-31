const VALID_TYPES = new Set(['item', 'command', 'effect', 'flag', 'composite']);
const VALID_COOLDOWNS = new Set(['NONE', 'SECONDS', 'GAME_DAY', 'GAME_TICK']);

export function validateTrade(trade) {
    const issues = [];
    const error = (path, msg) => issues.push({lvl: 'err', path, msg});
    if (!trade?.shopId?.trim()) error('shopId', 'shopId is required');
    if (!trade?.displayName?.value?.trim()) error('displayName', 'displayName is required');
    const entries = trade?.entries || {};
    if (!Object.keys(entries).length) issues.push({lvl:'warn', path:'entries', msg:'shop has no entries'});
    const ids = new Set();
    for (const [key, entry] of Object.entries(entries)) {
        const path = `entries.${key}`;
        if (!entry.entryId) error(`${path}.entryId`, 'entryId is required');
        else if (ids.has(entry.entryId)) error(`${path}.entryId`, 'duplicate entryId');
        ids.add(entry.entryId);
        if (!entry.rewards?.length) error(`${path}.rewards`, 'at least one reward is required');
        (entry.costs || []).forEach((offer, index) => validateOffer(offer, `${path}.costs[${index}]`, true, error));
        (entry.rewards || []).forEach((offer, index) => validateOffer(offer, `${path}.rewards[${index}]`, false, error));
        if (!VALID_COOLDOWNS.has(entry.cooldownType || 'NONE')) error(`${path}.cooldownType`, 'invalid cooldown type');
    }
    return issues;
}

function validateOffer(offer, path, isCost, error) {
    if (!VALID_TYPES.has(offer?.type)) return error(`${path}.type`, 'invalid offer type');
    if (offer.type === 'item') {
        if (!!offer.itemId === !!offer.itemTag) error(path, 'exactly one of itemId or itemTag is required');
        if (!isCost && offer.itemTag) error(`${path}.itemTag`, 'itemTag is only supported for costs');
    } else if (offer.type === 'command' && !offer.command) error(`${path}.command`, 'command is required');
    else if (offer.type === 'effect' && !offer.effectId) error(`${path}.effectId`, 'effectId is required');
    else if (offer.type === 'flag' && !offer.flagName) error(`${path}.flagName`, 'flagName is required');
    else if (offer.type === 'composite') {
        if (!offer.offers?.length) error(`${path}.offers`, 'composite offer requires children');
        (offer.offers || []).forEach((child, index) => validateOffer(child, `${path}.offers[${index}]`, isCost, error));
    }
}
