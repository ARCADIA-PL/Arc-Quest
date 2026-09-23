import {validateEntityCondition as validateConditionNode} from './condition-codec.js';



export function validateNpc(npc) {
    const d = [];
    const policies = new Set(['PARALLEL_PRIVATE', 'EXCLUSIVE', 'QUEUED', 'GROUP_SHARED', 'SPECTATE_SHARED']);

    if (!npc.entityType || !npc.entityType.trim()) {
        d.push({lvl: 'err', path: 'entityType', msg: '实体类型 (entityType) 不能为空'});
    }
    if (typeof npc.dialogueDistance === 'number' && npc.dialogueDistance < 1.0) {
        d.push({lvl: 'warn', path: 'dialogueDistance', msg: '对话距离应至少为 1.0'});
    }

    const interactionPolicy = npc.interactionPolicy || 'PARALLEL_PRIVATE';
    if (!policies.has(interactionPolicy)) {
        d.push({lvl: 'err', path: 'interactionPolicy', msg: 'NPC \u4ea4\u4e92\u7b56\u7565\u65e0\u6548'});
    } else if (!['PARALLEL_PRIVATE', 'EXCLUSIVE'].includes(interactionPolicy)) {
        d.push({lvl: 'err', path: 'interactionPolicy', msg: '\u8be5\u7b56\u7565\u4e3a\u9884\u7559\u503c\uff0c\u5f53\u524d\u8fd0\u884c\u65f6\u5c1a\u672a\u5b9e\u73b0'});
    }

    for (let i = 0; i < (npc.bindings || []).length; i++) {
        const b = npc.bindings[i];
        const hasDialogue = b.dialogueId || b.dialogueIdFromNbt;
        if (!hasDialogue) {
            d.push({lvl: 'err', path: `bindings[${i}]`, msg: '绑定必须指定 dialogueId 或 dialogueIdFromNbt'});
        }
        if (b.condition) {
            validateConditionNode(b.condition, `bindings[${i}].condition`, d);
        }
    }

    if (npc.interactCondition) {
        validateConditionNode(npc.interactCondition, 'interactCondition', d);
    }

    return d;
}
