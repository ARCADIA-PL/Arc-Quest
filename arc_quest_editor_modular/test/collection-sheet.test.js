import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {normalizeImportedQuest} from '../scripts/core/import-normalizer.js';
import {exportQuestToDatapack} from '../scripts/core/export-normalizer.js';
import {setByPath} from '../scripts/core/quest-shape.js';
import {validateQuest} from '../scripts/core/validators.js';
import {applyCollectionEditorAction, validateCollectionSheets} from '../scripts/core/collection-sheet.js';
import {renderCollectionEntryWorkspace, renderCollectionSheetEditor} from '../scripts/editors/collection-sheet-editor.js';
import {bindEditorInputs} from '../scripts/renderers/bindings/editor-inputs.js';
import {handleClickPrelude, handleNonDeleteButtonAction} from '../scripts/renderers/bindings/editor-click-actions.js';
import {esc, buildReferenceIndex} from '../scripts/core/utils.js';

const fixture = JSON.parse(readFileSync(new URL('./fixtures/collection-quest.json', import.meta.url), 'utf8'));
const fresh = () => normalizeImportedQuest(fixture);
const state = q => ({quest: {q, ui: {sel: {t:'phase',pi:0}}, meta:{dirty:false}}});
const field = (label, bind, value, type='text') => `<label>${esc(label)}<input type="${type}" data-b="${esc(bind)}" value="${esc(value)}"></label>`;
const area = (label, bind, value) => `<label>${esc(label)}<textarea data-b="${esc(bind)}">${esc(value)}</textarea></label>`;

test('图鉴真实阶段、长期规则、绑定和Guide配图经过多次导入导出完整保留', () => {
    const q = fresh(), output = exportQuestToDatapack(q);
    assert.deepEqual(output.collectionConfig, fixture.collectionConfig);
    assert.deepEqual(output.phases.map(p => p.collectionSheet), fixture.phases.map(p => p.collectionSheet));
    assert.equal(output.phases.length, 2);
    assert.deepEqual(exportQuestToDatapack(normalizeImportedQuest(output)), output);
    output.collectionConfig.entries[0].content[0].media.texture = 'changed';
    assert.equal(q.collectionConfig.entries[0].content[0].media.texture, fixture.collectionConfig.entries[0].content[0].media.texture);
});

test('旧Collection分类规则、顶层与条目奖励节点使用Java字段无损导出', () => {
    const legacy = structuredClone(fixture);
    delete legacy.collectionConfig.entries; delete legacy.collectionConfig.entryIds;
    legacy.phases.forEach(p => delete p.collectionSheet);
    const node = {nodeId:'arc_quest:reward',scope:'PHASE',scopeRefId:'fieldwork',grantMode:'MANUAL',completionRules:[{type:'all_entries_complete',value:1}],rewards:[{type:'item',itemId:'minecraft:diamond',count:1}]};
    legacy.collectionConfig.rewardNodes = [node];
    legacy.collectionConfig.categories[0].rewardNodes = [node];
    legacy.collectionConfig.categories[0].completionRules = [{type:'completed_entry_count',value:2}];
    legacy.phases[0].collectionEntryConfig = {categoryId:'mobs',visibilityMode:'VISIBLE_BY_DEFAULT',countingMode:'BINARY',completionTarget:1,rewardNodes:[node]};
    const output = exportQuestToDatapack(normalizeImportedQuest(legacy));
    assert.deepEqual(output.collectionConfig, legacy.collectionConfig);
    assert.deepEqual(output.phases[0].collectionEntryConfig, legacy.phases[0].collectionEntryConfig);
    assert.equal(output.phases[0].collectionSheet, undefined);
});

test('作者可添加多个稳定条目、内容块和绑定而不生成额外Phase', () => {
    const q = fresh(), beforePhases = q.phases.length;
    const s = state(q);
    for (const action of ['add-entry','add-entry','add-content:1','add-content:1','add-research:1','add-binding:0']) {
        assert.equal(handleNonDeleteButtonAction({dataset:{collectionAction:action}}, s), true);
    }
    assert.equal(q.phases.length, beforePhases);
    assert.equal(new Set(q.collectionConfig.entries.map(e => e.entryId)).size, 3);
    assert.equal(new Set(q.collectionConfig.entries[1].content.map(b => b.blockId)).size, 2);
    assert.equal(new Set(q.phases[0].collectionSheet.bindings.map(b => b.bindingId)).size, 2);
});

