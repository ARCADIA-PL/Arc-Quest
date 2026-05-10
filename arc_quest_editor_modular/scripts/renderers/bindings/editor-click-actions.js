import { createPhase, createObjective, createReward, createSplash, createTransition, createCollectionCategory, createRewardNode, createCompletionRule } from '../../core/factories.js';
import {
  ensureUniquePhaseId,
  ensureUniqueObjectiveId,
  ensureUniqueCategoryId,
  ensureUniqueCategoryNodeId,
  ensureUniqueTopNodeId,
  autoGeneratePhaseKeys,
  createNodeReward,
  createTopRewardNode,
  addChipValue,
  removeChipValue,
  fixCollectionReferences,
  fixCollectionRuleDefaults
} from './editor-helpers.js';

function cloneConditionNode(node) {
  return JSON.parse(JSON.stringify(node || { type: 'always' }));
}

function getConditionNodeByBind(root, bindBase) {
  const parts = bindBase.split('.');
  if (parts[0] === 'q' && parts[1] === 'uc') {
    let cursor = root.unlockConditions?.[Number(parts[2])];
    for (let i = 3; i < parts.length; i++) cursor = cursor?.[parts[i]];
    return cursor;
  }
  if (parts[0] === 'ph') {
    const phase = root.phases?.[Number(parts[1])];
    if (!phase) return null;
    if (parts[2] === 'ec') {
      let cursor = phase.rawEnterCondition;
      for (let i = 3; i < parts.length; i++) cursor = cursor?.[parts[i]];
      return cursor;
    }
    if (parts[2] === 'tr') {
      let cursor = phase.transitions?.[Number(parts[3])]?.condition;
      for (let i = 5; i < parts.length; i++) cursor = cursor?.[parts[i]];
      return cursor;
    }
    if (parts[2] === 'ch') {
      let cursor = phase.choices?.[Number(parts[3])]?.visibleCondition;
      for (let i = 5; i < parts.length; i++) cursor = cursor?.[parts[i]];
      return cursor;
    }
  }
  return null;
}

function getConditionParentRef(root, bindBase) {
  const parts = bindBase.split('.');
  if (parts[0] === 'q' && parts[1] === 'uc') {
    if (parts.length <= 3) return null;
    let cursor = root.unlockConditions?.[Number(parts[2])];
    for (let i = 3; i < parts.length - 1; i++) cursor = cursor?.[parts[i]];
    return { parent: cursor, key: parts[parts.length - 1] };
  }
  if (parts[0] === 'ph') {
    const phase = root.phases?.[Number(parts[1])];
    if (!phase) return null;
    if (parts[2] === 'ec') {
      if (parts.length <= 3) return null;
      let cursor = phase.rawEnterCondition;
      for (let i = 3; i < parts.length - 1; i++) cursor = cursor?.[parts[i]];
      return { parent: cursor, key: parts[parts.length - 1] };
    }
    if (parts[2] === 'tr') {
      if (parts.length <= 5) return null;
      let cursor = phase.transitions?.[Number(parts[3])]?.condition;
      for (let i = 5; i < parts.length - 1; i++) cursor = cursor?.[parts[i]];
      return { parent: cursor, key: parts[parts.length - 1] };
    }
    if (parts[2] === 'ch') {
      if (parts.length <= 5) return null;
      let cursor = phase.choices?.[Number(parts[3])]?.visibleCondition;
      for (let i = 5; i < parts.length - 1; i++) cursor = cursor?.[parts[i]];
      return { parent: cursor, key: parts[parts.length - 1] };
    }
  }
  return null;
}

function appendConditionBranch(root, descriptor) {
  const [bindBase, side] = String(descriptor || '').split(':');
  if (!bindBase || !side) return false;
  const node = getConditionNodeByBind(root, bindBase);
  if (!node || (node.type !== 'and' && node.type !== 'or')) return false;
  const existing = cloneConditionNode(node[side] || { type: 'always' });
  node[side] = { type: node.type, left: existing, right: { type: 'always' } };
  return true;
}

