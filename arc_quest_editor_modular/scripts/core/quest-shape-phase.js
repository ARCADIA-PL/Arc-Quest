import {splitList} from './utils.js';
import {setLooseJson, syncPhaseTransitions} from './quest-shape-core.js';
import {setMarkerField} from './marker-shape.js';

function setCollectionEntryField(phase, fieldName, value, inputType) {
    phase.collectionEntryConfig ||= {};
    if (fieldName === 'showInTrackerByDefault' || fieldName === 'repeatableProgress' || fieldName === 'repeatableCompletion') {
        phase.collectionEntryConfig[fieldName] = value === 'true';
        return;
    }
    if (fieldName === 'visibilityConditions' || fieldName === 'rewardNodes') {
        setLooseJson(phase.collectionEntryConfig, fieldName, value);
        return;
    }
    phase.collectionEntryConfig[fieldName] = inputType === 'number' ? Number(value || 0) : value;
}

function setConditionNodeField(rootNode, pathParts, value) {
    let cursor = rootNode;
    for (let i = 0; i < pathParts.length - 1; i++) {
        const key = pathParts[i];
        if (key === 'conditions' && !Array.isArray(cursor.conditions)) {
            cursor.conditions = [{condition: 'arc_quest:always'}];
        }
        cursor[key] ||= {condition: 'arc_quest:always'};
        cursor = cursor[key];
    }
    const fieldName = pathParts[pathParts.length - 1];
    if (fieldName === 'condition') {
        cursor.condition = value;
        if (value === 'arc_quest:always') {
            delete cursor.flag;
            delete cursor.quest_id;
            delete cursor.phase_id;
            delete cursor.key;
            delete cursor.op;
            delete cursor.value;
            delete cursor.inner;
            delete cursor.conditions;
            delete cursor.predicate;
            return;
        }
        if (value === 'arc_quest:has_flag' || value === 'arc_quest:not_has_flag') {
            cursor.flag ||= '';
            delete cursor.quest_id;
            delete cursor.phase_id;
            delete cursor.key;
            delete cursor.op;
            delete cursor.value;
            delete cursor.inner;
            delete cursor.conditions;
            delete cursor.predicate;
            return;
        }
        if (value === 'arc_quest:hold_item') {
            cursor.itemId ||= '';
            if (cursor.count === undefined) cursor.count = 1;
            delete cursor.flag;
            delete cursor.quest_id;
            delete cursor.phase_id;
            delete cursor.key;
            delete cursor.op;
            delete cursor.value;
            delete cursor.inner;
            delete cursor.conditions;
            delete cursor.predicate;
            return;
        }
        if (value === 'arc_quest:quest_completed' || value === 'arc_quest:quest_accepted' || value === 'arc_quest:quest_not_started') {
            cursor.quest_id ||= '';
            delete cursor.flag;
            delete cursor.phase_id;
            delete cursor.key;
            delete cursor.op;
            delete cursor.value;
            delete cursor.inner;
            delete cursor.conditions;
            delete cursor.predicate;
            return;
        }
        if (value === 'arc_quest:quest_phase' || value === 'arc_quest:quest_phase_completed' || value === 'arc_quest:quest_phase_reached') {
            cursor.quest_id ||= '';
            cursor.phase_id ||= '';
            delete cursor.flag;
            delete cursor.key;
            delete cursor.op;
            delete cursor.value;
            delete cursor.inner;
            delete cursor.conditions;
            delete cursor.predicate;
            return;
        }
        if (value === 'arc_quest:variable_check') {
            cursor.key ||= '';
            cursor.op ||= 'EQUAL';
            if (cursor.value === undefined) cursor.value = 0;
            delete cursor.flag;
            delete cursor.quest_id;
            delete cursor.phase_id;
            delete cursor.inner;
            delete cursor.conditions;
            delete cursor.predicate;
            return;
        }
        if (value === 'arc_quest:not') {
            cursor.inner ||= {condition: 'arc_quest:always'};
            delete cursor.flag;
            delete cursor.quest_id;
            delete cursor.phase_id;
            delete cursor.key;
            delete cursor.op;
            delete cursor.value;
            delete cursor.conditions;
            delete cursor.predicate;
            return;
        }
        if (value === 'arc_quest:and' || value === 'arc_quest:or') {
            cursor.conditions ||= [{condition: 'arc_quest:always'}, {condition: 'arc_quest:always'}];
            delete cursor.flag;
            delete cursor.quest_id;
            delete cursor.phase_id;
            delete cursor.key;
            delete cursor.op;
            delete cursor.value;
            delete cursor.inner;
            delete cursor.predicate;
            return;
        }
        if (value === 'minecraft:entity_properties') {
            cursor.predicate ||= {};
            delete cursor.flag;
            delete cursor.quest_id;
            delete cursor.phase_id;
            delete cursor.key;
            delete cursor.op;
            delete cursor.value;
            delete cursor.inner;
            delete cursor.conditions;
            return;
        }
        return;
    }
    if (fieldName === 'value') {
        cursor[fieldName] = Number(value || 0);
        return;
    }
    if (fieldName === 'predicate') {
        try {
            cursor.predicate = JSON.parse(value || '{}');
        } catch {
            cursor.predicate = {};
        }
        return;
    }
    cursor[fieldName] = value;
}

