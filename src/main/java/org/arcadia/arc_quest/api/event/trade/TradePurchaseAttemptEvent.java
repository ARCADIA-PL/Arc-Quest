package org.arcadia.arc_quest.api.event.trade;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;
import org.arcadia.arc_quest.trade.api.TradeEntry;
import org.arcadia.arc_quest.trade.api.TradeShopDefinition;
import org.jetbrains.annotations.Nullable;

/**
 * 服务端执行交易校验和扣款前触发的事件。
 * <p>事件位于幂等请求体内部，请求重放不会重复触发。</p>
 */
public final class TradePurchaseAttemptEvent extends Event {
    private final ServerPlayer player;
    private final TradeShopDefinition shop;
    private final TradeEntry entry;
    private boolean cancelled;
    @Nullable private String cancellationReason;

    public TradePurchaseAttemptEvent(ServerPlayer player, TradeShopDefinition shop, TradeEntry entry) {
        this.player = player;
        this.shop = shop;
        this.entry = entry;
    }

    public ServerPlayer getPlayer() { return player; }
    public TradeShopDefinition getShop() { return shop; }
    public TradeEntry getEntry() { return entry; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Nullable public String getCancellationReason() { return cancellationReason; }

    public void cancel(@Nullable String reason) {
        cancelled = true;
        cancellationReason = reason;
    }
}
