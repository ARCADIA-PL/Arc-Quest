package org.arcadia.arc_quest.quest.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.arcadia.arc_quest.dialogue.runtime.DialogueProgressStore;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerState;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;

/**
 * IQuestCapability 的标准实现。
 * <p>
 * v2: 对话历史从 6 个 Map 合并为 {@link DialogueProgressStore}。
 * v3: 引入 {@link NbtVersionManager} 统一管理版本号，支持链式迁移。
 * v4: 抽奖数据抽取到 {@link GachaDataStore}。
 */
public class QuestCapabilityImpl implements IQuestCapability {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuestCapabilityImpl.class);

    private static final NbtVersionManager VERSION_MANAGER = new NbtVersionManager(
            "arc_quest:player_data", 4, LOGGER
    );

    static {
        VERSION_MANAGER.addMigration(0, 1, tag -> {
            if (!tag.contains("Flags", Tag.TAG_LIST)) {
                tag.put("Flags", new ListTag());
            }
        });
        VERSION_MANAGER.addMigration(1, 2, tag -> {
            if (tag.contains("NodeVisitHistory", Tag.TAG_COMPOUND)) {
                tag.putInt("_needs_dialogue_migration", 1);
            } else if (!tag.contains("DialogueProgress", Tag.TAG_COMPOUND)) {
                tag.put("DialogueProgress", new CompoundTag());
            }
        });
        VERSION_MANAGER.addMigration(2, 3, tag -> {
            if (tag.contains("_version", Tag.TAG_INT)) {
                int oldVersion = tag.getInt("_version");
                tag.putInt("_ArcQuestVer", Math.max(oldVersion, 3));
                tag.remove("_version");
            } else {
                tag.putInt("_ArcQuestVer", 3);
            }
            tag.remove("_needs_dialogue_migration");
        });
        VERSION_MANAGER.addMigration(3, 4, root -> {
            if (!root.contains("ActiveQuests", Tag.TAG_LIST)) {
                return;
            }

            ListTag activeList = root.getList("ActiveQuests", Tag.TAG_COMPOUND);
            for (int i = 0; i < activeList.size(); i++) {
                CompoundTag q = activeList.getCompound(i);

                boolean hasNewStruct = q.contains("ActivePhases", Tag.TAG_LIST)
                        || q.contains("CompletedPhases", Tag.TAG_LIST)
                        || q.contains("PhaseProgress", Tag.TAG_COMPOUND);

                if (!hasNewStruct) {
                    String legacyPhaseId = q.getString("PhaseId");
                    int[] legacyProgress = q.contains("Progress", Tag.TAG_INT_ARRAY)
                            ? q.getIntArray("Progress")
                            : new int[0];

                    ListTag activePhases = new ListTag();
                    ListTag completedPhases = new ListTag();
                    CompoundTag phaseProgress = new CompoundTag();

                    if (legacyPhaseId != null && !legacyPhaseId.isEmpty()) {
                        activePhases.add(StringTag.valueOf(legacyPhaseId));
                        phaseProgress.putIntArray(legacyPhaseId, legacyProgress);
                    }

                    q.put("ActivePhases", activePhases);
                    q.put("CompletedPhases", completedPhases);
                    q.put("PhaseProgress", phaseProgress);

                    q.remove("PhaseId");
                    q.remove("Progress");
                    continue;
                }

                ListTag activePhases = q.getList("ActivePhases", Tag.TAG_STRING);
                CompoundTag phaseProgress = q.contains("PhaseProgress", Tag.TAG_COMPOUND)
                        ? q.getCompound("PhaseProgress")
                        : new CompoundTag();

                for (int j = 0; j < activePhases.size(); j++) {
                    String pid = activePhases.getString(j);
                    if (pid != null && !pid.isEmpty() && !phaseProgress.contains(pid, Tag.TAG_INT_ARRAY)) {
                        phaseProgress.putIntArray(pid, new int[0]);
                    }
                }

                q.put("PhaseProgress", phaseProgress);
                if (!q.contains("CompletedPhases", Tag.TAG_LIST)) {
                    q.put("CompletedPhases", new ListTag());
                }
            }
        });
    }

    private final Map<String, QuestRuntimeData> activeQuests = new LinkedHashMap<>();
    private final Set<String> completedQuests = new LinkedHashSet<>();
    private final Set<String> failedQuests = new LinkedHashSet<>();
    private final Set<String> flags = new HashSet<>();
    private final Map<String, Integer> variables = new HashMap<>();
    private final Map<String, QuestMarkerData> markers = new LinkedHashMap<>();

    /**
     * 统一对话/冷却进度存储
     */
    private final DialogueProgressStore dialogueProgress = new DialogueProgressStore();

    /**
     * 交易系统数据（购买次数 + 冷却时间戳）
     */
    private final TradeDataStore tradeData = new TradeDataStore();

    /**
     * 抽奖系统数据（次数、保底、历史记录、冷却时间戳）
     */
    private final GachaDataStore gachaData = new GachaDataStore();

    private boolean isDirty = false;

    private static QuestMarkerData.EntityAttachPoint parseAttachPoint(String value) {
        try {
            return QuestMarkerData.EntityAttachPoint.valueOf(value);
        } catch (Exception ignored) {
            return QuestMarkerData.EntityAttachPoint.HEAD;
        }
    }

    @Override
    public DialogueProgressStore getDialogueProgress() {
        return dialogueProgress;
    }

    @Override
    public GachaDataStore getGachaDataStore() {
        return gachaData;
    }

    // ════════════════════════════════════════
    //  抽奖系统 API（委托给 GachaDataStore）
    // ════════════════════════════════════════

    @Override
    public TradeDataStore getTradeDataStore() {
        return tradeData;
    }

    @Override
    public synchronized int getGachaDrawCount(String shopId) {
        return gachaData.getDrawCount(shopId);
    }

    @Override
    public synchronized void incrementGachaDrawCount(String shopId) {
        gachaData.incrementDrawCount(shopId);
        isDirty = true;
    }

    @Override
    public synchronized void resetGachaDrawCount(String shopId) {
        gachaData.resetDrawCount(shopId);
        isDirty = true;
    }

    @Override
    public synchronized int getGachaPityCounter(String shopId) {
        return gachaData.getPityCounter(shopId);
    }

    @Override
    public synchronized void setGachaPityCounter(String shopId, int count) {
        gachaData.setPityCounter(shopId, count);
        isDirty = true;
    }

    @Override
    public synchronized void addGachaDrawHistory(String shopId, String itemId, String rarityName,
                                                 int actualCount, boolean pityTriggered, long drawTime) {
        gachaData.addDrawHistory(shopId,
                new IQuestCapability.GachaDrawRecord(itemId, rarityName, actualCount, pityTriggered, drawTime));
        isDirty = true;
    }

    @Override
    public synchronized List<IQuestCapability.GachaDrawRecord> getGachaDrawHistory(String shopId) {
        return gachaData.getDrawHistory(shopId);
    }

    // ═══════════════════════════════════════════════
    //  任务管理
    // ═══════════════════════════════════════════════

    @Override
    public synchronized void clearGachaDrawHistory(String shopId) {
        gachaData.clearDrawHistory(shopId);
        isDirty = true;
    }

    @Override
    public synchronized void addActiveQuest(QuestRuntimeData data) {
        Objects.requireNonNull(data);
        String questId = data.getQuestId();
        activeQuests.put(questId, data);
        failedQuests.remove(questId);
        isDirty = true;
    }

    @Override
    public synchronized void removeActiveQuest(String questId) {
        activeQuests.remove(questId);
        isDirty = true;
    }

    @Override
    public synchronized void markCompleted(String questId) {
        activeQuests.remove(questId);
        completedQuests.add(questId);
        failedQuests.remove(questId);
        isDirty = true;
    }

    @Override
    public synchronized void markFailed(String questId) {
        activeQuests.remove(questId);
        failedQuests.add(questId);
        isDirty = true;
    }

    @Nullable
    @Override
    public QuestRuntimeData getActiveQuest(String questId) {
        return activeQuests.get(questId);
    }

    @Override
    public Map<String, QuestRuntimeData> getAllActiveQuests() {
        return Collections.unmodifiableMap(activeQuests);
    }

    @Override
    public Set<String> getCompletedQuests() {
        return Collections.unmodifiableSet(completedQuests);
    }

    @Override
    public Set<String> getFailedQuests() {
        return Collections.unmodifiableSet(failedQuests);
    }

    @Override
    public boolean isQuestActive(String questId) {
        return activeQuests.containsKey(questId);
    }

    @Override
    public boolean isQuestCompleted(String questId) {
        return completedQuests.contains(questId);
    }

    // ═══════════════════════════════════════════════
    //  Flag / Variable
    // ═══════════════════════════════════════════════

    @Override
    public boolean isQuestFailed(String questId) {
        return failedQuests.contains(questId);
    }

    @Override
    public void setFlag(String flag) {
        flags.add(flag);
        isDirty = true;
    }

    @Override
    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    @Override
    public void removeFlag(String flag) {
        flags.remove(flag);
        isDirty = true;
    }

    @Override
    public Set<String> getAllFlags() {
        return Collections.unmodifiableSet(flags);
    }

    @Override
    public int getVariable(String key) {
        return variables.getOrDefault(key, 0);
    }

    @Override
    public void setVariable(String key, int value) {
        variables.put(key, value);
        isDirty = true;
    }

    @Override
    public void incrementVariable(String key, int amount) {
        variables.merge(key, amount, Integer::sum);
        isDirty = true;
    }

    @Override
    public Map<String, Integer> getAllVariables() {
        return Collections.unmodifiableMap(variables);
    }

    @Override
    public synchronized void upsertMarker(QuestMarkerData marker) {
        Objects.requireNonNull(marker);
        markers.put(marker.getId(), marker);
        isDirty = true;
    }

    @Override
    public synchronized void removeMarker(String markerId) {
        markers.remove(markerId);
        isDirty = true;
    }

    @Override
    public synchronized void clearMarkers() {
        markers.clear();
        isDirty = true;
    }

    // ═══════════════════════════════════════════════
    //  序列化
    // ═══════════════════════════════════════════════

    @Override
    public synchronized Map<String, QuestMarkerData> getAllMarkers() {
        return Collections.unmodifiableMap(markers);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag root = new CompoundTag();

        ListTag activeList = new ListTag();
        for (QuestRuntimeData data : activeQuests.values()) activeList.add(data.serializeNBT());
        root.put("ActiveQuests", activeList);

        ListTag completedList = new ListTag();
        for (String id : completedQuests) completedList.add(StringTag.valueOf(id));
        root.put("CompletedQuests", completedList);

        ListTag failedList = new ListTag();
        for (String id : failedQuests) failedList.add(StringTag.valueOf(id));
        root.put("FailedQuests", failedList);

        ListTag flagList = new ListTag();
        for (String f : flags) flagList.add(StringTag.valueOf(f));
        root.put("Flags", flagList);

        CompoundTag varsTag = new CompoundTag();
        for (var e : variables.entrySet()) varsTag.putInt(e.getKey(), e.getValue());
        root.put("Variables", varsTag);

        ListTag markerList = new ListTag();
        for (QuestMarkerData m : markers.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("id", m.getId());
            t.putDouble("x", m.getWorldX());
            t.putDouble("y", m.getWorldY());
            t.putDouble("z", m.getWorldZ());
            t.putString("label", m.getLabel());
            t.putString("dimension", m.getDimension());
            t.putString("questId", m.getQuestId());
            t.putString("phaseId", m.getPhaseId());
            t.putInt("objectiveIndex", m.getObjectiveIndex());
            t.putInt("color", m.getColorARGB());
            t.putString("type", m.getType().name());
            t.putString("state", m.getState().name());
            t.putBoolean("showDistance", m.isShowDistance());
            t.putBoolean("allowOffscreenArrow", m.isAllowOffscreenArrow());
            t.putInt("followEntityId", m.getFollowEntityId());
            t.putString("followEntityUuid", m.getFollowEntityUuid());
            t.putString("followEntityGuid", m.getFollowEntityGuid());
            t.putString("attachPoint", m.getAttachPoint().name());
            markerList.add(t);
        }
        root.put("Markers", markerList);

        root.put("DialogueProgress", dialogueProgress.serialize());

        root.put("TradeData", tradeData.serialize());

        root.put("GachaData", gachaData.serialize());

        VERSION_MANAGER.setInitialVersion(root);
        return root;
    }

    @Override
    public void deserializeNBT(CompoundTag root) {
        VERSION_MANAGER.migrate(root);

        activeQuests.clear();
        completedQuests.clear();
        failedQuests.clear();
        flags.clear();
        variables.clear();
        markers.clear();

        ListTag activeList = root.getList("ActiveQuests", Tag.TAG_COMPOUND);
        for (int i = 0; i < activeList.size(); i++) {
            QuestRuntimeData data = QuestRuntimeData.deserializeNBT(activeList.getCompound(i));
            activeQuests.put(data.getQuestId(), data);
        }

        ListTag completedList = root.getList("CompletedQuests", Tag.TAG_STRING);
        for (int i = 0; i < completedList.size(); i++) completedQuests.add(completedList.getString(i));

        ListTag failedList = root.getList("FailedQuests", Tag.TAG_STRING);
        for (int i = 0; i < failedList.size(); i++) failedQuests.add(failedList.getString(i));

        ListTag flagList = root.getList("Flags", Tag.TAG_STRING);
        for (int i = 0; i < flagList.size(); i++) flags.add(flagList.getString(i));

        CompoundTag varsTag = root.getCompound("Variables");
        for (String key : varsTag.getAllKeys()) variables.put(key, varsTag.getInt(key));

        ListTag markerList = root.getList("Markers", Tag.TAG_COMPOUND);
        for (int i = 0; i < markerList.size(); i++) {
            CompoundTag t = markerList.getCompound(i);
            String id = t.getString("id");
            if (id == null || id.isEmpty()) continue;

            QuestMarkerType type;
            QuestMarkerState state;
            try {
                type = QuestMarkerType.valueOf(t.getString("type"));
            } catch (Exception e) {
                type = QuestMarkerType.CUSTOM;
            }
            try {
                state = QuestMarkerState.valueOf(t.getString("state"));
            } catch (Exception e) {
                state = QuestMarkerState.ACTIVE;
            }

            QuestMarkerData marker = new QuestMarkerData.Builder(
                    id,
                    t.getDouble("x"),
                    t.getDouble("y"),
                    t.getDouble("z"),
                    t.getString("label")
            )
                    .dimension(t.contains("dimension", Tag.TAG_STRING) ? t.getString("dimension") : "minecraft:overworld")
                    .bindQuest(t.contains("questId", Tag.TAG_STRING) ? t.getString("questId") : "")
                    .bindPhase(t.contains("phaseId", Tag.TAG_STRING) ? t.getString("phaseId") : "")
                    .bindObjective(t.contains("objectiveIndex", Tag.TAG_INT) ? t.getInt("objectiveIndex") : -1)
                    .followEntity(
                            t.contains("followEntityId", Tag.TAG_INT) ? t.getInt("followEntityId") : -1,
                            t.contains("followEntityUuid", Tag.TAG_STRING) ? t.getString("followEntityUuid") : "",
                            t.contains("followEntityGuid", Tag.TAG_STRING) ? t.getString("followEntityGuid") : "",
                            parseAttachPoint(t.contains("attachPoint", Tag.TAG_STRING) ? t.getString("attachPoint") : "HEAD")
                    )
                    .type(type)
                    .state(state)
                    .color(t.getInt("color"))
                    .showDistance(!t.contains("showDistance", Tag.TAG_BYTE) || t.getBoolean("showDistance"))
                    .allowOffscreenArrow(!t.contains("allowOffscreenArrow", Tag.TAG_BYTE) || t.getBoolean("allowOffscreenArrow"))
                    .build();
            markers.put(id, marker);
        }

        if (root.contains("DialogueProgress", Tag.TAG_COMPOUND)) {
            dialogueProgress.deserialize(root.getCompound("DialogueProgress"));
        } else if (root.contains("NodeVisitHistory", Tag.TAG_COMPOUND)) {
            dialogueProgress.migrateFromLegacy(root);
        }

        if (root.contains("TradeData", Tag.TAG_COMPOUND)) {
            tradeData.deserialize(root.getCompound("TradeData"));
        } else {
            // 旧存档：从 TradePurchases 和 DialogueProgress.Trade 分区迁移
            tradeData.deserializeLegacy(root);
        }

        if (root.contains("GachaData", Tag.TAG_COMPOUND)) {
            gachaData.deserialize(root.getCompound("GachaData"));
        } else {
            gachaData.deserializeLegacy(root);
        }
    }

    // ═══════════════════════════════════════════════
    //  其他
    // ═══════════════════════════════════════════════

    @Override
    public void copyFrom(IQuestCapability other) {
        deserializeNBT(other.serializeNBT());
    }

    @Override
    public void clearAllData() {
        activeQuests.clear();
        completedQuests.clear();
        failedQuests.clear();
        flags.clear();
        variables.clear();
        dialogueProgress.clear();
        tradeData.clear();
        gachaData.clear();
        markers.clear();
        isDirty = true;
    }

    public boolean isDirty() {
        if (isDirty) return true;
        if (dialogueProgress.isDirty()) return true;
        for (QuestRuntimeData data : activeQuests.values()) {
            if (data.isDirty()) return true;
        }
        return false;
    }

    public void clearDirty() {
        isDirty = false;
        dialogueProgress.clearDirty();
        for (QuestRuntimeData data : activeQuests.values()) data.clearDirty();
    }
}