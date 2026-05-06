package org.arcadia.arc_quest.client.hud.quest.editor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.quest.editor.model.EditableChoice;
import org.arcadia.arc_quest.quest.editor.model.EditableConnection;
import org.arcadia.arc_quest.quest.editor.model.EditableObjective;
import org.arcadia.arc_quest.quest.editor.model.EditablePhase;

public class QuestEditorInspectorPanel {
    private final QuestEditorScreen screen;
    private final QuestEditorQuestInspector questInspector;
    private final QuestEditorPhaseInspector phaseInspector;
    private EditBox objectiveTargetBox;
    private EditBox objectiveCountBox;
    private EditBox objectiveTextBox;
    private EditBox choiceTextBox;
    private EditBox choiceTargetBox;
    private EditBox connectionTargetBox;
    private Button addObjectiveButton;
    private Button removeObjectiveButton;
    private Button applyObjectiveButton;
    private Button previousObjectiveButton;
    private Button nextObjectiveButton;
    private Button moveObjectiveUpButton;
    private Button moveObjectiveDownButton;
    private Button addChoiceButton;
    private Button removeChoiceButton;
    private Button previousChoiceButton;
    private Button nextChoiceButton;
    private Button applyChoiceButton;
    private Button applyConnectionButton;
    private Button removeConnectionButton;
    private Button previousConnectionTargetButton;
    private Button nextConnectionTargetButton;
    private Button gotoConnectionSourceButton;
    private Button gotoConnectionTargetButton;
    private String boundObjectiveId = "";
    private String boundChoiceId = "";
    private int objectiveListScroll = 0;
    private int choiceListScroll = 0;
    private int panelX = 0;
    private int panelY = 0;
    private int panelWidth = 0;

    public QuestEditorInspectorPanel(QuestEditorScreen screen) {
        this.screen = screen;
        this.questInspector = new QuestEditorQuestInspector(screen);
        this.phaseInspector = new QuestEditorPhaseInspector(screen);
    }

