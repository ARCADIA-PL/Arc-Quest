import {normalizeMarkers} from './marker-shape.js';

export function normalizeImportedDialogue(input) {
    return {
        id: input.id || '',
        defaultNpc: normalizeTextSpec(input.defaultNpc),
        startNodeId: input.startNodeId || '',
        nodes: (input.nodes || []).map(node => normalizeNode(node)),
        visualConfig: normalizeVisualConfig(input.visualConfig),
        repeatable: input.repeatable !== false,
        cooldownSeconds: input.cooldownSeconds ?? 0,
        cooldownType: input.cooldownType || 'NONE',
        resetTimeTicks: input.resetTimeTicks ?? 0,
        npcBindings: (input.npcBindings || []).map(b => ({npcId: b.npcId || '', dialogueId: b.dialogueId || ''})),
        entityBindings: (input.entityBindings || []).map(b => ({entityType: b.entityType || '', dialogueId: b.dialogueId || ''}))
    };
}

function normalizeNode(node) {
    return {
        nodeId: node.nodeId || '',
        sayId: node.sayId || node.defaultSayId || '',
        speaker: normalizeTextSpec(node.speaker),
        text: normalizeTextSpec(node.text),
        conditionalTexts: normalizeConditionalTexts(node.conditionalTexts),
        choices: (node.choices || []).map(choice => normalizeChoice(choice)),
        autoNextId: node.autoNextId || '',
        delayMs: node.delayMs ?? 0,
        repeatable: node.repeatable !== false,
        cooldownSeconds: node.cooldownSeconds ?? 0,
        cooldownType: node.cooldownType || 'NONE',
        resetTimeTicks: node.resetTimeTicks ?? 0,
        nodeEnterSound: node.nodeEnterSound || '',
        relatedMarks: normalizeDialogueMarkers(node.relatedMarks, 'DIALOGUE_NODE_ENTERED')
    };
}

function normalizeChoice(choice) {
    return {
        choiceId: choice.choiceId || '',
        text: normalizeTextSpec(choice.text),
        nextNodeId: choice.nextNodeId || '',
        conditions: normalizeConditions(choice.conditions),
        actions: normalizeActions(choice.actions),
        repeatable: choice.repeatable !== false,
        cooldownSeconds: choice.cooldownSeconds ?? 0,
        cooldownType: choice.cooldownType || 'NONE',
        resetTimeTicks: choice.resetTimeTicks ?? 0,
        priority: choice.priority ?? 0,
        restoreNodeId: choice.restoreNodeId || '',
        selectSound: choice.selectSound || '',
        relatedMarks: normalizeDialogueMarkers(choice.relatedMarks, 'DIALOGUE_CHOICE_SELECTED')
    };
}

function normalizeDialogueMarkers(markers, trigger) {
    const normalized = normalizeMarkers(markers);
    normalized.forEach(marker => {
        if (!marker.trigger || marker.trigger === 'CONTINUOUS') marker.trigger = trigger;
    });
    return normalized;
}

function normalizeTextSpec(spec) {
    if (!spec || typeof spec !== 'object') return {mode: 'literal', value: '', args: []};
    return {
        mode: spec.mode === 'translatable' ? 'translatable' : 'literal',
        value: spec.value || '',
        args: Array.isArray(spec.args) ? [...spec.args] : []
    };
}

function normalizeConditionalTexts(map) {
    if (!map || typeof map !== 'object') return {};
    const result = {};
    for (const [key, say] of Object.entries(map)) {
        if (!say) continue;
        result[key] = {
            sayId: say.sayId || '',
            text: normalizeTextSpec(say.text),
            soundEvent: say.soundEvent || '',
            conditions: normalizeConditions(say.conditions),
            priority: say.priority ?? 0
        };
    }
    return result;
}

function normalizeConditions(conditions) {
    if (!Array.isArray(conditions)) return [];
    return conditions.map(c => normalizeCondition(c));
}

