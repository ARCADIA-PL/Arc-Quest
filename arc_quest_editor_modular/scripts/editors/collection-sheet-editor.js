import {esc} from '../core/utils.js';
import {renderObjectiveIconEditor} from './objective-icon-editor.js';
import {COLLECTION_OBJECTIVE_TYPES, COLLECTION_RECORD_TYPES, COLLECTION_SUBJECTS} from '../core/collection-sheet.js';

const list = value => Array.isArray(value) ? value : [];
const textValue = value => typeof value === 'string' ? value : value?.value || '';
function select(label, bind, value, options) {
    if (value && !options.some(o => (typeof o === 'string' ? o : o.value) === value)) options = [{value, label:value}, ...options];
    return `<div class="f"><label>${esc(label)}</label><select data-b="${esc(bind)}">${options.map(o => {
        const id = typeof o === 'string' ? o : o.value, name = typeof o === 'string' ? o : o.label;
        return `<option value="${esc(id)}" ${value === id ? 'selected' : ''}>${esc(name)}</option>`;
    }).join('')}</select></div>`;
}
const boolean = (label, bind, value) => select(label, bind, String(!!value), [{value:'false',label:'否'},{value:'true',label:'是'}]);
const button = (label, action, danger = false) => `<button type="button" data-collection-action="${esc(action)}" ${danger ? 'class="danger"' : ''}>${esc(label)}</button>`;
function textFields(label, base, value, field, area) {
    return `<div class="row">${select(`${label}文本类型`, `${base}.mode`, value?.mode || 'literal', ['literal','translatable'])}${field(label, `${base}.value`, textValue(value))}</div>`;
}
function recordObjectives(entry, index, kind, field, area) {
    const short = kind === 'discoveryObjectives' ? 'discovery' : 'research', label = short === 'discovery' ? '发现规则（任一满足）' : '长期研究（必需步骤全部满足）';
    return `<details class="collection-editor-section"><summary>${label} · ${list(entry[kind]).length}</summary>
        <p class="small">长期记录与本轮任务计数分开。稳定步骤 ID 可用于任务要求或资料解锁。获取、制作、提交对应不同真实行为。</p>
        ${list(entry[kind]).map((o, oi) => {
            const base = `q.ce.${index}.${kind}.${oi}`;
            const type = String(o.type || 'COLLECT').replace(/^arc_quest:/, '').toUpperCase();
            const types = COLLECTION_OBJECTIVE_TYPES.map(id => ({value:type === id ? (o.type || id) : id, label:id}));
            return `<div class="card"><div class="row">${field('步骤 ID', `${base}.id`, o.id || '')}${select('检测类型', `${base}.type`, o.type || 'COLLECT', types)}${field('目标数量', `${base}.requiredCount`, o.requiredCount ?? 1, 'number')}</div>
            <div class="row">${field('目标注册 ID', `${base}.targetId`, o.targetId || '')}${field('物品 Tag（任选一种）', `${base}.itemTag`, o.itemTag || '')}${field('NPC ID', `${base}.npcId`, o.npcId || '')}</div>
            ${type === 'REACH_LOCATION' ? `<div class="row">${field('X', `${base}.x`, o.x ?? 0, 'number')}${field('Y', `${base}.y`, o.y ?? 64, 'number')}${field('Z', `${base}.z`, o.z ?? 0, 'number')}${field('检测半径', `${base}.radius`, o.radius ?? 4, 'number')}</div>` : ''}
            ${textFields('玩家可见要求', `${base}.displayText`, o.displayText, field, area)}
            ${boolean('可选步骤', `${base}.optional`, o.optional)}
            <details><summary>高级：Objective 参数</summary>${area('extraData JSON', `${base}.extraData`, JSON.stringify(o.extraData || {}, null, 2))}${renderObjectiveIconEditor(o.icon, base, field, area)}</details>
            <div class="actions">${button('删除步骤', `delete-${short}:${index}:${oi}`, true)}</div></div>`;
        }).join('')}
        <div class="actions">${button('+ 添加步骤', `add-${short}:${index}`)}</div></details>`;
}

