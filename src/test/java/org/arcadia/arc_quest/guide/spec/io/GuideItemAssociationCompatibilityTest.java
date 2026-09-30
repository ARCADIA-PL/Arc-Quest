package org.arcadia.arc_quest.guide.spec.io;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.arcadia.arc_quest.guide.api.GuideItemAssociation;
import org.arcadia.arc_quest.guide.builder.GuideBuilder;
import org.arcadia.arc_quest.guide.builder.GuidePageBuilder;
import org.arcadia.arc_quest.guide.spec.compile.GuideSpecCompiler;
import org.arcadia.arc_quest.guide.spec.validate.GuideSpecValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assertions.*;

class GuideItemAssociationCompatibilityTest {
    private static final String BASE = "\"id\":\"test:guide\",\"title\":{\"mode\":\"literal\",\"value\":\"Guide\"},\"pages\":[{\"media\":{\"type\":\"none\"},\"description\":{\"mode\":\"literal\",\"value\":\"Page\"}}]";

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftRegistryTestBootstrap.initialize();
    }

    @Test
    void legacyGuideHasNoImplicitIconAssociation() {
        var spec = GuideSpecJsonReader.read("{" + BASE + ",\"icon\":\"minecraft:diamond\"}");
        assertTrue(spec.itemAssociations.isEmpty());
    }

    @Test
    void itemAndTagAssociationsSurviveJsonAndCompilation() {
        var spec = GuideSpecJsonReader.read("{" + BASE + ",\"itemAssociations\":[{\"item\":\"minecraft:diamond\"},{\"tag\":\"forge:ingots/iron\",\"pageIndex\":0}]}");
        assertFalse(new GuideSpecValidator().validate(spec).hasErrors());
        var restored = GuideSpecJsonReader.read(GuideSpecJsonWriter.write(spec));
        assertEquals("minecraft:diamond", restored.itemAssociations.get(0).item);
        assertEquals("forge:ingots/iron", restored.itemAssociations.get(1).tag);
        var definition = new GuideSpecCompiler().compile(restored);
        assertEquals(2, definition.getItemAssociations().size());
        assertFalse(definition.getItemAssociations().get(0).tag());
        assertTrue(definition.getItemAssociations().get(1).tag());
        assertEquals(0, definition.getItemAssociations().get(1).pageIndex());
        assertThrows(UnsupportedOperationException.class, () -> definition.getItemAssociations().clear());
    }

    @Test
    void rejectsAmbiguousMissingMalformedAndOutOfRangeAssociations() {
        for (String association : new String[]{"null", "{}",
                "{\"item\":\"minecraft:diamond\",\"tag\":\"forge:gems\"}",
                "{\"item\":\"bad id\"}",
                "{\"item\":\"minecraft:diamond\",\"pageIndex\":-1}",
                "{\"tag\":\"forge:gems\",\"pageIndex\":1}"}) {
            var spec = GuideSpecJsonReader.read("{" + BASE + ",\"itemAssociations\":[" + association + "]}");
            assertTrue(new GuideSpecValidator().validate(spec).hasErrors(), association);
        }
    }

    @Test
    void javaBuilderValidatesPageAndKeepsOldContentCompatible() {
        var id = ResourceLocation.parse("minecraft:diamond");
        assertThrows(IllegalArgumentException.class, () -> GuideItemAssociation.item(id, -1));
        var builder = GuideBuilder.create("test:guide").title("Guide")
                .page(GuidePageBuilder.create().description("Page"));
        assertTrue(builder.build().getItemAssociations().isEmpty());
        builder.associatedItem(id, 1);
        assertThrows(IllegalArgumentException.class, builder::build);
    }
}
