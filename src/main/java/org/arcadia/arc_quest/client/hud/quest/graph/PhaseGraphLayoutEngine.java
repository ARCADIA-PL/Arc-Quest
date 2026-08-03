package org.arcadia.arc_quest.client.hud.quest.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class PhaseGraphLayoutEngine {
    private PhaseGraphLayoutEngine() {
    }

    public static List<GraphNodeLayout> layout(List<String> nodeIds,
                                                Function<String, ? extends Collection<String>> targets,
                                                int horizontalSpacing, int verticalSpacing) {
        Map<String, Integer> depths = new LinkedHashMap<>();
        Map<String, Integer> indegrees = new LinkedHashMap<>();
        for (String nodeId : nodeIds) {
            depths.putIfAbsent(nodeId, 0);
            indegrees.putIfAbsent(nodeId, 0);
        }
        for (String nodeId : nodeIds) {
            for (String targetId : targets.apply(nodeId)) {
                if (indegrees.containsKey(targetId)) {
                    indegrees.put(targetId, indegrees.get(targetId) + 1);
                }
            }
        }

        ArrayDeque<String> queue = new ArrayDeque<>();
        indegrees.forEach((nodeId, indegree) -> {
            if (indegree == 0) queue.addLast(nodeId);
        });
        while (!queue.isEmpty()) {
            String sourceId = queue.removeFirst();
            int sourceDepth = depths.getOrDefault(sourceId, 0);
            for (String targetId : targets.apply(sourceId)) {
                Integer indegree = indegrees.get(targetId);
                if (indegree == null) continue;
                depths.put(targetId, Math.max(depths.getOrDefault(targetId, 0), sourceDepth + 1));
                int remaining = indegree - 1;
                indegrees.put(targetId, remaining);
                if (remaining == 0) queue.addLast(targetId);
            }
        }

        Map<Integer, List<String>> layers = new LinkedHashMap<>();
        for (String nodeId : nodeIds) {
            layers.computeIfAbsent(depths.getOrDefault(nodeId, 0), ignored -> new ArrayList<>()).add(nodeId);
        }
        List<GraphNodeLayout> result = new ArrayList<>(nodeIds.size());
        for (Map.Entry<Integer, List<String>> layer : layers.entrySet()) {
            int total = layer.getValue().size();
            for (int index = 0; index < total; index++) {
                result.add(new GraphNodeLayout(layer.getValue().get(index),
                        layer.getKey() * horizontalSpacing,
                        Math.round((index - (total - 1) / 2f) * verticalSpacing),
                        layer.getKey()));
            }
        }
        return List.copyOf(result);
    }
}
