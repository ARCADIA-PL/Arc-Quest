package org.arcadia.arc_quest.guide.api;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

public record GuideCategoryDefinition(
        GuideText displayName,
        String translationKey,
        int themeColor,
        int sortOrder,
        boolean builtin,
        @Nullable ResourceLocation iconTexture
) {
}
