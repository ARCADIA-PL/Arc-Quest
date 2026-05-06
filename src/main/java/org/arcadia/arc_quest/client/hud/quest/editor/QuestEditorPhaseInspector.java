package org.arcadia.arc_quest.client.hud.quest.editor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.quest.editor.model.EditableChoice;
import org.arcadia.arc_quest.quest.editor.model.EditableObjective;
import org.arcadia.arc_quest.quest.editor.model.EditablePhase;

public class QuestEditorPhaseInspector {
    private final QuestEditorScreen screen;
    private EditBox phaseIdBox;
    private EditBox phaseNameBox;
    private Button applyPhaseButton;
    private String boundPhaseNodeId = "";

    public QuestEditorPhaseInspector(QuestEditorScreen screen) {
        this.screen = screen;
    }

    public void initWidgets(int x, int y, int width) {
        phaseIdBox = new EditBox(screen.getUiFont(), x + 8, y + 148, width - 16, 18, Component.literal("phaseId"));
        phaseNameBox = new EditBox(screen.getUiFont(), x + 8, y + 172, width - 16, 18, Component.literal("phaseName"));
        applyPhaseButton = Button.builder(Component.literal("应用 Phase"), b -> apply()).bounds(x + 8, y + 196, width - 16, 18).build();
        screen.registerEditorWidget(phaseIdBox);
        screen.registerEditorWidget(phaseNameBox);
        screen.registerEditorWidget(applyPhaseButton);
    }

    public void sync() {
        EditablePhase phase = screen.getController().selectedPhase();
        if (phase == null) {
            boundPhaseNodeId = "";
            phaseIdBox.setValue("");
            phaseNameBox.setValue("");
            applyPhaseButton.active = false;
            return;
        }
        applyPhaseButton.active = true;
        if (!phase.nodeId.equals(boundPhaseNodeId)) {
            boundPhaseNodeId = phase.nodeId;
            phaseIdBox.setValue(phase.phaseId == null ? "" : phase.phaseId);
            phaseNameBox.setValue(phase.displayName == null ? "" : phase.displayName.value);
        }
    }

    public void renderHeader(GuiGraphics g, int x, int y) {
        g.drawString(screen.getUiFont(), "Phase", x + 8, y + 136, HudAnimUtil.withAlpha(0xFFFFFF, 255), true);
    }

    public int renderSummary(GuiGraphics g, EditablePhase phase, EditableObjective objective, EditableChoice choice, int x, int rowY, int selectedObjectiveIndex) {
        g.drawString(screen.getUiFont(), "objectives: " + phase.objectives.size() + (selectedObjectiveIndex >= 0 ? (" (" + (selectedObjectiveIndex + 1) + "/" + phase.objectives.size() + ")") : ""), x + 8, rowY, HudAnimUtil.withAlpha(0x88CCFF, 255), false);
        rowY += 14;
        g.drawString(screen.getUiFont(), "choices: " + phase.choices.size(), x + 8, rowY, HudAnimUtil.withAlpha(0x88CCFF, 255), false);
        rowY += 14;
        g.drawString(screen.getUiFont(), objective == null ? "当前未选中 Objective" : "当前 Objective: " + objective.objectiveId, x + 8, rowY, HudAnimUtil.withAlpha(0xCCCCCC, 255), false);
        rowY += 14;
        g.drawString(screen.getUiFont(), choice == null ? "当前未选中 Choice" : "当前 Choice: " + choice.choiceId, x + 8, rowY, HudAnimUtil.withAlpha(0xCCCCCC, 255), false);
        return rowY + 18;
    }

    private void apply() {
        screen.getController().updateSelectedPhaseBasics(phaseIdBox.getValue(), phaseNameBox.getValue());
        sync();
    }
}
