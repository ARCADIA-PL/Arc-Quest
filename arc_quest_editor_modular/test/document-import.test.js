import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {state as initialState} from '../scripts/core/state.js';
import {createRegistry, importToRegistry, clearRegistry} from '../scripts/core/registry.js';
import {detectJsonType, prepareDocumentImport, commitDocumentImport} from '../scripts/core/document-import.js';
import {normalizeImportedDialogue, exportDialogueToDatapack} from '../scripts/core/dialogue-normalizer.js';
import {normalizeImportedTrade, exportTradeToDatapack} from '../scripts/core/trade-normalizer.js';
import {importJson, importToLibrary} from '../scripts/app/import-export.js';

const fixtures = JSON.parse(readFileSync(new URL('../../src/test/resources/editor/condition-contract.json', import.meta.url), 'utf8'));
const newState = () => ({...structuredClone(initialState), registry: createRegistry()});

test('注册表把 __proto__、constructor、toString 当作普通稳定 ID', () => {
    const state = newState();
    for (const id of ['__proto__', 'constructor', 'toString']) {
        const source = {id, nodes: []};
        importToRegistry(state, source, 'dialogue');
        source.nodes.push('mutated');
        assert.equal(state.registry.dialogues[id].id, id);
        assert.deepEqual(state.registry.dialogues[id].nodes, []);
    }
    assert.equal(Object.getPrototypeOf(state.registry.dialogues), null);
    assert.equal(Object.keys(state.registry.dialogues).length, 3);
    clearRegistry(state, 'dialogues');
    assert.equal(Object.getPrototypeOf(state.registry.dialogues), null);
    assert.deepEqual(Object.keys(state.registry.dialogues), []);
    assert.throws(() => clearRegistry(state, '__proto__'), /未知/);
    state.registry.dialogues = {};
    importToRegistry(state, {id: '__proto__', nodes: []}, 'dialogue');
    assert.equal(Object.getPrototypeOf(state.registry.dialogues), Object.prototype);
    assert.equal(Object.hasOwn(state.registry.dialogues, '__proto__'), true);
});

test('对话条件文本与交易条目支持原型同名 Map 键', () => {
    const dialogue = structuredClone(fixtures.dialogue);
    dialogue.nodes[0].conditionalTexts = JSON.parse('{"__proto__":{"text":{"value":"hello"}}}');
    const dialogueOut = exportDialogueToDatapack(normalizeImportedDialogue(dialogue));
    assert.equal(dialogueOut.nodes[0].conditionalTexts.__proto__.text.value, 'hello');
    const trade = structuredClone(fixtures.trade);
    trade.entries = JSON.parse('{"__proto__":{"entryId":"constructor","rewards":[]}}');
    assert.equal(exportTradeToDatapack(normalizeImportedTrade(trade)).entries.__proto__.entryId, 'constructor');
});

test('六类文件准备时不改状态，提交后编辑文档与库快照隔离', () => {
    for (const [mode, fixture] of Object.entries(fixtures)) {
        const state = newState(); state.mode = mode;
        const before = JSON.stringify(state);
        const prepared = prepareDocumentImport(state, fixture, `${mode}.json`);
        assert.equal(JSON.stringify(state), before);
        commitDocumentImport(state, prepared);
        assert.equal(state[mode].meta.file, `${mode}.json`);
        assert.equal(state[mode].meta.dirty, false);
        const registryBefore = JSON.stringify(state.registry);
        state[mode].q.id = 'changed';
        assert.equal(JSON.stringify(state.registry), registryBefore);
    }
});

test('失败导入保留当前文档、注册表和未保存状态', () => {
    const state = newState(); state.quest.meta.dirty = true;
    const before = JSON.stringify(state);
    const malformed = structuredClone(fixtures.dialogue);
    malformed.nodes[0].choices[0].conditions = {};
    assert.throws(() => prepareDocumentImport(state, malformed, 'bad.json'), /必须是数组/);
    assert.throws(() => prepareDocumentImport(state, {unrelated: true}, 'unknown.json'), /无法识别/);
    assert.equal(JSON.stringify(state), before);
});

test('模式已切换时只入库；GuideCategory 和不带费用的 Gacha 仍能识别入库', () => {
    const state = newState();
    const current = state.quest.q;
    commitDocumentImport(state, prepareDocumentImport(state, fixtures.dialogue, 'd.json', 'dialogue'));
    assert.equal(state.quest.q, current);
    assert.ok(state.registry.dialogues[fixtures.dialogue.id]);
    const gacha = structuredClone(fixtures.gacha); delete gacha.drawCosts;
    assert.equal(detectJsonType(gacha), 'gacha');
    commitDocumentImport(state, prepareDocumentImport(state, gacha, 'g.json', null));
    assert.ok(state.registry.gachas[gacha.shopId]);
    const category = {id: 'arc_quest:category', displayName: {value: 'Category'}, iconTexture: ''};
    state.mode = 'guide';
    commitDocumentImport(state, prepareDocumentImport(state, category, 'category.json'));
    assert.equal(state.guide.kind, 'guideCategory');
    assert.ok(state.registry.guides[category.id]);
});

class FileReaderFixture {
    static instances = [];
    constructor() {FileReaderFixture.instances.push(this);}
    readAsText(file) {this.file = file;}
    abort() {this.aborted = true; this.onabort?.();}
    complete(value) {this.result = JSON.stringify(value); this.onload();}
}

test('乱序读取不能覆盖较新的导入；读取失败不提交；库导入包含 Gacha/Guide', t => {
    const previousReader = globalThis.FileReader;
    globalThis.FileReader = FileReaderFixture;
    t.after(() => {
        if (previousReader === undefined) delete globalThis.FileReader;
        else globalThis.FileReader = previousReader;
    });
    const state = newState();
    let renders = 0;
    const render = () => renders++;
    importJson(state, render, {}, {name: 'old.json', size: 100});
    const old = FileReaderFixture.instances.at(-1);
    importJson(state, render, {}, {name: 'new.json', size: 100});
    const latest = FileReaderFixture.instances.at(-1);
    latest.complete(fixtures.quest);
    old.complete({...fixtures.quest, id: 'obsolete'});
    assert.equal(old.aborted, true);
    assert.equal(state.quest.q.id, fixtures.quest.id);
    assert.equal(renders, 1);
    importJson(state, render, {}, {name: 'unreadable.json', size: 100});
    FileReaderFixture.instances.at(-1).onerror();
    assert.equal(state.quest.meta.file, 'new.json');
    for (const type of ['gacha', 'guide']) {
        importToLibrary(state, render, {}, {name: `${type}.json`, size: 100});
        FileReaderFixture.instances.at(-1).complete(fixtures[type]);
    }
    assert.ok(state.registry.gachas[fixtures.gacha.shopId]);
    assert.ok(state.registry.guides[fixtures.guide.id]);
});
