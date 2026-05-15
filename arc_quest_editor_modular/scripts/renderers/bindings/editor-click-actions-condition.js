export function bindConditionEditorClicks(state, descriptor, action, skipRerender = false) {
    let target;
    if (descriptor.startsWith('npc.')) {
        target = state.npc.q;
    } else if (descriptor.startsWith('diag.')) {
        target = state.dialogue.q;
    } else {
        target = state.quest.q;
    }

    if (action === 'append') {
        return appendConditionBranch(target, descriptor);
    }
    if (action === 'delete') {
        return deleteConditionBranch(target, descriptor);
    }
    return false;
}

function cloneConditionNode(node) {
    return JSON.parse(JSON.stringify(node || {condition: 'arc_quest:always'}));
}

function navigateConditionPath(root, bindBase) {
    const parts = bindBase.split('.');
    let cursor = root;

    if (parts[0] === 'q' && parts[1] === 'uc') {
        cursor = root.unlockConditions?.[Number(parts[2])];
        return followConditionPath(cursor, parts, 3);
    }
    if (parts[0] === 'ph') {
        const phase = root.phases?.[Number(parts[1])];
        if (!phase) return null;
        if (parts[2] === 'ec') {
            return followConditionPath(phase.rawEnterCondition, parts, 3);
        }
        if (parts[2] === 'tr') {
            return followConditionPath(phase.transitions?.[Number(parts[3])]?.condition, parts, 5);
        }
        if (parts[2] === 'ch') {
            return followConditionPath(phase.choices?.[Number(parts[3])]?.visibleCondition, parts, 5);
        }
    }
    if (parts[0] === 'npc') {
        if (parts[1] === 'interactCond') {
            return followConditionPath(root.interactCondition, parts, 2);
        }
        if (parts[1] === 'bind') {
            const binding = root.bindings?.[Number(parts[2])];
            if (!binding) return null;
            if (parts[3] === 'condition') {
                return followConditionPath(binding.condition, parts, 4);
            }
        }
    }
    if (parts[0] === 'diag') {
        if (parts[1] === 'node') {
            const node = root.nodes?.[Number(parts[2])];
            if (!node) return null;
            const subType = parts[3];
            if (subType === 'condText') {
                const sayIf = node.conditionalTexts?.[parts[4]];
                if (!sayIf) return null;
                if (parts[5] === 'cond') {
                    return followConditionPath(sayIf.conditions?.[Number(parts[6])], parts, 7);
                }
                return followConditionPath(sayIf, parts, 5);
            }
            if (subType === 'ch') {
                const choice = node.choices?.[Number(parts[4])];
                if (!choice) return null;
                if (parts[5] === 'cond') {
                    return followConditionPath(choice.conditions?.[Number(parts[6])], parts, 7);
                }
                return followConditionPath(choice, parts, 5);
            }
        }
    }
    return null;
}

function followConditionPath(cursor, parts, startIdx) {
    if (!cursor) return null;
    let c = cursor;
    for (let i = startIdx; i < parts.length; i++) {
        const key = parts[i];
        if (key === 'conditions') {
            const idx = Number(parts[i + 1]);
            c = c?.conditions?.[idx];
            i++;
        } else if (key === 'inner') {
            c = c?.inner;
        } else {
            c = c?.[key];
        }
    }
    return c;
}

function findConditionParent(target, cursor, parts, startIdx) {
    if (!cursor) return null;
    let parent = target;
    let c = cursor;
    let lastKey = parts[startIdx];
    for (let i = startIdx; i < parts.length - 1; i++) {
        const key = parts[i];
        parent = c;
        if (key === 'conditions') {
            const idx = Number(parts[i + 1]);
            c = c?.conditions?.[idx];
            i++;
        } else if (key === 'inner') {
            c = c?.inner;
        } else {
            c = c?.[key];
        }
        lastKey = parts[i + 1];
    }
    return {parent, key: lastKey, cursor: c};
}

function getConditionParentRef(root, bindBase) {
    const parts = bindBase.split('.');
    if (parts[0] === 'q' && parts[1] === 'uc') {
        let cursor = root.unlockConditions?.[Number(parts[2])];
        return findConditionParent(root, cursor, parts, 3);
    }
    if (parts[0] === 'ph') {
        const phase = root.phases?.[Number(parts[1])];
        if (!phase) return null;
        if (parts[2] === 'ec') {
            return findConditionParent(phase, phase.rawEnterCondition, parts, 3);
        }
        if (parts[2] === 'tr') {
            return findConditionParent(phase, phase.transitions?.[Number(parts[3])]?.condition, parts, 5);
        }
        if (parts[2] === 'ch') {
            return findConditionParent(phase, phase.choices?.[Number(parts[3])]?.visibleCondition, parts, 5);
        }
    }
    if (parts[0] === 'npc') {
        if (parts[1] === 'interactCond') {
            return findConditionParent(root, root.interactCondition, parts, 2);
        }
        if (parts[1] === 'bind') {
            const binding = root.bindings?.[Number(parts[2])];
            if (!binding) return null;
            return findConditionParent(binding, binding.condition, parts, 4);
        }
    }
    if (parts[0] === 'diag' && parts[1] === 'node') {
        const node = root.nodes?.[Number(parts[2])];
        if (!node) return null;
        if (parts[3] === 'condText') {
            const sayIf = node.conditionalTexts?.[parts[4]];
            if (!sayIf) return null;
            return findConditionParent(sayIf, sayIf.conditions?.[Number(parts[6])], parts, 7);
        }
        if (parts[3] === 'ch') {
            const choice = node.choices?.[Number(parts[4])];
            if (!choice) return null;
            return findConditionParent(choice, choice.conditions?.[Number(parts[6])], parts, 7);
        }
    }
    return null;
}

function appendConditionBranch(root, bindBase) {
    const node = navigateConditionPath(root, bindBase);
    if (!node || (node.condition !== 'arc_quest:and' && node.condition !== 'arc_quest:or')) return false;
    node.conditions ||= [];
    node.conditions.push({condition: 'arc_quest:always'});
    return true;
}

function deleteConditionBranch(root, bindBase) {
    const ref = getConditionParentRef(root, bindBase);
    if (!ref?.parent || !ref.key) return false;
    const parent = ref.parent;

    if (parent.condition === 'arc_quest:not' && ref.key === 'inner') {
        parent.inner = {condition: 'arc_quest:always'};
        return true;
    }

    if ((parent.condition === 'arc_quest:and' || parent.condition === 'arc_quest:or') && Array.isArray(parent.conditions)) {
        const idx = Number(bindBase.split('.').pop());
        if (!isNaN(idx) && idx >= 0 && idx < parent.conditions.length) {
            parent.conditions.splice(idx, 1);
            if (parent.conditions.length === 0) {
                parent.conditions = [{condition: 'arc_quest:always'}];
            }
            return true;
        }
    }

    parent[ref.key] = {condition: 'arc_quest:always'};
    return true;
}
