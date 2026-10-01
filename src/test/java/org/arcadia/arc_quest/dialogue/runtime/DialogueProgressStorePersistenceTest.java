package org.arcadia.arc_quest.dialogue.runtime;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueProgressStorePersistenceTest {
    @Test
    void stringRecordsPreserveTheirTypesAcrossReload() {
        DialogueProgressStore store = new DialogueProgressStore();
        store.recordNodeVisit("addon:npc", "123", 10, 20, 30);
        store.recordChoiceSelection("addon:npc", "start", 0, 10, 20, 30);
        store.recordDialogueVisit("addon:npc", "addon:story", 10, 20, 30);
        CompoundTag saved = store.serialize();
        assertEquals(1, saved.getCompound("Nodes").getAllKeys().size());
        assertEquals(1, saved.getCompound("Choices").getAllKeys().size());
        assertEquals(1, saved.getCompound("Dialogues").getAllKeys().size());

        store.deserialize(saved);
        assertFalse(store.isDirty());
        assertTrue(store.hasVisitedNode("addon:npc", "123"));
        assertTrue(store.getDialogueVisit("addon:npc", "addon:story").exists());
        assertEquals(saved, store.serialize());
    }

    @Test
    void legacyMigrationReplacesPreviousStateAndRetainsDialogueType() {
        DialogueProgressStore store = new DialogueProgressStore();
        store.recordNodeVisit("addon:npc", "stale", 1, 2, 3);
        CompoundTag legacy = new CompoundTag();
        CompoundTag history = new CompoundTag();
        history.putLong("addon:npc:addon:story", 10);
        legacy.put("DialogueHistory", history);
        store.migrateFromLegacy(legacy);
        assertTrue(store.isDirty());
        assertTrue(store.serialize().getCompound("Nodes").isEmpty());
        assertEquals(1, store.serialize().getCompound("Dialogues").getAllKeys().size());
    }
}
