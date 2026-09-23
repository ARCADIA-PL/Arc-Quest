import {cloneJson} from './json-document.js';

export const CONDITION_TYPES = new Set([
    'always', 'quest_completed', 'quest_accepted', 'quest_not_started', 'has_quest', 'quest_failed',
    'quest_phase', 'quest_phase_completed', 'quest_phase_reached', 'phase_enterable',
    'phase_before', 'phase_after', 'phase_between', 'any_active_in_range', 'all_completed_in_range',
    'has_flag', 'not_has_flag', 'variable_check', 'hold_item', 'has_effect', 'xp_level',
    'entity_nbt', 'entity_name', 'dialogue_completed', 'dialogue_on_cooldown',
    'node_visited', 'node_on_cooldown', 'choice_selected', 'choice_on_cooldown',
    'is_morning', 'is_afternoon', 'is_night', 'game_time_in_range', 'custom', 'and', 'or', 'not'
].map(type => `arc_quest:${type}`).concat('minecraft:entity_properties'));

const COMPARISON_SYMBOLS = Object.freeze({
    EQUAL: '==', NOT_EQUAL: '!=', GREATER: '>', GREATER_OR_EQUAL: '>=', LESS: '<', LESS_OR_EQUAL: '<='
});
const QUEST_TYPES = new Set(['always', 'quest_completed', 'quest_accepted', 'quest_not_started',
    'quest_phase', 'quest_phase_completed', 'quest_phase_reached', 'has_flag', 'not_has_flag',
    'variable_check', 'and', 'or', 'not', 'has_effect', 'xp_level']);
const ENTITY_TYPES = new Set(['always', 'quest_completed', 'quest_accepted', 'quest_not_started',
    'has_quest', 'quest_failed', 'quest_phase', 'quest_phase_completed', 'quest_phase_reached',
    'phase_before', 'phase_after', 'phase_between', 'has_flag', 'not_has_flag', 'variable_check',
    'is_morning', 'is_afternoon', 'is_night', 'game_time_in_range', 'and', 'or', 'not', 'entity_nbt', 'entity_name']);
const DIALOGUE_UNSUPPORTED = new Set(['entity_nbt', 'entity_name', 'has_effect', 'xp_level']);

export const validateQuestCondition = (value, path, diagnostics) => validateConditionNode(value, path, diagnostics, 'quest');
export const validateDialogueCondition = (value, path, diagnostics) => validateConditionNode(value, path, diagnostics, 'dialogue');
export const validateEntityCondition = (value, path, diagnostics) => validateConditionNode(value, path, diagnostics, 'entity');

// ConditionSpec 字段按原值保留；未知条件不能变成 always，也不能删除谓词/附属参数。
export function normalizeCondition(value) {
    const source = cloneJson(value);
    function visit(node) {
        if (node == null) return {condition: 'arc_quest:always'};
        if (typeof node !== 'object' || Array.isArray(node)) throw new Error('条件必须是 JSON 对象');
        if (!Object.hasOwn(node, 'condition')) {
            if (node.type) return visit(convertLegacy(node));
            if (Object.keys(node).length) throw new Error('条件缺少 condition');
            return {condition: 'arc_quest:always'};
        }
        if (typeof node.condition !== 'string' || !node.condition.trim()) throw new Error('condition 必须是非空字符串');
        // Java CompareOp.fromSymbol 只接受符号；旧编辑器的枚举名会被误当成等于。
        if (node.condition === 'arc_quest:variable_check' && Object.hasOwn(COMPARISON_SYMBOLS, node.op)) {
            node.op = COMPARISON_SYMBOLS[node.op];
        }
        if (node.condition === 'arc_quest:and' || node.condition === 'arc_quest:or') {
            if (node.conditions != null && !Array.isArray(node.conditions)) throw new Error('conditions 必须是数组');
            node.conditions = normalizeChildren(node.conditions || []);
        } else if (node.condition === 'arc_quest:not' && node.inner != null) node.inner = visit(node.inner);
        return node;
    }
    function normalizeChildren(children) {
        return children.map(child => {
            if (child == null) throw new Error('子条件不能为 null');
            return visit(child);
        });
    }
    function convertLegacy(node) {
        switch (node.type) {
            case 'always': return {condition: 'arc_quest:always'};
            case 'flag_set': return {condition: 'arc_quest:has_flag', flag: node.flag ?? ''};
            case 'flag_not_set': return {condition: 'arc_quest:not_has_flag', flag: node.flag ?? ''};
            case 'quest_completed': return {condition: 'arc_quest:quest_completed', questId: node.questId ?? ''};
            case 'variable': return {condition: 'arc_quest:variable_check', key: node.variable ?? '',
                op: node.compareOp ?? 'EQUAL', value: node.value ?? 0};
            case 'not': return {condition: 'arc_quest:not', inner: node.left == null ? null : visit(node.left)};
            case 'and': case 'or': return {condition: `arc_quest:${node.type}`,
                conditions: [node.left, node.right].filter(child => child != null).map(visit)};
            default: throw new Error(`不支持的旧条件类型: ${node.type}`);
        }
    }
    return visit(source);
}

export const exportCondition = normalizeCondition;

