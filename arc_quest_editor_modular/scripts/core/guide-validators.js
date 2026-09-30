import {validateQuestCondition} from './condition-codec.js';

export function validateGuide(document, kind = 'guide') {
    const issues = [];
    const error = (path, msg) => issues.push({lvl: 'err', path, msg});
    if (!document?.id) error('id', 'id is required');
    if (kind === 'guideCategory') {
        if (!document?.displayName?.value) error('displayName', 'displayName is required');
        return issues;
    }
    if (!Array.isArray(document?.unlockConditions)) error('unlockConditions', 'unlockConditions 必须是数组');
    else document.unlockConditions.forEach((condition, index) => validateQuestCondition(condition, `unlockConditions[${index}]`, issues));
    if (!document?.category) error('category', 'category is required');
    if (!document?.title?.value) error('title', 'title is required');
    const resourceId = value => typeof value === 'string' && /^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(value);
    if (document.itemAssociations != null && !Array.isArray(document.itemAssociations)) {
        error('itemAssociations', 'itemAssociations must be an array');
    } else (document.itemAssociations || []).forEach((association, index) => {
        const path = `itemAssociations[${index}]`;
        if (!association || typeof association !== 'object' || Array.isArray(association)) {
            error(path, 'association must be an object');
            return;
        }
        const item = typeof association.item === 'string' && association.item.trim() !== '';
        const tag = typeof association.tag === 'string' && association.tag.trim() !== '';
        if (item === tag) error(path, 'exactly one of item/tag is required');
        if (item && !resourceId(association.item)) error(`${path}.item`, 'invalid item resource id');
        if (tag && !resourceId(association.tag)) error(`${path}.tag`, 'invalid tag resource id');
        const page = association.pageIndex ?? 0;
        if (!Number.isInteger(page) || page < 0 || page >= (document.pages?.length || 0)) {
            error(`${path}.pageIndex`, 'pageIndex must reference an existing zero-based guide page');
        }
    });
    if (!Array.isArray(document?.pages) || !document.pages.length) error('pages', 'at least one page is required');
    (document?.pages || []).forEach((page, index) => {
        const media = page?.media || {};
        if (!['none', 'image', 'ponder'].includes(media.type)) error(`pages[${index}].media.type`, 'invalid media type');
        if (media.type === 'image' && !media.texture) error(`pages[${index}].media.texture`, 'texture is required');
        if (media.type === 'ponder' && !media.sceneId) error(`pages[${index}].media.sceneId`, 'sceneId is required');
        if ((media.width ?? 0) <= 0 || (media.height ?? 0) <= 0) error(`pages[${index}].media`, 'width and height must be positive');
    });
    return issues;
}
