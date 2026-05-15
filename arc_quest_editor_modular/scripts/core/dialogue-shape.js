import {setConditionNodeField} from './quest-shape-core.js';

function walkTextSpec(target, bindBase, value, inputType) {
    const field = bindBase.split('.').pop();
    if (field === 'mode') target.mode = value;
    else if (field === 'value') target.value = value;
}

export function setDialogueByPath(target, bind, value, inputType) {
    if (bind === 'diag.id') { target.id = value; return; }
    if (bind === 'diag.startNodeId') { target.startNodeId = value; return; }
    if (bind === 'diag.repeatable') { target.repeatable = value === 'true'; return; }
    if (bind === 'diag.cooldownType') { target.cooldownType = value; return; }
    if (bind === 'diag.cooldownSeconds') { target.cooldownSeconds = Number(value) || 0; return; }
    if (bind === 'diag.resetTimeTicks') { target.resetTimeTicks = Number(value) || 0; return; }

    if (bind.startsWith('diag.defaultNpc.')) {
        walkTextSpec(target.defaultNpc, bind, value, inputType);
        return;
    }

    if (bind.startsWith('diag.node.')) {
        const parts = bind.split('.');
        const ni = Number(parts[2]);
        if (!target.nodes[ni]) return;
        const node = target.nodes[ni];

        if (parts.length === 4) {
            const field = parts[3];
            if (field === 'nodeId') { node.nodeId = value; return; }
            if (field === 'autoNextId') { node.autoNextId = value; return; }
            if (field === 'delayMs') { node.delayMs = Number(value) || 0; return; }
            if (field === 'repeatable') { node.repeatable = value === 'true'; return; }
            if (field === 'cooldownType') { node.cooldownType = value; return; }
            if (field === 'cooldownSeconds') { node.cooldownSeconds = Number(value) || 0; return; }
            if (field === 'resetTimeTicks') { node.resetTimeTicks = Number(value) || 0; return; }
            if (field === 'nodeEnterSound') { node.nodeEnterSound = value; return; }
        }

        if (parts[3] === 'speaker' && parts.length >= 5) {
            walkTextSpec(node.speaker, parts.slice(4).join('.'), value, inputType);
            return;
        }
        if (parts[3] === 'text' && parts.length >= 5) {
            walkTextSpec(node.text, parts.slice(4).join('.'), value, inputType);
            return;
        }

        if (parts[3] === 'condText' && parts.length >= 5) {
            const mapKey = parts[4];
            const entry = node.conditionalTexts[mapKey];
            if (!entry) return;

            if (parts.length === 6 && parts[5] === 'sayId') { entry.sayId = value; return; }
            if (parts.length === 6 && parts[5] === 'soundEvent') { entry.soundEvent = value; return; }
            if (parts.length === 6 && parts[5] === 'priority') { entry.priority = Number(value) || 0; return; }

            if (parts[5] === 'text' && parts.length >= 7) {
                walkTextSpec(entry.text, parts.slice(6).join('.'), value, inputType);
                return;
            }

            if (parts[5] === 'cond' && parts.length >= 7) {
                const condIdx = Number(parts[6]);
                if (!entry.conditions) entry.conditions = [];
                if (!entry.conditions[condIdx]) entry.conditions[condIdx] = {condition: 'arc_quest:always'};
                if (parts.length >= 8) {
                    setConditionNodeField(entry.conditions[condIdx], parts.slice(7), value);
                }
                return;
            }
            return;
        }

        if (parts[3] === 'ch' && parts.length >= 5) {
            const ci = Number(parts[4]);
            if (!node.choices[ci]) return;
            const choice = node.choices[ci];

            if (parts.length === 6) {
                const field = parts[5];
                if (field === 'choiceId') { choice.choiceId = value; return; }
                if (field === 'nextNodeId') { choice.nextNodeId = value; return; }
                if (field === 'repeatable') { choice.repeatable = value === 'true'; return; }
                if (field === 'cooldownType') { choice.cooldownType = value; return; }
                if (field === 'cooldownSeconds') { choice.cooldownSeconds = Number(value) || 0; return; }
                if (field === 'resetTimeTicks') { choice.resetTimeTicks = Number(value) || 0; return; }
                if (field === 'priority') { choice.priority = Number(value) || 0; return; }
                if (field === 'restoreNodeId') { choice.restoreNodeId = value; return; }
                if (field === 'selectSound') { choice.selectSound = value; return; }
            }

            if (parts[5] === 'text' && parts.length >= 7) {
                walkTextSpec(choice.text, parts.slice(6).join('.'), value, inputType);
                return;
            }

            if (parts[5] === 'actions' && parts.length >= 7) {
                const ai = Number(parts[6]);
                if (!choice.actions[ai]) return;
                const action = choice.actions[ai];

                if (parts.length === 8) {
                    const field = parts[7];
                    if (action.hasOwnProperty(field)) {
                        if (inputType === 'number') action[field] = Number(value) || 0;
                        else action[field] = value;
                    }
                }
                return;
            }

            if (parts[5] === 'cond' && parts.length >= 7) {
                const condIdx = Number(parts[6]);
                if (!choice.conditions) choice.conditions = [];
                if (!choice.conditions[condIdx]) choice.conditions[condIdx] = {condition: 'arc_quest:always'};
                if (parts.length >= 8) {
                    setConditionNodeField(choice.conditions[condIdx], parts.slice(7), value);
                }
                return;
            }
            return;
        }

        if (parts[3] === 'cond' && parts.length >= 5) {
            const condIdx = Number(parts[4]);
            if (!node.conditions) node.conditions = [];
            if (!node.conditions[condIdx]) node.conditions[condIdx] = {condition: 'arc_quest:always'};
            if (parts.length >= 6) {
                setConditionNodeField(node.conditions[condIdx], parts.slice(5), value);
            }
            return;
        }
        return;
    }

    if (bind.startsWith('diag.npcBind.') || bind.startsWith('diag.entityBind.')) {
        const parts = bind.split('.');
        const idx = Number(parts[2]);
        const list = parts[1] === 'npcBind' ? target.npcBindings : target.entityBindings;
        if (!list[idx]) return;
        const field = parts[3];
        if (field === 'npcId' || field === 'entityType' || field === 'dialogueId') {
            list[idx][field] = value;
        }
    }
}
