import {normalizeConditionList, exportCondition} from './condition-codec.js';
import {cloneDocument, cloneJson} from './json-document.js';
import {normalizeMarkers} from './marker-shape.js';

export function normalizeImportedDialogue(input) {
    input = cloneDocument(input);
    return {
        id: input.id || '',
        defaultNpc: normalizeTextSpec(input.defaultNpc),
        startNodeId: input.startNodeId || '',
        nodes: (input.nodes || []).map(node => normalizeNode(node)),
        visualConfig: normalizeVisualConfig(input.visualConfig),
        relatedMarks: normalizeMarkers(input.relatedMarks),
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
        sayId: node.defaultSayId ?? node.sayId ?? '',
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
    return normalizeMarkers((markers || []).map(marker => ({...marker, trigger: marker.trigger || trigger})));
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
    const result = Object.create(null);
    for (const [key, say] of Object.entries(map)) {
        if (!say) continue;
        result[key] = {
            sayId: say.sayId || '',
            text: normalizeTextSpec(say.text),
            soundEvent: say.soundEvent || '',
            conditions: normalizeConditions(say.conditions),
            priority: say.priority ?? 0,
            relatedMarks: normalizeMarkers(say.relatedMarks)
        };
    }
    return result;
}

function normalizeConditions(conditions) {
    return normalizeConditionList(conditions);
}

function normalizeActions(actions) {
    if (actions == null) return [];
    if (!Array.isArray(actions)) throw new Error('对话 actions 必须是数组');
    return actions.map(a => normalizeAction(a));
}

function normalizeAction(action) {
    if (!action || typeof action !== 'object' || Array.isArray(action)) throw new Error('对话动作必须是 JSON 对象');
    return {...cloneJson(action), type: action.type ?? 'no_op'};
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

function exportTextSpec(spec) {
    if (!spec || typeof spec !== 'object') return {mode: 'literal', value: ''};
    const out = {mode: spec.mode === 'translatable' ? 'translatable' : 'literal', value: spec.value || ''};
    if (Array.isArray(spec.args) && spec.args.length) out.args = [...spec.args];
    return out;
}

function exportAction(action) {
    if (!action || typeof action !== 'object' || Array.isArray(action)) throw new Error('对话动作必须是 JSON 对象');
    return {...cloneJson(action), type: action.type ?? 'no_op'};
}

function exportChoice(c) {
    const out = {
        choiceId: c.choiceId || '',
        text: exportTextSpec(c.text)
    };
    if (c.nextNodeId) out.nextNodeId = c.nextNodeId;
    if (c.relatedMarks?.length) out.relatedMarks = cloneJson(c.relatedMarks);
    if (c.conditions?.length) out.conditions = c.conditions.map(exportCondition);
    if (c.actions?.length) out.actions = c.actions.map(exportAction);
    if (c.repeatable === false) out.repeatable = false;
    if (c.cooldownSeconds > 0) out.cooldownSeconds = c.cooldownSeconds;
    if (c.cooldownType && c.cooldownType !== 'NONE') out.cooldownType = c.cooldownType;
    if (c.resetTimeTicks > 0) out.resetTimeTicks = c.resetTimeTicks;
    if (c.priority !== undefined && c.priority !== 0) out.priority = c.priority;
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
    if (node.relatedMarks?.length) out.relatedMarks = cloneJson(node.relatedMarks);
    if (node.sayId) out.defaultSayId = node.sayId;
    if (node.conditionalTexts) {
        const ct = Object.create(null);
        for (const [key, say] of Object.entries(node.conditionalTexts)) {
            const sayOut = {text: exportTextSpec(say.text)};
            if (say.relatedMarks?.length) sayOut.relatedMarks = cloneJson(say.relatedMarks);
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
    dialogue = cloneDocument(dialogue);
    const out = {
        id: dialogue.id || '',
        startNodeId: dialogue.startNodeId || '',
        nodes: (dialogue.nodes || []).map(exportNode)
    };
    if (dialogue.relatedMarks?.length) out.relatedMarks = cloneJson(dialogue.relatedMarks);
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
    return out;
}
import {exportVisualAssetMap, normalizeVisualAssetMap} from './visual-asset.js';
