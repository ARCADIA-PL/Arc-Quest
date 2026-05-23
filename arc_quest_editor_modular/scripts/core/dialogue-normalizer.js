export function normalizeImportedDialogue(input) {
    const d = {...input};
    return {
        id: d.id || '',
        defaultNpc: normalizeTextSpec(d.defaultNpc),
        startNodeId: d.startNodeId || '',
        nodes: (d.nodes || []).map(node => ({
            nodeId: node.nodeId || '',
            sayId: node.sayId || '',
            speaker: normalizeTextSpec(node.speaker),
            text: normalizeTextSpec(node.text),
            conditionalTexts: normalizeConditionalTexts(node.conditionalTexts),
            choices: (node.choices || []).map(choice => ({
                choiceId: choice.choiceId || '',
                text: normalizeTextSpec(choice.text),
                nextNodeId: choice.nextNodeId || '',
                conditions: choice.conditions || [],
                actions: (choice.actions || []).map(a => ({...a})),
                repeatable: choice.repeatable !== false,
                cooldownSeconds: choice.cooldownSeconds ?? 0,
                cooldownType: choice.cooldownType || 'NONE',
                resetTimeTicks: choice.resetTimeTicks ?? 0,
                priority: choice.priority ?? 0,
                restoreNodeId: choice.restoreNodeId || '',
                selectSound: choice.selectSound || ''
            })),
            autoNextId: node.autoNextId || '',
            delayMs: node.delayMs ?? 0,
            repeatable: node.repeatable !== false,
            cooldownSeconds: node.cooldownSeconds ?? 0,
            cooldownType: node.cooldownType || 'NONE',
            resetTimeTicks: node.resetTimeTicks ?? 0,
            nodeEnterSound: node.nodeEnterSound || ''
        })),
        visualConfig: d.visualConfig || null,
        repeatable: d.repeatable !== false,
        cooldownSeconds: d.cooldownSeconds ?? 0,
        cooldownType: d.cooldownType || 'NONE',
        resetTimeTicks: d.resetTimeTicks ?? 0,
        npcBindings: (d.npcBindings || []).map(b => ({npcId: b.npcId || '', dialogueId: b.dialogueId || ''})),
        entityBindings: (d.entityBindings || []).map(b => ({entityType: b.entityType || '', dialogueId: b.dialogueId || ''}))
    };
}

function normalizeTextSpec(spec) {
    if (!spec || typeof spec !== 'object') return {mode: 'literal', value: '', args: []};
    return {
        mode: spec.mode === 'translatable' ? 'translatable' : 'literal',
        value: spec.value || '',
        args: Array.isArray(spec.args) ? spec.args : []
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
            conditions: say.conditions || [],
            priority: say.priority ?? 0
        };
    }
    return result;
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

function exportChoice(c) {
    const out = {
        choiceId: c.choiceId || '',
        text: {mode: c.text?.mode || 'literal', value: c.text?.value || ''},
        nextNodeId: c.nextNodeId || ''
    };
    if (c.conditions?.length) out.conditions = c.conditions;
    if (c.actions?.length) out.actions = c.actions;
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
        speaker: {mode: node.speaker?.mode || 'literal', value: node.speaker?.value || ''},
        text: {mode: node.text?.mode || 'literal', value: node.text?.value || ''}
    };
    if (node.sayId) out.sayId = node.sayId;
    if (node.conditionalTexts) {
        const ct = {};
        for (const [key, say] of Object.entries(node.conditionalTexts)) {
            const sayOut = {text: {mode: say.text?.mode || 'literal', value: say.text?.value || ''}};
            if (say.sayId) sayOut.sayId = say.sayId;
            if (say.conditions?.length) sayOut.conditions = say.conditions;
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
        defaultNpc: {mode: dialogue.defaultNpc?.mode || 'literal', value: dialogue.defaultNpc?.value || ''},
        startNodeId: dialogue.startNodeId || '',
        nodes: (dialogue.nodes || []).map(exportNode)
    };
    if (dialogue.repeatable === false) out.repeatable = false;
    if (dialogue.cooldownSeconds > 0) out.cooldownSeconds = dialogue.cooldownSeconds;
    if (dialogue.cooldownType && dialogue.cooldownType !== 'NONE') out.cooldownType = dialogue.cooldownType;
    if (dialogue.resetTimeTicks > 0) out.resetTimeTicks = dialogue.resetTimeTicks;
    if (dialogue.visualConfig) out.visualConfig = dialogue.visualConfig;
    if (dialogue.npcBindings?.length) out.npcBindings = dialogue.npcBindings;
    if (dialogue.entityBindings?.length) out.entityBindings = dialogue.entityBindings;
    return out;
}