function deleteConditionBranch(root, bindBase) {
  const ref = getConditionParentRef(root, bindBase);
  if (!ref?.parent || !ref.key) return false;
  const parent = ref.parent;
  const key = ref.key;
  const siblingKey = key === 'left' ? 'right' : key === 'right' ? 'left' : null;

  if (parent.type === 'not' && key === 'left') {
    parent.left = { type: 'always' };
    return true;
  }

  if ((parent.type === 'and' || parent.type === 'or') && siblingKey) {
    const sibling = cloneConditionNode(parent[siblingKey] || { type: 'always' });
    Object.keys(parent).forEach(prop => delete parent[prop]);
    Object.assign(parent, sibling);
    return true;
  }

  parent[key] = { type: 'always' };
  return true;
}

export function handleClickPrelude(e, midEl, state, rerender) {
  const removeTarget = e.target.closest('[data-chip-remove]');
  if (removeTarget) {
    const [key, index] = String(removeTarget.dataset.chipRemove || '').split(':');
    removeChipValue(state.q, key, index);
    state.meta.dirty = true;
    rerender();
    return true;
  }

  const addTarget = e.target.closest('[data-chip-add]');
  if (addTarget) {
    const key = addTarget.dataset.chipAdd;
    const input = midEl.querySelector(`[data-chip-add-input="${key}"]`);
    if (input) {
      addChipValue(state.q, key, input.value);
      input.value = '';
      state.meta.dirty = true;
      rerender();
    }
    return true;
  }

  const appendConditionTarget = e.target.closest('[data-cond-append]');
  if (appendConditionTarget) {
    if (appendConditionBranch(state.q, appendConditionTarget.dataset.condAppend)) {
      state.meta.dirty = true;
      rerender();
    }
    return true;
  }

  const deleteConditionTarget = e.target.closest('[data-cond-delete]');
  if (deleteConditionTarget) {
    if (deleteConditionBranch(state.q, deleteConditionTarget.dataset.condDelete)) {
      state.meta.dirty = true;
      rerender();
    }
    return true;
  }

  const collectionViewBtn = e.target.closest('[data-collection-view]');
  if (collectionViewBtn) {
    state.ui.collectionView = collectionViewBtn.dataset.collectionView === 'tree' ? 'tree' : 'card';
    rerender();
    return true;
  }

  return false;
}

