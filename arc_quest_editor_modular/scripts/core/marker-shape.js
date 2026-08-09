export function createMarkerSpec(index = 0) {
    return {
        id: `marker_${index + 1}`,
        target: {type: 'pos', dimension: '', x: 0, y: 64, z: 0, entityType: '', npcId: '', searchRadius: 32, structureTag: '', useSurfaceY: false, resolverId: '', args: {}},
        activateWhen: null,
        deactivateWhen: null,
        markerType: 'QUEST_OBJECTIVE',
        priority: 0,
        maxDistance: 256,
        refreshTicks: 20,
        trackMovingEntity: true,
        oneShot: false,
        trigger: 'CONTINUOUS',
        durationTicks: 100,
        styleHints: {}
    };
}

export function normalizeMarkers(markers) {
    if (!Array.isArray(markers)) return [];
    markers.forEach((marker, index) => {
        const defaults = createMarkerSpec(index);
        marker.id ||= defaults.id;
        marker.target ||= {...defaults.target};
        Object.entries(defaults.target).forEach(([key, value]) => {
            if (key === 'y' && marker.target.type === 'structure_nearest') return;
            marker.target[key] ??= value;
        });
        marker.markerType ||= defaults.markerType;
        marker.priority ??= 0;
        marker.maxDistance ??= 256;
        marker.refreshTicks ??= 20;
        marker.trackMovingEntity ??= true;
        marker.oneShot ??= false;
        marker.trigger ||= 'CONTINUOUS';
        marker.durationTicks ??= 100;
        marker.styleHints ||= {};
    });
    return markers;
}

export function resolveMarkerList(quest, prefix) {
    if (prefix === 'q.relatedMarks') return quest.relatedMarks ||= [];
    const parts = prefix.split('.');
    if (parts[0] === 'ph') return quest.phases[+parts[1]].relatedMarks ||= [];
    if (parts[0] === 'ob') return quest.phases[+parts[1]].objectives[+parts[2]].relatedMarks ||= [];
    return null;
}

export function setMarkerField(markers, pathParts, value, inputType) {
    const marker = markers?.[+pathParts[0]];
    if (!marker) return false;
    const field = pathParts[1];
    if (field === 'target') {
        const targetField = pathParts[2];
        marker.target ||= createMarkerSpec().target;
        if (targetField === 'args') marker.target.args = parseJson(value, {});
        else if (targetField === 'type') {
            marker.target.type = value;
            if (value === 'structure_nearest') {
                marker.target.y = null;
                marker.target.useSurfaceY = false;
            } else if (['pos', 'block', 'dimension_pos'].includes(value) && marker.target.y == null) {
                marker.target.y = 64;
            }
        }
        else if (targetField === 'useSurfaceY') marker.target.useSurfaceY = value === 'true';
        else if (targetField === 'y' && inputType === 'number' && value === '') marker.target.y = null;
        else marker.target[targetField] = inputType === 'number' ? Number(value || 0) : value;
        return true;
    }
    if (field === 'activateWhen' || field === 'deactivateWhen') marker[field] = parseJson(value, null);
    else if (field === 'styleHints') marker.styleHints = parseJson(value, {});
    else if (field === 'trackMovingEntity' || field === 'oneShot') marker[field] = value === 'true';
    else marker[field] = inputType === 'number' ? Number(value || 0) : value;
    return true;
}

function parseJson(value, fallback) {
    if (!String(value || '').trim()) return fallback;
    try { return JSON.parse(value); } catch { return fallback; }
}
