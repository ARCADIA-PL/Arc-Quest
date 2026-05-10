import { splitList } from './utils.js';

export function setLooseJson(target, key, value) {
  if (!value?.trim()) {
    target[key] = null;
    return;
  }
  try {
    target[key] = JSON.parse(value);
  } catch {
    target[key] = value;
  }
}

export function syncPhaseTransitions(phase) {
  if (phase.mode === 'parallel') {
    phase.transitions = (phase.parallelPhaseIds || []).filter(Boolean).map(targetPhaseId => ({ targetPhaseId, condition: { type: 'always' } }));
    return;
  }
  if (phase.mode === 'choice') {
    phase.transitions = (phase.choicePhaseIds || []).filter(Boolean).map(targetPhaseId => ({ targetPhaseId, condition: { type: 'always' } }));
    return;
  }
  if (!Array.isArray(phase.transitions)) phase.transitions = [];
}

export function ensureQuestShape(q) {
  q.tags ||= [];
  q.rewards ||= [];
  q.phases ||= [];
  q.visualConfig ||= {};
  q.visualConfig.themeColor ||= '#63c7ff';
  q.visualConfig.splashes ||= [];
  q.visualConfig.icons ||= {};
  q.flagsToSetOnAccept ||= [];
  q.flagsToSetOnComplete ||= [];
  q.unlockConditions ||= null;
  q.relatedMarks ||= [];
  q.completionPolicy ||= 'ALL';
  q.completionRequiredCount ||= 1;
  q.completionTargetPhaseId ||= '';
  q.timeLimitType ||= '';
  q.timeLimitValue ||= 0;
  q.chapterStartSound ||= '';
  q.chapterFailSound ||= '';
  q.chapterCompleteSound ||= '';
  q.titleMode ||= 'translatable';
  q.descriptionMode ||= 'translatable';
  if (q.collectionConfig) {
    q.collectionConfig.trackerPresentationMode ||= 'DETAILED';
    q.collectionConfig.collectionPresentationMode ||= 'GROUPED';
  }
  q.phases.forEach((p, i) => {
    p.id ||= `phase_${i + 1}`;
    p.mode ||= 'normal';
    p.titleMode ||= 'translatable';
    p.descriptionMode ||= 'translatable';
    p.parallelPhaseIds ||= [];
    p.choicePhaseIds ||= [];
    p.objectives ||= [];
    p.objectives.forEach(o => {
      o.textMode ||= 'translatable';
      o.countMode ||= 'fixed';
      o.countBase ??= o.count ?? 1;
      o.countPerLevel ??= 0;
      o.countMin ??= 1;
      o.countMax ??= -1;
      o.extraData ||= {};
      o.relatedMarks ||= [];
    });
    p.rewards ||= [];
    p.flagsToSetOnEnter ||= [];
    p.flagsToSetOnComplete ||= [];
    p.tradeShopId ||= '';
    p.phaseStartSound ||= '';
    p.phaseCompleteSound ||= '';
    p.relatedMarks ||= [];
    p.visualConfig ||= null;
    p.choices ||= [];
    p.transitions ||= [];
  });
}

export function setQuestRootField(target, bind, value, inputType) {
  if (bind === 'q.id') { target.id = value; return { fileName: (value || 'quest') + '.json' }; }
  if (bind === 'q.title') return target.title = value;
  if (bind === 'q.titleMode') return target.titleMode = value;
  if (bind === 'q.description') return target.description = value;
  if (bind === 'q.descriptionMode') return target.descriptionMode = value;
  if (bind === 'q.sortOrder') return target.sortOrder = inputType === 'number' ? Number(value) : Number(value || 0);
  if (bind === 'q.repeatable') return target.repeatable = value === 'true';
  if (bind === 'q.tags') return target.tags = splitList(value);
  if (bind === 'q.theme') return target.visualConfig.themeColor = value;
  if (bind === 'q.icons') return setLooseJson(target.visualConfig, 'icons', value);
  if (bind === 'q.category') return target.category = value;
  if (bind === 'q.mode') return target.mode = value;
  if (bind === 'q.chapterShopId') return target.chapterShopId = value;
  if (bind === 'q.chapterShopType') return target.chapterShopType = value;
  if (bind === 'q.chapterStartSound') return target.chapterStartSound = value;
  if (bind === 'q.chapterFailSound') return target.chapterFailSound = value;
  if (bind === 'q.chapterCompleteSound') return target.chapterCompleteSound = value;
  if (bind === 'q.completionPolicy') return target.completionPolicy = value;
  if (bind === 'q.completionRequiredCount') return target.completionRequiredCount = Number(value || 0);
  if (bind === 'q.completionTargetPhaseId') return target.completionTargetPhaseId = value;
  if (bind === 'q.timeLimitType') return target.timeLimitType = value;
  if (bind === 'q.timeLimitValue') return target.timeLimitValue = Number(value || 0);
  if (bind === 'q.flagsToSetOnAccept') return target.flagsToSetOnAccept = splitList(value);
  if (bind === 'q.unlockConditions') return setLooseJson(target, 'unlockConditions', value);
  if (bind === 'q.relatedMarks') return setLooseJson(target, 'relatedMarks', value);
  if (bind === 'q.collectionConfig') return setLooseJson(target, 'collectionConfig', value);
  return undefined;
}
