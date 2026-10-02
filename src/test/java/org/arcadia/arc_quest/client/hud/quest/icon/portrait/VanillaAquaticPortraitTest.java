package org.arcadia.arc_quest.client.hud.quest.icon.portrait;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VanillaAquaticPortraitTest {
    private static final String[] COMMON_SPECIES = {
            "cod", "salmon", "pufferfish", "tropical_fish", "squid", "glow_squid",
            "dolphin", "guardian", "elder_guardian", "axolotl", "turtle", "warden"
    };

    @Test void everyShippedPortraitParsesAndKeepsTheSourceAspectRatioForResourcePacks() throws Exception {
        for (String species : COMMON_SPECIES) {
            var portrait = builtin(species);
            assertTrue(portrait.acceptsTextureSize(portrait.referenceSize().width(), portrait.referenceSize().height()), species);
            assertTrue(portrait.acceptsTextureSize(portrait.referenceSize().width() * 4, portrait.referenceSize().height() * 4), species);
            assertFalse(portrait.acceptsTextureSize(portrait.referenceSize().width(), portrait.referenceSize().height() + 1), species);
            assertFalse(portrait.acceptsTextureSize(0, portrait.referenceSize().height()), species);
            for (var layer : portrait.layers()) {
                assertTrue(layer.region().fits(portrait.referenceSize()), species);
                assertTrue(layer.destination().fits(portrait.canvasSize()), species);
                assertEquals(0xFFFFFFFF, layer.tint(), species);
            }
        }
    }

    @Test void narrowFishUseTheActualEyeBearingHeadProfileWithoutIncludingTheBody() throws Exception {
        // CodModel: head texOffs(11,0), size(2,4,3); EAST is u+depth+width,v+depth.
        var cod = builtin("cod");
        assertEquals("minecraft:textures/entity/fish/cod.png", cod.texture().toString());
        assertEquals(new TexturePortraitDefinition.Size(32, 32), cod.referenceSize());
        assertEquals(new TexturePortraitDefinition.Size(4, 4), cod.canvasSize());
        assertLayer(cod, 0, 16, 3, 3, 4, 1, 0);
        // The separate nose is texOffs(0,0), size(2,3,1), one model unit ahead of the head.
        assertLayer(cod, 1, 3, 1, 1, 3, 0, 0);

        // SalmonModel uses a separate head texOffs(22,0), size(2,4,3).
        var salmon = builtin("salmon");
        assertEquals("minecraft:textures/entity/fish/salmon.png", salmon.texture().toString());
        assertEquals(new TexturePortraitDefinition.Size(3, 4), salmon.canvasSize());
        assertEquals(1, salmon.layers().size());
        assertLayer(salmon, 0, 27, 3, 3, 4, 0, 0);

        // TropicalFishModelA has one shared head/body cube; only the front three pixels
        // of its EAST face (8,6,6,3) contain the head and eye. The rear body is excluded.
        var tropical = builtin("tropical_fish");
        assertEquals("minecraft:textures/entity/fish/tropical_a.png", tropical.texture().toString());
        assertEquals(new TexturePortraitDefinition.Size(3, 3), tropical.canvasSize());
        assertEquals(1, tropical.layers().size());
        assertLayer(tropical, 0, 8, 6, 3, 3, 0, 0);
    }

    @Test void dolphinProfileIncludesTheHeadEyeAndSeparateSnoutAtTheirActualOffset() throws Exception {
        // DolphinModel: head texOffs(0,0), size(8,7,6), nose texOffs(0,13), size(2,2,4).
        // Their model z ranges are [-3,3] and [-7,-3], and nose y starts five below head y.
        var dolphin = builtin("dolphin");
        assertEquals("minecraft:textures/entity/dolphin.png", dolphin.texture().toString());
        assertEquals(new TexturePortraitDefinition.Size(64, 64), dolphin.referenceSize());
        assertEquals(new TexturePortraitDefinition.Size(10, 7), dolphin.canvasSize());
        assertEquals(2, dolphin.layers().size());
        assertLayer(dolphin, 0, 14, 6, 6, 7, 4, 0);
        assertLayer(dolphin, 1, 6, 17, 4, 2, 0, 5);
    }

    @Test void pufferfishUsesOneStableInflatedFaceWithItsRealFinAndSpikeUvs() throws Exception {
        // PufferfishBigModel body texOffs(0,0), size(8,8,8); planar spike UVs are not cube faces.
        var fish = builtin("pufferfish");
        assertEquals("minecraft:textures/entity/fish/pufferfish.png", fish.texture().toString());
        assertEquals(new TexturePortraitDefinition.Size(12, 10), fish.canvasSize());
        assertEquals(7, fish.layers().size());
        assertLayer(fish, 0, 26, 2, 2, 1, 0, 2);
        assertLayer(fish, 1, 26, 5, 2, 1, 10, 2);
        assertLayer(fish, 2, 15, 17, 8, 1, 2, 0);
        assertLayer(fish, 3, 15, 20, 8, 1, 2, 9);
        assertLayer(fish, 4, 5, 17, 1, 8, 1, 1);
        assertLayer(fish, 5, 1, 17, 1, 8, 10, 1);
        assertLayer(fish, 6, 8, 8, 8, 8, 2, 1);
    }

    @Test void relatedSpeciesKeepTheirOwnSkinsAndGuardianPupilIsTheLastLayer() throws Exception {
        // SquidModel body texOffs(0,0), size(12,16,12); NORTH includes both real eyes.
        for (String species : new String[]{"squid", "glow_squid"}) {
            var squid = builtin(species);
            assertEquals("minecraft:textures/entity/squid/" + species + ".png", squid.texture().toString());
            assertEquals(new TexturePortraitDefinition.Size(64, 32), squid.referenceSize());
            assertEquals(new TexturePortraitDefinition.Size(12, 16), squid.canvasSize());
            assertEquals(1, squid.layers().size());
            assertLayer(squid, 0, 12, 12, 12, 16, 0, 0);
        }
        assertNotEquals(builtin("squid").texture(), builtin("glow_squid").texture());

        for (String species : new String[]{"guardian", "elder_guardian"}) {
            var guardian = builtin(species);
            assertEquals("minecraft:textures/entity/" + (species.equals("guardian") ? "guardian" : "guardian_elder") + ".png",
                    guardian.texture().toString());
            assertEquals(new TexturePortraitDefinition.Size(16, 16), guardian.canvasSize());
            assertEquals(6, guardian.layers().size());
            // GuardianModel has four frame cubes, a 12x12 front, and a separate eye.
            assertLayer(guardian, 0, 12, 40, 2, 12, 0, 2);
            assertLayer(guardian, 1, 12, 40, 2, 12, 14, 2);
            assertTrue(guardian.layers().get(1).flipX());
            assertLayer(guardian, 2, 28, 52, 12, 2, 2, 0);
            assertLayer(guardian, 3, 28, 52, 12, 2, 2, 14);
            assertLayer(guardian, 4, 16, 16, 12, 12, 2, 2);
            // Eye texOffs(8,0), size(2,2,1), model x=-1,y=15, relative to frame x=-8,y=8.
            assertLayer(guardian, 5, 9, 1, 2, 2, 7, 7);
        }
        assertNotEquals(builtin("guardian").texture(), builtin("elder_guardian").texture());
    }

    @Test void axolotlRetainsItsThreeGillsAndTurtleUsesItsOwnWideAtlas() throws Exception {
        // AxolotlModel head texOffs(0,1), size(8,5,5); the three gills are zero-depth planes.
        var axolotl = builtin("axolotl");
        assertEquals("minecraft:textures/entity/axolotl/axolotl_lucy.png", axolotl.texture().toString());
        assertEquals(new TexturePortraitDefinition.Size(64, 64), axolotl.referenceSize());
        assertEquals(new TexturePortraitDefinition.Size(14, 8), axolotl.canvasSize());
        assertEquals(4, axolotl.layers().size());
        assertLayer(axolotl, 0, 3, 37, 8, 3, 3, 0);
        assertLayer(axolotl, 1, 0, 40, 3, 7, 0, 1);
        assertLayer(axolotl, 2, 11, 40, 3, 7, 11, 1);
        assertLayer(axolotl, 3, 5, 6, 8, 5, 3, 3);

        // TurtleModel head texOffs(3,0), size(6,5,6), on a 128x64 texture.
        var turtle = builtin("turtle");
        assertEquals("minecraft:textures/entity/turtle/big_sea_turtle.png", turtle.texture().toString());
        assertEquals(new TexturePortraitDefinition.Size(128, 64), turtle.referenceSize());
        assertEquals(new TexturePortraitDefinition.Size(6, 5), turtle.canvasSize());
        assertLayer(turtle, 0, 9, 6, 6, 5, 0, 0);
    }

    @Test void wardenPreservesTendrilPositionsWhileRemovingOnlyTransparentOuterPadding() throws Exception {
        // WardenModel: 16x16x10 head at texOffs(0,32); tendril planes at (52,32) and (58,0).
        // Vanilla alpha bounds are respectively (6,6,16,16) and (0,6,10,16).
        // Crop the overall 48x25 canvas by (6,6), retaining original model-relative positions.
        var warden = builtin("warden");
        assertEquals("minecraft:textures/entity/warden/warden.png", warden.texture().toString());
        assertEquals(new TexturePortraitDefinition.Size(128, 128), warden.referenceSize());
        assertEquals(new TexturePortraitDefinition.Size(36, 19), warden.canvasSize());
        assertEquals(3, warden.layers().size());
        assertLayer(warden, 0, 58, 38, 10, 10, 0, 0);
        assertLayer(warden, 1, 58, 6, 10, 10, 26, 0);
        assertLayer(warden, 2, 10, 42, 16, 16, 10, 3);
    }

    @Test void shippedSpeciesAreWiredIntoTheAutomaticEntityPortraitDefaults() throws Exception {
        var declaration = EntityPortraits.class.getDeclaredField("BUILTIN_TEXTURE_FILES");
        declaration.setAccessible(true);
        var defaults = (Map<?, ?>) declaration.get(null);
        for (String species : COMMON_SPECIES) {
            var mapping = defaults.entrySet().stream()
                    .filter(entry -> entry.getKey().toString().equals("minecraft:" + species)).findFirst();
            assertTrue(mapping.isPresent(), species);
            assertEquals("arc_quest:objective_icons/portraits/" + species + ".json", mapping.orElseThrow().getValue().toString());
        }
    }

    private static void assertLayer(TexturePortraitDefinition portrait, int index,
                                    int x, int y, int width, int height, int destX, int destY) {
        var layer = portrait.layers().get(index);
        assertEquals(new TexturePortraitDefinition.Rect(x, y, width, height), layer.region());
        assertEquals(new TexturePortraitDefinition.Rect(destX, destY, width, height), layer.destination());
    }

    private static TexturePortraitDefinition builtin(String name) throws Exception {
        try (var stream = VanillaAquaticPortraitTest.class.getResourceAsStream(
                "/assets/arc_quest/objective_icons/portraits/" + name + ".json")) {
            assertNotNull(stream, name);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return EntityPortraitRule.parse(JsonParser.parseReader(reader)).portrait();
            }
        }
    }
}
