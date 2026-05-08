import { createPhase, createObjective, createReward, createSplash, createTransition, createCollectionCategory, createRewardNode, createCompletionRule } from '../core/factories.js';
import { uniqueId, makeQuestTitleKey, makeQuestDescKey, makePhaseTitleKey, makePhaseDescKey, makeObjectiveTextKey, buildReferenceIndex } from '../core/utils.js';

function syncPhaseIdReferences(q, oldId, newId) {
  if (!oldId || !newId || oldId === newId) return;
  if (q.initialPhaseId === oldId) q.initialPhaseId = newId;
  (q.phases || []).forEach(phase => {
    phase.parallelPhaseIds = (phase.parallelPhaseIds || []).map(id => id === oldId ? newId : id);
    phase.choicePhaseIds = (phase.choicePhaseIds || []).map(id => id === oldId ? newId : id);
    phase.transitions = (phase.transitions || []).map(tr => ({ ...tr, targetPhaseId: tr.targetPhaseId === oldId ? newId : tr.targetPhaseId }));
  });
}

function removePhaseReferences(q, removedId) {
  if (!removedId) return;
  if (q.initialPhaseId === removedId) q.initialPhaseId = q.phases?.[0]?.id || '';
  (q.phases || []).forEach(phase => {
    phase.parallelPhaseIds = (phase.parallelPhaseIds || []).filter(id => id && id !== removedId);
    phase.choicePhaseIds = (phase.choicePhaseIds || []).filter(id => id && id !== removedId);
    phase.transitions = (phase.transitions || []).filter(tr => tr?.targetPhaseId && tr.targetPhaseId !== removedId);
  });
}

function ensureUniquePhaseId(q, phaseIndex) {
  const phase = q.phases?.[phaseIndex];
  if (!phase) return;
  const others = (q.phases || []).filter((_, i) => i !== phaseIndex).map(p => p.id);
  phase.id = uniqueId(others, phase.id || `phase_${phaseIndex + 1}`);
}

function ensureUniqueObjectiveId(q, phaseIndex, objectiveIndex) {
  const phase = q.phases?.[phaseIndex];
  const objective = phase?.objectives?.[objectiveIndex];
  if (!phase || !objective) return;
  const others = (phase.objectives || []).filter((_, i) => i !== objectiveIndex).map(o => o.id);
  objective.id = uniqueId(others, objective.id || `objective_${objectiveIndex + 1}`);
}

function ensureUniqueCategoryId(q, categoryIndex) {
  const category = q.collectionConfig?.categories?.[categoryIndex];
  if (!category) return;
  const others = (q.collectionConfig?.categories || []).filter((_, i) => i !== categoryIndex).map(c => c.categoryId);
  category.categoryId = uniqueId(others, category.categoryId || `category_${categoryIndex + 1}`);
}

function ensureUniqueCategoryNodeId(q, categoryIndex, nodeIndex) {
  const category = q.collectionConfig?.categories?.[categoryIndex];
  const node = category?.rewardNodes?.[nodeIndex];
  if (!category || !node) return;
  const others = (category.rewardNodes || []).filter((_, i) => i !== nodeIndex).map(n => n.nodeId);
  node.nodeId = uniqueId(others, node.nodeId || `reward_node_${nodeIndex + 1}`);
}

function ensureUniqueTopNodeId(q, nodeIndex) {
  const node = q.collectionConfig?.rewardNodes?.[nodeIndex];
  if (!node) return;
  const others = (q.collectionConfig?.rewardNodes || []).filter((_, i) => i !== nodeIndex).map(n => n.nodeId);
  node.nodeId = uniqueId(others, node.nodeId || `quest_reward_node_${nodeIndex + 1}`);
}

function autoGenerateQuestKeys(q) {
  if (q.titleMode !== 'literal') q.title = makeQuestTitleKey(q.id);
  if (q.descriptionMode !== 'literal') q.description = makeQuestDescKey(q.id);
}

