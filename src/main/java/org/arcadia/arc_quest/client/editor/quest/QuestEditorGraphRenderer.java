package org.arcadia.arc_quest.client.editor.quest;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.component.HudPanelRenderer;
import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalMarqueeTextRenderer;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalScaledTextRenderer;
import org.arcadia.arc_quest.quest.spec.PhaseSpec;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.spec.TransitionSpec;

import java.util.Map;

final class QuestEditorGraphRenderer {
    static final int NODE_WIDTH = 180;
    static final int NODE_HEIGHT = 90;

    private QuestEditorGraphRenderer() {
    }

    static void render(GuiGraphics graphics, Font font, HudRect panel, HudRect canvas,
                       QuestSpec document, Map<String, NodePosition> positions,
                       String selectedPhaseId, int mouseX, int mouseY,
                       float panX, float panY, float zoom, int themeColor) {
        HudPanelRenderer.drawJournalPanel(graphics, panel, themeColor,
                QuestEditorTheme.GRAPH_BACKGROUND_ALPHA, QuestEditorTheme.PANEL_BORDER_ALPHA);
        HudPanelRenderer.drawJournalHeader(graphics, font, panel,
                QuestEditorLayout.PANEL_HEADER_HEIGHT, "?????",
                Math.round(zoom * 100f) + "%", themeColor, 255);

        graphics.enableScissor(canvas.x(), canvas.y(), canvas.right(), canvas.bottom());
        renderGrid(graphics, canvas, panX, panY, zoom, themeColor);
        for (PhaseSpec phase : document.phases) {
            NodePosition source = positions.get(phase.phaseId);
            if (source == null) continue;
            for (TransitionSpec transition : phase.transitions) {
                NodePosition target = positions.get(transition.targetPhaseId);
                if (target != null) {
                    drawLine(graphics,
                            screenX(canvas, panX, zoom, source.x + NODE_WIDTH),
                            screenY(canvas, panY, zoom, source.y + NODE_HEIGHT / 2f),
                            screenX(canvas, panX, zoom, target.x),
                            screenY(canvas, panY, zoom, target.y + NODE_HEIGHT / 2f),
                            HudAnimUtil.withAlpha(themeColor, 210));
                }
            }
        }
        for (PhaseSpec phase : document.phases) {
            renderNode(graphics, font, canvas, phase, positions.get(phase.phaseId),
                    selectedPhaseId, mouseX, mouseY, panX, panY, zoom, themeColor);
        }
        graphics.disableScissor();
    }

