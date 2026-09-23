import {cloneDocument} from './json-document.js';
import {exportPhase, exportReward, cleanCondition} from './export-normalizer-phase.js';
import {exportVisualAsset, exportVisualAssetMap} from './visual-asset.js';

function isNonEmptyString(value) {
    return typeof value === 'string' && value.trim().length > 0;
}

function textNode(value, mode = 'translatable', fallback = '') {
    return {mode, value: value || fallback};
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
        out[s.eventType] = exportVisualAsset(s);
    });
    return Object.keys(out).length ? out : undefined;
}

export function exportQuestToDatapack(stateQuest) {
    const q = cloneDocument(stateQuest);
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
    if (q.initialPhaseIds?.length) out.initialPhaseIds = q.initialPhaseIds;
    if (q.allowAbandon === false) out.allowAbandon = false;
    if (q.canBeAutoTrack === false) out.canBeAutoTrack = false;
    if (isNonEmptyString(q.description)) out.description = textNode(q.description, q.descriptionMode || 'translatable', '');
    if (q.chapterShopId) out.chapterShopId = q.chapterShopId;
    if (q.chapterShopType) out.chapterShopType = q.chapterShopType;
    if (q.chapterShopPersistent) out.chapterShopPersistent = true;
    if (q.iconTexture) out.iconTexture = q.iconTexture;
    if (q.flagsToSetOnAccept?.length) out.flagsToSetOnAccept = q.flagsToSetOnAccept;
    if (q.flagsToSetOnComplete?.length) out.flagsToSetOnComplete = q.flagsToSetOnComplete;
    if (q.completionPolicy) out.completionPolicy = q.completionPolicy;
    if (q.completionPolicy === 'N_OF_M' && q.completionRequiredCount > 0) out.completionRequiredCount = Number(q.completionRequiredCount);
    if (q.completionTargetPhaseId) out.completionTargetPhaseId = q.completionTargetPhaseId;
    if (q.timeLimitType) out.timeLimitType = q.timeLimitType;
    if (q.timeLimitValue !== undefined && q.timeLimitValue !== null && Number(q.timeLimitValue) > 0) out.timeLimitValue = Number(q.timeLimitValue);
    if (q.chapterStartSound) out.chapterStartSound = q.chapterStartSound;
    if (q.chapterFailSound) out.chapterFailSound = q.chapterFailSound;
    if (q.chapterCompleteSound) out.chapterCompleteSound = q.chapterCompleteSound;
    if (q.unlockConditions) out.unlockConditions = q.unlockConditions.map(cleanCondition);
    if (q.relatedMarks?.length) out.relatedMarks = q.relatedMarks;
    if (q.visualConfig) {
        const splashes = exportSplashes(q.visualConfig.splashes);
        const icons = exportVisualAssetMap(q.visualConfig.icons);
        out.visualConfig = {
            themeColor: toThemeColor(q.visualConfig.themeColor)
        };
        if (splashes) out.visualConfig.splashes = splashes;
        if (icons) out.visualConfig.icons = icons;
    }
    if (q.rewards?.length) out.completionRewards = q.rewards.map(exportReward);
    if (q.collectionConfig) {
        const cc = q.collectionConfig;
        const hasCategories = Array.isArray(cc.categories) && cc.categories.length > 0;
        const hasRewardNodes = Array.isArray(cc.rewardNodes) && cc.rewardNodes.length > 0;
        const hasCompletionRules = cc.completionRules && Object.keys(cc.completionRules).length > 0;
        if (hasCategories || hasRewardNodes || hasCompletionRules) {
            const clean = {};
            if (hasCategories) {
                clean.categories = cc.categories.map(c => {
                    const cat = {};
                    if (c.categoryId) cat.categoryId = c.categoryId;
                    if (c.displayName) cat.displayName = c.displayName;
                    if (c.sortOrder !== undefined && c.sortOrder !== null) cat.sortOrder = Number(c.sortOrder);
                    if (c.description) cat.description = c.description;
                    if (c.trackingVisible !== undefined) cat.trackingVisible = c.trackingVisible;
                    if (c.toastsEnabled !== undefined) cat.toastsEnabled = c.toastsEnabled;
                    return cat;
                });
            }
            if (hasRewardNodes) {
                clean.rewardNodes = cc.rewardNodes.map(r => {
                    const rn = {};
                    if (r.rewardId) rn.rewardId = r.rewardId;
                    if (r.categoryId) rn.categoryId = r.categoryId;
                    if (r.completionMode) rn.completionMode = r.completionMode;
                    if (r.completionCount !== undefined && r.completionCount !== null) rn.completionCount = Number(r.completionCount);
                    if (r.rewards?.length) rn.rewards = r.rewards.map(exportReward);
                    return rn;
                });
            }
            if (hasCompletionRules) clean.completionRules = cc.completionRules;
            if (cc.closeManual !== undefined) clean.closeManual = !!cc.closeManual;
            if (cc.autoTrackOnProgress !== undefined) clean.autoTrackOnProgress = !!cc.autoTrackOnProgress;
            if (cc.showInNonCollectionTracker !== undefined) clean.showInNonCollectionTracker = !!cc.showInNonCollectionTracker;
            out.collectionConfig = clean;
        }
    }
    Object.keys(out).forEach(k => out[k] === undefined && delete out[k]);
    return out;
}
