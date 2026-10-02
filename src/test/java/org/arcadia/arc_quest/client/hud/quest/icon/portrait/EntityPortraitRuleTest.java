package org.arcadia.arc_quest.client.hud.quest.icon.portrait;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class EntityPortraitRuleTest {
    private static final String BASIC = """
            {"type":"arc_quest:entity_texture_portrait","texture":"test:textures/beast.png",
             "referenceSize":{"width":64,"height":32},"canvasSize":{"width":8,"height":8},
             "layers":[{"region":{"x":8,"y":8,"width":8,"height":8},
                          "destination":{"x":0,"y":0,"width":8,"height":8}}]}
            """;

    @Test void resourceRulesCannotRecurseIntoProvidersOrAuto() {
        assertEquals(EntityPortraitRule.NONE, EntityPortraitRule.parse(JsonParser.parseString("{\"type\":\"arc_quest:none\"}")));
        for (String type : new String[]{"arc_quest:auto", "arc_quest:provider", "unknown:portrait"}) {
            assertThrows(IllegalArgumentException.class, () -> EntityPortraitRule.parse(
                    JsonParser.parseString("{\"type\":\"" + type + "\"}")));
        }
    }

    @Test void textureOverrideMayUseWholeResourceOrAnExplicitRegion() {
        var whole = EntityPortraitRule.parse(JsonParser.parseString("{\"type\":\"arc_quest:texture\",\"texture\":\"test:portrait.png\"}"));
        assertEquals(EntityPortraitRule.Kind.TEXTURE, whole.kind());
        assertNull(whole.region());
        var cropped = EntityPortraitRule.parse(JsonParser.parseString("""
                {"type":"arc_quest:texture","texture":"test:portrait.png",
                 "region":{"x":4,"y":2,"width":8,"height":6}}
                """));
        assertEquals(new TexturePortraitDefinition.Rect(4, 2, 8, 6), cropped.region());
    }

    @Test void fullDefinitionIsValidatedBeforeAnyLayerCanRender() {
        JsonObject source = JsonParser.parseString(BASIC).getAsJsonObject();
        var badLayer = source.getAsJsonArray("layers").get(0).deepCopy().getAsJsonObject();
        badLayer.getAsJsonObject("region").addProperty("width", 100);
        source.getAsJsonArray("layers").add(badLayer);
        JsonObject invalidLayers = source;
        assertThrows(IllegalArgumentException.class, () -> EntityPortraitRule.parse(invalidLayers));
        source = JsonParser.parseString(BASIC).getAsJsonObject();
        source.getAsJsonArray("layers").get(0).getAsJsonObject().addProperty("flipX", "true");
        JsonObject badBoolean = source;
        assertThrows(IllegalArgumentException.class, () -> EntityPortraitRule.parse(badBoolean));
    }

    @Test void fractionalCoordinatesAndExcessLayersAreRejected() {
        var fraction = JsonParser.parseString(BASIC.replace("\"x\":8", "\"x\":8.5"));
        assertThrows(IllegalArgumentException.class, () -> EntityPortraitRule.parse(fraction));
        var excessive = JsonParser.parseString(BASIC).getAsJsonObject();
        var layers = excessive.getAsJsonArray("layers");
        while (layers.size() <= TexturePortraitDefinition.MAX_LAYERS) layers.add(layers.get(0).deepCopy());
        assertThrows(IllegalArgumentException.class, () -> EntityPortraitRule.parse(excessive));
    }

    @Test void tintAndMirrorsAreExplicitAndPreserved() {
        var root = JsonParser.parseString(BASIC).getAsJsonObject();
        var layer = root.getAsJsonArray("layers").get(0).getAsJsonObject();
        layer.addProperty("flipX", true);
        layer.addProperty("tint", "#80AA5500");
        var parsed = EntityPortraitRule.parse(root).portrait().layers().get(0);
        assertTrue(parsed.flipX());
        assertFalse(parsed.flipY());
        assertEquals(0x80AA5500, parsed.tint());
        layer.addProperty("tint", 4294967295L);
        assertEquals(0xFFFFFFFF, EntityPortraitRule.parse(root).portrait().layers().get(0).tint());
        for (String invalid : new String[]{"-1", "4294967296", "2.5"}) {
            layer.add("tint", JsonParser.parseString(invalid));
            assertThrows(IllegalArgumentException.class, () -> EntityPortraitRule.parse(root));
        }
    }

    @Test void shippedCowUvMatchesTheActual1201HeadAndHornCubes() throws Exception {
        var cow = builtin("cow");
        assertEquals("minecraft:textures/entity/cow/cow.png", cow.texture().toString());
        assertEquals(new TexturePortraitDefinition.Size(64, 32), cow.referenceSize());
        // CowModel.createBodyLayer: face texOffs(0,0), size(8,8,6). ModelPart NORTH starts at u+depth,v+depth.
        assertEquals(new TexturePortraitDefinition.Rect(6, 6, 8, 8), cow.layers().get(2).region());
        // Horns texOffs(22,0), size(1,3,1), at model x=-5 and x=4, y=-5.
        assertEquals(new TexturePortraitDefinition.Rect(23, 1, 1, 3), cow.layers().get(0).region());
        assertEquals(new TexturePortraitDefinition.Rect(0, 0, 1, 3), cow.layers().get(0).destination());
        assertEquals(new TexturePortraitDefinition.Rect(9, 0, 1, 3), cow.layers().get(1).destination());
        assertEquals(new TexturePortraitDefinition.Rect(1, 1, 8, 8), cow.layers().get(2).destination());
    }

    @Test void shippedPigUvIncludesTheRealSnoutAsTheTopLayer() throws Exception {
        var pig = builtin("pig");
        assertEquals("minecraft:textures/entity/pig/pig.png", pig.texture().toString());
        // PigModel.createBodyLayer: head texOffs(0,0), size(8,8,8); snout texOffs(16,16), size(4,3,1).
        assertEquals(new TexturePortraitDefinition.Rect(8, 8, 8, 8), pig.layers().get(0).region());
        assertEquals(new TexturePortraitDefinition.Rect(17, 17, 4, 3), pig.layers().get(1).region());
        assertEquals(new TexturePortraitDefinition.Rect(2, 4, 4, 3), pig.layers().get(1).destination());
        assertTrue(pig.acceptsTextureSize(256, 128));
        assertFalse(pig.acceptsTextureSize(256, 256));
    }

    private static TexturePortraitDefinition builtin(String name) throws Exception {
        try (var stream = EntityPortraitRuleTest.class.getResourceAsStream("/assets/arc_quest/objective_icons/portraits/" + name + ".json")) {
            assertNotNull(stream);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return EntityPortraitRule.parse(JsonParser.parseReader(reader)).portrait();
            }
        }
    }

    @Test void shippedSpiderFacesUseTheHeadFrontAndSeparateSpeciesSkins() throws Exception {
        // Both SpiderModel.createSpiderBodyLayer implementations use head texOffs(32,4)
        // and size(8,8,8): NORTH is (u+depth,v+depth), not the body's (8,8).
        for (String species : new String[]{"spider", "cave_spider"}) {
            var portrait = builtin(species);
            assertEquals("minecraft:textures/entity/spider/" + species + ".png", portrait.texture().toString());
            assertEquals(new TexturePortraitDefinition.Size(64, 32), portrait.referenceSize());
            assertEquals(new TexturePortraitDefinition.Size(8, 8), portrait.canvasSize());
            assertEquals(1, portrait.layers().size());
            var face = portrait.layers().get(0);
            assertEquals(new TexturePortraitDefinition.Rect(40, 12, 8, 8), face.region());
            assertEquals(new TexturePortraitDefinition.Rect(0, 0, 8, 8), face.destination());
            assertFalse(face.flipX()); assertFalse(face.flipY());
            assertEquals(0xFFFFFFFF, face.tint());
            assertTrue(portrait.acceptsTextureSize(64, 32));
            assertTrue(portrait.acceptsTextureSize(256, 128));
            assertFalse(portrait.acceptsTextureSize(256, 256));
        }
        assertNotEquals(builtin("spider").texture(), builtin("cave_spider").texture());
    }

    @Test void shippedSpiderUvFilesAreRegisteredAsAutomaticTextureSources() throws Exception {
        // A valid resource alone cannot fix a missing AUTO registration.
        // Read only the default declarations; no world, model, or GPU baking is involved.
        var declaration = EntityPortraits.class.getDeclaredField("BUILTIN_TEXTURE_FILES");
        declaration.setAccessible(true);
        var defaults = (java.util.Map<?, ?>) declaration.get(null);
        for (String species : new String[]{"spider", "cave_spider"}) {
            var entityId = net.minecraft.resources.ResourceLocation.parse("minecraft:" + species);
            assertEquals(net.minecraft.resources.ResourceLocation.parse("arc_quest:objective_icons/portraits/" + species + ".json"),
                    defaults.get(entityId));
        }
    }
}
