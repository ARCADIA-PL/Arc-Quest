package org.arcadia.arc_quest.guide.spec;

import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.spec.compile.GuideSpecCompiler;
import org.arcadia.arc_quest.guide.spec.io.GuideSpecJsonReader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideSpecCompatibilityTest {

    @Test
    void legacyJsonWithoutVisualFieldsStillCompiles() {
        GuideSpec spec = GuideSpecJsonReader.read("""
                {
                  "id": "arc_quest:legacy",
                  "category": "arc_quest:basics",
                  "title": {"mode": "literal", "value": "Legacy"},
                  "pages": [{
                    "media": {"type": "none"},
                    "description": {"mode": "literal", "value": "Body"}
                  }]
                }
                """);

        GuideDefinition guide = new GuideSpecCompiler().compile(spec);

        assertEquals("Legacy", guide.getTitle().getString());
        assertFalse(guide.getVisualConfig().hasIcon());
        assertFalse(guide.getVisualConfig().shouldShowUnlockPopup());
    }

    @Test
    void visualFieldsCompileIntoGuideDefinition() {
        GuideSpec spec = GuideSpecJsonReader.read("""
                {
                  "id": "arc_quest:diamond",
                  "category": "arc_quest:basics",
                  "title": {"mode": "literal", "value": "Diamond"},
                  "summary": {"mode": "literal", "value": "Found one"},
                  "icon": "minecraft:diamond",
                  "renderLargeIconOnIntro": true,
                  "showUnlockPopup": true,
                  "pages": [{
                    "media": {"type": "none"},
                    "description": {"mode": "literal", "value": "Details"}
                  }]
                }
                """);

        GuideDefinition guide = new GuideSpecCompiler().compile(spec);

        assertEquals("Found one", guide.getSummary().getString());
        assertTrue(guide.getVisualConfig().shouldRenderLargeIconOnIntro());
        assertTrue(guide.getVisualConfig().shouldShowUnlockPopup());
        assertEquals(Items.DIAMOND, guide.getVisualConfig().getIcon().getItem());
    }
}
