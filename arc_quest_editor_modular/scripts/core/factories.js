export const createQuestSkeleton = () => ({
    id: '',
    title: '',
    titleMode: 'translatable',
    description: '',
    descriptionMode: 'translatable',
    sortOrder: 0,
    repeatable: false,
    mode: 'PROGRESSION',
    category: '',
    tags: [],
    flagsToSetOnAccept: [],
    flagsToSetOnComplete: [],
    rewards: [],
    phases: [],
    unlockConditions: [],
    visualConfig: {
        themeColor: '#63c7ff',
        splashes: []
    }
});

export const createPhase = index => ({
    id: `phase_${index + 1}`,
    mode: 'normal',
    title: '',
    titleMode: 'translatable',
    description: '',
    descriptionMode: 'translatable',
    autoStart: false,
    autoAdvanceOnComplete: true,
    parallelPhaseIds: [],
    choicePhaseIds: [],
    flagsToSetOnEnter: [],
    flagsToSetOnComplete: [],
    objectives: [],
    rewards: [],
    transitions: []
});

export const createObjective = index => ({
    type: 'KILL',
    id: `objective_${index + 1}`,
    text: '',
    count: 1,
    targetId: 'minecraft:zombie',
    hidden: false,
    optional: false,
    npcId: '',
    itemTag: '',
    x: null,
    y: null,
    z: null,
    radius: null,
    extraData: {}
});

export const createReward = () => ({type: 'item', itemId: 'minecraft:iron_ingot', count: 1});
export const createSplash = (eventType = 'QUEST_ACQUIRED') => ({eventType, texture: '', scale: 1});
export const createTransition = () => ({targetPhaseId: '', condition: {type: 'always'}});
export const createCollectionCategory = index => ({
    categoryId: `category_${index + 1}`,
    displayName: {mode: 'translatable', value: `arc_quest.collection.category_${index + 1}`},
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
    value: 1
});
