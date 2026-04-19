package org.com.arc_quest.trade.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.dialogue.runtime.DialogueProgressStore;
import org.com.arc_quest.dialogue.runtime.ProgressKey;
import org.com.arc_quest.dialogue.runtime.UnifiedCooldownManager;
import org.com.arc_quest.dialogue.util.TimeSanitizer;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.com.arc_quest.trade.api.ITradeOffer;
import org.com.arc_quest.trade.api.TradeEntry;
import org.com.arc_quest.trade.api.TradeShopDefinition;
import org.slf4j.Logger;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 交易运行时会话 —— 管理玩家的交易状态、购买历史和冷却。
 * <p>
 * 持久化数据存储在玩家 Capability 的 NBT 中（{@code "TradeData"} 子标签）。
 */
public final class TradeSession {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final ServerPlayer player;
    private final TradeShopDefinition shop;

    public TradeSession(ServerPlayer player, TradeShopDefinition shop) {
        this.player = player;
        this.shop = shop;
    }

    /**
     * 尝试执行一次交易。
     *
     * @param entryId 交易项 ID
     * @return 交易结果
     */
    public TradeResult executeTrade(String entryId) {
        TradeEntry entry = shop.getEntry(entryId);
        if (entry == null) {
            return TradeResult.fail("arc_quest.trade.error.not_found");
        }

        // 检查条件
        if (entry.getCondition() != null) {
            IQuestCapability cap = getCap();
            Set<ResourceLocation> completed = cap.getCompletedQuests().stream()
                    .map(ResourceLocation::parse)
                    .collect(Collectors.toSet());
            if (!entry.getCondition().test(player, completed, cap.getAllFlags(), cap.getAllVariables())) {
                return TradeResult.fail("arc_quest.trade.error.condition_not_met");
            }
        }

        checkAndResetPurchases(entryId, entry);

        IQuestCapability cap = getCap();
        if (!TradeEntryStateResolver.canPurchase(player, cap, shop.getShopId(), entry)) {
            if (TradeEntryStateResolver.isPurchaseLimitReached(cap, shop.getShopId(), entry)) {
                return TradeResult.fail("arc_quest.trade.error.max_purchases");
            } else if (TradeEntryStateResolver.isOnCooldown(player, cap, shop.getShopId(), entry)) {
                return TradeResult.fail("arc_quest.trade.error.on_cooldown");
            }
            return TradeResult.fail("arc_quest.trade.error.condition_not_met");
        }

        for (ITradeOffer cost : entry.getCosts()) {
            if (!cost.canAfford(player)) {
                LOGGER.warn("[Trade]  Cannot afford cost: entry={}, cost={}", entryId, cost);
                return TradeResult.fail("arc_quest.trade.error.cannot_afford");
            }
        }

        for (ITradeOffer cost : entry.getCosts()) {
            cost.execute(player);
        }

        for (ITradeOffer reward : entry.getRewards()) {
            reward.execute(player);
        }

        boolean shouldRecordCooldown = TradeEntryStateResolver.shouldRecordCooldown(getCap(), shop.getShopId(), entry);
        
        TradeEntryStateResolver.recordPurchase(getCap(), shop.getShopId(), entryId);

        if (shouldRecordCooldown) {
            TradeEntryStateResolver.recordCooldown(player, getCap(), shop.getShopId(), entryId);
        }

        LOGGER.info("[Trade] Player {} purchased '{}' from shop '{}'",
                player.getName().getString(), entryId, shop.getShopId());

        return TradeResult.success();
    }

    /**
     * 检查交易项是否对当前玩家可见（条件满足）
     */
    public boolean isEntryVisible(TradeEntry entry) {
        if (entry.getCondition() == null) return true;
        IQuestCapability cap = getCap();
        Set<ResourceLocation> completed = cap.getCompletedQuests().stream()
                .map(ResourceLocation::parse)
                .collect(Collectors.toSet());
        return entry.getCondition().test(player, completed, cap.getAllFlags(), cap.getAllVariables());
    }

