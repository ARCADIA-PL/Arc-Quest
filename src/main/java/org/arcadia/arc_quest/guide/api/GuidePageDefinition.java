package org.arcadia.arc_quest.guide.api;

import java.util.Objects;

public final class GuidePageDefinition {

    private final GuideMediaDefinition media;
    private final GuideText description;

    public GuidePageDefinition(GuideMediaDefinition media, GuideText description) {
        this.media = Objects.requireNonNull(media, "media");
        this.description = Objects.requireNonNull(description, "description");
    }

    public GuideMediaDefinition getMedia() {
        return media;
    }

    public GuideText getDescriptionText() {
        return description;
    }
}
