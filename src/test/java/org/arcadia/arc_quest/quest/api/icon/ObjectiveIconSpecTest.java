package org.arcadia.arc_quest.quest.api.icon;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ObjectiveIconSpecTest {
    @Test
    void factoriesAndRegionAreImmutableAndCanonical() {
        var texture = ObjectiveIcons.texture("example:textures/gui/target.png");
        var crop = texture.region(8, 16, 20, 12);
        assertNull(texture.region());
        assertEquals(new ObjectiveIconSpec.Region(8, 16, 20, 12), crop.region());
        assertEquals(texture.texture(), crop.texture());
        assertSame(ObjectiveIconSpec.AUTO, ObjectiveIcons.normalize(null));
        assertSame(ObjectiveIconSpec.AUTO, ObjectiveIcons.normalize(
                new ObjectiveIconSpec(ObjectiveIconSpec.Mode.AUTO, null, null, null)));
        assertSame(ObjectiveIconSpec.NONE, ObjectiveIcons.none());
        assertEquals(ObjectiveIconSpec.Mode.PROVIDER, ObjectiveIcons.provider("addon:portrait").mode());
        assertEquals(ResourceLocation.parse("addon:portrait"), ObjectiveIcons.provider("addon:portrait").provider());
    }

    @Test
    void rejectsAmbiguousSourcesInvalidRegionsAndNonResourcePaths() {
        var texture = ResourceLocation.parse("example:textures/gui/target.png");
        var provider = ResourceLocation.parse("addon:portrait");
        assertThrows(IllegalArgumentException.class, () -> new ObjectiveIconSpec(
                ObjectiveIconSpec.Mode.NONE, texture, null, null));
        assertThrows(IllegalArgumentException.class, () -> new ObjectiveIconSpec(
                ObjectiveIconSpec.Mode.TEXTURE, texture, null, provider));
        assertThrows(IllegalArgumentException.class, () -> new ObjectiveIconSpec(
                ObjectiveIconSpec.Mode.PROVIDER, null, new ObjectiveIconSpec.Region(0, 0, 1, 1), provider));
        assertThrows(IllegalArgumentException.class, () -> new ObjectiveIconSpec.Region(-1, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ObjectiveIconSpec.Region(0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ObjectiveIconSpec.Region(Integer.MAX_VALUE, 0, 1, 1));
        assertThrows(IllegalStateException.class, () -> ObjectiveIcons.none().region(0, 0, 16, 16));
        for (String value : new String[]{"", " ", "bad ID", "https://example.org/a.png", "c:/textures/a.png", "/tmp/a.png", "example:../a.png"}) {
            assertThrows(IllegalArgumentException.class, () -> ObjectiveIcons.texture(value), value);
        }
        assertThrows(NullPointerException.class, () -> ObjectiveIcons.texture((String) null));
        assertThrows(NullPointerException.class, () -> ObjectiveIcons.texture((ResourceLocation) null));
    }
}
