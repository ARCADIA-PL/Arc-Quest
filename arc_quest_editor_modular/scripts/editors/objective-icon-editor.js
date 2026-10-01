import {esc} from '../core/utils.js';
import {ICON_TYPES, isIconResourceId} from '../core/objective-icon.js';

export function renderObjectiveIconEditor(icon, base, field, area) {
    const value = icon ?? {type: 'arc_quest:auto'};
    const type = value?.type;
    const known = ICON_TYPES.includes(type);
    const option = (id, text) => `<option value="${id}" ${type === id ? 'selected' : ''}>${text}</option>`;
    let body = `<div class="f"><label>目标图标</label><select data-b="${base}.icon.type">
        ${option('arc_quest:auto', '自动')}${option('arc_quest:item', '原生物品图标')}${option('arc_quest:texture', '自定义图片')}${option('arc_quest:none', '不显示')}
        <optgroup label="高级">${option('arc_quest:provider', '指定 provider')}</optgroup>
        ${known ? '' : `<option selected value="${esc(type ?? '')}">不支持的配置（保留原始数据）</option>`}
        </select></div>`;
    if (!known) {
        body += `<div class="small">当前版本不支持此配置；导入、复制和导出仍保留原始数据。</div>`;
        body += area('图标原始 JSON', `${base}.icon`, typeof value === 'string' ? value : JSON.stringify(value, null, 2));
    } else if (type === 'arc_quest:item') {
        body += field('物品注册 ID', `${base}.icon.item`, value.item ?? '');
        body += '<div class="small">使用物品栏原生图标；支持原版和模组物品，例如 minecraft:diamond、your_mod:custom_item。物品存在性待客户端验证。</div>';
    } else if (type === 'arc_quest:texture') {
        body += field('纹理资源 ID', `${base}.icon.texture`, value.texture ?? '');
        if (isIconResourceId(value.texture)) {
            const [namespace, path] = value.texture.includes(':') ? value.texture.split(':') : ['minecraft', value.texture];
            body += `<div class="small">assets/${esc(namespace)}/${esc(path)}<br>路径合法，资源存在性待客户端验证。</div>`;
        } else {
            body += `<div class="small">例如 my_pack:textures/gui/objectives/cow.png；使用模组或资源包中的图片。</div>`;
        }
        const r = value.region;
        body += `<details ${r ? 'open' : ''}><summary>高级：纹理裁切（原始图片像素）</summary>
            <div class="f"><label>裁切区域</label><select data-b="${base}.icon.regionEnabled">
            <option value="false" ${r ? '' : 'selected'}>完整图片</option>
            <option value="true" ${r ? 'selected' : ''}>指定区域</option></select></div>`;
        if (r) body += `<div class="row">${field('X', `${base}.icon.region.x`, r.x ?? '', 'number')}${field('Y', `${base}.icon.region.y`, r.y ?? '', 'number')}</div>
            <div class="row">${field('宽', `${base}.icon.region.width`, r.width ?? '', 'number')}${field('高', `${base}.icon.region.height`, r.height ?? '', 'number')}</div>`;
        body += '</details>';
    } else if (type === 'arc_quest:provider') {
        body += field('Provider 资源 ID', `${base}.icon.provider`, value.provider ?? '');
        body += '<div class="small">高级扩展：合法 ID 会被保留；客户端未注册该 provider 时不显示图标。</div>';
    } else {
        body += `<div class="small">${type === 'arc_quest:none' ? '明确关闭图标，不预留空图标列。' : '使用真实目标的自动图标；支持的 Tag 目标自动轮换候选。无可用图标时不留空槽。'}</div>`;
    }
    return `<div class="card" style="margin-top:10px">${body}</div>`;
}
