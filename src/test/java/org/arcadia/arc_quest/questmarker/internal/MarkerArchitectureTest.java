package org.arcadia.arc_quest.questmarker.internal;

import net.minecraft.nbt.CompoundTag;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerState;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerType;
import org.arcadia.arc_quest.questmarker.internal.codec.MarkerNbtCodec;
import org.arcadia.arc_quest.questmarker.internal.model.MarkerOwner;
import org.arcadia.arc_quest.questmarker.internal.model.MarkerPersistence;
import org.arcadia.arc_quest.questmarker.internal.runtime.MarkerReconciliationEngine;
import org.arcadia.arc_quest.questmarker.internal.store.PlayerMarkerStore;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkerArchitectureTest {

    @Test
    void publicMarkerRoundTripsThroughInternalModel() {
        QuestMarkerData original = new QuestMarkerData.Builder("addon:marker", 1.5, 64, -2.5, "Target")
                .dimension("minecraft:the_nether")
                .bindQuest("addon:quest").bindPhase("phase_a").bindObjective(2)
                .followEntity(42, UUID.randomUUID().toString(), "npc-guid", QuestMarkerData.EntityAttachPoint.CENTER)
                .type(QuestMarkerType.QUEST_OBJECTIVE).state(QuestMarkerState.ACTIVE).color(0xFFAABBCC)
                .showDistance(false).allowOffscreenArrow(false).priority(7)
                .styleHints(Map.of("custom", "value")).persistent(true).build();

        var snapshot = MarkerModelAdapter.fromPublic(original);

        assertEquals(MarkerPersistence.DURABLE, snapshot.persistence());
        assertInstanceOf(MarkerOwner.Objective.class, snapshot.owner());
        assertEquals(original, MarkerModelAdapter.toPublic(snapshot));
    }

    @Test
    void nbtPersistsOnlyDurableMarkersAndOneShotHistory() {
        PlayerMarkerStore source = new PlayerMarkerStore(new LinkedHashMap<>(), new LinkedHashSet<>());
        source.upsert(marker("addon:durable", true));
        source.upsert(marker("aq:auto:addon:quest:phase:marker", true));
        source.consumeOneShot("aq:trigger:quest:addon:quest:accepted:once");

        CompoundTag root = new CompoundTag();
        MarkerNbtCodec.writeToRoot(root, source);

        PlayerMarkerStore restored = new PlayerMarkerStore(new LinkedHashMap<>(), new LinkedHashSet<>());
        MarkerNbtCodec.readFromRoot(root, restored, QuestMarkerData.EntityAttachPoint::valueOf);

        assertTrue(restored.publicView().containsKey("addon:durable"));
        assertFalse(restored.publicView().containsKey("aq:auto:addon:quest:phase:marker"));
        assertTrue(restored.isOneShotConsumed("aq:trigger:quest:addon:quest:accepted:once"));
        assertFalse(restored.isDirty());
    }

    @Test
    void reconciliationIsIdempotentAndScopeBounded() {
        ArcQuestPlayer playerData = new ArcQuestPlayer(UUID.randomUUID());
        QuestMarkerData oldManaged = marker("quest:addon:quest:old:0", false);
        QuestMarkerData unrelated = marker("addon:manual", true);
        QuestMarkerData desired = marker("quest:addon:quest:new:0", false);
        playerData.upsertMarker(oldManaged);
        playerData.upsertMarker(unrelated);

        var first = MarkerReconciliationEngine.reconcile(playerData,
                marker -> marker.getId().startsWith("quest:addon:quest:"), List.of(desired));
        var second = MarkerReconciliationEngine.reconcile(playerData,
                marker -> marker.getId().startsWith("quest:addon:quest:"), List.of(desired));

        assertTrue(first.changed());
        assertFalse(second.changed());
        assertTrue(playerData.getAllMarkers().containsKey(desired.getId()));
        assertTrue(playerData.getAllMarkers().containsKey(unrelated.getId()));
        assertFalse(playerData.getAllMarkers().containsKey(oldManaged.getId()));
    }

    private static QuestMarkerData marker(String id, boolean persistent) {
        return new QuestMarkerData.Builder(id, 1, 2, 3, id)
                .bindQuest("addon:quest")
                .type(QuestMarkerType.CUSTOM)
                .persistent(persistent)
                .build();
    }
}
