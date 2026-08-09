package org.arcadia.arc_quest.questmarker.api;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.spec.compile.QuestCompileException;
import org.arcadia.arc_quest.quest.spec.compile.QuestSpecCompiler;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonReader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureNearestConfigTest {

    private static final ResourceLocation STRUCTURE_ID =
            ResourceLocation.parse("minecraft:village");
    private static final TagKey<Structure> STRUCTURE_TAG =
            TagKey.create(Registries.STRUCTURE, STRUCTURE_ID);

    @Test
    void legacyConstructorAndFactoryKeepLocatedY() {
        MarkableObject.StructureNearest constructed =
                new MarkableObject.StructureNearest(STRUCTURE_TAG, 128);
        MarkableObject.StructureNearest created =
                MarkableObject.StructureNearest.of(STRUCTURE_ID, 128);

        assertNull(constructed.y());
        assertFalse(constructed.useSurfaceY());
        assertEquals(constructed, created);
    }

    @Test
    void newApisConfigureFixedOrSurfaceY() {
        MarkableObject.StructureNearest fixed =
                new MarkableObject.StructureNearest(STRUCTURE_TAG, 128, 72);
        MarkableObject.StructureNearest surface =
                MarkableObject.StructureNearest.atSurface(STRUCTURE_ID, 128);

        assertEquals(72, fixed.y());
        assertFalse(fixed.useSurfaceY());
        assertNull(surface.y());
        assertTrue(surface.useSurfaceY());
        assertThrows(IllegalArgumentException.class,
                () -> new MarkableObject.StructureNearest(STRUCTURE_TAG, 128, 72, true));
    }

    @Test
    void datapackSupportsOptionalFixedAndSurfaceY() {
        QuestDefinition quest = new QuestSpecCompiler().compile(QuestSpecJsonReader.read("""
                {
                  "id": "arc_quest:structure_marker_y_test",
                  "initialPhaseId": "start",
                  "relatedMarks": [
                    {
                      "id": "fixed",
                      "target": {
                        "type": "structure_nearest",
                        "structureTag": "minecraft:village",
                        "searchRadius": 128,
                        "y": 90
                      }
                    },
                    {
                      "id": "surface",
                      "target": {
                        "type": "structure_nearest",
                        "structureTag": "minecraft:village",
                        "searchRadius": 128,
                        "useSurfaceY": true
                      }
                    }
                  ],
                  "phases": [{
                    "phaseId": "start",
                    "objectives": [{"id": "noop", "type": "arc_quest:null"}]
                  }]
                }
                """));

        MarkableObject.StructureNearest fixed =
                (MarkableObject.StructureNearest) quest.getRelatedMarks().get(0).target();
        MarkableObject.StructureNearest surface =
                (MarkableObject.StructureNearest) quest.getRelatedMarks().get(1).target();
        assertEquals(90, fixed.y());
        assertFalse(fixed.useSurfaceY());
        assertNull(surface.y());
        assertTrue(surface.useSurfaceY());
    }

    @Test
    void datapackRejectsConflictingVerticalModes() {
        assertThrows(QuestCompileException.class, () ->
                new QuestSpecCompiler().compile(QuestSpecJsonReader.read("""
                        {
                          "id": "arc_quest:invalid_structure_marker_y_test",
                          "initialPhaseId": "start",
                          "relatedMarks": [{
                            "id": "invalid",
                            "target": {
                              "type": "structure_nearest",
                              "structureTag": "minecraft:village",
                              "searchRadius": 128,
                              "y": 90,
                              "useSurfaceY": true
                            }
                          }],
                          "phases": [{
                            "phaseId": "start",
                            "objectives": [{"id": "noop", "type": "arc_quest:null"}]
                          }]
                        }
                        """)));
    }
}
