package org.arcadia.arc_quest.guide.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.Arc_Quest;

public final class GuideCategories {

    public static final GuideCategory BASICS = create("basics", Component.translatable("guide_category.arc_quest.basics"), 0x4FC3F7, 0, true, null);
    public static final GuideCategory QUEST = create("quest", Component.translatable("guide_category.arc_quest.quest"), 0xFFD166, 10, true, null);
    public static final GuideCategory DIALOGUE = create("dialogue", Component.translatable("guide_category.arc_quest.dialogue"), 0xB388FF, 20, true, null);
    public static final GuideCategory TRADE = create("trade", Component.translatable("guide_category.arc_quest.trade"), 0x63E6BE, 30, true, null);
    public static final GuideCategory PONDER = create("ponder", Component.translatable("guide_category.arc_quest.ponder"), 0xFF8A65, 40, true, null);
    public static final GuideCategory ADVANCED = create("advanced", Component.translatable("guide_category.arc_quest.advanced"), 0xEF5350, 50, true, null);

    private GuideCategories() {
    }

    private static GuideCategory create(String path,
                                        Component displayName,
                                        int themeColor,
                                        int sortOrder,
                                        boolean builtin,
                                        ResourceLocation iconTexture) {
        return new GuideCategory(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, path), displayName, themeColor, sortOrder, builtin, iconTexture);
    }
}
