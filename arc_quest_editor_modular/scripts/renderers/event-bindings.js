import { createPhase, createObjective, createReward, createSplash, createTransition, createCollectionCategory, createRewardNode, createCompletionRule } from '../core/factories.js';

function createNodeReward() {
  return { type: 'item', itemId: 'minecraft:iron_ingot', count: 1 };
}

function createTopRewardNode(index) {
  return { nodeId: `arc_quest:quest_reward_node_${index + 1}`, scope: 'QUEST', grantMode: 'MANUAL', rewards: [], completionRules: [], scopeRefId: '' };
}

function normalizeObjectiveByType(obj) {
  const base = {
    type: obj.type,
    id: obj.id || '',
    text: obj.text || '',
    textMode: obj.textMode || 'translatable',
    count: obj.count ?? 1,
    targetId: obj.targetId || '',
    hidden: !!obj.hidden,
    optional: !!obj.optional
  };
  if (obj.type === 'kill') return { ...base, entityType: obj.entityType || obj.targetId || 'minecraft:zombie' };
  if (obj.type === 'collect') return { ...base, itemId: obj.itemId || obj.targetId || 'minecraft:iron_ingot' };
  if (obj.type === 'talk') return { ...base, dialogueId: obj.dialogueId || '', npcId: obj.npcId || obj.targetId || '' };
  if (obj.type === 'interact') return { ...base, targetType: obj.targetType || 'entity' };
  if (obj.type === 'submit') return { ...base, itemId: obj.itemId || obj.targetId || 'minecraft:iron_ingot', consumeOnSubmit: !!obj.consumeOnSubmit };
  if (obj.type === 'custom_counter') return { ...base, counterId: obj.counterId || obj.targetId || '' };
  return { ...base, x: obj.x ?? 0, y: obj.y ?? 64, z: obj.z ?? 0 };
}

export function bindTreeSelection(leftEl, state, rerender) {
  leftEl.onclick = e => {
    const id = e.target.closest('[data-id]')?.dataset.id;
    if (!id) return;
    if (id === 'quest' || id === 'visual' || id === 'rewards' || id === 'raw') state.ui.sel = { t: id };
    else if (id.startsWith('phase-')) state.ui.sel = { t: 'phase', pi: +id.split('-')[1] };
    else { const [, pi, oi] = id.split('-'); state.ui.sel = { t: 'obj', pi: +pi, oi: +oi }; }
    rerender();
  };
}