    /**
     * 检查交易项是否可购买
     */
    public boolean canPurchase(TradeEntry entry) {
        if (!isEntryVisible(entry)) return false;
        
        IQuestCapability cap = getCap();
        return TradeEntryStateResolver.canPurchase(player, cap, shop.getShopId(), entry);
    }

    public int getPurchaseCount(String entryId) {
        IQuestCapability cap = getCap();
        return cap.getTradePurchaseCount(shop.getShopId(), entryId);
    }

    public int getRemainingPurchases(TradeEntry entry) {
        if (!entry.hasLimit()) return -1;
        return Math.max(0, entry.getMaxPurchases() - getPurchaseCount(entry.getEntryId()));
    }

    /**
     * 获取冷却剩余秒数（用于客户端显示）
     */
    public int getCooldownRemaining(String entryId, TradeEntry entry) {
        if (!entry.hasCooldown()) return 0;

        IQuestCapability cap = getCap();

        long lastPurchaseTime = cap.getTradeLastPurchaseTime(shop.getShopId(), entryId);
        if (lastPurchaseTime == 0) return 0;

        return switch (entry.getCooldownType()) {
            case NONE -> 0;
            case SECONDS -> {
                long nowRealTime = TimeSanitizer.getCurrentRealTime();
                long elapsed = (nowRealTime - lastPurchaseTime) / 1000;
                yield Math.max(0, (int)(entry.getCooldownValue() - elapsed));
            }
            case GAME_DAY -> {
                // 使用 TimeSanitizer 统一获取时间
                long nowGameTime = TimeSanitizer.getCurrentGameTime(player);
                long nowDayTime = TimeSanitizer.getCurrentDayTime(player);

                ProgressKey key = ProgressKey.ofTrade(shop.getShopId(), entryId);
                DialogueProgressStore.Entry storeEntry = cap.getDialogueProgress().getChoiceSelection(key);

                if (!storeEntry.exists() || storeEntry.dayTime() < 0) {
                    yield 0;
                }

                boolean onCooldown = UnifiedCooldownManager.isOnCooldown(
                        storeEntry, entry.getCooldownType(), (int) entry.getCooldownValue(),
                        entry.getResetTimeTicks(),
                        TimeSanitizer.getCurrentRealTime(), nowGameTime, nowDayTime
                );

                if (!onCooldown) {
                    yield 0;
                }

                long currentDayTick = nowDayTime % 24000;
                int remainingTicks = (int) (24000 - currentDayTick);
                yield Math.max(1, remainingTicks / 20);
            }
            case GAME_TICK -> {
                // 使用 TimeSanitizer 统一获取时间
                long nowGameTime = TimeSanitizer.getCurrentGameTime(player);
                long nowDayTime = TimeSanitizer.getCurrentDayTime(player);

                ProgressKey key = ProgressKey.ofTrade(shop.getShopId(), entryId);
                DialogueProgressStore.Entry storeEntry = cap.getDialogueProgress().getChoiceSelection(key);

                int remainingTicks = UnifiedCooldownManager
                        .getGameTickCooldownRemainingTicks(
                                storeEntry, entry.getResetTimeTicks(),
                                nowGameTime, nowDayTime
                        );
                yield Math.max(0, remainingTicks / 20);
            }
        };
    }


