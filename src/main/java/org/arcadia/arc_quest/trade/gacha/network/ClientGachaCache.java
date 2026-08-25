package org.arcadia.arc_quest.trade.gacha.network;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.sounds.SoundEvent;
import org.arcadia.arc_quest.client.util.ClientCooldownHelper;
import org.arcadia.arc_quest.client.util.GuiSoundManager;
import org.arcadia.arc_quest.trade.api.CostShortfallLine;
import org.arcadia.arc_quest.trade.gacha.registry.GachaRegistry;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 客户端抽奖数据镜像缓存。
 * 线程模型：仅客户端主线程访问（网络包通过 enqueueWork 切回主线程）。
 */
public final class ClientGachaCache {

    public static final ClientGachaCache INSTANCE = new ClientGachaCache();
    private final Map<String, GachaSessionData> gachaSessions = new HashMap<>();

    private ClientGachaCache() {
    }

    // ════════════════════════════════════════
    // 核心会话管理
    // ════════════════════════════════════════

    public void updateSession(String shopId, int pityCounter, int totalDraws) {
        GachaSessionData newSession = createSessionSnapshot(
                pityCounter, totalDraws, true, -1,
                0, 0, 0,
                0, 0, 0
        );
        copyRetainedState(gachaSessions.get(shopId), newSession);
        gachaSessions.put(shopId, newSession);
    }

    public void updateSession(String shopId, int pityCounter, int totalDraws,
                              long lastDrawRealTime, long lastDrawGameTime, long lastDrawDayTime,
                              int cooldownType, long cooldownValue, int resetTimeTicks) {
        GachaSessionData existing = gachaSessions.get(shopId);

        GachaSessionData newSession = createSessionSnapshot(
                pityCounter,
                totalDraws,
                existing != null ? existing.authority.canDraw : true,
                existing != null ? existing.authority.remainingDraws : -1,
                lastDrawRealTime,
                lastDrawGameTime,
                lastDrawDayTime,
                cooldownType,
                cooldownValue,
                resetTimeTicks
        );

        copyRetainedState(existing, newSession);
        gachaSessions.put(shopId, newSession);
    }

    public void updateSession(String shopId, int pityCounter, int totalDraws, boolean canDraw,
                              int remainingDraws,
                              long lastDrawRealTime, long lastDrawGameTime, long lastDrawDayTime,
                              int cooldownType, long cooldownValue, int resetTimeTicks) {
        GachaSessionData newSession = createSessionSnapshot(
                pityCounter, totalDraws, canDraw, remainingDraws,
                lastDrawRealTime, lastDrawGameTime, lastDrawDayTime,
                cooldownType, cooldownValue, resetTimeTicks
        );

        copyRetainedState(gachaSessions.get(shopId), newSession);
        gachaSessions.put(shopId, newSession);
    }

    public void updateSessionWithHistory(String shopId, int pityCounter, int totalDraws, boolean canDraw,
                                         int remainingDraws,
                                         long lastDrawRealTime, long lastDrawGameTime, long lastDrawDayTime,
                                         int cooldownType, long cooldownValue, int resetTimeTicks,
                                         List<DrawRecord> fullHistory) {
        GachaSessionData newSession = createSessionSnapshot(
                pityCounter, totalDraws, canDraw, remainingDraws,
                lastDrawRealTime, lastDrawGameTime, lastDrawDayTime,
                cooldownType, cooldownValue, resetTimeTicks
        );

        newSession.history.drawHistory.addAll(fullHistory);
        applyLatestHistoryState(newSession, fullHistory);

        // 全量权威快照覆盖后，清空旧反馈态
        newSession.feedback.lastFailReason = null;
        newSession.feedback.lastShortfallLines = List.of();

        gachaSessions.put(shopId, newSession);
    }

    private GachaSessionData createSessionSnapshot(int pityCounter, int totalDraws, boolean canDraw,
                                                   int remainingDraws,
                                                   long lastDrawRealTime, long lastDrawGameTime, long lastDrawDayTime,
                                                   int cooldownType, long cooldownValue, int resetTimeTicks) {
        return new GachaSessionData(
                pityCounter, totalDraws, canDraw, remainingDraws,
                lastDrawRealTime, lastDrawGameTime, lastDrawDayTime,
                cooldownType, cooldownValue, resetTimeTicks
        );
    }