export function bindEditorActions(midEl, state, rerender, setByPath) {
  midEl.oninput = e => {
    const b = e.target.dataset.b;
    if (!b) return;
    const beforeType = b.startsWith('ob.') && b.endsWith('.type') ? state.q.phases[state.ui.sel.pi].objectives[state.ui.sel.oi].type : null;
    const meta = setByPath(state.q, b, e.target.value, e.target.type);
    if (meta?.fileName) state.meta.file = meta.fileName;
    if (beforeType && beforeType !== e.target.value) {
      const obj = state.q.phases[state.ui.sel.pi].objectives[state.ui.sel.oi];
      state.q.phases[state.ui.sel.pi].objectives[state.ui.sel.oi] = normalizeObjectiveByType(obj);
    }
    state.meta.dirty = true;
    rerender();
  };
  midEl.onclick = e => {
    const d = e.target.dataset, s = state.ui.sel, q = state.q;
    if (e.target.id === 'addPhaseBtn') {
      q.phases.push(createPhase(q.phases.length));
      state.ui.sel = { t: 'phase', pi: q.phases.length - 1 };
    }
    if (e.target.id === 'addSplashBtn') q.visualConfig.splashes.push(createSplash());
    if (e.target.id === 'addCategoryBtn') { q.collectionConfig ||= { categories: [], rewardNodes: [], completionRules: [] }; q.collectionConfig.categories.push(createCollectionCategory(q.collectionConfig.categories.length)); }
    if (e.target.id === 'addTopCompletionRuleBtn') { q.collectionConfig ||= { categories: [], rewardNodes: [], completionRules: [] }; q.collectionConfig.completionRules ||= []; q.collectionConfig.completionRules.push(createCompletionRule()); }
    if (e.target.id === 'addTopRewardNodeBtn') { q.collectionConfig ||= { categories: [], rewardNodes: [], completionRules: [] }; q.collectionConfig.rewardNodes ||= []; q.collectionConfig.rewardNodes.push(createTopRewardNode(q.collectionConfig.rewardNodes.length)); }
    if (d.atrcr !== undefined) { q.collectionConfig.rewardNodes[+d.atrcr].completionRules ||= []; q.collectionConfig.rewardNodes[+d.atrcr].completionRules.push(createCompletionRule()); }
    if (d.atrr !== undefined) { q.collectionConfig.rewardNodes[+d.atrr].rewards ||= []; q.collectionConfig.rewardNodes[+d.atrr].rewards.push(createNodeReward()); }
    if (d.acr !== undefined) { q.collectionConfig ||= { categories: [], rewardNodes: [], completionRules: [] }; q.collectionConfig.categories[+d.acr].completionRules ||= []; q.collectionConfig.categories[+d.acr].completionRules.push(createCompletionRule()); }
    if (d.arn !== undefined) { q.collectionConfig ||= { categories: [], rewardNodes: [], completionRules: [] }; q.collectionConfig.categories[+d.arn].rewardNodes ||= []; q.collectionConfig.categories[+d.arn].rewardNodes.push(createRewardNode(q.collectionConfig.categories[+d.arn].rewardNodes.length)); }
    if (d.arcr !== undefined) { const [ci, ri] = d.arcr.split(':'); q.collectionConfig.categories[+ci].rewardNodes[+ri].completionRules ||= []; q.collectionConfig.categories[+ci].rewardNodes[+ri].completionRules.push(createCompletionRule()); }
    if (d.arr) { const [ci, ri] = d.arr.split(':'); q.collectionConfig.categories[+ci].rewardNodes[+ri].rewards ||= []; q.collectionConfig.categories[+ci].rewardNodes[+ri].rewards.push(createNodeReward()); }
    if (e.target.id === 'addQuestRewardBtn') q.rewards.push(createReward());
    if (e.target.id === 'addObjectiveBtn') {
      q.phases[s.pi].objectives.push(createObjective(q.phases[s.pi].objectives.length));
      state.ui.sel = { t: 'obj', pi: s.pi, oi: q.phases[s.pi].objectives.length - 1 };
    }
    if (e.target.id === 'addPhaseRewardBtn') q.phases[s.pi].rewards.push(createReward());
    if (e.target.id === 'addTransitionBtn') q.phases[s.pi].transitions.push(createTransition());
    if (e.target.id === 'movePhaseUpBtn' && s.pi > 0) { [q.phases[s.pi - 1], q.phases[s.pi]] = [q.phases[s.pi], q.phases[s.pi - 1]]; state.ui.sel = { t: 'phase', pi: s.pi - 1 }; }
    if (e.target.id === 'movePhaseDownBtn' && s.pi < q.phases.length - 1) { [q.phases[s.pi + 1], q.phases[s.pi]] = [q.phases[s.pi], q.phases[s.pi + 1]]; state.ui.sel = { t: 'phase', pi: s.pi + 1 }; }
    if (e.target.id === 'deletePhaseBtn') { if (q.phases.length <= 1) return alert('至少保留一个 phase。'); if (!confirm(`确定删除 phase: ${q.phases[s.pi].id} 吗？`)) return; q.phases.splice(s.pi, 1); state.ui.sel = { t: 'quest' }; }
    if (d.ds !== undefined) q.visualConfig.splashes.splice(+d.ds, 1);
    if (d.dtcr !== undefined) q.collectionConfig?.completionRules?.splice(+d.dtcr, 1);
    if (d.dtrn !== undefined) q.collectionConfig?.rewardNodes?.splice(+d.dtrn, 1);
    if (d.dtrcr) { const [ri, cri] = d.dtrcr.split(':'); q.collectionConfig?.rewardNodes?.[+ri]?.completionRules?.splice(+cri, 1); }
    if (d.dtrr) { const [ri, rwi] = d.dtrr.split(':'); q.collectionConfig?.rewardNodes?.[+ri]?.rewards?.splice(+rwi, 1); }
    if (d.dc !== undefined) q.collectionConfig?.categories?.splice(+d.dc, 1);
    if (d.dcr) { const [ci, cri] = d.dcr.split(':'); q.collectionConfig?.categories?.[+ci]?.completionRules?.splice(+cri, 1); }
    if (d.drn) { const [ci, ri] = d.drn.split(':'); q.collectionConfig?.categories?.[+ci]?.rewardNodes?.splice(+ri, 1); }
    if (d.drcr) { const [ci, ri, cri] = d.drcr.split(':'); q.collectionConfig?.categories?.[+ci]?.rewardNodes?.[+ri]?.completionRules?.splice(+cri, 1); }
    if (d.drr) { const [ci, ri, rwi] = d.drr.split(':'); q.collectionConfig?.categories?.[+ci]?.rewardNodes?.[+ri]?.rewards?.splice(+rwi, 1); }
    if (d.dr) { const [scope, phaseIndex, itemIndex] = d.dr.split(':'); (scope === 'quest' ? q.rewards : q.phases[+phaseIndex].rewards).splice(+itemIndex, 1); }
    if (d.open !== undefined) state.ui.sel = { t: 'obj', pi: s.pi, oi: +d.open };
    if (d.do !== undefined) { q.phases[s.pi].objectives.splice(+d.do, 1); state.ui.sel = { t: 'phase', pi: s.pi }; }
    if (d.dt !== undefined) q.phases[s.pi].transitions.splice(+d.dt, 1);
    state.meta.dirty = true; rerender();
  };
}
