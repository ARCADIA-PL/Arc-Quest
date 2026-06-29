package org.arcadia.arc_quest.api.event.trade;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

public class TradePurchaseRejectedEvent extends Event {
    private final ServerPlayer player;
    private final String shopId;
    private final String entryId;
    private final String errorKey;

    public TradePurchaseRejectedEvent(ServerPlayer player, String shopId, String entryId, String errorKey) {
        this.player = player;
        this.shopId = shopId;
        this.entryId = entryId;
        this.errorKey = errorKey;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public String getShopId() {
        return shopId;
    }

    public String getEntryId() {
        return entryId;
    }

    public String getErrorKey() {
        return errorKey;
    }
}
