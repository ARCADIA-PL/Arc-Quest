package org.arcadia.arc_quest.quest.registry;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.QuestGroupDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuestGroupRegistryTest {

    private static final ResourceLocation GROUP_ID = ResourceLocation.parse("arc_quest:test_group");
    private static final ResourceLocation QUEST_ID = ResourceLocation.parse("arc_quest:test_quest");

    @BeforeEach
    void setUp() {
        QuestGroupRegistry.resetForTests();
    }

    @AfterEach
    void tearDown() {
        QuestGroupRegistry.resetForTests();
    }

    @Test
    void registeredGroupCanOwnQuestBeforeRegistryFreeze() {
        QuestGroupDefinition group = new QuestGroupDefinition(GROUP_ID, Component.literal("Test Group"));

        QuestGroupRegistry.register(group);
        QuestGroupRegistry.assign(QUEST_ID, GROUP_ID);
        QuestGroupRegistry.assign(QUEST_ID, GROUP_ID);

        assertSame(group, QuestGroupRegistry.getGroupForQuest(QUEST_ID));
        assertEquals(1, QuestGroupRegistry.getAll().size());
    }

    @Test
    void assignmentRequiresKnownGroupAndCannotBeChanged() {
        ResourceLocation otherGroupId = ResourceLocation.parse("arc_quest:other_group");
        QuestGroupRegistry.register(new QuestGroupDefinition(GROUP_ID, Component.literal("Test Group")));
        QuestGroupRegistry.register(new QuestGroupDefinition(otherGroupId, Component.literal("Other Group")));

        assertThrows(IllegalStateException.class,
                () -> QuestGroupRegistry.assign(QUEST_ID, ResourceLocation.parse("arc_quest:missing")));

        QuestGroupRegistry.assign(QUEST_ID, GROUP_ID);
        assertThrows(IllegalStateException.class,
                () -> QuestGroupRegistry.assign(QUEST_ID, otherGroupId));
    }

    @Test
    void freezePreventsLateRegistrationAndAssignment() {
        QuestGroupRegistry.register(new QuestGroupDefinition(GROUP_ID, Component.literal("Test Group")));
        QuestGroupRegistry.freeze();

        assertThrows(IllegalStateException.class,
                () -> QuestGroupRegistry.register(new QuestGroupDefinition(
                        ResourceLocation.parse("arc_quest:late"), Component.literal("Late"))));
        assertThrows(IllegalStateException.class,
                () -> QuestGroupRegistry.assign(QUEST_ID, GROUP_ID));
    }
}
