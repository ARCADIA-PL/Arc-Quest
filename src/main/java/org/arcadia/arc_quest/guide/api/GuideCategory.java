package org.arcadia.arc_quest.guide.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Objects;

public final class GuideCategory {

    private final ResourceLocation id;
    private final GuideText displayName;
    private final String translationKey;
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
                         GuideText displayName,
                         String translationKey,
                         int themeColor,
                         int sortOrder,
                         boolean builtin,
                         @Nullable ResourceLocation iconTexture) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.translationKey = translationKey == null ? "" : translationKey;
        this.themeColor = themeColor;
        this.sortOrder = sortOrder;
        this.builtin = builtin;
        this.iconTexture = iconTexture;
    }

    public ResourceLocation getId() {
        return id;
    }

    public GuideText getDisplayNameText() {
        return displayName;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public Component getDisplayName() {
        return displayName.resolve(null, GuideTextContext.empty());
    }

    public Component getDisplayName(@Nullable ServerPlayer player, @Nullable GuideTextContext context) {
        return displayName.resolve(player, context);
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
        return id.toString();
    }
}