function normalizeCondition(c) {
    if (!c || typeof c !== 'object') return {condition: 'arc_quest:always'};
    const out = {condition: c.condition || 'arc_quest:always'};
    if (c.questId) out.questId = c.questId;
    if (c.phaseId) out.phaseId = c.phaseId;
    if (c.targetPhaseId) out.targetPhaseId = c.targetPhaseId;
    if (c.fromPhaseId) out.fromPhaseId = c.fromPhaseId;
    if (c.toPhaseId) out.toPhaseId = c.toPhaseId;
    if (c.flag) out.flag = c.flag;
    if (c.key) out.key = c.key;
    if (c.op) out.op = c.op;
    if (c.value) out.value = c.value;
    if (c.count) out.count = c.count;
    if (c.effectId) out.effectId = c.effectId;
    if (c.itemId) out.itemId = c.itemId;
    if (c.itemSource && c.itemSource !== 'hands') out.itemSource = c.itemSource;
    if (c.inner) out.inner = normalizeCondition(c.inner);
    if (Array.isArray(c.conditions) && c.conditions.length) out.conditions = normalizeConditions(c.conditions);
    if (c.predicate && typeof c.predicate === 'object') out.predicate = {...c.predicate};
    if (c.nodeId) out.nodeId = c.nodeId;
    if (c.choiceId) out.choiceId = c.choiceId;
    if (c.dialogueId) out.dialogueId = c.dialogueId;
    if (c.cooldownSeconds > 0) out.cooldownSeconds = c.cooldownSeconds;
    if (c.startTick) out.startTick = c.startTick;
    if (c.endTick) out.endTick = c.endTick;
    if (c.name) out.name = c.name;
    if (c.nbtScope) out.nbtScope = c.nbtScope;
    if (c.nbtKey) out.nbtKey = c.nbtKey;
    if (c.nbtValue) out.nbtValue = c.nbtValue;
    if (c.namePattern) out.namePattern = c.namePattern;
    return out;
}

function normalizeActions(actions) {
    if (!Array.isArray(actions)) return [];
    return actions.map(a => normalizeAction(a));
}

function normalizeAction(a) {
    if (!a || typeof a !== 'object') return {type: 'no_op'};
    const out = {type: a.type || 'no_op'};
    if (a.questId) out.questId = a.questId;
    if (a.amount) out.amount = a.amount;
    if (a.itemId) out.itemId = a.itemId;
    if (a.count !== undefined && a.count !== 1) out.count = a.count;
    if (a.npcId) out.npcId = a.npcId;
    if (a.targetId) out.targetId = a.targetId;
    if (a.command) out.command = a.command;
    if (a.flagName) out.flagName = a.flagName;
    if (a.key) out.key = a.key;
    if (a.value) out.value = a.value;
    if (a.shopId) out.shopId = a.shopId;
    if (a.restoreNodeId) out.restoreNodeId = a.restoreNodeId;
    if (a.customTypeId) out.customTypeId = a.customTypeId;
    if (a.customData && typeof a.customData === 'object' && Object.keys(a.customData).length) {
        out.customData = {...a.customData};
    }
    return out;
}

function normalizeVisualConfig(vc) {
    if (!vc || typeof vc !== 'object') return null;
    const out = {};
    if (vc.themeColor !== undefined && vc.themeColor !== null) out.themeColor = vc.themeColor;
    const splashes = normalizeVisualAssetMap(vc.splashes);
    if (Object.keys(splashes).length) out.splashes = splashes;
    const icons = normalizeVisualAssetMap(vc.icons);
    if (Object.keys(icons).length) out.icons = icons;
    return Object.keys(out).length ? out : null;
}

function cleanEmptyFields(obj) {
    if (obj == null) return;
    if (Array.isArray(obj)) {
        for (const item of obj) cleanEmptyFields(item);
        return;
    }
    if (typeof obj !== 'object') return;
    for (const key of Object.keys(obj)) {
        const v = obj[key];
        if (v === null || v === undefined || v === '' ||
            (Array.isArray(v) && v.length === 0) ||
            (typeof v === 'object' && !Array.isArray(v) && Object.keys(v).length === 0)) {
            delete obj[key];
        } else if (typeof v === 'object') {
            cleanEmptyFields(v);
        }
    }
}

