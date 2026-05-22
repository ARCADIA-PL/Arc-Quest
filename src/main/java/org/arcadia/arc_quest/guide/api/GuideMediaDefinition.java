package org.arcadia.arc_quest.guide.api;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Objects;

public final class GuideMediaDefinition {

    private final GuideMediaType type;
    @Nullable
    private final ResourceLocation texture;
    @Nullable
    private final ResourceLocation sceneId;
    private final int width;
    private final int height;
    private final boolean autoplay;
    private final boolean loop;

    public GuideMediaDefinition(GuideMediaType type,
                                @Nullable ResourceLocation texture,
                                @Nullable ResourceLocation sceneId,
                                int width,
                                int height,
                                boolean autoplay,
                                boolean loop) {
        this.type = Objects.requireNonNull(type, "type");
        this.texture = texture;
        this.sceneId = sceneId;
        this.width = width;
        this.height = height;
        this.autoplay = autoplay;
        this.loop = loop;

        switch (type) {
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
