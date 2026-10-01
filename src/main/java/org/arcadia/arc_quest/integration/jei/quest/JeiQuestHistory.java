package org.arcadia.arc_quest.integration.jei.quest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.api.event.quest.QuestAcceptedEvent;
import org.arcadia.arc_quest.api.event.quest.QuestPhaseActivatedEvent;
import org.arcadia.arc_quest.api.event.quest.QuestPhaseCompletedEvent;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

import java.util.HashSet;
import java.util.Set;

/** Overworld-scoped history survives quest runtime removal and works without the JEI mod installed. */
@EventBusSubscriber(modid = Arc_Quest.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class JeiQuestHistory extends SavedData {
    private static final String DATA_NAME = "arc_quest_jei_known_sources";
    private final JeiQuestKnowledge knowledge;

    public JeiQuestHistory() { this(new JeiQuestKnowledge()); }
    private JeiQuestHistory(JeiQuestKnowledge knowledge) { this.knowledge = knowledge; }

    public static JeiQuestHistory get(ServerPlayer player) {
        return player.serverLevel().getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(JeiQuestHistory::new, (tag, registries) -> new JeiQuestHistory(JeiQuestKnowledge.load(tag))), DATA_NAME);
    }

    public JeiQuestKnowledge.KnownQuest get(ServerPlayer player, String questId) {
        return knowledge.get(player.getUUID(), questId);
    }

    public void retain(ServerPlayer player, Set<String> questIds) {
        if (knowledge.retainQuests(player.getUUID(), questIds)) setDirty();
    }

    /** Also called with terminal runtimes by QuestSyncCoordinator before they leave the server. */
    public static void capture(ServerPlayer player, QuestRuntimeData runtime) {
        if (player == null || runtime == null) return;
        QuestDefinition definition = QuestRegistry.get(runtime.getQuestId());
        if (definition == null) return;
        Set<String> phases = new HashSet<>();
        definition.getAllPhases().forEach(phase -> {
            if (QuestJeiVisibility.canRevealPhase(phase, runtime)) phases.add(phase.getPhaseId());
        });
        Set<String> milestones = runtime.hasCollectionData() ? runtime.getCollectionData().getUnlockedRewardIds() : Set.of();
        Set<String> claimed = runtime.hasCollectionData() ? runtime.getCollectionData().getClaimedRewardIds() : Set.of();
        JeiQuestHistory history = get(player);
        if (history.knowledge.remember(player.getUUID(), runtime.getQuestId(), phases, milestones, claimed, runtime.hasCollectionData())) {
            history.setDirty();
        }
    }

    private static void captureCurrent(ServerPlayer player, String questId) {
        var data = ArcQuestPlayerManager.get(player);
        if (data != null) capture(player, data.getActiveQuest(questId));
    }

    @SubscribeEvent
    public static void onAccepted(QuestAcceptedEvent event) { captureCurrent(event.getPlayer(), event.getQuestId().toString()); }
    @SubscribeEvent
    public static void onActivated(QuestPhaseActivatedEvent event) { captureCurrent(event.getPlayer(), event.getQuestId().toString()); }
    @SubscribeEvent
    public static void onCompleted(QuestPhaseCompletedEvent event) { captureCurrent(event.getPlayer(), event.getQuestId().toString()); }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) { return root.merge(knowledge.save()); }
}
