package org.arcadia.arc_quest.quest.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * 运行时任务分类句柄。
 *
 * 第一阶段先保留与旧 enum 常量同名的内置静态常量，
 * 以便渐进替换下游 QuestCategory.X 的调用点。
 */
public final class QuestCategory {

    private final ResourceLocation id;
    private final String translationKey;
    private final int themeColor;
    private final boolean builtin;
    private final int sortOrder;

    public static final QuestCategory ARCHON = QuestCategories.ARCHON;
    public static final QuestCategory COMPANION = QuestCategories.COMPANION;
    public static final QuestCategory DAILY = QuestCategories.DAILY;
    public static final QuestCategory ADVENTURE = QuestCategories.ADVENTURE;
    public static final QuestCategory EVENT = QuestCategories.EVENT;

    public QuestCategory(ResourceLocation id, String translationKey, int themeColor, boolean builtin) {
        this(id, translationKey, themeColor, builtin, Integer.MAX_VALUE);
    }

    public QuestCategory(ResourceLocation id, String translationKey, int themeColor,
                         boolean builtin, int sortOrder) {
        this.id = Objects.requireNonNull(id, "id");
        this.translationKey = translationKey == null ? "" : translationKey;
        this.themeColor = themeColor;
        this.builtin = builtin;
        this.sortOrder = sortOrder;
    }

    public ResourceLocation getId() {
        return id;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public Component getDisplayName() {
        return Component.translatable(translationKey);
    }

    public int getThemeColor() {
        return themeColor;
    }

    public boolean isBuiltin() {
        return builtin;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getPathToken() {
        return id.getPath();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QuestCategory that)) return false;
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
