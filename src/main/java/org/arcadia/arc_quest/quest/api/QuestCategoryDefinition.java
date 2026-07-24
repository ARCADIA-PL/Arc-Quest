package org.arcadia.arc_quest.quest.api;

public record QuestCategoryDefinition(
        String translationKey,
        int themeColor,
        boolean builtin,
        int sortOrder
) {

    public QuestCategoryDefinition(String translationKey, int themeColor, boolean builtin) {
        this(translationKey, themeColor, builtin, Integer.MAX_VALUE);
    }
}
