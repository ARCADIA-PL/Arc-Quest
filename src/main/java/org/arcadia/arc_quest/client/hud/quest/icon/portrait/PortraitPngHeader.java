package org.arcadia.arc_quest.client.hud.quest.icon.portrait;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/** Bounds allocation before a resource texture is decoded. Full PNG validity is checked afterwards. */
final class PortraitPngHeader {
    private static final byte[] SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private PortraitPngHeader() { }

    static TexturePortraitDefinition.Size read(InputStream stream) throws IOException {
        DataInputStream data = new DataInputStream(stream);
        if (!Arrays.equals(SIGNATURE, data.readNBytes(8)) || data.readInt() != 13 || data.readInt() != 0x49484452)
            throw new IOException("Portrait texture must have a PNG IHDR header");
        int width = data.readInt(), height = data.readInt();
        if (width <= 0 || height <= 0 || width > 16384 || height > 16384
                || (long) width * height > 16_777_216L)
            throw new IOException("Portrait texture exceeds the 16 megapixel allocation limit");
        return new TexturePortraitDefinition.Size(width, height);
    }
}
