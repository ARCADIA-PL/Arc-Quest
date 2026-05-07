export const createPhase = index => ({
  id: `phase_${index + 1}`,
  mode: 'normal',
  title: '',
  description: '',
  autoStart: false,
  parallelPhaseIds: [],
  choicePhaseIds: [],
  objectives: [],
  rewards: [],
  transitions: []
});

export const createObjective = index => ({
  type: 'kill',
  id: `objective_${index + 1}`,
  text: '',
  count: 1,
  entityType: 'minecraft:zombie'
});

export const createReward = () => ({ type: 'item', itemId: 'minecraft:iron_ingot', count: 1 });
export const createSplash = () => ({ eventType: 'QUEST_ACQUIRED', texture: '', scale: 1 });
export const createTransition = () => ({ targetPhaseId: '', condition: { type: 'always' } });
export const createCollectionCategory = index => ({
  categoryId: `category_${index + 1}`,
  displayName: { mode: 'translatable', value: `arc_quest.collection.category_${index + 1}` },
  sortOrder: index,
  completionRules: [],
  rewardNodes: []
});

export const createRewardNode = index => ({
  nodeId: `arc_quest:reward_node_${index + 1}`,
  scope: 'CATEGORY',
  grantMode: 'AUTO',
  rewards: [],
  completionRules: [],
  scopeRefId: ''
});

export const createCompletionRule = () => ({
  type: 'completed_entry_count',
  value: 1,
  refId: ''
});
