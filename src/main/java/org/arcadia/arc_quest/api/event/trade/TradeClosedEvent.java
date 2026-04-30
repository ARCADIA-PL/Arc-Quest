package org.arcadia.arc_quest.api.event.trade;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.Nullable;

public class TradeClosedEvent extends Event {
    @Nullable
    private final Player player;
    private final String shopId;
    private final boolean clientSide;

    public TradeClosedEvent(@Nullable Player player, String shopId, boolean clientSide) {
        this.player = player;
        this.shopId = shopId;
        this.clientSide = clientSide;
    }

    @Nullable
    public Player getPlayer() {
        return player;
    }

    public String getShopId() {
        return shopId;
    }

    public boolean isClientSide() {
        return clientSide;
    }
}