export function normalizeConditionList(value, path = 'conditions') {
    if (value == null) return [];
    if (!Array.isArray(value)) throw new Error(`${path} 必须是数组`);
    return value.map(condition => {
        if (condition == null) throw new Error(`${path} 子条件不能为 null`);
        return normalizeCondition(condition);
    });
}

export function validateConditionNode(value, path, diagnostics, context = null) {
    if (value == null) return;
    let root;
    try {
        root = cloneJson(value, path);
    } catch (error) {
        diagnostics.push({lvl: 'err', path, msg: error.message});
        return;
    }
    const issue = (location, msg, lvl = 'err') => diagnostics.push({lvl, path: location, msg});
    const requiredString = (node, key, location) => {
        if (typeof node[key] !== 'string' || !node[key].trim()) issue(`${location}.${key}`, `缺少有效 ${key}`);
    };
    function visit(node, location) {
        if (!node || typeof node !== 'object' || Array.isArray(node)) return issue(location, '条件必须是 JSON 对象');
        const type = node.condition;
        if (typeof type !== 'string' || !/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(type)) {
            return issue(`${location}.condition`, 'condition 必须是有效的命名空间 ID');
        }
        if (!CONDITION_TYPES.has(type)) issue(location, `未验证条件 ${type}，将原样保留；请确认游戏端支持`, 'warn');
        const name = type.replace(/^arc_quest:/, '');
        if (CONDITION_TYPES.has(type) && ((context === 'quest' && !QUEST_TYPES.has(name) && !type.startsWith('minecraft:'))
            || (context === 'entity' && !ENTITY_TYPES.has(name) && !type.startsWith('minecraft:'))
            || (context === 'dialogue' && (DIALOGUE_UNSUPPORTED.has(name) || type.startsWith('minecraft:'))))) {
            issue(location, `当前 ${context} 内置运行时未直接支持 ${type}，请确认附属实现`, 'warn');
        }
        if (type.startsWith('arc_quest:') && CONDITION_TYPES.has(type)) {
            for (const key of ['value', 'count', 'startTick', 'endTick', 'cooldownSeconds']) {
                if (node[key] != null && (!Number.isInteger(node[key]) || node[key] < -2147483648 || node[key] > 2147483647)) {
                    issue(`${location}.${key}`, `${key} 必须在运行时支持的 32 位整数范围内`);
                }
            }
        }
        if (['quest_completed', 'quest_accepted', 'quest_not_started', 'has_quest', 'quest_failed',
            'quest_phase', 'quest_phase_completed', 'quest_phase_reached', 'phase_enterable',
            'phase_before', 'phase_after', 'phase_between', 'any_active_in_range', 'all_completed_in_range'].includes(name)) {
            requiredString(node, 'questId', location);
        }
        if (['quest_phase', 'quest_phase_completed', 'quest_phase_reached', 'phase_enterable'].includes(name)) requiredString(node, 'phaseId', location);
        if (['phase_before', 'phase_after'].includes(name)) requiredString(node, 'targetPhaseId', location);
        if (['phase_between', 'any_active_in_range', 'all_completed_in_range'].includes(name)) {
            requiredString(node, 'fromPhaseId', location);
            requiredString(node, 'toPhaseId', location);
        }
        if (['has_flag', 'not_has_flag'].includes(name)) requiredString(node, 'flag', location);
        if (name === 'variable_check') {
            requiredString(node, 'key', location);
            if (node.op != null && !Object.hasOwn(COMPARISON_SYMBOLS, node.op)
                && !Object.values(COMPARISON_SYMBOLS).includes(node.op)) issue(`${location}.op`, '无效比较运算符');
            if (!Number.isInteger(node.value ?? 0)) issue(`${location}.value`, '变量比较值必须是整数');
        }
        if (name === 'hold_item') {
            requiredString(node, 'itemId', location);
            if (!Number.isInteger(node.count ?? 1) || (node.count ?? 1) < 1) issue(`${location}.count`, '物品数量必须是正整数');
            if (!['hands', 'inventory'].includes(node.itemSource || 'hands')) issue(`${location}.itemSource`, 'itemSource 仅支持 hands 或 inventory');
        }
        if (name === 'custom') requiredString(node, 'name', location);
        if (name === 'has_effect') requiredString(node, 'effectId', location);
        if (name === 'entity_nbt') requiredString(node, 'nbtKey', location);
        if (name === 'entity_name') requiredString(node, 'namePattern', location);
        if (name.startsWith('dialogue_')) requiredString(node, 'dialogueId', location);
        if (name.startsWith('node_')) requiredString(node, 'nodeId', location);
        if (name.startsWith('choice_')) requiredString(node, 'choiceId', location);
        if (type === 'minecraft:entity_properties' && (!node.predicate || typeof node.predicate !== 'object' || Array.isArray(node.predicate))) {
            issue(`${location}.predicate`, 'predicate 必须是 JSON 对象');
        }
        if (name === 'and' || name === 'or') {
            if (node.conditions != null && !Array.isArray(node.conditions)) issue(`${location}.conditions`, 'conditions 必须是数组');
            else (node.conditions || []).forEach((child, index) => visit(child, `${location}.conditions[${index}]`));
        } else if (name === 'not') {
            if (node.inner == null) issue(`${location}.inner`, 'not 条件缺少 inner');
            else visit(node.inner, `${location}.inner`);
        }
    }
    visit(root, path);
}
