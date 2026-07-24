package org.arcadia.arc_quest.quest.api;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.registry.QuestCategoryRegistry;

public final class QuestCategories {

    public static final QuestCategory ARCHON = register("archon", "arc_quest.category.archon", 0xFFD700, 0);
    public static final QuestCategory COMPANION = register("companion", "arc_quest.category.companion", 0x00BFFF, 1);
    public static final QuestCategory DAILY = register("daily", "arc_quest.category.daily", 0x90EE90, 2);
    public static final QuestCategory ADVENTURE = register("adventure", "arc_quest.category.adventure", 0xDDA0DD, 1);
    public static final QuestCategory EVENT = register("event", "arc_quest.category.event", 0xFF6347, 0);

    private QuestCategories() {
    }

    private static QuestCategory register(String path, String translationKey, int themeColor, int sortOrder) {
        return QuestCategoryRegistry.register(
                ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, path),
                new QuestCategoryDefinition(translationKey, themeColor, true, sortOrder)
        );
    }
}
