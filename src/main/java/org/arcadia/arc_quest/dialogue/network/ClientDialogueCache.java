package org.arcadia.arc_quest.dialogue.network;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import org.arcadia.arc_quest.client.util.ClientCooldownHelper;
import org.arcadia.arc_quest.client.util.GuiSoundManager;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 客户端对话状态缓存（单例）。
 * <p>
 * <b>线程模型</b>：仅在客户端主线程（Render Thread）访问，由 S2C 网络包更新。
 * 所有更新通过 {@code ctx.get().enqueueWork()} 确保在主线程执行，因此无需同步保护。
 * <p>
 * 负责统一管理对话系统的运行时状态，实现 UI 与逻辑解耦，
 * 并处理服务端权威的音效触发。
 */
public final class ClientDialogueCache {
    public static final ClientDialogueCache INSTANCE = new ClientDialogueCache();
    private final Map<UUID, List<TranscriptEntry>> transcripts = new HashMap<>();
    /**
     * 当前活跃的对话会话映射 (treeId -> SessionData)
     * 支持嵌套场景：对话中打开商店再返回对话时保持状态
     */
    private final Map<UUID, DialogueSessionData> activeSessions = new HashMap<>();
    private final Map<String, UUID> latestSessionByTree = new HashMap<>();
    @Nullable
    private UUID currentSessionId = null;
    /**
     * 当前正在显示的对话树 ID（用于 getCurrentSession()）
     */
    @Nullable
    private String currentTreeId = null;

    private ClientDialogueCache() {
    }

    /**
     * 从网络包更新会话状态。
     */
    public void updateFromPacket(String treeId, String nodeId, Component speaker, Component text, Component[] choices,
                                 boolean isTerminal, boolean hasAutoNext, int delayMs, int entityId,
                                 long[] lastSelectTimes, long[] purchaseGTs, long[] purchaseDTs,
                                 int[] cooldownTypes, long[] cooldownValues, int[] resetTimeTicks,
                                 @Nullable SoundEvent matchedSaySound, @Nullable SoundEvent[] choiceSounds,
                                 @Nullable String matchedSayId, @Nullable String[] choiceIds) {
        UUID legacySessionId = currentSessionId != null && Objects.equals(currentTreeId, treeId)
                ? currentSessionId
                : UUID.randomUUID();
        long legacyRevision = Optional.ofNullable(activeSessions.get(legacySessionId))
                .map(session -> session.revision + 1L)
                .orElse(1L);
        updateFromPacket(legacySessionId, legacyRevision, 0L, true,
                treeId, nodeId, speaker, text, choices, isTerminal, hasAutoNext, delayMs, entityId,
                lastSelectTimes, purchaseGTs, purchaseDTs, cooldownTypes, cooldownValues, resetTimeTicks,
                matchedSaySound, choiceSounds, matchedSayId, choiceIds);
    }

    public boolean updateFromPacket(UUID sessionId, long revision, long playerSessionEpoch, boolean openMode,
                                    String treeId, String nodeId, Component speaker, Component text, Component[] choices,
                                    boolean isTerminal, boolean hasAutoNext, int delayMs, int entityId,
                                    long[] lastSelectTimes, long[] purchaseGTs, long[] purchaseDTs,
                                    int[] cooldownTypes, long[] cooldownValues, int[] resetTimeTicks,
                                    @Nullable SoundEvent matchedSaySound, @Nullable SoundEvent[] choiceSounds,
                                    @Nullable String matchedSayId, @Nullable String[] choiceIds) {
        UUID effectiveSessionId = sessionId != null && !S2COpenDialoguePacket.LEGACY_SESSION_ID.equals(sessionId)
                ? sessionId
                : (currentSessionId != null && Objects.equals(currentTreeId, treeId)
                ? currentSessionId : UUID.randomUUID());

        DialogueSessionData current = getCurrentSession();
        if (!openMode && current != null && !current.sessionId.equals(effectiveSessionId)) {
            ArcQuestLog.warn(ArcQuestLog.Category.DIALOGUE_NETWORK, "Ignored update for stale session. incoming={}, current={}",
                    effectiveSessionId, current.sessionId);
            return false;
        }
        if (!openMode && current == null) {
            ArcQuestLog.debug(ArcQuestLog.Category.DIALOGUE_NETWORK, "Ignored update without active session. incoming={}", effectiveSessionId);
            return false;
        }
        if (openMode && current != null && !current.sessionId.equals(effectiveSessionId)) {
            activeSessions.remove(current.sessionId);
            latestSessionByTree.remove(current.treeId, current.sessionId);
        }

        DialogueSessionData session = activeSessions.computeIfAbsent(effectiveSessionId,
                id -> new DialogueSessionData(id, treeId));
        if (session.playerSessionEpoch != 0L && playerSessionEpoch != 0L
                && session.playerSessionEpoch != playerSessionEpoch) {
            ArcQuestLog.warn(ArcQuestLog.Category.DIALOGUE_NETWORK, "Ignored packet from stale player epoch. session={}, incomingEpoch={}, currentEpoch={}",
                    effectiveSessionId, playerSessionEpoch, session.playerSessionEpoch);
            return false;
        }
        if (revision < session.revision) {
            ArcQuestLog.debug(ArcQuestLog.Category.DIALOGUE_NETWORK, "Ignored stale revision. session={}, incoming={}, current={}",
                    effectiveSessionId, revision, session.revision);
            return false;
        }

        currentSessionId = effectiveSessionId;
        currentTreeId = treeId;
        latestSessionByTree.put(treeId, effectiveSessionId);
        session.updateNode(revision, playerSessionEpoch, nodeId, speaker, text, choices,
                isTerminal, hasAutoNext, delayMs, entityId,
                lastSelectTimes, purchaseGTs, purchaseDTs, cooldownTypes, cooldownValues, resetTimeTicks,
                choiceSounds, matchedSayId, choiceIds);

        if (matchedSaySound != null) {
            GuiSoundManager.play(matchedSaySound);
            ArcQuestLog.debug(ArcQuestLog.Category.DIALOGUE_NETWORK, "Played SayIf selectSound for node: {}, sayId: {}", nodeId, matchedSayId);
        }
        return true;
    }

