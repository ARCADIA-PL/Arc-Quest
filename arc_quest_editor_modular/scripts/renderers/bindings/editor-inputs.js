import {
    syncPhaseIdReferences,
    ensureUniquePhaseId,
    ensureUniqueObjectiveId,
    ensureUniqueCategoryId,
    ensureUniqueCategoryNodeId,
    ensureUniqueTopNodeId,
    applyAutoKeysByBind,
    normalizeObjectiveByType,
    normalizeRewardByType,
    addChipValue,
    markInputValidity
} from './editor-helpers.js';

function isQuestRootState(state) {
    return !!state?.quest?.q && !!state?.quest?.meta && !!state?.quest?.ui;
}

function getModuleContext(state) {
    if (isQuestRootState(state)) {
        return {
            isQuest: true,
            questState: state.quest,
            data: state.quest.q,
            meta: state.quest.meta,
            ui: state.quest.ui
        };
    }

    return {
        isQuest: false,
        questState: null,
        data: state.q,
        meta: state.meta,
        ui: state.ui
    };
}

export function bindEditorInputs(midEl, state, rerender, setByPath) {
    const ctx = getModuleContext(state);

    midEl.onchange = e => {
        const b = e.target.dataset.b;
        if (!b) return;
        markInputValidity(e.target, b);

        const beforeType = ctx.isQuest && b.startsWith('ob.') && b.endsWith('.type')
            ? ctx.data.phases[ctx.ui.sel.pi].objectives[ctx.ui.sel.oi].type
            : null;
        const isQuestRewardTypeChange = ctx.isQuest && b.startsWith('rw.quest.') && b.endsWith('.type');
        const isPhaseRewardTypeChange = ctx.isQuest && b.startsWith('rw.phase.') && b.endsWith('.type');
        const phaseIdChange = ctx.isQuest && b.startsWith('ph.') && b.endsWith('.id');
        const phaseIdChangeParts = phaseIdChange ? b.split('.') : null;
        const phaseIndex = phaseIdChange ? Number(phaseIdChangeParts[1]) : -1;
        const oldPhaseId = phaseIdChange ? ctx.data.phases?.[phaseIndex]?.id : null;
        const resolvedValue = e.target.type === 'checkbox'
            ? String(e.target.checked)
            : e.target.dataset.bArray !== undefined
            ? e.target.value.split('\n').map(s => s.trim()).filter(Boolean)
            : e.target.multiple
            ? Array.from(e.target.selectedOptions || []).map(option => option.value).filter(Boolean).join(', ')
            : e.target.value;

        const meta = setByPath(ctx.data, b, resolvedValue, e.target.type);

        if (meta?.fileName) ctx.meta.file = meta.fileName;

        if (phaseIdChange) {
            const newPhaseId = ctx.data.phases?.[phaseIndex]?.id;
            syncPhaseIdReferences(ctx.data, oldPhaseId, newPhaseId);
            ensureUniquePhaseId(ctx.data, phaseIndex);
        }

        if (ctx.isQuest && b.startsWith('ob.') && b.endsWith('.id')) {
            const [, pi, oi] = b.split('.');
            ensureUniqueObjectiveId(ctx.data, Number(pi), Number(oi));
        }

        if (ctx.isQuest && b.startsWith('q.cat.') && b.endsWith('.categoryId')) {
            const parts = b.split('.');
            ensureUniqueCategoryId(ctx.data, Number(parts[2]));
        }

        if (ctx.isQuest && b.startsWith('q.cat.') && b.includes('.rn.') && b.endsWith('.nodeId')) {
            const parts = b.split('.');
            ensureUniqueCategoryNodeId(ctx.data, Number(parts[2]), Number(parts[4]));
        }

        if (ctx.isQuest && b.startsWith('q.trn.') && b.endsWith('.nodeId')) {
            const parts = b.split('.');
            ensureUniqueTopNodeId(ctx.data, Number(parts[2]));
        }

        if (ctx.isQuest) {
            applyAutoKeysByBind(ctx.data, b);
        }

        if (beforeType && beforeType !== e.target.value) {
            const obj = ctx.data.phases[ctx.ui.sel.pi].objectives[ctx.ui.sel.oi];
            ctx.data.phases[ctx.ui.sel.pi].objectives[ctx.ui.sel.oi] = normalizeObjectiveByType(obj);
        }

        if (isQuestRewardTypeChange) {
            const parts = b.split('.');
            const rewardIndex = Number(parts[2]);
            ctx.data.rewards[rewardIndex] = normalizeRewardByType(ctx.data.rewards[rewardIndex]);
        }

        if (isPhaseRewardTypeChange) {
            const parts = b.split('.');
            const phaseIndexForReward = Number(parts[2]);
            const rewardIndex = Number(parts[3]);
            ctx.data.phases[phaseIndexForReward].rewards[rewardIndex] = normalizeRewardByType(ctx.data.phases[phaseIndexForReward].rewards[rewardIndex]);
        }

        ctx.meta.dirty = true;
        rerender();
    };

    midEl.oninput = e => {
        const b = e.target.dataset?.b;
        const cpBind = e.target.dataset?.colorPicker;
        if (!b && !cpBind) return;
        if (b) markInputValidity(e.target, b);
        if (cpBind) {
            const hex = e.target.value;
            const intVal = parseInt(hex.slice(1), 16) | 0xFF000000;
            const dataInput = midEl.querySelector(`[data-b="${cpBind}"]`);
            const row = e.target.closest('.color-input-row');
            if (dataInput) {
                dataInput.value = intVal;
                dataInput.dispatchEvent(new Event('change', {bubbles: true}));
            }
            const swatch = row?.querySelector('.color-swatch');
            if (swatch) swatch.style.background = hex;
        }
    };

    midEl.onkeydown = e => {
        if (!ctx.isQuest) return;
        const chipKey = e.target.dataset?.chipAddInput;
        if (!chipKey) return;
        if (e.key !== 'Enter') return;
        e.preventDefault();
        addChipValue(ctx.data, chipKey, e.target.value);
        e.target.value = '';
        ctx.meta.dirty = true;
        rerender();
    };
}
