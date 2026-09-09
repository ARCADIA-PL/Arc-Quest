function isNonEmptyString(value) {
    return typeof value === 'string' && value.trim().length > 0;
}

function textNode(value, mode = 'translatable', fallback = '') {
    return {mode, value: value || fallback};
}

function descriptionNode(value, mode = 'translatable') {
    if (!value) return undefined;
    return {mode: mode || 'translatable', value};
}

export function cleanCondition(node) {
    if (!node?.condition || node.condition === 'arc_quest:always') return {condition: 'arc_quest:always'};

    switch (node.condition) {
        case 'arc_quest:has_flag':
        case 'arc_quest:not_has_flag':
            return {condition: node.condition, flag: node.flag || ''};

        case 'arc_quest:quest_completed':
        case 'arc_quest:quest_accepted':
        case 'arc_quest:quest_not_started':
        case 'arc_quest:has_quest':
            return {condition: node.condition, questId: node.questId || ''};

        case 'arc_quest:quest_phase':
        case 'arc_quest:quest_phase_completed':
        case 'arc_quest:quest_phase_reached':
        case 'arc_quest:phase_enterable':
            return {condition: node.condition, questId: node.questId || '', phaseId: node.phaseId || ''};

        case 'arc_quest:phase_before':
        case 'arc_quest:phase_after':
            return {condition: node.condition, questId: node.questId || '', targetPhaseId: node.targetPhaseId || ''};

        case 'arc_quest:phase_between':
        case 'arc_quest:any_active_in_range':
        case 'arc_quest:all_completed_in_range':
            return {condition: node.condition, questId: node.questId || '', fromPhaseId: node.fromPhaseId || '', toPhaseId: node.toPhaseId || ''};

        case 'arc_quest:variable_check':
            return {condition: 'arc_quest:variable_check', key: node.key || '', op: node.op || 'EQUAL', value: Number(node.value ?? 0)};

        case 'arc_quest:hold_item': {
            const condition = {
                condition: 'arc_quest:hold_item',
                itemId: node.itemId || '',
                count: Math.max(1, Number(node.count ?? 1))
            };
            if ((node.itemSource || 'hands') !== 'hands') condition.itemSource = node.itemSource;
            return condition;
        }

        case 'arc_quest:entity_nbt':
            return {condition: 'arc_quest:entity_nbt', nbtScope: node.nbtScope || 'default', nbtKey: node.nbtKey || '', nbtValue: node.nbtValue || ''};

        case 'arc_quest:entity_name':
            return {condition: 'arc_quest:entity_name', namePattern: node.namePattern || ''};

        case 'arc_quest:dialogue_completed':
        case 'arc_quest:dialogue_on_cooldown':
            return {condition: node.condition, dialogueId: node.dialogueId || '', cooldownSeconds: node.cooldownSeconds};

        case 'arc_quest:node_visited':
        case 'arc_quest:node_on_cooldown':
            return {condition: node.condition, nodeId: node.nodeId || ''};

        case 'arc_quest:choice_selected':
        case 'arc_quest:choice_on_cooldown':
            return {condition: node.condition, choiceId: node.choiceId || ''};

        case 'arc_quest:game_time_in_range':
            return {condition: 'arc_quest:game_time_in_range', startTick: Number(node.startTick ?? 0), endTick: Number(node.endTick ?? 0)};

        case 'arc_quest:not':
            return {condition: 'arc_quest:not', inner: cleanCondition(node.inner)};

        case 'arc_quest:and':
        case 'arc_quest:or':
            return {condition: node.condition, conditions: (node.conditions || []).map(cleanCondition)};

        case 'minecraft:entity_properties':
            return {condition: 'minecraft:entity_properties', predicate: node.predicate || {}};
    }

    return {condition: 'arc_quest:always'};
}

