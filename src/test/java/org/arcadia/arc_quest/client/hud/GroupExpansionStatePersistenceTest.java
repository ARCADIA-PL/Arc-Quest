package org.arcadia.arc_quest.client.hud;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupExpansionStatePersistenceTest {

    @Test
    void persistsJournalAndGuideScopesSeparately() throws Exception {
        Path file = Files.createTempFile("arc-quest-group-expansion", ".json");
        try {
            ResourceLocation groupId = ResourceLocation.fromNamespaceAndPath("arc_quest", "basics");
            GroupExpansionStatePersistence first = GroupExpansionStatePersistence.forFile(file);
            first.setExpanded(GroupExpansionStatePersistence.JOURNAL_SCOPE, groupId, false);
            first.setExpanded(GroupExpansionStatePersistence.GUIDE_SCOPE, groupId, true);

            GroupExpansionStatePersistence second = GroupExpansionStatePersistence.forFile(file);
            assertFalse(second.isExpanded(GroupExpansionStatePersistence.JOURNAL_SCOPE, groupId));
            assertTrue(second.isExpanded(GroupExpansionStatePersistence.GUIDE_SCOPE, groupId));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".tmp"));
        }
    }
}
