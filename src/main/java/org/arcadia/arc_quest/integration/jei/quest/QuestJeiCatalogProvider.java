package org.arcadia.arc_quest.integration.jei.quest;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.core.CoreProcessors;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogEntry;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogProvider;
import org.arcadia.arc_quest.integration.jei.api.JeiDisplayAdapters;
import org.arcadia.arc_quest.integration.jei.api.JeiIngredient;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.data.CollectionRuntimeData;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Consumer;

/** Server-authorized sources only; registry membership and unlock eligibility are not disclosure grants. */
public final class QuestJeiCatalogProvider implements JeiCatalogProvider {
    @Override
    public void collect(ServerPlayer player, ArcQuestPlayer data, Consumer<JeiCatalogEntry> sink) {
        TreeSet<String> known = new TreeSet<>(data.getAllActiveQuests().keySet());
        known.addAll(data.getCompletedQuests());
        known.addAll(data.getFailedQuests());
        JeiQuestHistory history = player == null ? null : JeiQuestHistory.get(player);
        if (history != null) history.retain(player, known);
        for (String id : known) {
            QuestDefinition quest = QuestRegistry.get(id);
            QuestRuntimeData runtime = data.getActiveQuest(id);
            JeiQuestHistory.capture(player, runtime);
            if (quest != null) collectQuest(player, data, quest, runtime,
                    history == null ? JeiQuestKnowledge.KnownQuest.EMPTY : history.get(player, id), sink);
        }
    }

    static void collectQuest(ServerPlayer player, ArcQuestPlayer data, QuestDefinition quest,
                             QuestRuntimeData runtime, Consumer<JeiCatalogEntry> sink) {
        collectQuest(player, data, quest, runtime, JeiQuestKnowledge.KnownQuest.EMPTY, sink);
    }

    static void collectQuest(ServerPlayer player, ArcQuestPlayer data, QuestDefinition quest,
                             QuestRuntimeData runtime, JeiQuestKnowledge.KnownQuest known, Consumer<JeiCatalogEntry> sink) {
        String id = quest.getId().toString();
        if (!data.isQuestActive(id) && !data.isQuestCompleted(id) && !data.isQuestFailed(id)) return;
        QuestTextContext questContext = new QuestTextContext(quest, null, null, Map.of());
        Component title = quest.getDisplayName(player, questContext);
        List<Component> baseNotes = new ArrayList<>();
        baseNotes.add(Component.translatableWithFallback("arc_quest.jei.quest.source", "Quest: %s", title));
        baseNotes.add(Component.translatableWithFallback("arc_quest.jei.quest.transaction",
                "Progress, submission and rewards are validated by the server"));
        if (!quest.getUnlockConditions().isEmpty()) {
            baseNotes.add(Component.translatableWithFallback("arc_quest.jei.quest.prerequisites",
                    "Quest prerequisites apply; see the quest journal"));
        }
        if (quest.hasChapterShop()) baseNotes.add(Component.translatableWithFallback(
                "arc_quest.jei.quest.chapter_shop", "Chapter shop: %s", quest.getChapterShopId()));

        // Completed quests retain their public completion reward; unseen alternate phases stay private.
        if (!quest.isCollectionQuest()) {
            List<Component> completionNotes = new ArrayList<>(baseNotes);
            completionNotes.add(Component.translatableWithFallback("arc_quest.jei.quest.completion_reward",
                    "Reward for successful quest completion; reward policies may apply"));
            emitRewards(player, "quest/" + id + "/complete", title, quest.getCompletionRewards(),
                    completionNotes, id, "", sink);
        }
        CollectionRuntimeData collection = collectionEvidence(runtime, known);
        QuestConditionContext conditions = new QuestConditionContext(player, data.getCompletedQuestLocations(),
                data.getAllFlags(), data.getAllVariables());
        for (PhaseDefinition phase : quest.getAllPhases()) {
            if (!QuestJeiVisibility.canRevealPhase(phase, runtime) && !known.phases().contains(phase.getPhaseId())) continue;
            if (!collectionConditionsVisible(quest, phase, conditions)) continue;
            String phaseId = phase.getPhaseId();
            QuestTextContext context = new QuestTextContext(quest, phase, phaseId, Map.of());
            Component phaseTitle = phase.getDisplayName(player, context);
            List<Component> notes = new ArrayList<>(baseNotes);
            notes.add(Component.translatableWithFallback("arc_quest.jei.quest.phase", "Phase: %s", phaseTitle));
            if (phase.hasEnterCondition() || phase.hasChoices()) {
                notes.add(Component.translatableWithFallback("arc_quest.jei.quest.branch",
                        "Reached branch only; phase conditions and choices still apply"));
            }
            if (phase.hasTradeShop()) notes.add(Component.translatableWithFallback(
                    "arc_quest.jei.quest.phase_shop", "Phase shop: %s", phase.getTradeShopId()));
            int index = 0;
            for (ObjectiveEntry objective : phase.getObjectives()) {
                String objectiveKey = objective.hasObjectiveId() ? objective.getObjectiveId() : Integer.toString(index);
                index++;
                if (objective.isHidden()) continue;
                var presentation = JeiDisplayAdapters.objective(objective, player);
                if (presentation.ingredients().isEmpty()) continue;
                List<Component> objectiveNotes = new ArrayList<>(notes);
                objectiveNotes.add(objective.getDisplayText(player, context));
                objectiveNotes.addAll(presentation.notes());
                if (objective.isOptional()) objectiveNotes.add(Component.translatableWithFallback(
                        "arc_quest.jei.quest.optional", "Optional objective"));
                String npc = objective.getExtra("npc_id");
                if (npc != null && !npc.isBlank()) objectiveNotes.add(Component.translatableWithFallback(
                        "arc_quest.jei.quest.npc", "Associated NPC: %s", npc));
                // CRAFT is a recorded requirement, never a duplicate crafting recipe or item output.
                sink.accept(new JeiCatalogEntry("quest/" + id + "/phase/" + phaseId + "/objective/" + objectiveKey,
                        JeiCatalogEntry.Kind.QUEST_REQUIREMENT, phaseTitle, presentation.ingredients(), List.of(),
                        objectiveNotes, id, phaseId));
            }
            if (!quest.isCollectionQuest()) {
                List<Component> phaseRewardNotes = new ArrayList<>(notes);
                phaseRewardNotes.add(Component.translatableWithFallback("arc_quest.jei.quest.phase_reward",
                        "Reward for phase completion; reward policies may apply"));
                emitRewards(player, "quest/" + id + "/phase/" + phaseId + "/reward", phaseTitle,
                        phase.getPhaseRewards(), phaseRewardNotes, id, phaseId, sink);
            }
            if (collection != null && phase.getCollectionEntryConfig() != null) {
                emitNodes(player, collection, phase.getCollectionEntryConfig().getRewardNodes(),
                        title, notes, id, phaseId, sink);
            }
        }
        if (collection != null && quest.getCollectionConfig() != null) {
            var config = quest.getCollectionConfig();
            emitNodes(player, collection, config.getQuestRewardNodes(), title, baseNotes, id, "", sink);
            for (CollectionCategoryDefinition category : config.getCategories()) {
                boolean visible = conditionsVisible(category.getVisibilityConditions(), conditions);
                if (!visible) continue;
                List<Component> notes = new ArrayList<>(baseNotes);
                notes.add(category.getDisplayNameText().resolve(player, questContext));
                emitNodes(player, collection, category.getRewardNodes(), title, notes, id, "", sink);
            }
        }
    }

