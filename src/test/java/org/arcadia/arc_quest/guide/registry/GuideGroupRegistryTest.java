package org.arcadia.arc_quest.guide.registry;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.guide.api.GuideGroupDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GuideGroupRegistryTest {

    private static final ResourceLocation GROUP_ID = ResourceLocation.parse("arc_quest:test_group");
    private static final ResourceLocation GUIDE_ID = ResourceLocation.parse("arc_quest:test_guide");

    @BeforeEach
    void setUp() {
        GuideGroupRegistry.resetForTests();
    }

    @AfterEach
    void tearDown() {
        GuideGroupRegistry.resetForTests();
    }

    @Test
    void registeredGroupCanOwnGuideBeforeRegistryFreeze() {
        GuideGroupDefinition group = new GuideGroupDefinition(GROUP_ID, Component.literal("Test Group"));

        GuideGroupRegistry.register(group);
        GuideGroupRegistry.assign(GUIDE_ID, GROUP_ID);
        GuideGroupRegistry.assign(GUIDE_ID, GROUP_ID);

        assertSame(group, GuideGroupRegistry.getGroupForGuide(GUIDE_ID));
        assertEquals(1, GuideGroupRegistry.getAll().size());
    }

    @Test
    void assignmentRequiresKnownGroupAndCannotBeChanged() {
        ResourceLocation otherGroupId = ResourceLocation.parse("arc_quest:other_group");
        GuideGroupRegistry.register(new GuideGroupDefinition(GROUP_ID, Component.literal("Test Group")));
        GuideGroupRegistry.register(new GuideGroupDefinition(otherGroupId, Component.literal("Other Group")));

        assertThrows(IllegalStateException.class,
                () -> GuideGroupRegistry.assign(GUIDE_ID, ResourceLocation.parse("arc_quest:missing")));

        GuideGroupRegistry.assign(GUIDE_ID, GROUP_ID);
        assertThrows(IllegalStateException.class,
                () -> GuideGroupRegistry.assign(GUIDE_ID, otherGroupId));
    }

    @Test
    void freezePreventsLateRegistrationAndAssignment() {
        GuideGroupRegistry.register(new GuideGroupDefinition(GROUP_ID, Component.literal("Test Group")));
        GuideGroupRegistry.freeze();

        assertThrows(IllegalStateException.class,
                () -> GuideGroupRegistry.register(new GuideGroupDefinition(
                        ResourceLocation.parse("arc_quest:late"), Component.literal("Late"))));
        assertThrows(IllegalStateException.class,
                () -> GuideGroupRegistry.assign(GUIDE_ID, GROUP_ID));
    }
}