function contentEditor(entry, index, field, area) {
    return `<details class="collection-editor-section"><summary>Guide 式图文资料 · ${list(entry.content).length}</summary>
        ${list(entry.content).map((block, bi) => {
            const base = `q.ce.${index}.content.${bi}`;
            return `<div class="card"><div class="row">${field('内容块稳定 ID', `${base}.blockId`, block.blockId || '')}${select('公开时机', `${base}.reveal`, block.reveal || 'DISCOVERED', ['ALWAYS','DISCOVERED','RESEARCH_COMPLETE','RESEARCH_STEP'])}</div>
            ${block.reveal === 'RESEARCH_STEP' ? select('解锁步骤', `${base}.revealStepId`, block.revealStepId || '', [{value:'',label:'请选择研究步骤'}, ...list(entry.researchObjectives).map(o => ({value:o.id,label:`${o.id} · ${textValue(o.displayText)}`}))]) : ''}
            ${select('正文文本类型', `${base}.text.mode`, block.text?.mode || 'literal', ['literal','translatable'])}
            ${area('正文（允许多行）', `${base}.text.value`, textValue(block.text))}
            <div class="row">${select('媒体类型', `${base}.media.type`, block.media?.type || 'none', ['none','image'])}${field('图片纹理资源', `${base}.media.texture`, block.media?.texture || '')}</div>
            <div class="row">${field('显示宽度', `${base}.media.width`, block.media?.width ?? 180, 'number')}${field('显示高度', `${base}.media.height`, block.media?.height ?? 90, 'number')}${select('适应方式', `${base}.fit`, block.fit || 'CONTAIN', [{value:'CONTAIN',label:'完整显示 · 保持比例'},{value:'COVER',label:'填满区域 · 允许裁切'}])}</div>
            ${textFields('图片说明', `${base}.caption`, block.caption, field, area)}${boolean('允许点击放大', `${base}.zoomable`, block.zoomable !== false)}
            <p class="small">纹理示例 my_pack:textures/collection/zombie.png，对应资源包 assets/my_pack/textures/collection/zombie.png。</p>
            <div class="actions">${button('删除图文块', `delete-content:${index}:${bi}`, true)}</div></div>`;
        }).join('')}<div class="actions">${button('+ 添加图文块', `add-content:${index}`)}</div></details>`;
}

function preview(state, entries) {
    if (!entries.length) return '';
    const selected = Math.min(state.quest.ui.collectionPreviewEntry || 0, entries.length - 1), entry = entries[selected];
    const collapsed = !!state.quest.ui.collectionPreviewCollapsed;
    return `<details class="collection-editor-section"><summary>任务面板结构预览 · 不模拟服务端进度</summary>
        <p class="small">目录主状态代表本任务要求；长期发现/研究状态在详情另行显示。点击条目仅切换详情，不改变追踪。</p>
        <div class="collection-author-preview"><div class="collection-author-gallery">${entries.map((e, i) => `<button type="button" class="collection-author-specimen ${i === selected ? 'selected' : ''}" data-collection-preview="${i}"><span>${esc(e.subjectKind === 'ENTITY' ? '二维头像' : e.subjectKind === 'ITEM' ? '物品图标' : '自定义图标')}</span><strong>${esc(textValue(e.displayName) || e.entryId)}</strong><small>本任务要求</small></button>`).join('')}</div>
        ${collapsed ? '' : `<aside class="collection-author-detail"><strong>${esc(textValue(entry.displayName) || entry.entryId)}</strong><p>${esc(textValue(entry.description))}</p><small>图鉴记录：独立长期状态</small><hr><p>本任务要求：由阶段条目绑定提供</p>${list(entry.content).map(b => `<p>${esc(textValue(b.text))}</p>${b.media?.texture ? `<div class="collection-author-media"><span>Guide 配图 · ${esc(b.fit || 'CONTAIN')}</span><code>${esc(b.media.texture)}</code></div><small>${esc(textValue(b.caption))}</small>` : ''}`).join('')}<p class="small">追踪此条目 · JEI 物品查询在游戏内启用</p></aside>`}
        </div><div class="actions"><button type="button" data-collection-preview-collapse="${collapsed ? 'false' : 'true'}">${collapsed ? '展开详情' : '收起详情'}</button></div></details>`;
}

