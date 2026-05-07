package org.arcadia.arc_quest.client.hud.quest.editor;

import org.arcadia.arc_quest.quest.editor.model.EditableConnection;
import org.arcadia.arc_quest.quest.editor.model.EditablePhase;
import org.arcadia.arc_quest.quest.editor.model.EditableQuest;
import org.arcadia.arc_quest.quest.editor.model.EditorNodePosition;

import java.util.*;

public class QuestEditorTreeLayoutService {
    private static final double X_GAP = 260;
    private static final double Y_GAP = 120;
    private static final double START_X = 40;
    private static final double START_Y = 40;

    public QuestEditorGraphLayout layout(EditableQuest quest) {
        QuestEditorGraphLayout out = new QuestEditorGraphLayout();
        if (quest == null || quest.phases == null || quest.phases.isEmpty()) return out;

        Map<String, EditablePhase> phaseByNode = new LinkedHashMap<>();
        for (EditablePhase p : quest.phases) {
            if (p.nodeId != null && !p.nodeId.isBlank()) phaseByNode.put(p.nodeId, p);
        }

        Map<String, List<String>> next = new HashMap<>();
        for (EditableConnection c : quest.connections) {
            if (!phaseByNode.containsKey(c.sourcePhaseNodeId) || !phaseByNode.containsKey(c.targetPhaseNodeId)) continue;
            next.computeIfAbsent(c.sourcePhaseNodeId, k -> new ArrayList<>()).add(c.targetPhaseNodeId);

            EditorEdgeLayout e = new EditorEdgeLayout();
            e.connectionId = c.connectionId;
            e.sourceNodeId = c.sourcePhaseNodeId;
            e.targetNodeId = c.targetPhaseNodeId;
            e.edgeType = c.connectionType == null ? "TRANSITION" : c.connectionType.name();
            out.edges.add(e);
        }

        for (List<String> list : next.values()) list.sort(String::compareTo);

        String initial = quest.initialPhaseNodeId;
        if (initial == null || initial.isBlank() || !phaseByNode.containsKey(initial)) {
            initial = quest.phases.get(0).nodeId;
        }

        Map<String, Integer> depth = new HashMap<>();
        Deque<String> dq = new ArrayDeque<>();
        depth.put(initial, 0);
        dq.add(initial);
        while (!dq.isEmpty()) {
            String cur = dq.removeFirst();
            int d = depth.get(cur);
            for (String n : next.getOrDefault(cur, List.of())) {
                if (!depth.containsKey(n)) {
                    depth.put(n, d + 1);
                    dq.addLast(n);
                }
            }
        }

        Map<Integer, List<String>> layers = new TreeMap<>();
        for (var e : depth.entrySet()) layers.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
        for (List<String> ids : layers.values()) ids.sort(String::compareTo);

        for (var layer : layers.entrySet()) {
            int d = layer.getKey();
            List<String> ids = layer.getValue();
            for (int i = 0; i < ids.size(); i++) {
                var manual = quest.layout.phasePositions.get(ids.get(i));
                boolean useManual = manual != null && (Math.abs(manual.x) > 1e-6 || Math.abs(manual.y) > 1e-6);
                double x = useManual ? manual.x : START_X + d * X_GAP;
                double y = useManual ? manual.y : START_Y + i * Y_GAP;
                out.nodePositions.put(ids.get(i), new EditorNodePosition(x, y));
            }
        }

        int maxDepth = layers.isEmpty() ? 0 : layers.keySet().stream().max(Integer::compareTo).orElse(0);
        int idx = 0;
        List<String> all = new ArrayList<>(phaseByNode.keySet());
        Collections.sort(all);
        for (String nodeId : all) {
            if (depth.containsKey(nodeId)) continue;
            int row = idx % 8;
            int col = idx / 8;
            var manual = quest.layout.phasePositions.get(nodeId);
            boolean useManual = manual != null && (Math.abs(manual.x) > 1e-6 || Math.abs(manual.y) > 1e-6);
            double x = useManual ? manual.x : START_X + (maxDepth + 2 + col) * X_GAP;
            double y = useManual ? manual.y : START_Y + row * Y_GAP;
            out.nodePositions.put(nodeId, new EditorNodePosition(x, y));
            out.unreachableNodeIds.add(nodeId);
            idx++;
        }

        computeBounds(out);
        return out;
    }

    private void computeBounds(QuestEditorGraphLayout layout) {
        if (layout.nodePositions.isEmpty()) return;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (EditorNodePosition p : layout.nodePositions.values()) {
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x + 180);
            maxY = Math.max(maxY, p.y + 80);
        }
        layout.bounds.minX = minX;
        layout.bounds.minY = minY;
        layout.bounds.maxX = maxX;
        layout.bounds.maxY = maxY;
    }
}
