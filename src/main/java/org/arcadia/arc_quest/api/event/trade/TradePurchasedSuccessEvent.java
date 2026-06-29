package org.arcadia.arc_quest.api.event.trade;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * 商品购买事件。
 * <p>
 * 当玩家成功购买商品时触发（服务端）。
 * </p>
 *
 * @since 1.0.0
 */
public class TradePurchasedSuccessEvent extends Event {

    private final ServerPlayer player;
    private final String shopId;
    private final String entryId;

    public TradePurchasedSuccessEvent(ServerPlayer player, String shopId, String entryId) {
        this.player = player;
        this.shopId = shopId;
        this.entryId = entryId;
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

    /**
     * 获取商品条目 ID。
     */
    public String getEntryId() {
        return entryId;
    }
}
