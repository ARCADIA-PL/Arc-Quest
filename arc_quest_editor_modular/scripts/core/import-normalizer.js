function asTextNode(value, fallback = '') {
    if (typeof value === 'string') return {text: value, mode: 'translatable'};
    if (value && typeof value === 'object' && 'value' in value) return {
        text: value.value || fallback,
        mode: value.mode || 'translatable'
    };
    return {text: fallback, mode: 'translatable'};
}

function toHexColor(value) {
    if (typeof value === 'string') return value;
    if (typeof value === 'number' && Number.isFinite(value)) return '#' + value.toString(16).padStart(6, '0');
    return '#63c7ff';
}

function normalizeSplashMap(splashes) {
    if (!splashes || Array.isArray(splashes)) return splashes || [];
    return Object.entries(splashes).map(([eventType, cfg]) => ({
        eventType,
        texture: cfg?.texture || '',
        scale: cfg?.scale ?? 1
    }));
}

function normalizeReward(reward) {
    const type = reward?.type || 'item';
    if (type === 'command') return {type, command: reward?.command || ''};
    if (type === 'flag_set' || type === 'flag_clear') return {type, flag: reward?.flag || ''};
    if (type === 'var_set' || type === 'var_add' || type === 'var_subtract' || type === 'var_multiply') {
        return {type, variable: reward?.variable || '', value: reward?.value ?? 0};
    }
    return {type: 'item', itemId: reward?.itemId || '', count: reward?.count ?? 1, nbt: reward?.nbt || '', command: reward?.command || ''};
}

function mapObjectiveType(type) {
    const t = String(type || '').toUpperCase();
    if (t === 'KILL') return 'KILL';
    if (t === 'COLLECT') return 'COLLECT';
    if (t === 'TALK') return 'TALK';
    if (t === 'INTERACT') return 'INTERACT';
    if (t === 'REACH_LOCATION') return 'REACH_LOCATION';
    if (t === 'DELIVER') return 'DELIVER';
    if (t === 'CRAFT') return 'CRAFT';
    if (t === 'OFFER') return 'OFFER';
    return 'CUSTOM';
}

function normalizeObjective(obj, idx) {
    const type = mapObjectiveType(obj?.type);
    const textNode = asTextNode(obj?.displayText, '');
    const base = {
        type,
        id: obj?.id || `objective_${idx + 1}`,
        text: textNode.text,
        textMode: textNode.mode,
        count: obj?.requiredCount ?? 1,
        countMode: obj?.countMode || 'fixed',
        countBase: obj?.countBase ?? obj?.requiredCount ?? 1,
        countPerLevel: obj?.countPerLevel ?? 0,
        countMin: obj?.countMin ?? 1,
        countMax: obj?.countMax ?? -1,
        hidden: !!obj?.hidden,
        optional: !!obj?.optional,
        targetId: obj?.targetId || '',
        npcId: obj?.npcId || '',
        itemTag: obj?.itemTag || '',
        x: obj?.x ?? null,
        y: obj?.y ?? null,
        z: obj?.z ?? null,
        radius: obj?.radius ?? null,
        extraData: obj?.extraData || {},
        relatedMarks: obj?.relatedMarks || []
    };
    if (!base.npcId && base.extraData?.npc_id) base.npcId = base.extraData.npc_id;
    if (!base.itemTag && base.extraData?.target_tag) base.itemTag = base.extraData.target_tag;
    if (base.x === null && base.extraData?.x !== undefined) base.x = Number(base.extraData.x);
    if (base.y === null && base.extraData?.y !== undefined) base.y = Number(base.extraData.y);
    if (base.z === null && base.extraData?.z !== undefined) base.z = Number(base.extraData.z);
    if (base.radius === null && base.extraData?.radius !== undefined) base.radius = Number(base.extraData.radius);
    return base;
}

function inferPhaseMode(phase) {
    const transitions = phase?.transitions || [];
    const choices = phase?.choices || [];
    if (choices.length > 0) return 'choice';
    if (transitions.length > 1) return 'parallel';
    return 'normal';
}

function normalizeCondition(node) {
    if (!node) return {condition: 'arc_quest:always'};
    if (node.condition) {
        if (node.condition === 'arc_quest:and' || node.condition === 'arc_quest:or') {
            return {
                condition: node.condition,
                conditions: (node.conditions || []).map(normalizeCondition)
            };
        }
        if (node.condition === 'arc_quest:not') {
            return {
                condition: 'arc_quest:not',
                inner: normalizeCondition(node.inner)
            };
        }
        return {
            condition: node.condition,
            flag: node.flag,
            questId: node.questId,
            key: node.key,
            op: node.op,
            value: node.value,
            item: node.item,
            dimension: node.dimension,
            pos: node.pos,
            entityType: node.entityType,
            target: node.target,
            count: node.count,
            inner: node.inner ? normalizeCondition(node.inner) : undefined,
            conditions: node.conditions ? node.conditions.map(normalizeCondition) : undefined
        };
    }
    if (node.type) {
        return convertOldCondition(node);
    }
    return {condition: 'arc_quest:always'};
}

