import { exportPhase, exportReward } from './export-normalizer-phase.js';

function isNonEmptyString(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function textNode(value, mode = 'translatable', fallback = '') {
  return { mode, value: value || fallback };
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
