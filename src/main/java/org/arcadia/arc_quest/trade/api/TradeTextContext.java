package org.arcadia.arc_quest.trade.api;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.capability.ArcQuestPlayer;

import javax.annotation.Nullable;
import java.util.Map;

public record TradeTextContext(
        ServerPlayer player,
        String shopId,
        @Nullable ArcQuestPlayer questData,
        Map<String, Object> vars
) {
    public TradeTextContext {
        vars = vars == null ? Map.of() : Map.copyOf(vars);
    }

    public static TradeTextContext of(ServerPlayer player, String shopId, @Nullable ArcQuestPlayer questData) {
        return new TradeTextContext(player, shopId, questData, Map.of());
    }
}
