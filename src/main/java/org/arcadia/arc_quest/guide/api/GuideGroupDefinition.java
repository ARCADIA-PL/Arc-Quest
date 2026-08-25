package org.arcadia.arc_quest.guide.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** 相关处理说明。 */
public final class GuideGroupDefinition {

    private final ResourceLocation id;
    private final Component displayName;
    private final int sortOrder;
    private final int themeColor;

    public GuideGroupDefinition(ResourceLocation id, Component displayName, int sortOrder, int themeColor) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.sortOrder = sortOrder;
        this.themeColor = themeColor;
    }

    public GuideGroupDefinition(ResourceLocation id, Component displayName) {
        this(id, displayName, 0, 0xFFFFFFFF);
    }

    public static GuideGroupDefinition literal(ResourceLocation id, String displayName) {
        return new GuideGroupDefinition(id, Component.literal(displayName));
    }

    public static GuideGroupDefinition literal(String id, String displayName) {
        return literal(ResourceLocation.parse(id), displayName);
    }

    public static GuideGroupDefinition translated(ResourceLocation id, String translationKey) {
        return new GuideGroupDefinition(id, Component.translatable(translationKey));
    }

    public static GuideGroupDefinition translated(String id, String translationKey) {
        return translated(ResourceLocation.parse(id), translationKey);
    }

    public ResourceLocation getId() {
        return id;
    }

    public Component getDisplayName() {
        return displayName;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public int getThemeColor() {
        return themeColor;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof GuideGroupDefinition other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "GuideGroupDefinition[" + id + "]";
    }
}
