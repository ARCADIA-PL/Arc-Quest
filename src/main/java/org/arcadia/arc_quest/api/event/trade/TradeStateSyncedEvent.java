package org.arcadia.arc_quest.api.event.trade;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

public class TradeStateSyncedEvent extends Event {
    public enum SyncResult { SENT, DROPPED }

    private final ServerPlayer player;
    private final String shopId;
    private final String reason;
    private final SyncResult result;

    public TradeStateSyncedEvent(ServerPlayer player, String shopId, String reason, SyncResult result) {
        this.player = player;
        this.shopId = shopId;
        this.reason = reason;
        this.result = result;
    }

    public ServerPlayer getPlayer() { return player; }
    public String getShopId() { return shopId; }
    public String getReason() { return reason; }
    public SyncResult getSyncResult() { return result; }
}
