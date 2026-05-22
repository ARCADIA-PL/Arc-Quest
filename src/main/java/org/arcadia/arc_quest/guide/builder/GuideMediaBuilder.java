package org.arcadia.arc_quest.guide.builder;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.guide.api.GuideMediaDefinition;
import org.arcadia.arc_quest.guide.api.GuideMediaType;

import javax.annotation.Nullable;

public final class GuideMediaBuilder {

    private static final int DEFAULT_WIDTH = 180;
    private static final int DEFAULT_HEIGHT = 90;

    private final GuideMediaType type;
    @Nullable
    private final ResourceLocation texture;
    @Nullable
    private final ResourceLocation sceneId;
    private int width = DEFAULT_WIDTH;
    private int height = DEFAULT_HEIGHT;
    private boolean autoplay = true;
    private boolean loop = false;

    private GuideMediaBuilder(GuideMediaType type, @Nullable ResourceLocation texture, @Nullable ResourceLocation sceneId) {
        this.type = type;
        this.texture = texture;
        this.sceneId = sceneId;
    }

    public static GuideMediaBuilder none() {
        return new GuideMediaBuilder(GuideMediaType.NONE, null, null);
    }

    public static GuideMediaBuilder image(ResourceLocation texture) {
        return new GuideMediaBuilder(GuideMediaType.IMAGE, texture, null);
    }

    public static GuideMediaBuilder ponder(ResourceLocation sceneId) {
        return new GuideMediaBuilder(GuideMediaType.PONDER, null, sceneId);
    }

    public GuideMediaBuilder size(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    public GuideMediaBuilder autoplay(boolean autoplay) {
        this.autoplay = autoplay;
        return this;
    }

    public GuideMediaBuilder loop(boolean loop) {
        this.loop = loop;
        return this;
    }

    public GuideMediaDefinition build() {
        int finalWidth = width > 0 ? width : DEFAULT_WIDTH;
        int finalHeight = height > 0 ? height : DEFAULT_HEIGHT;
        return new GuideMediaDefinition(type, texture, sceneId, finalWidth, finalHeight, autoplay, loop);
    }
}
