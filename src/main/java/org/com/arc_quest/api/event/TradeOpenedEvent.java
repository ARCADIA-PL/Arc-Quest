package org.com.arc_quest.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/**
 * 商店打开事件。
 * <p>
 * 当玩家打开商店时触发（服务端）。
 * </p>
 *
 * @since 1.0.0
 */
public class TradeOpenedEvent extends Event {

    private final ServerPlayer player;
    private final String shopId;

    public TradeOpenedEvent(ServerPlayer player, String shopId) {
        this.player = player;
        this.shopId = shopId;
    }

    /**
     * 获取玩家。
     */
    public ServerPlayer getPlayer() {
        return player;
    }

    /**
     * 获取商店 ID。
     */
    public String getShopId() {
        return shopId;
    }
}
