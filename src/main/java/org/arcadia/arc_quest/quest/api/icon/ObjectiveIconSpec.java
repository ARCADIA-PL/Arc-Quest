package org.arcadia.arc_quest.quest.api.icon;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Immutable, common-side icon policy. Contains no renderer, item stack or client callback. */
public record ObjectiveIconSpec(Mode mode,
                                @Nullable ResourceLocation texture,
                                @Nullable Region region,
                                @Nullable ResourceLocation provider,
                                @Nullable ResourceLocation item) {
    public static final ObjectiveIconSpec AUTO = new ObjectiveIconSpec(Mode.AUTO, null, null, null);
    public static final ObjectiveIconSpec NONE = new ObjectiveIconSpec(Mode.NONE, null, null, null);

    public ObjectiveIconSpec {
        validate(mode, texture, region, provider, item);
    }

    /** Keeps the original constructor available to existing texture/provider callers. */
    public ObjectiveIconSpec(Mode mode, ResourceLocation texture, Region region, ResourceLocation provider) {
        this(mode, texture, region, provider, null);
    }

    /** Pixel coordinates in the actual texture; its dimensions are resolved on the client. */
    public record Region(int x, int y, int width, int height) {
        public Region {
            if (x < 0 || y < 0) throw new IllegalArgumentException("icon.region x/y must be >= 0");
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("icon.region width/height must be > 0");
            if ((long) x + width > Integer.MAX_VALUE || (long) y + height > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("icon.region bounds exceed integer range");
            }
        }
    }

    public enum Mode { AUTO, NONE, TEXTURE, PROVIDER, ITEM }

    /** Returns a new texture policy; the original value remains unchanged. */
    public ObjectiveIconSpec region(int x, int y, int width, int height) {
        if (mode != Mode.TEXTURE) throw new IllegalStateException("Only a texture icon can have a region");
        return new ObjectiveIconSpec(mode, texture, new Region(x, y, width, height), null);
    }

    /** Shared validation used by constructors, definition validation and client compilation. */
    public void validate() {
        validate(mode, texture, region, provider, item);
    }

    private static void validate(Mode mode, ResourceLocation texture, Region region, ResourceLocation provider, ResourceLocation item) {
        Objects.requireNonNull(mode, "icon.mode must not be null");
        switch (mode) {
            case AUTO, NONE -> {
                if (texture != null || region != null || provider != null || item != null) {
                    throw new IllegalArgumentException("icon " + mode + " cannot contain texture, region, provider or item");
                }
            }
            case TEXTURE -> {
                requireResourceId(texture, "icon.texture");
                if (provider != null || item != null) throw new IllegalArgumentException("icon.texture cannot also specify provider or item");
            }
            case PROVIDER -> {
                requireResourceId(provider, "icon.provider");
                if (texture != null || region != null || item != null) {
                    throw new IllegalArgumentException("icon.provider cannot also specify texture, region or item");
                }
            }
            case ITEM -> {
                requireResourceId(item, "icon.item");
                if (texture != null || region != null || provider != null) {
                    throw new IllegalArgumentException("icon.item cannot also specify texture, region or provider");
                }
            }
        }
    }

    static ResourceLocation requireResourceId(ResourceLocation id, String field) {
        Objects.requireNonNull(id, field + " must not be null");
        String path = id.getPath();
        if (path.isEmpty() || path.startsWith("/")) {
            throw new IllegalArgumentException(field + " must be a resource ID, not an absolute path");
        }
        for (String part : path.split("/")) {
            if (part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException(field + " cannot contain relative path segments");
            }
        }
        return id;
    }
}
