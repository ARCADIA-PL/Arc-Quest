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

    @Test
    void explicitItemUsesAnIdAndRejectsMixedSources() {
        var id = ResourceLocation.parse("addon:example_item");
        var icon = ObjectiveIcons.item(id);
        assertEquals(ObjectiveIconSpec.Mode.ITEM, icon.mode());
        assertEquals(id, icon.item());
        assertEquals(icon, ObjectiveIcons.item(id.toString()));
        assertNull(icon.texture());
        assertNull(icon.provider());
        assertNull(icon.region());
        assertThrows(IllegalStateException.class, () -> icon.region(0, 0, 16, 16));
        assertThrows(NullPointerException.class, () -> ObjectiveIcons.item((ResourceLocation) null));
        assertThrows(IllegalArgumentException.class, () -> ObjectiveIcons.item(""));
        assertThrows(IllegalArgumentException.class, () -> ObjectiveIcons.item("addon:../bad"));
        assertThrows(IllegalArgumentException.class, () -> new ObjectiveIconSpec(
                ObjectiveIconSpec.Mode.ITEM, id, null, null, id));
        assertThrows(IllegalArgumentException.class, () -> new ObjectiveIconSpec(
                ObjectiveIconSpec.Mode.ITEM, null, null, id, id));
        assertThrows(IllegalArgumentException.class, () -> new ObjectiveIconSpec(
                ObjectiveIconSpec.Mode.ITEM, null, new ObjectiveIconSpec.Region(0, 0, 1, 1), null, id));
        for (var mode : new ObjectiveIconSpec.Mode[]{ObjectiveIconSpec.Mode.AUTO, ObjectiveIconSpec.Mode.NONE,
                ObjectiveIconSpec.Mode.TEXTURE, ObjectiveIconSpec.Mode.PROVIDER}) {
            assertThrows(IllegalArgumentException.class, () -> new ObjectiveIconSpec(mode,
                    mode == ObjectiveIconSpec.Mode.TEXTURE ? id : null, null,
                    mode == ObjectiveIconSpec.Mode.PROVIDER ? id : null, id));
        }
    }
}
