package org.arcadia.arc_quest.questplayer.state;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArcQuestGuideStateTest {

    @Test
    void markAllUnlockedSeenOnlyMarksUnlockedGuides() {
        ArcQuestGuideState state = new ArcQuestGuideState();
        ResourceLocation unlocked = ResourceLocation.fromNamespaceAndPath("arc_quest", "unlocked");
        ResourceLocation locked = ResourceLocation.fromNamespaceAndPath("arc_quest", "locked");

        state.unlock(unlocked);

        assertTrue(state.markAllUnlockedSeen());
        assertTrue(state.isSeen(unlocked));
        assertFalse(state.isSeen(locked));
        assertFalse(state.markAllUnlockedSeen());
    }
}
