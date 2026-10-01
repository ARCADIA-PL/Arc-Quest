package org.arcadia.arc_quest.quest.tracking;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.builder.PhaseBuilder;
import org.arcadia.arc_quest.quest.builder.QuestBuilder;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ObjectiveTypeIndexTest {
    @BeforeAll static void bootstrap() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void indexesDirectTargetsByTypeAndDoesNotFallbackFromMissingTags() {
        var definition = QuestBuilder.create("arc_quest:index_test")
                .phase(PhaseBuilder.create("phase")
                        .objective(ObjectiveBuilder.collect(Items.APPLE, 10))
                        .objective(ObjectiveBuilder.craft(Items.APPLE, 10))
                        .objective(ObjectiveBuilder.collect(Items.APPLE, 10)
                                .extra("target_tag", "arc_quest:missing_test_tag")))
                .build();
        var index = ObjectiveTypeIndex.build(Map.of(definition.getId(), definition));
        var apple = ResourceLocation.parse("minecraft:apple");
        assertEquals(List.of(new ObjectiveTypeIndex.ObjectiveRef(definition.getId(), "phase", 0)),
                index.find(ObjectiveType.COLLECT, apple));
        assertEquals(List.of(new ObjectiveTypeIndex.ObjectiveRef(definition.getId(), "phase", 1)),
                index.find(ObjectiveType.CRAFT, apple));
        assertNull(index.find(ObjectiveType.OFFER, apple));
        assertNull(index.find(ObjectiveType.COLLECT, ResourceLocation.parse("minecraft:stick")));
    }
}
