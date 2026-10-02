package org.arcadia.arc_quest.quest.api;

import org.arcadia.arc_quest.guide.api.GuideMediaDefinition;
import org.arcadia.arc_quest.guide.api.GuideMediaType;

import javax.annotation.Nullable;
import java.util.Objects;

/** An ordered, independently unlockable text/image block using the Guide media definition. */
public record CollectionContentBlock(String blockId,
                                     QuestText text,
                                     @Nullable GuideMediaDefinition media,
                                     QuestText caption,
                                     CollectionMediaFit fit,
                                     boolean zoomable,
                                     CollectionContentReveal reveal,
                                     String revealStepId) {
    public CollectionContentBlock {
        if (blockId == null || blockId.isBlank()) throw new IllegalArgumentException("content blockId is required");
        blockId = blockId.trim();
        text = text == null ? QuestText.literal("") : text;
        caption = caption == null ? QuestText.literal("") : caption;
        fit = Objects.requireNonNullElse(fit, CollectionMediaFit.CONTAIN);
        reveal = Objects.requireNonNullElse(reveal, CollectionContentReveal.DISCOVERED);
        revealStepId = revealStepId == null ? "" : revealStepId.trim();
        if (reveal == CollectionContentReveal.RESEARCH_STEP && revealStepId.isEmpty()) {
            throw new IllegalArgumentException("RESEARCH_STEP content requires revealStepId");
        }
        if (reveal != CollectionContentReveal.RESEARCH_STEP && !revealStepId.isEmpty()) {
            throw new IllegalArgumentException("revealStepId is only valid for RESEARCH_STEP content");
        }
        if (media != null && media.getType() != GuideMediaType.IMAGE && media.getType() != GuideMediaType.NONE) {
            throw new IllegalArgumentException("Collection content supports Guide IMAGE media only");
        }
        if (media != null && (media.getWidth() < 1 || media.getHeight() < 1)) {
            throw new IllegalArgumentException("Collection media width/height must be positive");
        }
    }

    public CollectionContentBlock(String blockId, QuestText text, @Nullable GuideMediaDefinition media, QuestText caption) {
        this(blockId, text, media, caption, CollectionMediaFit.CONTAIN, true, CollectionContentReveal.DISCOVERED, "");
    }
}
