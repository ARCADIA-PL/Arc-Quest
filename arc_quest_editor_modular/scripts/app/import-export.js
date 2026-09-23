import {prepareDocumentImport, commitDocumentImport} from '../core/document-import.js';
import {parseDocument, MAX_DOCUMENT_BYTES} from '../core/json-document.js';
import {validateQuest} from '../core/validators.js';
import {validateNpc} from '../core/npc-validators.js';
import {validateDialogue} from '../core/dialogue-validators.js';
import {validateTrade} from '../core/trade-validators.js';
import {validateGacha} from '../core/gacha-validators.js';
import {exportNpcToDatapack} from '../core/npc-normalizer.js';
import {exportDialogueToDatapack} from '../core/dialogue-normalizer.js';
import {exportTradeToDatapack} from '../core/trade-normalizer.js';
import {exportGachaToDatapack} from '../core/gacha-normalizer.js';
import {exportQuestToDatapack} from '../core/export-normalizer.js';
import {exportGuideToDatapack} from '../core/guide-normalizer.js';
import {validateGuide} from '../core/guide-validators.js';
import {showToast, setDropOverlayVisible} from './toast.js';

export {detectJsonType} from '../core/document-import.js';

function getTypeLabel(type) {
    return {quest: 'Quest', npc: 'NPC', dialogue: 'Dialogue', trade: 'Trade', gacha: 'Gacha', guide: 'Guide', guideCategory: 'Guide Category'}[type] || '未知';
}

function exportBlob(json, filename) {
    const a = document.createElement('a');
    const url = URL.createObjectURL(new Blob([JSON.stringify(json, null, 2)], {type: 'application/json'}));
    try {
        a.href = url;
        a.download = filename;
        a.click();
    } finally {
        setTimeout(() => URL.revokeObjectURL(url), 0);
    }
}

export function exportJson(state, rerender, dom) {
    try {
        exportCurrentDocument(state, rerender, dom);
    } catch (error) {
        showToast(dom, '导出失败', error.message, 'error', 3600);
    }
}

function exportCurrentDocument(state, rerender, dom) {
    if (state.mode === 'guide') {
        const diag = validateGuide(state.guide.q, state.guide.kind);
        const blockingErrors = diag.filter(issue => issue.lvl === 'err');
        if (blockingErrors.length) {
            state.guide.diag = diag;
            rerender();
            showToast(dom, 'Export blocked', `${blockingErrors.length} schema errors`, 'error', 3600);
            return;
        }
        exportBlob(exportGuideToDatapack(state.guide.q, state.guide.kind), state.guide.meta.file);
        state.guide.meta.dirty = false;
        rerender();
        return;
    }
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

    if (state.mode === 'gacha') {
        const diag = validateGacha(state.gacha.q);
        const blockingErrors = diag.filter(x => x.lvl === 'err');
        if (blockingErrors.length > 0) {
            state.quest.ui.tab = 'validate';
            rerender();
            showToast(dom, '导出已阻止', `存在 ${blockingErrors.length} 个错误`, 'error', 3600);
            return;
        }
        const exported = exportGachaToDatapack(state.gacha.q);
        const filename = (state.gacha.q.shopId || 'unnamed') + '_gacha.json';
        exportBlob(exported, filename);
        state.gacha.meta.dirty = false;
        state.gacha.meta.file = filename;
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

const pendingImports = new WeakMap();

function readImportedFile(state, rerender, dom, file, libraryOnly) {
    if (!file) return;
    if (!libraryOnly) {
        const previous = pendingImports.get(state);
        pendingImports.delete(state);
        previous?.abort();
    }
    if (file.size > MAX_DOCUMENT_BYTES) {
        showToast(dom, '导入失败', 'JSON 文件不能超过 8 MiB', 'error', 3600);
        return;
    }
    const reader = new FileReader();
    const targetMode = libraryOnly ? null : state.mode;
    if (!libraryOnly) pendingImports.set(state, reader);
    const isCurrent = () => libraryOnly || pendingImports.get(state) === reader;
    const finish = () => {
        if (pendingImports.get(state) === reader) pendingImports.delete(state);
    };
    const fail = message => {
        if (!isCurrent()) return;
        finish();
        showToast(dom, '导入失败', message, 'error', 3600);
    };
    reader.onerror = () => fail(`无法读取 ${file.name}`);
    reader.onabort = () => {
        if (!isCurrent()) return;
        finish();
        showToast(dom, '导入已取消', file.name, 'info');
    };
    reader.onload = () => {
        if (!isCurrent()) return;
        let prepared;
        try {
            prepared = prepareDocumentImport(state, parseDocument(reader.result), file.name, targetMode);
        } catch (error) {
            fail(error.message);
            return;
        }
        finish();
        commitDocumentImport(state, prepared);
        rerender();
        showToast(dom, prepared.activate ? '导入成功' : '已导入注册表',
            `${file.name} → ${getTypeLabel(prepared.type)}`, 'success');
    };
    try {
        reader.readAsText(file, 'utf-8');
    } catch (error) {
        fail(error.message);
    }
}

export function importJson(state, rerender, dom, file) {
    readImportedFile(state, rerender, dom, file, false);
}

export function importToLibrary(state, rerender, dom, file) {
    readImportedFile(state, rerender, dom, file, true);
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
        if (state.mode === 'gacha') return '拖入 Gacha JSON 或任意 JSON 入库';
        return '松开以导入 Arc Quest 任务文件';
    }
    function getDropNoFile() {
        if (state.mode === 'npc') return '请拖入 .json 文件';
        if (state.mode === 'dialogue') return '请拖入 .json 文件';
        if (state.mode === 'trade') return '请拖入 .json 文件';
        if (state.mode === 'gacha') return '请拖入 .json 文件';
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