test('Entry与研究步骤改名同步任务引用和图文解锁引用', () => {
    const q = fresh();
    setByPath(q, 'q.ce.0.entryId', 'arc_quest:renamed_zombie', 'text');
    assert.ok(q.phases.every(p => p.collectionSheet.bindings[0].entryId === 'arc_quest:renamed_zombie'));
    q.collectionConfig.entries[0].content[0].reveal = 'RESEARCH_STEP';
    q.collectionConfig.entries[0].content[0].revealStepId = 'defeat_five';
    setByPath(q, 'q.ce.0.researchObjectives.0.id', 'defeat_ten', 'text');
    assert.equal(q.phases[1].collectionSheet.bindings[0].recordRequirements[0].stepId, 'defeat_ten');
    assert.equal(q.collectionConfig.entries[0].content[0].revealStepId, 'defeat_ten');
});

test('普通Objective编辑器修改目标ID后保留Collection绑定', () => {
    const s = state(fresh()), mid = {};
    bindEditorInputs(mid, s, () => {}, setByPath);
    mid.onchange({target:{dataset:{b:'ob.0.0.id'},type:'text',value:'kill_again',classList:{toggle(){}}}});
    assert.deepEqual(s.quest.q.phases[0].collectionSheet.bindings[0].objectiveIds, ['kill_again']);
    assert.equal(s.quest.meta.dirty, true);
});

test('图鉴物品Icon独立于生物主体且实际表单绑定支持裁切图片和尺寸', () => {
    const q = fresh();
    setByPath(q, 'q.ce.0.icon.type', 'arc_quest:item', 'select-one');
    setByPath(q, 'q.ce.0.icon.item', 'minecraft:diamond', 'text');
    setByPath(q, 'q.ce.0.content.0.media.height', '120', 'number');
    setByPath(q, 'q.ce.0.content.0.zoomable', 'false', 'select-one');
    assert.equal(q.collectionConfig.entries[0].subjectId, 'minecraft:zombie');
    assert.deepEqual(exportQuestToDatapack(q).collectionConfig.entries[0].icon, {type:'arc_quest:item',item:'minecraft:diamond'});
    assert.equal(q.collectionConfig.entries[0].content[0].media.height, 120);
    assert.equal(q.collectionConfig.entries[0].content[0].zoomable, false);
});

test('记录型章节无需伪造Objective，配额按不同Entry且排除可选条目', () => {
    const q = fresh(), s = state(q);
    validateQuest(s);
    assert.deepEqual(s.quest.diag.filter(d => d.lvl === 'err'), []);
    assert.ok(!s.quest.diag.some(d => d.msg.includes('没有 objective')));
    const sheet = q.phases[0].collectionSheet;
    sheet.bindings.push({...structuredClone(sheet.bindings[0]),bindingId:'second'});
    sheet.requiredCount = 2;
    assert.ok(validateCollectionSheets(q).some(d => d.path.endsWith('.requiredCount')));
    sheet.countDistinctEntries = false;
    assert.ok(!validateCollectionSheets(q).some(d => d.path.endsWith('.requiredCount')));
    sheet.bindings[1].optional = true;
    assert.ok(validateCollectionSheets(q).some(d => d.path.endsWith('.requiredCount')));
});

test('断开的条目与Objective引用、新发现误用研究、非法配图逐字段报错', () => {
    const q = fresh(), binding = q.phases[0].collectionSheet.bindings[0];
    binding.entryId = 'arc_quest:missing'; binding.objectiveIds = ['missing'];
    binding.recordPolicy = 'NEW_DISCOVERIES'; binding.recordRequirements = [{type:'RESEARCH_COMPLETE'}];
    q.collectionConfig.entries[0].content[0].media.texture = 'C:/private/image.png';
    const errors = validateCollectionSheets(q).filter(d => d.lvl === 'err');
    for (const suffix of ['.entryId','.objectiveIds','.recordRequirements[0].type','.media.texture']) assert.ok(errors.some(d => d.path.endsWith(suffix)), suffix);
});

