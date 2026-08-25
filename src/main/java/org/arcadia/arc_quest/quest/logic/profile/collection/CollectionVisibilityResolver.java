package org.arcadia.arc_quest.quest.logic.profile.collection;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.core.CoreProcessors;
import org.arcadia.arc_quest.quest.api.CollectionEntryConfig;
import org.arcadia.arc_quest.quest.api.QuestConditionContext;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;

import java.util.Set;

public final class CollectionVisibilityResolver {
    private CollectionVisibilityResolver() {
    }

    public static boolean shouldBeVisible(ServerPlayer player,
                                          Set<ResourceLocation> completedQuests,
                                          Set<String> flags,
                                          ArcQuestPlayer data,
                                          CollectionEntryConfig entryConfig) {
        if (player == null || data == null || entryConfig == null) return false;
        return switch (entryConfig.getVisibilityMode()) {
            case VISIBLE_BY_DEFAULT -> true;
            case HIDDEN_BY_DEFAULT, DISCOVER_ONLY -> false;
            case CONDITIONAL -> areConditionsSatisfied(player, completedQuests, flags, data, entryConfig);
        };
    }

    private static boolean areConditionsSatisfied(ServerPlayer player,
                                                  Set<ResourceLocation> completedQuests,
                                                  Set<String> flags,
                                                  ArcQuestPlayer data,
                                                  CollectionEntryConfig entryConfig) {
        for (var condition : entryConfig.getVisibilityConditions()) {
            if (condition == null) return false;
            if (!CoreProcessors.get().conditions().evaluateSafely(condition,
                    new QuestConditionContext(player, completedQuests, flags, data.getAllVariables()),
                    false, null, "collection visibility category=" + entryConfig.getCategoryId())) return false;
        }
        return true;
    }
}