export function handleNonDeleteButtonAction(btn, state) {
  const d = btn.dataset;
  const id = btn.id;
  const s = state.ui.sel;
  const q = state.q;

  if (id === 'addPhaseBtn') {
    q.phases.push(createPhase(q.phases.length));
    const newPhaseIndex = q.phases.length - 1;
    ensureUniquePhaseId(q, newPhaseIndex);
    autoGeneratePhaseKeys(q, newPhaseIndex);
    state.ui.sel = { t: 'phase', pi: newPhaseIndex };
    return true;
  }
  if (id === 'fixCollectionRefsBtn') {
    fixCollectionReferences(q);
    return true;
  }
  if (id === 'fixCollectionRulesBtn') {
    fixCollectionRuleDefaults(q);
    return true;
  }
  if (id === 'addSplashBtn') {
    q.visualConfig.splashes.push(createSplash());
    return true;
  }
  if (id === 'addCategoryBtn') {
    q.collectionConfig ||= { categories: [], rewardNodes: [], completionRules: [] };
    q.collectionConfig.categories.push(createCollectionCategory(q.collectionConfig.categories.length));
    ensureUniqueCategoryId(q, q.collectionConfig.categories.length - 1);
    return true;
  }
  if (id === 'addTopCompletionRuleBtn') {
    q.collectionConfig ||= { categories: [], rewardNodes: [], completionRules: [] };
    q.collectionConfig.completionRules ||= [];
    q.collectionConfig.completionRules.push(createCompletionRule());
    return true;
  }
  if (id === 'addTopRewardNodeBtn') {
    q.collectionConfig ||= { categories: [], rewardNodes: [], completionRules: [] };
    q.collectionConfig.rewardNodes ||= [];
    q.collectionConfig.rewardNodes.push(createTopRewardNode(q.collectionConfig.rewardNodes.length));
    ensureUniqueTopNodeId(q, q.collectionConfig.rewardNodes.length - 1);
    return true;
  }
  if (d.atrcr !== undefined) {
    q.collectionConfig.rewardNodes[+d.atrcr].completionRules ||= [];
    q.collectionConfig.rewardNodes[+d.atrcr].completionRules.push(createCompletionRule());
    return true;
  }
  if (d.atrr !== undefined) {
    q.collectionConfig.rewardNodes[+d.atrr].rewards ||= [];
    q.collectionConfig.rewardNodes[+d.atrr].rewards.push(createNodeReward());
    return true;
  }
  if (d.acr !== undefined) {
    q.collectionConfig ||= { categories: [], rewardNodes: [], completionRules: [] };
    q.collectionConfig.categories[+d.acr].completionRules ||= [];
    q.collectionConfig.categories[+d.acr].completionRules.push(createCompletionRule());
    return true;
  }
  if (d.arn !== undefined) {
    q.collectionConfig ||= { categories: [], rewardNodes: [], completionRules: [] };
    q.collectionConfig.categories[+d.arn].rewardNodes ||= [];
    q.collectionConfig.categories[+d.arn].rewardNodes.push(createRewardNode(q.collectionConfig.categories[+d.arn].rewardNodes.length));
    ensureUniqueCategoryNodeId(q, +d.arn, q.collectionConfig.categories[+d.arn].rewardNodes.length - 1);
    return true;
  }
  if (d.arcr !== undefined) {
    const [ci, ri] = d.arcr.split(':');
    q.collectionConfig.categories[+ci].rewardNodes[+ri].completionRules ||= [];
    q.collectionConfig.categories[+ci].rewardNodes[+ri].completionRules.push(createCompletionRule());
    return true;
  }
  if (d.arr) {
    const [ci, ri] = d.arr.split(':');
    q.collectionConfig.categories[+ci].rewardNodes[+ri].rewards ||= [];
    q.collectionConfig.categories[+ci].rewardNodes[+ri].rewards.push(createNodeReward());
    return true;
  }
  if (id === 'addQuestRewardBtn') {
    q.rewards.push(createReward());
    return true;
  }
  if (id === 'addUnlockConditionBtn') {
    q.unlockConditions ||= [];
    q.unlockConditions.push({ type: 'always' });
    return true;
  }
  if (id === 'addObjectiveBtn') {
    q.phases[s.pi].objectives.push(createObjective(q.phases[s.pi].objectives.length));
    const newObjectiveIndex = q.phases[s.pi].objectives.length - 1;
    ensureUniqueObjectiveId(q, s.pi, newObjectiveIndex);
    autoGeneratePhaseKeys(q, s.pi);
    state.ui.sel = { t: 'obj', pi: s.pi, oi: newObjectiveIndex };
    return true;
  }
  if (id === 'addPhaseRewardBtn') {
    q.phases[s.pi].rewards.push(createReward());
    return true;
  }
  if (id === 'addTransitionBtn') {
    q.phases[s.pi].transitions.push(createTransition());
    return true;
  }
  if (id === 'addChoiceBtn') {
    q.phases[s.pi].choices ||= [];
    q.phases[s.pi].choices.push({ text: '', flagToSet: '', targetPhaseId: '', visibleCondition: { type: 'always' } });
    return true;
  }
  if (id === 'movePhaseUpBtn' && s.pi > 0) {
    [q.phases[s.pi - 1], q.phases[s.pi]] = [q.phases[s.pi], q.phases[s.pi - 1]];
    state.ui.sel = { t: 'phase', pi: s.pi - 1 };
    return true;
  }
  if (id === 'movePhaseDownBtn' && s.pi < q.phases.length - 1) {
    [q.phases[s.pi + 1], q.phases[s.pi]] = [q.phases[s.pi], q.phases[s.pi + 1]];
    state.ui.sel = { t: 'phase', pi: s.pi + 1 };
    return true;
  }
  if (d.open !== undefined) {
    state.ui.sel = { t: 'obj', pi: s.pi, oi: +d.open };
    return true;
  }

  return false;
}
