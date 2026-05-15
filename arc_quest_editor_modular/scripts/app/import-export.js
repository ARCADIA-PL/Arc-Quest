import {validateQuest} from '../core/validators.js';
import {normalizeImportedQuest} from '../core/import-normalizer.js';
import {exportQuestToDatapack} from '../core/export-normalizer.js';
import {importToRegistry} from '../core/registry.js';
import {showToast, setDropOverlayVisible} from './toast.js';
import {validateCrossReferences} from '../core/cross-validator.js';

export function exportJson(state, rerender, dom) {
    validateQuest(state);
    const blockingErrors = (state.quest.diag || []).filter(x => x.lvl === 'err');
    if (blockingErrors.length > 0) {
        state.quest.ui.tab = 'validate';
        rerender();
        showToast(dom, '导出已阻止', `存在 ${blockingErrors.length} 个错误，请先修复后再导出。`, 'error', 3600);
        return;
    }

    const exported = exportQuestToDatapack(state.quest.q);
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([JSON.stringify(exported, null, 2)], {type: 'application/json'}));
    a.download = state.quest.meta.file;
    a.click();
    state.quest.meta.dirty = false;
    rerender();
}

export function importJson(state, rerender, dom, file) {
    const r = new FileReader();
    const isLibraryImport = dom.fileInput?.dataset?.libraryImport === 'true';
    r.onload = () => {
        try {
            const json = JSON.parse(r.result);
            const normalized = normalizeImportedQuest(json);
            importToRegistry(state, normalized, 'quest');

            if (isLibraryImport) {
                delete dom.fileInput.dataset.libraryImport;
                state.quest.crossResults = validateCrossReferences(state.registry, state.quest.q);
                showToast(dom, '已导入到库', `${file.name} 已加入注册表`, 'info');
            } else {
                state.quest.q = normalized;
                state.quest.meta.file = file.name;
                state.quest.meta.dirty = false;
                state.quest.ui.sel = {t: 'quest'};
                state.quest.crossResults = validateCrossReferences(state.registry, state.quest.q);
                rerender();
                showToast(dom, '导入成功', `已载入 ${file.name}`, 'success');
            }
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
            const normalized = normalizeImportedQuest(json);
            importToRegistry(state, normalized, 'quest');
            showToast(dom, '已导入到库', `${file.name} 已加入注册表`, 'info');
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
        showToast(dom, '无法导入', '请拖入 quest JSON 文件，而不是其它格式。', 'error', 3200);
        alert('请拖入 quest JSON 文件。');
        return;
    }
    importJson(state, rerender, dom, file);
}

export function bindDragAndDropImport(state, rerender, dom) {
    if (!dom.appRoot) return;
    let dragDepth = 0;

    window.addEventListener('dragenter', e => {
        e.preventDefault();
        dragDepth += 1;
        const hasSupportedFile = getFirstSupportedFile(e.dataTransfer?.files);
        setDropOverlayVisible(dom, true, hasSupportedFile ? '松开以导入 Arc Quest 任务文件' : '请拖入 .json quest 文件');
    });

    window.addEventListener('dragover', e => {
        e.preventDefault();
        if (e.dataTransfer) e.dataTransfer.dropEffect = 'copy';
        const hasSupportedFile = getFirstSupportedFile(e.dataTransfer?.files);
        setDropOverlayVisible(dom, true, hasSupportedFile ? '松开以导入 Arc Quest 任务文件' : '请拖入 .json quest 文件');
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
