package org.arcadia.arc_quest.quest.logic;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import org.arcadia.arc_quest.api.event.*;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.capability.IQuestCapability;
import org.arcadia.arc_quest.quest.capability.QuestCapabilityProvider;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.event.QuestChangeEvent;
import org.arcadia.arc_quest.quest.event.QuestEventBus;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.QuestRejectCodeDictionary;
import org.arcadia.arc_quest.quest.network.QuestSyncCoordinator;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.tracking.ObjectiveKey;
import org.arcadia.arc_quest.quest.tracking.ObjectiveTracker;
import org.arcadia.arc_quest.quest.tracking.TrackedObjective;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerState;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerType;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 任务进度推进核心逻辑（服务端）。
 * 并行模型：同一 Quest 内可有多个 active phase 同时推进。
 */
public final class QuestProgressHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private QuestProgressHandler() {
    }

    private static final class ActivationContext {
        int activatedCount = 0;
        boolean flagsChanged = false;
    }

    // ═══════════════════════════════════════════════════════
    //  接受任务
    // ═══════════════════════════════════════════════════════

    public static boolean acceptQuest(ServerPlayer player, String questId) {
        return acceptQuestWithCode(player, questId) == QuestRejectCodeDictionary.Code.OK;
    }

    private static boolean shouldCompleteQuest(QuestDefinition def, QuestRuntimeData data) {
        int done = data.getCompletedPhaseIds().size();
        return switch (def.getCompletionPolicy()) {
            case ALL -> done >= def.getPhaseIds().size();
            case ANY -> done >= 1;
            case N_OF_M -> done >= Math.max(1, def.getCompletionRequiredCount());
            case SPECIFIC_PHASE -> {
                String target = def.getCompletionTargetPhaseId();
                yield target != null && data.getCompletedPhaseIds().contains(target);
            }
        };
    }

    public static QuestRejectCodeDictionary.Code acceptQuestWithCode(ServerPlayer player, String questId) {
        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) {
            LOGGER.warn("[ArcQuest] Cannot accept unknown quest: {}", questId);
            return QuestRejectCodeDictionary.Code.QUEST_NOT_FOUND;
        }

        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);

        if (cap.isQuestActive(questId)) {
            return QuestRejectCodeDictionary.Code.ALREADY_ACTIVE;
        }
        if (cap.isQuestCompleted(questId) && !def.isRepeatable()) {
            return QuestRejectCodeDictionary.Code.ALREADY_COMPLETED_NOT_REPEATABLE;
        }

        Set<ResourceLocation> completedQuests = cap.getCompletedQuestLocations();
        for (ICondition cond : def.getUnlockConditions()) {
            if (!cond.test(player, completedQuests, cap.getAllFlags(), cap.getAllVariables())) {
                return QuestRejectCodeDictionary.Code.UNLOCK_CONDITION_NOT_MET;
            }
        }

        PhaseDefinition firstPhase = def.getInitialPhase();
        if (firstPhase == null) {
            return QuestRejectCodeDictionary.Code.NO_INITIAL_PHASE;
        }

        QuestRuntimeData data = new QuestRuntimeData(
                questId,
                firstPhase.getPhaseId(),
                firstPhase.getObjectives().size(),
                player.getServer() != null ? player.getServer().getTickCount() : 0
        );
        cap.addActiveQuest(data);

        boolean flagsChanged = false;
        for (String flag : def.getFlagsToSetOnAccept()) {
            cap.setFlag(flag);
            flagsChanged = true;
        }
        for (String flag : firstPhase.getFlagsToSetOnEnter()) {
            cap.setFlag(flag);
            flagsChanged = true;
        }

        registerPhaseObjectives(player, def, firstPhase);
        refreshQuestMarkersForQuest(player, cap, data, def);

        // 仅对 autoEnterByCondition=true 的 phase 扫描自动入场
        ActivationContext ctx = new ActivationContext();
        tryAutoEnterPhases(player, cap, data, def, firstPhase.getPhaseId(), ctx);
        flagsChanged = flagsChanged || ctx.flagsChanged;

        syncQuestStateAndPush(player, data);
        if (flagsChanged) {
            syncFlagsVarsAndPush(player, cap);
        }

        QuestEventBus.fire(QuestChangeEvent.questAccepted(ResourceLocation.parse(questId)));
        MinecraftForge.EVENT_BUS.post(new QuestAcceptedEvent(player, ResourceLocation.parse(questId)));
        MinecraftForge.EVENT_BUS.post(new QuestStartedEvent(player, ResourceLocation.parse(questId)));

        return QuestRejectCodeDictionary.Code.OK;
    }

    // ═══════════════════════════════════════════════════════
    //  目标推进（并行 phase 维度）
    // ═══════════════════════════════════════════════════════

    public static void incrementObjective(ServerPlayer player,
                                          String questId,
                                          String phaseId,
                                          int objIndex,
                                          int amount) {
        if (amount <= 0) return;

        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data == null || data.getState() != QuestState.ACTIVE) return;
        if (!data.isPhaseActive(phaseId)) return;

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) return;

        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase == null) return;
        if (objIndex < 0 || objIndex >= phase.getObjectives().size()) return;

        ObjectiveEntry objEntry = phase.getObjectives().get(objIndex);
        int required = resolveRequiredCount(player, objEntry, cap);

        int currentProgress = data.getObjectiveProgress(phaseId, objIndex);
        if (currentProgress >= required) return;

        int newProgress = data.incrementProgress(phaseId, objIndex, amount, required);

        syncDeltaProgressAndPush(player, questId, phaseId, objIndex, newProgress);

        QuestEventBus.fire(QuestChangeEvent.objectiveProgressed(
                ResourceLocation.parse(questId), objIndex, newProgress, required));

        MinecraftForge.EVENT_BUS.post(new QuestProgressChangedEvent(
                player, ResourceLocation.parse(questId), phaseId,
                objIndex, currentProgress, newProgress, required));

        checkPhaseCompletion(player, cap, data, def, phaseId);
    }

    private static void checkPhaseCompletion(ServerPlayer player,
                                             IQuestCapability cap,
                                             QuestRuntimeData data,
                                             QuestDefinition def,
                                             String phaseId) {
        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase == null || !data.isPhaseActive(phaseId)) return;

        List<ObjectiveEntry> objectives = phase.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            if (data.getObjectiveProgress(phaseId, i) < resolveRequiredCount(player, objectives.get(i), cap)) {
                return;
            }
        }

        MinecraftForge.EVENT_BUS.post(new QuestPhaseCompletedEvent(
                player, ResourceLocation.parse(data.getQuestId()), phase.getPhaseId()));

        grantRewards(player, phase.getPhaseRewards(), "phase");
        unregisterPhaseObjectives(player, def, phase);
        data.completePhase(phaseId);

        ActivationContext ctx = new ActivationContext();
        for (String flag : phase.getFlagsToSetOnComplete()) {
            cap.setFlag(flag);
            ctx.flagsChanged = true;
        }

        // choices：该 phase 完成后等待玩家选路，不自动推进 transition
        if (phase.hasChoices()) {
            // 但允许 auto enter phase 扫描（如配置了 autoEnterByCondition=true）
            tryAutoEnterPhases(player, cap, data, def, phaseId, ctx);

            if (shouldCompleteQuest(def, data)) {
                completeQuest(player, cap, data, def);
                return;
            }

            refreshQuestMarkersForQuest(player, cap, data, def);
            syncQuestStateAndPush(player, data);
            if (ctx.flagsChanged) {
                syncFlagsVarsAndPush(player, cap);
            }
            return;
        }

        Set<ResourceLocation> completedQuests = cap.getCompletedQuestLocations();

        // 自动解锁后继（可多条，支持 thenGoToIf）
        for (PhaseTransition tr : phase.getTransitions()) {
            boolean ok = tr.getCondition() == null
                    || tr.getCondition().test(player, completedQuests, cap.getAllFlags(), cap.getAllVariables());
            if (!ok) continue;

            activatePhase(player, cap, data, def, phaseId, tr.getTargetPhaseId(), true, ctx);
        }

        // enterCondition 自动扫描（仅 autoEnterByCondition=true 的 phase）
        tryAutoEnterPhases(player, cap, data, def, phaseId, ctx);

        if (shouldCompleteQuest(def, data)) {
            completeQuest(player, cap, data, def);
        } else {
            syncQuestStateAndPush(player, data);
            if (ctx.flagsChanged) {
                syncFlagsVarsAndPush(player, cap);
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // 阶段推进（显式推进，用于 choice 等）
    // ═══════════════════════════════════════════════════════

    public static void advanceToPhase(ServerPlayer player,
                                      IQuestCapability cap,
                                      QuestRuntimeData data,
                                      QuestDefinition def,
                                      String nextPhaseId) {
        String fromPhaseId = data.getCurrentPhaseId();

        ActivationContext ctx = new ActivationContext();
        boolean ok = activatePhase(player, cap, data, def, fromPhaseId, nextPhaseId, true, ctx);
        if (!ok) {
            LOGGER.warn("[ArcQuest] Target phase cannot be activated: {}/{}", def.getId(), nextPhaseId);
            return;
        }

        tryAutoEnterPhases(player, cap, data, def, fromPhaseId, ctx);

        refreshQuestMarkersForQuest(player, cap, data, def);
        syncQuestStateAndPush(player, data);
        if (ctx.flagsChanged) {
            syncFlagsVarsAndPush(player, cap);
        }
    }

    // ═══════════════════════════════════════════════════════
    // 分支选择
    // ═══════════════════════════════════════════════════════

    public static boolean handlePlayerChoice(ServerPlayer player,
                                             String questId,
                                             String phaseId,
                                             int choiceIndex) {
        return handlePlayerChoiceWithCode(player, questId, phaseId, choiceIndex) == QuestRejectCodeDictionary.Code.OK;
    }

    // 兼容旧入口
    public static boolean handlePlayerChoice(ServerPlayer player,
                                             String questId,
                                             int choiceIndex) {
        return handlePlayerChoiceWithCode(player, questId, "", choiceIndex) == QuestRejectCodeDictionary.Code.OK;
    }

    public static QuestRejectCodeDictionary.Code handlePlayerChoiceWithCode(ServerPlayer player,
                                                                            String questId,
                                                                            String phaseId,
                                                                            int choiceIndex) {
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);

        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data == null) return QuestRejectCodeDictionary.Code.NOT_ACTIVE;

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) return QuestRejectCodeDictionary.Code.QUEST_NOT_FOUND;

        String resolvedPhaseId = phaseId;
        if (resolvedPhaseId == null || resolvedPhaseId.isEmpty()) {
            for (String pid : data.getActivePhaseIds()) {
                PhaseDefinition p = def.getPhase(pid);
                if (p != null && p.hasChoices()) {
                    resolvedPhaseId = pid;
                    break;
                }
            }
        }

        if (resolvedPhaseId == null || resolvedPhaseId.isEmpty()) {
            return QuestRejectCodeDictionary.Code.PHASE_NOT_FOUND;
        }
        if (!data.isPhaseActive(resolvedPhaseId)) {
            return QuestRejectCodeDictionary.Code.PHASE_NOT_FOUND;
        }

        PhaseDefinition currentPhase = def.getPhase(resolvedPhaseId);
        if (currentPhase == null) return QuestRejectCodeDictionary.Code.PHASE_NOT_FOUND;

        List<ChoiceOption> choices = currentPhase.getChoices();
        if (choiceIndex < 0 || choiceIndex >= choices.size()) {
            LOGGER.warn("[ArcQuest] Invalid choice index {} for quest {} phase {}", choiceIndex, questId, resolvedPhaseId);
            return QuestRejectCodeDictionary.Code.INVALID_CHOICE_INDEX;
        }

        ChoiceOption chosen = choices.get(choiceIndex);

        Set<ResourceLocation> completedQuests = cap.getCompletedQuestLocations();
        ICondition visibleCondition = chosen.getVisibleCondition();
        boolean conditionsMet = visibleCondition == null ||
                visibleCondition.test(player, completedQuests, cap.getAllFlags(), cap.getAllVariables());
        if (!conditionsMet) {
            LOGGER.debug("[ArcQuest] Choice conditions not met for index {}", choiceIndex);
            return QuestRejectCodeDictionary.Code.CHOICE_CONDITION_NOT_MET;
        }

        ActivationContext ctx = new ActivationContext();

        String flagToSet = chosen.getFlagToSet();
        if (flagToSet != null && !flagToSet.isEmpty()) {
            cap.setFlag(flagToSet);
            ctx.flagsChanged = true;
            LOGGER.debug("[ArcQuest] Set flag '{}' from choice", flagToSet);
        }

        String targetPhaseId = chosen.getTargetPhaseId();
        if (targetPhaseId == null || targetPhaseId.isEmpty()) {
            LOGGER.warn("[ArcQuest] Choice has no target phase: {}", choiceIndex);
            return QuestRejectCodeDictionary.Code.CHOICE_TARGET_PHASE_MISSING;
        }

        // 完成当前 choice phase
        data.completePhase(resolvedPhaseId);
        unregisterPhaseObjectives(player, def, currentPhase);
        for (String flag : currentPhase.getFlagsToSetOnComplete()) {
            cap.setFlag(flag);
            ctx.flagsChanged = true;
        }

        // 并行增强：激活“选择目标 + 当前 phase 里其它满足条件的 transition”
        Set<String> toActivate = new LinkedHashSet<>();
        toActivate.add(targetPhaseId);

        for (PhaseTransition tr : currentPhase.getTransitions()) {
            String pid = tr.getTargetPhaseId();
            if (pid == null || pid.isEmpty() || pid.equals(targetPhaseId)) continue;

            ICondition cond = tr.getCondition();
            boolean ok = cond == null || cond.test(player, completedQuests, cap.getAllFlags(), cap.getAllVariables());
            if (!ok) continue;

            toActivate.add(pid);
        }

        for (String pid : toActivate) {
            activatePhase(player, cap, data, def, resolvedPhaseId, pid, true, ctx);
        }

        // enterCondition 自动扫描（仅 autoEnterByCondition=true 的 phase）
        tryAutoEnterPhases(player, cap, data, def, resolvedPhaseId, ctx);

        if (shouldCompleteQuest(def, data)) {
            completeQuest(player, cap, data, def);
        } else {
            refreshQuestMarkersForQuest(player, cap, data, def);
            syncQuestStateAndPush(player, data);
            if (ctx.flagsChanged) {
                syncFlagsVarsAndPush(player, cap);
            }
        }

        if (ctx.activatedCount == 0 && !shouldCompleteQuest(def, data)) {
            LOGGER.warn("[ArcQuest] Choice resolved but no next phase activated: quest={}, phase={}, choice={}",
                    questId, resolvedPhaseId, choiceIndex);
            return QuestRejectCodeDictionary.Code.CHOICE_TARGET_PHASE_MISSING;
        }

        return QuestRejectCodeDictionary.Code.OK;
    }

    // ═══════════════════════════════════════════════════════
    // 任务完成 / 失败 / 放弃
    // ═══════════════════════════════════════════════════════

    private static void completeQuest(ServerPlayer player,
                                      IQuestCapability cap,
                                      QuestRuntimeData data,
                                      QuestDefinition def) {
        doCompleteQuest(player, cap, data, def, "completed");
    }

    public static void failQuest(ServerPlayer player, String questId) {
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data == null) return;

        data.setState(QuestState.FAILED);
        cap.markFailed(questId);
        ObjectiveTracker.INSTANCE.unregisterQuest(player.getUUID(), questId);
        clearQuestMarkers(cap, questId);

        syncQuestStateAndPush(player, data);
        QuestEventBus.fire(QuestChangeEvent.questFailed(ResourceLocation.parse(questId)));
        MinecraftForge.EVENT_BUS.post(new QuestFailedEvent(player, ResourceLocation.parse(questId)));
    }

    public static boolean abandonQuest(ServerPlayer player, String questId) {
        return abandonQuestWithCode(player, questId) == QuestRejectCodeDictionary.Code.OK;
    }

    public static QuestRejectCodeDictionary.Code abandonQuestWithCode(ServerPlayer player, String questId) {
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        if (!cap.isQuestActive(questId)) {
            return QuestRejectCodeDictionary.Code.NOT_ACTIVE;
        }

        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data != null) {
            data.setState(QuestState.FAILED);
        }

        cap.markFailed(questId);
        ObjectiveTracker.INSTANCE.unregisterQuest(player.getUUID(), questId);
        clearQuestMarkers(cap, questId);

        syncFullDataAndPush(player, cap);
        QuestEventBus.fire(QuestChangeEvent.questFailed(ResourceLocation.parse(questId)));
        MinecraftForge.EVENT_BUS.post(new QuestFailedEvent(player, ResourceLocation.parse(questId)));
        return QuestRejectCodeDictionary.Code.OK;
    }

    public static void forceComplete(ServerPlayer player, String questId) {
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data == null) return;

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) return;

        doCompleteQuest(player, cap, data, def, "force-completed");
    }

    private static void doCompleteQuest(ServerPlayer player,
                                        IQuestCapability cap,
                                        QuestRuntimeData data,
                                        QuestDefinition def,
                                        String logPrefix) {
        String questId = data.getQuestId();

        grantRewards(player, def.getCompletionRewards(), "completion");
        def.getFlagsToSetOnComplete().forEach(cap::setFlag);

        data.setState(QuestState.COMPLETED);
        cap.markCompleted(questId);

        ObjectiveTracker.INSTANCE.unregisterQuest(player.getUUID(), questId);
        clearQuestMarkers(cap, questId);

        LOGGER.info("[ArcQuest] Player {} {} quest: {}",
                player.getGameProfile().getName(), logPrefix, questId);

        syncQuestStateAndPush(player, data);
        syncFlagsVarsAndPush(player, cap);
        QuestEventBus.fire(QuestChangeEvent.questCompleted(ResourceLocation.parse(questId)));
        MinecraftForge.EVENT_BUS.post(new QuestCompletedEvent(player, ResourceLocation.parse(questId)));
    }

    public static void syncToClient(ServerPlayer player, String questId) {
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data != null) {
            syncQuestStateAndPush(player, data);
        }
    }

    private static void syncQuestStateAndPush(ServerPlayer player, QuestRuntimeData data) {
        QuestSyncCoordinator.syncQuestStateAndPush(player, data);
    }

    private static void syncFlagsVarsAndPush(ServerPlayer player, IQuestCapability cap) {
        QuestSyncCoordinator.syncFlagsVarsAndPush(player, cap);
    }

    private static void syncFullDataAndPush(ServerPlayer player, IQuestCapability cap) {
        QuestSyncCoordinator.syncFullDataAndPush(player, cap);
    }

    private static void syncDeltaProgressAndPush(ServerPlayer player,
                                                 String questId,
                                                 String phaseId,
                                                 int objIndex,
                                                 int newProgress) {
        QuestSyncCoordinator.syncDeltaProgressAndPush(player, questId, phaseId, objIndex, newProgress);
    }

    // ═══════════════════════════════════════════════════════
    // 索引管理
    // ═══════════════════════════════════════════════════════

    public static void rebuildTrackingIndex(ServerPlayer player, IQuestCapability cap) {
        ObjectiveTracker.INSTANCE.unregisterPlayer(player.getUUID());

        for (Map.Entry<String, QuestRuntimeData> entry : cap.getAllActiveQuests().entrySet()) {
            QuestRuntimeData data = entry.getValue();
            if (data.getState() != QuestState.ACTIVE) continue;

            QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(data.getQuestId()));
            if (def == null) continue;

            for (String phaseId : data.getActivePhaseIds()) {
                PhaseDefinition phase = def.getPhase(phaseId);
                if (phase == null) continue;
                registerPhaseObjectives(player, def, phase);
            }

            refreshQuestMarkersForQuest(player, cap, data, def);
        }
    }

    public static void registerPhaseObjectives(ServerPlayer player,
                                               QuestDefinition def,
                                               PhaseDefinition phase) {
        List<ObjectiveEntry> objectives = phase.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            ObjectiveEntry obj = objectives.get(i);
            List<ResourceLocation> keys = objectiveKeyTargets(obj);
            for (ResourceLocation keyTarget : keys) {
                TrackedObjective tracked = new TrackedObjective(
                        player.getUUID(),
                        def.getId(),
                        phase.getPhaseId(),
                        i,
                        new ObjectiveKey(obj.getType(), keyTarget),
                        obj.getRequiredCount()
                );
                ObjectiveTracker.INSTANCE.register(tracked);
            }
        }
    }

    private static void unregisterPhaseObjectives(ServerPlayer player,
                                                  QuestDefinition def,
                                                  PhaseDefinition phase) {
        List<ObjectiveEntry> objectives = phase.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            ObjectiveEntry obj = objectives.get(i);
            List<ResourceLocation> keys = objectiveKeyTargets(obj);
            for (ResourceLocation keyTarget : keys) {
                TrackedObjective tracked = new TrackedObjective(
                        player.getUUID(),
                        def.getId(),
                        phase.getPhaseId(),
                        i,
                        new ObjectiveKey(obj.getType(), keyTarget),
                        obj.getRequiredCount()
                );
                ObjectiveTracker.INSTANCE.unregister(tracked);
            }
        }
    }

    public static int resolveRequiredCount(ServerPlayer player, ObjectiveEntry obj, IQuestCapability cap) {
        int fromModifier = obj.resolveRequiredCount(player);

        String mode = obj.getExtra("count_mode");
        if (mode == null || mode.isEmpty()) return Math.max(1, fromModifier);

        int base = obj.getExtraInt("count_base", fromModifier);
        int min = obj.getExtraInt("count_min", 1);
        int max = obj.getExtraInt("count_max", -1);

        int computed = base;
        if ("player_level".equals(mode)) {
            int perLevel = obj.getExtraInt("count_per_level", 0);
            computed = base + Math.max(0, player.experienceLevel) * perLevel;
        } else if ("variable".equals(mode)) {
            String var = obj.getExtra("count_var");
            int perVar = obj.getExtraInt("count_per_var", 0);
            int varVal = (var == null || var.isEmpty()) ? 0 : cap.getVariable(var);
            computed = base + varVal * perVar;
        }

        computed = Math.max(min, computed);
        if (max > 0) computed = Math.min(max, computed);
        return Math.max(1, computed);
    }

    private static List<ResourceLocation> objectiveKeyTargets(ObjectiveEntry obj) {
        String tag = obj.getExtra("target_tag");
        if (tag == null || tag.isEmpty()) {
            return List.of(obj.getTargetId());
        }

        ResourceLocation tagId = ResourceLocation.parse(tag);
        TagKey<Item> key = TagKey.create(Registries.ITEM, tagId);
        var named = ForgeRegistries.ITEMS.tags();
        if (named == null) return List.of(obj.getTargetId());

        List<ResourceLocation> ids = new ArrayList<>();
        for (Item taggedItem : named.getTag(key)) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(taggedItem);
            if (id != null) ids.add(id);
        }
        if (ids.isEmpty()) ids.add(obj.getTargetId());
        return ids;
    }

    private static void grantRewards(ServerPlayer player, List<IReward> rewards, String context) {
        for (IReward reward : rewards) {
            try {
                reward.grant(player);
            } catch (Exception e) {
                LOGGER.error("[ArcQuest] Error granting {} reward: {}", context, e.getMessage(), e);
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // enterCondition / 激活辅助
    // ═══════════════════════════════════════════════════════

    private static boolean canEnterPhase(ServerPlayer player,
                                         IQuestCapability cap,
                                         PhaseDefinition phase) {
        ICondition cond = phase.getEnterCondition();
        if (cond == null) return true;
        return cond.test(player, cap.getCompletedQuestLocations(), cap.getAllFlags(), cap.getAllVariables());
    }

    private static boolean activatePhase(ServerPlayer player,
                                         IQuestCapability cap,
                                         QuestRuntimeData data,
                                         QuestDefinition def,
                                         String fromPhaseId,
                                         String targetPhaseId,
                                         boolean enforceEnterCondition,
                                         ActivationContext ctx) {
        PhaseDefinition next = def.getPhase(targetPhaseId);
        if (next == null) return false;
        if (data.isPhaseActive(targetPhaseId) || data.isPhaseCompleted(targetPhaseId)) return false;

        if (enforceEnterCondition && !canEnterPhase(player, cap, next)) {
            return false;
        }

        data.activatePhase(next.getPhaseId(), next.getObjectives().size());
        registerPhaseObjectives(player, def, next);

        for (String flag : next.getFlagsToSetOnEnter()) {
            cap.setFlag(flag);
            ctx.flagsChanged = true;
        }

        ctx.activatedCount++;

        QuestEventBus.fire(QuestChangeEvent.phaseChanged(def.getId(), fromPhaseId, next.getPhaseId()));
        MinecraftForge.EVENT_BUS.post(new QuestPhaseChangedEvent(player, def.getId(), fromPhaseId, next.getPhaseId()));
        return true;
    }

    private static int tryAutoEnterPhases(ServerPlayer player,
                                          IQuestCapability cap,
                                          QuestRuntimeData data,
                                          QuestDefinition def,
                                          String fromPhaseId,
                                          ActivationContext ctx) {
        int before = ctx.activatedCount;
        boolean changed;

        do {
            changed = false;

            for (String pid : def.getPhaseIds()) {
                if (data.isPhaseActive(pid) || data.isPhaseCompleted(pid)) continue;

                PhaseDefinition phase = def.getPhase(pid);
                if (phase == null) continue;
                if (!phase.isAutoEnterByCondition()) continue;
                if (phase.getEnterCondition() == null) continue;
                if (!canEnterPhase(player, cap, phase)) continue;

                boolean ok = activatePhase(player, cap, data, def, fromPhaseId, pid, false, ctx);
                if (ok) changed = true;
            }
        } while (changed);

        return ctx.activatedCount - before;
    }

    private static void refreshQuestMarkersForQuest(ServerPlayer player,
                                                    IQuestCapability cap,
                                                    QuestRuntimeData data,
                                                    QuestDefinition def) {
        List<String> removedIds = clearQuestMarkers(cap, data.getQuestId());
        for (String markerId : removedIds) {
            ArcQuestNetwork.syncMarkerDeltaRemove(player, markerId);
        }

        String dimension = player.level().dimension().location().toString();
        QuestMarkerType questType = def.getCategory() == QuestCategory.ARCHON
                ? QuestMarkerType.QUEST_MAIN
                : QuestMarkerType.QUEST_SIDE;

        for (String phaseId : data.getActivePhaseIds()) {
            PhaseDefinition phase = def.getPhase(phaseId);
            if (phase == null) continue;

            List<ObjectiveEntry> objectives = phase.getObjectives();
            for (int i = 0; i < objectives.size(); i++) {
                ObjectiveEntry obj = objectives.get(i);
                if (obj.isHidden()) continue;

                if (obj.getType() != ObjectiveType.REACH_LOCATION) continue;

                Double x = parseDouble(obj.getExtra("x"));
                Double y = parseDouble(obj.getExtra("y"));
                Double z = parseDouble(obj.getExtra("z"));
                if (x == null || y == null || z == null) continue;

                String markerDimension = firstNonEmpty(
                        obj.getExtra("dimension"),
                        obj.getExtra("dim"),
                        obj.getExtra("world"),
                        dimension
                );

                String markerId = markerId(data.getQuestId(), phaseId, i);
                String label = obj.getDisplayText().getString();

                QuestMarkerData marker = new QuestMarkerData.Builder(markerId, x, y, z, label)
                        .dimension(markerDimension)
                        .bindQuest(data.getQuestId())
                        .bindPhase(phaseId)
                        .bindObjective(i)
                        .type(questType)
                        .state(QuestMarkerState.fromQuestState(data.getState()))
                        .color(0xFF000000 | def.getCategory().getThemeColor())
                        .showDistance(true)
                        .allowOffscreenArrow(true)
                        .build();
                cap.upsertMarker(marker);
                ArcQuestNetwork.syncMarkerDeltaUpsert(player, marker);
                ArcQuestNetwork.syncMarkerDeltaUpsert(player, marker);
            }
        }
    }

    private static List<String> clearQuestMarkers(IQuestCapability cap, String questId) {
        List<String> toRemove = cap.getAllMarkers().values().stream()
                .filter(m -> m.hasQuestBinding() && questId.equals(m.getQuestId()))
                .map(QuestMarkerData::getId)
                .toList();
        for (String id : toRemove) {
            cap.removeMarker(id);
        }
        return toRemove;
    }

    private static String markerId(String questId, String phaseId, int objectiveIndex) {
        return "quest:" + questId + ":" + phaseId + ":" + objectiveIndex;
    }

    private static String firstNonEmpty(String... candidates) {
        for (String s : candidates) {
            if (s != null && !s.isEmpty()) return s;
        }
        return "";
    }

    private static Double parseDouble(String v) {
        if (v == null || v.isEmpty()) return null;
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
