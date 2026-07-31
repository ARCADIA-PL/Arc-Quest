export function normalizeVisualAsset(asset) {
    if (!asset || typeof asset !== 'object') return null;
    return {
        texture: asset.texture || '',
        itemId: asset.itemId || '',
        itemCount: asset.itemCount ?? 1,
        scale: asset.scale ?? 1,
        offsetX: asset.offsetX ?? 0,
        offsetY: asset.offsetY ?? 0,
        tintColor: asset.tintColor ?? 0xFFFFFFFF,
        enabled: asset.enabled !== false
    };
}

export function exportVisualAsset(asset) {
    const normalized = normalizeVisualAsset(asset);
    if (!normalized) return null;
    const out = {};
    if (normalized.texture) out.texture = normalized.texture;
    if (normalized.itemId) out.itemId = normalized.itemId;
    if (normalized.itemCount !== 1) out.itemCount = normalized.itemCount;
    if (normalized.scale !== 1) out.scale = normalized.scale;
    if (normalized.offsetX !== 0) out.offsetX = normalized.offsetX;
    if (normalized.offsetY !== 0) out.offsetY = normalized.offsetY;
    if (normalized.tintColor !== 0xFFFFFFFF) out.tintColor = normalized.tintColor;
    if (normalized.enabled === false) out.enabled = false;
    return out;
}

export function normalizeVisualAssetMap(map) {
    if (!map || typeof map !== 'object' || Array.isArray(map)) return {};
    return Object.fromEntries(Object.entries(map)
        .map(([key, asset]) => [key, normalizeVisualAsset(asset)])
        .filter(([, asset]) => asset));
}

export function exportVisualAssetMap(map) {
    if (!map || typeof map !== 'object') return undefined;
    const entries = Object.entries(map)
        .map(([key, asset]) => [key, exportVisualAsset(asset)])
        .filter(([, asset]) => asset && Object.keys(asset).length);
    return entries.length ? Object.fromEntries(entries) : undefined;
}
