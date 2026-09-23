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
