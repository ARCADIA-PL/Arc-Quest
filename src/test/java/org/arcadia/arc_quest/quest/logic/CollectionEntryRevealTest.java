package org.arcadia.arc_quest.quest.logic;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.CollectionRecordState;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionUnreadState;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CollectionEntryRevealTest {
    private static final ResourceLocation ENTRY = ResourceLocation.parse("test:weapon");
    @BeforeAll static void setup() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void revealOpensTheCardAndCategoryWithoutCompletingCollection() {
        var entry = CollectionEntryBuilder.create(ENTRY).category("swords")
                .visibility(VisibilityMode.HIDDEN_BY_DEFAULT, HiddenPresentationMode.FULLY_HIDDEN).build();
        var quest = QuestBuilder.create("test:weapons").mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("swords", "Swords").entry(entry).build())
                .phase(PhaseBuilder.create("catalogue").collectionSheet(CollectionSheetBuilder.create()
                        .binding(EntryRequirementBuilder.create("weapon", ENTRY).discovered()))).build();
        var records = new CollectionRecordState();
        var before = CollectionProgressProjector.project(quest, quest.getPhase("catalogue"), null, records);
        assertFalse(before.bindings().get(0).visible());
        assertTrue(before.categories().isEmpty());
        var oldGrant = org.arcadia.arc_quest.data.sync.DatapackContentSyncService.DisclosureGrant.of(entry, records);
        assertTrue(records.reveal(ENTRY));
        var newGrant = org.arcadia.arc_quest.data.sync.DatapackContentSyncService.DisclosureGrant.of(entry, records);
        assertNotEquals(oldGrant, newGrant, "A material reveal must invalidate the recipient content cache");
        assertTrue(newGrant.revealed()); assertFalse(newGrant.discovered());
        var opened = CollectionProgressProjector.project(quest, quest.getPhase("catalogue"), null, records);
        assertTrue(opened.bindings().get(0).revealed());
        assertTrue(CollectionUnreadState.isUnread(opened.bindings().get(0), records.getRecord(ENTRY)));
        records.markSeen(ENTRY, "entry");
        assertFalse(CollectionUnreadState.isUnread(opened.bindings().get(0), records.getRecord(ENTRY)));
        assertFalse(opened.bindings().get(0).discovered());
        assertFalse(opened.bindings().get(0).complete());
        assertEquals("swords", opened.categories().get(0).categoryId());
        assertFalse(records.reveal(ENTRY));
        records.discover(ENTRY);
        assertTrue(CollectionUnreadState.isUnread(opened.bindings().get(0), records.getRecord(ENTRY)),
                "Collecting a previously read material-unlocked weapon is a new update");
        assertTrue(CollectionProgressProjector.project(quest, quest.getPhase("catalogue"), null, records)
                .bindings().get(0).complete());
    }

    @Test void researchUpdatesBecomeUnreadWithoutRepeatedProgressProducingNewNotifications() {
        var records = new CollectionRecordState(); records.reveal(ENTRY); records.markSeen(ENTRY, "entry");
        assertTrue(records.increment(ENTRY, "research:sample", 1, 2));
        assertFalse(records.getRecord(ENTRY).isSeen());
        records.markSeen(ENTRY, "entry");
        assertFalse(records.importProgress(ENTRY, "research:sample", 1, 2));
        assertTrue(records.getRecord(ENTRY).isSeen());
        var restored = new CollectionRecordState(); restored.readSnapshot(records.serializeNBT());
        assertTrue(restored.getRecord(ENTRY).isSeen());
        records.increment(ENTRY, "research:sample", 1, 2);
        restored.applyDelta(records.serializeEntries(java.util.Set.of(ENTRY)));
        assertFalse(restored.getRecord(ENTRY).isSeen());
    }

    @Test void revealSurvivesSnapshotsAndDeltasAndIsRemovedByAnExplicitReset() {
        var records = new CollectionRecordState();
        records.reveal(ENTRY);
        assertTrue(records.markSeen(ENTRY, "entry"));
        var restored = new CollectionRecordState(); restored.readSnapshot(records.serializeNBT());
        assertTrue(restored.isRevealed(ENTRY));
        assertFalse(restored.isDiscovered(ENTRY));
        assertTrue(restored.getRecord(ENTRY).isSeen());
        assertEquals(records.serializeNBT(), restored.serializeNBT());
        var clientDelta = new CollectionRecordState(); clientDelta.applyDelta(records.serializeEntries(java.util.Set.of(ENTRY)));
        assertTrue(clientDelta.isRevealed(ENTRY)); assertFalse(clientDelta.isDiscovered(ENTRY));
        var other = new CollectionRecordState();
        assertFalse(other.isRevealed(ENTRY));
        restored.resetQuest("test:weapons", java.util.Set.of(ENTRY));
        assertFalse(restored.isRevealed(ENTRY));
        assertFalse(restored.isDiscovered(ENTRY));
    }
}
