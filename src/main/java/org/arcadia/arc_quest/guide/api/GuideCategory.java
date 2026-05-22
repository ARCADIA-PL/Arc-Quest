package org.arcadia.arc_quest.guide.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Objects;

public final class GuideCategory {

    private final ResourceLocation id;
    private final Component displayName;
    private final int themeColor;
    private final int sortOrder;
    private final boolean builtin;
    @Nullable
    private final ResourceLocation iconTexture;

    public static final GuideCategory BASICS = GuideCategories.BASICS;
    public static final GuideCategory QUEST = GuideCategories.QUEST;
    public static final GuideCategory DIALOGUE = GuideCategories.DIALOGUE;
    public static final GuideCategory TRADE = GuideCategories.TRADE;
    public static final GuideCategory PONDER = GuideCategories.PONDER;
    public static final GuideCategory ADVANCED = GuideCategories.ADVANCED;

    public GuideCategory(ResourceLocation id,
                         Component displayName,
                         int themeColor,
                         int sortOrder,
                         boolean builtin,
                         @Nullable ResourceLocation iconTexture) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.themeColor = themeColor;
        this.sortOrder = sortOrder;
        this.builtin = builtin;
        this.iconTexture = iconTexture;
    }

    public ResourceLocation getId() {
        return id;
    }

    public Component getDisplayName() {
        return displayName;
    }

    public int getThemeColor() {
        return themeColor;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isBuiltin() {
        return builtin;
    }

    @Nullable
    public ResourceLocation getIconTexture() {
        return iconTexture;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GuideCategory that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "GuideCategory[" + id + "]";
    }
}