    /**
     * 保留 UI 依赖态（历史和失败反馈），避免每次 state 包覆盖。
     */
    private void copyRetainedState(@Nullable GachaSessionData existing, GachaSessionData next) {
        if (existing == null) return;

        next.history.drawHistory.addAll(existing.history.drawHistory);
        next.history.lastDrawnItemId = existing.history.lastDrawnItemId;
        next.history.lastRarityName = existing.history.lastRarityName;
        next.history.lastActualCount = existing.history.lastActualCount;
        next.history.lastPityTriggered = existing.history.lastPityTriggered;
        next.history.lastDrawTime = existing.history.lastDrawTime;

        next.feedback.lastFailReason = existing.feedback.lastFailReason;
        next.feedback.lastShortfallLines = existing.feedback.lastShortfallLines;
    }

    private void applyLatestHistoryState(GachaSessionData session, List<DrawRecord> fullHistory) {
        if (fullHistory.isEmpty()) return;
        DrawRecord last = fullHistory.get(fullHistory.size() - 1);
        session.history.lastDrawnItemId = last.itemId();
        session.history.lastRarityName = last.rarityName();
        session.history.lastActualCount = last.actualCount();
        session.history.lastPityTriggered = last.pityTriggered();
        session.history.lastDrawTime = last.drawTime();
    }

    public void recordDrawResult(String shopId, String drawnItemId, String rarityName,
                                 int actualCount, boolean pityTriggered, int newPityCounter) {
        GachaSessionData session = gachaSessions.get(shopId);
        if (session == null) {
            session = new GachaSessionData(newPityCounter, 1);
            gachaSessions.put(shopId, session);
        } else {
            session.authority.pityCounter = newPityCounter;
            session.authority.totalDraws++;
        }

        session.history.lastDrawnItemId = drawnItemId;
        session.history.lastRarityName = rarityName;
        session.history.lastActualCount = actualCount;
        session.history.lastPityTriggered = pityTriggered;
        session.history.lastDrawTime = System.currentTimeMillis();

        session.feedback.lastFailReason = null;
        session.feedback.lastShortfallLines = List.of();

        session.history.drawHistory.add(new DrawRecord(
                drawnItemId, rarityName, actualCount, pityTriggered, session.history.lastDrawTime
        ));
        if (session.history.drawHistory.size() > 50) {
            session.history.drawHistory.remove(0);
        }
    }

    public void recordDrawFailure(String shopId, String failReason) {
        recordDrawFailure(shopId, failReason, List.of());
    }

    public void recordDrawFailure(String shopId, String failReason, List<CostShortfallLine> shortfallLines) {
        GachaSessionData session = gachaSessions.get(shopId);
        if (session == null) {
            session = new GachaSessionData(0, 0);
            gachaSessions.put(shopId, session);
        }

        session.feedback.lastFailReason = failReason;
        session.feedback.lastShortfallLines = shortfallLines != null ? List.copyOf(shortfallLines) : List.of();
        session.history.lastDrawTime = System.currentTimeMillis();
    }

    public void handleDrawResult(String shopId, @Nullable String itemId, @Nullable String rarityName,
                                 boolean success, @Nullable FailReason failReason) {
        if (!success) {
            SoundEvent sound = switch (failReason != null ? failReason : FailReason.GENERIC) {
                case COOLDOWN -> getCooldownSound(shopId);
                case LIMIT_REACHED -> getLimitReachedSound(shopId);
                case CONDITION_FAIL -> getConditionFailSound(shopId);
                case CANNOT_AFFORD -> getCannotAffordSound(shopId);
                default -> getDrawFailSound(shopId);
            };
            GuiSoundManager.play(sound);
            return;
        }

        if (itemId == null || rarityName == null) {
            ArcQuestLog.warn(ArcQuestLog.Category.GACHA, "Draw success but missing item/rarity info");
            return;
        }

        SoundEvent successSound = getDrawSuccessSound(shopId, itemId, rarityName);
        if (successSound != null) {
            GuiSoundManager.play(successSound);
        }
    }

    @Nullable
    public GachaSessionData getSession(String shopId) {
        return gachaSessions.get(shopId);
    }

    public void closeSession(String shopId) {
        gachaSessions.remove(shopId);
    }

    public void clear() {
        gachaSessions.clear();
    }

