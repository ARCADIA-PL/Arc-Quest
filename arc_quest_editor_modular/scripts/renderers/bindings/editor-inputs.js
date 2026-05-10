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
    const beforeType = b.startsWith('ob.') && b.endsWith('.type') ? state.q.phases[state.ui.sel.pi].objectives[state.ui.sel.oi].type : null;
    const isQuestRewardTypeChange = b.startsWith('rw.quest.') && b.endsWith('.type');
    const isPhaseRewardTypeChange = b.startsWith('rw.phase.') && b.endsWith('.type');
    const phaseIdChange = b.startsWith('ph.') && b.endsWith('.id');
    const phaseIdChangeParts = phaseIdChange ? b.split('.') : null;
    const phaseIndex = phaseIdChange ? Number(phaseIdChangeParts[1]) : -1;
    const oldPhaseId = phaseIdChange ? state.q.phases?.[phaseIndex]?.id : null;
    const resolvedValue = e.target.multiple
      ? Array.from(e.target.selectedOptions || []).map(option => option.value).filter(Boolean).join(', ')
      : e.target.value;

    const meta = setByPath(state.q, b, resolvedValue, e.target.type);

    if (meta?.fileName) state.meta.file = meta.fileName;

    if (phaseIdChange) {
      const newPhaseId = state.q.phases?.[phaseIndex]?.id;
      syncPhaseIdReferences(state.q, oldPhaseId, newPhaseId);
      ensureUniquePhaseId(state.q, phaseIndex);
    }

    if (b.startsWith('ob.') && b.endsWith('.id')) {
      const [, pi, oi] = b.split('.');
      ensureUniqueObjectiveId(state.q, Number(pi), Number(oi));
    }

    if (b.startsWith('q.cat.') && b.endsWith('.categoryId')) {
      const parts = b.split('.');
      ensureUniqueCategoryId(state.q, Number(parts[2]));
    }

    if (b.startsWith('q.cat.') && b.includes('.rn.') && b.endsWith('.nodeId')) {
      const parts = b.split('.');
      ensureUniqueCategoryNodeId(state.q, Number(parts[2]), Number(parts[4]));
    }

    if (b.startsWith('q.trn.') && b.endsWith('.nodeId')) {
      const parts = b.split('.');
      ensureUniqueTopNodeId(state.q, Number(parts[2]));
    }

    applyAutoKeysByBind(state.q, b);

    if (beforeType && beforeType !== e.target.value) {
      const obj = state.q.phases[state.ui.sel.pi].objectives[state.ui.sel.oi];
      state.q.phases[state.ui.sel.pi].objectives[state.ui.sel.oi] = normalizeObjectiveByType(obj);
    }

    if (isQuestRewardTypeChange) {
      const parts = b.split('.');
      const rewardIndex = Number(parts[2]);
      state.q.rewards[rewardIndex] = normalizeRewardByType(state.q.rewards[rewardIndex]);
    }

    if (isPhaseRewardTypeChange) {
      const parts = b.split('.');
      const phaseIndexForReward = Number(parts[2]);
      const rewardIndex = Number(parts[3]);
      state.q.phases[phaseIndexForReward].rewards[rewardIndex] = normalizeRewardByType(state.q.phases[phaseIndexForReward].rewards[rewardIndex]);
    }

    state.meta.dirty = true;
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
    addChipValue(state.q, chipKey, e.target.value);
    e.target.value = '';
    state.meta.dirty = true;
    rerender();
  };
}
