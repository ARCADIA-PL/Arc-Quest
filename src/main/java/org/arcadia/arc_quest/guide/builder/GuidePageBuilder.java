package org.arcadia.arc_quest.guide.builder;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.guide.api.GuideMediaDefinition;
import org.arcadia.arc_quest.guide.api.GuidePageDefinition;
import org.arcadia.arc_quest.guide.api.GuideText;

import java.util.Objects;

public final class GuidePageBuilder {

    private GuideMediaDefinition media = GuideMediaBuilder.none().build();
    private GuideText description;

    private GuidePageBuilder() {
    }

    public static GuidePageBuilder create() {
        return new GuidePageBuilder();
    }

    public GuidePageBuilder description(String literal) {
        this.description = GuideText.literal(literal);
        return this;
    }

    public GuidePageBuilder description(Component component) {
        this.description = GuideText.component(component);
        return this;
    }

    public GuidePageBuilder description(GuideText text) {
        this.description = text;
        return this;
    }

    public GuidePageBuilder media(GuideMediaDefinition media) {
        this.media = Objects.requireNonNull(media, "media");
        return this;
    }

    public GuidePageBuilder media(GuideMediaBuilder builder) {
        return media(Objects.requireNonNull(builder, "builder").build());
    }

    public GuidePageBuilder image(ResourceLocation texture, int width, int height) {
        return media(GuideMediaBuilder.image(texture).size(width, height));
    }

    public GuidePageBuilder ponder(ResourceLocation sceneId) {
        return media(GuideMediaBuilder.ponder(sceneId));
    }

    public GuidePageBuilder none() {
        return media(GuideMediaBuilder.none());
    }

    public GuidePageDefinition build() {
        if (description == null) {
            throw new IllegalStateException("Guide page requires description");
        }
        return new GuidePageDefinition(media, description);
    }
}