function autoGeneratePhaseKeys(q, phaseIndex) {
  const phase = q.phases?.[phaseIndex];
  if (!phase) return;
  if (phase.titleMode !== 'literal') phase.title = makePhaseTitleKey(q.id, phase.id);
  if (phase.descriptionMode !== 'literal') phase.description = makePhaseDescKey(q.id, phase.id);
  (phase.objectives || []).forEach(objective => {
    if (objective.textMode !== 'literal') objective.text = makeObjectiveTextKey(q.id, phase.id, objective.id);
  });
}

function autoGenerateAllPhaseKeys(q) {
  (q.phases || []).forEach((_, index) => autoGeneratePhaseKeys(q, index));
}

function applyAutoKeysByBind(q, bind) {
  if (bind === 'q.id' || bind === 'q.titleMode' || bind === 'q.descriptionMode') {
    autoGenerateQuestKeys(q);
    autoGenerateAllPhaseKeys(q);
    return;
  }
  if (bind.startsWith('ph.') && (bind.endsWith('.id') || bind.endsWith('.titleMode') || bind.endsWith('.descriptionMode'))) {
    const phaseIndex = Number(bind.split('.')[1]);
    autoGeneratePhaseKeys(q, phaseIndex);
    return;
  }
  if (bind.startsWith('ob.') && (bind.endsWith('.id') || bind.endsWith('.textMode'))) {
    const parts = bind.split('.');
    autoGeneratePhaseKeys(q, Number(parts[1]));
  }
}

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
    optional: !!obj.optional,
    npcId: obj.npcId || '',
    itemTag: obj.itemTag || '',
    x: obj.x ?? null,
    y: obj.y ?? null,
    z: obj.z ?? null,
    radius: obj.radius ?? null,
    extraData: obj.extraData || {}
  };
  if (obj.type === 'KILL') return { ...base, targetId: obj.targetId || 'minecraft:zombie' };
  if (obj.type === 'COLLECT') return { ...base, targetId: obj.targetId || 'minecraft:iron_ingot' };
  if (obj.type === 'TALK') return { ...base, npcId: obj.npcId || obj.targetId || 'arc_quest:npc_guard' };
  if (obj.type === 'INTERACT') return { ...base, targetId: obj.targetId || 'minecraft:crafting_table' };
  if (obj.type === 'OFFER') return { ...base, targetId: obj.targetId || 'minecraft:iron_ingot' };
  if (obj.type === 'DELIVER') return { ...base, targetId: obj.targetId || 'minecraft:iron_ingot', npcId: obj.npcId || 'arc_quest:npc_guard' };
  if (obj.type === 'REACH_LOCATION') return { ...base, x: obj.x ?? 0, y: obj.y ?? 64, z: obj.z ?? 0, radius: obj.radius ?? 4 };
  if (obj.type === 'CRAFT') return { ...base, targetId: obj.targetId || 'minecraft:torch' };
  return { ...base, targetId: obj.targetId || 'arc_quest:custom_target' };
}

function normalizeRewardByType(reward) {
  const type = reward?.type || 'item';
  if (type === 'command') return { type, command: reward?.command || '' };
  if (type === 'flag_set' || type === 'flag_clear') return { type, flag: reward?.flag || '' };
  if (type === 'var_set' || type === 'var_add' || type === 'var_subtract' || type === 'var_multiply') {
    return { type, variable: reward?.variable || '', value: reward?.value ?? 0 };
  }
  return { type: 'item', itemId: reward?.itemId || 'minecraft:iron_ingot', count: reward?.count ?? 1 };
}

function addChipValue(q, key, value) {
  const v = String(value || '').trim();
  if (!v) return;
  if (key === 'q.tags') {
    q.tags ||= [];
    if (!q.tags.includes(v)) q.tags.push(v);
  }
  if (key === 'q.flagsToSetOnAccept') {
    q.flagsToSetOnAccept ||= [];
    if (!q.flagsToSetOnAccept.includes(v)) q.flagsToSetOnAccept.push(v);
  }
  if (key === 'q.flagsToSetOnComplete') {
    q.flagsToSetOnComplete ||= [];
    if (!q.flagsToSetOnComplete.includes(v)) q.flagsToSetOnComplete.push(v);
  }
}

