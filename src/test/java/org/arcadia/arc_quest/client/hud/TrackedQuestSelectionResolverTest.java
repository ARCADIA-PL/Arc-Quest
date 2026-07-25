package org.arcadia.arc_quest.client.hud;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TrackedQuestSelectionResolverTest {

    @Test
    void keepsPersistedSelectionUntilFullSyncArrives() {
        assertEquals("arc_quest:second", TrackedQuestSelectionResolver.resolve(
                "arc_quest:second", List.of(), false));
    }

    @Test
    void restoresPersistedSelectionAfterFullSync() {
        assertEquals("arc_quest:second", TrackedQuestSelectionResolver.resolve(
                "arc_quest:second", List.of("arc_quest:first", "arc_quest:second"), true));
    }

    @Test
    void fallsBackOnlyAfterFullSyncConfirmsSelectionIsInvalid() {
        assertEquals("arc_quest:first", TrackedQuestSelectionResolver.resolve(
                "arc_quest:missing", List.of("arc_quest:first", "arc_quest:second"), true));
        assertNull(TrackedQuestSelectionResolver.resolve("arc_quest:missing", List.of(), true));
    }
}