    public void initWidgets(int x, int y, int width, int height) {
        questInspector.initWidgets(x, y, width);
        phaseInspector.initWidgets(x, y, width);
        objectiveTargetBox = new EditBox(screen.getUiFont(), x + 8, y + 314, width - 16, 18, Component.literal("objectiveTarget"));
        objectiveCountBox = new EditBox(screen.getUiFont(), x + 8, y + 338, width - 16, 18, Component.literal("objectiveCount"));
        objectiveTextBox = new EditBox(screen.getUiFont(), x + 8, y + 362, width - 16, 18, Component.literal("objectiveText"));
        choiceTextBox = new EditBox(screen.getUiFont(), x + 8, y + 452, width - 16, 18, Component.literal("choiceText"));
        choiceTargetBox = new EditBox(screen.getUiFont(), x + 8, y + 476, width - 16, 18, Component.literal("choiceTargetPhaseNodeId"));
        connectionTargetBox = new EditBox(screen.getUiFont(), x + 8, y + 590, width - 16, 18, Component.literal("connectionTargetNodeId"));

        addObjectiveButton = Button.builder(Component.literal("新增 Objective"), b -> addObjective()).bounds(x + 8, y + 220, width - 16, 18).build();
        removeObjectiveButton = Button.builder(Component.literal("删除当前 Objective"), b -> removeObjective()).bounds(x + 8, y + 242, width - 16, 18).build();
        previousObjectiveButton = Button.builder(Component.literal("上一个"), b -> selectPreviousObjective()).bounds(x + 8, y + 264, (width - 20) / 2, 18).build();
        nextObjectiveButton = Button.builder(Component.literal("下一个"), b -> selectNextObjective()).bounds(x + 12 + (width - 20) / 2, y + 264, (width - 20) / 2, 18).build();
        moveObjectiveUpButton = Button.builder(Component.literal("上移"), b -> moveObjectiveUp()).bounds(x + 8, y + 286, (width - 20) / 2, 18).build();
        moveObjectiveDownButton = Button.builder(Component.literal("下移"), b -> moveObjectiveDown()).bounds(x + 12 + (width - 20) / 2, y + 286, (width - 20) / 2, 18).build();
        applyObjectiveButton = Button.builder(Component.literal("应用 Objective"), b -> applyObjectiveChanges()).bounds(x + 8, y + 386, width - 16, 18).build();
        addChoiceButton = Button.builder(Component.literal("新增 Choice"), b -> addChoice()).bounds(x + 8, y + 410, width - 16, 18).build();
        removeChoiceButton = Button.builder(Component.literal("删除当前 Choice"), b -> removeChoice()).bounds(x + 8, y + 432, width - 16, 18).build();
        previousChoiceButton = Button.builder(Component.literal("上一个"), b -> previousChoice()).bounds(x + 8, y + 500, (width - 20) / 2, 18).build();
        nextChoiceButton = Button.builder(Component.literal("下一个"), b -> nextChoice()).bounds(x + 12 + (width - 20) / 2, y + 500, (width - 20) / 2, 18).build();
        applyChoiceButton = Button.builder(Component.literal("应用 Choice"), b -> applyChoiceChanges()).bounds(x + 8, y + 524, width - 16, 18).build();
        applyConnectionButton = Button.builder(Component.literal("应用 Connection Target"), b -> applyConnectionChanges()).bounds(x + 8, y + 614, width - 16, 18).build();
        removeConnectionButton = Button.builder(Component.literal("删除当前 Connection"), b -> removeConnection()).bounds(x + 8, y + 636, width - 16, 18).build();
        previousConnectionTargetButton = Button.builder(Component.literal("目标-"), b -> previousConnectionTarget()).bounds(x + 8, y + 568, (width - 20) / 2, 18).build();
        nextConnectionTargetButton = Button.builder(Component.literal("目标+"), b -> nextConnectionTarget()).bounds(x + 12 + (width - 20) / 2, y + 568, (width - 20) / 2, 18).build();
        gotoConnectionSourceButton = Button.builder(Component.literal("跳转Source"), b -> gotoConnectionSource()).bounds(x + 8, y + 660, (width - 20) / 2, 18).build();
        gotoConnectionTargetButton = Button.builder(Component.literal("跳转Target"), b -> gotoConnectionTarget()).bounds(x + 12 + (width - 20) / 2, y + 660, (width - 20) / 2, 18).build();

        syncFromController();
        screen.registerEditorWidget(objectiveTargetBox);
        screen.registerEditorWidget(objectiveCountBox);
        screen.registerEditorWidget(objectiveTextBox);
        screen.registerEditorWidget(choiceTextBox);
        screen.registerEditorWidget(choiceTargetBox);
        screen.registerEditorWidget(connectionTargetBox);
        screen.registerEditorWidget(addObjectiveButton);
        screen.registerEditorWidget(removeObjectiveButton);
        screen.registerEditorWidget(previousObjectiveButton);
        screen.registerEditorWidget(nextObjectiveButton);
        screen.registerEditorWidget(moveObjectiveUpButton);
        screen.registerEditorWidget(moveObjectiveDownButton);
        screen.registerEditorWidget(applyObjectiveButton);
        screen.registerEditorWidget(addChoiceButton);
        screen.registerEditorWidget(removeChoiceButton);
        screen.registerEditorWidget(previousChoiceButton);
        screen.registerEditorWidget(nextChoiceButton);
        screen.registerEditorWidget(applyChoiceButton);
        screen.registerEditorWidget(applyConnectionButton);
        screen.registerEditorWidget(removeConnectionButton);
        screen.registerEditorWidget(previousConnectionTargetButton);
        screen.registerEditorWidget(nextConnectionTargetButton);
        screen.registerEditorWidget(gotoConnectionSourceButton);
        screen.registerEditorWidget(gotoConnectionTargetButton);
    }

