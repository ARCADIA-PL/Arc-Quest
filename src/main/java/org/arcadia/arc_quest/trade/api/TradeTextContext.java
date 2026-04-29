package org.arcadia.arc_quest.trade.api;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.capability.IQuestCapability;

import javax.annotation.Nullable;
import java.util.Map;

public record TradeTextContext(
        ServerPlayer player,
        String shopId,
        @Nullable IQuestCapability questCap,
        Map<String, Object> vars
) {
    public TradeTextContext {
        vars = vars == null ? Map.of() : Map.copyOf(vars);
    }

    public static TradeTextContext of(ServerPlayer player, String shopId, @Nullable IQuestCapability questCap) {
        return new TradeTextContext(player, shopId, questCap, Map.of());
    }
}
