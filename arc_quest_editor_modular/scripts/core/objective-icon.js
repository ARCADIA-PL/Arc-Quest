import {cloneJson} from './json-document.js';

export const ICON_TYPES = Object.freeze(['arc_quest:auto', 'arc_quest:texture', 'arc_quest:none', 'arc_quest:provider']);
const INT_MAX = 2147483647;
const object = value => value !== null && typeof value === 'object' && !Array.isArray(value);
const present = (value, key) => value[key] !== undefined && value[key] !== null;

// Preserve unfamiliar data so opening and saving with an older editor is not destructive.
export function normalizeObjectiveIcon(value) {
    return value == null ? {type: 'arc_quest:auto'} : cloneJson(value);
}

export function exportObjectiveIcon(value) {
    if (value == null) return undefined;
    if (validateObjectiveIcon(value, 'icon').length) return cloneJson(value);
    if (value.type === 'arc_quest:auto') return undefined;
    const out = {type: value.type};
    const canonicalId = id => id.includes(':') ? id : `minecraft:${id}`;
    if (value.type === 'arc_quest:texture') {
        out.texture = canonicalId(value.texture);
        if (present(value, 'region')) out.region = cloneJson(value.region);
    }
    if (value.type === 'arc_quest:provider') out.provider = canonicalId(value.provider);
    return out;
}

export function isIconResourceId(value) {
    if (typeof value !== 'string' || !/^(?:[a-z0-9_.-]+:)?[a-z0-9_.-][a-z0-9/._-]*$/.test(value)) return false;
    const path = value.includes(':') ? value.split(':')[1] : value;
    return !path.split('/').some(part => part === '.' || part === '..');
}

export function validateObjectiveIcon(icon, path, diagnostics = []) {
    const error = (field, msg) => diagnostics.push({lvl: 'err', path: `${path}${field}`, msg});
    if (icon == null) return diagnostics;
    if (!object(icon)) { error('', '目标图标必须是 JSON 对象'); return diagnostics; }
    if (!ICON_TYPES.includes(icon.type)) {
        error('.type', `不支持的图标类型: ${String(icon.type)}；原始配置已保留`);
        return diagnostics;
    }
    for (const key of Object.keys(icon)) {
        if (!['type', 'texture', 'region', 'provider'].includes(key)) error(`.${key}`, `未知图标字段: ${key}`);
    }
    if (icon.type === 'arc_quest:texture') {
        if (!isIconResourceId(icon.texture)) error('.texture', '纹理必须是资源 ID，不能使用磁盘路径或网址');
        if (present(icon, 'provider')) error('.provider', '图片模式不能同时指定 provider');
        if (present(icon, 'region')) {
            const r = icon.region;
            if (!object(r)) { error('.region', '裁切区域必须是对象'); return diagnostics; }
            for (const key of Object.keys(r)) {
                if (!['x', 'y', 'width', 'height'].includes(key)) error(`.region.${key}`, `未知裁切字段: ${key}`);
            }
            for (const key of ['x', 'y', 'width', 'height']) {
                const min = key === 'x' || key === 'y' ? 0 : 1;
                if (!Number.isInteger(r[key]) || r[key] < min || r[key] > INT_MAX) {
                    error(`.region.${key}`, `${key} 必须是 ${min} 到 ${INT_MAX} 之间的整数`);
                }
            }
            if (Number.isInteger(r.x) && Number.isInteger(r.width) && r.x + r.width > INT_MAX) error('.region.width', '裁切右边界超出整数范围');
            if (Number.isInteger(r.y) && Number.isInteger(r.height) && r.y + r.height > INT_MAX) error('.region.height', '裁切下边界超出整数范围');
        }
    } else if (icon.type === 'arc_quest:provider') {
        if (!isIconResourceId(icon.provider)) error('.provider', 'provider 必须是合法资源 ID；无需已安装该 provider');
        for (const key of ['texture', 'region']) if (present(icon, key)) error(`.${key}`, 'provider 模式不能包含图片或裁切');
    } else {
        for (const key of ['texture', 'region', 'provider']) {
            if (present(icon, key)) error(`.${key}`, '自动/不显示模式不能包含其他图标配置');
        }
    }
    return diagnostics;
}

export function setObjectiveIconField(objective, parts, value, inputType) {
    if (parts.length === 0) {
        try { objective.icon = normalizeObjectiveIcon(JSON.parse(value)); }
        catch { objective.icon = value; }
        return true;
    }
    const [key, child] = parts;
    if (key === 'type' && parts.length === 1) {
        if (!ICON_TYPES.includes(value)) return false;
        if (objective.icon?.type === value) return true;
        // Changing mode is an explicit replacement, matching the Java builder contract.
        objective.icon = value === 'arc_quest:texture' ? {type: value, texture: ''}
            : value === 'arc_quest:provider' ? {type: value, provider: ''} : {type: value};
        return true;
    }
    if (!object(objective.icon)) return false;
    if (key === 'texture' && parts.length === 1 && objective.icon.type === 'arc_quest:texture') {
        objective.icon.texture = value; return true;
    }
    if (key === 'provider' && parts.length === 1 && objective.icon.type === 'arc_quest:provider') {
        objective.icon.provider = value; return true;
    }
    if (key === 'regionEnabled' && parts.length === 1 && objective.icon.type === 'arc_quest:texture') {
        if (value === true || value === 'true') objective.icon.region ||= {x: 0, y: 0, width: 16, height: 16};
        else delete objective.icon.region;
        return true;
    }
    if (key === 'region' && parts.length === 2 && objective.icon.type === 'arc_quest:texture'
        && ['x', 'y', 'width', 'height'].includes(child)) {
        if (!object(objective.icon.region)) objective.icon.region = {x: 0, y: 0, width: 16, height: 16};
        objective.icon.region[child] = value === '' ? null : (inputType === 'number' ? Number(value) : value);
        return true;
    }
    return false;
}
