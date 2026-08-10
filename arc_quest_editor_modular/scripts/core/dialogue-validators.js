import {validateMarkers} from './validators.js';

function validateConditionNode(node, path, d) {
    if (!node) return;
    if (!node.condition || node.condition === 'arc_quest:always') return;
    if (node.condition === 'arc_quest:hold_item') {
        if (!node.itemId || !node.itemId.trim()) {
            d.push({lvl: 'err', path: `${path}.itemId`, msg: 'hold_item requires itemId'});
        }
        if (!Number.isFinite(Number(node.count)) || Number(node.count) < 1) {
            d.push({lvl: 'err', path: `${path}.count`, msg: 'hold_item count must be at least 1'});
        }
        const itemSource = node.itemSource || 'hands';
        if (itemSource !== 'hands' && itemSource !== 'inventory') {
            d.push({lvl: 'err', path: `${path}.itemSource`, msg: 'hold_item itemSource must be hands or inventory'});
        }
    }
    if (node.inner) validateConditionNode(node.inner, `${path}.inner`, d);
    if (node.conditions) node.conditions.forEach((c, i) => validateConditionNode(c, `${path}.conditions.${i}`, d));
}

export function validateDialogue(dialogue) {
    const d = [];
    const nodes = dialogue.nodes || [];

    if (!dialogue.id || !dialogue.id.trim()) {
        d.push({lvl: 'err', path: 'id', msg: '对话 ID 不能为空'});
    }
    if (nodes.length === 0) {
        d.push({lvl: 'err', path: 'nodes', msg: '至少需要一个对话节点'});
    }

    const nodeIds = new Set();
    for (let i = 0; i < nodes.length; i++) {
        const node = nodes[i];
        const p = `nodes[${i}]`;
        if (!node.nodeId || !node.nodeId.trim()) {
            d.push({lvl: 'err', path: `${p}.nodeId`, msg: '节点 ID 不能为空'});
        } else if (nodeIds.has(node.nodeId)) {
            d.push({lvl: 'err', path: `${p}.nodeId`, msg: `节点 ID 重复: ${node.nodeId}`});
        } else {
            nodeIds.add(node.nodeId);
        }
        if (node.speaker && node.speaker.mode && !['literal', 'translatable'].includes(node.speaker.mode)) {
            d.push({lvl: 'err', path: `${p}.speaker.mode`, msg: `文本 mode 必须是 literal 或 translatable`});
        }
        if (node.text && (!node.text.value || !node.text.value.trim())) {
            d.push({lvl: 'warn', path: `${p}.text.value`, msg: '节点文本为空'});
        }
        validateMarkers(node.relatedMarks, `${p}.relatedMarks`, d,
            ['CONTINUOUS', 'DIALOGUE_NODE_ENTERED']);
        if (node.conditionalTexts) {
            for (const [key, say] of Object.entries(node.conditionalTexts)) {
                if (!say) continue;
                const cp = `${p}.conditionalTexts.${key}`;
                if (!say.sayId || !say.sayId.trim()) d.push({lvl: 'warn', path: `${cp}.sayId`, msg: 'conditional say ID 为空'});
                if (key.includes('.')) d.push({lvl: 'warn', path: cp, msg: `Map key 不能包含 "." 字符`});
                if (say.conditions) say.conditions.forEach((c, ci) => validateConditionNode(c, `${cp}.conditions[${ci}]`, d));
            }
        }
    }

    if (dialogue.startNodeId && !nodeIds.has(dialogue.startNodeId)) {
        d.push({lvl: 'err', path: 'startNodeId', msg: `起始节点 "${dialogue.startNodeId}" 不在节点列表中`});
    }

    for (let i = 0; i < nodes.length; i++) {
        const node = nodes[i];
        const p = `nodes[${i}]`;
        if (node.autoNextId && !nodeIds.has(node.autoNextId)) {
            d.push({lvl: 'err', path: `${p}.autoNextId`, msg: `目标节点 "${node.autoNextId}" 不在节点列表中`});
        }
        for (let ci = 0; ci < (node.choices || []).length; ci++) {
            const choice = node.choices[ci];
            const cp = `${p}.choices[${ci}]`;
            if (!choice.choiceId || !choice.choiceId.trim()) {
                d.push({lvl: 'err', path: `${cp}.choiceId`, msg: '选项 ID 不能为空'});
            }
            if (choice.nextNodeId && !nodeIds.has(choice.nextNodeId)) {
                d.push({lvl: 'err', path: `${cp}.nextNodeId`, msg: `目标节点 "${choice.nextNodeId}" 不在节点列表中`});
            }
            if (choice.restoreNodeId && !nodeIds.has(choice.restoreNodeId)) {
                d.push({lvl: 'err', path: `${cp}.restoreNodeId`, msg: `恢复节点 "${choice.restoreNodeId}" 不在节点列表中`});
            }
            validateMarkers(choice.relatedMarks, `${cp}.relatedMarks`, d,
                ['CONTINUOUS', 'DIALOGUE_CHOICE_SELECTED']);
            for (let ai = 0; ai < (choice.actions || []).length; ai++) {
                validateAction(choice.actions[ai], `${cp}.actions[${ai}]`, d);
            }
        }
    }

    return d;
}

const ACTION_TYPES = new Set([
    'start_quest', 'complete_quest', 'advance_phase', 'give_xp', 'give_item',
    'notify_talk', 'notify_interact', 'no_op', 'close', 'run_command',
    'set_flag', 'set_variable', 'open_trade', 'open_simple_trade', 'open_gacha', 'custom'
]);

function validateAction(action, path, d) {
    if (!action) return;
    if (!action.type || !ACTION_TYPES.has(action.type)) {
        d.push({lvl: 'err', path: `${path}.type`, msg: `Action type 非法: ${action.type || '<空>'}`});
        return;
    }
    switch (action.type) {
        case 'start_quest': case 'complete_quest': case 'advance_phase':
            if (!action.questId) d.push({lvl: 'err', path, msg: `${action.type} 需要 questId`}); break;
        case 'give_item':
            if (!action.itemId) d.push({lvl: 'err', path, msg: 'give_item 需要 itemId'}); break;
        case 'notify_talk':
            if (!action.npcId) d.push({lvl: 'err', path, msg: 'notify_talk 需要 npcId'}); break;
        case 'notify_interact':
            if (!action.targetId) d.push({lvl: 'err', path, msg: 'notify_interact 需要 targetId'}); break;
        case 'run_command':
            if (!action.command) d.push({lvl: 'err', path, msg: 'run_command 需要 command'}); break;
        case 'set_flag':
            if (!action.flagName) d.push({lvl: 'err', path, msg: 'set_flag 需要 flagName'}); break;
        case 'set_variable':
            if (!action.key) d.push({lvl: 'err', path, msg: 'set_variable 需要 key'}); break;
        case 'open_trade': case 'open_simple_trade': case 'open_gacha':
            if (!action.shopId) d.push({lvl: 'err', path, msg: `${action.type} 需要 shopId`});
            break;
        case 'custom':
            if (!action.customTypeId) d.push({lvl: 'err', path, msg: 'custom action 需要 customTypeId'}); break;
    }
}
