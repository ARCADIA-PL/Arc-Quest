package org.arcadia.arc_quest.client.hud.quest.editor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.quest.editor.model.EditableConnection;
import org.arcadia.arc_quest.quest.editor.model.EditablePhase;
import org.arcadia.arc_quest.quest.editor.model.EditorNodePosition;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class QuestEditorCanvasPanel {
    private static final int NODE_WIDTH = 170;
    private static final int NODE_HEIGHT = 54;

    private final QuestEditorScreen screen;
    private final List<QuestEditorNodeHitBox> nodeHitBoxes = new ArrayList<>();
    private final List<QuestEditorConnectionHitBox> connectionHitBoxes = new ArrayList<>();
    private String draggingNodeId = "";
    private boolean draggingMultiSelection = false;
    private double lastDragMouseWorldX = 0;
    private double lastDragMouseWorldY = 0;
    private double dragOffsetWorldX = 0;
    private double dragOffsetWorldY = 0;
    private boolean panning = false;
    private double panStartMouseX = 0;
    private double panStartMouseY = 0;
    private double panStartX = 0;
    private double panStartY = 0;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private double panX = 0;
    private double panY = 0;
    private double zoom = 1.0;
    private String linkingSourceNodeId = "";
    private int lastMouseX = 0;
    private int lastMouseY = 0;
    private boolean marqueeSelecting = false;
    private double marqueeStartX = 0;
    private double marqueeStartY = 0;
    private double marqueeEndX = 0;
    private double marqueeEndY = 0;
    private int miniMapX = 0;
    private int miniMapY = 0;
    private int miniMapW = 0;
    private int miniMapH = 0;
    private int miniMapContentX = 0;
    private int miniMapContentY = 0;
    private int miniMapContentW = 0;
    private int miniMapContentH = 0;
    private int miniViewportX1 = 0;
    private int miniViewportY1 = 0;
    private int miniViewportX2 = 0;
    private int miniViewportY2 = 0;
    private double miniWorldMinX = 0;
    private double miniWorldMinY = 0;
    private double miniWorldScale = 1;
    private boolean miniMapReady = false;
    private boolean draggingMiniViewport = false;

    public QuestEditorCanvasPanel(QuestEditorScreen screen) {
        this.screen = screen;
    }

    public void render(GuiGraphics g, int x, int y, int width, int height, int mouseX, int mouseY) {
        this.panelX = x;
        this.panelY = y;
        this.panelW = width;
        this.panelH = height;
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        nodeHitBoxes.clear();
        connectionHitBoxes.clear();
        HudRenderUtil.drawGlassPanel(g, x, y, width, height, 0x091018, 170, screen.getThemeColor(), 180, 3);
        g.drawString(screen.getUiFont(), "PHASE GRAPH  Zoom: " + String.format("%.2f", zoom), x + 8, y + 8, HudAnimUtil.withAlpha(0xFFFFFF, 255), true);
        g.drawString(screen.getUiFont(), "LinkMode: " + screen.getController().linkCreateType().name() + " (Shift+Click)", x + 8, y + 20, HudAnimUtil.withAlpha(0x9EC9D9, 255), false);

        for (EditableConnection connection : screen.getController().quest().connections) {
            EditablePhase source = findPhase(connection.sourcePhaseNodeId);
            EditablePhase target = findPhase(connection.targetPhaseNodeId);
            if (source == null || target == null) continue;
            EditorNodePosition sourcePos = screen.getController().quest().layout.phasePositions.get(source.nodeId);
            EditorNodePosition targetPos = screen.getController().quest().layout.phasePositions.get(target.nodeId);
            if (sourcePos == null || targetPos == null) continue;
            int x1 = worldToScreenX(sourcePos.x + NODE_WIDTH);
            int y1 = worldToScreenY(sourcePos.y + NODE_HEIGHT / 2.0);
            int x2 = worldToScreenX(targetPos.x);
            int y2 = worldToScreenY(targetPos.y + NODE_HEIGHT / 2.0);
            int color = connection.connectionType.name().equals("CHOICE") ? 0x66CCFF : screen.getThemeColor();
            if (screen.getController().selection().type() == EditorSelectionType.CONNECTION && connection.connectionId.equals(screen.getController().selection().connectionId())) {
                color = 0xFFFFFF;
            }
            drawLine(g, x1, y1, x2, y2, color);
            connectionHitBoxes.add(new QuestEditorConnectionHitBox(connection.connectionId, x1, y1, x2, y2));
        }

        for (EditablePhase phase : screen.getController().phases()) {
            EditorNodePosition pos = screen.getController().quest().layout.phasePositions.get(phase.nodeId);
            if (pos == null) continue;
            int nodeX = worldToScreenX(pos.x);
            int nodeY = worldToScreenY(pos.y);
            int nodeW = (int) (NODE_WIDTH * zoom);
            int nodeH = (int) (NODE_HEIGHT * zoom);
            boolean selected = screen.getController().selectedPhaseNodeIds().contains(phase.nodeId) || (screen.getController().selection().type() == EditorSelectionType.PHASE && phase.nodeId.equals(screen.getController().selection().phaseNodeId()));
            int border = selected ? 0xFFFFFF : screen.getController().isUnreachable(phase.nodeId) ? 0xFF8888 : screen.getThemeColor();
            g.fill(nodeX, nodeY, nodeX + nodeW, nodeY + nodeH, HudAnimUtil.withAlpha(0x11161E, 220));
            g.fill(nodeX, nodeY, nodeX + nodeW, nodeY + 1, HudAnimUtil.withAlpha(border, 255));
            g.fill(nodeX, nodeY + nodeH - 1, nodeX + nodeW, nodeY + nodeH, HudAnimUtil.withAlpha(border, 255));
            g.fill(nodeX, nodeY, nodeX + 1, nodeY + nodeH, HudAnimUtil.withAlpha(border, 255));
            g.fill(nodeX + nodeW - 1, nodeY, nodeX + nodeW, nodeY + nodeH, HudAnimUtil.withAlpha(border, 255));
            String title = phase.displayName == null ? phase.phaseId : phase.displayName.value;
            if (title == null || title.isBlank()) title = phase.phaseId;
            g.drawString(screen.getUiFont(), title, nodeX + 8, nodeY + 8, HudAnimUtil.withAlpha(0xFFFFFF, 255), false);
            g.drawString(screen.getUiFont(), phase.phaseId, nodeX + 8, nodeY + 20, HudAnimUtil.withAlpha(0xAAAAAA, 255), false);
            String summary = "Obj " + phase.objectives.size() + " / Choice " + phase.choices.size();
            g.drawString(screen.getUiFont(), summary, nodeX + 8, nodeY + 34, HudAnimUtil.withAlpha(0x88CCFF, 255), false);
            int linkPortX = nodeX + nodeW - 5;
            int linkPortY = nodeY + nodeH / 2;
            g.fill(linkPortX - 2, linkPortY - 2, linkPortX + 2, linkPortY + 2, HudAnimUtil.withAlpha(0x66CCFF, 255));
            nodeHitBoxes.add(new QuestEditorNodeHitBox(phase.nodeId, nodeX, nodeY, nodeW, nodeH));
        }

        if (linkingSourceNodeId != null && !linkingSourceNodeId.isBlank()) {
            EditablePhase source = findPhase(linkingSourceNodeId);
            if (source != null) {
                EditorNodePosition pos = screen.getController().quest().layout.phasePositions.get(source.nodeId);
                if (pos != null) {
                    int x1 = worldToScreenX(pos.x + NODE_WIDTH);
                    int y1 = worldToScreenY(pos.y + NODE_HEIGHT / 2.0);
                    drawLine(g, x1, y1, lastMouseX, lastMouseY, 0x99DDFF);
                }
            }
        }

        if (marqueeSelecting) {
            int minX = (int) Math.min(marqueeStartX, marqueeEndX);
            int minY = (int) Math.min(marqueeStartY, marqueeEndY);
            int maxX = (int) Math.max(marqueeStartX, marqueeEndX);
            int maxY = (int) Math.max(marqueeStartY, marqueeEndY);
            g.fill(minX, minY, maxX, maxY, HudAnimUtil.withAlpha(0x44D7FF, 40));
            g.fill(minX, minY, maxX, minY + 1, HudAnimUtil.withAlpha(0x44D7FF, 220));
            g.fill(minX, maxY - 1, maxX, maxY, HudAnimUtil.withAlpha(0x44D7FF, 220));
            g.fill(minX, minY, minX + 1, maxY, HudAnimUtil.withAlpha(0x44D7FF, 220));
            g.fill(maxX - 1, minY, maxX, maxY, HudAnimUtil.withAlpha(0x44D7FF, 220));
        }

        renderMiniMap(g);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isInRect(mouseX, mouseY, miniMapX, miniMapY, miniMapW, miniMapH) && miniMapReady) {
            double worldX = miniWorldMinX + (mouseX - miniMapContentX) / miniWorldScale;
            double worldY = miniWorldMinY + (mouseY - miniMapContentY) / miniWorldScale;
            panToWorldCenter(worldX, worldY);
            if (isInRect(mouseX, mouseY, miniViewportX1, miniViewportY1, Math.max(1, miniViewportX2 - miniViewportX1), Math.max(1, miniViewportY2 - miniViewportY1))) {
                draggingMiniViewport = true;
            }
            return true;
        }

        for (int i = nodeHitBoxes.size() - 1; i >= 0; i--) {
            QuestEditorNodeHitBox hitBox = nodeHitBoxes.get(i);
            if (hitBox.contains(mouseX, mouseY)) {
                if (button == 0 && Screen.hasShiftDown()) {
                    if (linkingSourceNodeId == null || linkingSourceNodeId.isBlank()) {
                        linkingSourceNodeId = hitBox.nodeId();
                        screen.getController().setStatusText("连线模式: 选择目标节点");
                    } else {
                        boolean created = screen.getController().createConnection(linkingSourceNodeId, hitBox.nodeId());
                        linkingSourceNodeId = "";
                        return created;
                    }
                    return true;
                }

                if (button == 0 && Screen.hasControlDown()) {
                    screen.getController().addPhaseToSelection(hitBox.nodeId());
                    return true;
                }

                boolean inMultiSelection = screen.getController().selectedPhaseNodeIds().contains(hitBox.nodeId());
                int multiCountBeforeClick = screen.getController().selectedPhaseNodeIds().size();
                if (inMultiSelection && multiCountBeforeClick > 1) {
                    draggingNodeId = "";
                    draggingMultiSelection = true;
                    double[] world = screenToWorld(mouseX, mouseY);
                    lastDragMouseWorldX = world[0];
                    lastDragMouseWorldY = world[1];
                    return true;
                }

                screen.getController().setSelection(EditorSelection.phase(hitBox.nodeId()));
                draggingMultiSelection = false;
                draggingNodeId = hitBox.nodeId();
                double[] world = screenToWorld(mouseX, mouseY);
                EditorNodePosition pos = screen.getController().quest().layout.phasePositions.get(hitBox.nodeId());
                if (pos != null) {
                    dragOffsetWorldX = world[0] - pos.x;
                    dragOffsetWorldY = world[1] - pos.y;
                }
                return true;
            }
        }

        for (QuestEditorConnectionHitBox hitBox : connectionHitBoxes) {
            if (hitBox.near(mouseX, mouseY)) {
                screen.getController().setSelection(new EditorSelection(EditorSelectionType.CONNECTION, "", "", "", hitBox.connectionId()));
                return true;
            }
        }

        if (button == 0) {
            marqueeSelecting = true;
            marqueeStartX = mouseX;
            marqueeStartY = mouseY;
            marqueeEndX = mouseX;
            marqueeEndY = mouseY;
            return true;
        }

        if (button == 1) {
            panning = true;
            panStartMouseX = mouseX;
            panStartMouseY = mouseY;
            panStartX = panX;
            panStartY = panY;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (draggingMiniViewport && miniMapReady) {
            double worldX = miniWorldMinX + (mouseX - miniMapContentX) / miniWorldScale;
            double worldY = miniWorldMinY + (mouseY - miniMapContentY) / miniWorldScale;
            panToWorldCenter(worldX, worldY);
            return true;
        }
        if (draggingMultiSelection) {
            double[] world = screenToWorld(mouseX, mouseY);
            double dx = world[0] - lastDragMouseWorldX;
            double dy = world[1] - lastDragMouseWorldY;
            lastDragMouseWorldX = world[0];
            lastDragMouseWorldY = world[1];
            return screen.getController().moveSelectedPhasesByDelta(dx, dy);
        }
        if (draggingNodeId != null && !draggingNodeId.isBlank()) {
            screen.getController().setSelection(EditorSelection.phase(draggingNodeId));
            double[] world = screenToWorld(mouseX, mouseY);
            double worldX = world[0] - dragOffsetWorldX;
            double worldY = world[1] - dragOffsetWorldY;
            return screen.getController().moveSelectedPhaseNode(Math.max(0, worldX), Math.max(0, worldY));
        }
        if (marqueeSelecting) {
            marqueeEndX = mouseX;
            marqueeEndY = mouseY;
            return true;
        }
        if (panning) {
            panX = panStartX + (mouseX - panStartMouseX);
            panY = panStartY + (mouseY - panStartMouseY);
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double oldZoom = zoom;
        zoom = Math.max(0.5, Math.min(2.0, zoom + delta * 0.1));
        if (Math.abs(zoom - oldZoom) < 1e-6) return false;
        double[] before = screenToWorld(mouseX, mouseY, oldZoom);
        double[] after = screenToWorld(mouseX, mouseY, zoom);
        panX += (after[0] - before[0]) * zoom;
        panY += (after[1] - before[1]) * zoom;
        return true;
    }

    public void mouseReleased() {
        draggingNodeId = "";
        draggingMultiSelection = false;
        draggingMiniViewport = false;
        panning = false;
        if (marqueeSelecting) {
            marqueeSelecting = false;
            int minX = (int) Math.min(marqueeStartX, marqueeEndX);
            int minY = (int) Math.min(marqueeStartY, marqueeEndY);
            int maxX = (int) Math.max(marqueeStartX, marqueeEndX);
            int maxY = (int) Math.max(marqueeStartY, marqueeEndY);
            LinkedHashSet<String> hits = new LinkedHashSet<>();
            for (QuestEditorNodeHitBox hitBox : nodeHitBoxes) {
                if (hitBox.x() >= minX && hitBox.y() >= minY && (hitBox.x() + hitBox.width()) <= maxX && (hitBox.y() + hitBox.height()) <= maxY) {
                    hits.add(hitBox.nodeId());
                }
            }
            if (!hits.isEmpty()) {
                screen.getController().setPhaseMultiSelection(hits);
            }
        }
    }

    private void renderMiniMap(GuiGraphics g) {
        int miniW = 170;
        int miniH = 110;
        int miniX = panelX + panelW - miniW - 10;
        int miniY = panelY + panelH - miniH - 10;
        this.miniMapX = miniX;
        this.miniMapY = miniY;
        this.miniMapW = miniW;
        this.miniMapH = miniH;
        this.miniMapReady = false;
        HudRenderUtil.drawGlassPanel(g, miniX, miniY, miniW, miniH, 0x091018, 190, screen.getThemeColor(), 140, 2);
        g.drawString(screen.getUiFont(), "MiniMap", miniX + 6, miniY + 5, HudAnimUtil.withAlpha(0xDDEEFF, 255), false);

        if (screen.getController().phases().isEmpty()) return;

        double minWX = Double.MAX_VALUE;
        double minWY = Double.MAX_VALUE;
        double maxWX = 0;
        double maxWY = 0;
        for (EditablePhase phase : screen.getController().phases()) {
            EditorNodePosition pos = screen.getController().quest().layout.phasePositions.get(phase.nodeId);
            if (pos == null) continue;
            minWX = Math.min(minWX, pos.x);
            minWY = Math.min(minWY, pos.y);
            maxWX = Math.max(maxWX, pos.x + NODE_WIDTH);
            maxWY = Math.max(maxWY, pos.y + NODE_HEIGHT);
        }
        if (minWX == Double.MAX_VALUE) return;

        int contentX = miniX + 6;
        int contentY = miniY + 18;
        int contentW = miniW - 12;
        int contentH = miniH - 24;
        this.miniMapContentX = contentX;
        this.miniMapContentY = contentY;
        this.miniMapContentW = contentW;
        this.miniMapContentH = contentH;

        double worldW = Math.max(1.0, maxWX - minWX);
        double worldH = Math.max(1.0, maxWY - minWY);
        double scale = Math.min(contentW / worldW, contentH / worldH);
        this.miniWorldMinX = minWX;
        this.miniWorldMinY = minWY;
        this.miniWorldScale = scale;
        this.miniMapReady = true;

        for (EditablePhase phase : screen.getController().phases()) {
            EditorNodePosition pos = screen.getController().quest().layout.phasePositions.get(phase.nodeId);
            if (pos == null) continue;
            int px = contentX + (int) ((pos.x - minWX) * scale);
            int py = contentY + (int) ((pos.y - minWY) * scale);
            int pw = Math.max(2, (int) (NODE_WIDTH * scale));
            int ph = Math.max(2, (int) (NODE_HEIGHT * scale));
            boolean selected = screen.getController().selectedPhaseNodeIds().contains(phase.nodeId);
            int color = selected ? 0xFFFFFF : 0x66CCFF;
            g.fill(px, py, px + pw, py + ph, HudAnimUtil.withAlpha(color, selected ? 140 : 90));
        }

        double viewWorldLeft = (-panX) / zoom;
        double viewWorldTop = (-panY) / zoom;
        double viewWorldRight = viewWorldLeft + (panelW - 40) / zoom;
        double viewWorldBottom = viewWorldTop + (panelH - 40) / zoom;

        int vx1 = contentX + (int) ((viewWorldLeft - minWX) * scale);
        int vy1 = contentY + (int) ((viewWorldTop - minWY) * scale);
        int vx2 = contentX + (int) ((viewWorldRight - minWX) * scale);
        int vy2 = contentY + (int) ((viewWorldBottom - minWY) * scale);

        vx1 = Math.max(contentX, Math.min(contentX + contentW, vx1));
        vy1 = Math.max(contentY, Math.min(contentY + contentH, vy1));
        vx2 = Math.max(contentX, Math.min(contentX + contentW, vx2));
        vy2 = Math.max(contentY, Math.min(contentY + contentH, vy2));

        this.miniViewportX1 = Math.min(vx1, vx2);
        this.miniViewportY1 = Math.min(vy1, vy2);
        this.miniViewportX2 = Math.max(vx1, vx2);
        this.miniViewportY2 = Math.max(vy1, vy2);

        g.fill(miniViewportX1, miniViewportY1, miniViewportX2, miniViewportY1 + 1, HudAnimUtil.withAlpha(0xFFFFFF, 220));
        g.fill(miniViewportX1, miniViewportY2 - 1, miniViewportX2, miniViewportY2, HudAnimUtil.withAlpha(0xFFFFFF, 220));
        g.fill(miniViewportX1, miniViewportY1, miniViewportX1 + 1, miniViewportY2, HudAnimUtil.withAlpha(0xFFFFFF, 220));
        g.fill(miniViewportX2 - 1, miniViewportY1, miniViewportX2, miniViewportY2, HudAnimUtil.withAlpha(0xFFFFFF, 220));
    }

    private boolean isInRect(double x, double y, int rx, int ry, int rw, int rh) {
        return x >= rx && y >= ry && x <= rx + rw && y <= ry + rh;
    }

    private void panToWorldCenter(double worldX, double worldY) {
        panX = -worldX * zoom + (panelW - 40) / 2.0;
        panY = -worldY * zoom + (panelH - 40) / 2.0;
    }

    private int worldToScreenX(double worldX) {
        return (int) (panelX + 20 + panX + worldX * zoom);
    }

    private int worldToScreenY(double worldY) {
        return (int) (panelY + 20 + panY + worldY * zoom);
    }

    private double[] screenToWorld(double screenX, double screenY) {
        return screenToWorld(screenX, screenY, zoom);
    }

    private double[] screenToWorld(double screenX, double screenY, double withZoom) {
        double wx = (screenX - panelX - 20 - panX) / withZoom;
        double wy = (screenY - panelY - 20 - panY) / withZoom;
        return new double[]{wx, wy};
    }

    private EditablePhase findPhase(String nodeId) {
        for (EditablePhase phase : screen.getController().phases()) {
            if (nodeId.equals(phase.nodeId)) return phase;
        }
        return null;
    }

    private void drawLine(GuiGraphics g, int x1, int y1, int x2, int y2, int rgb) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps <= 0) return;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int x = (int) (x1 + (x2 - x1) * t);
            int y = (int) (y1 + (y2 - y1) * t);
            g.fill(x, y, x + 1, y + 1, HudAnimUtil.withAlpha(rgb, 220));
        }
    }
}
