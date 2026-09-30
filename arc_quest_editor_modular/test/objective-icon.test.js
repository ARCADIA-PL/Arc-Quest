import {test} from 'node:test';
import assert from 'node:assert/strict';
import {normalizeImportedQuest} from '../scripts/core/import-normalizer.js';
import {exportQuestToDatapack} from '../scripts/core/export-normalizer.js';
import {validateQuest} from '../scripts/core/validators.js';
import {setByPath} from '../scripts/core/quest-shape.js';
import {createObjective} from '../scripts/core/factories.js';
import {cloneDocument} from '../scripts/core/json-document.js';
import {exportObjectiveIcon, normalizeObjectiveIcon, validateObjectiveIcon} from '../scripts/core/objective-icon.js';
import {renderObjectiveIconEditor} from '../scripts/editors/objective-icon-editor.js';
import {field, area} from '../scripts/renderers/center/form-fields.js';
import {normalizeObjectiveByType} from '../scripts/renderers/bindings/editor-helpers-collection.js';
import {bindEditorInputs} from '../scripts/renderers/bindings/editor-inputs.js';

const source = icon => ({id: 'example:icons', category: 'arc_quest:adventure', initialPhaseId: 'phase',
    displayName: {mode: 'literal', value: 'Icons'}, phases: [{phaseId: 'phase', objectives: [{
        id: 'collect', type: 'arc_quest:collect', targetId: 'minecraft:oak_log', requiredCount: 4,
        displayText: {mode: 'literal', value: 'Collect logs'}, ...(icon === undefined ? {} : {icon})
    }]}]});
const objective = document => document.phases[0].objectives[0];
const texture = {type: 'arc_quest:texture', texture: 'example:textures/gui/atlas.png', region: {x: 8, y: 16, width: 32, height: 24}};

test('old, null and explicit auto icons export without a default icon field', () => {
    for (const icon of [undefined, null, {type: 'arc_quest:auto'}, {type: 'arc_quest:auto', texture: null}]) {
        const q = normalizeImportedQuest(source(icon));
        assert.equal(objective(q).icon.type, 'arc_quest:auto');
        assert.equal(Object.hasOwn(objective(exportQuestToDatapack(q)), 'icon'), false);
    }
    assert.deepEqual(createObjective(0).icon, {type: 'arc_quest:auto'});
});

test('canonical namespaced item and kill objective types retain their meaning during icon editing', () => {
    for (const type of ['collect', 'craft', 'kill']) {
        const input = source(texture);
        objective(input).type = `arc_quest:${type}`;
        const q = normalizeImportedQuest(input);
        assert.equal(objective(q).type, type.toUpperCase());
        const reopened = normalizeImportedQuest(exportQuestToDatapack(q));
        assert.equal(objective(reopened).type, type.toUpperCase());
        assert.deepEqual(objective(reopened).icon, texture);
    }
});

test('none, texture region and uninstalled provider survive repeated document round trips', () => {
    for (const icon of [{type: 'arc_quest:none'}, texture, {type: 'arc_quest:provider', provider: 'uninstalled:portrait'}]) {
        const q = normalizeImportedQuest(source(icon));
        const state = {quest: {q}};
        validateQuest(state);
        assert.deepEqual(state.quest.diag.filter(d => d.path.includes('.icon')), []);
        const exported = exportQuestToDatapack(q);
        assert.deepEqual(objective(exported).icon, icon);
        assert.deepEqual(exportQuestToDatapack(normalizeImportedQuest(exported)), exported);
    }
});

test('import, copied documents and export do not share mutable region data', () => {
    const input = source(structuredClone(texture));
    const q = normalizeImportedQuest(input);
    const copy = cloneDocument(q);
    const output = exportQuestToDatapack(q);
    objective(input).icon.region.x = 100;
    objective(output).icon.region.y = 100;
    setByPath(copy, 'ob.0.0.icon.region.width', '10', 'number');
    assert.deepEqual(objective(q).icon, texture);
    assert.equal(objective(copy).icon.region.width, 10);
});

test('nested editor fields replace modes, preserve typed regions and can disable cropping', () => {
    const q = normalizeImportedQuest(source());
    const set = (path, value, type = 'text') => setByPath(q, `ob.0.0.icon.${path}`, value, type);
    set('type', 'arc_quest:texture');
    set('texture', 'example:textures/gui/cow.png');
    set('regionEnabled', 'true');
    set('region.x', '8', 'number');
    set('region.width', '24', 'number');
    assert.deepEqual(objective(q).icon.region, {x: 8, y: 0, width: 24, height: 16});
    assert.deepEqual(validateObjectiveIcon(objective(q).icon, 'icon'), []);
    set('regionEnabled', 'false');
    assert.equal(Object.hasOwn(objective(q).icon, 'region'), false);
    set('type', 'arc_quest:provider');
    set('provider', 'addon:icon');
    assert.deepEqual(objective(q).icon, {type: 'arc_quest:provider', provider: 'addon:icon'});
    set('type', 'arc_quest:none');
    assert.deepEqual(objective(q).icon, {type: 'arc_quest:none'});
    set('type', 'arc_quest:auto');
    assert.equal(Object.hasOwn(objective(exportQuestToDatapack(q)), 'icon'), false);
});

