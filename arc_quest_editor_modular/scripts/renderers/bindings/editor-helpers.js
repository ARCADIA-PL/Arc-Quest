export {
    syncPhaseIdReferences,
    removePhaseReferences,
    ensureUniquePhaseId,
    ensureUniqueObjectiveId,
    ensureUniqueCategoryId,
    ensureUniqueCategoryNodeId,
    ensureUniqueTopNodeId,
    autoGenerateQuestKeys,
    autoGeneratePhaseKeys,
    autoGenerateAllPhaseKeys,
    applyAutoKeysByBind
} from './editor-helpers-identity.js';

export {
    createNodeReward,
    createTopRewardNode,
    normalizeObjectiveByType,
    normalizeRewardByType,
    addChipValue,
    removeChipValue,
    markInputValidity,
    fixCollectionReferences,
    fixCollectionRuleDefaults
} from './editor-helpers-collection.js';
