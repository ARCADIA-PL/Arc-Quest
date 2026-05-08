import {ensureQuestShape} from '../core/quest-shape.js';

export function validateQuest(state) {
  const q = state.q;
  ensureQuestShape(q);
  const d = [];
  if (!q.id?.trim()) d.push({ lvl: 'err', path: 'quest', msg: 'Quest id 不能为空' });
  if (!q.phases.length) d.push({ lvl: 'err', path: 'phases', msg: '至少需要一个 phase' });
  const ids = new Set();
  q.phases.forEach((p, pi) => {
    if (!p.id?.trim()) d.push({ lvl: 'err', path: `phase:${pi}`, msg: `Phase ${pi + 1} 缺少 id` });
    if (ids.has(p.id)) d.push({ lvl: 'err', path: `phase:${pi}`, msg: `Phase id 重复: ${p.id}` });
    ids.add(p.id);
    if (!['normal', 'parallel', 'choice'].includes(p.mode)) d.push({ lvl: 'warn', path: `phase:${pi}`, msg: `Phase ${p.id} mode 非法` });
    if (!p.objectives.length) d.push({ lvl: 'warn', path: `phase:${pi}`, msg: `Phase ${p.id} 没有 objective` });
    if (q.mode === 'COLLECTION' && p.collectionEntryConfig && !p.collectionEntryConfig.categoryId) d.push({ lvl: 'warn', path: `phase:${pi}`, msg: `Collection phase ${p.id} 缺少 categoryId` });
    if (q.mode === 'COLLECTION' && p.collectionEntryConfig && !(p.collectionEntryConfig.completionTarget > 0)) d.push({ lvl: 'warn', path: `phase:${pi}`, msg: `Collection phase ${p.id} completionTarget 应大于 0` });
    (p.parallelPhaseIds || []).forEach(id => { if (p.mode === 'parallel' && !q.phases.some(x => x.id === id)) d.push({ lvl: 'err', path: `phase:${pi}`, msg: `parallelPhaseIds 引用了不存在的 phase: ${id}` }); });
    (p.choicePhaseIds || []).forEach(id => { if (p.mode === 'choice' && !q.phases.some(x => x.id === id)) d.push({ lvl: 'err', path: `phase:${pi}`, msg: `choicePhaseIds 引用了不存在的 phase: ${id}` }); });
    (p.transitions || []).forEach((tr, ti) => {
      if (tr?.targetPhaseId && !q.phases.some(x => x.id === tr.targetPhaseId)) {
        d.push({ lvl: 'err', path: `phase:${pi}`, msg: `transition[${ti}] 引用了不存在的 phase: ${tr.targetPhaseId}` });
      }
    });
    p.objectives.forEach((o, oi) => {
      if (!o.type) d.push({ lvl: 'err', path: `objective:${pi}:${oi}`, msg: 'Objective 缺少 type' });
      if (!o.text) d.push({ lvl: 'warn', path: `objective:${pi}:${oi}`, msg: 'Objective 缺少文案 key' });
      if (o.type === 'kill' && !o.entityType) d.push({ lvl: 'err', path: `objective:${pi}:${oi}`, msg: 'Kill objective 缺少 entityType' });
      if (o.type === 'collect' && !o.itemId) d.push({ lvl: 'err', path: `objective:${pi}:${oi}`, msg: 'Collect objective 缺少 itemId' });
      if (o.type === 'talk' && !o.dialogueId && !o.npcId && !o.targetId) d.push({ lvl: 'warn', path: `objective:${pi}:${oi}`, msg: 'Talk objective 建议填写 dialogueId 或 npcId' });
      if (o.type === 'interact' && !o.targetId) d.push({ lvl: 'warn', path: `objective:${pi}:${oi}`, msg: 'Interact objective 缺少 targetId' });
      if (o.type === 'submit' && !o.itemId) d.push({ lvl: 'warn', path: `objective:${pi}:${oi}`, msg: 'Submit objective 缺少 itemId' });
      if (o.type === 'custom_counter' && !o.counterId) d.push({ lvl: 'warn', path: `objective:${pi}:${oi}`, msg: 'custom_counter 建议填写 counterId' });
    });
    (p.rewards || []).forEach((r, ri) => {
      const type = r?.type || 'item';
      if (type === 'item' && !r.itemId) d.push({ lvl: 'err', path: `phase:${pi}`, msg: `Phase reward[${ri}] item 缺少 itemId` });
      if (type === 'var_add' && !r.variable) d.push({ lvl: 'warn', path: `phase:${pi}`, msg: `Phase reward[${ri}] var_add 建议填写 variable` });
      if (type === 'command' && !r.command) d.push({ lvl: 'warn', path: `phase:${pi}`, msg: `Phase reward[${ri}] command 为空` });
      if (type === 'flag' && !r.flag) d.push({ lvl: 'warn', path: `phase:${pi}`, msg: `Phase reward[${ri}] flag 为空` });
      if (type === 'currency' && !r.currencyId) d.push({ lvl: 'warn', path: `phase:${pi}`, msg: `Phase reward[${ri}] currencyId 为空` });
    });
  });
  if (q.initialPhaseId && !q.phases.some(p => p.id === q.initialPhaseId)) {
    d.push({ lvl: 'err', path: 'quest', msg: `initialPhaseId 引用了不存在的 phase: ${q.initialPhaseId}` });
  }
  const sp = new Set((q.visualConfig.splashes || []).map(x => x.eventType));
  if (!sp.has('QUEST_ACQUIRED')) d.push({ lvl: 'warn', path: 'visual', msg: '未配置 QUEST_ACQUIRED splash' });
  if (!sp.has('QUEST_COMPLETED')) d.push({ lvl: 'warn', path: 'visual', msg: '未配置 QUEST_COMPLETED splash' });
  if (q.mode === 'COLLECTION' && !q.collectionConfig) d.push({ lvl: 'warn', path: 'quest', msg: 'Collection quest 缺少 collectionConfig' });
  if (q.collectionConfig) {
    const categoryIds = new Set();
    (q.collectionConfig.categories || []).forEach((cat, ci) => {
      if (!cat?.categoryId) return;
      if (categoryIds.has(cat.categoryId)) d.push({ lvl: 'err', path: 'quest', msg: `categoryId 重复: ${cat.categoryId} (index ${ci})` });
      categoryIds.add(cat.categoryId);
      const nodeIds = new Set();
      (cat.rewardNodes || []).forEach((node, ni) => {
        if (!node?.nodeId) return;
        if (nodeIds.has(node.nodeId)) d.push({ lvl: 'err', path: 'quest', msg: `rewardNode nodeId 重复: ${node.nodeId} (category ${cat.categoryId}, index ${ni})` });
        nodeIds.add(node.nodeId);
      });
    });
    const topNodeIds = new Set();
    (q.collectionConfig.rewardNodes || []).forEach((node, ni) => {
      if (!node?.nodeId) return;
      if (topNodeIds.has(node.nodeId)) d.push({ lvl: 'err', path: 'quest', msg: `top rewardNode nodeId 重复: ${node.nodeId} (index ${ni})` });
      topNodeIds.add(node.nodeId);
    });
  }
  (q.rewards || []).forEach((r, ri) => {
    const type = r?.type || 'item';
    if (type === 'item' && !r.itemId) d.push({ lvl: 'err', path: 'quest', msg: `Quest reward[${ri}] item 缺少 itemId` });
    if (type === 'var_add' && !r.variable) d.push({ lvl: 'warn', path: 'quest', msg: `Quest reward[${ri}] var_add 建议填写 variable` });
    if (type === 'command' && !r.command) d.push({ lvl: 'warn', path: 'quest', msg: `Quest reward[${ri}] command 为空` });
    if (type === 'flag' && !r.flag) d.push({ lvl: 'warn', path: 'quest', msg: `Quest reward[${ri}] flag 为空` });
    if (type === 'currency' && !r.currencyId) d.push({ lvl: 'warn', path: 'quest', msg: `Quest reward[${ri}] currencyId 为空` });
  });
  state.diag = d;
}
