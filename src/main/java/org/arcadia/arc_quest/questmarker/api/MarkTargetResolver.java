package org.arcadia.arc_quest.questmarker.api;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

@FunctionalInterface
public interface MarkTargetResolver {
    ResolvedMarkTarget resolve(ServerPlayer player,
                              ServerLevel level,
                              MarkableObject.CustomResolver target);
}
