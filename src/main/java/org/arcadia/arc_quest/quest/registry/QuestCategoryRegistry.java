package org.arcadia.arc_quest.quest.registry;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.QuestCategory;
import org.arcadia.arc_quest.quest.api.QuestCategoryDefinition;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class QuestCategoryRegistry {

    private static final Map<ResourceLocation, QuestCategory> CATEGORIES = new LinkedHashMap<>();

    private QuestCategoryRegistry() {
    }

    public static synchronized QuestCategory register(ResourceLocation id, QuestCategoryDefinition definition) {
        if (CATEGORIES.containsKey(id)) {
            throw new IllegalStateException("Duplicate quest category id: " + id);
        }
        QuestCategory category = new QuestCategory(
                id,
                definition.translationKey(),
                definition.themeColor(),
                definition.builtin(),
                definition.sortOrder()
        );
        CATEGORIES.put(id, category);
        return category;
    }

    @Nullable
    public static synchronized QuestCategory get(ResourceLocation id) {
        return CATEGORIES.get(id);
    }

    public static synchronized QuestCategory require(ResourceLocation id) {
        QuestCategory category = CATEGORIES.get(id);
        if (category == null) {
            throw new IllegalArgumentException("Unknown quest category id: " + id);
        }
        return category;
    }

    public static synchronized boolean contains(ResourceLocation id) {
        return CATEGORIES.containsKey(id);
    }

    public static synchronized Collection<QuestCategory> all() {
        return Collections.unmodifiableList(CATEGORIES.values().stream().toList());
    }
}
