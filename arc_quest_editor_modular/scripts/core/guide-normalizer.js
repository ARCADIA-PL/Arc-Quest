const textSpec = value => ({
    mode: value?.mode === 'translatable' ? 'translatable' : 'literal',
    value: value?.value || '',
    args: Array.isArray(value?.args) ? value.args : []
});

export const createGuideSkeleton = () => ({
    id: '', category: 'arc_quest:basics', title: textSpec(), summary: textSpec(), sortOrder: 0,
    hidden: false, repeatablePopup: false, icon: '', renderLargeIconOnIntro: false,
    showUnlockPopup: false, renderPopupBackground: false, popupBackground: '',
    unlockConditions: [], pages: []
});

export const createGuideCategorySkeleton = () => ({
    id: '', displayName: textSpec(), themeColor: 0x4FC3F7, sortOrder: 0, iconTexture: ''
});

export function normalizeImportedGuide(input, kind = 'guide') {
    if (kind === 'guideCategory') {
        return {
            id: input.id || '', displayName: textSpec(input.displayName),
            themeColor: input.themeColor ?? 0x4FC3F7, sortOrder: input.sortOrder ?? 0,
            iconTexture: input.iconTexture || ''
        };
    }
    return {
        id: input.id || '', category: input.category || 'arc_quest:basics',
        title: textSpec(input.title), summary: textSpec(input.summary), sortOrder: input.sortOrder ?? 0,
        hidden: input.hidden === true, repeatablePopup: input.repeatablePopup === true,
        icon: input.icon || '', renderLargeIconOnIntro: input.renderLargeIconOnIntro === true,
        showUnlockPopup: input.showUnlockPopup === true,
        renderPopupBackground: input.renderPopupBackground === true,
        popupBackground: input.popupBackground || '', unlockConditions: input.unlockConditions || [],
        pages: (input.pages || []).map(page => ({
            description: textSpec(page.description),
            media: {
                type: page.media?.type || 'image', texture: page.media?.texture || '',
                sceneId: page.media?.sceneId || '', width: page.media?.width ?? 180,
                height: page.media?.height ?? 90, autoplay: page.media?.autoplay !== false,
                loop: page.media?.loop === true
            }
        }))
    };
}

export function exportGuideToDatapack(document, kind = 'guide') {
    return JSON.parse(JSON.stringify(document));
}
