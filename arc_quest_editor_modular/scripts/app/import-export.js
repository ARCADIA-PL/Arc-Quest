import {validateQuest} from '../core/validators.js';
import {validateNpc} from '../core/npc-validators.js';
import {validateDialogue} from '../core/dialogue-validators.js';
import {validateTrade} from '../core/trade-validators.js';
import {normalizeImportedQuest} from '../core/import-normalizer.js';
import {normalizeImportedNpc, exportNpcToDatapack} from '../core/npc-normalizer.js';
import {normalizeImportedDialogue, exportDialogueToDatapack} from '../core/dialogue-normalizer.js';
import {normalizeImportedTrade, exportTradeToDatapack} from '../core/trade-normalizer.js';
import {exportQuestToDatapack} from '../core/export-normalizer.js';
import {importToRegistry} from '../core/registry.js';
import {showToast, setDropOverlayVisible} from './toast.js';
import {validateCrossReferences} from '../core/cross-validator.js';

function detectJsonType(json) {
    if (json && json.nodes && Array.isArray(json.nodes)) return 'dialogue';
    if (json && json.entityType && Array.isArray(json.bindings)) return 'npc';
    if (json && json.entries && json.shopId) return 'trade';
    if (json && (Array.isArray(json.phases) || json.id)) return 'quest';
    return 'unknown';
}

function getTypeLabel(type) {
    return {quest: 'Quest', npc: 'NPC', dialogue: 'Dialogue', trade: 'Trade'}[type] || '未知';
}

function exportBlob(json, filename) {
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([JSON.stringify(json, null, 2)], {type: 'application/json'}));
    a.download = filename;
    a.click();
}

export function exportJson(state, rerender, dom) {
    if (state.mode === 'npc') {
        const diag = validateNpc(state.npc.q);
        const blockingErrors = diag.filter(x => x.lvl === 'err');
        if (blockingErrors.length > 0) {
            state.quest.ui.tab = 'validate';
            rerender();
            showToast(dom, '导出已阻止', `存在 ${blockingErrors.length} 个错误`, 'error', 3600);
            return;
        }
        const exported = exportNpcToDatapack(state.npc.q);
        const filename = (state.npc.q.entityType || 'unnamed') + '_npc.json';
        exportBlob(exported, filename);
        state.npc.meta.dirty = false;
        state.npc.meta.file = filename;
        rerender();
        return;
    }

    if (state.mode === 'dialogue') {
        const diag = validateDialogue(state.dialogue.q);
        const blockingErrors = diag.filter(x => x.lvl === 'err');
        if (blockingErrors.length > 0) {
            state.quest.ui.tab = 'validate';
            rerender();
            showToast(dom, '导出已阻止', `存在 ${blockingErrors.length} 个错误`, 'error', 3600);
            return;
        }
        const exported = exportDialogueToDatapack(state.dialogue.q);
        const filename = (state.dialogue.q.id || 'unnamed') + '_dialogue.json';
        exportBlob(exported, filename);
        state.dialogue.meta.dirty = false;
        state.dialogue.meta.file = filename;
        rerender();
        return;
    }

    if (state.mode === 'trade') {
        const diag = validateTrade(state.trade.q);
        const blockingErrors = diag.filter(x => x.lvl === 'err');
        if (blockingErrors.length > 0) {
            state.quest.ui.tab = 'validate';
            rerender();
            showToast(dom, '导出已阻止', `存在 ${blockingErrors.length} 个错误`, 'error', 3600);
            return;
        }
        const exported = exportTradeToDatapack(state.trade.q);
        const filename = (state.trade.q.shopId || 'unnamed') + '_shop.json';
        exportBlob(exported, filename);
        state.trade.meta.dirty = false;
        state.trade.meta.file = filename;
        rerender();
        return;
    }

    validateQuest(state);
    const blockingErrors = (state.quest.diag || []).filter(x => x.lvl === 'err');
    if (blockingErrors.length > 0) {
        state.quest.ui.tab = 'validate';
        rerender();
        showToast(dom, '导出已阻止', `存在 ${blockingErrors.length} 个错误，请先修复后再导出。`, 'error', 3600);
        return;
    }

    const exported = exportQuestToDatapack(state.quest.q);
    exportBlob(exported, state.quest.meta.file);
    state.quest.meta.dirty = false;
    rerender();
}