export function renderCollectionEntryWorkspace(state, q, field, area) {
    const entries = list(q.collectionConfig?.entries), categories = list(q.collectionConfig?.categories);
    return `<h4>图鉴条目定义 · 跨真实调查章节共用</h4>
        <div class="card collection-editor-workspace"><p class="small">Entry 是长期知识对象，Binding 是本任务要求，Phase 是实际调查章节。创建条目后，到对应阶段添加条目绑定；不要为每个条目创建一个阶段。</p>
        ${area('引用其他任务已注册的共享 Entry ID（每行一个）', 'q.entryIds', list(q.collectionConfig?.entryIds).join('\n'))}
        ${preview(state, entries)}
        ${entries.map((entry, i) => `<details class="collection-editor-section"><summary>${esc(textValue(entry.displayName) || entry.entryId || `条目 ${i + 1}`)} <small>${esc(entry.entryId || '')}</small></summary>
            <div class="row">${field('条目稳定 Entry ID', `q.ce.${i}.entryId`, entry.entryId || '')}${select('分类', `q.ce.${i}.categoryId`, entry.categoryId || '', [{value:'',label:'未分类'}, ...categories.map(c => ({value:c.categoryId,label:textValue(c.displayName) || c.categoryId}))])}</div>
            ${textFields('条目名称', `q.ce.${i}.displayName`, entry.displayName, field, area)}${textFields('简介', `q.ce.${i}.description`, entry.description, field, area)}
            <div class="row">${select('主体类型', `q.ce.${i}.subjectKind`, entry.subjectKind || 'CUSTOM', COLLECTION_SUBJECTS)}${field('主体注册 ID', `q.ce.${i}.subjectId`, entry.subjectId || '')}${field('物品 Tag', `q.ce.${i}.itemTag`, entry.itemTag || '')}</div>
            <p class="small">一个 Tag 条目代表候选中任一种；若要每一种都单独收录，应为每种物品建立独立 Entry，并在阶段配置不同条目配额。</p>
            <div class="row">${select('可见规则', `q.ce.${i}.visibilityMode`, entry.visibilityMode || 'VISIBLE_BY_DEFAULT', ['VISIBLE_BY_DEFAULT','HIDDEN_BY_DEFAULT','LOCKED'])}${select('未公开时', `q.ce.${i}.hiddenPresentationMode`, entry.hiddenPresentationMode || 'FULLY_HIDDEN', [{value:'FULLY_HIDDEN',label:'隐藏整个条目'},{value:'PLACEHOLDER',label:'神秘占位 · 不泄露名称与内容'}])}${field('排序', `q.ce.${i}.sortOrder`, entry.sortOrder ?? i, 'number')}</div>
            ${renderObjectiveIconEditor(entry.icon, `q.ce.${i}`, field, area)}
            ${area('关联物品 ID（每行一个，可用于 JEI）', `q.ce.${i}.relatedItems`, list(entry.relatedItems).join('\n'))}
            ${boolean('研究仅在发现后开始计数', `q.ce.${i}.researchAfterDiscovery`, entry.researchAfterDiscovery)}
            ${recordObjectives(entry, i, 'discoveryObjectives', field, area)}${recordObjectives(entry, i, 'researchObjectives', field, area)}${contentEditor(entry, i, field, area)}
            <div class="actions">${button('删除条目定义（引用将报错）', `delete-entry:${i}`, true)}</div>
        </details>`).join('')}<div class="actions">${button('+ 添加图鉴条目', 'add-entry')}</div></div>`;
}

