export const createQuestSkeleton = () => ({
    id: '',
    title: '',
    titleMode: 'translatable',
    description: '',
    descriptionMode: 'translatable',
    sortOrder: 0,
    repeatable: false,
    allowAbandon: true,
    canBeAutoTrack: true,
    mode: 'PROGRESSION',
    category: '',
    initialPhaseId: '',
    initialPhaseIds: [],
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
    guidesToGrantOnEnter: [],
    guidesToGrantOnComplete: [],
    objectives: [],
    rewards: [],
    relatedMarks: [],
    trackingMarks: [],
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
export const createTransition = () => ({targetPhaseId: '', targetPhaseIds: [], condition: {condition: 'arc_quest:always'}});
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

export const createNpcSkeleton = () => ({
    entityType: '',
    bindings: [],
    cancelVanillaInteract: true,
    dialogueDistance: 8.0,
    shouldLookAtPlayer: true,
    shouldStopMoving: true,
    interactionPolicy: 'PARALLEL_PRIVATE',
    interactCondition: null,
    onDialogueStartCommands: [],
    onDialogueEndCommands: []
});

export const createNpcBinding = () => ({
    bindingId: '',
    dialogueId: '',
    dialogueIdFromNbt: '',
    condition: null,
    priority: 0
});

export const createDialogueSkeleton = () => ({
    id: '',
    defaultNpc: { mode: 'literal', value: '', args: [] },
    startNodeId: '',
    nodes: [],
    visualConfig: null,
    repeatable: true,
    cooldownSeconds: 0,
    cooldownType: 'NONE',
    resetTimeTicks: 0,
    npcBindings: [],
    entityBindings: []
});

export const createDialogueNode = () => ({
    nodeId: '',
    sayId: '',
    speaker: { mode: 'literal', value: '', args: [] },
    text: { mode: 'literal', value: '', args: [] },
    conditionalTexts: {},
    choices: [],
    autoNextId: '',
    delayMs: 0,
    repeatable: true,
    cooldownSeconds: 0,
    cooldownType: 'NONE',
    resetTimeTicks: 0,
    nodeEnterSound: '',
    relatedMarks: []
});

export const createConditionalSay = () => ({
    sayId: '',
    text: { mode: 'literal', value: '', args: [] },
    soundEvent: '',
    conditions: [],
    priority: 0
});

export const createTradeSkeleton = () => ({
    shopId: '',
    displayName: { mode: 'literal', value: '', args: [] },
    description: null,
    categories: [],
    entries: {},
    openCondition: null,
    simpleMode: false,
    themeColor: 0xE0C860,
    openSound: '',
    closeSound: ''
});

export const createTradeEntrySkeleton = () => ({
    entryId: '',
    displayName: { mode: 'literal', value: '', args: [] },
    description: null,
    costs: [],
    rewards: [],
    category: '',
    visibleCondition: null,
    canBuyCondition: null,
    cooldownType: 'NONE',
    cooldownValue: 0,
    resetTimeTicks: 0,
    maxPurchases: -1,
    rewardIcon: '',
    costIcon: '',
    sortOrder: 0,
    themeColor: -1,
    purchaseSuccessSound: '',
    purchaseFailSound: '',
    cooldownSound: '',
    limitReachedSound: '',
    conditionFailSound: ''
});

export const createTradeOfferSkeleton = () => ({
    type: 'item',
    itemId: '',
    itemTag: '',
    count: 1,
    nbt: '',
    customIcon: '',
    command: '',
    executeAs: 'console',
    effectId: '',
    duration: 0,
    amplifier: 0,
    flagName: '',
    offers: []
});

export const createGachaSkeleton = () => ({
    shopId: '',
    displayName: { mode: 'literal', value: '', args: [] },
    description: null,
    categories: [],
    openCondition: null,
    themeColor: 0xFFD700,
    simpleMode: false,
    openSound: '',
    closeSound: '',
    drawCost: null,
    drawCosts: [createTradeOfferSkeleton()],
    maxDraws: -1,
    cooldownType: 'NONE',
    cooldownValue: 0,
    resetTimeTicks: 0,
    drawCondition: null,
    resetCondition: null,
    resetOnLimitReached: true,
    resetPityOnEarlyTrigger: true,
    drawCooldownSound: '',
    drawLimitReachedSound: '',
    drawConditionFailSound: '',
    drawFailSound: '',
    rarities: [],
    pity: null,
    pools: [
        { poolId: 'default', items: [] }
    ]
});

export const createGachaItemSkeleton = () => ({
    itemId: '',
    displayName: { mode: 'literal', value: '', args: [] },
    item: '',
    weight: 1,
    countsTowardsPity: true,
    rarity: 'RARE',
    minCount: 1,
    maxCount: 1,
    sortOrder: 0,
    rewardIcon: '',
    themeColor: -1,
    drawSuccessSound: '',
    visibleCondition: null,
    reward: null,
    weightModifiers: []
});

export const createDialogueChoice = () => ({
    choiceId: '',
    text: { mode: 'literal', value: '', args: [] },
    nextNodeId: '',
    conditions: [],
    actions: [],
    repeatable: true,
    cooldownSeconds: 0,
    cooldownType: 'NONE',
    resetTimeTicks: 0,
    priority: 0,
    restoreNodeId: '',
    selectSound: '',
    relatedMarks: []
});

export const createDialogueAction = (type = 'no_op') => ({
    type,
    questId: '',
    amount: 0,
    itemId: '',
    count: 1,
    npcId: '',
    targetId: '',
    command: '',
    flagName: '',
    key: '',
    value: 0,
    shopId: '',
    restoreNodeId: '',
    customTypeId: '',
    customData: {}
});
