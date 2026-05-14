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

export function bindEditorInputs(midEl, state, rerender, setByPath) {
    midEl.onchange = e => {
        const b = e.target.dataset.b;
        if (!b) return;
        markInputValidity(e.target, b);
        const beforeType = b.startsWith('ob.') && b.endsWith('.type') ? state.quest.q.phases[state.quest.ui.sel.pi].objectives[state.quest.ui.sel.oi].type : null;
        const isQuestRewardTypeChange = b.startsWith('rw.quest.') && b.endsWith('.type');
        const isPhaseRewardTypeChange = b.startsWith('rw.phase.') && b.endsWith('.type');
        const phaseIdChange = b.startsWith('ph.') && b.endsWith('.id');
        const phaseIdChangeParts = phaseIdChange ? b.split('.') : null;
        const phaseIndex = phaseIdChange ? Number(phaseIdChangeParts[1]) : -1;
        const oldPhaseId = phaseIdChange ? state.quest.q.phases?.[phaseIndex]?.id : null;
        const resolvedValue = e.target.multiple
            ? Array.from(e.target.selectedOptions || []).map(option => option.value).filter(Boolean).join(', ')
            : e.target.value;

        const meta = setByPath(state.quest.q, b, resolvedValue, e.target.type);

        if (meta?.fileName) state.quest.meta.file = meta.fileName;

        if (phaseIdChange) {
            const newPhaseId = state.quest.q.phases?.[phaseIndex]?.id;
            syncPhaseIdReferences(state.quest.q, oldPhaseId, newPhaseId);
            ensureUniquePhaseId(state.quest.q, phaseIndex);
        }

        if (b.startsWith('ob.') && b.endsWith('.id')) {
            const [, pi, oi] = b.split('.');
            ensureUniqueObjectiveId(state.quest.q, Number(pi), Number(oi));
        }

        if (b.startsWith('q.cat.') && b.endsWith('.categoryId')) {
            const parts = b.split('.');
            ensureUniqueCategoryId(state.quest.q, Number(parts[2]));
        }

        if (b.startsWith('q.cat.') && b.includes('.rn.') && b.endsWith('.nodeId')) {
            const parts = b.split('.');
            ensureUniqueCategoryNodeId(state.quest.q, Number(parts[2]), Number(parts[4]));
        }

        if (b.startsWith('q.trn.') && b.endsWith('.nodeId')) {
            const parts = b.split('.');
            ensureUniqueTopNodeId(state.quest.q, Number(parts[2]));
        }

        applyAutoKeysByBind(state.quest.q, b);

        if (beforeType && beforeType !== e.target.value) {
            const obj = state.quest.q.phases[state.quest.ui.sel.pi].objectives[state.quest.ui.sel.oi];
            state.quest.q.phases[state.quest.ui.sel.pi].objectives[state.quest.ui.sel.oi] = normalizeObjectiveByType(obj);
        }

        if (isQuestRewardTypeChange) {
            const parts = b.split('.');
            const rewardIndex = Number(parts[2]);
            state.quest.q.rewards[rewardIndex] = normalizeRewardByType(state.quest.q.rewards[rewardIndex]);
        }

        if (isPhaseRewardTypeChange) {
            const parts = b.split('.');
            const phaseIndexForReward = Number(parts[2]);
            const rewardIndex = Number(parts[3]);
            state.quest.q.phases[phaseIndexForReward].rewards[rewardIndex] = normalizeRewardByType(state.quest.q.phases[phaseIndexForReward].rewards[rewardIndex]);
        }

        state.quest.meta.dirty = true;
        rerender();
    };

    midEl.oninput = e => {
        const b = e.target.dataset?.b;
        if (!b) return;
        markInputValidity(e.target, b);
    };

    midEl.onkeydown = e => {
        const chipKey = e.target.dataset?.chipAddInput;
        if (!chipKey) return;
        if (e.key !== 'Enter') return;
        e.preventDefault();
        addChipValue(state.quest.q, chipKey, e.target.value);
        e.target.value = '';
        state.quest.meta.dirty = true;
        rerender();
    };
}