function removeChipValue(q, key, index) {
  const i = Number(index);
  if (!Number.isInteger(i) || i < 0) return;
  if (key === 'q.tags') q.tags?.splice(i, 1);
  if (key === 'q.flagsToSetOnAccept') q.flagsToSetOnAccept?.splice(i, 1);
  if (key === 'q.flagsToSetOnComplete') q.flagsToSetOnComplete?.splice(i, 1);
}

function markInputValidity(el, bind) {
  if (!el || !bind) return;
  const v = String(el.value || '').trim();
  let invalid = false;
  if (bind.endsWith('itemId')) invalid = !!v && !v.includes(':');
  if (bind.endsWith('entityType')) invalid = !!v && !v.includes(':');
  if (bind.endsWith('npcId')) invalid = !!v && !v.includes(':');
  if (bind.endsWith('dialogueId')) invalid = !!v && !v.includes(':');
  if (bind === 'q.iconTexture') invalid = !!v && !v.includes(':');
  el.classList.toggle('input-invalid', invalid);
}

function fixCollectionReferences(q) {
  q.collectionConfig ||= { categories: [], rewardNodes: [], completionRules: [] };
  const phaseIds = new Set((q.phases || []).map(p => p.id).filter(Boolean));
  const categoryIds = new Set((q.collectionConfig.categories || []).map(c => c.categoryId).filter(Boolean));
  const nodeIds = new Set([
    ...(q.collectionConfig.rewardNodes || []).map(n => n.nodeId),
    ...(q.collectionConfig.categories || []).flatMap(c => (c.rewardNodes || []).map(n => n.nodeId))
  ].filter(Boolean));
  const allowRef = new Set([...phaseIds, ...categoryIds, ...nodeIds]);

  (q.collectionConfig.completionRules || []).forEach(rule => {
    if (rule?.refId && !allowRef.has(rule.refId)) rule.refId = '';
  });

  (q.collectionConfig.rewardNodes || []).forEach(node => {
    if (node?.scopeRefId && !allowRef.has(node.scopeRefId)) node.scopeRefId = '';
    (node.completionRules || []).forEach(rule => {
      if (rule?.refId && !allowRef.has(rule.refId)) rule.refId = '';
    });
  });

  (q.collectionConfig.categories || []).forEach(cat => {
    (cat.completionRules || []).forEach(rule => {
      if (rule?.refId && !allowRef.has(rule.refId)) rule.refId = '';
    });
    (cat.rewardNodes || []).forEach(node => {
      if (node?.scopeRefId && !allowRef.has(node.scopeRefId)) node.scopeRefId = '';
      (node.completionRules || []).forEach(rule => {
        if (rule?.refId && !allowRef.has(rule.refId)) rule.refId = '';
      });
    });
  });
}