function cleanCollectionEntryConfig(config) {
    if (!config) return undefined;
    const out = {
        categoryId: config.categoryId || undefined,
        visibilityMode: config.visibilityMode || undefined,
        hiddenPresentationMode: config.hiddenPresentationMode || undefined,
        visibilityConditions: config.visibilityConditions ? config.visibilityConditions.map(cleanCondition) : undefined,
        countingMode: config.countingMode || undefined,
        completionTarget: config.completionTarget === undefined ? undefined : Number(config.completionTarget),
        repeatableProgress: config.repeatableProgress === undefined ? undefined : !!config.repeatableProgress,
        repeatableCompletion: config.repeatableCompletion === undefined ? undefined : !!config.repeatableCompletion,
        sortOrder: config.sortOrder === undefined ? undefined : Number(config.sortOrder),
        maxCount: config.maxCount === undefined ? undefined : Number(config.maxCount),
        rewardGrantMode: config.rewardGrantMode || undefined,
        rewardNodes: config.rewardNodes ? config.rewardNodes.map(r => ({
            rewardId: r?.rewardId || '',
            categoryId: r?.categoryId || '',
            completionMode: r?.completionMode || 'PER_CATEGORY',
            completionCount: r?.completionCount,
            rewards: (r?.rewards || []).map(exportReward)
        })) : undefined,
        showInTrackerByDefault: config.showInTrackerByDefault === undefined ? undefined : !!config.showInTrackerByDefault
    };
    Object.keys(out).forEach(key => out[key] === undefined && delete out[key]);
    return Object.keys(out).length ? out : undefined;
}

function objectiveType(type) {
    const t = String(type || '').toUpperCase();
    if (t === 'KILL' || t === 'COLLECT' || t === 'TALK' || t === 'INTERACT' || t === 'REACH_LOCATION' || t === 'DELIVER' || t === 'CRAFT' || t === 'OFFER' || t === 'CUSTOM') {
        return t;
    }
    return 'CUSTOM';
}

export function exportReward(reward) {
    const type = reward?.type || 'item';
    if (type === 'command') {
        return {
            type: 'command',
            command: reward?.command || ''
        };
    }
    if (type === 'flag_set' || type === 'flag_clear') {
        return {
            type,
            flag: reward?.flag || ''
        };
    }
    if (type === 'var_set' || type === 'var_add' || type === 'var_subtract' || type === 'var_multiply') {
        return {
            type,
            variable: reward?.variable || '',
            value: Number(reward?.value ?? 0)
        };
    }
    return {
        type: 'item',
        itemId: reward?.itemId || '',
        count: Number(reward?.count ?? 1),
        ...(reward?.nbt ? {nbt: reward.nbt} : {}),
        ...(reward?.command ? {command: reward.command} : {})
    };
}

export function exportObjective(o) {
    const type = objectiveType(o.type);
    const targetId = o.targetId || '';
    const out = {
        id: o.id || '',
        type,
        targetId,
        requiredCount: Number(o.count ?? 1),
        displayText: textNode(o.text, o.textMode || 'translatable', '')
    };

    if (o.hidden) out.hidden = true;
    if (o.optional) out.optional = true;
    if (o.countMode && o.countMode !== 'fixed') out.countMode = o.countMode;
    if (o.countBase !== null && o.countBase !== undefined && Number(o.countBase) !== 1) out.countBase = Number(o.countBase);
    if (o.countPerLevel !== null && o.countPerLevel !== undefined && Number(o.countPerLevel) !== 0) out.countPerLevel = Number(o.countPerLevel);
    if (o.countMin !== null && o.countMin !== undefined && Number(o.countMin) !== 1) out.countMin = Number(o.countMin);
    if (o.countMax !== null && o.countMax !== undefined && Number(o.countMax) !== -1) out.countMax = Number(o.countMax);
    if (o.extraData && Object.keys(o.extraData).length) out.extraData = {...o.extraData};

    if (o.relatedMarks?.length) out.relatedMarks = o.relatedMarks;
    if (o.npcId) out.npcId = o.npcId;
    if (o.itemTag) out.itemTag = o.itemTag;
    if (o.x !== null && o.x !== undefined) out.x = Number(o.x);
    if (o.y !== null && o.y !== undefined) out.y = Number(o.y);
    if (o.z !== null && o.z !== undefined) out.z = Number(o.z);
    if (o.radius !== null && o.radius !== undefined) out.radius = Number(o.radius);

    return out;
}

