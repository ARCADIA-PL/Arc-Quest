package org.arcadia.arc_quest.guide.api;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.Arc_Quest;

public final class GuideCategories {

    public static final GuideCategory BASICS = create("basics", GuideText.translatable("guide_category.arc_quest.basics"), "guide_category.arc_quest.basics", 0x4FC3F7, 0, true, null);
    public static final GuideCategory QUEST = create("quest", GuideText.translatable("guide_category.arc_quest.quest"), "guide_category.arc_quest.quest", 0xFFD166, 10, true, null);
    public static final GuideCategory DIALOGUE = create("dialogue", GuideText.translatable("guide_category.arc_quest.dialogue"), "guide_category.arc_quest.dialogue", 0xB388FF, 20, true, null);
    public static final GuideCategory TRADE = create("trade", GuideText.translatable("guide_category.arc_quest.trade"), "guide_category.arc_quest.trade", 0x63E6BE, 30, true, null);
    public static final GuideCategory PONDER = create("ponder", GuideText.translatable("guide_category.arc_quest.ponder"), "guide_category.arc_quest.ponder", 0xFF8A65, 40, true, null);
    public static final GuideCategory ADVANCED = create("advanced", GuideText.translatable("guide_category.arc_quest.advanced"), "guide_category.arc_quest.advanced", 0xEF5350, 50, true, null);

    private GuideCategories() {
    }

    private static GuideCategory create(String path,
                                        GuideText displayName,
                                        String translationKey,
                                        int themeColor,
                                        int sortOrder,
                                        boolean builtin,
                                        ResourceLocation iconTexture) {
        return new GuideCategory(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, path), displayName, translationKey, themeColor, sortOrder, builtin, iconTexture);
    }
}