test('重复任务长期记录直接领奖风险提供诊断，本轮行动绑定不误报', () => {
    const q = fresh(); q.repeatable = true;
    const warnings = validateCollectionSheets(q).filter(d => d.lvl === 'warn');
    assert.equal(warnings.filter(d => d.msg.includes('重复任务')).length, 1);
    assert.ok(warnings[0].path.includes('phases[1]'));
});

test('详情预览选择与收纳只修改UI状态，表单不会泄露HTML或声称模拟游戏进度', () => {
    const s = state(fresh());
    let rerenders = 0;
    const click = dataset => ({target:{closest(selector){return selector.includes('collapse') ? (dataset.collectionPreviewCollapse != null ? {dataset} : null) : (dataset.collectionPreview != null ? {dataset} : null);}}});
    handleClickPrelude(click({collectionPreviewCollapse:'true'}), {}, s, () => rerenders++);
    assert.equal(s.quest.ui.collectionPreviewCollapsed, true);
    handleClickPrelude(click({collectionPreview:'0'}), {}, s, () => rerenders++);
    assert.equal(s.quest.ui.collectionPreviewCollapsed, false);
    assert.equal(s.quest.meta.dirty, false);
    assert.equal(rerenders, 2);
    s.quest.q.collectionConfig.entries[0].displayName.value = '<img src=x onerror=alert(1)>';
    const html = renderCollectionEntryWorkspace(s, s.quest.q, field, area);
    assert.ok(!html.includes('<img src=x'));
    assert.ok(html.includes('&lt;img'));
    assert.ok(html.includes('不模拟服务端进度'));
    assert.ok(html.includes('q.ce.0.content.0.media.width'));
    assert.ok(renderCollectionSheetEditor(s.quest.q, 0, field, area).includes('ph.0.cs.bindings.0.objectiveIds'));
});

test('编辑器不静默丢弃未知扩展配置，删除定义保留引用供错误诊断', () => {
    const q = fresh(); q.collectionConfig.entries[0].customExtension = {nested:[false,0,null]};
    assert.deepEqual(exportQuestToDatapack(q).collectionConfig.entries[0].customExtension, {nested:[false,0,null]});
    applyCollectionEditorAction(q, 'delete-entry:0');
    assert.equal(q.phases[0].collectionSheet.bindings[0].entryId, 'arc_quest:zombie');
    assert.ok(validateCollectionSheets(q).some(d => d.path.endsWith('.entryId')));
});

test('引用索引识别真实阶段、Entry及本轮Objective之间的关系', () => {
    const q = fresh(), references = buildReferenceIndex(q);
    assert.deepEqual(references.unresolved, []);
    assert.equal(references.incoming.get('entry:arc_quest:zombie').length, 2);
    assert.equal(references.incoming.get('objective:fieldwork:kill_zombie').length, 1);
    q.phases[0].collectionSheet.bindings[0].objectiveIds.push('missing');
    assert.ok(buildReferenceIndex(q).unresolved.some(r => r.to === 'objective:fieldwork:missing'));
});

test('部分损坏的Collection配置保留数据并报错，不在诊断阶段崩溃', () => {
    const q = fresh();
    q.collectionConfig.entries.push(null);
    q.phases[0].collectionSheet.bindings.push(null);
    q.collectionConfig.entries[0].researchObjectives[0].extraData = 'invalid JSON';
    const errors = validateCollectionSheets(q).filter(d => d.lvl === 'err');
    assert.ok(errors.some(d => d.path.endsWith('.extraData')));
    assert.ok(errors.some(d => d.path.endsWith('.entries[1]')));
    assert.ok(errors.some(d => d.path.endsWith('.bindings[1]')));
    assert.equal(exportQuestToDatapack(q).collectionConfig.entries[1], null);
});