export function importJson(state, rerender, dom, file) {
    const r = new FileReader();
    const isLibraryImport = dom.fileInput?.dataset?.libraryImport === 'true';
    r.onload = () => {
        try {
            const json = JSON.parse(r.result);
            const detectedType = detectJsonType(json);

            if (isLibraryImport) {
                delete dom.fileInput.dataset.libraryImport;
                if (detectedType === 'unknown') {
                    showToast(dom, '无法识别', 'JSON 类型未知，无法导入到注册表', 'error', 3600);
                    return;
                }
                if (detectedType === 'quest') importToRegistry(state, normalizeImportedQuest(json), 'quest');
                else if (detectedType === 'npc') importToRegistry(state, normalizeImportedNpc(json), 'npc');
                else if (detectedType === 'trade') importToRegistry(state, normalizeImportedTrade(json), 'trade');
                else importToRegistry(state, json, 'dialogue');
                state.quest.crossResults = validateCrossReferences(state.registry, state.quest.q);
                showToast(dom, '已导入到库', `${file.name} → ${getTypeLabel(detectedType)} 注册表`, 'info');
                return;
            }

            if (detectedType === 'npc') {
                const normalized = normalizeImportedNpc(json);
                importToRegistry(state, normalized, 'npc');
                if (state.mode !== 'npc') {
                    showToast(dom, '已导入注册表', `NPC JSON 已加入注册表（当前在 ${state.mode} 模式）`, 'info');
                } else {
                    state.npc.q = normalized;
                    state.npc.meta = {file: file.name, dirty: false};
                    state.npc.ui.sel = {t: 'overview'};
                    state.npc.ui.condFold = false;
                    state.npc.ui.cmdFold = false;
                    state.quest.crossResults = validateCrossReferences(state.registry, state.quest.q);
                    rerender();
                    showToast(dom, '导入成功', `已载入 ${file.name}`, 'success');
                }
                return;
            }

            if (detectedType === 'dialogue') {
                const normalized = normalizeImportedDialogue(json);
                importToRegistry(state, normalized, 'dialogue');
                if (state.mode !== 'dialogue') {
                    showToast(dom, '已导入注册表', `Dialogue JSON 已加入注册表（当前在 ${state.mode} 模式）`, 'info');
                } else {
                    state.dialogue.q = normalized;
                    state.dialogue.meta = {file: file.name, dirty: false};
                    state.quest.crossResults = validateCrossReferences(state.registry, state.quest.q);
                    rerender();
                    showToast(dom, '导入成功', `已载入 ${file.name}`, 'success');
                }
                return;
            }

            if (detectedType === 'trade') {
                const normalized = normalizeImportedTrade(json);
                importToRegistry(state, normalized, 'trade');
                if (state.mode !== 'trade') {
                    showToast(dom, '已导入注册表', `Trade JSON 已加入注册表（当前在 ${state.mode} 模式）`, 'info');
                } else {
                    state.trade.q = normalized;
                    state.trade.meta = {file: file.name, dirty: false};
                    state.trade.ui.sel = {t: 'overview'};
                    state.quest.crossResults = validateCrossReferences(state.registry, state.quest.q);
                    rerender();
                    showToast(dom, '导入成功', `已载入 ${file.name}`, 'success');
                }
                return;
            }

            if (detectedType === 'unknown') {
                showToast(dom, '无法识别', 'JSON 类型未知', 'error', 3600);
                return;
            }

            const normalized = normalizeImportedQuest(json);
            importToRegistry(state, normalized, 'quest');

            if (state.mode !== 'quest') {
                showToast(dom, '已导入注册表', `Quest JSON 已加入注册表（当前在 ${state.mode} 模式）`, 'info');
                return;
            }

            state.quest.q = normalized;
            state.quest.meta = {file: file.name, dirty: false};
            state.quest.ui.sel = {t: 'quest'};
            state.quest.crossResults = validateCrossReferences(state.registry, state.quest.q);
            rerender();
            showToast(dom, '导入成功', `已载入 ${file.name}`, 'success');
        } catch (err) {
            showToast(dom, '导入失败', `JSON 解析失败：${err.message}`, 'error', 3600);
            alert('JSON 解析失败: ' + err.message);
        }
    };
    r.readAsText(file, 'utf-8');
}

