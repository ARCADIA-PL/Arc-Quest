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
  if (type === 'command') return { type, command: reward?.command || '', ...reward };
  if (type === 'flag_set' || type === 'flag_clear') return { type, flag: reward?.flag || '', ...reward };
  if (type === 'var_set' || type === 'var_add' || type === 'var_subtract' || type === 'var_multiply') {
    return { type, variable: reward?.variable || '', value: reward?.value ?? 0, ...reward };
  }
  return { type: 'item', itemId: reward?.itemId || '', count: reward?.count ?? 1, ...reward };
}

function mapObjectiveType(type) {
  const t = String(type || '').toUpperCase();
  if (t === 'KILL') return 'KILL';
  if (t === 'COLLECT') return 'COLLECT';
  if (t === 'TALK') return 'TALK';
  if (t === 'INTERACT') return 'INTERACT';
  if (t === 'REACH_LOCATION') return 'REACH_LOCATION';
  if (t === 'DELIVER') return 'DELIVER';
  if (t === 'CRAFT') return 'CRAFT';
  if (t === 'OFFER') return 'OFFER';
  return 'CUSTOM';
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
    countMode: obj?.countMode || 'fixed',
    countBase: obj?.countBase ?? obj?.requiredCount ?? 1,
    countPerLevel: obj?.countPerLevel ?? 0,
    countMin: obj?.countMin ?? 1,
    countMax: obj?.countMax ?? -1,
    hidden: !!obj?.hidden,
    optional: !!obj?.optional,
    targetId: obj?.targetId || '',
    npcId: obj?.npcId || '',
    itemTag: obj?.itemTag || '',
    x: obj?.x ?? null,
    y: obj?.y ?? null,
    z: obj?.z ?? null,
    radius: obj?.radius ?? null,
    extraData: obj?.extraData || {},
    relatedMarks: obj?.relatedMarks || []
  };
  if (!base.npcId && base.extraData?.npc_id) base.npcId = base.extraData.npc_id;
  if (!base.itemTag && base.extraData?.target_tag) base.itemTag = base.extraData.target_tag;
  if (base.x === null && base.extraData?.x !== undefined) base.x = Number(base.extraData.x);
  if (base.y === null && base.extraData?.y !== undefined) base.y = Number(base.extraData.y);
  if (base.z === null && base.extraData?.z !== undefined) base.z = Number(base.extraData.z);
  if (base.radius === null && base.extraData?.radius !== undefined) base.radius = Number(base.extraData.radius);
  return base;
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
    tradeShopId: phase?.tradeShopId || '',
    phaseStartSound: phase?.phaseStartSound || '',
    phaseCompleteSound: phase?.phaseCompleteSound || '',
    relatedMarks: phase?.relatedMarks || [],
    visualConfig: phase?.visualConfig || null,
    autoStart: !!phase?.autoEnterByCondition,
    parallelPhaseIds: mode === 'parallel' ? targetIds : [],
    choicePhaseIds: mode === 'choice' ? targetIds : [],
    transitions,
    choices: phase?.choices || [],
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
    flagsToSetOnAccept: q.flagsToSetOnAccept || [],
    flagsToSetOnComplete: q.flagsToSetOnComplete || [],
    completionPolicy: q.completionPolicy || 'ALL',
    completionRequiredCount: q.completionRequiredCount ?? 1,
    completionTargetPhaseId: q.completionTargetPhaseId || '',
    timeLimitType: q.timeLimitType || '',
    timeLimitValue: q.timeLimitValue ?? 0,
    chapterStartSound: q.chapterStartSound || '',
    chapterFailSound: q.chapterFailSound || '',
    chapterCompleteSound: q.chapterCompleteSound || '',
    unlockConditions: q.unlockConditions || null,
    relatedMarks: q.relatedMarks || [],
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
