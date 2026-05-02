package org.arcadia.arc_quest.questmarker.api;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.capability.IQuestCapability;

@FunctionalInterface
public interface MarkActivation {
    boolean test(ServerPlayer player, IQuestCapability cap);
}