    static PhaseSpec findNode(QuestSpec document, Map<String, NodePosition> positions,
                              HudRect canvas, double mouseX, double mouseY,
                              float panX, float panY, float zoom) {
        if (!canvas.contains(mouseX, mouseY)) return null;
        for (PhaseSpec phase : document.phases) {
            NodePosition position = positions.get(phase.phaseId);
            if (position == null) continue;
            int x = screenX(canvas, panX, zoom, position.x);
            int y = screenY(canvas, panY, zoom, position.y);
            int width = Math.max(72, Math.round(NODE_WIDTH * zoom));
            int height = Math.max(42, Math.round(NODE_HEIGHT * zoom));
            if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
                return phase;
            }
        }
        return null;
    }

    static ViewportTransform fit(HudRect canvas, Map<String, NodePosition> positions) {
        if (positions.isEmpty()) return null;
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (NodePosition position : positions.values()) {
            minX = Math.min(minX, position.x);
            minY = Math.min(minY, position.y);
            maxX = Math.max(maxX, position.x + NODE_WIDTH);
            maxY = Math.max(maxY, position.y + NODE_HEIGHT);
        }
        float contentWidth = Math.max(1f, maxX - minX);
        float contentHeight = Math.max(1f, maxY - minY);
        float zoom = Math.max(0.55f, Math.min(1.1f,
                Math.min((canvas.width() - 48f) / contentWidth,
                        (canvas.height() - 48f) / contentHeight)));
        float panX = (canvas.width() - contentWidth * zoom) * 0.5f - minX * zoom;
        float panY = (canvas.height() - contentHeight * zoom) * 0.5f - minY * zoom;
        return new ViewportTransform(panX, panY, zoom);
    }

    private static void renderGrid(GuiGraphics graphics, HudRect canvas,
                                   float panX, float panY, float zoom, int themeColor) {
        int spacing = Math.max(18, Math.round(40 * zoom));
        int offsetX = Math.floorMod(Math.round(panX), spacing);
        int offsetY = Math.floorMod(Math.round(panY), spacing);
        for (int x = canvas.x() + offsetX; x < canvas.right(); x += spacing) {
            graphics.fill(x, canvas.y(), x + 1, canvas.bottom(), HudAnimUtil.withAlpha(themeColor, 18));
        }
        for (int y = canvas.y() + offsetY; y < canvas.bottom(); y += spacing) {
            graphics.fill(canvas.x(), y, canvas.right(), y + 1, HudAnimUtil.withAlpha(themeColor, 18));
        }
    }

    private static void renderNode(GuiGraphics graphics, Font font, HudRect canvas,
                                   PhaseSpec phase, NodePosition position, String selectedPhaseId,
                                   int mouseX, int mouseY, float panX, float panY,
                                   float zoom, int themeColor) {
        if (position == null) return;
        int x = screenX(canvas, panX, zoom, position.x);
        int y = screenY(canvas, panY, zoom, position.y);
        int width = Math.max(72, Math.round(NODE_WIDTH * zoom));
        int height = Math.max(42, Math.round(NODE_HEIGHT * zoom));
        HudRect node = new HudRect(x, y, width, height);
        boolean selected = phase.phaseId.equals(selectedPhaseId);
        boolean hovered = node.contains(mouseX, mouseY);
        int nodeTheme = selected ? themeColor : hovered ? 0xA7E6FF : 0x526878;
        HudPanelRenderer.drawJournalPanel(graphics, node, nodeTheme,
                selected ? 86 : hovered ? 72 : 58, selected ? 120 : hovered ? 90 : 52);

        int textX = x + Math.max(7, Math.round(10 * zoom));
        int titleY = y + Math.max(7, Math.round(10 * zoom));
        int textWidth = Math.max(8, width - Math.max(14, Math.round(20 * zoom)));
        String displayName = phase.displayName == null || phase.displayName.value == null
                || phase.displayName.value.isBlank() ? phase.phaseId : phase.displayName.value;
        JournalMarqueeTextRenderer.drawString(graphics, font, displayName,
                textX, titleY, textWidth, selected ? 0xFFFFFFFF : 0xFFE8EEF5, false,
                textX, titleY, canvas.x(), canvas.y(), canvas.right(), canvas.bottom(),
                (target, x1, y1, x2, y2) -> target.enableScissor(x1, y1, x2, y2));
        if (height >= 62) {
            JournalScaledTextRenderer.draw(graphics, font, phase.phaseId, textX,
                    y + Math.max(27, Math.round(30 * zoom)), 0.78f,
                    HudAnimUtil.withAlpha(selected ? themeColor : QuestEditorTheme.TEXT_MUTED, 240), false);
        }
        if (height >= 82) {
            String summary = "?? " + phase.objectives.size() + "  ?  ?? " + phase.transitions.size();
            JournalScaledTextRenderer.draw(graphics, font, summary, textX,
                    y + Math.max(49, Math.round(54 * zoom)), 0.78f,
                    HudAnimUtil.withAlpha(QuestEditorTheme.TEXT_SECONDARY, 235), false);
        }
    }

    private static int screenX(HudRect canvas, float panX, float zoom, float worldX) {
        return canvas.x() + Math.round(panX + worldX * zoom);
    }

    private static int screenY(HudRect canvas, float panY, float zoom, float worldY) {
        return canvas.y() + Math.round(panY + worldY * zoom);
    }

    private static void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) return;
        for (int step = 0; step <= steps; step += 3) {
            int x = x1 + (x2 - x1) * step / steps;
            int y = y1 + (y2 - y1) * step / steps;
            graphics.fill(x, y, x + 2, y + 2, color);
        }
    }

    static final class NodePosition {
        private float x;
        private float y;

        NodePosition(float x, float y) {
            this.x = x;
            this.y = y;
        }

        void move(float deltaX, float deltaY) {
            x += deltaX;
            y += deltaY;
        }
    }

    record ViewportTransform(float panX, float panY, float zoom) {
    }
}
