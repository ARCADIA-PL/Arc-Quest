package org.arcadia.arc_quest.api.event.trade;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

public class TradeOpenRejectedEvent extends Event {
    private final ServerPlayer player;
    private final String shopId;
    private final String errorKey;

    public TradeOpenRejectedEvent(ServerPlayer player, String shopId, String errorKey) {
        this.player = player;
        this.shopId = shopId;
        this.errorKey = errorKey;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public String getShopId() {
        return shopId;
    }

    public String getErrorKey() {
        return errorKey;
    }
}