export function renderCollectionSheetEditor(q, pi, field, area) {
    const phase = q.phases[pi], sheet = phase.collectionSheet, entries = list(q.collectionConfig?.entries);
    if (!sheet) return `<h4>调查章节 · Collection Sheet</h4><div class="card"><p class="small">此 Phase 是真实调查章节，可以容纳多个图鉴条目；并行推进仍由阶段流程配置。</p><div class="actions">${button('为此阶段添加条目目标板', `add-sheet:${pi}`)}</div></div>`;
    const base = `ph.${pi}.cs`, entryOptions = [{value:'',label:'请选择图鉴条目'}, ...entries.map(e => ({value:e.entryId,label:`${textValue(e.displayName) || e.entryId} · ${e.entryId}`})), ...list(q.collectionConfig?.entryIds).map(id => ({value:id,label:`共享 · ${id}`}))];
    const objectives = list(phase.objectives);
    return `<h4>调查章节 · 条目目标板</h4><div class="card collection-editor-workspace">
        <div class="row">${select('条目完成门槛', `${base}.completionPolicy`, sheet.completionPolicy || 'ALL', [{value:'ALL',label:'全部必需条目'},{value:'QUOTA',label:'候选中达到配额'}])}${sheet.completionPolicy === 'QUOTA' ? field('所需条目数', `${base}.requiredCount`, sheet.requiredCount ?? 1, 'number') : '<div class="f"><label>总体进度</label><p class="small">达成数 / 必需条目数</p></div>'}${boolean('按不同 Entry 计数', `${base}.countDistinctEntries`, sheet.countDistinctEntries)}</div>
        <p class="small">配额显示达成数 / 配额，例如 3/5；候选 12 另行显示。可选条目不增加门槛。重复委托通过本阶段 Objective 实现本轮计数。</p>
        ${list(sheet.bindings).map((binding, bi) => {
            const bp = `${base}.bindings.${bi}`, entry = entries.find(e => e.entryId === binding.entryId);
            return `<div class="card"><div class="row">${field('稳定 Binding ID', `${bp}.bindingId`, binding.bindingId || '')}${select('图鉴条目', `${bp}.entryId`, binding.entryId || '', entryOptions)}</div>
            <div class="row">${select('多个要求如何组合', `${bp}.requirementMode`, binding.requirementMode || 'ALL', [{value:'ALL',label:'全部要求'},{value:'ANY',label:'任一要求'}])}${select('记录认可策略', `${bp}.recordPolicy`, binding.recordPolicy || 'EXISTING_RECORDS', [{value:'EXISTING_RECORDS',label:'认可既有长期记录'},{value:'NEW_DISCOVERIES',label:'仅接取后首次发现'}])}${boolean('可选条目', `${bp}.optional`, binding.optional)}</div>
            <div class="f"><label>本轮行动目标 · 引用当前 Phase Objective</label><select multiple size="${Math.max(2, Math.min(5, objectives.length))}" data-b="${bp}.objectiveIds">${objectives.map(o => `<option value="${esc(o.id)}" ${list(binding.objectiveIds).includes(o.id) ? 'selected' : ''}>${esc(`${o.id} · ${o.text || o.type}`)}</option>`).join('')}</select><small>可多选；目标数量、OFFER/DELIVER 提交、图标仍在上方普通 Objective 编辑器配置。</small></div>
            <p class="small">长期记录要求</p>${list(binding.recordRequirements).map((requirement, ri) => `<div class="row">${select('要求', `${bp}.recordRequirements.${ri}.type`, requirement.type, COLLECTION_RECORD_TYPES)}${requirement.type === 'RESEARCH_STEP' ? select('研究步骤', `${bp}.recordRequirements.${ri}.stepId`, requirement.stepId || '', [{value:'',label:'选择步骤'}, ...list(entry?.researchObjectives).map(o => ({value:o.id,label:o.id}))]) : '<div class="f"></div>'}${button('移除记录要求', `delete-record:${pi}:${bi}:${ri}`, true)}</div>`).join('')}
            <div class="actions">${button('+ 添加记录要求', `add-record:${pi}:${bi}`)}${button('删除条目绑定', `delete-binding:${pi}:${bi}`, true)}</div></div>`;
        }).join('')}<div class="actions">${button('+ 添加条目绑定', `add-binding:${pi}`)}${button('移除此阶段目标板', `delete-sheet:${pi}`, true)}</div></div>`;
}
