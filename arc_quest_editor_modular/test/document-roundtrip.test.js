import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {normalizeImportedQuest} from '../scripts/core/import-normalizer.js';
import {exportQuestToDatapack} from '../scripts/core/export-normalizer.js';
import {normalizeImportedDialogue, exportDialogueToDatapack} from '../scripts/core/dialogue-normalizer.js';
import {normalizeImportedNpc, exportNpcToDatapack} from '../scripts/core/npc-normalizer.js';
import {normalizeImportedTrade, exportTradeToDatapack} from '../scripts/core/trade-normalizer.js';
import {normalizeImportedGacha, exportGachaToDatapack} from '../scripts/core/gacha-normalizer.js';
import {normalizeImportedGuide, exportGuideToDatapack} from '../scripts/core/guide-normalizer.js';
import {validateQuest} from '../scripts/core/validators.js';
import {validateDialogue} from '../scripts/core/dialogue-validators.js';
import {validateNpc} from '../scripts/core/npc-validators.js';
import {validateTrade} from '../scripts/core/trade-validators.js';
import {validateGacha} from '../scripts/core/gacha-validators.js';
import {validateGuide} from '../scripts/core/guide-validators.js';

const fixtures = JSON.parse(readFileSync(new URL('../../src/test/resources/editor/condition-contract.json', import.meta.url), 'utf8'));
const json = value => JSON.parse(JSON.stringify(value));
function freeze(value) {
    if (value && typeof value === 'object') {
        Object.values(value).forEach(freeze);
        Object.freeze(value);
    }
    return value;
}
function conditionPaths(value, path = [], output = []) {
    if (value && typeof value === 'object') {
        if (typeof value.condition === 'string') output.push([path, value]);
        for (const [key, child] of Object.entries(value)) conditionPaths(child, [...path, key], output);
    }
    return output;
}
const codecs = [
    ['quest', normalizeImportedQuest, exportQuestToDatapack, q => {const state = {quest: {q}}; validateQuest(state); return state.quest.diag;}],
    ['dialogue', normalizeImportedDialogue, exportDialogueToDatapack, validateDialogue],
    ['npc', normalizeImportedNpc, exportNpcToDatapack, validateNpc],
    ['trade', normalizeImportedTrade, exportTradeToDatapack, validateTrade],
    ['gacha', normalizeImportedGacha, exportGachaToDatapack, validateGacha],
    ['guide', normalizeImportedGuide, exportGuideToDatapack, validateGuide]
];

for (const [kind, normalize, encode, validate] of codecs) {
    test(`${kind}: Java 共用夹具的所有条件字段完整往返，连续导出稳定`, () => {
        const input = freeze(json(fixtures[kind]));
        const document = normalize(input);
        const errors = validate(document).filter(issue => issue.lvl === 'err');
        assert.deepEqual(errors, []);
        const output = json(encode(freeze(document)));
        for (const [path, expected] of conditionPaths(input)) {
            assert.deepEqual(path.reduce((node, key) => node?.[key], output), expected, path.join('.'));
        }
        assert.deepEqual(json(encode(normalize(output))), output);
        assert.deepEqual(input, fixtures[kind]);
    });

    test(`${kind}: 导入与导出不共享可变数据`, () => {
        const input = json(fixtures[kind]);
        const document = normalize(input);
        const before = json(document);
        const output = encode(document);
        const corrupt = value => {
            if (value && typeof value === 'object') {
                Object.values(value).forEach(corrupt);
                if (Array.isArray(value)) value.push('changed');
                else value.changed = true;
            }
        };
        corrupt(input);
        corrupt(output);
        assert.deepEqual(json(document), before);
    });
}

test('对话保留自定义动作中的空值、标记触发方式和负优先级', () => {
    const output = exportDialogueToDatapack(normalizeImportedDialogue(fixtures.dialogue));
    const choice = output.nodes[0].choices[0];
    assert.deepEqual(choice.actions, fixtures.dialogue.nodes[0].choices[0].actions);
    assert.equal(choice.priority, -5);
    assert.equal(choice.relatedMarks[0].trigger, 'CONTINUOUS');
    assert.equal(output.nodes[0].conditionalTexts.special.priority, -4);
    assert.equal(output.nodes[0].conditionalTexts.special.relatedMarks[0].priority, -2);
    assert.equal(Object.hasOwn(choice, 'nextNodeId'), false);
    const source = json(fixtures.dialogue);
    delete source.nodes[0].relatedMarks[0].trigger;
    assert.equal(normalizeImportedDialogue(source).nodes[0].relatedMarks[0].trigger, 'DIALOGUE_NODE_ENTERED');
});

test('对话拒绝把非数组 actions 静默清空', () => {
    const source = json(fixtures.dialogue);
    source.nodes[0].choices[0].actions = {type: 'close'};
    assert.throws(() => normalizeImportedDialogue(source), /actions 必须是数组/);
});

test('默认台词使用 Java 字段 defaultSayId 导出，同时仍可读旧编辑器字段', () => {
    const source = structuredClone(fixtures.dialogue);
    source.nodes[0].defaultSayId = 'canonical';
    source.nodes[0].sayId = 'legacy';
    const output = exportDialogueToDatapack(normalizeImportedDialogue(source));
    assert.equal(output.nodes[0].defaultSayId, 'canonical');
    assert.equal(Object.hasOwn(output.nodes[0], 'sayId'), false);
    delete source.nodes[0].defaultSayId;
    assert.equal(exportDialogueToDatapack(normalizeImportedDialogue(source)).nodes[0].defaultSayId, 'legacy');
});

test('所有抽卡条件位置和对话选项条件均参与校验', () => {
    const invalid = {condition: 'arc_quest:not'};
    const gacha = normalizeImportedGacha(fixtures.gacha);
    gacha.drawCondition = invalid;
    gacha.resetCondition = invalid;
    gacha.pity.resetCondition = invalid;
    assert.equal(validateGacha(gacha).filter(issue => issue.lvl === 'err' && issue.path.endsWith('.inner')).length, 3);
    const dialogue = normalizeImportedDialogue(fixtures.dialogue);
    dialogue.nodes[0].choices[0].conditions = [invalid];
    assert.ok(validateDialogue(dialogue).some(issue => issue.path === 'nodes[0].choices[0].conditions[0].inner'));
});

test('Quest 按数组校验解锁条件，同时校验阶段进入条件', () => {
    const q = normalizeImportedQuest(fixtures.quest);
    q.unlockConditions = {condition: 'arc_quest:always'};
    q.phases[0].rawEnterCondition = {condition: 'arc_quest:quest_phase', quest_id: q.id, phase_id: 'intro'};
    const state = {quest: {q}};
    validateQuest(state);
    for (const path of ['quest:unlockConditions', 'phase:0:enterCondition.questId', 'phase:0:enterCondition.phaseId']) {
        assert.ok(state.quest.diag.some(issue => issue.lvl === 'err' && issue.path === path), path);
    }
});