function setEnterConditionField(phase, pathParts, value) {
    phase.rawEnterCondition ||= {condition: 'arc_quest:always'};
    setConditionNodeField(phase.rawEnterCondition, pathParts, value);
}

function setTransitionField(phase, transitionIndex, fieldName, value, pathParts) {
    phase.transitions ||= [];
    phase.transitions[transitionIndex] ||= {targetPhaseId: '', condition: {condition: 'arc_quest:always'}};
    if (fieldName === 'conditionType') {
        phase.transitions[transitionIndex].condition ||= {};
        phase.transitions[transitionIndex].condition.condition = value;
        return;
    }
    if (fieldName === 'c') {
        phase.transitions[transitionIndex].condition ||= {condition: 'arc_quest:always'};
        setConditionNodeField(phase.transitions[transitionIndex].condition, pathParts || [], value);
        return;
    }
    phase.transitions[transitionIndex][fieldName] = value;
}

export function setPhaseField(target, bind, value, inputType) {
    if (bind.startsWith('ph.') && bind.includes('.relatedMarks.')) {
        const parts = bind.split('.');
        return setMarkerField(target.phases[+parts[1]].relatedMarks, parts.slice(3), value, inputType);
    }
    if (!bind.startsWith('ph.')) return false;
    const [, i, k, sub, leaf] = bind.split('.');
    if (k === 'cec') {
        setCollectionEntryField(target.phases[+i], sub, value, inputType);
    } else if (k === 'ec') {
        setEnterConditionField(target.phases[+i], bind.split('.').slice(3), value);
    } else if (k === 'tr') {
        const parts = bind.split('.');
        if (parts[4] === 'c') setTransitionField(target.phases[+i], +sub, 'c', value, parts.slice(5));
        else setTransitionField(target.phases[+i], +sub, leaf, value);
    } else if (k === 'ch') {
        const parts = bind.split('.');
        const ci = Number(parts[3]);
        target.phases[+i].choices ||= [];
        target.phases[+i].choices[ci] ||= {
            text: '',
            flagToSet: '',
            targetPhaseId: '',
            visibleCondition: {condition: 'arc_quest:always'}
        };
        if (parts[4] === 'vc') {
            setConditionNodeField(target.phases[+i].choices[ci].visibleCondition ||= {condition: 'arc_quest:always'}, parts.slice(5), value);
        } else {
            target.phases[+i].choices[ci][parts[4]] = value;
        }
    } else if (k === 'autoStart' || k === 'autoAdvanceOnComplete') target.phases[+i][k] = value === 'true';
    else if (k === 'useQuestSplashPresentation') {
        target.phases[+i].visualConfig ||= {};
        target.phases[+i].visualConfig.useQuestSplashPresentation = value === 'true';
    }
    else if (k === 'titleMode' || k === 'descriptionMode' || k === 'storyMode') target.phases[+i][k] = value;
    else if (k === 'parallelPhaseIds' || k === 'choicePhaseIds' || k === 'flagsToSetOnEnter' || k === 'flagsToSetOnComplete' || k === 'guidesToGrantOnEnter' || k === 'guidesToGrantOnComplete') target.phases[+i][k] = splitList(value);
    else if (k === 'relatedMarks') setLooseJson(target.phases[+i], 'relatedMarks', value);
    else if (k === 'visualConfig') setLooseJson(target.phases[+i], 'visualConfig', value);
    else if (k === 'rawEnterCondition' || k === 'transitions' || k === 'collectionEntryConfig' || k === 'choices') setLooseJson(target.phases[+i], k, value);
    else target.phases[+i][k] = inputType === 'number' ? Number(value || 0) : value;
    if (k === 'mode' || k === 'parallelPhaseIds' || k === 'choicePhaseIds') syncPhaseTransitions(target.phases[+i]);
    return true;
}

export function setObjectiveField(target, bind, value, inputType) {
    if (bind.startsWith('ob.') && bind.includes('.relatedMarks.')) {
        const parts = bind.split('.');
        return setMarkerField(target.phases[+parts[1]].objectives[+parts[2]].relatedMarks, parts.slice(4), value, inputType);
    }
    if (!bind.startsWith('ob.')) return false;
    const [, pi, oi, k] = bind.split('.');
    if (k === 'extraData' || k === 'relatedMarks') {
        const obj = target.phases[+pi].objectives[+oi];
        setLooseJson(obj, k, value);
        return true;
    }
    if (k === 'textMode' || k === 'targetType' || k === 'countMode') {
        target.phases[+pi].objectives[+oi][k] = value;
        return true;
    }
    if (k === 'hidden' || k === 'optional' || k === 'consumeOnSubmit') {
        target.phases[+pi].objectives[+oi][k] = value === 'true';
        return true;
    }
    target.phases[+pi].objectives[+oi][k] = inputType === 'number' ? Number(value || 0) : value;
    return true;
}

export function setRewardField(target, bind, value, inputType) {
    if (bind.startsWith('rw.quest.')) {
        const [, , i, k] = bind.split('.');
        target.rewards[+i][k] = inputType === 'number' ? Number(value || 0) : value;
        return true;
    }
    if (bind.startsWith('rw.phase.')) {
        const [, , phaseIndex, i, k] = bind.split('.');
        target.phases[+phaseIndex].rewards[+i][k] = inputType === 'number' ? Number(value || 0) : value;
        return true;
    }
    return false;
}
