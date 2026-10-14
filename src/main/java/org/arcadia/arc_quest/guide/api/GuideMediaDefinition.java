package org.arcadia.arc_quest.guide.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Objects;

public final class GuideMediaDefinition {
    public enum ImageFit { COVER, CONTAIN }

    private final GuideMediaType type;
    @Nullable
    private final ResourceLocation texture;
    @Nullable
    private final ResourceLocation sceneId;
    private final int width;
    private final int height;
    private final boolean autoplay;
    private final boolean loop;
    private final ItemStack item;
    private final ImageFit imageFit;

    public GuideMediaDefinition(GuideMediaType type,
                                @Nullable ResourceLocation texture,
                                @Nullable ResourceLocation sceneId,
                                int width,
                                int height,
                                boolean autoplay,
                                boolean loop) {
        this(type, texture, sceneId, width, height, autoplay, loop, ItemStack.EMPTY, ImageFit.COVER);
    }

    public GuideMediaDefinition(GuideMediaType type, @Nullable ResourceLocation texture,
                                @Nullable ResourceLocation sceneId, int width, int height,
                                boolean autoplay, boolean loop, ItemStack item, ImageFit imageFit) {
        this.type = Objects.requireNonNull(type, "type");
        this.texture = texture;
        this.sceneId = sceneId;
        this.width = width;
        this.height = height;
        this.autoplay = autoplay;
        this.loop = loop;
        this.item = Objects.requireNonNull(item, "item").copy();
        this.imageFit = Objects.requireNonNull(imageFit, "imageFit");
        if (type != GuideMediaType.ITEM && !item.isEmpty())
            throw new IllegalArgumentException("Only ITEM media may define an item");

        switch (type) {
            case ITEM -> {
                if (item.isEmpty() || texture != null || sceneId != null)
                    throw new IllegalArgumentException("Guide media ITEM requires only a nonempty item");
            }
            case NONE -> {
                if (texture != null || sceneId != null) {
                    throw new IllegalArgumentException("Guide media NONE must not define texture or sceneId");
                }
            }
            case IMAGE -> {
                if (texture == null) {
                    throw new IllegalArgumentException("Guide media IMAGE requires texture");
                }
            }
            case PONDER -> {
                if (sceneId == null) {
                    throw new IllegalArgumentException("Guide media PONDER requires sceneId");
                }
            }
        }
    }

    public GuideMediaType getType() {
        return type;
    }

    public ItemStack getItem() { return item.copy(); }
    public ImageFit getImageFit() { return imageFit; }

    @Nullable
    public ResourceLocation getTexture() {
        return texture;
    }

    @Nullable
    public ResourceLocation getSceneId() {
        return sceneId;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isAutoplay() {
        return autoplay;
    }

    public boolean isLoop() {
        return loop;
    }
}