test('unknown icon types and their complete configuration are preserved and diagnosed', () => {
    const icon = {type: 'addon:animated', custom: {frames: ['a', 'b'], flags: [null, false]}, region: {future: 4}};
    const q = normalizeImportedQuest(source(icon));
    const state = {quest: {q}};
    validateQuest(state);
    assert.ok(state.quest.diag.some(d => d.lvl === 'err' && d.path === 'phases[0].objectives[0].icon.type'));
    assert.deepEqual(objective(exportQuestToDatapack(q)).icon, icon);
    const copy = cloneDocument(q);
    objective(copy).icon.custom.frames.push('c');
    assert.deepEqual(objective(q).icon.custom.frames, ['a', 'b']);
});

test('objective type normalization retains independent icon configuration without aliasing', () => {
    const before = objective(normalizeImportedQuest(source(texture)));
    const after = normalizeObjectiveByType({...before, type: 'KILL'});
    assert.deepEqual(after.icon, texture);
    after.icon.region.x = 99;
    assert.equal(before.icon.region.x, 8);
});

test('real onchange binding distinguishes icon mode from objective type and retains configuration', () => {
    const q = normalizeImportedQuest(source(texture));
    const original = objective(q);
    original.countMode = 'unique';
    const state = {quest: {q, meta: {}, ui: {sel: {pi: 0, oi: 0}}}};
    const surface = {};
    let renders = 0;
    bindEditorInputs(surface, state, () => renders++, setByPath);
    const change = (bind, value) => surface.onchange({target: {dataset: {b: bind}, value,
        type: 'select-one', classList: {toggle() {}}}});
    change('ob.0.0.icon.type', 'arc_quest:provider');
    assert.strictEqual(objective(q), original, 'icon changes must not normalize the objective business fields');
    assert.equal(objective(q).countMode, 'unique');
    assert.equal(objective(q).type, 'COLLECT');
    change('ob.0.0.icon.provider', 'addon:portrait');
    change('ob.0.0.type', 'KILL');
    assert.equal(objective(q).type, 'KILL');
    assert.deepEqual(objective(q).icon, {type: 'arc_quest:provider', provider: 'addon:portrait'});
    assert.equal(renders, 3);
    assert.equal(state.quest.meta.dirty, true);
});

test('invalid source combinations, IDs and fractional or overflowing regions have field paths', () => {
    for (const [icon, fieldPath] of [
        [[], 'icon'], [{type: 'arc_quest:texture'}, 'icon.texture'],
        [{type: 'arc_quest:texture', texture: 'https://example.org/a.png'}, 'icon.texture'],
        [{type: 'arc_quest:texture', texture: 'c:/textures/a.png'}, 'icon.texture'],
        [{type: 'arc_quest:texture', texture: 'example:../a.png'}, 'icon.texture'],
        [{type: 'arc_quest:none', texture: 'example:a.png'}, 'icon.texture'],
        [{type: 'arc_quest:provider', provider: 'addon:p', region: {}}, 'icon.region'],
        [{...texture, region: {...texture.region, width: 1.5}}, 'icon.region.width'],
        [{...texture, region: {...texture.region, x: -1}}, 'icon.region.x'],
        [{...texture, region: {...texture.region, x: 2147483647}}, 'icon.region.width'],
        [{...texture, region: {...texture.region, height: '16'}}, 'icon.region.height'],
        [{type: 'arc_quest:auto', future: true}, 'icon.future']
    ]) {
        assert.ok(validateObjectiveIcon(icon, 'icon').some(d => d.path === fieldPath), JSON.stringify(icon));
        assert.deepEqual(exportObjectiveIcon(icon), icon, 'invalid input must not silently lose fields');
    }
});

test('partially edited JSON remains recoverable without throwing or discarding it', () => {
    const q = normalizeImportedQuest(source({type: 'addon:future'}));
    setByPath(q, 'ob.0.0.icon', '{', 'textarea');
    assert.equal(objective(q).icon, '{');
    assert.ok(validateObjectiveIcon(objective(q).icon, 'icon').length);
    setByPath(q, 'ob.0.0.icon', '{"type":"arc_quest:none"}', 'textarea');
    assert.deepEqual(objective(q).icon, {type: 'arc_quest:none'});
});

test('known valid exports use canonical resource IDs and omit irrelevant null fields', () => {
    assert.deepEqual(exportObjectiveIcon({type: 'arc_quest:texture', texture: 'textures/gui/a.png', region: null}),
        {type: 'arc_quest:texture', texture: 'minecraft:textures/gui/a.png'});
    assert.deepEqual(normalizeObjectiveIcon(null), {type: 'arc_quest:auto'});
});

test('icon form exposes texture, crop and provider bindings without claiming resource validation', () => {
    const html = renderObjectiveIconEditor(texture, 'ob.0.0', field, area);
    assert.ok(html.includes('ob.0.0.icon.texture'));
    assert.ok(html.includes('ob.0.0.icon.region.width'));
    assert.ok(html.includes('assets/example/textures/gui/atlas.png'));
    assert.match(html, /资源存在性待客户端验证/);
    const providerHtml = renderObjectiveIconEditor({type: 'arc_quest:provider', provider: 'addon:p'}, 'ob.0.0', field, area);
    assert.ok(providerHtml.includes('ob.0.0.icon.provider'));
    const unknownHtml = renderObjectiveIconEditor({type: '<script>alert(1)</script>'}, 'ob.0.0', field, area);
    assert.doesNotMatch(unknownHtml, /<script>/);
    assert.match(unknownHtml, /原始数据/);
});
