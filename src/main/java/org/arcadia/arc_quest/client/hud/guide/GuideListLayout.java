package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.api.GuideGroupDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class GuideListLayout {

    private GuideListLayout() {
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
