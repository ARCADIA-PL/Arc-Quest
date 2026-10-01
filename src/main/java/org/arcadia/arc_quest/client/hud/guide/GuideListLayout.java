package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.guide.api.GuideCategory;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.api.GuideGroupDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

final class GuideListLayout {

    private GuideListLayout() {
    }

    /** Resolve the current view once, without caching dynamic visibility or search text. */
    static List<GuideCategory> visibleCategories(Collection<GuideCategory> categories,
                                                 Collection<GuideDefinition> guides,
                                                 Predicate<GuideDefinition> matches) {
        if (categories.isEmpty()) return List.of();
        var registered = new HashSet<ResourceLocation>();
        for (GuideCategory category : categories) registered.add(category.getId());
        var populated = new HashSet<ResourceLocation>();
        for (GuideDefinition guide : guides) {
            ResourceLocation categoryId = guide.getCategory().getId();
            if (registered.contains(categoryId) && !populated.contains(categoryId) && matches.test(guide)) {
                populated.add(categoryId);
                if (populated.size() == registered.size()) break;
            }
        }
        List<GuideCategory> result = new ArrayList<>();
        for (GuideCategory category : categories) {
            if (populated.contains(category.getId())) result.add(category);
        }
        result.sort(Comparator.comparingInt(GuideCategory::getSortOrder)
                .thenComparing(category -> category.getId().toString()));
        return result;
    }

    static List<Row> build(List<GuideDefinition> guides,
                           Function<GuideDefinition, GuideGroupDefinition> groupResolver) {
        Map<GuideGroupDefinition, List<GuideDefinition>> grouped = new LinkedHashMap<>();
        List<GuideDefinition> ungrouped = new ArrayList<>();
        for (GuideDefinition guide : guides) {
            GuideGroupDefinition group = groupResolver.apply(guide);
            if (group == null) ungrouped.add(guide);
            else grouped.computeIfAbsent(group, ignored -> new ArrayList<>()).add(guide);
        }

        List<Map.Entry<GuideGroupDefinition, List<GuideDefinition>>> orderedGroups = new ArrayList<>(grouped.entrySet());
        orderedGroups.sort(Map.Entry.comparingByKey(
                Comparator.comparingInt(GuideGroupDefinition::getSortOrder)
                        .thenComparing(group -> group.getId().toString())));

        List<Row> rows = new ArrayList<>(guides.size() + orderedGroups.size());
        for (Map.Entry<GuideGroupDefinition, List<GuideDefinition>> entry : orderedGroups) {
            GuideGroupDefinition group = entry.getKey();
            List<GuideDefinition> children = List.copyOf(entry.getValue());
            rows.add(new GroupRow(group, children));
            for (GuideDefinition guide : children) rows.add(new GuideRow(guide, group.getId()));
        }
        for (GuideDefinition guide : ungrouped) rows.add(new GuideRow(guide, null));
        return List.copyOf(rows);
    }

    sealed interface Row permits GroupRow, GuideRow {
    }

    record GroupRow(GuideGroupDefinition group, List<GuideDefinition> guides) implements Row {
    }

    record GuideRow(GuideDefinition guide, ResourceLocation groupId) implements Row {
        boolean grouped() {
            return groupId != null;
        }
    }
}