function fixCollectionRuleDefaults(q) {
  q.collectionConfig ||= { categories: [], rewardNodes: [], completionRules: [] };
  const fixRule = rule => {
    if (!rule) return;
    rule.type ||= 'completed_entry_count';
    if (rule.value === undefined || rule.value === null || Number.isNaN(Number(rule.value))) rule.value = 1;
    if ((rule.type === 'and' || rule.type === 'or' || rule.type === 'not')) {
      rule.left ||= { type: 'all_entries_complete', value: 1 };
      if (rule.type !== 'not') rule.right ||= { type: 'all_entries_complete', value: 1 };
    }
  };
  (q.collectionConfig.completionRules || []).forEach(fixRule);
  (q.collectionConfig.rewardNodes || []).forEach(node => (node.completionRules || []).forEach(fixRule));
  (q.collectionConfig.categories || []).forEach(cat => {
    (cat.completionRules || []).forEach(fixRule);
    (cat.rewardNodes || []).forEach(node => (node.completionRules || []).forEach(fixRule));
  });
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

  // 核心修复 1：将 oninput 改为 onchange。
  // 这保证了只有在下拉框选择完成、或输入框失去焦点时，才触发数据保存与重绘，彻底解决焦点丢失问题。
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

  midEl.onclick = e => {
    const removeTarget = e.target.closest('[data-chip-remove]');
    if (removeTarget) {
      const [key, index] = String(removeTarget.dataset.chipRemove || '').split(':');
      removeChipValue(state.q, key, index);
      state.meta.dirty = true;
      rerender();
      return;
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
      return;
    }

    const collectionViewBtn = e.target.closest('[data-collection-view]');
    if (collectionViewBtn) {
      state.ui.collectionView = collectionViewBtn.dataset.collectionView === 'tree' ? 'tree' : 'card';
      rerender();
      return;
    }

    // 核心修复 2：点击事件精准拦截！
    // 只有点击的是 <button> 标签（或其内部的 SVG 图标），才继续往下执行。
    // 如果点击的是输入框、下拉框或空白处，直接 return，绝不允许触发底部的 rerender()。
    const btn = e.target.closest('button');
    if (!btn) return;

    // 使用 btn 的 dataset 和 id，防止点击按钮内部图标时获取不到数据
    const d = btn.dataset;
    const id = btn.id;
    const s = state.ui.sel;
    const q = state.q;

    // 以下所有判定统一使用按钮元素的 id 和 d (dataset)
    if (id === 'addPhaseBtn') {
      q.phases.push(createPhase(q.phases.length));
      const newPhaseIndex = q.phases.length - 1;
      ensureUniquePhaseId(q, newPhaseIndex);
      autoGeneratePhaseKeys(q, newPhaseIndex);
      state.ui.sel = { t: 'phase', pi: newPhaseIndex };
    }
    if (id === 'fixCollectionRefsBtn') {
      fixCollectionReferences(q);
    }
    if (id === 'fixCollectionRulesBtn') {
      fixCollectionRuleDefaults(q);
    }
    if (id === 'addSplashBtn') q.visualConfig.splashes.push(createSplash());
    if (id === 'addCategoryBtn') {
      q.collectionConfig ||= { categories: [], rewardNodes: [], completionRules: [] };
      q.collectionConfig.categories.push(createCollectionCategory(q.collectionConfig.categories.length));
      ensureUniqueCategoryId(q, q.collectionConfig.categories.length - 1);
    }
    if (id === 'addTopCompletionRuleBtn') { q.collectionConfig ||= { categories: [], rewardNodes: [], completionRules: [] }; q.collectionConfig.completionRules ||= []; q.collectionConfig.completionRules.push(createCompletionRule()); }
    if (id === 'addTopRewardNodeBtn') {
      q.collectionConfig ||= { categories: [], rewardNodes: [], completionRules: [] };
      q.collectionConfig.rewardNodes ||= [];
      q.collectionConfig.rewardNodes.push(createTopRewardNode(q.collectionConfig.rewardNodes.length));
      ensureUniqueTopNodeId(q, q.collectionConfig.rewardNodes.length - 1);
    }

    if (d.atrcr !== undefined) { q.collectionConfig.rewardNodes[+d.atrcr].completionRules ||= []; q.collectionConfig.rewardNodes[+d.atrcr].completionRules.push(createCompletionRule()); }
    if (d.atrr !== undefined) { q.collectionConfig.rewardNodes[+d.atrr].rewards ||= []; q.collectionConfig.rewardNodes[+d.atrr].rewards.push(createNodeReward()); }
    if (d.acr !== undefined) { q.collectionConfig ||= { categories: [], rewardNodes: [], completionRules: [] }; q.collectionConfig.categories[+d.acr].completionRules ||= []; q.collectionConfig.categories[+d.acr].completionRules.push(createCompletionRule()); }
    if (d.arn !== undefined) {
      q.collectionConfig ||= { categories: [], rewardNodes: [], completionRules: [] };
      q.collectionConfig.categories[+d.arn].rewardNodes ||= [];
      q.collectionConfig.categories[+d.arn].rewardNodes.push(createRewardNode(q.collectionConfig.categories[+d.arn].rewardNodes.length));
      ensureUniqueCategoryNodeId(q, +d.arn, q.collectionConfig.categories[+d.arn].rewardNodes.length - 1);
    }

    if (d.arcr !== undefined) { const [ci, ri] = d.arcr.split(':'); q.collectionConfig.categories[+ci].rewardNodes[+ri].completionRules ||= []; q.collectionConfig.categories[+ci].rewardNodes[+ri].completionRules.push(createCompletionRule()); }
    if (d.arr) { const [ci, ri] = d.arr.split(':'); q.collectionConfig.categories[+ci].rewardNodes[+ri].rewards ||= []; q.collectionConfig.categories[+ci].rewardNodes[+ri].rewards.push(createNodeReward()); }

    if (id === 'addQuestRewardBtn') q.rewards.push(createReward());

    if (id === 'addObjectiveBtn') {
      q.phases[s.pi].objectives.push(createObjective(q.phases[s.pi].objectives.length));
      const newObjectiveIndex = q.phases[s.pi].objectives.length - 1;
      ensureUniqueObjectiveId(q, s.pi, newObjectiveIndex);
      autoGeneratePhaseKeys(q, s.pi);
      state.ui.sel = { t: 'obj', pi: s.pi, oi: newObjectiveIndex };
    }

    if (id === 'addPhaseRewardBtn') q.phases[s.pi].rewards.push(createReward());
    if (id === 'addTransitionBtn') q.phases[s.pi].transitions.push(createTransition());
    if (id === 'addChoiceBtn') {
      q.phases[s.pi].choices ||= [];
      q.phases[s.pi].choices.push({ text: '', flagToSet: '', targetPhaseId: '', visibleCondition: { type: 'always' } });
    }

    if (id === 'movePhaseUpBtn' && s.pi > 0) { [q.phases[s.pi - 1], q.phases[s.pi]] = [q.phases[s.pi], q.phases[s.pi - 1]]; state.ui.sel = { t: 'phase', pi: s.pi - 1 }; }
    if (id === 'movePhaseDownBtn' && s.pi < q.phases.length - 1) { [q.phases[s.pi + 1], q.phases[s.pi]] = [q.phases[s.pi], q.phases[s.pi + 1]]; state.ui.sel = { t: 'phase', pi: s.pi + 1 }; }
    if (id === 'deletePhaseBtn') {
      if (q.phases.length <= 1) return alert('至少保留一个 phase。');
      const removedId = q.phases[s.pi]?.id;
      const refIndex = buildReferenceIndex(q);
      const incoming = refIndex.incoming.get(`phase:${removedId}`) || [];
      const impactLines = incoming.map(r => `- ${r.kind} @ ${r.path} (${r.from} -> ${r.to})`).join('\n');
      const impactTip = incoming.length ? `\n\n检测到 ${incoming.length} 条引用将被清理：\n${impactLines}` : '';
      if (!confirm(`确定删除 phase: ${removedId} 吗？${impactTip}`)) return;
      q.phases.splice(s.pi, 1);
      removePhaseReferences(q, removedId);
      state.ui.sel = { t: 'quest' };
    }

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
    if (d.do !== undefined) {
      q.phases[s.pi].objectives.splice(+d.do, 1);
      autoGeneratePhaseKeys(q, s.pi);
      state.ui.sel = { t: 'phase', pi: s.pi };
    }
    if (d.dt !== undefined) q.phases[s.pi].transitions.splice(+d.dt, 1);
    if (d.dch !== undefined) q.phases[s.pi].choices.splice(+d.dch, 1);

    // 只有真正的按钮操作走到了这里，才标记脏数据并全量重绘！
    state.meta.dirty = true;
    rerender();
  };
}