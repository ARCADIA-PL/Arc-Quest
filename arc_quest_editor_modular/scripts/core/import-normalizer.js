function asTextNode(value, fallback = '') {
  if (typeof value === 'string') return { text: value, mode: 'translatable' };
  if (value && typeof value === 'object' && 'value' in value) return { text: value.value || fallback, mode: value.mode || 'translatable' };
  return { text: fallback, mode: 'translatable' };
}

function toHexColor(value) {
  if (typeof value === 'string') return value;
  if (typeof value === 'number' && Number.isFinite(value)) return '#' + value.toString(16).padStart(6, '0');
  return '#63c7ff';
}

function normalizeSplashMap(splashes) {
  if (!splashes || Array.isArray(splashes)) return splashes || [];
  return Object.entries(splashes).map(([eventType, cfg]) => ({ eventType, texture: cfg?.texture || '', scale: cfg?.scale ?? 1 }));
}

function normalizeReward(reward) {
  const type = reward?.type || 'item';
  if (type === 'var_add') return { type, variable: reward?.variable || '', value: reward?.value ?? 0, ...reward };
  return { type, itemId: reward?.itemId || '', count: reward?.count ?? 1, ...reward };
}

function mapObjectiveType(type) {
  const t = String(type || '').toUpperCase();
  if (t === 'KILL') return 'kill';
  if (t === 'COLLECT') return 'collect';
  if (t === 'INTERACT') return 'interact';
  if (t === 'OFFER') return 'submit';
  if (t === 'TALK') return 'talk';
  return 'custom_counter';
}

function normalizeObjective(obj, idx) {
  const type = mapObjectiveType(obj?.type);
  const textNode = asTextNode(obj?.displayText, '');
  const base = {
    type,
    id: obj?.id || `objective_${idx + 1}`,
    text: textNode.text,
    textMode: textNode.mode,
    count: obj?.requiredCount ?? 1,
    hidden: !!obj?.hidden,
    optional: !!obj?.optional,
    targetId: obj?.targetId || ''
  };
  if (type === 'kill') return { ...base, entityType: obj?.targetId || '' };
  if (type === 'collect') return { ...base, itemId: obj?.targetId || '' };
  if (type === 'interact') return { ...base, targetType: 'entity', targetId: obj?.targetId || '' };
  if (type === 'submit') return { ...base, itemId: obj?.targetId || obj?.itemTag || '', consumeOnSubmit: true };
  if (type === 'talk') return { ...base, npcId: obj?.targetId || '' };
  return { ...base, counterId: obj?.targetId || '' };
}

function normalizePhase(phase, idx) {
  const transitions = phase?.transitions || [];
  const targetIds = transitions.map(t => t?.targetPhaseId).filter(Boolean);
  const mode = targetIds.length > 1 ? 'parallel' : 'normal';
  const titleNode = asTextNode(phase?.displayName, '');
  const descNode = asTextNode(phase?.description, '');
  const storyNode = asTextNode(phase?.story, '');
  return {
    id: phase?.phaseId || phase?.id || `phase_${idx + 1}`,
    mode,
    title: titleNode.text,
    titleMode: titleNode.mode,
    description: descNode.text,
    descriptionMode: descNode.mode,
    story: storyNode.text,
    storyMode: storyNode.mode,
    intelSceneId: phase?.intelSceneId || '',
    autoStart: !!phase?.autoEnterByCondition,
    parallelPhaseIds: mode === 'parallel' ? targetIds : [],
    choicePhaseIds: mode === 'choice' ? targetIds : [],
    transitions,
    rawEnterCondition: phase?.enterCondition || null,
    flagsToSetOnEnter: phase?.flagsToSetOnEnter || [],
    flagsToSetOnComplete: phase?.flagsToSetOnComplete || [],
    objectives: (phase?.objectives || []).map(normalizeObjective),
    rewards: (phase?.phaseRewards || []).map(normalizeReward),
    collectionEntryConfig: phase?.collectionEntryConfig || null,
    raw: phase
  };
}

export function normalizeImportedQuest(input) {
  const q = { ...input };
  const titleNode = asTextNode(q.displayName, q.title || '');
  const descNode = asTextNode(q.description, q.descriptionText || '');
  return {
    ...q,
    id: q.id || 'imported_quest',
    title: titleNode.text,
    titleMode: titleNode.mode,
    description: descNode.text,
    descriptionMode: descNode.mode,
    sortOrder: q.sortOrder ?? 0,
    repeatable: !!q.repeatable,
    tags: q.tags || [],
    mode: q.mode || 'PROGRESSION',
    category: q.category || '',
    chapterShopId: q.chapterShopId || '',
    chapterShopType: q.chapterShopType || '',
    chapterShopPersistent: !!q.chapterShopPersistent,
    initialPhaseId: q.initialPhaseId || '',
    iconTexture: q.iconTexture || '',
    flagsToSetOnComplete: q.flagsToSetOnComplete || [],
    visualConfig: {
      themeColor: toHexColor(q.visualConfig?.themeColor),
      splashes: normalizeSplashMap(q.visualConfig?.splashes),
      icons: q.visualConfig?.icons || {}
    },
    rewards: (q.completionRewards || q.rewards || []).map(normalizeReward),
    phases: (q.phases || []).map(normalizePhase),
    collectionConfig: q.collectionConfig || null,
    rawImported: input
  };
}