    /**
     * 获取格式化的冷却文本（适配三种冷却类型）
     */
    public String getCooldownText(String entryId, TradeEntry entry) {
        if (!entry.hasCooldown()) return "";

        IQuestCapability cap = getCap();

        long lastPurchaseTime = cap.getTradeLastPurchaseTime(shop.getShopId(), entryId);
        if (lastPurchaseTime == 0) return "";

        return switch (entry.getCooldownType()) {
            case NONE -> "";
            case SECONDS -> {
                long nowRealTime = TimeSanitizer.getCurrentRealTime();
                long elapsed = (nowRealTime - lastPurchaseTime) / 1000;
                long remaining = Math.max(0, entry.getCooldownValue() - elapsed);
                yield remaining > 0 ? remaining + "s" : "";
            }
            case GAME_DAY -> {
                // 使用 TimeSanitizer 统一获取时间
                long nowGameTime = TimeSanitizer.getCurrentGameTime(player);
                long nowDayTime = TimeSanitizer.getCurrentDayTime(player);

                ProgressKey key = ProgressKey.ofTrade(shop.getShopId(), entryId);
                DialogueProgressStore.Entry storeEntry = cap.getDialogueProgress().getChoiceSelection(key);

                if (!storeEntry.exists()) {
                    yield "";
                }

                boolean onCooldown = UnifiedCooldownManager.isOnCooldown(
                        storeEntry, entry.getCooldownType(), (int) entry.getCooldownValue(),
                        entry.getResetTimeTicks(),
                        TimeSanitizer.getCurrentRealTime(), nowGameTime, nowDayTime
                );

                // 使用翻译键
                yield onCooldown ? Component.translatable("arc_quest.trade.cooldown.game_day").getString() : "";
            }
            case GAME_TICK -> {
                // 使用 TimeSanitizer 统一获取时间
                long nowGameTime = TimeSanitizer.getCurrentGameTime(player);
                long nowDayTime = TimeSanitizer.getCurrentDayTime(player);

                ProgressKey key = ProgressKey.ofTrade(shop.getShopId(), entryId);
                DialogueProgressStore.Entry storeEntry = cap.getDialogueProgress().getChoiceSelection(key);

                int remainingTicks = UnifiedCooldownManager
                        .getGameTickCooldownRemainingTicks(
                                storeEntry, entry.getResetTimeTicks(),
                                nowGameTime, nowDayTime
                        );

                if (remainingTicks <= 0) {
                    yield "";
                }

                if (remainingTicks < 60) {
                    yield remainingTicks + "t";
                } else if (remainingTicks < 1200) {
                    yield (remainingTicks / 20) + "s";
                } else {
                    long minutes = remainingTicks / 1200;
                    long seconds = (remainingTicks % 1200) / 20;
                    yield String.format("%dm%ds", minutes, seconds);
                }
            }
        };
    }

    /**
     * 检查并执行限购恢复。
     * <p>
     * 支持两种恢复机制：
     * <ol>
     *   <li>冷却自动恢复：基于 CooldownType 判断是否过期（仅当限购满时）</li>
     *   <li>自定义条件恢复：使用 Predicate<ServerPlayer> 判断</li>
     * </ol>
     */
    private void checkAndResetPurchases(String entryId, TradeEntry entry) {
        if (!entry.hasLimit()) return;
        
        var resetCondition = entry.getPurchaseResetCondition();
        if (resetCondition == null) return;
        
        IQuestCapability cap = getCap();
        int currentCount = cap.getTradePurchaseCount(shop.getShopId(), entryId);
        if (currentCount == 0) return;
        
        boolean shouldReset = false;
        
        if (TradeEntryStateResolver.shouldResetByCooldown(player, cap, shop.getShopId(), entry)) {
            shouldReset = true;
        }
        
        if (!shouldReset) {
            try {
                shouldReset = resetCondition.test(player);
                if (shouldReset) {
                    LOGGER.info("[Trade] Purchase limit reset by custom condition for entry={}", entryId);
                }
            } catch (Exception e) {
                LOGGER.warn("[Trade] Error evaluating purchase reset condition for entry={}: {}", 
                        entryId, e.getMessage());
            }
        }
        
        if (shouldReset && currentCount > 0) {
            TradeEntryStateResolver.resetPurchaseAndCooldown(cap, shop.getShopId(), entryId);
        }
    }

    private IQuestCapability getCap() {
        return player.getCapability(QuestCapabilityProvider.QUEST_CAP).orElse(null);
    }

    public TradeShopDefinition getShop() { return shop; }
    public ServerPlayer getPlayer() { return player; }

    public record TradeResult(boolean succeeded, String errorKey) {
        public static TradeResult success() {
            return new TradeResult(true, null);
        }
        
        public static TradeResult fail(String errorKey) {
            return new TradeResult(false, errorKey);
        }
    }
}