export function importToLibrary(state, rerender, dom, file) {
    const r = new FileReader();
    r.onload = () => {
        try {
            const json = JSON.parse(r.result);
            const detectedType = detectJsonType(json);
            if (detectedType === 'quest') importToRegistry(state, normalizeImportedQuest(json), 'quest');
            else if (detectedType === 'npc') importToRegistry(state, normalizeImportedNpc(json), 'npc');
            else if (detectedType === 'dialogue') importToRegistry(state, normalizeImportedDialogue(json), 'dialogue');
            else if (detectedType === 'trade') importToRegistry(state, normalizeImportedTrade(json), 'trade');
            else {
                showToast(dom, '无法识别', 'JSON 类型未知', 'error', 3600);
                return;
            }
            showToast(dom, '已导入到库', `${file.name} → ${getTypeLabel(detectedType)} 注册表`, 'info');
        } catch (err) {
            showToast(dom, '导入失败', `JSON 解析失败：${err.message}`, 'error', 3600);
            alert('JSON 解析失败: ' + err.message);
        }
    };
    r.readAsText(file, 'utf-8');
}

export function getFirstSupportedFile(fileList) {
    return [...(fileList || [])].find(item => item?.name?.toLowerCase().endsWith('.json') || item?.type === 'application/json') || null;
}

export function importFirstSupportedFile(state, rerender, dom, fileList) {
    const file = getFirstSupportedFile(fileList);
    if (!file) {
        showToast(dom, '无法导入', '请拖入 JSON 文件，而不是其它格式。', 'error', 3200);
        alert('请拖入 JSON 文件。');
        return;
    }
    importJson(state, rerender, dom, file);
}

export function bindDragAndDropImport(state, rerender, dom) {
    if (!dom.appRoot) return;
    let dragDepth = 0;

    function getDropLabel() {
        if (state.mode === 'npc') return '拖入 NPC JSON 或任意 JSON 入库';
        if (state.mode === 'dialogue') return '拖入 Dialogue JSON 或任意 JSON 入库';
        if (state.mode === 'trade') return '拖入 Trade JSON 或任意 JSON 入库';
        return '松开以导入 Arc Quest 任务文件';
    }
    function getDropNoFile() {
        if (state.mode === 'npc') return '请拖入 .json 文件';
        if (state.mode === 'dialogue') return '请拖入 .json 文件';
        if (state.mode === 'trade') return '请拖入 .json 文件';
        return '请拖入 .json quest 文件';
    }

    window.addEventListener('dragenter', e => {
        e.preventDefault();
        dragDepth += 1;
        const hasSupportedFile = getFirstSupportedFile(e.dataTransfer?.files);
        setDropOverlayVisible(dom, true, hasSupportedFile ? getDropLabel() : getDropNoFile());
    });

    window.addEventListener('dragover', e => {
        e.preventDefault();
        if (e.dataTransfer) e.dataTransfer.dropEffect = 'copy';
        const hasSupportedFile = getFirstSupportedFile(e.dataTransfer?.files);
        setDropOverlayVisible(dom, true, hasSupportedFile ? getDropLabel() : getDropNoFile());
    });

    window.addEventListener('dragleave', e => {
        e.preventDefault();
        dragDepth = Math.max(0, dragDepth - 1);
        if (dragDepth === 0) setDropOverlayVisible(dom, false);
    });

    window.addEventListener('drop', e => {
        e.preventDefault();
        dragDepth = 0;
        setDropOverlayVisible(dom, false);
        importFirstSupportedFile(state, rerender, dom, e.dataTransfer?.files);
    });
}
