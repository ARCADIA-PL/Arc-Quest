package org.arcadia.arc_quest.client.hud.quest.icon.portrait;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class PortraitValidationTest {
    @Test void headPoseAndCropAreStableAndBounded() {
        var frame = new HeadPortraitDefinition.Frame(-.3f, -.05f, .3f, .55f);
        var source = HeadPortraitDefinition.skull(ResourceLocation.parse("minecraft:zombie_head"), frame);
        assertEquals(new HeadPortraitDefinition.Pose(180, 0, 0), source.pose());
        assertEquals(64, source.resolution());
        assertThrows(IllegalArgumentException.class, () -> new HeadPortraitDefinition.Pose(Float.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new HeadPortraitDefinition.Frame(1, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new HeadPortraitDefinition(source.headItem(), source.adapter(), source.pose(), frame, 1024));
    }

    @Test void pngAllocationIsBoundedBeforeFullDecode() throws Exception {
        assertEquals(new TexturePortraitDefinition.Size(64, 32), PortraitPngHeader.read(header(64, 32)));
        assertThrows(IOException.class, () -> PortraitPngHeader.read(header(16384, 16384)));
        assertThrows(IOException.class, () -> PortraitPngHeader.read(header(-1, 32)));
        assertThrows(IOException.class, () -> PortraitPngHeader.read(new ByteArrayInputStream(new byte[8])));
        assertThrows(IOException.class, () -> PortraitPngHeader.read(new ByteArrayInputStream(new byte[0])));
    }

    @Test void cachedPortraitAlphaDoesNotDarkenTransparentEdgesTwice() {
        assertEquals(0, HeadPortraitBaker.straightAlpha(0x00FFFFFF));
        assertEquals(0xFFAABBCC, HeadPortraitBaker.straightAlpha(0xFFAABBCC));
        assertEquals(0x80FF8040, HeadPortraitBaker.straightAlpha(0x80804020));
        assertEquals(0x0100FF00, HeadPortraitBaker.straightAlpha(0x01000100));
    }

    private static ByteArrayInputStream header(int width, int height) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var data = new DataOutputStream(bytes)) {
            data.write(new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
            data.writeInt(13); data.writeInt(0x49484452); data.writeInt(width); data.writeInt(height);
        }
        return new ByteArrayInputStream(bytes.toByteArray());
    }
}