    public void syncFromController() {
        if (objectiveTargetBox == null) return;
        questInspector.sync();
        phaseInspector.sync();
        EditablePhase phase = screen.getController().selectedPhase();
        EditableConnection selectedConnection = screen.getController().selectedConnection();
        if (phase == null) {
            objectiveTargetBox.setValue("");
            objectiveCountBox.setValue("1");
            objectiveTextBox.setValue("");
            choiceTextBox.setValue("");
            choiceTargetBox.setValue("");
            connectionTargetBox.setValue(selectedConnection == null ? "" : selectedConnection.targetPhaseNodeId);
            addObjectiveButton.active = false;
            removeObjectiveButton.active = false;
            previousObjectiveButton.active = false;
            nextObjectiveButton.active = false;
            moveObjectiveUpButton.active = false;
            moveObjectiveDownButton.active = false;
            applyObjectiveButton.active = false;
            addChoiceButton.active = false;
            removeChoiceButton.active = false;
            previousChoiceButton.active = false;
            nextChoiceButton.active = false;
            applyChoiceButton.active = false;
            applyConnectionButton.active = selectedConnection != null;
            removeConnectionButton.active = selectedConnection != null;
            previousConnectionTargetButton.active = selectedConnection != null;
            nextConnectionTargetButton.active = selectedConnection != null;
            gotoConnectionSourceButton.active = selectedConnection != null;
            gotoConnectionTargetButton.active = selectedConnection != null;
            return;
        }
        addObjectiveButton.active = true;
        addChoiceButton.active = true;
        applyConnectionButton.active = false;
        removeConnectionButton.active = false;
        previousConnectionTargetButton.active = false;
        nextConnectionTargetButton.active = false;
        gotoConnectionSourceButton.active = false;
        gotoConnectionTargetButton.active = false;

        EditableObjective objective = screen.getController().selectedObjective();
        previousObjectiveButton.active = !phase.objectives.isEmpty();
        nextObjectiveButton.active = !phase.objectives.isEmpty();
        if (objective == null) {
            boundObjectiveId = "";
            objectiveTargetBox.setValue("");
            objectiveCountBox.setValue("1");
            objectiveTextBox.setValue("");
            removeObjectiveButton.active = false;
            moveObjectiveUpButton.active = false;
            moveObjectiveDownButton.active = false;
            applyObjectiveButton.active = false;
        } else if (!objective.objectiveId.equals(boundObjectiveId)) {
            boundObjectiveId = objective.objectiveId;
            objectiveTargetBox.setValue(objective.targetId == null ? "" : objective.targetId);
            objectiveCountBox.setValue(String.valueOf(objective.requiredCount));
            objectiveTextBox.setValue(objective.displayText == null ? "" : objective.displayText.value);
            removeObjectiveButton.active = true;
            moveObjectiveUpButton.active = true;
            moveObjectiveDownButton.active = true;
            applyObjectiveButton.active = true;
        }

        EditableChoice choice = screen.getController().selectedChoice();
        previousChoiceButton.active = !phase.choices.isEmpty();
        nextChoiceButton.active = !phase.choices.isEmpty();
        if (choice == null) {
            boundChoiceId = "";
            choiceTextBox.setValue("");
            choiceTargetBox.setValue("");
            removeChoiceButton.active = false;
            applyChoiceButton.active = false;
        } else if (!choice.choiceId.equals(boundChoiceId)) {
            boundChoiceId = choice.choiceId;
            choiceTextBox.setValue(choice.text == null ? "" : choice.text.value);
            choiceTargetBox.setValue(choice.targetPhaseNodeId == null ? "" : choice.targetPhaseNodeId);
            removeChoiceButton.active = true;
            applyChoiceButton.active = true;
        }
    }

