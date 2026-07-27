package org.arcadia.arc_quest.quest.api;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.builder.PhaseBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhaseGuideGrantConfigTest {

    @Test
    void builderKeepsGuideGrantsForEnterAndComplete() {
        ResourceLocation enterGuide = ResourceLocation.parse("arc_quest:enter_guide");
        ResourceLocation completeGuide = ResourceLocation.parse("arc_quest:complete_guide");

        PhaseDefinition phase = PhaseBuilder.create("start")
                .displayName("Start")
                .objective(ObjectiveBuilder.nullObjective().display("Continue"))
                .grantGuideOnEnter(enterGuide)
                .grantGuideOnComplete(completeGuide)
                .build();

        assertEquals(java.util.List.of(enterGuide), phase.getGuidesToGrantOnEnter());
        assertEquals(java.util.List.of(completeGuide), phase.getGuidesToGrantOnComplete());
    }
}