function exportTextSpec(spec) {
    if (!spec || typeof spec !== 'object') return {mode: 'literal', value: ''};
    const out = {mode: spec.mode === 'translatable' ? 'translatable' : 'literal', value: spec.value || ''};
    if (Array.isArray(spec.args) && spec.args.length) out.args = [...spec.args];
    return out;
}

function exportCondition(c) {
    if (!c || typeof c !== 'object') return {condition: 'arc_quest:always'};
    const out = {condition: c.condition || 'arc_quest:always'};
    if (c.questId) out.questId = c.questId;
    if (c.phaseId) out.phaseId = c.phaseId;
    if (c.targetPhaseId) out.targetPhaseId = c.targetPhaseId;
    if (c.fromPhaseId) out.fromPhaseId = c.fromPhaseId;
    if (c.toPhaseId) out.toPhaseId = c.toPhaseId;
    if (c.flag) out.flag = c.flag;
    if (c.key) out.key = c.key;
    if (c.op) out.op = c.op;
    if (c.value) out.value = c.value;
    if (c.count) out.count = c.count;
    if (c.effectId) out.effectId = c.effectId;
    if (c.inner) out.inner = exportCondition(c.inner);
    if (Array.isArray(c.conditions) && c.conditions.length) out.conditions = c.conditions.map(exportCondition);
    if (c.predicate && typeof c.predicate === 'object') out.predicate = {...c.predicate};
    if (c.nodeId) out.nodeId = c.nodeId;
    if (c.choiceId) out.choiceId = c.choiceId;
    if (c.dialogueId) out.dialogueId = c.dialogueId;
    if (c.cooldownSeconds > 0) out.cooldownSeconds = c.cooldownSeconds;
    if (c.startTick) out.startTick = c.startTick;
    if (c.endTick) out.endTick = c.endTick;
    if (c.name) out.name = c.name;
    if (c.nbtScope) out.nbtScope = c.nbtScope;
    if (c.nbtKey) out.nbtKey = c.nbtKey;
    if (c.nbtValue) out.nbtValue = c.nbtValue;
    if (c.namePattern) out.namePattern = c.namePattern;
    return out;
}

function exportAction(a) {
    if (!a || typeof a !== 'object') return {type: 'no_op'};
    const out = {type: a.type || 'no_op'};
    if (a.questId) out.questId = a.questId;
    if (a.amount) out.amount = a.amount;
    if (a.itemId) out.itemId = a.itemId;
    if (a.count !== undefined && a.count !== 1) out.count = a.count;
    if (a.npcId) out.npcId = a.npcId;
    if (a.targetId) out.targetId = a.targetId;
    if (a.command) out.command = a.command;
    if (a.flagName) out.flagName = a.flagName;
    if (a.key) out.key = a.key;
    if (a.value) out.value = a.value;
    if (a.shopId) out.shopId = a.shopId;
    if (a.restoreNodeId) out.restoreNodeId = a.restoreNodeId;
    if (a.customTypeId) out.customTypeId = a.customTypeId;
    if (a.customData && typeof a.customData === 'object' && Object.keys(a.customData).length) {
        out.customData = {...a.customData};
    }
    return out;
}

function exportChoice(c) {
    const out = {
        choiceId: c.choiceId || '',
        text: exportTextSpec(c.text),
        nextNodeId: c.nextNodeId || ''
    };
    if (c.conditions?.length) out.conditions = c.conditions.map(exportCondition);
    if (c.actions?.length) out.actions = c.actions.map(exportAction);
    if (c.repeatable === false) out.repeatable = false;
    if (c.cooldownSeconds > 0) out.cooldownSeconds = c.cooldownSeconds;
    if (c.cooldownType && c.cooldownType !== 'NONE') out.cooldownType = c.cooldownType;
    if (c.resetTimeTicks > 0) out.resetTimeTicks = c.resetTimeTicks;
    if (c.priority > 0) out.priority = c.priority;
    if (c.restoreNodeId) out.restoreNodeId = c.restoreNodeId;
    if (c.selectSound) out.selectSound = c.selectSound;
    return out;
}

