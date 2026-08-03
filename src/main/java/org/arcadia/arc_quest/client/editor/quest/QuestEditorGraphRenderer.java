package org.arcadia.arc_quest.client.editor.quest;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.component.HudPanelRenderer;
import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.client.hud.quest.graph.GraphNodeLayout;
import org.arcadia.arc_quest.quest.api.VisualAsset;
import org.arcadia.arc_quest.quest.spec.PhaseSpec;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.spec.TransitionSpec;

import java.util.Map;

final class QuestEditorGraphRenderer {
    static final int NODE_WIDTH = QuestEditorNodeRenderer.CARD_WIDTH;
    static final int NODE_HEIGHT = QuestEditorNodeRenderer.CARD_HEIGHT;

    private QuestEditorGraphRenderer() {
    }

    static void render(GuiGraphics graphics, Font font, HudRect panel, HudRect canvas,
                       QuestSpec document, Map<String, GraphNodeLayout> positions,
                       Map<String, VisualAsset> images,
                       String selectedPhaseId, int mouseX, int mouseY,
                       float panX, float panY, float zoom, int themeColor,
                       float titleVisibility, float deltaTime) {
        HudPanelRenderer.drawJournalPanel(graphics, panel, themeColor,
                QuestEditorTheme.GRAPH_BACKGROUND_ALPHA, QuestEditorTheme.PANEL_BORDER_ALPHA);
        HudPanelRenderer.drawJournalHeader(graphics, font, panel,
                QuestEditorLayout.PANEL_HEADER_HEIGHT, "\u9636\u6bb5\u62d3\u6251",
                Math.round(zoom * 100f) + "%", themeColor, 255);
        graphics.enableScissor(canvas.x(), canvas.y(), canvas.right(), canvas.bottom());
        renderGrid(graphics, canvas, panX, panY, zoom, themeColor);
        graphics.pose().pushPose();
        graphics.pose().translate(canvas.x() + panX, canvas.y() + panY, 0f);
        graphics.pose().scale(zoom, zoom, 1f);
        for (PhaseSpec phase : document.phases) {
            GraphNodeLayout source = positions.get(phase.phaseId);
            if (source == null) continue;
            for (TransitionSpec transition : phase.transitions) {
                GraphNodeLayout target = positions.get(transition.targetPhaseId);
                if (target != null) drawConnection(graphics, source, target, themeColor);
            }
        }
        for (PhaseSpec phase : document.phases) {
            GraphNodeLayout position = positions.get(phase.phaseId);
            if (position == null) continue;
            boolean hovered = isInsideNode(canvas, mouseX, mouseY, position, panX, panY, zoom);
            graphics.pose().pushPose();
            graphics.pose().translate(position.x(), position.y(), 2f);
            QuestEditorNodeRenderer.render(graphics, font, phase, images.get(phase.phaseId), hovered,
                    phase.phaseId.equals(selectedPhaseId), themeColor,
                    titleVisibility, deltaTime);
            graphics.pose().popPose();
        }
        graphics.pose().popPose();
        graphics.disableScissor();
    }

    static PhaseSpec findNode(QuestSpec document, Map<String, GraphNodeLayout> positions,
                              HudRect canvas, double mouseX, double mouseY,
                              float panX, float panY, float zoom) {
        if (!canvas.contains(mouseX, mouseY)) return null;
        for (PhaseSpec phase : document.phases) {
            GraphNodeLayout position = positions.get(phase.phaseId);
            if (position != null && isInsideNode(canvas, mouseX, mouseY, position, panX, panY, zoom)) {
                return phase;
            }
        }
        return null;
    }

    private static boolean isInsideNode(HudRect canvas, double mouseX, double mouseY,
                                        GraphNodeLayout position, float panX, float panY, float zoom) {
        float centerX = canvas.x() + panX + position.x() * zoom;
        float centerY = canvas.y() + panY + position.y() * zoom;
        return Math.abs(mouseX - centerX) <= NODE_WIDTH * zoom / 2f
                && Math.abs(mouseY - centerY) <= NODE_HEIGHT * zoom / 2f;
    }

    private static void renderGrid(GuiGraphics graphics, HudRect canvas,
                                   float panX, float panY, float zoom, int themeColor) {
        int spacing = Math.max(15, Math.round(15 * zoom));
        int offsetX = Math.floorMod(Math.round(panX), spacing);
        int offsetY = Math.floorMod(Math.round(panY), spacing);
        int color = HudAnimUtil.withAlpha(0xFFFFFF, 5);
        for (int x = canvas.x() + offsetX; x < canvas.right(); x += spacing) {
            graphics.fill(x, canvas.y(), x + 1, canvas.bottom(), color);
        }
        for (int y = canvas.y() + offsetY; y < canvas.bottom(); y += spacing) {
            graphics.fill(canvas.x(), y, canvas.right(), y + 1, color);
        }
    }

    private static void drawConnection(GuiGraphics graphics, GraphNodeLayout source,
                                       GraphNodeLayout target, int themeColor) {
        int sourceX = source.x() + NODE_WIDTH / 2;
        int targetX = target.x() - NODE_WIDTH / 2;
        int midX = (sourceX + targetX) / 2;
        int lineColor = HudAnimUtil.withAlpha(themeColor, 170);
        graphics.fill(sourceX, source.y() - 1, midX, source.y() + 1, lineColor);
        graphics.fill(midX - 1, Math.min(source.y(), target.y()),
                midX + 1, Math.max(source.y(), target.y()), lineColor);
        graphics.fill(midX, target.y() - 1, targetX, target.y() + 1, lineColor);
        graphics.fill(sourceX - 2, source.y() - 2, sourceX + 2, source.y() + 2, lineColor);
        graphics.fill(targetX - 2, target.y() - 2, targetX + 2, target.y() + 2, lineColor);
    }
}
