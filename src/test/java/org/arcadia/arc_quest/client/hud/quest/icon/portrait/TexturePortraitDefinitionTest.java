package org.arcadia.arc_quest.client.hud.quest.icon.portrait;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TexturePortraitDefinitionTest {
    @Test void layerBoundsCannotOverflowOrRenderPartialFaces() {
        var size = new TexturePortraitDefinition.Size(64, 32);
        assertFalse(new TexturePortraitDefinition.Rect(Integer.MAX_VALUE, 0, 8, 8).fits(size));
        var region = new TexturePortraitDefinition.Rect(0, 0, 8, 8);
        assertThrows(IllegalArgumentException.class, () -> definition(List.of(new TexturePortraitDefinition.Layer(region,
                new TexturePortraitDefinition.Rect(1, 0, 8, 8)))));
        assertThrows(IllegalArgumentException.class, () -> new TexturePortraitDefinition.Size(0, 64));
        assertThrows(IllegalArgumentException.class, () -> new TexturePortraitDefinition.Rect(0, 0, -1, 8));
    }

    @Test void adaptedTextureDefinitionIsAnImmutableCacheKey() {
        var rect = new TexturePortraitDefinition.Rect(0, 0, 8, 8);
        var layers = new ArrayList<TexturePortraitDefinition.Layer>();
        layers.add(new TexturePortraitDefinition.Layer(rect, rect));
        var definition = definition(layers);
        int hash = definition.hashCode();
        layers.clear();
        assertEquals(1, definition.layers().size());
        assertEquals(hash, definition.hashCode());
        assertThrows(UnsupportedOperationException.class, () -> definition.layers().clear());
    }

    @Test void highResolutionKeepsNormalizedUvButChangedAspectIsNotGuessed() {
        var rect = new TexturePortraitDefinition.Rect(0, 0, 8, 8);
        var definition = definition(List.of(new TexturePortraitDefinition.Layer(rect, rect)));
        assertTrue(definition.acceptsTextureSize(64, 32));
        assertTrue(definition.acceptsTextureSize(128, 64));
        assertTrue(definition.acceptsTextureSize(96, 48));
        assertFalse(definition.acceptsTextureSize(64, 64));
        assertFalse(definition.acceptsTextureSize(0, 0));
    }

    private static TexturePortraitDefinition definition(List<TexturePortraitDefinition.Layer> layers) {
        return new TexturePortraitDefinition(ResourceLocation.fromNamespaceAndPath("test", "texture.png"),
                new TexturePortraitDefinition.Size(64, 32), new TexturePortraitDefinition.Size(8, 8), layers);
    }
}
