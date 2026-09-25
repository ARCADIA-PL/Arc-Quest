package org.arcadia.arc_quest.trade.network;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.arcadia.arc_quest.client.util.ClientCooldownHelper;
import org.arcadia.arc_quest.client.util.GuiSoundManager;
import org.arcadia.arc_quest.trade.api.CostShortfallLine;
import org.arcadia.arc_quest.trade.api.TradeEntry;
import org.arcadia.arc_quest.trade.api.TradeShopDefinition;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClientTradeCache {

    public static final ClientTradeCache INSTANCE = new ClientTradeCache();
    private final Map<String, TradeSessionData> activeSessions = new HashMap<>();

    // 性能优化：entryId -> globalIndex 映射缓存（避免 O(n) 查找）
    private final Map<String, Map<String, Integer>> globalIndexCache = new HashMap<>();
    @Nullable
    private TradeShopDefinition presentation;

    private ClientTradeCache() {
    }

    public void setPlayerSessionEpoch(String shopId, long playerSessionEpoch) {
        acceptPlayerSessionEpoch(shopId, playerSessionEpoch);
    }

    public boolean acceptPlayerSessionEpoch(String shopId, long playerSessionEpoch) {
        TradeSessionData data = activeSessions.computeIfAbsent(shopId, TradeSessionData::new);
        long normalizedEpoch = Math.max(0L, playerSessionEpoch);
        if (normalizedEpoch > 0L && data.playerSessionEpoch > normalizedEpoch) {
            return false;
        }
        data.playerSessionEpoch = normalizedEpoch;
        return true;
    }

    public long getPlayerSessionEpoch(String shopId) {
        TradeSessionData data = activeSessions.get(shopId);
        return data != null ? data.playerSessionEpoch : 0L;
    }

    public C2SRequestTradePacket createPurchasePacket(String shopId, String entryId,
                                                       C2SRequestTradePacket.ScreenType screenType) {
        return C2SRequestTradePacket.purchaseWithScreenType(
                shopId, entryId, screenType, UUID.randomUUID(), getPlayerSessionEpoch(shopId));
    }

    public void clear() {
        activeSessions.clear();
        globalIndexCache.clear();
        presentation = null;
    }

    /** 仅保存当前临时屏幕的商品；不会修改冻结的 Registry 或服务端商店定义。 */
    public void setPresentation(TradeShopDefinition definition) {
        java.util.Objects.requireNonNull(definition);
        if (TradeRegistry.get(definition.getShopId()) != null) {
            throw new IllegalArgumentException("Temporary trade presentation must not shadow a registered shop: " + definition.getShopId());
        }
        if (presentation != null) clearPresentation(presentation.getShopId());
        presentation = definition;
        globalIndexCache.remove(definition.getShopId());
    }

    public void clearPresentation(String shopId) {
        if (presentation != null && presentation.getShopId().equals(shopId)) {
            presentation = null;
            globalIndexCache.remove(shopId);
            activeSessions.remove(shopId);
        }
    }

    public void clearPresentation() {
        if (presentation != null) clearPresentation(presentation.getShopId());
    }

    @Nullable
    private TradeShopDefinition resolveDefinition(String shopId) {
        return presentation != null && presentation.getShopId().equals(shopId)
                ? presentation : TradeRegistry.get(shopId);
    }

    public void playOpenSound(String shopId, String openSoundId) {
        if (openSoundId == null || openSoundId.isEmpty()) return;
        try {
            ResourceLocation rl = ResourceLocation.tryParse(openSoundId);
            if (rl != null) {
                SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(rl);
                if (sound != null) GuiSoundManager.play(sound);
                else ArcQuestLog.warn(ArcQuestLog.Category.TRADE, "Open sound not found: {}", openSoundId);
            }
        } catch (Exception e) {
            ArcQuestLog.error(ArcQuestLog.Category.TRADE, "Failed to play open sound: {}", openSoundId, e);
        }
    }

    public void playCloseSound(String shopId, String closeSoundId) {
        if (closeSoundId == null || closeSoundId.isEmpty()) return;
        try {
            ResourceLocation rl = ResourceLocation.tryParse(closeSoundId);
            if (rl != null) {
                SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(rl);
                if (sound != null) GuiSoundManager.play(sound);
                else ArcQuestLog.warn(ArcQuestLog.Category.TRADE, "Close sound not found: {}", closeSoundId);
            }
        } catch (Exception e) {
            ArcQuestLog.error(ArcQuestLog.Category.TRADE, "Failed to play close sound: {}", closeSoundId, e);
        }
    }

    public void handlePurchaseResult(String shopId, String entryId, boolean success,
                                     @Nullable S2COpenTradePacket.FailReason failReason,
                                     @Nullable String errorKey) {
        if (entryId == null || entryId.isEmpty()) return;
        var shopDef = resolveDefinition(shopId);
        if (shopDef == null) return;
        TradeEntry entry = shopDef.getEntry(entryId);
        if (entry == null) return;

        TradeSessionData data = activeSessions.computeIfAbsent(shopId, TradeSessionData::new);
        if (success) {
            data.feedback.lastFailedEntryId = null;
            data.feedback.lastShortfallLines = List.of();
            data.feedback.lastFailReason = null;
            data.feedback.lastErrorKey = null;
            GuiSoundManager.play(entry.getPurchaseSuccessSound());
        } else {
            if (failReason != S2COpenTradePacket.FailReason.CANNOT_AFFORD) {
                data.feedback.lastFailedEntryId = null;
                data.feedback.lastShortfallLines = List.of();
            }
            data.feedback.lastFailReason = failReason;
            data.feedback.lastErrorKey = errorKey;
            SoundEvent sound = switch (failReason != null ? failReason : S2COpenTradePacket.FailReason.GENERIC) {
                case COOLDOWN -> entry.getCooldownSound();
                case LIMIT_REACHED -> entry.getLimitReachedSound();
                case CONDITION_FAIL -> entry.getConditionFailSound();
                default -> entry.getPurchaseFailSound();
            };
            GuiSoundManager.play(sound);
        }
    }

    public void updateSession(String shopId, int[] purchaseCounts, int[] maxPurchases,
                              long[] lastPurchaseTimes, long[] purchaseGameTimes, long[] purchaseDayTimes,
                              int[] cooldownTypes, long[] cooldownValues, int[] resetTimeTicks,
                              boolean[] visibility, boolean[] canBuyConditions) {
        TradeSessionData data = activeSessions.computeIfAbsent(shopId, TradeSessionData::new);
        data.authority.purchaseCounts = purchaseCounts;
        data.authority.maxPurchases = maxPurchases;
        data.authority.lastPurchaseTimes = lastPurchaseTimes;
        data.authority.purchaseGameTimes = purchaseGameTimes;
        data.authority.purchaseDayTimes = purchaseDayTimes;
        data.authority.cooldownTypes = cooldownTypes;
        data.authority.cooldownValues = cooldownValues;
        data.authority.resetTimeTicks = resetTimeTicks;
        data.authority.visibility = visibility;
        data.authority.canBuyConditions = canBuyConditions;

        // 状态更新时清除索引缓存
        globalIndexCache.remove(shopId);
    }

    public boolean isOnCooldown(String shopId, int entryIndex) {
        TradeSessionData data = activeSessions.get(shopId);
        if (data == null || entryIndex < 0 || entryIndex >= data.authority.lastPurchaseTimes.length) return false;
        return ClientCooldownHelper.isOnCooldown(
                data.authority.lastPurchaseTimes[entryIndex],
                data.authority.purchaseGameTimes[entryIndex],
                data.authority.purchaseDayTimes[entryIndex],
                data.authority.cooldownTypes[entryIndex],
                data.authority.cooldownValues[entryIndex],
                data.authority.resetTimeTicks[entryIndex]
        );
    }

    public String getCooldownText(String shopId, int entryIndex) {
        TradeSessionData data = activeSessions.get(shopId);
        if (data == null || entryIndex < 0 || entryIndex >= data.authority.lastPurchaseTimes.length) return "";
        return ClientCooldownHelper.getCooldownText(
                data.authority.lastPurchaseTimes[entryIndex],
                data.authority.purchaseGameTimes[entryIndex],
                data.authority.purchaseDayTimes[entryIndex],
                data.authority.cooldownTypes[entryIndex],
                data.authority.cooldownValues[entryIndex],
                data.authority.resetTimeTicks[entryIndex]
        );
    }

    public int getPurchaseCount(String shopId, int entryIndex) {
        TradeSessionData data = activeSessions.get(shopId);
        if (data == null || entryIndex < 0 || entryIndex >= data.authority.purchaseCounts.length) return 0;
        return data.authority.purchaseCounts[entryIndex];
    }

    public int getMaxPurchases(String shopId, int entryIndex) {
        TradeSessionData data = activeSessions.get(shopId);
        if (data == null || entryIndex < 0 || entryIndex >= data.authority.maxPurchases.length) return -1;
        return data.authority.maxPurchases[entryIndex];
    }

    public int getRemainingPurchases(String shopId, int entryIndex) {
        int max = getMaxPurchases(shopId, entryIndex);
        if (max < 0) return -1;
        return Math.max(0, max - getPurchaseCount(shopId, entryIndex));
    }

    public boolean isVisible(String shopId, int entryIndex) {
        TradeSessionData data = activeSessions.get(shopId);
        if (data == null || entryIndex < 0 || entryIndex >= data.authority.visibility.length) return true;
        return data.authority.visibility[entryIndex];
    }

    public boolean canBuy(String shopId, int entryIndex) {
        TradeSessionData data = activeSessions.get(shopId);
        if (data == null || entryIndex < 0 || entryIndex >= data.authority.canBuyConditions.length) return false;
        return data.authority.canBuyConditions[entryIndex];
    }

    public int getGlobalIndex(String shopId, String entryId) {
        if (entryId == null || entryId.isEmpty()) return -1;

        // 先查缓存（O(1) 查找）
        Map<String, Integer> shopCache = globalIndexCache.get(shopId);
        if (shopCache != null) {
            Integer cached = shopCache.get(entryId);
            if (cached != null) return cached;
        }

        // 缓存未命中，执行原逻辑
        var shopDef = resolveDefinition(shopId);
        if (shopDef == null) return -1;

        int index = 0;
        for (TradeEntry entry : shopDef.getAllEntries()) {
            if (entryId.equals(entry.getEntryId())) {
                // 缓存结果
                globalIndexCache.computeIfAbsent(shopId, k -> new HashMap<>())
                        .put(entryId, index);
                return index;
            }
            index++;
        }
        return -1;
    }

    public boolean isEntryCoolingDown(String shopId, int entryIndex, TradeEntry entry) {
        return isOnCooldown(shopId, entryIndex);
    }

    public boolean isPurchaseLimitReached(String shopId, int entryIndex, TradeEntry entry) {
        return getMaxPurchases(shopId, entryIndex) >= 0 && getRemainingPurchases(shopId, entryIndex) == 0;
    }

    public boolean isConditionBlocked(String shopId, int entryIndex, TradeEntry entry) {
        return isVisible(shopId, entryIndex) && !canBuy(shopId, entryIndex);
    }

    public boolean canPurchase(String shopId, int entryIndex) {
        return isVisible(shopId, entryIndex)
                && !isOnCooldown(shopId, entryIndex)
                && !isPurchaseLimitReached(shopId, entryIndex, null)
                && canBuy(shopId, entryIndex);
    }

    public void closeAllExcept(String shopId) {
        if (presentation != null && !presentation.getShopId().equals(shopId)) {
            clearPresentation(presentation.getShopId());
        }
        activeSessions.entrySet().removeIf(entry -> !entry.getKey().equals(shopId));
        // 清除其他商店的索引缓存
        globalIndexCache.entrySet().removeIf(entry -> !entry.getKey().equals(shopId));
    }

    public void recordShortfall(String shopId, String entryId, List<CostShortfallLine> shortfallLines) {
        TradeSessionData data = activeSessions.computeIfAbsent(shopId, TradeSessionData::new);
        data.feedback.lastFailedEntryId = entryId;
        data.feedback.lastShortfallLines = shortfallLines != null ? List.copyOf(shortfallLines) : List.of();
    }

    public void clearFeedback(String shopId) {
        TradeSessionData data = activeSessions.get(shopId);
        if (data == null) return;
        data.feedback.lastFailedEntryId = null;
        data.feedback.lastShortfallLines = List.of();
        data.feedback.lastFailReason = null;
        data.feedback.lastErrorKey = null;
    }

    public List<CostShortfallLine> getShortfall(String shopId, String entryId) {
        TradeSessionData data = activeSessions.get(shopId);
        if (data == null || entryId == null || !entryId.equals(data.feedback.lastFailedEntryId)) return List.of();
        return data.feedback.lastShortfallLines;
    }

    @Nullable
    public List<TradeEntry> getShopEntries(String shopId) {
        var shopDef = resolveDefinition(shopId);
        return shopDef != null ? new ArrayList<>(shopDef.getAllEntries()) : null;
    }

    // 可选快照接口（给外层只读消费，后续治理会更稳）
    @Nullable
    public AuthoritySnapshot authoritySnapshot(String shopId) {
        TradeSessionData data = activeSessions.get(shopId);
        if (data == null) return null;
        return new AuthoritySnapshot(
                data.authority.purchaseCounts,
                data.authority.maxPurchases,
                data.authority.lastPurchaseTimes,
                data.authority.purchaseGameTimes,
                data.authority.purchaseDayTimes,
                data.authority.cooldownTypes,
                data.authority.cooldownValues,
                data.authority.resetTimeTicks,
                data.authority.visibility,
                data.authority.canBuyConditions
        );
    }

    @Nullable
    public FeedbackSnapshot feedbackSnapshot(String shopId) {
        TradeSessionData data = activeSessions.get(shopId);
        if (data == null) return null;
        return new FeedbackSnapshot(
                data.feedback.lastFailedEntryId,
                List.copyOf(data.feedback.lastShortfallLines),
                data.feedback.lastFailReason,
                data.feedback.lastErrorKey
        );
    }

    static final class TradeSessionData {
        final String shopId;
        final AuthorityState authority = new AuthorityState();
        final FeedbackState feedback = new FeedbackState();
        long playerSessionEpoch;

        TradeSessionData(String shopId) {
            this.shopId = shopId;
        }
    }

    static final class AuthorityState {
        int[] purchaseCounts = new int[0];
        int[] maxPurchases = new int[0];
        long[] lastPurchaseTimes = new long[0];
        long[] purchaseGameTimes = new long[0];
        long[] purchaseDayTimes = new long[0];
        int[] cooldownTypes = new int[0];
        long[] cooldownValues = new long[0];
        int[] resetTimeTicks = new int[0];
        boolean[] visibility = new boolean[0];
        boolean[] canBuyConditions = new boolean[0];
    }

    static final class FeedbackState {
        @Nullable
        String lastFailedEntryId;
        List<CostShortfallLine> lastShortfallLines = List.of();
        @Nullable
        S2COpenTradePacket.FailReason lastFailReason;
        @Nullable
        String lastErrorKey;
    }

    public record AuthoritySnapshot(
            int[] purchaseCounts,
            int[] maxPurchases,
            long[] lastPurchaseTimes,
            long[] purchaseGameTimes,
            long[] purchaseDayTimes,
            int[] cooldownTypes,
            long[] cooldownValues,
            int[] resetTimeTicks,
            boolean[] visibility,
            boolean[] canBuyConditions
    ) {
    }

    public record FeedbackSnapshot(
            @Nullable String lastFailedEntryId,
            List<CostShortfallLine> shortfallLines,
            @Nullable S2COpenTradePacket.FailReason failReason,
            @Nullable String errorKey
    ) {
    }
}
