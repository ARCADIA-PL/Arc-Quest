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
  if (node.type === 'flag_set') return { type: 'flag_set', flag: node.flag || '' };
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
    countingMode: config.countingMode || undefined,
    completionTarget: config.completionTarget === undefined ? undefined : Number(config.completionTarget),
    sortOrder: config.sortOrder === undefined ? undefined : Number(config.sortOrder),
    maxCount: config.maxCount === undefined ? undefined : Number(config.maxCount),
    rewardGrantMode: config.rewardGrantMode || undefined,
    showInTrackerByDefault: config.showInTrackerByDefault === undefined ? undefined : !!config.showInTrackerByDefault
  };
  Object.keys(out).forEach(key => out[key] === undefined && delete out[key]);
  return Object.keys(out).length ? out : undefined;
}

function exportReward(reward) {
  const type = reward?.type || 'item';
  if (type === 'var_add') {
    return {
      type: 'var_add',
      variable: reward?.variable || '',
      value: Number(reward?.value ?? 0)
    };
  }
  if (type === 'command') {
    return {
      type: 'command',
      command: reward?.command || ''
    };
  }
  if (type === 'flag') {
    return {
      type: 'flag',
      flag: reward?.flag || '',
      enabled: reward?.enabled !== false
    };
  }
  if (type === 'currency') {
    return {
      type: 'currency',
      currencyId: reward?.currencyId || 'arc_quest:coin',
      amount: Number(reward?.amount ?? 1)
    };
  }
  return {
    type: 'item',
    itemId: reward?.itemId || '',
    count: Number(reward?.count ?? 1)
  };
}

function objectiveType(type) {
  if (type === 'kill') return 'KILL';
  if (type === 'collect') return 'COLLECT';
  if (type === 'interact') return 'INTERACT';
  if (type === 'submit') return 'OFFER';
  if (type === 'talk') return 'TALK';
  return 'CUSTOM_COUNTER';
}

function exportObjective(o) {
  const type = objectiveType(o.type);
  const targetId = o.entityType || o.itemId || o.targetId || o.npcId || o.counterId || '';
  const out = {
    type,
    targetId,
    requiredCount: Number(o.count ?? 1),
    displayText: textNode(o.text, o.textMode || 'translatable', ''),
    hidden: !!o.hidden,
    optional: !!o.optional,
    extraData: {}
  };
  if (type === 'OFFER') {
    out.itemTag = o.itemId || o.targetId || '';
    out.consumeOnSubmit = !!o.consumeOnSubmit;
  }
  if (type === 'INTERACT' && isNonEmptyString(o.targetType)) out.targetType = o.targetType;
  if (type === 'TALK' && isNonEmptyString(o.dialogueId)) out.dialogueId = o.dialogueId;
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
  if (q.flagsToSetOnComplete?.length) out.flagsToSetOnComplete = q.flagsToSetOnComplete;
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
