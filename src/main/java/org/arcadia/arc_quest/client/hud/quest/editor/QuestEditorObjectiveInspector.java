package org.arcadia.arc_quest.client.hud.quest.editor;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.quest.editor.model.EditableObjective;
import org.arcadia.arc_quest.quest.editor.model.EditablePhase;

public final class QuestEditorObjectiveInspector {
    private QuestEditorObjectiveInspector() {}

    public static void renderHeader(QuestEditorScreen screen, GuiGraphics g, int x, int y) {
        g.drawString(screen.getUiFont(), "Objective", x + 8, y + 256, HudAnimUtil.withAlpha(0xFFFFFF, 255), true);
    }

    public static void renderList(QuestEditorScreen screen, GuiGraphics g, EditablePhase phase, EditableObjective selected, int x, int startY, int scroll) {
        int rowY = startY;
        int begin = Math.max(0, scroll);
        int end = Math.min(phase.objectives.size(), begin + 4);
        for (int i = begin; i < end; i++) {
            EditableObjective objective = phase.objectives.get(i);
            boolean active = selected != null && objective.objectiveId.equals(selected.objectiveId);
            int bg = HudAnimUtil.withAlpha(active ? 0x44D7FF : 0x1B2330, active ? 90 : 70);
            g.fill(x + 8, rowY, x + 208, rowY + 12, bg);
            g.drawString(screen.getUiFont(), (i + 1) + ". " + objective.objectiveId, x + 10, rowY + 2, HudAnimUtil.withAlpha(0xDDDDDD, 255), false);
            rowY += 14;
        }
    }
}