    /**
     * 播放选项选择音效（由 UI 点击事件调用）。
     *
     * @param treeId 对话树 ID
     * @param index  选项在列表中的索引
     */
    public void playChoiceSound(String treeId, int index) {
        DialogueSessionData session = getSession(treeId);
        if (session == null) {
            ArcQuestLog.warn(ArcQuestLog.Category.DIALOGUE_NETWORK, "Cannot play choice selectSound: no session for treeId={}", treeId);
            return;
        }

        if (index < 0 || index >= session.choiceSounds.length) {
            ArcQuestLog.warn(ArcQuestLog.Category.DIALOGUE_NETWORK, "Invalid choice index: {} (max: {})", index, session.choiceSounds.length - 1);
            return;
        }

        SoundEvent sound = session.choiceSounds[index];
        if (sound != null) {
            GuiSoundManager.play(sound);
            ArcQuestLog.debug(ArcQuestLog.Category.DIALOGUE_NETWORK, "Played choice selectSound for index: {}", index);
        } else {
            ArcQuestLog.debug(ArcQuestLog.Category.DIALOGUE_NETWORK, "No selectSound configured for choice index: {}", index);
        }
    }

    /**
     * 关闭指定对话树的会话。
     *
     * @param treeId 对话树 ID
     */
    public void closeSession(String treeId) {
        UUID sessionId = latestSessionByTree.remove(treeId);
        DialogueSessionData removed = sessionId != null ? activeSessions.remove(sessionId) : null;
        if (removed != null) {
            ArcQuestLog.debug(ArcQuestLog.Category.DIALOGUE_NETWORK, "Session closed for treeId: {}", treeId);
            // 如果关闭的是当前会话，清除 currentTreeId
            if (treeId.equals(currentTreeId)) {
                currentTreeId = null;
                currentSessionId = null;
            }
        }
    }

    /**
     * 关闭当前最后一个活跃的会话（向后兼容）。
     */
    public void closeSession() {
        if (currentSessionId != null) {
            closeSession(currentSessionId, 0L);
        } else if (!activeSessions.isEmpty()) {
            // 兜底：如果没有 currentTreeId，记录警告并清除所有会话
            ArcQuestLog.warn(ArcQuestLog.Category.DIALOGUE_NETWORK, "closeSession() called without currentTreeId, clearing all sessions");
            activeSessions.clear();
            latestSessionByTree.clear();
            currentSessionId = null;
            currentTreeId = null;
        }
    }

    public boolean closeSession(UUID sessionId, long playerSessionEpoch) {
        if (sessionId == null || S2COpenDialoguePacket.LEGACY_SESSION_ID.equals(sessionId)) {
            closeSession();
            return true;
        }
        DialogueSessionData session = activeSessions.get(sessionId);
        if (session == null) return false;
        if (playerSessionEpoch != 0L && session.playerSessionEpoch != 0L
                && playerSessionEpoch != session.playerSessionEpoch) {
            return false;
        }
        activeSessions.remove(sessionId);
        latestSessionByTree.remove(session.treeId, sessionId);
        if (sessionId.equals(currentSessionId)) {
            currentSessionId = null;
            currentTreeId = null;
        }
        return true;
    }