    // ════════════════════════════════════════
    // 状态查询（Authority）
    // ════════════════════════════════════════

    public boolean isOnCooldown(String shopId) {
        GachaSessionData data = gachaSessions.get(shopId);
        if (data == null) return false;
        if (data.authority.cooldownType == 0) return false;

        return ClientCooldownHelper.isOnCooldown(
                data.authority.lastDrawRealTime,
                data.authority.lastDrawGameTime,
                data.authority.lastDrawDayTime,
                data.authority.cooldownType,
                data.authority.cooldownValue,
                data.authority.resetTimeTicks
        );
    }

    public String getCooldownText(String shopId) {
        GachaSessionData data = gachaSessions.get(shopId);
        if (data == null || data.authority.cooldownType == 0) return "";

        return ClientCooldownHelper.getCooldownText(
                data.authority.lastDrawRealTime,
                data.authority.lastDrawGameTime,
                data.authority.lastDrawDayTime,
                data.authority.cooldownType,
                data.authority.cooldownValue,
                data.authority.resetTimeTicks
        );
    }

    public int getPityProgress(String shopId) {
        var s = gachaSessions.get(shopId);
        return s != null ? s.authority.pityCounter : -1;
    }

    public int getTotalDraws(String shopId) {
        var s = gachaSessions.get(shopId);
        return s != null ? s.authority.totalDraws : 0;
    }

    public boolean canDraw(String shopId) {
        var s = gachaSessions.get(shopId);
        return s != null && s.authority.canDraw;
    }

    public int getRemainingDraws(String shopId) {
        var s = gachaSessions.get(shopId);
        return s != null ? s.authority.remainingDraws : -1;
    }

    // ════════════════════════════════════════
    // 反馈查询（Feedback）
    // ════════════════════════════════════════

    @Nullable
    public String getLastFailReason(String shopId) {
        var s = gachaSessions.get(shopId);
        return s != null ? s.feedback.lastFailReason : null;
    }

    public List<CostShortfallLine> getLastShortfall(String shopId) {
        var s = gachaSessions.get(shopId);
        return s != null ? List.copyOf(s.feedback.lastShortfallLines) : List.of();
    }

    public void clearFeedback(String shopId) {
        var s = gachaSessions.get(shopId);
        if (s == null) {
            return;
        }
        s.feedback.lastFailReason = null;
        s.feedback.lastShortfallLines = List.of();
    }

    // ════════════════════════════════════════
    // 历史/统计（History）
    // ════════════════════════════════════════

    public int getPityRemaining(String shopId, int pityThreshold) {
        int current = getPityProgress(shopId);
        if (current < 0) return -1;
        return Math.max(0, pityThreshold - current);
    }

    public int getPityProgressPercent(String shopId, int pityThreshold) {
        if (pityThreshold <= 0) return 0;
        int current = getPityProgress(shopId);
        if (current < 0) return 0;
        return Math.min(100, (current * 100) / pityThreshold);
    }

    public boolean isPityTriggered(String shopId, int pityThreshold) {
        return getPityProgress(shopId) >= pityThreshold;
    }

    @Nullable
    public DrawRecord getLastDrawResult(String shopId) {
        var s = gachaSessions.get(shopId);
        if (s == null || s.history.drawHistory.isEmpty()) return null;
        return s.history.drawHistory.get(s.history.drawHistory.size() - 1);
    }

    public List<DrawRecord> getDrawHistory(String shopId) {
        var s = gachaSessions.get(shopId);
        return s != null ? Collections.unmodifiableList(s.history.drawHistory) : Collections.emptyList();
    }

    public int getRarityDrawCount(String shopId, String rarityName) {
        var s = gachaSessions.get(shopId);
        if (s == null) return 0;
        return (int) s.history.drawHistory.stream().filter(r -> rarityName.equals(r.rarityName())).count();
    }

    public double getAverageItemCount(String shopId) {
        var s = gachaSessions.get(shopId);
        if (s == null || s.history.drawHistory.isEmpty()) return 0.0;
        return s.history.drawHistory.stream().mapToInt(DrawRecord::actualCount).average().orElse(0.0);
    }

    public int getPityTriggerCount(String shopId) {
        var s = gachaSessions.get(shopId);
        if (s == null) return 0;
        return (int) s.history.drawHistory.stream().filter(DrawRecord::pityTriggered).count();
    }

