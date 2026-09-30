package org.arcadia.arc_quest.client.hud.quest.icon.portrait;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/** Explicit, immutable UV adaptation of one entity texture; no entity or model is instantiated. */
public record TexturePortraitDefinition(ResourceLocation texture, Size referenceSize, Size canvasSize,
                                        List<Layer> layers) {
    public static final int MAX_LAYERS = 32;

    public TexturePortraitDefinition {
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(referenceSize, "referenceSize");
        Objects.requireNonNull(canvasSize, "canvasSize");
        if (canvasSize.width() > 256 || canvasSize.height() > 256)
            throw new IllegalArgumentException("Portrait canvas must be at most 256 by 256");
        layers = List.copyOf(layers);
        if (layers.isEmpty() || layers.size() > MAX_LAYERS)
            throw new IllegalArgumentException("Portrait requires between 1 and " + MAX_LAYERS + " layers");
        for (Layer layer : layers) {
            if (!layer.region().fits(referenceSize)) throw new IllegalArgumentException("Portrait UV exceeds referenceSize");
            if (!layer.destination().fits(canvasSize)) throw new IllegalArgumentException("Portrait layer exceeds canvasSize");
        }
    }

    /** A same-layout HD replacement keeps normalized UVs, including non-integer scale factors. */
    public boolean acceptsTextureSize(int width, int height) {
        return width > 0 && height > 0
                && (long) width * referenceSize.height() == (long) height * referenceSize.width();
    }

    public record Size(int width, int height) {
        public Size {
            if (width <= 0 || height <= 0 || width > 16384 || height > 16384)
                throw new IllegalArgumentException("Portrait dimensions must be between 1 and 16384");
        }
    }

    public record Rect(int x, int y, int width, int height) {
        public Rect {
            if (x < 0 || y < 0 || width <= 0 || height <= 0)
                throw new IllegalArgumentException("Portrait rectangles need nonnegative origins and positive dimensions");
        }
        public boolean fits(Size size) {
            return (long) x + width <= size.width() && (long) y + height <= size.height();
        }
    }

    /** Layers are drawn back-to-front. Tint uses unsigned ARGB bits; white means unchanged. */
    public record Layer(Rect region, Rect destination, boolean flipX, boolean flipY, int tint) {
        public Layer {
            Objects.requireNonNull(region, "region");
            Objects.requireNonNull(destination, "destination");
        }
        public Layer(Rect region, Rect destination) { this(region, destination, false, false, 0xFFFFFFFF); }
    }
}
