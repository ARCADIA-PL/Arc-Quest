package org.arcadia.arc_quest.questplayer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.dialogue.runtime.DialogueProgressStore;
import org.arcadia.arc_quest.quest.data.GachaDataStore;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.data.TradeDataStore;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerState;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ArcQuestPlayerPersistenceTest {

    @Test
    void allPlayerOwnedStateSurvivesRoundTrip() {
        UUID playerId = UUID.randomUUID();
        ArcQuestPlayer source = new ArcQuestPlayer(playerId);

        QuestRuntimeData quest = new QuestRuntimeData(
                "arc_quest:test_quest", "phase_one", 2, 120L, 10_000L, 6_000L);
        quest.setObjectiveProgress("phase_one", 0, 3);
        source.addActiveQuest(quest);
        source.markCompleted("arc_quest:completed");
        source.markFailed("arc_quest:failed");
        source.setFlag("met_blacksmith");
        source.setVariable("reputation", 17);

        ResourceLocation guideId = ResourceLocation.fromNamespaceAndPath("arc_quest", "getting_started");
        source.unlockGuide(guideId);
        source.markGuideSeen(guideId);

        QuestMarkerData marker = new QuestMarkerData.Builder(
                "marker:test", 12.5D, 64.0D, -8.25D, "??")
                .dimension("minecraft:the_nether")
                .bindQuest("arc_quest:test_quest")
                .bindPhase("phase_one")
                .bindObjective(1)
                .followEntity(42, UUID.randomUUID().toString(), "npc:blacksmith",
                        QuestMarkerData.EntityAttachPoint.CENTER)
                .color(0xFFAABBCC)
                .type(QuestMarkerType.QUEST_OBJECTIVE)
                .state(QuestMarkerState.ACTIVE)
                .showDistance(false)
                .allowOffscreenArrow(false)
                .build();
        source.upsertMarker(marker);

        source.getDialogueProgress().recordNodeVisit("arc_quest:test", "node_a", 1_000L, 200L, 300L);
        source.getDialogueProgress().recordChoiceSelection("arc_quest:test", "node_a", 2,
                1_100L, 210L, 310L);
        source.getDialogueProgress().recordDialogueVisit("arc_quest:test", "dialogue_a",
                1_200L, 220L, 320L);

        source.getTradeDataStore().incrementPurchase("shop_a", "entry_a");
        source.getTradeDataStore().recordCooldown("shop_a", "entry_a", 2_000L, 400L, 500L);
        source.incrementGachaDrawCount("gacha_a");
        source.setGachaPityCounter("gacha_a", 9);
        source.addGachaDrawHistory("gacha_a", "minecraft:diamond", "rare", 2, true, 3_000L);
        source.getGachaDataStore().recordDrawCooldown("gacha_a", 3_100L, 600L, 700L);

        CompoundTag snapshot = source.serializeNBT();
        ArcQuestPlayer restored = new ArcQuestPlayer(playerId);
        restored.deserializeNBT(snapshot);

        QuestRuntimeData restoredQuest = restored.getActiveQuest("arc_quest:test_quest");
        assertNotNull(restoredQuest);
        assertEquals(3, restoredQuest.getObjectiveProgress("phase_one", 0));
        assertTrue(restored.isQuestCompleted("arc_quest:completed"));
        assertTrue(restored.isQuestFailed("arc_quest:failed"));
        assertTrue(restored.hasFlag("met_blacksmith"));
        assertEquals(17, restored.getVariable("reputation"));
        assertTrue(restored.isGuideUnlocked(guideId));
        assertTrue(restored.isGuideSeen(guideId));

        QuestMarkerData restoredMarker = restored.getAllMarkers().get("marker:test");
        assertNotNull(restoredMarker);
        assertEquals("minecraft:the_nether", restoredMarker.getDimension());
        assertEquals("arc_quest:test_quest", restoredMarker.getQuestId());
        assertEquals("phase_one", restoredMarker.getPhaseId());
        assertEquals(1, restoredMarker.getObjectiveIndex());
        assertEquals(42, restoredMarker.getFollowEntityId());
        assertEquals("npc:blacksmith", restoredMarker.getFollowEntityGuid());
        assertEquals(QuestMarkerData.EntityAttachPoint.CENTER, restoredMarker.getAttachPoint());
        assertEquals(0xFFAABBCC, restoredMarker.getColorARGB());
        assertFalse(restoredMarker.isShowDistance());
        assertFalse(restoredMarker.isAllowOffscreenArrow());

        DialogueProgressStore dialogue = restored.getDialogueProgress();
        assertTrue(dialogue.hasVisitedNode("arc_quest:test", "node_a"));
        assertTrue(dialogue.hasSelectedChoice("arc_quest:test", "node_a", 2));
        assertTrue(dialogue.hasCompletedDialogue("arc_quest:test", "dialogue_a"));
        assertEquals(320L, dialogue.getDialogueVisit("arc_quest:test", "dialogue_a").dayTime());

        TradeDataStore trade = restored.getTradeDataStore();
        assertEquals(1, trade.getPurchaseCount("shop_a", "entry_a"));
        assertEquals(500L, trade.getCooldown("shop_a", "entry_a").dayTime());
        GachaDataStore gacha = restored.getGachaDataStore();
        assertEquals(1, gacha.getDrawCount("gacha_a"));
        assertEquals(9, gacha.getPityCounter("gacha_a"));
        assertEquals(1, gacha.getDrawHistory("gacha_a").size());
        assertEquals(700L, gacha.getDrawCooldown("gacha_a").dayTime());
        assertFalse(restored.isDirty());
    }

    @Test
    void stringDialogueApiKeepsDialogueRecordsInDialoguePartition() {
        DialogueProgressStore source = new DialogueProgressStore();
        source.recordDialogueVisit("arc_quest:test", "intro", 100L, 20L, 30L);

        CompoundTag serialized = source.serialize();
        assertTrue(serialized.getCompound("Dialogues").contains("arc_quest:test:intro"));
        assertFalse(serialized.getCompound("Nodes").contains("arc_quest:test:intro"));

        DialogueProgressStore restored = new DialogueProgressStore();
        restored.deserialize(serialized);
        assertTrue(restored.hasCompletedDialogue("arc_quest:test", "intro"));
        assertFalse(restored.isDirty());
    }
}
