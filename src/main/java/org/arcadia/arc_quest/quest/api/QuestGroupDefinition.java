package org.arcadia.arc_quest.quest.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public final class QuestGroupDefinition {

    private final ResourceLocation id;
    private final Component displayName;
    private final int sortOrder;
    private final int themeColor;

    public QuestGroupDefinition(ResourceLocation id, Component displayName, int sortOrder, int themeColor) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.sortOrder = sortOrder;
        this.themeColor = themeColor;
    }

    public QuestGroupDefinition(ResourceLocation id, Component displayName) {
        this(id, displayName, 0, 0xFFFFFFFF);
    }

    public static QuestGroupDefinition literal(ResourceLocation id, String displayName) {
        return new QuestGroupDefinition(id, Component.literal(displayName));
    }

    public static QuestGroupDefinition literal(String id, String displayName) {
        return literal(ResourceLocation.parse(id), displayName);
    }

    public static QuestGroupDefinition translated(ResourceLocation id, String translationKey) {
        return new QuestGroupDefinition(id, Component.translatable(translationKey));
    }

    public static QuestGroupDefinition translated(String id, String translationKey) {
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
        if (!(obj instanceof QuestGroupDefinition other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "QuestGroupDefinition[" + id + "]";
    }
}