export function exportPhase(phase) {
    const transitions = Array.isArray(phase.transitions)
        ? phase.transitions.map(tr => ({
            targetPhaseId: tr?.targetPhaseId || '',
            ...(tr?.targetPhaseIds?.length ? {targetPhaseIds: tr.targetPhaseIds} : {}),
            condition: cleanCondition(tr?.condition)
        }))
        : [];
    const enterCondition = cleanCondition(phase.rawEnterCondition);
    const out = {
        phaseId: phase.id,
        displayName: textNode(phase.title, phase.titleMode || 'translatable', phase.id),
        objectives: (phase.objectives || []).map(exportObjective),
        transitions
    };
    if (phase.rewards?.length) out.phaseRewards = phase.rewards.map(exportReward);
    if (isNonEmptyString(phase.description)) out.description = descriptionNode(phase.description, phase.descriptionMode || 'translatable');
    if (isNonEmptyString(phase.story)) out.story = descriptionNode(phase.story, phase.storyMode || 'literal');
    if (phase.intelSceneId) out.intelSceneId = phase.intelSceneId;
    if (phase.tradeShopId) out.tradeShopId = phase.tradeShopId;
    if (phase.phaseStartSound) out.phaseStartSound = phase.phaseStartSound;
    if (phase.phaseCompleteSound) out.phaseCompleteSound = phase.phaseCompleteSound;
    if (phase.relatedMarks?.length) out.relatedMarks = phase.relatedMarks;
    if (phase.trackingMarks?.length) out.trackingMarks = phase.trackingMarks;
    if (phase.visualConfig) {
        const vc = {};
        if (phase.visualConfig.themeColor) vc.themeColor = String(phase.visualConfig.themeColor);
        if (phase.visualConfig.useQuestSplashPresentation === true) vc.useQuestSplashPresentation = true;
        const splashArr = Array.isArray(phase.visualConfig.splashes) ? phase.visualConfig.splashes : [];
        const cleanSplashes = {};
        splashArr.forEach(s => {
            if (s?.eventType) cleanSplashes[s.eventType] = exportVisualAsset(s);
        });
        if (Object.keys(cleanSplashes).length) vc.splashes = cleanSplashes;
        const icons = exportVisualAssetMap(phase.visualConfig.icons);
        if (icons) vc.icons = icons;
        if (Object.keys(vc).length) out.visualConfig = vc;
    }
    if (phase.choices?.length) {
        out.choices = phase.choices.map(c => {
            const choice = {};
            if (c.text) {
                if (typeof c.text === 'string') choice.text = {mode: 'literal', value: c.text};
                else choice.text = {mode: c.text.mode || 'literal', value: c.text.value || c.text.text || ''};
            }
            if (c.flagToSet) choice.flagToSet = c.flagToSet;
            if (c.targetPhaseId) choice.targetPhaseId = c.targetPhaseId;
            if (c.visibleCondition) choice.visibleCondition = cleanCondition(c.visibleCondition);
            return choice;
        });
    }
    if (phase.flagsToSetOnEnter?.length) out.flagsToSetOnEnter = phase.flagsToSetOnEnter;
    if (phase.flagsToSetOnComplete?.length) out.flagsToSetOnComplete = phase.flagsToSetOnComplete;
    if (phase.guidesToGrantOnEnter?.length) out.guidesToGrantOnEnter = phase.guidesToGrantOnEnter;
    if (phase.guidesToGrantOnComplete?.length) out.guidesToGrantOnComplete = phase.guidesToGrantOnComplete;
    if (enterCondition.condition !== 'arc_quest:always') out.enterCondition = enterCondition;
    if (phase.autoStart) out.autoEnterByCondition = true;
    const collectionEntryConfig = cleanCollectionEntryConfig(phase.collectionEntryConfig);
    if (collectionEntryConfig) out.collectionEntryConfig = collectionEntryConfig;
    return out;
}
import {exportVisualAsset, exportVisualAssetMap} from './visual-asset.js';
