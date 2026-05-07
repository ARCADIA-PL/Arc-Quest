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
    p.objectives.forEach((o, oi) => {
      if (!o.type) d.push({ lvl: 'err', path: `objective:${pi}:${oi}`, msg: 'Objective 缺少 type' });
      if (!o.text) d.push({ lvl: 'warn', path: `objective:${pi}:${oi}`, msg: 'Objective 缺少文案 key' });
      if (o.type === 'kill' && !o.entityType) d.push({ lvl: 'err', path: `objective:${pi}:${oi}`, msg: 'Kill objective 缺少 entityType' });
      if (o.type === 'collect' && !o.itemId) d.push({ lvl: 'err', path: `objective:${pi}:${oi}`, msg: 'Collect objective 缺少 itemId' });
      if (o.type === 'talk' && !o.dialogueId && !o.npcId && !o.targetId) d.push({ lvl: 'warn', path: `objective:${pi}:${oi}`, msg: 'Talk objective 建议填写 dialogueId 或 npcId' });
      if (o.type === 'interact' && !o.targetId) d.push({ lvl: 'warn', path: `objective:${pi}:${oi}`, msg: 'Interact objective 缺少 targetId' });
      if (o.type === 'submit' && !o.itemId) d.push({ lvl: 'warn', path: `objective:${pi}:${oi}`, msg: 'Submit objective 缺少 itemId' });
    });
  });
  const sp = new Set((q.visualConfig.splashes || []).map(x => x.eventType));
  if (!sp.has('QUEST_ACQUIRED')) d.push({ lvl: 'warn', path: 'visual', msg: '未配置 QUEST_ACQUIRED splash' });
  if (!sp.has('QUEST_COMPLETED')) d.push({ lvl: 'warn', path: 'visual', msg: '未配置 QUEST_COMPLETED splash' });
  if (q.mode === 'COLLECTION' && !q.collectionConfig) d.push({ lvl: 'warn', path: 'quest', msg: 'Collection quest 缺少 collectionConfig' });
  state.diag = d;
}
