function isNonEmptyString(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function textNode(value, mode = 'translatable', fallback = '') {
  return { mode, value: value || fallback };
}

function descriptionNode(value, mode = 'translatable') {
  if (!value) return undefined;
  return { mode: mode || 'translatable', value };
}

function toThemeColor(value) {
  if (typeof value === 'number') return value;
  const str = String(value || '').replace('#', '');
  const parsed = Number.parseInt(str, 16);
  return Number.isFinite(parsed) ? parsed : 0x63c7ff;
}

function exportSplashes(splashes) {
  const out = {};
  (splashes || []).forEach(s => {
    if (!s?.eventType) return;
    out[s.eventType] = { texture: s.texture || '', scale: Number(s.scale ?? 1) };
  });
  return Object.keys(out).length ? out : undefined;
}

function cleanCondition(node) {
  if (!node?.type || node.type === 'always') return { type: 'always' };
  if (node.type === 'flag_set' || node.type === 'flag_not_set') {
    return { type: node.type, flag: node.flag || '' };
  }
  if (node.type === 'quest_completed') {
    return { type: 'quest_completed', questId: node.questId || '' };
  }
  if (node.type === 'variable') {
    return {
      type: 'variable',
      variable: node.variable || '',
      compareOp: node.compareOp || 'EQUAL',
      value: Number(node.value ?? 0)
    };
  }
  if (node.type === 'not') {
    return {
      type: 'not',
      left: cleanCondition(node.left)
    };
  }
  return {
    type: node.type,
    left: cleanCondition(node.left),
    right: cleanCondition(node.right)
  };
}

function cleanCollectionEntryConfig(config) {
  if (!config) return undefined;
  const out = {
    categoryId: config.categoryId || undefined,
    visibilityMode: config.visibilityMode || undefined,
    hiddenPresentationMode: config.hiddenPresentationMode || undefined,
    visibilityConditions: config.visibilityConditions || undefined,
    countingMode: config.countingMode || undefined,
    completionTarget: config.completionTarget === undefined ? undefined : Number(config.completionTarget),
    repeatableProgress: config.repeatableProgress === undefined ? undefined : !!config.repeatableProgress,
    repeatableCompletion: config.repeatableCompletion === undefined ? undefined : !!config.repeatableCompletion,
    sortOrder: config.sortOrder === undefined ? undefined : Number(config.sortOrder),
    maxCount: config.maxCount === undefined ? undefined : Number(config.maxCount),
    rewardGrantMode: config.rewardGrantMode || undefined,
    rewardNodes: config.rewardNodes || undefined,
    showInTrackerByDefault: config.showInTrackerByDefault === undefined ? undefined : !!config.showInTrackerByDefault
  };
  Object.keys(out).forEach(key => out[key] === undefined && delete out[key]);
  return Object.keys(out).length ? out : undefined;
}

function exportReward(reward) {
  const type = reward?.type || 'item';
  if (type === 'command') {
    return {
      type: 'command',
      command: reward?.command || ''
    };
  }
  if (type === 'flag_set' || type === 'flag_clear') {
    return {
      type,
      flag: reward?.flag || ''
    };
  }
  if (type === 'var_set' || type === 'var_add' || type === 'var_subtract' || type === 'var_multiply') {
    return {
      type,
      variable: reward?.variable || '',
      value: Number(reward?.value ?? 0)
    };
  }
  return {
    type: 'item',
    itemId: reward?.itemId || '',
    count: Number(reward?.count ?? 1)
  };
}

function objectiveType(type) {
  const t = String(type || '').toUpperCase();
  if (t === 'KILL' || t === 'COLLECT' || t === 'TALK' || t === 'INTERACT' || t === 'REACH_LOCATION' || t === 'DELIVER' || t === 'CRAFT' || t === 'OFFER' || t === 'CUSTOM') {
    return t;
  }
  return 'CUSTOM';
}

function exportObjective(o) {
  const type = objectiveType(o.type);
  const targetId = o.targetId || '';
  const out = {
    type,
    targetId,
    requiredCount: Number(o.count ?? 1),
    displayText: textNode(o.text, o.textMode || 'translatable', ''),
    hidden: !!o.hidden,
    optional: !!o.optional,
    countMode: o.countMode || 'fixed',
    countBase: Number(o.countBase ?? o.count ?? 1),
    countPerLevel: Number(o.countPerLevel ?? 0),
    countMin: Number(o.countMin ?? 1),
    countMax: Number(o.countMax ?? -1),
    extraData: { ...(o.extraData || {}) }
  };

  if (o.relatedMarks?.length) out.relatedMarks = o.relatedMarks;

  if (o.npcId) out.npcId = o.npcId;
  if (o.itemTag) out.itemTag = o.itemTag;
  if (o.x !== null && o.x !== undefined) out.x = Number(o.x);
  if (o.y !== null && o.y !== undefined) out.y = Number(o.y);
  if (o.z !== null && o.z !== undefined) out.z = Number(o.z);
  if (o.radius !== null && o.radius !== undefined) out.radius = Number(o.radius);

  return out;
}

function exportPhase(phase) {
  const transitions = Array.isArray(phase.transitions)
    ? phase.transitions.map(tr => ({
        targetPhaseId: tr?.targetPhaseId || '',
        condition: cleanCondition(tr?.condition)
      }))
    : [];
  const enterCondition = cleanCondition(phase.rawEnterCondition);
  const out = {
    phaseId: phase.id,
    displayName: textNode(phase.title, phase.titleMode || 'translatable', phase.id),
    objectives: (phase.objectives || []).map(exportObjective),
    transitions
  };
  if (phase.rewards?.length) out.phaseRewards = phase.rewards.map(exportReward);
  if (isNonEmptyString(phase.description)) out.description = descriptionNode(phase.description, phase.descriptionMode || 'translatable');
  if (isNonEmptyString(phase.story)) out.story = descriptionNode(phase.story, phase.storyMode || 'literal');
  if (phase.intelSceneId) out.intelSceneId = phase.intelSceneId;
  if (phase.tradeShopId) out.tradeShopId = phase.tradeShopId;
  if (phase.phaseStartSound) out.phaseStartSound = phase.phaseStartSound;
  if (phase.phaseCompleteSound) out.phaseCompleteSound = phase.phaseCompleteSound;
  if (phase.relatedMarks?.length) out.relatedMarks = phase.relatedMarks;
  if (phase.visualConfig) out.visualConfig = phase.visualConfig;
  if (phase.choices?.length) out.choices = phase.choices;
  if (phase.flagsToSetOnEnter?.length) out.flagsToSetOnEnter = phase.flagsToSetOnEnter;
  if (phase.flagsToSetOnComplete?.length) out.flagsToSetOnComplete = phase.flagsToSetOnComplete;
  if (enterCondition.type !== 'always') out.enterCondition = enterCondition;
  if (phase.autoStart) out.autoEnterByCondition = true;
  const collectionEntryConfig = cleanCollectionEntryConfig(phase.collectionEntryConfig);
  if (collectionEntryConfig) out.collectionEntryConfig = collectionEntryConfig;
  return out;
}

export function exportQuestToDatapack(stateQuest) {
  const q = stateQuest;
  const out = {
    id: q.id,
    category: q.category || undefined,
    displayName: textNode(q.title, q.titleMode || 'translatable', q.id),
    sortOrder: Number(q.sortOrder ?? 0),
    repeatable: !!q.repeatable,
    mode: q.mode || 'PROGRESSION',
    initialPhaseId: q.initialPhaseId || q.phases?.[0]?.id || '',
    phases: (q.phases || []).map(exportPhase)
  };
  if (isNonEmptyString(q.description)) out.description = textNode(q.description, q.descriptionMode || 'translatable', '');
  if (q.chapterShopId) out.chapterShopId = q.chapterShopId;
  if (q.chapterShopType) out.chapterShopType = q.chapterShopType;
  if (q.chapterShopPersistent) out.chapterShopPersistent = true;
  if (q.iconTexture) out.iconTexture = q.iconTexture;
  if (q.flagsToSetOnAccept?.length) out.flagsToSetOnAccept = q.flagsToSetOnAccept;
  if (q.flagsToSetOnComplete?.length) out.flagsToSetOnComplete = q.flagsToSetOnComplete;
  if (q.completionPolicy) out.completionPolicy = q.completionPolicy;
  if (q.completionRequiredCount !== undefined && q.completionRequiredCount !== null) out.completionRequiredCount = Number(q.completionRequiredCount);
  if (q.completionTargetPhaseId) out.completionTargetPhaseId = q.completionTargetPhaseId;
  if (q.timeLimitType) out.timeLimitType = q.timeLimitType;
  if (q.timeLimitValue !== undefined && q.timeLimitValue !== null && Number(q.timeLimitValue) > 0) out.timeLimitValue = Number(q.timeLimitValue);
  if (q.chapterStartSound) out.chapterStartSound = q.chapterStartSound;
  if (q.chapterFailSound) out.chapterFailSound = q.chapterFailSound;
  if (q.chapterCompleteSound) out.chapterCompleteSound = q.chapterCompleteSound;
  if (q.unlockConditions) out.unlockConditions = q.unlockConditions;
  if (q.relatedMarks?.length) out.relatedMarks = q.relatedMarks;
  if (q.visualConfig) {
    const splashes = exportSplashes(q.visualConfig.splashes);
    const icons = q.visualConfig.icons && Object.keys(q.visualConfig.icons).length ? q.visualConfig.icons : undefined;
    out.visualConfig = {
      themeColor: toThemeColor(q.visualConfig.themeColor)
    };
    if (splashes) out.visualConfig.splashes = splashes;
    if (icons) out.visualConfig.icons = icons;
  }
  if (q.rewards?.length) out.completionRewards = q.rewards.map(exportReward);
  if (q.collectionConfig) out.collectionConfig = q.collectionConfig;
  Object.keys(out).forEach(k => out[k] === undefined && delete out[k]);
  return out;
}
