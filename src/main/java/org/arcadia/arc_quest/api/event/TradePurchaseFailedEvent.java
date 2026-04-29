package org.arcadia.arc_quest.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/**
 * 商品购买失败事件。
 * <p>
 * 当玩家购买商品失败时触发（服务端）。
 * </p>
 *
 * @since 1.0.0
 */
public class TradePurchaseFailedEvent extends Event {

    /**
     * 购买失败原因枚举。
     */
    public enum FailureReason {
        /**
         * 条件不满足（前置任务、等级等）
         */
        CONDITION_NOT_MET,
        /**
         * 冷却中
         */
        ON_COOLDOWN,
        /**
         * 已达限购次数
         */
        MAX_PURCHASES_REACHED,
        /**
         * 余额不足
         */
        INSUFFICIENT_FUNDS,
        /**
         * 其他未知原因
         */
        UNKNOWN
    }

    private final ServerPlayer player;
    private final String shopId;
    private final String entryId;
    private final FailureReason reason;

    public TradePurchaseFailedEvent(ServerPlayer player, String shopId, String entryId, FailureReason reason) {
        this.player = player;
        this.shopId = shopId;
        this.entryId = entryId;
        this.reason = reason;
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

    /**
     * 获取失败原因。
     */
    public FailureReason getReason() {
        return reason;
    }

    /**
     * 检查是否因为条件不满足而失败。
     */
    public boolean isConditionNotMet() {
        return reason == FailureReason.CONDITION_NOT_MET;
    }

    /**
     * 检查是否因为冷却而失败。
     */
    public boolean isOnCooldown() {
        return reason == FailureReason.ON_COOLDOWN;
    }

    /**
     * 检查是否因为达到限购次数而失败。
     */
    public boolean isMaxPurchasesReached() {
        return reason == FailureReason.MAX_PURCHASES_REACHED;
    }

    /**
     * 检查是否因为余额不足而失败。
     */
    public boolean isInsufficientFunds() {
        return reason == FailureReason.INSUFFICIENT_FUNDS;
    }
}
