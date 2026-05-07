package org.arcadia.arc_quest.client.hud.quest.editor;

import org.arcadia.arc_quest.quest.editor.mapper.EditableQuestToQuestSpecMapper;
import org.arcadia.arc_quest.quest.editor.mapper.QuestEditorRoundTripVerifier;
import org.arcadia.arc_quest.quest.editor.mapper.QuestSpecToEditableQuestMapper;
import org.arcadia.arc_quest.quest.editor.mapper.SampleQuestRoundTripDebugHelper;
import org.arcadia.arc_quest.quest.editor.model.*;
import org.arcadia.arc_quest.quest.editor.service.*;
import org.arcadia.arc_quest.quest.editor.ui.schema.FormRuntime;
import org.arcadia.arc_quest.quest.editor.ui.schema.SchemaPathAccessor;
import org.arcadia.arc_quest.quest.editor.ui.schema.SchemaRegistry;
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
    private final EditableCollectionService collectionService = new EditableCollectionService();
    private final EditableQuestToQuestSpecMapper editableToSpecMapper = new EditableQuestToQuestSpecMapper();
    private final QuestSpecToEditableQuestMapper specToEditableMapper = new QuestSpecToEditableQuestMapper();
    private final QuestEditorRoundTripVerifier roundTripVerifier = new QuestEditorRoundTripVerifier();
    private final EditableQuestValidationService validationService = new EditableQuestValidationService();
    private final EditableQuestGraphService graphService = new EditableQuestGraphService();
    private final QuestEditorSaveService saveService = new QuestEditorSaveService();
    private final QuestEditorSnapshotHistory snapshotHistory = new QuestEditorSnapshotHistory();
    private final QuestDatapackScanService datapackScanService = new QuestDatapackScanService();
    private final QuestEditorDocumentService documentService = new QuestEditorDocumentService();
    private final SchemaRegistry schemaRegistry = new SchemaRegistry();
    private final SchemaPathAccessor schemaPathAccessor = new SchemaPathAccessor();
    private final FormRuntime formRuntime = new FormRuntime(schemaRegistry, schemaPathAccessor);

    private final QuestEditorState state = new QuestEditorState();
    private final LinkedHashSet<String> selectedPhaseNodeIds = new LinkedHashSet<>();
    private EditableQuest quest = new EditableQuest();
    private EditorSelection selection = EditorSelection.none();
    private EditableValidationReport validationReport = new EditableValidationReport();
    private String statusText = "未加载任务";
    private EditableConnectionType linkCreateType = EditableConnectionType.TRANSITION;
    private double autoLayoutLayerGap = 260;
    private double autoLayoutRowGap = 120;
    private int autoLayoutUnreachablePerLayer = 8;
    private long revision = 0;
    private long savepointRevision = 0;
    private int phaseAutoIndex = 1;

    public List<QuestDatapackEntry> scanWorkspace(Path workspaceRoot) {
        return datapackScanService.scanWorkspace(workspaceRoot);
    }

    public boolean importDatapack(Path file) {
        if (file == null) return false;
        try {
            QuestEditorDocument doc = documentService.importFromFile(file);
            this.quest = doc.quest;
            this.state.document = doc;
            this.selection = quest.phases.isEmpty() ? EditorSelection.quest() : EditorSelection.phase(resolveInitialPhaseNodeId());
            this.snapshotHistory.clear();
            this.revision = 0;
            this.savepointRevision = 0;
            selectedPhaseNodeIds.clear();
            if (selection.type() == EditorSelectionType.PHASE) selectedPhaseNodeIds.add(selection.phaseNodeId());
            validate();
            this.statusText = "已导入: " + file.getFileName();
            syncStateFromRuntime();
            return true;
        } catch (Exception ex) {
            this.statusText = "导入失败: " + ex.getMessage();
            syncStateFromRuntime();
            return false;
        }
    }

    public void loadSampleQuest(Path workspaceRoot) {
        try {
            var report = SampleQuestRoundTripDebugHelper.verifySampleQuestFromWorkspace(workspaceRoot);
            this.quest = report.editable();
            this.selection = quest.phases.isEmpty() ? EditorSelection.quest() : EditorSelection.phase(resolveInitialPhaseNodeId());
            this.snapshotHistory.clear();
            this.revision = 0;
            this.savepointRevision = 0;
            selectedPhaseNodeIds.clear();
            if (selection.type() == EditorSelectionType.PHASE) selectedPhaseNodeIds.add(selection.phaseNodeId());
            validate();
            this.statusText = report.success() ? "Sample quest 已导入" : "Sample quest 已导入，但 round-trip 存在差异";
            syncStateFromRuntime();
        } catch (Exception ex) {
            this.quest = new EditableQuest();
            this.selection = EditorSelection.none();
            this.validationReport = new EditableValidationReport();
            this.statusText = "导入 sample 失败: " + ex.getMessage();
            syncStateFromRuntime();
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
        this.snapshotHistory.clear();
        this.revision = 0;
        this.savepointRevision = 0;
        selectedPhaseNodeIds.clear();
        selectedPhaseNodeIds.add(first.nodeId);
        validate();
        this.statusText = "已新建 Quest: " + normalizedId;
        syncStateFromRuntime();
    }

    public boolean createNewQuest(CreateQuestRequest request, Path workspaceRoot) {
        try {
            QuestEditorDocument doc = documentService.createNew(request, workspaceRoot);
            this.quest = doc.quest;
            this.state.document = doc;
            this.selection = quest.phases.isEmpty() ? EditorSelection.quest() : EditorSelection.phase(resolveInitialPhaseNodeId());
            this.snapshotHistory.clear();
            this.revision = 0;
            this.savepointRevision = 0;
            selectedPhaseNodeIds.clear();
            if (selection.type() == EditorSelectionType.PHASE) selectedPhaseNodeIds.add(selection.phaseNodeId());
            validate();
            this.statusText = "已新建 Quest: " + quest.meta.questId + " (" + quest.meta.mode + ")";
            syncStateFromRuntime();
            return true;
        } catch (Exception ex) {
            this.statusText = "新建 Quest 失败: " + ex.getMessage();
            syncStateFromRuntime();
            return false;
        }
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

    public boolean updateSelectedPhaseDetails(String displayName, String description, String story, String tradeShopId, String intelSceneId,
                                              String startSound, String completeSound, String enterFlag, String completeFlag) {
        return executeMutation("Phase 详细信息已更新", () -> {
            EditablePhase phase = selectedPhase();
            if (phase == null) return false;
            phase.displayName = QuestTextSpec.literal(displayName == null ? "" : displayName);
            phase.description = QuestTextSpec.literal(description == null ? "" : description);
            phase.story = QuestTextSpec.literal(story == null ? "" : story);
            phase.tradeShopId = tradeShopId == null ? "" : tradeShopId;
            phase.intelSceneId = intelSceneId == null ? "" : intelSceneId;
            phase.phaseStartSound = startSound == null ? "" : startSound;
            phase.phaseCompleteSound = completeSound == null ? "" : completeSound;
            phase.flagsToSetOnEnter.clear();
            if (enterFlag != null && !enterFlag.isBlank()) phase.flagsToSetOnEnter.add(enterFlag.trim());
            phase.flagsToSetOnComplete.clear();
            if (completeFlag != null && !completeFlag.isBlank()) phase.flagsToSetOnComplete.add(completeFlag.trim());
            return true;
        });
    }

    public boolean updateCollectionEntryBasics(String entryId, String categoryId, int completionTarget, int maxCount) {
        if (!isCollectionMode()) return false;
        return executeMutation("已更新 Collection Entry", () -> collectionService.moveEntryToCategory(quest, entryId, categoryId)
                | collectionService.updateEntryCounting(quest, entryId, null, completionTarget, maxCount, false, false));
    }

    public EditableCollectionCategory collectionCategory(String categoryId) {
        if (quest == null || quest.collectionConfig == null) return null;
        for (EditableCollectionCategory c : quest.collectionConfig.categories)
            if (categoryId.equals(c.categoryId)) return c;
        return null;
    }

    public EditableCollectionEntry collectionEntry(String entryId) {
        if (quest == null || quest.collectionEntries == null) return null;
        for (EditableCollectionEntry e : quest.collectionEntries) if (entryId.equals(e.entryId)) return e;
        return null;
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

    public boolean updateSelectedObjectiveAdvanced(String targetId, int requiredCount, String displayText,
                                                   String objectiveType, boolean hidden, boolean optional,
                                                   String npcId, String itemTag, Integer radius, String countMode,
                                                   String extraKey, String extraValue,
                                                   Integer x, Integer y, Integer z,
                                                   Integer countBase, Integer countPerLevel, Integer countMin, Integer countMax) {
        return executeMutation("Objective 高级字段已更新", () -> {
            EditablePhase phase = selectedPhase();
            EditableObjective objective = selectedObjective();
            if (phase == null || objective == null) return false;
            boolean changed = questService.updateObjectiveBasic(quest, phase.nodeId, objective.objectiveId, targetId, requiredCount, QuestTextSpec.literal(displayText));
            try {
                if (objectiveType != null && !objectiveType.isBlank()) {
                    objective.type = org.arcadia.arc_quest.quest.api.ObjectiveType.valueOf(objectiveType.trim().toUpperCase());
                    changed = true;
                }
            } catch (Exception ignored) {
            }
            objective.hidden = hidden;
            objective.optional = optional;
            objective.npcId = npcId == null ? "" : npcId;
            objective.itemTag = itemTag == null ? "" : itemTag;
            objective.radius = radius;
            objective.countMode = countMode == null ? "" : countMode;
            objective.x = x;
            objective.y = y;
            objective.z = z;
            objective.countBase = countBase;
            objective.countPerLevel = countPerLevel;
            objective.countMin = countMin;
            objective.countMax = countMax;
            if (objective.collectionEntryConfig == null && objective.type == org.arcadia.arc_quest.quest.api.ObjectiveType.COLLECT) {
                objective.collectionEntryConfig = new org.arcadia.arc_quest.quest.api.CollectionEntryConfig(
                        "default",
                        org.arcadia.arc_quest.quest.api.VisibilityMode.VISIBLE_BY_DEFAULT,
                        org.arcadia.arc_quest.quest.api.HiddenPresentationMode.FULLY_HIDDEN,
                        java.util.List.of(),
                        org.arcadia.arc_quest.quest.api.CountingMode.BINARY,
                        1,
                        false,
                        false,
                        1,
                        org.arcadia.arc_quest.quest.api.EntryRewardGrantMode.AUTO,
                        java.util.List.of(),
                        0,
                        true);
            }
            if (extraKey != null && !extraKey.isBlank()) {
                objective.extraData.put(extraKey.trim(), extraValue == null ? "" : extraValue);
                changed = true;
            }
            return changed || true;
        });
    }

    public boolean updateSelectedObjectiveFromSchema(java.util.Map<String, String> values, String extraKey, String extraValue) {
        return executeMutation("Objective Schema字段已更新", () -> {
            EditableObjective objective = selectedObjective();
            if (objective == null) return false;
            FormRuntime.ApplyResult result = formRuntime.applyValues("phase.objective", objective, values == null ? java.util.Map.of() : values);
            if (extraKey != null && !extraKey.isBlank())
                objective.extraData.put(extraKey.trim(), extraValue == null ? "" : extraValue);
            if (objective.collectionEntryConfig == null && objective.type == org.arcadia.arc_quest.quest.api.ObjectiveType.COLLECT) {
                objective.collectionEntryConfig = new org.arcadia.arc_quest.quest.api.CollectionEntryConfig(
                        "default",
                        org.arcadia.arc_quest.quest.api.VisibilityMode.VISIBLE_BY_DEFAULT,
                        org.arcadia.arc_quest.quest.api.HiddenPresentationMode.FULLY_HIDDEN,
                        java.util.List.of(),
                        org.arcadia.arc_quest.quest.api.CountingMode.BINARY,
                        1,
                        false,
                        false,
                        1,
                        org.arcadia.arc_quest.quest.api.EntryRewardGrantMode.AUTO,
                        java.util.List.of(),
                        0,
                        true);
            }
            return result.success();
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

    public boolean updateSelectedChoiceAdvanced(String text, String targetPhaseNodeId, String flagToSet) {
        return executeMutation("Choice 高级字段已更新", () -> {
            EditablePhase phase = selectedPhase();
            EditableChoice choice = selectedChoice();
            if (phase == null || choice == null) return false;
            boolean changed = questService.updateChoiceText(quest, phase.nodeId, choice.choiceId,
                    QuestTextSpec.literal(text == null ? "" : text), flagToSet == null ? "" : flagToSet, choice.visibleCondition);
            changed |= questService.updateChoiceTarget(quest, phase.nodeId, choice.choiceId, targetPhaseNodeId == null ? "" : targetPhaseNodeId);
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
            if (!phaseById.containsKey(connection.sourcePhaseNodeId) || !phaseById.containsKey(connection.targetPhaseNodeId))
                continue;
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

    public double autoLayoutLayerGap() {
        return autoLayoutLayerGap;
    }

    public double autoLayoutRowGap() {
        return autoLayoutRowGap;
    }

    public int autoLayoutUnreachablePerLayer() {
        return autoLayoutUnreachablePerLayer;
    }

    public EditableConnectionType linkCreateType() {
        return linkCreateType;
    }

    public boolean beginPlacePhase() {
        if (!isProgressMode()) return false;
        state.mode = QuestEditorMode.PLACE_PHASE;
        statusText = "进入 Phase 放置模式";
        syncStateFromRuntime();
        return true;
    }

    public EditablePhase placePhaseAt(double graphX, double graphY) {
        if (!isProgressMode() || state.mode != QuestEditorMode.PLACE_PHASE) return null;
        final EditablePhase[] created = new EditablePhase[1];
        boolean changed = executeMutation("已放置 Phase", () -> {
            String phaseId = nextAutoPhaseId();
            created[0] = questService.addPhase(quest, phaseId, graphX, graphY);
            selection = EditorSelection.phase(created[0].nodeId);
            state.mode = QuestEditorMode.IDLE;
            return true;
        });
        return changed ? created[0] : null;
    }

    public boolean cancelPlacePhase() {
        if (state.mode != QuestEditorMode.PLACE_PHASE) return false;
        state.mode = QuestEditorMode.IDLE;
        statusText = "已取消 Phase 放置";
        syncStateFromRuntime();
        return true;
    }

    public boolean beginDeletePhaseSelection() {
        if (!isProgressMode()) return false;
        state.mode = QuestEditorMode.DELETE_PHASE_SELECT;
        statusText = "进入 Phase 删除选择模式";
        syncStateFromRuntime();
        return true;
    }

    public boolean addPhaseToPendingDelete(String nodeId) {
        if (nodeId == null || nodeId.isBlank() || phaseByNodeId(nodeId) == null) return false;
        boolean added = state.pendingDeletePhaseNodeIds.add(nodeId);
        if (added) syncStateFromRuntime();
        return added;
    }

    public boolean removePhaseFromPendingDelete(String nodeId) {
        boolean removed = state.pendingDeletePhaseNodeIds.remove(nodeId);
        if (removed) syncStateFromRuntime();
        return removed;
    }

    public void clearPendingDelete() {
        state.pendingDeletePhaseNodeIds.clear();
        syncStateFromRuntime();
    }

    public boolean openDeleteConfirm() {
        if (state.pendingDeletePhaseNodeIds.isEmpty()) return false;
        state.mode = QuestEditorMode.DELETE_CONFIRM_MODAL;
        syncStateFromRuntime();
        return true;
    }

    public boolean confirmDeletePendingPhases() {
        if (state.pendingDeletePhaseNodeIds.isEmpty()) return false;
        var ids = new java.util.LinkedHashSet<>(state.pendingDeletePhaseNodeIds);
        return executeMutation("已删除选中 Phase", () -> {
            boolean changed = false;
            for (String id : ids) changed |= questService.removePhase(quest, id);
            state.pendingDeletePhaseNodeIds.clear();
            state.mode = QuestEditorMode.IDLE;
            if (selection != null && selection.type() == EditorSelectionType.PHASE && phaseByNodeId(selection.phaseNodeId()) == null) {
                selection = quest.phases.isEmpty() ? EditorSelection.none() : EditorSelection.phase(quest.phases.get(0).nodeId);
            }
            return changed;
        });
    }

    public boolean cancelDeletePhases() {
        state.pendingDeletePhaseNodeIds.clear();
        if (state.mode == QuestEditorMode.DELETE_PHASE_SELECT || state.mode == QuestEditorMode.DELETE_CONFIRM_MODAL) {
            state.mode = QuestEditorMode.IDLE;
            syncStateFromRuntime();
            return true;
        }
        return false;
    }

    public CreateConnectionReport beginGotoConnection(String sourceNodeId) {
        CreateConnectionReport report = new CreateConnectionReport();
        if (!isProgressMode()) {
            report.message = "仅 Progress 模式可连线";
            return report;
        }
        if (phaseByNodeId(sourceNodeId) == null) {
            report.message = "来源 Phase 不存在";
            return report;
        }
        state.connectSourcePhaseNodeId = sourceNodeId;
        state.mode = QuestEditorMode.CONNECT_GOTO;
        report.created = false;
        report.message = "已进入 Goto 连线模式";
        syncStateFromRuntime();
        return report;
    }

    public CreateConnectionReport finishGotoConnection(String targetNodeId) {
        return finishGotoConnection(targetNodeId, GotoConnectionPolicy.AUTO);
    }

    public CreateConnectionReport finishGotoConnection(String targetNodeId, GotoConnectionPolicy policy) {
        CreateConnectionReport report = new CreateConnectionReport();
        String sourceNodeId = state.connectSourcePhaseNodeId;
        if (sourceNodeId == null || sourceNodeId.isBlank() || state.mode != QuestEditorMode.CONNECT_GOTO) {
            report.message = "未处于 Goto 连线模式";
            return report;
        }
        if (sourceNodeId.equals(targetNodeId)) {
            report.message = "连接失败: 不支持自环";
            return report;
        }
        if (phaseByNodeId(targetNodeId) == null) {
            report.message = "目标 Phase 不存在";
            return report;
        }

        EditableConnectionType type = resolveConnectionTypeForGoto(sourceNodeId, policy);
        if (type == null) {
            report.requiresPolicySelection = true;
            report.message = "无法自动判断连接类型，需要用户选择";
            return report;
        }

        if (type == EditableConnectionType.TRANSITION && hasTransitionConnection(sourceNodeId, targetNodeId)) {
            report.message = "连接失败: 已存在 Transition";
            return report;
        }

        final EditableConnectionType finalType = type;
        boolean changed = executeMutation("已创建 Goto 连接", () -> {
            if (finalType == EditableConnectionType.TRANSITION) {
                var connection = questService.connectTransition(quest, sourceNodeId, targetNodeId, null);
                selection = new EditorSelection(EditorSelectionType.CONNECTION, "", "", "", connection.connectionId);
                state.selectedConnectionId = connection.connectionId;
            } else {
                EditableChoice choice = questService.addChoice(quest, sourceNodeId, targetNodeId, "", "", null);
                selection = EditorSelection.choice(sourceNodeId, choice.choiceId);
                var connection = quest.connections.stream().filter(c -> c.connectionType == EditableConnectionType.CHOICE && choice.choiceId.equals(c.choiceId)).findFirst().orElse(null);
                if (connection != null) state.selectedConnectionId = connection.connectionId;
            }
            state.mode = QuestEditorMode.IDLE;
            state.connectSourcePhaseNodeId = "";
            return true;
        });

        report.created = changed;
        report.connectionType = type;
        report.message = changed ? "创建成功" : "创建失败";
        if (changed && state.selectedConnectionId != null) report.connectionId = state.selectedConnectionId;
        return report;
    }

    public boolean cancelGotoConnection() {
        if (state.mode != QuestEditorMode.CONNECT_GOTO) return false;
        state.mode = QuestEditorMode.IDLE;
        state.connectSourcePhaseNodeId = "";
        syncStateFromRuntime();
        return true;
    }

    public EditableCollectionCategory addCollectionCategory(String categoryId) {
        if (!isCollectionMode()) {
            statusText = "Collection API 仅 Collection 模式可用";
            return null;
        }
        if (categoryId == null || categoryId.isBlank() || quest.collectionConfig.categories.stream().anyMatch(c -> categoryId.equals(c.categoryId))) {
            statusText = "Category id 无效或重复";
            return null;
        }
        final EditableCollectionCategory[] created = new EditableCollectionCategory[1];
        boolean changed = executeMutation("已新增 Collection Category", () -> {
            created[0] = collectionService.addCategory(quest, categoryId);
            state.mode = QuestEditorMode.COLLECTION_CATEGORY_SELECTED;
            return created[0] != null;
        });
        return changed ? created[0] : null;
    }

    public boolean updateCollectionCategoryBasics(String categoryId, String displayName, String iconTexture) {
        if (!isCollectionMode()) return false;
        return executeMutation("已更新 Collection Category", () -> collectionService.updateCategoryBasics(quest, categoryId, displayName, iconTexture));
    }

    public boolean moveCollectionCategory(String categoryId, int toIndex) {
        if (!isCollectionMode()) return false;
        return executeMutation("已重排 Collection Category", () -> collectionService.moveCategory(quest, categoryId, toIndex));
    }

    public EditableCollectionEntry addCollectionEntry(String categoryId, String entryId) {
        if (!isCollectionMode()) {
            statusText = "Collection API 仅 Collection 模式可用";
            return null;
        }
        if (entryId == null || entryId.isBlank() || quest.collectionEntries.stream().anyMatch(e -> entryId.equals(e.entryId)))
            return null;
        final EditableCollectionEntry[] created = new EditableCollectionEntry[1];
        boolean changed = executeMutation("已新增 Collection Entry", () -> {
            created[0] = collectionService.addEntry(quest, categoryId, entryId);
            state.mode = QuestEditorMode.COLLECTION_ENTRY_SELECTED;
            return created[0] != null;
        });
        return changed ? created[0] : null;
    }

    public boolean moveCollectionEntryToCategory(String entryId, String categoryId) {
        if (!isCollectionMode()) return false;
        return executeMutation("已移动 Collection Entry", () -> collectionService.moveEntryToCategory(quest, entryId, categoryId));
    }

    public boolean moveCollectionEntry(String entryId, int toIndex) {
        if (!isCollectionMode()) return false;
        return executeMutation("已重排 Collection Entry", () -> collectionService.moveEntry(quest, entryId, toIndex));
    }

    public boolean updateCollectionEntryVisibility(String entryId, org.arcadia.arc_quest.quest.api.VisibilityMode mode, org.arcadia.arc_quest.quest.api.HiddenPresentationMode hidden, boolean showInTracker) {
        if (!isCollectionMode()) return false;
        return executeMutation("已更新 Entry Visibility", () -> collectionService.updateEntryVisibility(quest, entryId, mode, hidden, showInTracker));
    }

    public boolean updateCollectionEntryCounting(String entryId, org.arcadia.arc_quest.quest.api.CountingMode countingMode, int completionTarget, int maxCount, boolean repeatableProgress, boolean repeatableCompletion) {
        if (!isCollectionMode()) return false;
        return executeMutation("已更新 Entry Counting", () -> collectionService.updateEntryCounting(quest, entryId, countingMode, completionTarget, maxCount, repeatableProgress, repeatableCompletion));
    }

    public boolean updateCollectionEntryRewardGrantMode(String entryId, org.arcadia.arc_quest.quest.api.EntryRewardGrantMode grantMode) {
        if (!isCollectionMode()) return false;
        return executeMutation("已更新 Entry RewardGrantMode", () -> collectionService.updateEntryRewardGrantMode(quest, entryId, grantMode));
    }

    public EditableCollectionRewardNode addCollectionEntryRewardNode(String entryId, String rewardNodeId) {
        if (!isCollectionMode()) return null;
        final EditableCollectionRewardNode[] created = new EditableCollectionRewardNode[1];
        boolean changed = executeMutation("已新增 RewardNode", () -> {
            created[0] = collectionService.addEntryRewardNode(quest, entryId, rewardNodeId);
            return created[0] != null;
        });
        return changed ? created[0] : null;
    }

    public boolean updateCollectionEntryRewardNode(String entryId, String rewardNodeId, org.arcadia.arc_quest.quest.api.EntryRewardGrantMode grantMode) {
        if (!isCollectionMode()) return false;
        return executeMutation("已更新 RewardNode", () -> collectionService.updateEntryRewardNode(quest, entryId, rewardNodeId, grantMode));
    }

    public boolean updateCollectionEntryRewardNodeAdvanced(String entryId, String rewardNodeId, String scope, String grantMode) {
        if (!isCollectionMode()) return false;
        return executeMutation("已更新 RewardNode 高级字段", () -> {
            EditableCollectionEntry entry = collectionEntry(entryId);
            if (entry == null || entry.rewardNodes.isEmpty()) return false;
            EditableCollectionRewardNode node = entry.rewardNodes.get(0);
            node.rewardNodeId = rewardNodeId == null ? "" : rewardNodeId;
            try {
                node.scope = org.arcadia.arc_quest.quest.api.RewardScope.valueOf(scope == null ? "ENTRY" : scope.trim().toUpperCase());
            } catch (Exception ignored) {
            }
            try {
                node.grantMode = org.arcadia.arc_quest.quest.api.EntryRewardGrantMode.valueOf(grantMode == null ? "AUTO" : grantMode.trim().toUpperCase());
            } catch (Exception ignored) {
            }
            return true;
        });
    }

    public boolean removeCollectionEntryRewardNode(String entryId, String rewardNodeId) {
        if (!isCollectionMode()) return false;
        return executeMutation("已删除 RewardNode", () -> collectionService.removeEntryRewardNode(quest, entryId, rewardNodeId));
    }

    public EditableCollectionCompletionRule addCollectionCategoryCompletionRule(String categoryId, String type, String expression) {
        if (!isCollectionMode()) return null;
        final EditableCollectionCompletionRule[] created = new EditableCollectionCompletionRule[1];
        boolean changed = executeMutation("已新增 CompletionRule", () -> {
            created[0] = collectionService.addCategoryCompletionRule(quest, categoryId, type, expression);
            return created[0] != null;
        });
        return changed ? created[0] : null;
    }

    public boolean updateCollectionCategoryCompletionRule(String categoryId, int index, String type, String expression) {
        if (!isCollectionMode()) return false;
        return executeMutation("已更新 CompletionRule", () -> collectionService.updateCategoryCompletionRule(quest, categoryId, index, type, expression));
    }

    public boolean removeCollectionCategoryCompletionRule(String categoryId, int index) {
        if (!isCollectionMode()) return false;
        return executeMutation("已删除 CompletionRule", () -> collectionService.removeCategoryCompletionRule(quest, categoryId, index));
    }

    public boolean beginDeleteCollectionObjectSelection() {
        if (!isCollectionMode()) return false;
        state.mode = QuestEditorMode.DELETE_COLLECTION_OBJECT_SELECT;
        syncStateFromRuntime();
        return true;
    }

    public boolean addCollectionObjectToPendingDelete(EditorObjectRef ref) {
        if (!isCollectionMode() || ref == null) return false;
        boolean added = state.pendingDeleteCollectionObjectRefs.add(ref.type().name() + "::" + ref.ownerId() + "::" + ref.id());
        if (added) syncStateFromRuntime();
        return added;
    }

    public boolean removeCollectionObjectFromPendingDelete(EditorObjectRef ref) {
        if (!isCollectionMode() || ref == null) return false;
        boolean removed = state.pendingDeleteCollectionObjectRefs.remove(ref.type().name() + "::" + ref.ownerId() + "::" + ref.id());
        if (removed) syncStateFromRuntime();
        return removed;
    }

    public boolean confirmDeletePendingCollectionObjects(CategoryDeletePolicy categoryPolicy, String defaultCategoryId) {
        if (!isCollectionMode() || state.pendingDeleteCollectionObjectRefs.isEmpty()) return false;
        var refs = new java.util.ArrayList<>(state.pendingDeleteCollectionObjectRefs);
        return executeMutation("已确认删除 Collection 对象", () -> {
            boolean changed = false;
            for (String raw : refs) {
                EditorObjectRef ref = parseObjectRef(raw);
                changed |= collectionService.deleteObject(quest, ref, categoryPolicy, defaultCategoryId);
            }
            state.pendingDeleteCollectionObjectRefs.clear();
            state.mode = QuestEditorMode.IDLE;
            return changed;
        });
    }

    public boolean cancelDeleteCollectionObjects() {
        state.pendingDeleteCollectionObjectRefs.clear();
        if (state.mode == QuestEditorMode.DELETE_COLLECTION_OBJECT_SELECT || state.mode == QuestEditorMode.DELETE_CONFIRM_MODAL) {
            state.mode = QuestEditorMode.IDLE;
            syncStateFromRuntime();
            return true;
        }
        return false;
    }

    private EditorObjectRef parseObjectRef(String raw) {
        if (raw == null) return new EditorObjectRef(EditorObjectType.ENTRY, "", "");
        String[] p = raw.split("::", 3);
        EditorObjectType type = p.length > 0 ? EditorObjectType.valueOf(p[0]) : EditorObjectType.ENTRY;
        String owner = p.length > 1 ? p[1] : "";
        String id = p.length > 2 ? p[2] : "";
        return new EditorObjectRef(type, id, owner);
    }

    public void toggleLinkCreateType() {
        linkCreateType = (linkCreateType == EditableConnectionType.TRANSITION) ? EditableConnectionType.CHOICE : EditableConnectionType.TRANSITION;
        statusText = "连线模式: " + (linkCreateType == EditableConnectionType.TRANSITION ? "TRANSITION" : "CHOICE");
    }

    public boolean createConnection(String sourceNodeId, String targetNodeId) {
        if (sourceNodeId == null || targetNodeId == null || sourceNodeId.isBlank() || targetNodeId.isBlank())
            return false;
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
                if (questService.getPhase(quest, sourceNodeId) == null || questService.getPhase(quest, targetNodeId) == null)
                    return false;
                questService.connectTransition(quest, sourceNodeId, targetNodeId, null);
                return true;
            });
        }

        return executeMutation("已创建 Choice 连接", () -> {
            if (questService.getPhase(quest, sourceNodeId) == null || questService.getPhase(quest, targetNodeId) == null)
                return false;
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

    public boolean selectConnection(String connectionId) {
        if (connectionId == null || connectionId.isBlank()) return false;
        EditableConnection connection = quest.connections.stream().filter(c -> connectionId.equals(c.connectionId)).findFirst().orElse(null);
        if (connection == null) return false;
        selection = new EditorSelection(EditorSelectionType.CONNECTION, "", "", "", connection.connectionId);
        state.mode = QuestEditorMode.CONNECTION_SELECTED;
        state.selectedConnectionId = connection.connectionId;
        syncStateFromRuntime();
        return true;
    }

    public ConnectionSummary connectionSummary(String connectionId) {
        EditableConnection connection = quest.connections.stream().filter(c -> connectionId.equals(c.connectionId)).findFirst().orElse(null);
        if (connection == null) return null;
        ConnectionSummary summary = new ConnectionSummary();
        summary.connectionId = connection.connectionId;
        summary.connectionType = connection.connectionType;
        summary.sourcePhaseNodeId = connection.sourcePhaseNodeId;
        summary.targetPhaseNodeId = connection.targetPhaseNodeId;
        summary.choiceId = connection.choiceId == null ? "" : connection.choiceId;
        summary.priority = connection.priority;
        return summary;
    }

    public boolean deleteSelectedConnection() {
        return removeSelectedConnection();
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

    public boolean updateSelectedConnectionAdvanced(String targetNodeId, int priority, String conditionType, String conditionFlag,
                                                    String conditionQuestId, String conditionVariable, String conditionCompareOp, int conditionValue) {
        EditableConnection selected = selectedConnection();
        if (selected == null) return false;
        return executeMutation("已更新连接高级字段", () -> {
            EditableConnection connection = selectedConnection();
            if (connection == null) return false;
            if (targetNodeId != null && !targetNodeId.isBlank()) {
                if (questService.getPhase(quest, targetNodeId) == null) return false;
                if (connection.connectionType == EditableConnectionType.CHOICE && connection.choiceId != null && !connection.choiceId.isBlank()) {
                    questService.updateChoiceTarget(quest, connection.sourcePhaseNodeId, connection.choiceId, targetNodeId);
                } else {
                    connection.targetPhaseNodeId = targetNodeId;
                }
            }
            connection.priority = priority;
            if (conditionType == null || conditionType.isBlank()) {
                connection.condition = null;
            } else {
                if (connection.condition == null)
                    connection.condition = new org.arcadia.arc_quest.quest.spec.ConditionSpec();
                connection.condition.type = conditionType;
                connection.condition.flag = conditionFlag == null ? "" : conditionFlag;
                connection.condition.questId = conditionQuestId == null ? "" : conditionQuestId;
                connection.condition.variable = conditionVariable == null ? "" : conditionVariable;
                try {
                    connection.condition.compareOp = org.arcadia.arc_quest.quest.api.CompareOp.valueOf(conditionCompareOp == null ? "GREATER_OR_EQUAL" : conditionCompareOp.trim().toUpperCase());
                } catch (Exception ignored) {
                }
                connection.condition.value = conditionValue;
            }
            return true;
        });
    }

    public boolean updateSelectedConnectionFromSchema(java.util.Map<String, String> values) {
        EditableConnection selected = selectedConnection();
        if (selected == null) return false;
        return executeMutation("已更新连接Schema字段", () -> {
            EditableConnection connection = selectedConnection();
            if (connection == null) return false;
            FormRuntime.ApplyResult result = formRuntime.applyValues("phase.connection", connection, values == null ? java.util.Map.of() : values);
            if (result.errors.containsKey("targetNode")) return false;
            if (questService.getPhase(quest, connection.targetPhaseNodeId) == null) return false;
            if (connection.connectionType == EditableConnectionType.CHOICE && connection.choiceId != null && !connection.choiceId.isBlank()) {
                questService.updateChoiceTarget(quest, connection.sourcePhaseNodeId, connection.choiceId, connection.targetPhaseNodeId);
            }
            return result.success();
        });
    }

    public boolean selectNextConnection() {
        if (quest == null || quest.connections == null || quest.connections.isEmpty()) return false;
        int idx = -1;
        if (state.selectedConnectionId != null && !state.selectedConnectionId.isBlank()) {
            for (int i = 0; i < quest.connections.size(); i++)
                if (state.selectedConnectionId.equals(quest.connections.get(i).connectionId)) {
                    idx = i;
                    break;
                }
        }
        int next = Math.min(quest.connections.size() - 1, idx + 1);
        return selectConnection(quest.connections.get(next).connectionId);
    }

    public boolean selectPreviousConnection() {
        if (quest == null || quest.connections == null || quest.connections.isEmpty()) return false;
        int idx = quest.connections.size();
        if (state.selectedConnectionId != null && !state.selectedConnectionId.isBlank()) {
            for (int i = 0; i < quest.connections.size(); i++)
                if (state.selectedConnectionId.equals(quest.connections.get(i).connectionId)) {
                    idx = i;
                    break;
                }
        }
        int prev = Math.max(0, idx - 1);
        return selectConnection(quest.connections.get(prev).connectionId);
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
        } else {
            selectedPhaseNodeIds.clear();
        }
        syncStateFromRuntime();
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
        if (selection.type() != EditorSelectionType.PHASE && selection.type() != EditorSelectionType.OBJECTIVE && selection.type() != EditorSelectionType.CHOICE)
            return null;
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

    public FormRuntime formRuntime() {
        return formRuntime;
    }

    public SchemaRegistry schemaRegistry() {
        return schemaRegistry;
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
        QuestEditorSnapshot target = snapshotHistory.undo(captureSnapshot());
        if (target == null) return false;
        restoreSnapshot(target);
        validate();
        state.lastSaveReport = saveService.autosave(state.document);
        revision = Math.max(0, revision - 1);
        statusText = "Undo";
        syncStateFromRuntime();
        return true;
    }

    public boolean redo() {
        QuestEditorSnapshot target = snapshotHistory.redo(captureSnapshot());
        if (target == null) return false;
        restoreSnapshot(target);
        validate();
        state.lastSaveReport = saveService.autosave(state.document);
        revision++;
        statusText = "Redo";
        syncStateFromRuntime();
        return true;
    }

    public boolean canUndo() {
        return snapshotHistory.canUndo();
    }

    public boolean canRedo() {
        return snapshotHistory.canRedo();
    }

    public String undoDescription() {
        return canUndo() ? "Undo" : "";
    }

    public String redoDescription() {
        return canRedo() ? "Redo" : "";
    }

    public boolean isDirty() {
        return revision != savepointRevision;
    }

    private boolean executeMutation(String description, Mutation mutation) {
        QuestEditorSnapshot before = captureSnapshot();
        boolean changed = mutation.apply();
        if (!changed) return false;

        snapshotHistory.pushUndo(before);
        validate();
        state.document.quest = quest;
        state.lastSaveReport = saveService.autosave(state.document);
        revision++;
        statusText = description;
        syncStateFromRuntime();
        return true;
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

    private boolean isProgressMode() {
        return quest != null && quest.meta != null && quest.meta.mode == org.arcadia.arc_quest.quest.api.QuestMode.PROGRESSION;
    }

    private boolean isCollectionMode() {
        return quest != null && quest.meta != null && quest.meta.mode == org.arcadia.arc_quest.quest.api.QuestMode.COLLECTION;
    }

    private EditableConnectionType resolveConnectionTypeForGoto(String sourceNodeId, GotoConnectionPolicy policy) {
        if (policy == GotoConnectionPolicy.FORCE_TRANSITION) return EditableConnectionType.TRANSITION;
        if (policy == GotoConnectionPolicy.FORCE_CHOICE) return EditableConnectionType.CHOICE;

        EditablePhase source = phaseByNodeId(sourceNodeId);
        if (source == null) return null;
        if (source.choices == null || source.choices.isEmpty()) return EditableConnectionType.TRANSITION;
        if (source.choices.size() == 1) return EditableConnectionType.CHOICE;
        return null;
    }

    private String nextAutoPhaseId() {
        while (true) {
            String id = "phase_" + phaseAutoIndex++;
            boolean exists = quest.phases.stream().anyMatch(p -> id.equals(p.phaseId));
            if (!exists) return id;
        }
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

    public QuestEditorSnapshot captureSnapshot() {
        QuestEditorSnapshot snapshot = new QuestEditorSnapshot();
        snapshot.quest = specToEditableMapper.map(editableToSpecMapper.map(quest));
        snapshot.selection = selection;
        snapshot.mode = state.mode;
        snapshot.cameraX = state.cameraX;
        snapshot.cameraY = state.cameraY;
        snapshot.zoom = state.zoom;
        snapshot.pendingDeletePhaseNodeIds.addAll(state.pendingDeletePhaseNodeIds);
        snapshot.pendingDeleteCollectionObjectRefs.addAll(state.pendingDeleteCollectionObjectRefs);
        snapshot.selectedConnectionId = state.selectedConnectionId;
        snapshot.connectSourcePhaseNodeId = state.connectSourcePhaseNodeId;
        snapshot.draggingObjectId = state.draggingObjectId;
        snapshot.statusText = statusText;
        return snapshot;
    }

    public void restoreSnapshot(QuestEditorSnapshot snapshot) {
        if (snapshot == null || snapshot.quest == null) return;
        this.quest = specToEditableMapper.map(editableToSpecMapper.map(snapshot.quest));
        this.selection = snapshot.selection == null ? EditorSelection.none() : snapshot.selection;
        this.state.mode = snapshot.mode == null ? QuestEditorMode.IDLE : snapshot.mode;
        this.state.cameraX = snapshot.cameraX;
        this.state.cameraY = snapshot.cameraY;
        this.state.zoom = snapshot.zoom;
        this.state.pendingDeletePhaseNodeIds.clear();
        this.state.pendingDeletePhaseNodeIds.addAll(snapshot.pendingDeletePhaseNodeIds);
        this.state.pendingDeleteCollectionObjectRefs.clear();
        this.state.pendingDeleteCollectionObjectRefs.addAll(snapshot.pendingDeleteCollectionObjectRefs);
        this.state.selectedConnectionId = snapshot.selectedConnectionId;
        this.state.connectSourcePhaseNodeId = snapshot.connectSourcePhaseNodeId;
        this.state.draggingObjectId = snapshot.draggingObjectId;
        this.statusText = snapshot.statusText == null ? "" : snapshot.statusText;
        syncMultiSelectionFromSelection();
        syncStateFromRuntime();
    }

    public QuestEditorState state() {
        syncStateFromRuntime();
        return state;
    }

    private void syncStateFromRuntime() {
        state.document.quest = quest;
        state.selection = selection;
        state.statusText = statusText;
        state.lastValidationReport = validationReport;
    }

    private void syncMultiSelectionFromSelection() {
        if (selection != null && selection.type() == EditorSelectionType.PHASE && selection.phaseNodeId() != null && !selection.phaseNodeId().isBlank()) {
            selectedPhaseNodeIds.clear();
            selectedPhaseNodeIds.add(selection.phaseNodeId());
        } else if (selection == null || selection.type() == EditorSelectionType.NONE || selection.type() == EditorSelectionType.CONNECTION || selection.type() == EditorSelectionType.QUEST) {
            selectedPhaseNodeIds.clear();
        }
    }

    @FunctionalInterface
    private interface Mutation {
        boolean apply();
    }
}
