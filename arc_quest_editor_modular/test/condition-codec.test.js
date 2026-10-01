import {test} from 'node:test';
import assert from 'node:assert/strict';
import {cloneDocument, cloneJson, parseDocument, MAX_DOCUMENT_BYTES} from '../scripts/core/json-document.js';
import {normalizeCondition, normalizeConditionList, exportCondition, validateConditionNode,
    validateQuestCondition, validateDialogueCondition, validateEntityCondition} from '../scripts/core/condition-codec.js';

test('条件保留附属参数、零、false、null、空容器和原型同名键', () => {
    const input = JSON.parse('{"condition":"extension:rule","value":0,"enabled":false,"data":{"empty":"","none":null,"array":[],"object":{},"__proto__":{"flag":true}}}');
    assert.deepEqual(exportCondition(normalizeCondition(input)), input);
    const issues = [];
    validateConditionNode(input, 'rule', issues);
    assert.equal(issues.length, 1);
    assert.equal(issues[0].lvl, 'warn');
    assert.equal(Object.hasOwn(input.data, '__proto__'), true);
    assert.equal({}.flag, undefined);
});

test('所有旧比较枚举转换为 Java CompareOp 支持的符号', () => {
    for (const [op, symbol] of Object.entries({EQUAL:'==', NOT_EQUAL:'!=', GREATER:'>', GREATER_OR_EQUAL:'>=', LESS:'<', LESS_OR_EQUAL:'<='})) {
        const source = {condition: 'arc_quest:variable_check', key: 'score', op, value: 0};
        assert.equal(exportCondition(source).op, symbol);
        assert.equal(source.op, op);
        assert.equal(normalizeCondition({type: 'variable', variable: 'score', compareOp: op, value: 0}).op, symbol);
    }
});

test('已知旧条件转换，未知旧格式和错误子节点拒绝导入', () => {
    assert.deepEqual(normalizeCondition({type: 'and', left: {type: 'flag_set', flag: 'ready'}, right: {type: 'always'}}), {
        condition: 'arc_quest:and', conditions: [{condition: 'arc_quest:has_flag', flag: 'ready'}, {condition: 'arc_quest:always'}]
    });
    for (const value of [false, [], 'rule', {field: true}, {type: 'unknown'}, {condition: ''},
        {condition: 'arc_quest:and', conditions: {}}, {condition: 'arc_quest:or', conditions: [null]}]) {
        assert.throws(() => normalizeCondition(value));
    }
    assert.throws(() => normalizeConditionList({condition: 'arc_quest:always'}), /必须是数组/);
    assert.throws(() => normalizeConditionList([null]), /不能为 null/);
});

test('条件校验拒绝整数溢出、错误谓词和缺失引用字段', () => {
    for (const value of [
        {condition: 'arc_quest:variable_check', key: 'v', op: 'bad', value: 2147483648},
        {condition: 'arc_quest:hold_item', itemId: 'minecraft:stone', count: 1.5, itemSource: 'bad'},
        {condition: 'arc_quest:dialogue_on_cooldown', dialogueId: 'test:d', cooldownSeconds: 2147483648},
        {condition: 'arc_quest:quest_phase', quest_id: 'test:q', phase_id: 'p'},
        {condition: 'arc_quest:not'}, {condition: 'minecraft:entity_properties', predicate: []}
    ]) {
        const issues = [];
        validateConditionNode(value, 'condition', issues);
        assert.ok(issues.some(issue => issue.lvl === 'err'), JSON.stringify(value));
    }
});

test('区分 Quest、Dialogue 与 NPC 条件运行时能力', () => {
    const hold = {condition: 'arc_quest:hold_item', itemId: 'minecraft:stone', count: 1};
    const entity = {condition: 'arc_quest:entity_nbt', nbtKey: 'id', nbtValue: ''};
    const questIssues = [], dialogueIssues = [], npcIssues = [];
    validateQuestCondition(hold, 'hold', questIssues);
    validateDialogueCondition(hold, 'hold', dialogueIssues);
    validateEntityCondition(entity, 'entity', npcIssues);
    assert.ok(questIssues.some(issue => issue.lvl === 'warn'));
    assert.deepEqual(dialogueIssues, []);
    assert.deepEqual(npcIssues, []);
});

test('JSON 边界拒绝循环、超深、过大、非有限值和不安全整数', () => {
    const cycle = {}; cycle.self = cycle;
    assert.throws(() => cloneDocument(cycle), /循环/);
    let deep = {};
    for (let i = 0; i < 66; i++) deep = {inner: deep};
    assert.throws(() => cloneDocument(deep), /嵌套/);
    assert.throws(() => cloneDocument({items: Array(100001).fill(0)}), /数量/);
    assert.throws(() => parseDocument(' '.repeat(MAX_DOCUMENT_BYTES + 1)), /8 MiB/);
    assert.throws(() => parseDocument(JSON.stringify({text: '中'.repeat(MAX_DOCUMENT_BYTES / 3)})), /8 MiB/);
    for (const invalid of [NaN, Infinity, -Infinity, Number.MAX_SAFE_INTEGER + 1, new Date(), 1n]) {
        assert.throws(() => cloneDocument({value: invalid}));
    }
    for (const input of ['[]', 'null', 'true', '12', '"text"']) assert.throws(() => parseDocument(input), /根节点/);
});

test('JSON 副本允许重复引用，并遵循 undefined 的 JSON 序列化规则', () => {
    const shared = {x: 1};
    const result = cloneJson({a: shared, b: shared, omitted: undefined, list: [undefined]});
    assert.notEqual(result.a, result.b);
    assert.notEqual(result.a, shared);
    assert.deepEqual(result, {a: {x: 1}, b: {x: 1}, list: [null]});
});
