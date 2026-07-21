package org.arcadia.arc_quest.quest.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record QuestConditionContext(
        @Nullable ServerPlayer player,
        Set<ResourceLocation> completedQuests,
        Set<String> flags,
        Map<String, Integer> variables
) {
    public QuestConditionContext {
        Objects.requireNonNull(completedQuests, "completedQuests");
        Objects.requireNonNull(flags, "flags");
        Objects.requireNonNull(variables, "variables");
    }
}