function convertOldCondition(node) {
    const type = node.type || 'always';
    if (type === 'always') return {condition: 'arc_quest:always'};
    if (type === 'flag_set') return {condition: 'arc_quest:has_flag', flag: node.flag || ''};
    if (type === 'flag_not_set') return {condition: 'arc_quest:not_has_flag', flag: node.flag || ''};
    if (type === 'quest_completed') return {condition: 'arc_quest:quest_completed', questId: node.questId || ''};
    if (type === 'variable') return {
        condition: 'arc_quest:variable_check',
        key: node.variable || '',
        op: node.compareOp || 'EQUAL',
        value: Number(node.value ?? 0)
    };
    if (type === 'not') return {
        condition: 'arc_quest:not',
        inner: convertOldCondition(node.left)
    };
    if (type === 'and' || type === 'or') {
        const conditions = [];
        if (node.left) conditions.push(convertOldCondition(node.left));
        if (node.right) conditions.push(convertOldCondition(node.right));
        return {condition: `arc_quest:${type}`, conditions};
    }
    return {condition: 'arc_quest:always'};
}

function normalizePhase(phase, idx) {
    const transitions = phase?.transitions || [];
    const targetIds = transitions.map(t => t?.targetPhaseId).filter(Boolean);
    const mode = inferPhaseMode(phase);
    const titleNode = asTextNode(phase?.displayName, '');
    const descNode = asTextNode(phase?.description, '');
    const storyNode = asTextNode(phase?.story, '');
    return {
        id: phase?.phaseId || phase?.id || `phase_${idx + 1}`,
        mode,
        title: titleNode.text,
        titleMode: titleNode.mode,
        description: descNode.text,
        descriptionMode: descNode.mode,
        story: storyNode.text,
        storyMode: storyNode.mode,
        intelSceneId: phase?.intelSceneId || '',
        tradeShopId: phase?.tradeShopId || '',
        phaseStartSound: phase?.phaseStartSound || '',
        phaseCompleteSound: phase?.phaseCompleteSound || '',
        relatedMarks: phase?.relatedMarks || [],
        visualConfig: phase?.visualConfig ? {
            themeColor: phase.visualConfig.themeColor == null ? '' : toHexColor(phase.visualConfig.themeColor),
            useQuestSplashPresentation: phase.visualConfig.useQuestSplashPresentation === true,
            splashes: normalizeSplashMap(phase.visualConfig.splashes),
            icons: phase.visualConfig.icons || {}
        } : null,
        autoStart: !!phase?.autoEnterByCondition,
        parallelPhaseIds: mode === 'parallel' ? targetIds : [],
        choicePhaseIds: mode === 'choice' ? targetIds : [],
        transitions: transitions.map(t => ({
            targetPhaseId: t?.targetPhaseId || '',
            condition: normalizeCondition(t?.condition)
        })),
        choices: mode === 'choice' ? (phase?.choices || []).map(c => ({
            text: c?.text,
            flagToSet: c?.flagToSet || '',
            targetPhaseId: c?.targetPhaseId || '',
            visibleCondition: normalizeCondition(c?.visibleCondition)
        })) : [],
        rawEnterCondition: normalizeCondition(phase?.enterCondition),
        flagsToSetOnEnter: phase?.flagsToSetOnEnter || [],
        flagsToSetOnComplete: phase?.flagsToSetOnComplete || [],
        objectives: (phase?.objectives || []).map(normalizeObjective),
        rewards: (phase?.phaseRewards || []).map(normalizeReward),
        collectionEntryConfig: phase?.collectionEntryConfig || null
    };
}

export function normalizeImportedQuest(input) {
    const titleNode = asTextNode(input.displayName, input.title || '');
    const descNode = asTextNode(input.description, input.descriptionText || '');
    return {
        id: input.id || 'imported_quest',
        title: titleNode.text,
        titleMode: titleNode.mode,
        description: descNode.text,
        descriptionMode: descNode.mode,
        sortOrder: input.sortOrder ?? 0,
        repeatable: !!input.repeatable,
        allowAbandon: input.allowAbandon !== false,
        tags: input.tags || [],
        mode: input.mode || 'PROGRESSION',
        category: input.category || '',
        chapterShopId: input.chapterShopId || '',
        chapterShopType: input.chapterShopType || '',
        chapterShopPersistent: !!input.chapterShopPersistent,
        initialPhaseId: input.initialPhaseId || '',
        iconTexture: input.iconTexture || '',
        flagsToSetOnAccept: input.flagsToSetOnAccept || [],
        flagsToSetOnComplete: input.flagsToSetOnComplete || [],
        completionPolicy: input.completionPolicy || 'ALL',
        completionRequiredCount: input.completionRequiredCount ?? 0,
        completionTargetPhaseId: input.completionTargetPhaseId || '',
        timeLimitType: input.timeLimitType || '',
        timeLimitValue: input.timeLimitValue ?? 0,
        chapterStartSound: input.chapterStartSound || '',
        chapterFailSound: input.chapterFailSound || '',
        chapterCompleteSound: input.chapterCompleteSound || '',
        unlockConditions: input.unlockConditions || null,
        relatedMarks: input.relatedMarks || [],
        visualConfig: {
            themeColor: toHexColor(input.visualConfig?.themeColor),
            splashes: normalizeSplashMap(input.visualConfig?.splashes),
            icons: input.visualConfig?.icons || {}
        },
        rewards: (input.completionRewards || input.rewards || []).map(normalizeReward),
        phases: (input.phases || []).map(normalizePhase),
        collectionConfig: input.collectionConfig || input.collection || null
    };
}
