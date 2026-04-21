package org.com.arc_quest.dialogue.network;

import com.mojang.logging.LogUtils;
import net.minecraft.sounds.SoundEvent;
import org.com.arc_quest.client.util.GuiSoundManager;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * 客户端对话状态缓存（单例）。
 * <p>
 * 负责统一管理对话系统的运行时状态，实现 UI 与逻辑解耦，
 * 并处理服务端权威的音效触发。
 */
public final class ClientDialogueCache {
    public static final ClientDialogueCache INSTANCE = new ClientDialogueCache();
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 当前活跃的对话会话映射 (treeId -> SessionData)
     * 支持嵌套场景：对话中打开商店再返回对话时保持状态
     */
    private final Map<String, DialogueSessionData> activeSessions = new HashMap<>();
    
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
    public void updateFromPacket(String treeId, String nodeId, String speaker, String text, String[] choices,
                                 boolean isTerminal, boolean hasAutoNext, int delayMs, int entityId,
                                 long[] lastSelectTimes, long[] purchaseGTs, long[] purchaseDTs,
                                 int[] cooldownTypes, long[] cooldownValues, int[] resetTimeTicks,
                                 @Nullable SoundEvent matchedSaySound, @Nullable SoundEvent[] choiceSounds,
                                 @Nullable String matchedSayId, @Nullable String[] choiceIds) {
        
        // 更新当前活跃的对话树 ID
        this.currentTreeId = treeId;
        
        DialogueSessionData session = activeSessions.computeIfAbsent(treeId, DialogueSessionData::new);
        session.updateNode(nodeId, speaker, text, choices, isTerminal, hasAutoNext, delayMs, entityId,
                lastSelectTimes, purchaseGTs, purchaseDTs, cooldownTypes, cooldownValues, resetTimeTicks,
                choiceSounds, matchedSayId, choiceIds);

        // 播放 SayIf 匹配的个体化音效
        if (matchedSaySound != null) {
            GuiSoundManager.play(matchedSaySound);
            LOGGER.debug("[DialogueCache] Played SayIf selectSound for node: {}, sayId: {}", nodeId, matchedSayId);
        }
    }

    /**
     * 播放选项选择音效（由 UI 点击事件调用）。
     *
     * @param treeId 对话树 ID
     * @param index  选项在列表中的索引
     */
    public void playChoiceSound(String treeId, int index) {
        DialogueSessionData session = activeSessions.get(treeId);
        if (session == null) {
            LOGGER.warn("[DialogueCache] Cannot play choice selectSound: no session for treeId={}", treeId);
            return;
        }
        
        if (index < 0 || index >= session.choiceSounds.length) {
            LOGGER.warn("[DialogueCache] Invalid choice index: {} (max: {})", index, session.choiceSounds.length - 1);
            return;
        }
        
        SoundEvent sound = session.choiceSounds[index];
        if (sound != null) {
            GuiSoundManager.play(sound);
            LOGGER.debug("[DialogueCache] Played choice selectSound for index: {}", index);
        } else {
            LOGGER.debug("[DialogueCache] No selectSound configured for choice index: {}", index);
        }
    }

    /**
     * 关闭指定对话树的会话。
     *
     * @param treeId 对话树 ID
     */
    public void closeSession(String treeId) {
        DialogueSessionData removed = activeSessions.remove(treeId);
        if (removed != null) {
            LOGGER.debug("[DialogueCache] Session closed for treeId: {}", treeId);
            // 如果关闭的是当前会话，清除 currentTreeId
            if (treeId.equals(currentTreeId)) {
                currentTreeId = null;
            }
        }
    }

    /**
     * 关闭当前最后一个活跃的会话（向后兼容）。
     */
    public void closeSession() {
        if (currentTreeId != null) {
            closeSession(currentTreeId);
        } else if (!activeSessions.isEmpty()) {
            // 兜底：如果没有 currentTreeId，关闭第一个会话
            String firstTreeId = activeSessions.keySet().iterator().next();
            closeSession(firstTreeId);
        }
    }

    /**
     * 获取指定对话树的活跃会话数据。
     *
     * @param treeId 对话树 ID
     * @return 会话数据，如果不存在则返回 null
     */
    @Nullable
    public DialogueSessionData getSession(String treeId) {
        return activeSessions.get(treeId);
    }

    /**
     * 获取当前正在显示的对话会话数据。
     *
     * @return 会话数据，如果不存在则返回 null
     */
    @Nullable
    public DialogueSessionData getCurrentSession() {
        if (currentTreeId == null) {
            return null;
        }
        return activeSessions.get(currentTreeId);
    }

    /**
     * 清理所有会话数据（用于模组卸载或世界切换）。
     */
    public void clear() {
        int count = activeSessions.size();
        activeSessions.clear();
        LOGGER.debug("[DialogueCache] Cleared {} session(s)", count);
    }

    /**
     * 内部数据容器。
     */
    public static class DialogueSessionData {
        public final String treeId;
        public String nodeId;
        public String speaker;
        public String text;
        public String[] choices;
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
        
        // Say/Choice IDs
        public String matchedSayId;
        public String[] choiceIds;

        public DialogueSessionData(String treeId) {
            this.treeId = treeId;
        }

        public void updateNode(String nodeId, String speaker, String text, String[] choices,
                               boolean isTerminal, boolean hasAutoNext, int delayMs, int entityId,
                               long[] lastSelectTimes, long[] purchaseGTs, long[] purchaseDTs,
                               int[] cooldownTypes, long[] cooldownValues, int[] resetTimeTicks,
                               SoundEvent[] choiceSounds, String matchedSayId, String[] choiceIds) {
            this.nodeId = nodeId;
            this.speaker = speaker;
            this.text = text;
            this.choices = choices != null ? choices : new String[0];
            this.isTerminal = isTerminal;
            this.hasAutoNext = hasAutoNext;
            this.delayMs = delayMs;
            this.entityId = entityId;

            this.lastSelectTimes = (lastSelectTimes != null) ? lastSelectTimes.clone() : new long[this.choices.length];
            this.purchaseGameTimes = (purchaseGTs != null) ? purchaseGTs.clone() : new long[this.choices.length];
            this.purchaseDayTimes = (purchaseDTs != null) ? purchaseDTs.clone() : new long[this.choices.length];
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
            return org.com.arc_quest.client.util.ClientCooldownHelper.isOnCooldown(
                    lastSelectTimes[index], purchaseGameTimes[index], purchaseDayTimes[index],
                    cooldownTypes[index], cooldownValues[index], resetTimeTicks[index]);
        }

        /**
         * 获取选项的冷却倒计时文本。
         */
        public String getChoiceCooldownText(int index) {
            if (index < 0 || index >= choices.length) return "";
            return org.com.arc_quest.client.util.ClientCooldownHelper.getCooldownText(
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
