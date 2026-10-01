import {test} from 'node:test';
import assert from 'node:assert/strict';
import {normalizeImportedGuide, exportGuideToDatapack} from '../scripts/core/guide-normalizer.js';
import {validateGuide} from '../scripts/core/guide-validators.js';

const guide = () => ({id:'arc_quest:tools', category:'arc_quest:basics', title:{mode:'literal',value:'Tools'},
    unlockConditions:[], pages:[{description:{mode:'literal',value:'Tools'},media:{type:'none',width:180,height:90}}]});

test('guide associations survive repeated import/export without aliasing source data', () => {
    const input = {...guide(),forceOpenWithScreen:false,itemAssociations:[{item:'minecraft:diamond',pageIndex:0},{tag:'forge:ingots/iron',pageIndex:0}]};
    const normalized = normalizeImportedGuide(input);
    assert.deepEqual(validateGuide(normalized), []);
    const output = exportGuideToDatapack(normalized);
    assert.deepEqual(output.itemAssociations, input.itemAssociations);
    assert.equal(output.forceOpenWithScreen, false);
    assert.deepEqual(exportGuideToDatapack(normalizeImportedGuide(output)), output);
    output.itemAssociations[0].item = 'minecraft:coal';
    assert.equal(input.itemAssociations[0].item, 'minecraft:diamond');
    assert.equal(normalized.itemAssociations[0].item, 'minecraft:diamond');
});

test('legacy guide icons never imply ingredient associations', () => {
    const normalized = normalizeImportedGuide({...guide(),icon:'minecraft:diamond'});
    assert.deepEqual(normalized.itemAssociations, []);
    assert.deepEqual(validateGuide(normalized), []);
});

test('rejects ambiguous association, malformed ID and out-of-range page', () => {
    for (const association of [null,{}, {item:'minecraft:diamond',tag:'forge:gems'},
        {item:'bad id'}, {tag:'forge:gems',pageIndex:-1}, {item:'minecraft:coal',pageIndex:1},
        {item:'minecraft:coal',pageIndex:0.5}]) {
        const errors = validateGuide({...guide(),itemAssociations:[association]});
        assert.ok(errors.some(issue => issue.path.startsWith('itemAssociations')), JSON.stringify(association));
    }
});