    public void render(GuiGraphics g, int x, int y, int width, int height, int mouseX, int mouseY) {
        this.panelX = x;
        this.panelY = y;
        this.panelWidth = width;
        HudRenderUtil.drawGlassPanel(g, x, y, width, height, 0x0A0D12, 180, screen.getThemeColor(), 180, 3);
        g.drawString(screen.getUiFont(), "INSPECTOR", x + 8, y + 8, HudAnimUtil.withAlpha(0xFFFFFF, 255), true);
        g.drawString(screen.getUiFont(), screen.getController().selectedSummary(), x + 8, y + 24, HudAnimUtil.withAlpha(0xCCCCCC, 255), false);
        questInspector.renderHeader(g, x, y);
        phaseInspector.renderHeader(g, x, y);
        QuestEditorObjectiveInspector.renderHeader(screen, g, x, y);
        QuestEditorChoiceInspector.renderHeader(screen, g, x, y);

        EditablePhase phase = screen.getController().selectedPhase();
        EditableConnection selectedConnection = screen.getController().selectedConnection();
        int rowY = y + 548;
        if (phase == null) {
            if (selectedConnection != null) {
                g.drawString(screen.getUiFont(), "Connection", x + 8, rowY, HudAnimUtil.withAlpha(0xFFFFFF, 255), true);
                rowY += 14;
                g.drawString(screen.getUiFont(), "id: " + selectedConnection.connectionId, x + 8, rowY, HudAnimUtil.withAlpha(0xCCCCCC, 255), false);
                rowY += 14;
                g.drawString(screen.getUiFont(), "type: " + selectedConnection.connectionType.name(), x + 8, rowY, HudAnimUtil.withAlpha(0x88CCFF, 255), false);
                rowY += 14;
                g.drawString(screen.getUiFont(), "source: " + selectedConnection.sourcePhaseNodeId, x + 8, rowY, HudAnimUtil.withAlpha(0xCCCCCC, 255), false);
                rowY += 14;
                g.drawString(screen.getUiFont(), "target: " + selectedConnection.targetPhaseNodeId, x + 8, rowY, HudAnimUtil.withAlpha(0xCCCCCC, 255), false);
                rowY += 14;
                EditablePhase sourcePhase = screen.getController().phaseByNodeId(selectedConnection.sourcePhaseNodeId);
                EditablePhase targetPhase = screen.getController().phaseByNodeId(selectedConnection.targetPhaseNodeId);
                g.drawString(screen.getUiFont(), "sourcePhaseId: " + (sourcePhase == null ? "?" : sourcePhase.phaseId), x + 8, rowY, HudAnimUtil.withAlpha(0x88CCFF, 255), false);
                rowY += 14;
                g.drawString(screen.getUiFont(), "targetPhaseId: " + (targetPhase == null ? "?" : targetPhase.phaseId), x + 8, rowY, HudAnimUtil.withAlpha(0x88CCFF, 255), false);
                rowY += 14;
                g.drawString(screen.getUiFont(), "可编辑 targetNodeId 后点击应用", x + 8, rowY, HudAnimUtil.withAlpha(0x88CCFF, 255), false);
                return;
            }
            g.drawString(screen.getUiFont(), "选择 Phase 后可编辑其基础信息", x + 8, rowY, HudAnimUtil.withAlpha(0x888888, 255), false);
            return;
        }

        EditableObjective objective = screen.getController().selectedObjective();
        EditableChoice choice = screen.getController().selectedChoice();
        QuestEditorObjectiveInspector.renderList(screen, g, phase, objective, x, y + 556, objectiveListScroll);
        QuestEditorChoiceInspector.renderList(screen, g, phase, choice, x, y + 612, choiceListScroll);
        int selectedIndex = objective == null ? -1 : indexOf(phase, objective.objectiveId);
        rowY = phaseInspector.renderSummary(g, phase, objective, choice, x, rowY, selectedIndex);
        g.drawString(screen.getUiFont(), "Validation", x + 8, rowY, HudAnimUtil.withAlpha(0xFFFFFF, 255), true);
        rowY += 14;
        for (var issue : screen.getController().issues()) {
            if (issue.path().contains(phase.phaseId) || issue.path().contains(phase.nodeId) || (objective != null && issue.path().contains(objective.objectiveId))) {
                g.drawString(screen.getUiFont(), issue.message(), x + 8, rowY, HudAnimUtil.withAlpha(issue.severity().name().equals("ERROR") ? 0xFF6666 : 0xFFCC66, 255), false);
                rowY += 12;
                if (rowY > y + height - 12) break;
            }
        }
    }

    private int indexOf(EditablePhase phase, String objectiveId) {
        for (int i = 0; i < phase.objectives.size(); i++) {
            if (objectiveId.equals(phase.objectives.get(i).objectiveId)) return i;
        }
        return -1;
    }

