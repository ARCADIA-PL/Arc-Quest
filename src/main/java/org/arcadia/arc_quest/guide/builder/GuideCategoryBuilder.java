package org.arcadia.arc_quest.guide.builder;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.guide.api.GuideCategory;
import org.arcadia.arc_quest.guide.api.GuideText;
import org.arcadia.arc_quest.guide.api.GuideTextContext;

import javax.annotation.Nullable;
import java.util.Objects;

public final class GuideCategoryBuilder {

    private final ResourceLocation id;
    private Component displayName;
    private int themeColor = 0x4FC3F7;
    private int sortOrder = 0;
    @Nullable
    private ResourceLocation iconTexture;
    private boolean builtin;

    private GuideCategoryBuilder(ResourceLocation id) {
        this.id = id;
    }

    public static GuideCategoryBuilder create(ResourceLocation id) {
        return new GuideCategoryBuilder(id);
    }

    public static GuideCategoryBuilder create(String id) {
        return id.contains(":")
                ? new GuideCategoryBuilder(ResourceLocation.parse(id))
                : new GuideCategoryBuilder(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, id));
    }

    public GuideCategoryBuilder displayName(String literal) {
        this.displayName = Component.literal(literal);
        return this;
    }

    public GuideCategoryBuilder displayName(Component component) {
        this.displayName = Objects.requireNonNull(component, "component");
        return this;
    }

    public GuideCategoryBuilder displayName(GuideText text) {
        this.displayName = Objects.requireNonNull(text, "text").resolve(null, GuideTextContext.empty());
        return this;
    }

    public GuideCategoryBuilder translatableDisplayName(String key) {
        this.displayName = Component.translatable(key);
        return this;
    }

    public GuideCategoryBuilder themeColor(int color) {
        this.themeColor = color;
        return this;
    }

    public GuideCategoryBuilder sortOrder(int order) {
        this.sortOrder = order;
        return this;
    }

    public GuideCategoryBuilder iconTexture(ResourceLocation texture) {
        this.iconTexture = texture;
        return this;
    }

    public GuideCategoryBuilder builtin() {
        this.builtin = true;
        return this;
    }

    public GuideCategory build() {
        Component finalDisplayName = displayName == null ? Component.literal(id.getPath()) : displayName;
        return new GuideCategory(id, finalDisplayName, themeColor, sortOrder, builtin, iconTexture);
    }
}
