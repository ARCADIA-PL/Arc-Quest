package org.arcadia.arc_quest.quest.api;

import net.minecraft.resources.ResourceLocation;

import java.util.*;

/** Display-only layout; never a completion or discovery rule. */
public record CollectionAtlasDefinition(
        int schemaVersion,
        ResourceLocation id,
        ResourceLocation profileId,
        List<Category> categories) {
    public CollectionAtlasDefinition {
        if (schemaVersion != 1 || id == null)
            throw new IllegalArgumentException("Invalid atlas version/id");
        categories = List.copyOf(categories);
        Set<String> seen = new HashSet<>();
        Set<ResourceLocation> entries = new HashSet<>();
        for (var c : categories) {
            if (!seen.add(c.categoryId()))
                throw new IllegalArgumentException("Duplicate atlas category");
            for (var g : c.groups())
                for (var n : g.nodes())
                    if (!entries.add(n.entryId()))
                        throw new IllegalArgumentException("Duplicate atlas node");
        }
    }

    public record Category(
            String categoryId,
            List<Group> groups,
            ResourceLocation layoutType,
            Map<String, Double> layoutParameters) {
        public Category(String id, List<Group> groups) {
            this(id, groups, ResourceLocation.parse("arc_quest:group_grid"), Map.of());
        }

        public Category {
            layoutType =
                    layoutType == null
                            ? ResourceLocation.parse("arc_quest:group_grid")
                            : layoutType;
            org.arcadia.arc_quest.quest.registry.CollectionAtlasLayoutRegistry.validate(
                    layoutType,
                    org.arcadia.arc_quest.quest.registry.CollectionAtlasLayoutRegistry.Scope
                            .CATEGORY,
                    layoutParameters);
            layoutParameters = Map.copyOf(layoutParameters == null ? Map.of() : layoutParameters);
            validateParameters(layoutParameters);
            if (categoryId == null || categoryId.isBlank())
                throw new IllegalArgumentException("categoryId required");
            groups = List.copyOf(groups);
        }
    }

    public record Group(
            String groupId,
            QuestText title,
            int columns,
            double x,
            double y,
            List<Node> nodes,
            ResourceLocation layoutType,
            Map<String, Double> layoutParameters) {
        public Group(
                String id, QuestText title, int columns, double x, double y, List<Node> nodes) {
            this(
                    id,
                    title,
                    columns,
                    x,
                    y,
                    nodes,
                    ResourceLocation.parse("arc_quest:node_grid"),
                    Map.of());
        }

        public Group {
            layoutType =
                    layoutType == null ? ResourceLocation.parse("arc_quest:node_grid") : layoutType;
            org.arcadia.arc_quest.quest.registry.CollectionAtlasLayoutRegistry.validate(
                    layoutType,
                    org.arcadia.arc_quest.quest.registry.CollectionAtlasLayoutRegistry.Scope.GROUP,
                    layoutParameters);
            layoutParameters = Map.copyOf(layoutParameters == null ? Map.of() : layoutParameters);
            validateParameters(layoutParameters);
            if (groupId == null
                    || groupId.isBlank()
                    || groupId.length() > 128
                    || columns < 1
                    || columns > 64
                    || !Double.isFinite(x)
                    || !Double.isFinite(y))
                throw new IllegalArgumentException("Invalid atlas group");
            nodes = List.copyOf(nodes);
        }
    }

    private static void validateParameters(Map<String, Double> parameters) {
        for (var v : parameters.values())
            if (v == null || !Double.isFinite(v) || Math.abs(v) > 100000)
                throw new IllegalArgumentException("Invalid layout parameter");
    }

    public record Node(ResourceLocation entryId, double x, double y) {
        public Node {
            if (entryId == null || !Double.isFinite(x) || !Double.isFinite(y))
                throw new IllegalArgumentException("Invalid atlas node");
        }
    }
}
