package org.arcadia.arc_quest.quest.api;

public record QuestCategoryDefinition(
        String translationKey,
        int themeColor,
        boolean builtin
) {
}