    private static CollectionRuntimeData collectionEvidence(QuestRuntimeData runtime, JeiQuestKnowledge.KnownQuest known) {
        if (runtime != null && runtime.hasCollectionData()) return runtime.getCollectionData();
        if (!known.collection()) return null;
        CollectionRuntimeData evidence = new CollectionRuntimeData();
        known.milestones().forEach(evidence::markRewardUnlocked);
        known.claimedMilestones().forEach(evidence::markRewardClaimed);
        return evidence;
    }

    private static boolean conditionsVisible(List<ICondition> conditions, QuestConditionContext context) {
        return conditions.stream().allMatch(condition -> condition != null &&
                CoreProcessors.get().conditions().evaluateSafely(condition, context, false, null, "JEI quest visibility"));
    }

    private static boolean collectionConditionsVisible(QuestDefinition quest, PhaseDefinition phase, QuestConditionContext context) {
        var entry = phase.getCollectionEntryConfig();
        if (entry == null) return true;
        if (!conditionsVisible(entry.getVisibilityConditions(), context)) return false;
        if (quest.getCollectionConfig() == null) return false;
        return quest.getCollectionConfig().getCategories().stream()
                .filter(category -> category.getCategoryId().equals(entry.getCategoryId()))
                .allMatch(category -> conditionsVisible(category.getVisibilityConditions(), context));
    }

    private static void emitNodes(ServerPlayer player, CollectionRuntimeData collection, List<CollectionRewardNode> nodes,
                                  Component title, List<Component> sourceNotes, String questId, String phaseId,
                                  Consumer<JeiCatalogEntry> sink) {
        for (CollectionRewardNode node : nodes) {
            // Unlock rules can include undiscovered entries. Never leak a locked milestone's item.
            if (!collection.isRewardUnlocked(node.getRewardNodeId()) && !collection.isRewardClaimed(node.getRewardNodeId())) continue;
            List<Component> notes = new ArrayList<>(sourceNotes);
            notes.add(Component.translatableWithFallback("arc_quest.jei.quest.milestone",
                    "Collection milestone: %s", node.getRewardNodeId()));
            notes.add(Component.translatableWithFallback(collection.isRewardClaimed(node.getRewardNodeId())
                            ? "arc_quest.jei.quest.claimed" : "arc_quest.jei.quest.unlocked",
                    collection.isRewardClaimed(node.getRewardNodeId()) ? "Already claimed" : "Unlocked reward"));
            notes.add(Component.translatableWithFallback("arc_quest.jei.quest.grant_mode",
                    "Grant mode: %s", node.getGrantMode().name()));
            emitRewards(player, "quest/" + questId + "/milestone/" + node.getRewardNodeId(), title,
                    node.getRewards(), notes, questId, phaseId, sink);
        }
    }

    private static void emitRewards(ServerPlayer player, String entryId, Component title, List<IReward> rewards,
                                    List<Component> sourceNotes, String questId, String detail, Consumer<JeiCatalogEntry> sink) {
        List<JeiIngredient> outputs = new ArrayList<>();
        List<Component> notes = new ArrayList<>(sourceNotes);
        for (IReward reward : rewards) {
            var presentation = JeiDisplayAdapters.reward(reward, player);
            outputs.addAll(presentation.ingredients());
            notes.addAll(presentation.notes());
        }
        if (!outputs.isEmpty()) sink.accept(new JeiCatalogEntry(entryId, JeiCatalogEntry.Kind.QUEST_REWARD,
                title, List.of(), outputs, notes, questId, detail));
    }
}