    /**
     * 获取指定对话树的活跃会话数据。
     *
     * @param treeId 对话树 ID
     * @return 会话数据，如果不存在则返回 null
     */
    @Nullable
    public DialogueSessionData getSession(String treeId) {
        UUID sessionId = latestSessionByTree.get(treeId);
        return sessionId != null ? activeSessions.get(sessionId) : null;
    }

    /**
     * 获取当前正在显示的对话会话数据。
     *
     * @return 会话数据，如果不存在则返回 null
     */
    @Nullable
    public DialogueSessionData getCurrentSession() {
        if (currentSessionId == null) {
            return null;
        }
        return activeSessions.get(currentSessionId);
    }

    @Nullable
    public UUID getCurrentSessionId() {
        return currentSessionId;
    }

    public List<TranscriptEntry> getCurrentTranscript() {
        if (currentSessionId == null) return List.of();
        return transcripts.getOrDefault(currentSessionId, List.of());
    }

    public C2SDialogueChoicePacket createChoicePacket(int choiceIndex) {
        DialogueSessionData session = getCurrentSession();
        if (session == null) return new C2SDialogueChoicePacket(choiceIndex);
        return C2SDialogueChoicePacket.choice(choiceIndex, session.sessionId, session.revision,
                session.nodeId, session.getChoiceId(choiceIndex), session.playerSessionEpoch);
    }

    public C2SDialogueChoicePacket createAutoAdvancePacket() {
        DialogueSessionData session = getCurrentSession();
        if (session == null) return C2SDialogueChoicePacket.autoAdvance();
        return C2SDialogueChoicePacket.autoAdvance(session.sessionId, session.revision,
                session.nodeId, session.playerSessionEpoch);
    }

    public C2SDialogueChoicePacket createClosePacket() {
        DialogueSessionData session = getCurrentSession();
        if (session == null) return C2SDialogueChoicePacket.close();
        return C2SDialogueChoicePacket.close(session.sessionId, session.revision, session.playerSessionEpoch);
    }

    public C2SDialogueChoicePacket createRestorePacket() {
        DialogueSessionData session = getCurrentSession();
        if (session == null) return C2SDialogueChoicePacket.restore();
        return C2SDialogueChoicePacket.restore(session.sessionId, session.revision,
                session.nodeId, session.playerSessionEpoch);
    }

    public void replaceTranscriptSnapshot(UUID sessionId, List<S2CDialogueTranscriptDeltaPacket.Entry> entries) {
        currentSessionId = sessionId;
        List<TranscriptEntry> mapped = new ArrayList<>(entries.size());
        for (S2CDialogueTranscriptDeltaPacket.Entry e : entries) {
            mapped.add(new TranscriptEntry(
                    e.clientMs(), e.role(), e.speaker(), e.text(),
                    e.nodeId(), e.sayId(), e.choiceId(),
                    e.choiceIndexOrNeg1() >= 0 ? e.choiceIndexOrNeg1() : null
            ));
        }
        transcripts.put(sessionId, mapped);
    }

    public void appendTranscriptEntry(UUID sessionId, long clientMs, String role, Component speaker, Component text,
                                      @Nullable String nodeId, @Nullable String sayId,
                                      @Nullable String choiceId, @Nullable Integer choiceIndex) {
        transcripts.computeIfAbsent(sessionId, k -> new ArrayList<>())
                .add(new TranscriptEntry(clientMs, role, speaker, text, nodeId, sayId, choiceId, choiceIndex));
    }

    /**
     * 清理所有会话数据（用于模组卸载或世界切换）。
     */
    public void clear() {
        int count = activeSessions.size();
        activeSessions.clear();
        latestSessionByTree.clear();
        transcripts.clear();
        currentSessionId = null;
        currentTreeId = null;
        ArcQuestLog.debug(ArcQuestLog.Category.DIALOGUE_NETWORK, "Cleared {} session(s)", count);
    }

    public record TranscriptEntry(long clientMs, String role, Component speaker, Component text,
                                  @Nullable String nodeId, @Nullable String sayId,
                                  @Nullable String choiceId, @Nullable Integer choiceIndex) {
    }

    /**
     * 内部数据容器。
     */
    public static class DialogueSessionData {
        public final UUID sessionId;
        public final String treeId;
        public long revision;
        public long playerSessionEpoch;
        public String nodeId;
        public Component speaker;
        public Component text;
        public Component[] choices;
        public boolean isTerminal;
        public boolean hasAutoNext;
        public int delayMs;
        public int entityId;

