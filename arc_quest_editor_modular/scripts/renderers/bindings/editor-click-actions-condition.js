export function bindConditionEditorClicks(state, descriptor, action) {
    if (action === 'append') {
        return appendConditionBranch(state.q, descriptor);
    }
    if (action === 'delete') {
        return deleteConditionBranch(state.q, descriptor);
    }
    return false;
}

function cloneConditionNode(node) {
    return JSON.parse(JSON.stringify(node || {condition: 'arc_quest:always'}));
}

function getConditionNodeByBind(root, bindBase) {
    const parts = bindBase.split('.');
    if (parts[0] === 'q' && parts[1] === 'uc') {
        let cursor = root.unlockConditions?.[Number(parts[2])];
        for (let i = 3; i < parts.length; i++) {
            const key = parts[i];
            if (key === 'conditions') {
                const idx = Number(parts[i + 1]);
                cursor = cursor?.conditions?.[idx];
                i++;
            } else {
                cursor = cursor?.[key];
            }
        }
        return cursor;
    }
    if (parts[0] === 'ph') {
        const phase = root.phases?.[Number(parts[1])];
        if (!phase) return null;
        if (parts[2] === 'ec') {
            let cursor = phase.rawEnterCondition;
            for (let i = 3; i < parts.length; i++) {
                const key = parts[i];
                if (key === 'conditions') {
                    const idx = Number(parts[i + 1]);
                    cursor = cursor?.conditions?.[idx];
                    i++;
                } else {
                    cursor = cursor?.[key];
                }
            }
            return cursor;
        }
        if (parts[2] === 'tr') {
            let cursor = phase.transitions?.[Number(parts[3])]?.condition;
            for (let i = 5; i < parts.length; i++) {
                const key = parts[i];
                if (key === 'conditions') {
                    const idx = Number(parts[i + 1]);
                    cursor = cursor?.conditions?.[idx];
                    i++;
                } else {
                    cursor = cursor?.[key];
                }
            }
            return cursor;
        }
        if (parts[2] === 'ch') {
            let cursor = phase.choices?.[Number(parts[3])]?.visibleCondition;
            for (let i = 5; i < parts.length; i++) {
                const key = parts[i];
                if (key === 'conditions') {
                    const idx = Number(parts[i + 1]);
                    cursor = cursor?.conditions?.[idx];
                    i++;
                } else {
                    cursor = cursor?.[key];
                }
            }
            return cursor;
        }
    }
    return null;
}

function getConditionParentRef(root, bindBase) {
    const parts = bindBase.split('.');
    if (parts[0] === 'q' && parts[1] === 'uc') {
        if (parts.length <= 3) return null;
        let cursor = root.unlockConditions?.[Number(parts[2])];
        for (let i = 3; i < parts.length - 1; i++) {
            const key = parts[i];
            if (key === 'conditions') {
                const idx = Number(parts[i + 1]);
                cursor = cursor?.conditions?.[idx];
                i++;
            } else {
                cursor = cursor?.[key];
            }
        }
        const lastKey = parts[parts.length - 1];
        if (lastKey === 'conditions') {
            return {parent: cursor, key: 'conditions', isArray: true};
        }
        return {parent: cursor, key: lastKey};
    }
    if (parts[0] === 'ph') {
        const phase = root.phases?.[Number(parts[1])];
        if (!phase) return null;
        const startIdx = parts[2] === 'ec' ? 3 : parts[2] === 'tr' ? 5 : parts[2] === 'ch' ? 5 : 0;
        if (parts.length <= startIdx) return null;

        let cursor;
        if (parts[2] === 'ec') {
            cursor = phase.rawEnterCondition;
        } else if (parts[2] === 'tr') {
            cursor = phase.transitions?.[Number(parts[3])]?.condition;
        } else if (parts[2] === 'ch') {
            cursor = phase.choices?.[Number(parts[3])]?.visibleCondition;
        }
        if (!cursor) return null;

        for (let i = startIdx; i < parts.length - 1; i++) {
            const key = parts[i];
            if (key === 'conditions') {
                const idx = Number(parts[i + 1]);
                cursor = cursor?.conditions?.[idx];
                i++;
            } else {
                cursor = cursor?.[key];
            }
        }
        const lastKey = parts[parts.length - 1];
        if (lastKey === 'conditions') {
            return {parent: cursor, key: 'conditions', isArray: true};
        }
        return {parent: cursor, key: lastKey};
    }
    return null;
}

function appendConditionBranch(root, bindBase) {
    const node = getConditionNodeByBind(root, bindBase);
    if (!node || (node.condition !== 'arc_quest:and' && node.condition !== 'arc_quest:or')) return false;
    node.conditions ||= [];
    node.conditions.push({condition: 'arc_quest:always'});
    return true;
}

function deleteConditionBranch(root, bindBase) {
    const ref = getConditionParentRef(root, bindBase);
    if (!ref?.parent || !ref.key) return false;
    const parent = ref.parent;
    const key = ref.key;

    if (parent.condition === 'arc_quest:not' && key === 'inner') {
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

    parent[key] = {condition: 'arc_quest:always'};
    return true;
}