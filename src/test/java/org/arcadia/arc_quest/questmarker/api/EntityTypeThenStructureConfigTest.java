package org.arcadia.arc_quest.questmarker.api;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.spec.compile.QuestSpecCompiler;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonReader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityTypeThenStructureConfigTest {

    private static final ResourceLocation STRUCTURE_ID = ResourceLocation.parse("minecraft:village");
    private static final TagKey<Structure> STRUCTURE_TAG =
            TagKey.create(Registries.STRUCTURE, STRUCTURE_ID);

    @Test
    void apiDefaultsStructureRadiusToEntityRadius() {
        MarkableObject.EntityByTypeThenStructure target =
                new MarkableObject.EntityByTypeThenStructure(EntityType.VILLAGER, 48, STRUCTURE_TAG);

        assertEquals(EntityType.VILLAGER, target.type());
        assertEquals(48, target.searchRadius());
        assertEquals(STRUCTURE_TAG, target.structureTag());
        assertEquals(48, target.structureSearchRadius());
    }

    @Test
    void apiRejectsInvalidRadii() {
        assertThrows(IllegalArgumentException.class,
                () -> new MarkableObject.EntityByTypeThenStructure(EntityType.VILLAGER, 0, STRUCTURE_TAG));
        assertThrows(IllegalArgumentException.class,
                () -> new MarkableObject.EntityByTypeThenStructure(EntityType.VILLAGER, 48, STRUCTURE_TAG, 0));
    }

    @Test
    void datapackSupportsDefaultAndExplicitStructureRadius() {
        QuestDefinition quest = new QuestSpecCompiler().compile(QuestSpecJsonReader.read("""
                {
                  "id": "arc_quest:entity_then_structure_marker_test",
                  "initialPhaseId": "start",
                  "relatedMarks": [
                    {
                      "id": "default_radius",
                      "target": {
                        "type": "entity_type_then_structure",
                        "entityType": "minecraft:villager",
                        "searchRadius": 48,
                        "structureTag": "minecraft:village"
                      }
                    },
                    {
                      "id": "explicit_radius",
                      "target": {
                        "type": "entity_type_then_structure",
                        "entityType": "minecraft:villager",
                        "searchRadius": 32,
                        "structureTag": "minecraft:village",
                        "structureSearchRadius": 128
                      }
                    }
                  ],
                  "phases": [{
                    "phaseId": "start",
                    "objectives": [{"id": "noop", "type": "arc_quest:null"}]
                  }]
                }
                """));

        MarkableObject.EntityByTypeThenStructure defaultRadius =
                (MarkableObject.EntityByTypeThenStructure) quest.getRelatedMarks().get(0).target();
        MarkableObject.EntityByTypeThenStructure explicitRadius =
                (MarkableObject.EntityByTypeThenStructure) quest.getRelatedMarks().get(1).target();

        assertEquals(48, defaultRadius.structureSearchRadius());
        assertEquals(128, explicitRadius.structureSearchRadius());
    }
}
