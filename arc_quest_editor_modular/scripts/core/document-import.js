import {normalizeImportedQuest} from './import-normalizer.js';
import {normalizeImportedDialogue} from './dialogue-normalizer.js';
import {normalizeImportedNpc} from './npc-normalizer.js';
import {normalizeImportedTrade} from './trade-normalizer.js';
import {normalizeImportedGacha} from './gacha-normalizer.js';
import {normalizeImportedGuide} from './guide-normalizer.js';
import {createRegistry, importToRegistry} from './registry.js';
import {validateCrossReferences} from './cross-validator.js';

export function detectJsonType(json) {
    if (Array.isArray(json?.nodes)) return 'dialogue';
    if (json?.entityType && Array.isArray(json.bindings)) return 'npc';
    if (json?.shopId && Array.isArray(json.pools)) return 'gacha';
    if (json?.entries && json.shopId) return 'trade';
    if (Array.isArray(json?.pages) && json.category && json.title) return 'guide';
    if (Array.isArray(json?.phases)) return 'quest';
    if (json?.displayName && Object.hasOwn(json, 'iconTexture') && !json.pages) return 'guideCategory';
    if (json?.id && ['initialPhaseId', 'completionPolicy', 'unlockConditions'].some(key => Object.hasOwn(json, key))) return 'quest';
    return 'unknown';
}

const NORMALIZERS = new Map([
    ['quest', normalizeImportedQuest], ['dialogue', normalizeImportedDialogue], ['npc', normalizeImportedNpc],
    ['trade', normalizeImportedTrade], ['gacha', normalizeImportedGacha],
    ['guide', normalizeImportedGuide], ['guideCategory', normalizeImportedGuide]
]);

// 解析、规范化与交叉检查全部成功后才提交，失败不能留下半次导入。
export function prepareDocumentImport(state, json, filename, targetMode = state.mode) {
    const type = detectJsonType(json);
    const normalizer = NORMALIZERS.get(type);
    if (!normalizer) throw new Error('无法识别 JSON 类型');
    const document = normalizer(json, type);
    const mode = type === 'guideCategory' ? 'guide' : type;
    const activate = mode === targetMode && state.mode === targetMode;
    const registry = createRegistry();
    for (const key of ['quests', 'dialogues', 'npcs', 'shops', 'gachas', 'guides']) {
        Object.assign(registry[key], state.registry[key]);
    }
    for (const key of ['dialogue', 'npc']) Object.assign(registry.npcBindings[key], state.registry.npcBindings?.[key]);
    importToRegistry({registry}, document, type);
    const quest = activate && mode === 'quest' ? document : state.quest.q;
    const crossResults = validateCrossReferences(registry, quest);
    return {type, mode, activate, document, registry, crossResults, filename};
}

export function commitDocumentImport(state, prepared) {
    state.registry = prepared.registry;
    state.quest.crossResults = prepared.crossResults;
    if (!prepared.activate) return;
    const editor = state[prepared.mode];
    editor.q = prepared.document;
    editor.meta = {file: prepared.filename, dirty: false};
    editor.diag = [];
    editor.ui.sel = {t: prepared.mode === 'quest' ? 'quest' : prepared.mode === 'dialogue' ? 'config' : 'overview'};
    if (prepared.mode === 'guide') editor.kind = prepared.type;
    if (prepared.mode === 'npc') {
        editor.ui.condFold = false;
        editor.ui.cmdFold = false;
    }
}
