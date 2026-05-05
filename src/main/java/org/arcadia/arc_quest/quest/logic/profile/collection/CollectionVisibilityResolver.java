package org.arcadia.arc_quest.quest.logic.profile.collection;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.CollectionEntryConfig;
import org.arcadia.arc_quest.quest.capability.IQuestCapability;
import org.slf4j.Logger;

import java.util.Set;

public final class CollectionVisibilityResolver {

    private static final Logger LOGGER = LogUtils.getLogger();

    private CollectionVisibilityResolver() {
    }

    public static boolean shouldBeVisible(ServerPlayer player,
                                          Set<ResourceLocation> completedQuests,
                                          Set<String> flags,
                                          IQuestCapability cap,
                                          CollectionEntryConfig entryConfig) {
        if (player == null || cap == null || entryConfig == null) return false;
        return switch (entryConfig.getVisibilityMode()) {
            case VISIBLE_BY_DEFAULT -> true;
            case HIDDEN_BY_DEFAULT, DISCOVER_ONLY -> false;
            case CONDITIONAL -> areConditionsSatisfied(player, completedQuests, flags, cap, entryConfig);
        };
    }

    private static boolean areConditionsSatisfied(ServerPlayer player,
                                                  Set<ResourceLocation> completedQuests,
                                                  Set<String> flags,
                                                  IQuestCapability cap,
                                                  CollectionEntryConfig entryConfig) {
        for (var condition : entryConfig.getVisibilityConditions()) {
            if (condition == null) return false;
            try {
                if (!condition.test(player, completedQuests, flags, cap.getAllVariables())) return false;
            } catch (RuntimeException e) {
                LOGGER.warn("[ArcQuest] Collection visibility condition failed for category {}", entryConfig.getCategoryId(), e);
                return false;
            }
        }
        return true;
    }
}