    public boolean mouseScrolled(double scaledMouseX, double scaledMouseY, double delta) {
        EditablePhase phase = screen.getController().selectedPhase();
        if (phase == null) return false;
        boolean inX = scaledMouseX >= panelX + 8 && scaledMouseX <= panelX + panelWidth - 8;
        if (!inX) return false;

        int objectiveListTop = panelY + 556;
        int objectiveListBottom = objectiveListTop + 50;
        int choiceListTop = panelY + 612;
        int choiceListBottom = choiceListTop + 50;

        if (scaledMouseY >= objectiveListTop && scaledMouseY <= objectiveListBottom) {
            int max = Math.max(0, phase.objectives.size() - 4);
            objectiveListScroll = Math.max(0, Math.min(max, objectiveListScroll - (int) Math.signum(delta)));
            return true;
        }
        if (scaledMouseY >= choiceListTop && scaledMouseY <= choiceListBottom) {
            int max = Math.max(0, phase.choices.size() - 4);
            choiceListScroll = Math.max(0, Math.min(max, choiceListScroll - (int) Math.signum(delta)));
            return true;
        }
        return false;
    }

    private void addObjective() {
        screen.getController().addObjectiveToSelectedPhase();
        syncFromController();
    }

    private void removeObjective() {
        screen.getController().removeSelectedObjective();
        syncFromController();
    }

    private void selectPreviousObjective() {
        screen.getController().selectPreviousObjective();
        syncFromController();
    }

    private void selectNextObjective() {
        screen.getController().selectNextObjective();
        syncFromController();
    }

    private void moveObjectiveUp() {
        screen.getController().moveSelectedObjectiveUp();
        syncFromController();
    }

    private void moveObjectiveDown() {
        screen.getController().moveSelectedObjectiveDown();
        syncFromController();
    }

    private void applyObjectiveChanges() {
        int requiredCount = 1;
        try {
            requiredCount = Integer.parseInt(objectiveCountBox.getValue());
        } catch (NumberFormatException ignored) {
        }
        screen.getController().updateSelectedObjective(objectiveTargetBox.getValue(), requiredCount, objectiveTextBox.getValue());
        syncFromController();
    }

    private void addChoice() {
        screen.getController().addChoiceToSelectedPhase();
        syncFromController();
    }

    private void removeChoice() {
        screen.getController().removeSelectedChoice();
        syncFromController();
    }

    private void previousChoice() {
        screen.getController().selectPreviousChoice();
        syncFromController();
    }

    private void nextChoice() {
        screen.getController().selectNextChoice();
        syncFromController();
    }

    private void applyChoiceChanges() {
        screen.getController().updateSelectedChoice(choiceTextBox.getValue(), choiceTargetBox.getValue());
        syncFromController();
    }

    private void applyConnectionChanges() {
        screen.getController().updateSelectedConnectionTarget(connectionTargetBox.getValue());
        syncFromController();
    }

    private void removeConnection() {
        screen.getController().removeSelectedConnection();
        syncFromController();
    }

    private void previousConnectionTarget() {
        cycleConnectionTarget(-1);
    }

    private void nextConnectionTarget() {
        cycleConnectionTarget(1);
    }

    private void cycleConnectionTarget(int step) {
        EditableConnection connection = screen.getController().selectedConnection();
        if (connection == null) return;
        var phases = screen.getController().phases();
        if (phases.isEmpty()) return;
        int currentIndex = -1;
        for (int i = 0; i < phases.size(); i++) {
            if (connection.targetPhaseNodeId != null && connection.targetPhaseNodeId.equals(phases.get(i).nodeId)) {
                currentIndex = i;
                break;
            }
        }
        if (currentIndex < 0) currentIndex = 0;
        int next = (currentIndex + step + phases.size()) % phases.size();
        connectionTargetBox.setValue(phases.get(next).nodeId);
    }

    private void gotoConnectionSource() {
        EditableConnection connection = screen.getController().selectedConnection();
        if (connection == null) return;
        if (screen.getController().selectPhaseByNodeId(connection.sourcePhaseNodeId)) {
            syncFromController();
        }
    }

    private void gotoConnectionTarget() {
        EditableConnection connection = screen.getController().selectedConnection();
        if (connection == null) return;
        if (screen.getController().selectPhaseByNodeId(connection.targetPhaseNodeId)) {
            syncFromController();
        }
    }
}