function exportNode(node) {
    const out = {
        nodeId: node.nodeId || '',
        text: exportTextSpec(node.text)
    };
    const speaker = node.speaker;
    if (speaker && (speaker.mode !== 'literal' || (speaker.value && speaker.value.trim())))
        out.speaker = exportTextSpec(speaker);
    if (node.sayId) out.sayId = node.sayId;
    if (node.conditionalTexts) {
        const ct = {};
        for (const [key, say] of Object.entries(node.conditionalTexts)) {
            const sayOut = {text: exportTextSpec(say.text)};
            if (say.sayId) sayOut.sayId = say.sayId;
            if (say.conditions?.length) sayOut.conditions = say.conditions.map(exportCondition);
            if (say.soundEvent) sayOut.soundEvent = say.soundEvent;
            if (say.priority !== undefined && say.priority !== 0) sayOut.priority = say.priority;
            ct[key] = sayOut;
        }
        if (Object.keys(ct).length) out.conditionalTexts = ct;
    }
    if (node.choices?.length) out.choices = node.choices.map(exportChoice);
    if (node.autoNextId) out.autoNextId = node.autoNextId;
    if (node.delayMs > 0) out.delayMs = node.delayMs;
    if (node.repeatable === false) out.repeatable = false;
    if (node.cooldownSeconds > 0) out.cooldownSeconds = node.cooldownSeconds;
    if (node.cooldownType && node.cooldownType !== 'NONE') out.cooldownType = node.cooldownType;
    if (node.resetTimeTicks > 0) out.resetTimeTicks = node.resetTimeTicks;
    if (node.nodeEnterSound) out.nodeEnterSound = node.nodeEnterSound;
    return out;
}

export function exportDialogueToDatapack(dialogue) {
    const out = {
        id: dialogue.id || '',
        startNodeId: dialogue.startNodeId || '',
        nodes: (dialogue.nodes || []).map(exportNode)
    };
    const npc = dialogue.defaultNpc;
    if (npc && (npc.mode !== 'literal' || (npc.value && npc.value.trim())))
        out.defaultNpc = exportTextSpec(npc);
    if (dialogue.repeatable === false) out.repeatable = false;
    if (dialogue.cooldownSeconds > 0) out.cooldownSeconds = dialogue.cooldownSeconds;
    if (dialogue.cooldownType && dialogue.cooldownType !== 'NONE') out.cooldownType = dialogue.cooldownType;
    if (dialogue.resetTimeTicks > 0) out.resetTimeTicks = dialogue.resetTimeTicks;
    if (dialogue.visualConfig) {
        const vc = {};
        if (dialogue.visualConfig.themeColor !== undefined && dialogue.visualConfig.themeColor !== null) vc.themeColor = dialogue.visualConfig.themeColor;
        const splashes = exportVisualAssetMap(dialogue.visualConfig.splashes);
        if (splashes) vc.splashes = splashes;
        const icons = exportVisualAssetMap(dialogue.visualConfig.icons);
        if (icons) vc.icons = icons;
        if (Object.keys(vc).length) out.visualConfig = vc;
    }
    if (dialogue.npcBindings?.length) out.npcBindings = dialogue.npcBindings.map(b => ({npcId: b.npcId || '', dialogueId: b.dialogueId || ''}));
    if (dialogue.entityBindings?.length) out.entityBindings = dialogue.entityBindings.map(b => ({entityType: b.entityType || '', dialogueId: b.dialogueId || ''}));
    cleanEmptyFields(out);
    return out;
}
import {exportVisualAssetMap, normalizeVisualAssetMap} from './visual-asset.js';
