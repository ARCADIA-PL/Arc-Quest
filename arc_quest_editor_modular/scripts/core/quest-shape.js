import { splitList } from './utils.js';

function setLooseJson(target, key, value) {
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

function syncPhaseTransitions(phase) {
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

function setCollectionEntryField(phase, fieldName, value, inputType) {
  phase.collectionEntryConfig ||= {};
  if (fieldName === 'showInTrackerByDefault') {
    phase.collectionEntryConfig[fieldName] = value === 'true';
    return;
  }
  phase.collectionEntryConfig[fieldName] = inputType === 'number' ? Number(value || 0) : value;
}

function setConditionNodeField(rootNode, pathParts, value) {
  let cursor = rootNode;
  for (let i = 0; i < pathParts.length - 1; i++) {
    const key = pathParts[i];
    cursor[key] ||= { type: 'always' };
    cursor = cursor[key];
  }
  const fieldName = pathParts[pathParts.length - 1];
  if (fieldName === 'type') {
    cursor.type = value;
    if (value === 'always') {
      delete cursor.flag;
      delete cursor.left;
      delete cursor.right;
      return;
    }
    if (value === 'flag_set') {
      cursor.flag ||= '';
      delete cursor.left;
      delete cursor.right;
      return;
    }
    cursor.left ||= { type: 'always' };
    cursor.right ||= { type: 'always' };
    delete cursor.flag;
    return;
  }
  cursor[fieldName] = value;
}

function setEnterConditionField(phase, pathParts, value) {
  phase.rawEnterCondition ||= { type: 'always' };
  setConditionNodeField(phase.rawEnterCondition, pathParts, value);
}

function setTransitionField(phase, transitionIndex, fieldName, value, pathParts) {
  phase.transitions ||= [];
  phase.transitions[transitionIndex] ||= { targetPhaseId: '', condition: { type: 'always' } };
  if (fieldName === 'conditionType') {
    phase.transitions[transitionIndex].condition ||= {};
    phase.transitions[transitionIndex].condition.type = value;
    return;
  }
  if (fieldName === 'c') {
    phase.transitions[transitionIndex].condition ||= { type: 'always' };
    setConditionNodeField(phase.transitions[transitionIndex].condition, pathParts || [], value);
    return;
  }
  phase.transitions[transitionIndex][fieldName] = value;
}

function setTopCollectionConfigField(target, fieldName, value) {
  target.collectionConfig ||= { categories: [], rewardNodes: [], completionRules: [] };
  if (fieldName === 'allowCategoryCollapse' || fieldName === 'showCompletedEntries' || fieldName === 'showProgressInTracker') {
    target.collectionConfig[fieldName] = value === 'true';
    return;
  }
  target.collectionConfig[fieldName] = value;
}

function setTopCompletionRuleField(target, ruleIndex, fieldName, value, inputType) {
  target.collectionConfig ||= { categories: [], rewardNodes: [], completionRules: [] };
  target.collectionConfig.completionRules ||= [];
  target.collectionConfig.completionRules[ruleIndex] ||= { type: 'completed_entry_count', value: 1, refId: '' };
  target.collectionConfig.completionRules[ruleIndex][fieldName] = inputType === 'number' ? Number(value || 0) : value;
}

function setTopRewardNodeField(target, rewardNodeIndex, fieldName, value, inputType) {
  target.collectionConfig ||= { categories: [], rewardNodes: [], completionRules: [] };
  target.collectionConfig.rewardNodes ||= [];
  target.collectionConfig.rewardNodes[rewardNodeIndex] ||= { nodeId: '', scope: 'QUEST', grantMode: 'MANUAL', rewards: [], completionRules: [], scopeRefId: '' };
  target.collectionConfig.rewardNodes[rewardNodeIndex][fieldName] = inputType === 'number' ? Number(value || 0) : value;
}

function setTopRewardNodeCompletionRuleField(target, rewardNodeIndex, ruleIndex, fieldName, value, inputType) {
  target.collectionConfig ||= { categories: [], rewardNodes: [], completionRules: [] };
  target.collectionConfig.rewardNodes ||= [];
  target.collectionConfig.rewardNodes[rewardNodeIndex] ||= { nodeId: '', scope: 'QUEST', grantMode: 'MANUAL', rewards: [], completionRules: [], scopeRefId: '' };
  target.collectionConfig.rewardNodes[rewardNodeIndex].completionRules ||= [];
  target.collectionConfig.rewardNodes[rewardNodeIndex].completionRules[ruleIndex] ||= { type: 'completed_entry_count', value: 1, refId: '' };
  target.collectionConfig.rewardNodes[rewardNodeIndex].completionRules[ruleIndex][fieldName] = inputType === 'number' ? Number(value || 0) : value;
}

function setTopRewardNodeRewardField(target, rewardNodeIndex, rewardIndex, fieldName, value, inputType) {
  target.collectionConfig ||= { categories: [], rewardNodes: [], completionRules: [] };
  target.collectionConfig.rewardNodes ||= [];
  target.collectionConfig.rewardNodes[rewardNodeIndex] ||= { nodeId: '', scope: 'QUEST', grantMode: 'MANUAL', rewards: [], completionRules: [], scopeRefId: '' };
  target.collectionConfig.rewardNodes[rewardNodeIndex].rewards ||= [];
  target.collectionConfig.rewardNodes[rewardNodeIndex].rewards[rewardIndex] ||= { type: 'item', itemId: '', count: 1 };
  target.collectionConfig.rewardNodes[rewardNodeIndex].rewards[rewardIndex][fieldName] = inputType === 'number' ? Number(value || 0) : value;
}

function setCategoryField(target, categoryIndex, fieldName, value, inputType) {
  target.collectionConfig ||= { categories: [], rewardNodes: [], completionRules: [] };
  target.collectionConfig.categories ||= [];
  target.collectionConfig.categories[categoryIndex] ||= { categoryId: '', displayName: { mode: 'translatable', value: '' }, sortOrder: categoryIndex, completionRules: [], rewardNodes: [] };
  const cat = target.collectionConfig.categories[categoryIndex];
  if (fieldName === 'displayMode') {
    cat.displayName ||= { mode: 'translatable', value: '' };
    cat.displayName.mode = value;
    return;
  }
  if (fieldName === 'displayValue') {
    cat.displayName ||= { mode: 'translatable', value: '' };
    cat.displayName.value = value;
    return;
  }
  cat[fieldName] = inputType === 'number' ? Number(value || 0) : value;
}

function setRewardNodeField(target, categoryIndex, rewardNodeIndex, fieldName, value, inputType) {
  target.collectionConfig ||= { categories: [], rewardNodes: [], completionRules: [] };
  target.collectionConfig.categories ||= [];
  target.collectionConfig.categories[categoryIndex] ||= { categoryId: '', displayName: { mode: 'translatable', value: '' }, sortOrder: categoryIndex, completionRules: [], rewardNodes: [] };
  const cat = target.collectionConfig.categories[categoryIndex];
  cat.rewardNodes ||= [];
  cat.rewardNodes[rewardNodeIndex] ||= { nodeId: '', scope: 'CATEGORY', grantMode: 'AUTO', rewards: [], completionRules: [], scopeRefId: '' };
  cat.rewardNodes[rewardNodeIndex][fieldName] = inputType === 'number' ? Number(value || 0) : value;
}

function setRewardNodeRewardField(target, categoryIndex, rewardNodeIndex, rewardIndex, fieldName, value, inputType) {
  target.collectionConfig ||= { categories: [], rewardNodes: [], completionRules: [] };
  target.collectionConfig.categories ||= [];
  target.collectionConfig.categories[categoryIndex] ||= { categoryId: '', displayName: { mode: 'translatable', value: '' }, sortOrder: categoryIndex, completionRules: [], rewardNodes: [] };
  const cat = target.collectionConfig.categories[categoryIndex];
  cat.rewardNodes ||= [];
  cat.rewardNodes[rewardNodeIndex] ||= { nodeId: '', scope: 'CATEGORY', grantMode: 'AUTO', rewards: [], completionRules: [], scopeRefId: '' };
  cat.rewardNodes[rewardNodeIndex].rewards ||= [];
  cat.rewardNodes[rewardNodeIndex].rewards[rewardIndex] ||= { type: 'item', itemId: '', count: 1 };
  cat.rewardNodes[rewardNodeIndex].rewards[rewardIndex][fieldName] = inputType === 'number' ? Number(value || 0) : value;
}

function setCategoryCompletionRuleField(target, categoryIndex, ruleIndex, fieldName, value, inputType) {
  target.collectionConfig ||= { categories: [], rewardNodes: [], completionRules: [] };
  target.collectionConfig.categories ||= [];
  target.collectionConfig.categories[categoryIndex] ||= { categoryId: '', displayName: { mode: 'translatable', value: '' }, sortOrder: categoryIndex, completionRules: [], rewardNodes: [] };
  const cat = target.collectionConfig.categories[categoryIndex];
  cat.completionRules ||= [];
  cat.completionRules[ruleIndex] ||= { type: 'completed_entry_count', value: 1, refId: '' };
  cat.completionRules[ruleIndex][fieldName] = inputType === 'number' ? Number(value || 0) : value;
}

function setRewardNodeCompletionRuleField(target, categoryIndex, rewardNodeIndex, ruleIndex, fieldName, value, inputType) {
  target.collectionConfig ||= { categories: [], rewardNodes: [], completionRules: [] };
  target.collectionConfig.categories ||= [];
  target.collectionConfig.categories[categoryIndex] ||= { categoryId: '', displayName: { mode: 'translatable', value: '' }, sortOrder: categoryIndex, completionRules: [], rewardNodes: [] };
  const cat = target.collectionConfig.categories[categoryIndex];
  cat.rewardNodes ||= [];
  cat.rewardNodes[rewardNodeIndex] ||= { nodeId: '', scope: 'CATEGORY', grantMode: 'AUTO', rewards: [], completionRules: [], scopeRefId: '' };
  cat.rewardNodes[rewardNodeIndex].completionRules ||= [];
  cat.rewardNodes[rewardNodeIndex].completionRules[ruleIndex] ||= { type: 'completed_entry_count', value: 1, refId: '' };
  cat.rewardNodes[rewardNodeIndex].completionRules[ruleIndex][fieldName] = inputType === 'number' ? Number(value || 0) : value;
}

export function ensureQuestShape(q) {
  q.tags ||= [];
  q.rewards ||= [];
  q.phases ||= [];
  q.visualConfig ||= {};
  q.visualConfig.themeColor ||= '#63c7ff';
  q.visualConfig.splashes ||= [];
  q.flagsToSetOnComplete ||= [];
  q.collectionConfig ||= null;
  q.phases.forEach((p, i) => {
    p.id ||= `phase_${i + 1}`;
    p.mode ||= 'normal';
    p.parallelPhaseIds ||= [];
    p.choicePhaseIds ||= [];
    p.objectives ||= [];
    p.rewards ||= [];
    p.flagsToSetOnEnter ||= [];
    p.flagsToSetOnComplete ||= [];
    p.transitions ||= [];
  });
}

export function setByPath(target, bind, value, inputType, phaseIndexResolver) {
  if (bind === 'q.id') { target.id = value; return { fileName: (value || 'quest') + '.json' }; }
  if (bind === 'q.title') return target.title = value;
  if (bind === 'q.titleMode') return target.titleMode = value;
  if (bind === 'q.description') return target.description = value;
  if (bind === 'q.descriptionMode') return target.descriptionMode = value;
  if (bind === 'q.sortOrder') return target.sortOrder = inputType === 'number' ? Number(value) : Number(value || 0);
  if (bind === 'q.repeatable') return target.repeatable = value === 'true';
  if (bind === 'q.tags') return target.tags = splitList(value);
  if (bind === 'q.theme') return target.visualConfig.themeColor = value;
  if (bind === 'q.category') return target.category = value;
  if (bind === 'q.mode') return target.mode = value;
  if (bind === 'q.chapterShopId') return target.chapterShopId = value;
  if (bind === 'q.chapterShopType') return target.chapterShopType = value;
  if (bind === 'q.chapterShopPersistent') return target.chapterShopPersistent = value === 'true';
  if (bind === 'q.initialPhaseId') return target.initialPhaseId = value;
  if (bind === 'q.iconTexture') return target.iconTexture = value;
  if (bind === 'q.flagsToSetOnComplete') return target.flagsToSetOnComplete = splitList(value);
  if (bind === 'q.collectionConfig') return setLooseJson(target, 'collectionConfig', value);
  if (bind.startsWith('q.cc.')) {
    const [, , fieldName] = bind.split('.');
    return setTopCollectionConfigField(target, fieldName, value);
  }
  if (bind.startsWith('q.tcr.')) {
    const [, , ruleIndex, fieldName] = bind.split('.');
    return setTopCompletionRuleField(target, +ruleIndex, fieldName, value, inputType);
  }
  if (bind.startsWith('q.trn.')) {
    const [, , rewardNodeIndex, fieldName, rewardIndex, rewardField] = bind.split('.');
    if (fieldName === 'cr') return setTopRewardNodeCompletionRuleField(target, +rewardNodeIndex, +rewardIndex, rewardField, value, inputType);
    if (fieldName === 'rw') return setTopRewardNodeRewardField(target, +rewardNodeIndex, +rewardIndex, rewardField, value, inputType);
    return setTopRewardNodeField(target, +rewardNodeIndex, fieldName, value, inputType);
  }
  if (bind.startsWith('q.cat.')) {
    const [, , i, fieldName, rewardNodeIndex, rewardNodeField, rewardIndex, rewardField] = bind.split('.');
    if (fieldName === 'cr') return setCategoryCompletionRuleField(target, +i, +rewardNodeIndex, rewardNodeField, value, inputType);
    if (fieldName === 'rn' && rewardNodeField === 'cr') return setRewardNodeCompletionRuleField(target, +i, +rewardNodeIndex, +rewardIndex, rewardField, value, inputType);
    if (fieldName === 'rn' && rewardNodeField === 'rw') return setRewardNodeRewardField(target, +i, +rewardNodeIndex, +rewardIndex, rewardField, value, inputType);
    if (fieldName === 'rn') return setRewardNodeField(target, +i, +rewardNodeIndex, rewardNodeField, value, inputType);
    return setCategoryField(target, +i, fieldName, value, inputType);
  }
  if (bind.startsWith('sp.')) {
    const [, i, k] = bind.split('.');
    target.visualConfig.splashes[+i][k] = inputType === 'number' ? Number(value || 0) : value; return;
  }
  if (bind.startsWith('ph.')) {
    const [, i, k, sub, leaf] = bind.split('.');
    if (k === 'cec') {
      setCollectionEntryField(target.phases[+i], sub, value, inputType);
    }
    else if (k === 'ec') {
      setEnterConditionField(target.phases[+i], bind.split('.').slice(3), value);
    }
    else if (k === 'tr') {
      const parts = bind.split('.');
      if (parts[4] === 'c') setTransitionField(target.phases[+i], +sub, 'c', value, parts.slice(5));
      else setTransitionField(target.phases[+i], +sub, leaf, value);
    }
    else if (k === 'autoStart') target.phases[+i][k] = value === 'true';
    else if (k === 'titleMode' || k === 'descriptionMode' || k === 'storyMode') target.phases[+i][k] = value;
    else if (k === 'parallelPhaseIds' || k === 'choicePhaseIds' || k === 'flagsToSetOnEnter' || k === 'flagsToSetOnComplete') target.phases[+i][k] = splitList(value);
    else if (k === 'rawEnterCondition' || k === 'transitions' || k === 'collectionEntryConfig') setLooseJson(target.phases[+i], k, value);
    else target.phases[+i][k] = value;
    if (k === 'mode' || k === 'parallelPhaseIds' || k === 'choicePhaseIds') syncPhaseTransitions(target.phases[+i]);
    return;
  }
  if (bind.startsWith('ob.')) {
    const [, pi, oi, k] = bind.split('.');
    if (k === 'textMode' || k === 'targetType') {
      target.phases[+pi].objectives[+oi][k] = value;
      return;
    }
    if (k === 'hidden' || k === 'optional' || k === 'consumeOnSubmit') {
      target.phases[+pi].objectives[+oi][k] = value === 'true';
      return;
    }
    target.phases[+pi].objectives[+oi][k] = inputType === 'number' ? Number(value || 0) : value; return;
  }
  if (bind.startsWith('rw.quest.')) {
    const [, , i, k] = bind.split('.');
    target.rewards[+i][k] = inputType === 'number' ? Number(value || 0) : value; return;
  }
  if (bind.startsWith('rw.phase.')) {
    const [, , phaseIndex, i, k] = bind.split('.');
    target.phases[+phaseIndex].rewards[+i][k] = inputType === 'number' ? Number(value || 0) : value;
    return;
  }
}