    public int getRemainingDraws(String shopId, int maxDraws) {
        if (maxDraws < 0) return -1;
        var s = gachaSessions.get(shopId);
        if (s == null) return -2;
        return Math.max(0, maxDraws - s.authority.totalDraws);
    }

    public boolean isDrawLimitReached(String shopId, int maxDraws) {
        if (maxDraws < 0) return false;
        var s = gachaSessions.get(shopId);
        return s != null && s.authority.totalDraws >= maxDraws;
    }

    public int getDrawProgressPercent(String shopId, int maxDraws) {
        if (maxDraws <= 0) return 0;
        var s = gachaSessions.get(shopId);
        if (s == null) return 0;
        return Math.min(100, (s.authority.totalDraws * 100) / maxDraws);
    }

    public Map<String, Integer> getRarityDistribution(String shopId) {
        var s = gachaSessions.get(shopId);
        if (s == null || s.history.drawHistory.isEmpty()) return Collections.emptyMap();

        Map<String, Integer> m = new HashMap<>();
        for (DrawRecord r : s.history.drawHistory) {
            m.merge(r.rarityName(), 1, Integer::sum);
        }
        return Collections.unmodifiableMap(m);
    }

    public int getEstimatedDrawsToPity(String shopId, int pityThreshold) {
        return getPityRemaining(shopId, pityThreshold);
    }

    // ════════════════════════════════════════
    // 音效辅助
    // ════════════════════════════════════════

    @Nullable
    private SoundEvent getDrawSuccessSound(String shopId, String itemId, String rarityName) {
        var gachaShop = GachaRegistry.get(shopId);
        if (gachaShop == null) return null;

        var pool = gachaShop.getGachaPool();
        for (var item : pool.getItems()) {
            if (item.getItemId().equals(itemId)) {
                return gachaShop.getEffectiveDrawSuccessSound(item);
            }
        }

        var rarityConfig = gachaShop.getRarityConfig(rarityName);
        return rarityConfig != null ? rarityConfig.getDrawSuccessSound() : null;
    }

    @Nullable
    private SoundEvent getCooldownSound(String shopId) {
        var gachaShop = GachaRegistry.get(shopId);
        return gachaShop != null ? gachaShop.getDrawCooldownSound() : null;
    }

    @Nullable
    private SoundEvent getLimitReachedSound(String shopId) {
        var gachaShop = GachaRegistry.get(shopId);
        return gachaShop != null ? gachaShop.getDrawLimitReachedSound() : null;
    }

    @Nullable
    private SoundEvent getConditionFailSound(String shopId) {
        var gachaShop = GachaRegistry.get(shopId);
        return gachaShop != null ? gachaShop.getDrawConditionFailSound() : null;
    }

    @Nullable
    private SoundEvent getCannotAffordSound(String shopId) {
        return getConditionFailSound(shopId);
    }

    @Nullable
    private SoundEvent getDrawFailSound(String shopId) {
        var gachaShop = GachaRegistry.get(shopId);
        return gachaShop != null ? gachaShop.getDrawFailSound() : null;
    }

    // ════════════════════════════════════════
    // 数据结构
    // ════════════════════════════════════════

    public enum FailReason {
        COOLDOWN,
        LIMIT_REACHED,
        CONDITION_FAIL,
        CANNOT_AFFORD,
        GENERIC
    }

    public static class GachaSessionData {
        final AuthorityState authority;
        final FeedbackState feedback;
        final HistoryState history;

        public GachaSessionData(int pityCounter, int totalDraws) {
            this(pityCounter, totalDraws, true, -1, 0, 0, 0, 0, 0, 0);
        }

        public GachaSessionData(int pityCounter, int totalDraws, boolean canDraw, int remainingDraws,
                                long lastDrawRealTime, long lastDrawGameTime, long lastDrawDayTime,
                                int cooldownType, long cooldownValue, int resetTimeTicks) {
            authority = new AuthorityState(
                    pityCounter, totalDraws, canDraw, remainingDraws,
                    lastDrawRealTime, lastDrawGameTime, lastDrawDayTime,
                    cooldownType, cooldownValue, resetTimeTicks
            );
            feedback = new FeedbackState();
            history = new HistoryState();
        }