        // 冷却原始数据
        public long[] lastSelectTimes;
        public long[] purchaseGameTimes;
        public long[] purchaseDayTimes;
        public int[] cooldownTypes;
        public long[] cooldownValues;
        public int[] resetTimeTicks;
        public SoundEvent[] choiceSounds;

        // Say/Choice 标识。
        public String matchedSayId;
        public String[] choiceIds;

        public DialogueSessionData(String treeId) {
            this(UUID.randomUUID(), treeId);
        }

        public DialogueSessionData(UUID sessionId, String treeId) {
            this.sessionId = sessionId;
            this.treeId = treeId;
        }

        public void updateNode(String nodeId, Component speaker, Component text, Component[] choices,
                               boolean isTerminal, boolean hasAutoNext, int delayMs, int entityId,
                               long[] lastSelectTimes, long[] purchaseGTs, long[] purchaseDTs,
                               int[] cooldownTypes, long[] cooldownValues, int[] resetTimeTicks,
                               SoundEvent[] choiceSounds, String matchedSayId, String[] choiceIds) {
            updateNode(revision + 1L, playerSessionEpoch, nodeId, speaker, text, choices,
                    isTerminal, hasAutoNext, delayMs, entityId, lastSelectTimes, purchaseGTs, purchaseDTs,
                    cooldownTypes, cooldownValues, resetTimeTicks, choiceSounds, matchedSayId, choiceIds);
        }

        public void updateNode(long revision, long playerSessionEpoch,
                               String nodeId, Component speaker, Component text, Component[] choices,
                               boolean isTerminal, boolean hasAutoNext, int delayMs, int entityId,
                               long[] lastSelectTimes, long[] purchaseGTs, long[] purchaseDTs,
                               int[] cooldownTypes, long[] cooldownValues, int[] resetTimeTicks,
                               SoundEvent[] choiceSounds, String matchedSayId, String[] choiceIds) {
            this.revision = revision;
            this.playerSessionEpoch = playerSessionEpoch;
            this.nodeId = nodeId;
            this.speaker = speaker;
            this.text = text;
            this.choices = choices != null ? choices : new Component[0];
            this.isTerminal = isTerminal;
            this.hasAutoNext = hasAutoNext;
            this.delayMs = delayMs;
            this.entityId = entityId;

            this.lastSelectTimes = (lastSelectTimes != null) ? lastSelectTimes.clone() : new long[this.choices.length];
            purchaseGameTimes = (purchaseGTs != null) ? purchaseGTs.clone() : new long[this.choices.length];
            purchaseDayTimes = (purchaseDTs != null) ? purchaseDTs.clone() : new long[this.choices.length];
            this.cooldownTypes = (cooldownTypes != null) ? cooldownTypes.clone() : new int[this.choices.length];
            this.cooldownValues = (cooldownValues != null) ? cooldownValues.clone() : new long[this.choices.length];
            this.resetTimeTicks = (resetTimeTicks != null) ? resetTimeTicks.clone() : new int[this.choices.length];
            this.choiceSounds = (choiceSounds != null) ? choiceSounds.clone() : new SoundEvent[this.choices.length];

            // 存储 ID
            this.matchedSayId = matchedSayId;
            this.choiceIds = (choiceIds != null) ? choiceIds.clone() : new String[this.choices.length];
        }

        /**
         * 判断指定索引的选项是否处于冷却中。
         */
        public boolean isChoiceOnCooldown(int index) {
            if (index < 0 || index >= choices.length) return false;
            return ClientCooldownHelper.isOnCooldown(
                    lastSelectTimes[index], purchaseGameTimes[index], purchaseDayTimes[index],
                    cooldownTypes[index], cooldownValues[index], resetTimeTicks[index]);
        }

        /**
         * 获取选项的冷却倒计时文本。
         */
        public String getChoiceCooldownText(int index) {
            if (index < 0 || index >= choices.length) return "";
            return ClientCooldownHelper.getCooldownText(
                    lastSelectTimes[index], purchaseGameTimes[index], purchaseDayTimes[index],
                    cooldownTypes[index], cooldownValues[index], resetTimeTicks[index]);
        }

        /**
         * 获取选项总数。
         */
        public int getChoiceCount() {
            return choices.length;
        }

        /**
         * 获取当前匹配的 SayIf ID。
         */
        @Nullable
        public String getMatchedSayId() {
            return matchedSayId;
        }

        /**
         * 获取指定索引的 Choice ID。
         */
        @Nullable
        public String getChoiceId(int index) {
            if (index < 0 || index >= choiceIds.length) return null;
            return choiceIds[index];
        }

        /**
         * 判断指定索引的选项是否有配置音效。
         */
        public boolean hasChoiceSound(int index) {
            return index >= 0 && index < choiceSounds.length && choiceSounds[index] != null;
        }
    }
}
