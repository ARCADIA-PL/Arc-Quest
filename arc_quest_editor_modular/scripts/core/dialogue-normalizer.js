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

export function exportDialogueToDatapack(dialogue) {
    const exported = JSON.parse(JSON.stringify(dialogue));
    cleanEmptyFields(exported);
    return exported;
}
