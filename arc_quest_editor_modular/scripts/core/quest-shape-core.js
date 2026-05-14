import {createQuestSkeleton} from './factories.js';
import {splitList} from './utils.js';

export function setLooseJson(target, key, value) {
    if (!value?.trim()) {
        target[key] = null;
        return;
    }
    try {
        target[key] = JSON.parse(value);
    } catch {
        target[key] = value;
    }
}

export function setConditionNodeField(rootNode, pathParts, value) {
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

export function syncPhaseTransitions(phase) {
    if (phase.mode === 'parallel') {
        phase.transitions = (phase.parallelPhaseIds || []).filter(Boolean).map(targetPhaseId => ({
            targetPhaseId,
            condition: {condition: 'arc_quest:always'}
        }));
        return;
    }
    if (phase.mode === 'choice') {
        phase.transitions = (phase.choicePhaseIds || []).filter(Boolean).map(targetPhaseId => ({
            targetPhaseId,
            condition: {condition: 'arc_quest:always'}
        }));
        return;
    }
    if (!Array.isArray(phase.transitions)) phase.transitions = [];
}

export function ensureQuestShape(q) {
    const defaults = createQuestSkeleton();

    q.id ??= defaults.id;
    q.title ??= defaults.title;
    q.titleMode ||= defaults.titleMode;
    q.description ??= defaults.description;
    q.descriptionMode ||= defaults.descriptionMode;
    q.sortOrder ??= defaults.sortOrder;
    q.repeatable ??= defaults.repeatable;
    q.mode ||= defaults.mode;
    q.category ||= defaults.category;
    q.tags ||= [...defaults.tags];
    q.flagsToSetOnAccept ||= [...defaults.flagsToSetOnAccept];
    q.flagsToSetOnComplete ||= [...defaults.flagsToSetOnComplete];
    q.rewards ||= [...defaults.rewards];
    q.phases ||= [...defaults.phases];
    q.unlockConditions ??= defaults.unlockConditions;
    q.visualConfig ||= {};
    q.visualConfig.themeColor ||= defaults.visualConfig.themeColor;
    q.visualConfig.splashes ||= [...defaults.visualConfig.splashes];
    q.visualConfig.icons ||= {};
    q.relatedMarks ||= [];
    q.completionPolicy ||= 'ALL';
    q.completionRequiredCount ||= 1;
    q.completionTargetPhaseId ||= '';
    q.timeLimitType ||= '';
    q.timeLimitValue ||= 0;
    q.chapterStartSound ||= '';
    q.chapterFailSound ||= '';
    q.chapterCompleteSound ||= '';
    q.chapterShopType ||= 'TRADE';
    if (q.collectionConfig) {
        q.collectionConfig.trackerPresentationMode ||= 'DETAILED';
        q.collectionConfig.collectionPresentationMode ||= 'GROUPED';
    }
    q.phases.forEach((p, i) => {
        p.id ||= `phase_${i + 1}`;
        p.mode ||= 'normal';
        p.titleMode ||= 'translatable';
        p.descriptionMode ||= 'translatable';
        p.autoAdvanceOnComplete ??= true;
        p.parallelPhaseIds ||= [];
        p.choicePhaseIds ||= [];
        p.objectives ||= [];
        p.objectives.forEach(o => {
            o.textMode ||= 'translatable';
            o.countMode ||= 'fixed';
            o.countBase ??= o.count ?? 1;
            o.countPerLevel ??= 0;
            o.countMin ??= 1;
            o.countMax ??= -1;
            o.extraData ||= {};
            o.relatedMarks ||= [];
        });
        p.rewards ||= [];
        p.flagsToSetOnEnter ||= [];
        p.flagsToSetOnComplete ||= [];
        p.tradeShopId ||= '';
        p.phaseStartSound ||= '';
        p.phaseCompleteSound ||= '';
        p.relatedMarks ||= [];
        p.visualConfig ||= null;
        p.choices ||= [];
        p.transitions ||= [];
    });
}

export function setQuestRootField(target, bind, value, inputType) {
    if (bind === 'q.id') {
        target.id = value;
        return {fileName: (value || 'quest') + '.json'};
    }
    if (bind === 'q.title') return target.title = value;
    if (bind === 'q.titleMode') return target.titleMode = value;
    if (bind === 'q.description') return target.description = value;
    if (bind === 'q.descriptionMode') return target.descriptionMode = value;
    if (bind === 'q.sortOrder') return target.sortOrder = inputType === 'number' ? Number(value) : Number(value || 0);
    if (bind === 'q.repeatable') return target.repeatable = value === 'true';
    if (bind === 'q.tags') return target.tags = splitList(value);
    if (bind === 'q.theme') return target.visualConfig.themeColor = value;
    if (bind === 'q.icons') return setLooseJson(target.visualConfig, 'icons', value);
    if (bind === 'q.category') return target.category = value;
    if (bind === 'q.mode') return target.mode = value;
    if (bind === 'q.chapterShopId') return target.chapterShopId = value;
    if (bind === 'q.chapterShopType') return target.chapterShopType = value;
    if (bind === 'q.chapterStartSound') return target.chapterStartSound = value;
    if (bind === 'q.chapterFailSound') return target.chapterFailSound = value;
    if (bind === 'q.chapterCompleteSound') return target.chapterCompleteSound = value;
    if (bind === 'q.completionPolicy') return target.completionPolicy = value;
    if (bind === 'q.completionRequiredCount') return target.completionRequiredCount = Number(value || 0);
    if (bind === 'q.completionTargetPhaseId') return target.completionTargetPhaseId = value;
    if (bind === 'q.hasTimeLimit') {
        if (value === 'true') {
            target.timeLimitType ||= 'REAL_SECONDS';
            if (!Number(target.timeLimitValue || 0)) target.timeLimitValue = 60;
        } else {
            target.timeLimitType = '';
            target.timeLimitValue = 0;
        }
        return;
    }
    if (bind === 'q.timeLimitType') return target.timeLimitType = value;
    if (bind === 'q.timeLimitValue') return target.timeLimitValue = Number(value || 0);
    if (bind === 'q.flagsToSetOnAccept') return target.flagsToSetOnAccept = splitList(value);
    if (bind === 'q.unlockConditions') return setLooseJson(target, 'unlockConditions', value);
    if (bind.startsWith('q.uc.')) {
        const parts = bind.split('.');
        const index = Number(parts[2]);
        target.unlockConditions ||= [];
        target.unlockConditions[index] ||= {condition: 'arc_quest:always'};
        setConditionNodeField(target.unlockConditions[index], parts.slice(3), value);
        return;
    }
    if (bind.startsWith('ph.') && bind.endsWith('.hasEnterCondition')) {
        const parts = bind.split('.');
        const phase = target.phases[Number(parts[1])];
        if (value === 'true') phase.rawEnterCondition ||= {condition: 'arc_quest:always'};
        else phase.rawEnterCondition = null;
        return;
    }
    if (bind === 'q.relatedMarks') return setLooseJson(target, 'relatedMarks', value);
    if (bind === 'q.collectionConfig') return setLooseJson(target, 'collectionConfig', value);
    return undefined;
}