        // 兼容旧 API
        public int getPityCounter() {
            return authority.pityCounter;
        }

        public int getTotalDraws() {
            return authority.totalDraws;
        }

        public boolean canDraw() {
            return authority.canDraw;
        }

        public int getRemainingDraws() {
            return authority.remainingDraws;
        }

        public long getLastDrawRealTime() {
            return authority.lastDrawRealTime;
        }

        public long getLastDrawGameTime() {
            return authority.lastDrawGameTime;
        }

        public long getLastDrawDayTime() {
            return authority.lastDrawDayTime;
        }

        public int getCooldownType() {
            return authority.cooldownType;
        }

        public long getCooldownValue() {
            return authority.cooldownValue;
        }

        public int getResetTimeTicks() {
            return authority.resetTimeTicks;
        }

        @Nullable
        public String getLastDrawnItemId() {
            return history.lastDrawnItemId;
        }

        @Nullable
        public String getLastRarityName() {
            return history.lastRarityName;
        }

        public int getLastActualCount() {
            return history.lastActualCount;
        }

        public boolean isLastPityTriggered() {
            return history.lastPityTriggered;
        }

        public long getLastDrawTime() {
            return history.lastDrawTime;
        }

        @Nullable
        public String getLastFailReason() {
            return feedback.lastFailReason;
        }

        public List<CostShortfallLine> getLastShortfallLines() {
            return List.copyOf(feedback.lastShortfallLines);
        }

        public List<DrawRecord> getDrawHistory() {
            return Collections.unmodifiableList(history.drawHistory);
        }

        // 新分层快照 API
        public AuthoritySnapshot authority() {
            return new AuthoritySnapshot(
                    authority.pityCounter,
                    authority.totalDraws,
                    authority.canDraw,
                    authority.remainingDraws,
                    authority.lastDrawRealTime,
                    authority.lastDrawGameTime,
                    authority.lastDrawDayTime,
                    authority.cooldownType,
                    authority.cooldownValue,
                    authority.resetTimeTicks
            );
        }

        public FeedbackSnapshot feedback() {
            return new FeedbackSnapshot(feedback.lastFailReason, List.copyOf(feedback.lastShortfallLines));
        }
    }

    static final class AuthorityState {
        int pityCounter;
        int totalDraws;
        boolean canDraw;
        int remainingDraws;

        long lastDrawRealTime;
        long lastDrawGameTime;
        long lastDrawDayTime;
        int cooldownType;
        long cooldownValue;
        int resetTimeTicks;

        AuthorityState(int pityCounter, int totalDraws, boolean canDraw, int remainingDraws,
                       long lastDrawRealTime, long lastDrawGameTime, long lastDrawDayTime,
                       int cooldownType, long cooldownValue, int resetTimeTicks) {
            this.pityCounter = pityCounter;
            this.totalDraws = totalDraws;
            this.canDraw = canDraw;
            this.remainingDraws = remainingDraws;
            this.lastDrawRealTime = lastDrawRealTime;
            this.lastDrawGameTime = lastDrawGameTime;
            this.lastDrawDayTime = lastDrawDayTime;
            this.cooldownType = cooldownType;
            this.cooldownValue = cooldownValue;
            this.resetTimeTicks = resetTimeTicks;
        }
    }

    static final class FeedbackState {
        @Nullable
        String lastFailReason;
        List<CostShortfallLine> lastShortfallLines = List.of();
    }

    static final class HistoryState {
        final ArrayList<DrawRecord> drawHistory = new ArrayList<>();
        @Nullable
        String lastDrawnItemId;
        @Nullable
        String lastRarityName;
        int lastActualCount;
        boolean lastPityTriggered;
        long lastDrawTime;
    }

    public record AuthoritySnapshot(
            int pityCounter,
            int totalDraws,
            boolean canDraw,
            int remainingDraws,
            long lastDrawRealTime,
            long lastDrawGameTime,
            long lastDrawDayTime,
            int cooldownType,
            long cooldownValue,
            int resetTimeTicks
    ) {
    }

    public record FeedbackSnapshot(@Nullable String failReason, List<CostShortfallLine> shortfallLines) {
    }

    public record DrawRecord(
            String itemId,
            String rarityName,
            int actualCount,
            boolean pityTriggered,
            long drawTime
    ) {
    }
}