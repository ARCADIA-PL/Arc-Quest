package org.arcadia.arc_quest.guide.api;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Explicit searchable subject of a guide. The decorative guide icon is never an association. */
public record GuideItemAssociation(ResourceLocation id, boolean tag, int pageIndex) {
    public GuideItemAssociation {
        Objects.requireNonNull(id, "id");
        if (pageIndex < 0) throw new IllegalArgumentException("Guide pageIndex must be nonnegative");
    }

    public static GuideItemAssociation item(ResourceLocation id, int pageIndex) {
        return new GuideItemAssociation(id, false, pageIndex);
    }

    public static GuideItemAssociation tag(ResourceLocation id, int pageIndex) {
        return new GuideItemAssociation(id, true, pageIndex);
    }
}
