package org.arcadia.arc_quest.client.hud.quest.editor;

import org.arcadia.arc_quest.quest.editor.mapper.EditableQuestToQuestSpecMapper;
import org.arcadia.arc_quest.quest.editor.mapper.QuestEditorRoundTripVerifier;
import org.arcadia.arc_quest.quest.editor.mapper.QuestSpecToEditableQuestMapper;
import org.arcadia.arc_quest.quest.editor.mapper.SampleQuestRoundTripDebugHelper;
import org.arcadia.arc_quest.quest.editor.model.EditableConnection;
import org.arcadia.arc_quest.quest.editor.model.EditableConnectionType;
import org.arcadia.arc_quest.quest.editor.model.EditableChoice;
import org.arcadia.arc_quest.quest.editor.model.EditableObjective;
import org.arcadia.arc_quest.quest.editor.model.EditablePhase;
import org.arcadia.arc_quest.quest.editor.model.EditableQuest;
import org.arcadia.arc_quest.quest.editor.service.EditableQuestGraphService;
import org.arcadia.arc_quest.quest.editor.service.EditableQuestService;
import org.arcadia.arc_quest.quest.editor.service.EditableQuestValidationService;
import org.arcadia.arc_quest.quest.editor.service.EditableValidationIssue;
import org.arcadia.arc_quest.quest.editor.service.EditableValidationReport;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.spec.QuestTextSpec;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class QuestEditorController {
    private final EditableQuestService questService = new EditableQuestService();
    private final EditableQuestToQuestSpecMapper editableToSpecMapper = new EditableQuestToQuestSpecMapper();
    private final QuestSpecToEditableQuestMapper specToEditableMapper = new QuestSpecToEditableQuestMapper();
    private final QuestEditorRoundTripVerifier roundTripVerifier = new QuestEditorRoundTripVerifier();
    private final EditableQuestValidationService validationService = new EditableQuestValidationService();
    private final EditableQuestGraphService graphService = new EditableQuestGraphService();
    private final EditorCommandBus commandBus = new EditorCommandBus();
    private EditableQuest quest = new EditableQuest();
    private EditorSelection selection = EditorSelection.none();
    private EditableValidationReport validationReport = new EditableValidationReport();
    private String statusText = "未加载任务";
    private EditableConnectionType linkCreateType = EditableConnectionType.TRANSITION;
    private final LinkedHashSet<String> selectedPhaseNodeIds = new LinkedHashSet<>();
    private long revision = 0;
    private long savepointRevision = 0;
    private double autoLayoutLayerGap = 260;
    private double autoLayoutRowGap = 120;
    private int autoLayoutUnreachablePerLayer = 8;

    public void loadSampleQuest(Path workspaceRoot) {
        try {
            var report = SampleQuestRoundTripDebugHelper.verifySampleQuestFromWorkspace(workspaceRoot);
            this.quest = report.editable();
            this.selection = quest.phases.isEmpty() ? EditorSelection.quest() : EditorSelection.phase(resolveInitialPhaseNodeId());
            this.commandBus.clear();
            this.revision = 0;
            this.savepointRevision = 0;
            selectedPhaseNodeIds.clear();
            if (selection.type() == EditorSelectionType.PHASE) selectedPhaseNodeIds.add(selection.phaseNodeId());
            validate();
            this.statusText = report.success() ? "Sample quest 已导入" : "Sample quest 已导入，但 round-trip 存在差异";
        } catch (Exception ex) {
            this.quest = new EditableQuest();
            this.selection = EditorSelection.none();
            this.validationReport = new EditableValidationReport();
            this.statusText = "导入 sample 失败: " + ex.getMessage();
        }
    }

    public void createNewQuest(String questId) {
        String normalizedId = (questId == null || questId.isBlank()) ? "new_quest" : questId.trim();
        this.quest = new EditableQuest();
        quest.meta.questId = normalizedId;
        quest.meta.displayName = QuestTextSpec.literal(normalizedId);
        EditablePhase first = questService.addPhase(quest, "start", 40, 40);
        quest.initialPhaseNodeId = first.nodeId;
        this.selection = EditorSelection.phase(first.nodeId);
        this.commandBus.clear();
        this.revision = 0;
        this.savepointRevision = 0;
        selectedPhaseNodeIds.clear();
        selectedPhaseNodeIds.add(first.nodeId);
        validate();
        this.statusText = "已新建 Quest: " + normalizedId;
    }

    public void validate() {
        this.validationReport = validationService.validate(quest);
    }

    public boolean updateQuestMeta(String questId, String displayName) {
        return executeMutation("Quest 基础信息已更新", () -> {
            boolean changed = questService.updateQuestId(quest, questId);
            changed |= questService.updateQuestDisplay(quest, QuestTextSpec.literal(displayName), quest.meta.description);
            return changed;
        });
    }

    public boolean updateSelectedPhaseBasics(String phaseId, String displayName) {
        return executeMutation("Phase 基础信息已更新", () -> {
            EditablePhase phase = selectedPhase();
            if (phase == null) return false;
            boolean changed = questService.updatePhaseId(quest, phase.nodeId, phaseId);
            changed |= questService.updatePhaseDisplay(quest, phase.nodeId, QuestTextSpec.literal(displayName), phase.description, phase.story);
            return changed;
        });
    }

    public EditableObjective addObjectiveToSelectedPhase() {
        EditablePhase phase = selectedPhase();
        if (phase == null) return null;
        boolean changed = executeMutation("已新增 Objective", () -> {
            EditablePhase selected = selectedPhase();
            if (selected == null) return false;
            EditableObjective created = questService.addObjective(quest, selected.nodeId);
            this.selection = EditorSelection.objective(selected.nodeId, created.objectiveId);
            return true;
        });
        return changed ? selectedObjective() : null;
    }

    public boolean removeSelectedObjective() {
        return executeMutation("已删除 Objective", () -> {
            EditablePhase phase = selectedPhase();
            EditableObjective objective = selectedObjective();
            if (phase == null || objective == null) return false;
            boolean changed = questService.removeObjective(quest, phase.nodeId, objective.objectiveId);
            if (changed) this.selection = EditorSelection.phase(phase.nodeId);
            return changed;
        });
    }

    public boolean moveSelectedObjectiveUp() {
        return executeMutation("Objective 上移", () -> {
            EditablePhase phase = selectedPhase();
            EditableObjective objective = selectedObjective();
            if (phase == null || objective == null) return false;
            int index = indexOfObjective(phase, objective.objectiveId);
            if (index <= 0) return false;
            return questService.moveObjective(quest, phase.nodeId, objective.objectiveId, index - 1);
        });
    }

    public boolean moveSelectedObjectiveDown() {
        return executeMutation("Objective 下移", () -> {
            EditablePhase phase = selectedPhase();
            EditableObjective objective = selectedObjective();
            if (phase == null || objective == null) return false;
            int index = indexOfObjective(phase, objective.objectiveId);
            if (index < 0 || index >= phase.objectives.size() - 1) return false;
            return questService.moveObjective(quest, phase.nodeId, objective.objectiveId, index + 1);
        });
    }

    public boolean selectNextObjective() {
        EditablePhase phase = selectedPhase();
        if (phase == null || phase.objectives.isEmpty()) return false;
        EditableObjective current = selectedObjective();
        int index = current == null ? -1 : indexOfObjective(phase, current.objectiveId);
        int next = Math.min(phase.objectives.size() - 1, index + 1);
        EditableObjective target = phase.objectives.get(next);
        this.selection = EditorSelection.objective(phase.nodeId, target.objectiveId);
        return true;
    }

    public boolean selectPreviousObjective() {
        EditablePhase phase = selectedPhase();
        if (phase == null || phase.objectives.isEmpty()) return false;
        EditableObjective current = selectedObjective();
        int index = current == null ? 1 : indexOfObjective(phase, current.objectiveId);
        int prev = Math.max(0, index - 1);
        EditableObjective target = phase.objectives.get(prev);
        this.selection = EditorSelection.objective(phase.nodeId, target.objectiveId);
        return true;
    }

    public boolean updateSelectedObjective(String targetId, int requiredCount, String displayText) {
        return executeMutation("Objective 基础信息已更新", () -> {
            EditablePhase phase = selectedPhase();
            EditableObjective objective = selectedObjective();
            if (phase == null || objective == null) return false;
            return questService.updateObjectiveBasic(quest, phase.nodeId, objective.objectiveId, targetId, requiredCount, QuestTextSpec.literal(displayText));
        });
    }

    public EditableChoice addChoiceToSelectedPhase() {
        EditablePhase phase = selectedPhase();
        if (phase == null) return null;
        boolean changed = executeMutation("已新增 Choice", () -> {
            EditablePhase selected = selectedPhase();
            if (selected == null) return false;
            EditableChoice created = questService.addChoice(quest, selected.nodeId, selected.nodeId, "", "", null);
            this.selection = EditorSelection.choice(selected.nodeId, created.choiceId);
            return true;
        });
        return changed ? selectedChoice() : null;
    }

    public boolean removeSelectedChoice() {
        return executeMutation("已删除 Choice", () -> {
            EditablePhase phase = selectedPhase();
            EditableChoice choice = selectedChoice();
            if (phase == null || choice == null) return false;
            boolean changed = questService.removeChoice(quest, phase.nodeId, choice.choiceId);
            if (changed) this.selection = EditorSelection.phase(phase.nodeId);
            return changed;
        });
    }

    public boolean selectNextChoice() {
        EditablePhase phase = selectedPhase();
        if (phase == null || phase.choices.isEmpty()) return false;
        int index = indexOfChoice(phase, selection.choiceId());
        int next = Math.min(phase.choices.size() - 1, index + 1);
        this.selection = EditorSelection.choice(phase.nodeId, phase.choices.get(next).choiceId);
        return true;
    }

    public boolean selectPreviousChoice() {
        EditablePhase phase = selectedPhase();
        if (phase == null || phase.choices.isEmpty()) return false;
        int index = indexOfChoice(phase, selection.choiceId());
        if (index < 0) index = 1;
        int prev = Math.max(0, index - 1);
        this.selection = EditorSelection.choice(phase.nodeId, phase.choices.get(prev).choiceId);
        return true;
    }

    public boolean updateSelectedChoice(String text, String targetPhaseNodeId) {
        return executeMutation("Choice 基础信息已更新", () -> {
            EditablePhase phase = selectedPhase();
            EditableChoice choice = selectedChoice();
            if (phase == null || choice == null) return false;
            boolean changed = questService.updateChoiceText(quest, phase.nodeId, choice.choiceId, QuestTextSpec.literal(text), choice.flagToSet, choice.visibleCondition);
            changed |= questService.updateChoiceTarget(quest, phase.nodeId, choice.choiceId, targetPhaseNodeId);
            return changed;
        });
    }

    public EditableChoice selectedChoice() {
        EditablePhase phase = selectedPhase();
        if (phase == null || selection == null || selection.type() != EditorSelectionType.CHOICE) return null;
        for (EditableChoice choice : phase.choices) {
            if (selection.choiceId().equals(choice.choiceId)) return choice;
        }
        return null;
    }

    public boolean selectObjective(String objectiveId) {
        EditablePhase phase = selectedPhase();
        if (phase == null) return false;
        for (EditableObjective objective : phase.objectives) {
            if (objectiveId.equals(objective.objectiveId)) {
                this.selection = EditorSelection.objective(phase.nodeId, objectiveId);
                return true;
            }
        }
        return false;
    }

    public boolean moveSelectedPhaseNode(double x, double y) {
        return executeMutation("节点位置已更新", () -> {
            EditablePhase phase = selectedPhase();
            if (phase == null) return false;
            return questService.movePhaseNode(quest, phase.nodeId, x, y);
        });
    }

    public boolean moveSelectedPhasesByDelta(double dx, double dy) {
        if (Math.abs(dx) < 1e-6 && Math.abs(dy) < 1e-6) return false;
        Set<String> ids = selectedPhaseNodeIds();
        if (ids.isEmpty()) return false;
        Map<String, org.arcadia.arc_quest.quest.editor.model.EditorNodePosition> before = new LinkedHashMap<>();
        for (String id : ids) {
            var pos = quest.layout.phasePositions.get(id);
            if (pos != null) before.put(id, pos);
        }
        if (before.isEmpty()) return false;
        return executeMutation("多选节点移动", () -> {
            boolean changed = false;
            for (var entry : before.entrySet()) {
                String id = entry.getKey();
                var pos = entry.getValue();
                changed |= questService.movePhaseNode(quest, id, Math.max(0, pos.x + dx), Math.max(0, pos.y + dy));
            }
            return changed;
        });
    }

    public boolean alignSelectedPhasesLeft() {
        Set<String> ids = selectedPhaseNodeIds();
        if (ids.size() < 2) return false;
        Map<String, org.arcadia.arc_quest.quest.editor.model.EditorNodePosition> before = new LinkedHashMap<>();
        double minX = Double.MAX_VALUE;
        for (String id : ids) {
            var pos = quest.layout.phasePositions.get(id);
            if (pos == null) continue;
            before.put(id, pos);
            minX = Math.min(minX, pos.x);
        }
        if (before.size() < 2) return false;
        final double targetX = minX;
        return executeMutation("多选节点左对齐", () -> {
            boolean changed = false;
            for (var entry : before.entrySet()) {
                String id = entry.getKey();
                var pos = entry.getValue();
                changed |= questService.movePhaseNode(quest, id, targetX, pos.y);
            }
            return changed;
        });
    }

    public boolean distributeSelectedPhasesHorizontally() {
        Set<String> ids = selectedPhaseNodeIds();
        if (ids.size() < 3) return false;
        List<Map.Entry<String, org.arcadia.arc_quest.quest.editor.model.EditorNodePosition>> entries = new java.util.ArrayList<>();
        for (String id : ids) {
            var pos = quest.layout.phasePositions.get(id);
            if (pos != null) entries.add(Map.entry(id, pos));
        }
        if (entries.size() < 3) return false;
        entries.sort(java.util.Comparator.comparingDouble(e -> e.getValue().x));
        double left = entries.get(0).getValue().x;
        double right = entries.get(entries.size() - 1).getValue().x;
        double step = (right - left) / (entries.size() - 1);
        return executeMutation("多选节点水平分布", () -> {
            boolean changed = false;
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.get(i);
                double x = left + step * i;
                changed |= questService.movePhaseNode(quest, entry.getKey(), x, entry.getValue().y);
            }
            return changed;
        });
    }

    public boolean alignSelectedPhasesTop() {
        Set<String> ids = selectedPhaseNodeIds();
        if (ids.size() < 2) return false;
        Map<String, org.arcadia.arc_quest.quest.editor.model.EditorNodePosition> before = new LinkedHashMap<>();
        double minY = Double.MAX_VALUE;
        for (String id : ids) {
            var pos = quest.layout.phasePositions.get(id);
            if (pos == null) continue;
            before.put(id, pos);
            minY = Math.min(minY, pos.y);
        }
        if (before.size() < 2) return false;
        final double targetY = minY;
        return executeMutation("多选节点顶部对齐", () -> {
            boolean changed = false;
            for (var entry : before.entrySet()) {
                String id = entry.getKey();
                var pos = entry.getValue();
                changed |= questService.movePhaseNode(quest, id, pos.x, targetY);
            }
            return changed;
        });
    }

    public boolean distributeSelectedPhasesVertically() {
        Set<String> ids = selectedPhaseNodeIds();
        if (ids.size() < 3) return false;
        List<Map.Entry<String, org.arcadia.arc_quest.quest.editor.model.EditorNodePosition>> entries = new java.util.ArrayList<>();
        for (String id : ids) {
            var pos = quest.layout.phasePositions.get(id);
            if (pos != null) entries.add(Map.entry(id, pos));
        }
        if (entries.size() < 3) return false;
        entries.sort(java.util.Comparator.comparingDouble(e -> e.getValue().y));
        double top = entries.get(0).getValue().y;
        double bottom = entries.get(entries.size() - 1).getValue().y;
        double step = (bottom - top) / (entries.size() - 1);
        return executeMutation("多选节点垂直分布", () -> {
            boolean changed = false;
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.get(i);
                double y = top + step * i;
                changed |= questService.movePhaseNode(quest, entry.getKey(), entry.getValue().x, y);
            }
            return changed;
        });
    }

    public boolean autoLayout2() {
        if (quest == null || quest.phases == null || quest.phases.isEmpty()) return false;

        Map<String, EditablePhase> phaseById = new LinkedHashMap<>();
        for (EditablePhase phase : quest.phases) {
            if (phase.nodeId != null && !phase.nodeId.isBlank()) phaseById.put(phase.nodeId, phase);
        }
        if (phaseById.isEmpty()) return false;

        Map<String, List<String>> out = new HashMap<>();
        for (EditableConnection connection : quest.connections) {
            if (connection.sourcePhaseNodeId == null || connection.targetPhaseNodeId == null) continue;
            if (!phaseById.containsKey(connection.sourcePhaseNodeId) || !phaseById.containsKey(connection.targetPhaseNodeId)) continue;
            out.computeIfAbsent(connection.sourcePhaseNodeId, k -> new ArrayList<>()).add(connection.targetPhaseNodeId);
        }

        Map<String, Integer> depth = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        String root = resolveInitialPhaseNodeId();
        if (root != null && !root.isBlank() && phaseById.containsKey(root)) {
            depth.put(root, 0);
            queue.addLast(root);
        }
        while (!queue.isEmpty()) {
            String cur = queue.removeFirst();
            int d = depth.get(cur);
            for (String next : out.getOrDefault(cur, List.of())) {
                if (!depth.containsKey(next)) {
                    depth.put(next, d + 1);
                    queue.addLast(next);
                }
            }
        }

        int maxDepth = 0;
        for (int d : depth.values()) maxDepth = Math.max(maxDepth, d);

        Map<Integer, List<String>> layers = new LinkedHashMap<>();
        for (EditablePhase phase : quest.phases) {
            String id = phase.nodeId;
            if (id == null || id.isBlank()) continue;
            if (depth.containsKey(id)) {
                layers.computeIfAbsent(depth.get(id), k -> new ArrayList<>()).add(id);
            }
        }
        for (List<String> layerIds : layers.values()) {
            layerIds.sort(String::compareTo);
        }

        int unreachableStart = maxDepth + 2;
        int unreachableIndex = 0;
        int perLayer = Math.max(1, autoLayoutUnreachablePerLayer);
        for (EditablePhase phase : quest.phases) {
            String id = phase.nodeId;
            if (id == null || id.isBlank() || depth.containsKey(id)) continue;
            int layer = unreachableStart + (unreachableIndex / perLayer);
            layers.computeIfAbsent(layer, k -> new ArrayList<>()).add(id);
            unreachableIndex++;
        }
        for (List<String> layerIds : layers.values()) {
            layerIds.sort(String::compareTo);
        }

        final double xGap = Math.max(80, autoLayoutLayerGap);
        final double yGap = Math.max(60, autoLayoutRowGap);
        return executeMutation("自动布局 2.0", () -> {
            boolean changed = false;
            for (Map.Entry<Integer, List<String>> entry : layers.entrySet()) {
                int layer = entry.getKey();
                List<String> ids = entry.getValue();
                for (int i = 0; i < ids.size(); i++) {
                    String id = ids.get(i);
                    double nx = 40 + layer * xGap;
                    double ny = 40 + i * yGap;
                    var old = quest.layout.phasePositions.get(id);
                    if (old == null || Math.abs(old.x - nx) > 1e-6 || Math.abs(old.y - ny) > 1e-6) {
                        changed |= questService.movePhaseNode(quest, id, nx, ny);
                    }
                }
            }
            return changed;
        });
    }

    public boolean updateAutoLayoutConfig(double layerGap, double rowGap, int unreachablePerLayer) {
        autoLayoutLayerGap = Math.max(80, layerGap);
        autoLayoutRowGap = Math.max(60, rowGap);
        autoLayoutUnreachablePerLayer = Math.max(1, unreachablePerLayer);
        statusText = "自动布局参数已更新";
        return true;
    }

    public double autoLayoutLayerGap() { return autoLayoutLayerGap; }
    public double autoLayoutRowGap() { return autoLayoutRowGap; }
    public int autoLayoutUnreachablePerLayer() { return autoLayoutUnreachablePerLayer; }

    public EditableConnectionType linkCreateType() { return linkCreateType; }

    public void toggleLinkCreateType() {
        linkCreateType = (linkCreateType == EditableConnectionType.TRANSITION) ? EditableConnectionType.CHOICE : EditableConnectionType.TRANSITION;
        statusText = "连线模式: " + (linkCreateType == EditableConnectionType.TRANSITION ? "TRANSITION" : "CHOICE");
    }

    public boolean createConnection(String sourceNodeId, String targetNodeId) {
        if (sourceNodeId == null || targetNodeId == null || sourceNodeId.isBlank() || targetNodeId.isBlank()) return false;
        if (sourceNodeId.equals(targetNodeId)) {
            statusText = "连接失败: 不支持自环";
            return false;
        }

        if (linkCreateType == EditableConnectionType.TRANSITION) {
            if (hasTransitionConnection(sourceNodeId, targetNodeId)) {
                statusText = "连接失败: 已存在 Transition";
                return false;
            }
            return executeMutation("已创建 Transition 连接", () -> {
                if (questService.getPhase(quest, sourceNodeId) == null || questService.getPhase(quest, targetNodeId) == null) return false;
                questService.connectTransition(quest, sourceNodeId, targetNodeId, null);
                return true;
            });
        }

        return executeMutation("已创建 Choice 连接", () -> {
            if (questService.getPhase(quest, sourceNodeId) == null || questService.getPhase(quest, targetNodeId) == null) return false;
            EditableChoice choice = questService.addChoice(quest, sourceNodeId, targetNodeId, "", "", null);
            if (choice == null) return false;
            selection = EditorSelection.choice(sourceNodeId, choice.choiceId);
            return true;
        });
    }

    public boolean removeSelectedConnection() {
        if (selection == null || selection.type() != EditorSelectionType.CONNECTION) return false;
        String connectionId = selection.connectionId();
        return executeMutation("已删除连接", () -> {
            EditableConnection selected = selectedConnection();
            if (selected == null) return false;
            if (selected.connectionType == EditableConnectionType.CHOICE && selected.choiceId != null && !selected.choiceId.isBlank()) {
                return questService.removeChoice(quest, selected.sourcePhaseNodeId, selected.choiceId);
            }
            boolean removed = quest.connections.removeIf(c -> connectionId.equals(c.connectionId));
            if (removed) selection = EditorSelection.none();
            return removed;
        });
    }

    public boolean updateSelectedConnectionTarget(String targetNodeId) {
        EditableConnection selected = selectedConnection();
        if (selected == null || targetNodeId == null || targetNodeId.isBlank()) return false;
        if (questService.getPhase(quest, targetNodeId) == null) {
            statusText = "连接更新失败: 目标节点不存在";
            return false;
        }
        return executeMutation("已更新连接目标", () -> {
            EditableConnection connection = selectedConnection();
            if (connection == null) return false;
            if (connection.connectionType == EditableConnectionType.CHOICE && connection.choiceId != null && !connection.choiceId.isBlank()) {
                return questService.updateChoiceTarget(quest, connection.sourcePhaseNodeId, connection.choiceId, targetNodeId);
            }
            connection.targetPhaseNodeId = targetNodeId;
            return true;
        });
    }

    public EditableQuest quest() {
        return quest;
    }

    public EditorSelection selection() {
        return selection;
    }

    public void setSelection(EditorSelection selection) {
        this.selection = selection == null ? EditorSelection.none() : selection;
        if (this.selection.type() == EditorSelectionType.PHASE) {
            selectedPhaseNodeIds.clear();
            selectedPhaseNodeIds.add(this.selection.phaseNodeId());
        }
    }

    public EditableValidationReport validationReport() {
        return validationReport;
    }

    public List<EditableValidationIssue> issues() {
        return validationReport == null ? List.of() : validationReport.issues();
    }

    public List<EditablePhase> phases() {
        return quest == null || quest.phases == null ? List.of() : quest.phases;
    }

    public List<EditablePhase> unreachablePhases() {
        return quest == null ? List.of() : graphService.getUnreachablePhases(quest);
    }

    public String statusText() {
        return statusText;
    }

    public void setStatusText(String statusText) {
        this.statusText = statusText == null ? "" : statusText;
    }

    public String selectedSummary() {
        if (selection == null) return "未选中";
        return switch (selection.type()) {
            case NONE -> "未选中";
            case QUEST -> "当前编辑：Quest";
            case PHASE -> "当前编辑：Phase / " + selection.phaseNodeId();
            case OBJECTIVE -> "当前编辑：Objective / " + selection.objectiveId();
            case CHOICE -> "当前编辑：Choice / " + selection.choiceId();
            case CONNECTION -> "当前编辑：Connection / " + selection.connectionId();
        };
    }

    public EditablePhase selectedPhase() {
        if (selection == null) return null;
        if (selection.type() != EditorSelectionType.PHASE && selection.type() != EditorSelectionType.OBJECTIVE && selection.type() != EditorSelectionType.CHOICE) return null;
        for (EditablePhase phase : phases()) {
            if (selection.phaseNodeId().equals(phase.nodeId)) return phase;
        }
        return null;
    }

    public EditableObjective selectedObjective() {
        EditablePhase phase = selectedPhase();
        if (phase == null || selection == null || selection.type() != EditorSelectionType.OBJECTIVE) return null;
        for (EditableObjective objective : phase.objectives) {
            if (selection.objectiveId().equals(objective.objectiveId)) return objective;
        }
        return null;
    }

    public EditableConnection selectedConnection() {
        if (selection == null || selection.type() != EditorSelectionType.CONNECTION) return null;
        for (EditableConnection connection : quest.connections) {
            if (selection.connectionId().equals(connection.connectionId)) return connection;
        }
        return null;
    }

    public EditablePhase phaseByNodeId(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) return null;
        for (EditablePhase phase : phases()) {
            if (nodeId.equals(phase.nodeId)) return phase;
        }
        return null;
    }

    public boolean selectPhaseByNodeId(String nodeId) {
        EditablePhase phase = phaseByNodeId(nodeId);
        if (phase == null) return false;
        selection = EditorSelection.phase(nodeId);
        selectedPhaseNodeIds.clear();
        selectedPhaseNodeIds.add(nodeId);
        statusText = "已定位到 Phase: " + phase.phaseId;
        return true;
    }

    public boolean selectByValidationIssue(EditableValidationIssue issue) {
        if (issue == null || issue.path() == null) return false;
        String path = issue.path();
        if (path.startsWith("phases[")) {
            int idxEnd = path.indexOf(']');
            if (idxEnd > 7) {
                String idxText = path.substring(7, idxEnd);
                try {
                    int idx = Integer.parseInt(idxText);
                    if (idx >= 0 && idx < phases().size()) {
                        EditablePhase phase = phases().get(idx);
                        return selectPhaseByNodeId(phase.nodeId);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (path.startsWith("connections[")) {
            int idxEnd = path.indexOf(']');
            if (idxEnd > 12) {
                String idxText = path.substring(12, idxEnd);
                try {
                    int idx = Integer.parseInt(idxText);
                    if (quest.connections != null && idx >= 0 && idx < quest.connections.size()) {
                        EditableConnection connection = quest.connections.get(idx);
                        selection = new EditorSelection(EditorSelectionType.CONNECTION, "", "", "", connection.connectionId);
                        statusText = "已定位到 Connection: " + connection.connectionId;
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return false;
    }

    public Set<String> selectedPhaseNodeIds() {
        return Set.copyOf(selectedPhaseNodeIds);
    }

    public void clearPhaseMultiSelection() {
        selectedPhaseNodeIds.clear();
    }

    public void addPhaseToSelection(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) return;
        if (phaseByNodeId(nodeId) == null) return;
        selectedPhaseNodeIds.add(nodeId);
        selection = EditorSelection.phase(nodeId);
    }

    public void setPhaseMultiSelection(Set<String> nodeIds) {
        selectedPhaseNodeIds.clear();
        if (nodeIds == null) return;
        for (String nodeId : nodeIds) {
            if (phaseByNodeId(nodeId) != null) selectedPhaseNodeIds.add(nodeId);
        }
        if (!selectedPhaseNodeIds.isEmpty()) {
            String last = "";
            for (String nodeId : selectedPhaseNodeIds) last = nodeId;
            selection = EditorSelection.phase(last);
        }
    }

    public boolean isUnreachable(String nodeId) {
        for (EditablePhase phase : unreachablePhases()) {
            if (nodeId.equals(phase.nodeId)) return true;
        }
        return false;
    }

    public List<String> initialReachableNodes() {
        if (quest == null) return Collections.emptyList();
        return List.copyOf(graphService.getReachablePhaseNodeIds(quest));
    }

    public boolean exportToWorkspace(Path workspaceRoot) {
        try {
            QuestSpec mappedSpec = editableToSpecMapper.map(quest);
            QuestEditorRoundTripVerifier.RoundTripReport guardReport = roundTripVerifier.verify(mappedSpec);
            if (!guardReport.success()) {
                String firstError = guardReport.errors().isEmpty() ? "未知 round-trip 差异" : guardReport.errors().get(0);
                setStatusText("导出已阻断: " + firstError);
                return false;
            }

            String questId = quest == null || quest.meta == null || quest.meta.questId == null || quest.meta.questId.isBlank() ? "quest_editor_export" : quest.meta.questId;
            Path output = workspaceRoot.resolve("src/main/resources/data/arc_quest/arc_quest/quests/" + questId + "_editor_export.json");
            Files.createDirectories(output.getParent());
            String json = QuestSpecJsonWriter.write(mappedSpec);
            Files.writeString(output, json, StandardCharsets.UTF_8);
            setStatusText("导出成功: " + output.toString());
            savepointRevision = revision;
            return true;
        } catch (IOException ex) {
            setStatusText("导出失败: " + ex.getMessage());
            return false;
        }
    }

    public boolean undo() {
        boolean changed = commandBus.undo();
        if (changed) {
            revision = Math.max(0, revision - 1);
            syncMultiSelectionFromSelection();
            validate();
            statusText = "Undo: " + commandBus.lastRedoDescription();
        }
        return changed;
    }

    public boolean redo() {
        boolean changed = commandBus.redo();
        if (changed) {
            revision++;
            syncMultiSelectionFromSelection();
            validate();
            statusText = "Redo: " + commandBus.lastUndoDescription();
        }
        return changed;
    }

    public boolean canUndo() { return commandBus.canUndo(); }
    public boolean canRedo() { return commandBus.canRedo(); }
    public String undoDescription() { return commandBus.lastUndoDescription(); }
    public String redoDescription() { return commandBus.lastRedoDescription(); }
    public boolean isDirty() { return revision != savepointRevision; }

    private boolean executeMutation(String description, Mutation mutation) {
        QuestSpec beforeSpec = editableToSpecMapper.map(quest);
        EditorSelection beforeSelection = selection;
        EditorCommand command = new EditorCommand() {
            private QuestSpec afterSpec;
            private EditorSelection afterSelection;

            @Override
            public boolean apply() {
                if (afterSpec != null) {
                    quest = specToEditableMapper.map(afterSpec);
                    selection = afterSelection;
                    return true;
                }
                boolean changed = mutation.apply();
                if (!changed) return false;
                afterSpec = editableToSpecMapper.map(quest);
                afterSelection = selection;
                return true;
            }

            @Override
            public void revert() {
                quest = specToEditableMapper.map(beforeSpec);
                selection = beforeSelection;
            }

            @Override
            public String description() {
                return description;
            }
        };

        boolean changed = commandBus.execute(command);
        if (changed) {
            revision++;
            validate();
            statusText = description;
        }
        return changed;
    }

    private boolean hasTransitionConnection(String sourceNodeId, String targetNodeId) {
        for (var connection : quest.connections) {
            if (connection.connectionType.name().equals("TRANSITION")
                    && sourceNodeId.equals(connection.sourcePhaseNodeId)
                    && targetNodeId.equals(connection.targetPhaseNodeId)) {
                return true;
            }
        }
        return false;
    }

    @FunctionalInterface
    private interface Mutation {
        boolean apply();
    }

    private int indexOfObjective(EditablePhase phase, String objectiveId) {
        for (int i = 0; i < phase.objectives.size(); i++) {
            if (objectiveId.equals(phase.objectives.get(i).objectiveId)) return i;
        }
        return -1;
    }

    private int indexOfChoice(EditablePhase phase, String choiceId) {
        for (int i = 0; i < phase.choices.size(); i++) {
            if (choiceId != null && choiceId.equals(phase.choices.get(i).choiceId)) return i;
        }
        return -1;
    }

    private String resolveInitialPhaseNodeId() {
        if (quest.initialPhaseNodeId != null && !quest.initialPhaseNodeId.isBlank()) return quest.initialPhaseNodeId;
        return quest.phases.isEmpty() ? "" : quest.phases.get(0).nodeId;
    }

    private void syncMultiSelectionFromSelection() {
        if (selection != null && selection.type() == EditorSelectionType.PHASE && selection.phaseNodeId() != null && !selection.phaseNodeId().isBlank()) {
            selectedPhaseNodeIds.clear();
            selectedPhaseNodeIds.add(selection.phaseNodeId());
        } else if (selection == null || selection.type() == EditorSelectionType.NONE || selection.type() == EditorSelectionType.CONNECTION || selection.type() == EditorSelectionType.QUEST) {
            selectedPhaseNodeIds.clear();
        }
    }
}
