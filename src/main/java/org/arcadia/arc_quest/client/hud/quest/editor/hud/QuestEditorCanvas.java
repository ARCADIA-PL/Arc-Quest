package org.arcadia.arc_quest.client.hud.quest.editor.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.quest.editor.*;

import java.util.Map;

public class QuestEditorCanvas {
    private static final int NODE_W = 180, NODE_H = 80, ENTRY_W = 130, ENTRY_H = 72;
    private final QuestEditorScreen screen;
    private final QuestEditorController controller;
    private boolean isPanning;
    private boolean isRightDraggingNode;
    private String draggingNodeId;

    public QuestEditorCanvas(QuestEditorScreen screen, QuestEditorController controller) {
        this.screen = screen;
        this.controller = controller;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        QuestEditorState s = controller.state();
        QuestEditorLayoutEnvelope l = screen.getCurrentLayout();
        if (l == null) return;

        g.pose().pushPose();
        g.pose().translate(screen.width / 2.0, screen.height / 2.0, 0);
        g.pose().scale((float) s.zoom, (float) s.zoom, 1f);
        g.pose().translate(-s.cameraX, -s.cameraY, 0);

        Font font = screen.getMinecraft().font;
        double wx = (mouseX - screen.width / 2.0) / s.zoom + s.cameraX;
        double wy = (mouseY - screen.height / 2.0) / s.zoom + s.cameraY;

        if ("PROGRESSION".equals(l.mode) && l.graphLayout != null) {
            renderProgress(g, font, l.graphLayout, wx, wy);
        } else if (l.collectionLayout != null) {
            renderCollection(g, font, l.collectionLayout, wx, wy);
        }

        // 渲染正在拖拽的连接线
        if (s.mode == QuestEditorMode.CONNECT_GOTO && s.connectSourcePhaseNodeId != null && l.graphLayout != null) {
            var p = l.graphLayout.nodePositions.get(s.connectSourcePhaseNodeId);
            if (p != null) {
                drawLine(g, (int) (p.x + NODE_W), (int) (p.y + NODE_H / 2), (int) wx, (int) wy, 0xFFFFCC00);
            }
        }
        g.pose().popPose();
    }

    private void renderProgress(GuiGraphics g, Font font, QuestEditorGraphLayout l, double mx, double my) {
        for (EditorEdgeLayout e : l.edges) {
            var a = l.nodePositions.get(e.sourceNodeId);
            var b = l.nodePositions.get(e.targetNodeId);
            if (a != null && b != null) {
                drawLine(g, (int) (a.x + NODE_W), (int) (a.y + NODE_H / 2), (int) b.x, (int) (b.y + NODE_H / 2), "CHOICE".equals(e.edgeType) ? 0xFFFFAA00 : 0xFF5AD7FF);
            }
        }

        for (Map.Entry<String, org.arcadia.arc_quest.quest.editor.model.EditorNodePosition> e : l.nodePositions.entrySet()) {
            String id = e.getKey();
            var p = e.getValue();
            int x = (int) p.x, y = (int) p.y;

            boolean hover = inside(mx, my, x, y, NODE_W, NODE_H);
            boolean sel = controller.selectedPhaseNodeIds().contains(id);
            boolean del = controller.state().pendingDeletePhaseNodeIds.contains(id);

            g.fill(x, y, x + NODE_W, y + NODE_H, hover ? 0xFF2A2A35 : 0xFF1A1A24);
            outline(g, x, y, NODE_W, NODE_H, del ? 0xFFFF4444 : (sel ? 0xFF5AD7FF : 0xFF555566));

            var phase = controller.phaseByNodeId(id);
            String name = phase != null && phase.displayName != null ? phase.displayName.value : id;
            g.drawString(font, font.plainSubstrByWidth(name, NODE_W - 10), x + 5, y + 5, 0xFFFFFF, false);
            g.drawString(font, "ID: " + id, x + 5, y + 20, 0xAAAAAA, false);

            if (sel) {
                int ex = x + NODE_W - 70, ey = y + NODE_H - 24;
                g.fill(ex, ey, ex + 62, ey + 18, 0xFF000000);
                outline(g, ex, ey, 62, 18, 0xFF5AD7FF);
                g.drawString(font, "EDIT", ex + 19, ey + 5, 0x5AD7FF, false);
            }
        }
    }

    private void renderCollection(GuiGraphics g, Font font, CollectionLayout l, double mx, double my) {
        for (CollectionEntryLayout e : l.globalEntries) renderEntry(g, font, e, mx, my);
        for (CollectionCategoryLayout c : l.categories) {
            int x = (int) c.x, y = (int) c.y;
            g.fill(x, y, x + (int) c.w, y + 40, 0xFF112233);
            outline(g, x, y, (int) c.w, 40, 0xFF5AD7FF);
            g.drawString(font, "CAT: " + c.categoryId, x + 10, y + 15, 0xFFFFFF, false);
            if (!c.collapsed) {
                for (CollectionEntryLayout e : c.entries) renderEntry(g, font, e, mx, my);
            }
        }
    }

    private void renderEntry(GuiGraphics g, Font font, CollectionEntryLayout e, double mx, double my) {
        int x = (int) e.x, y = (int) e.y;
        g.fill(x, y, x + ENTRY_W, y + ENTRY_H, inside(mx, my, x, y, ENTRY_W, ENTRY_H) ? 0xFF223344 : 0xFF0A0D12);
        outline(g, x, y, ENTRY_W, ENTRY_H, 0xFF556677);
        g.drawString(font, e.entryId, x + 5, y + 5, 0xDDDDDD, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseY < 30 || (mouseX > screen.width - 140 && mouseY > 30) || !screen.hasActiveQuest()) return false;

        QuestEditorState s = controller.state();
        double wx = (mouseX - screen.width / 2.0) / s.zoom + s.cameraX;
        double wy = (mouseY - screen.height / 2.0) / s.zoom + s.cameraY;
        QuestEditorLayoutEnvelope env = screen.getCurrentLayout();

        if (env == null) return false;
        String node = hitNode(env, wx, wy);

        // 鼠标右键：拖拽初始化或上下文交互
        if (button == 1) return rightClick(env, s, node);

        // 鼠标左键逻辑
        if (button != 0) return false;

        if (s.mode == QuestEditorMode.PLACE_PHASE) {
            controller.placePhaseAt(wx, wy);
            screen.refreshLayout();
            return true;
        }

        if (node != null) return clickNode(env, s, node, wx, wy);

        if ("COLLECTION".equals(env.mode) && env.collectionLayout != null && clickCollection(env.collectionLayout, s, wx, wy)) {
            return true;
        }

        // 点击空白处：清空选择，准备平移
        if (s.mode == QuestEditorMode.IDLE) controller.setSelection(null);
        isPanning = true;
        return true;
    }

    private boolean rightClick(QuestEditorLayoutEnvelope env, QuestEditorState s, String node) {
        if (s.mode == QuestEditorMode.PLACE_PHASE) {
            controller.cancelPlacePhase();
            return true;
        }
        if (node == null) return false;

        // 绑定拖拽节点
        controller.selectPhaseByNodeId(node);
        isRightDraggingNode = true;
        draggingNodeId = node;
        return true;
    }

    private boolean clickNode(QuestEditorLayoutEnvelope env, QuestEditorState s, String node, double wx, double wy) {
        // 删除模式处理
        if (s.mode == QuestEditorMode.DELETE_PHASE_SELECT) {
            if (s.pendingDeletePhaseNodeIds.contains(node)) {
                controller.removePhaseFromPendingDelete(node);
            } else {
                controller.addPhaseToPendingDelete(node);
            }
            return true;
        }

        // 连线模式处理
        if (s.mode == QuestEditorMode.CONNECT_GOTO) {
            controller.finishGotoConnection(node); // 基于最新源码修正
            screen.refreshLayout();
            return true;
        }

        // 编辑按钮点击判定
        var p = env.graphLayout.nodePositions.get(node);
        boolean edit = controller.selectedPhaseNodeIds().contains(node) && p != null && inside(wx, wy, p.x + NODE_W - 70, p.y + NODE_H - 24, 62, 18);

        if (edit) {
            screen.openModal(QuestEditorScreen.ModalType.PHASE_EDIT);
            return true;
        }

        // 节点选中反选逻辑
        if (controller.selectedPhaseNodeIds().contains(node)) {
            controller.setSelection(null);
        } else {
            controller.selectPhaseByNodeId(node); // 基于最新源码修正
        }
        return true;
    }

    private boolean clickCollection(CollectionLayout l, QuestEditorState s, double wx, double wy) {
        CollectionEntryLayout hitEntry = null;
        CollectionCategoryLayout hitCat = null;

        for (CollectionEntryLayout e : l.globalEntries) if (inside(wx, wy, e.x, e.y, e.w, e.h)) hitEntry = e;
        for (CollectionCategoryLayout c : l.categories) {
            if (inside(wx, wy, c.x, c.y, c.w, 40)) hitCat = c;
            for (CollectionEntryLayout e : c.entries) if (inside(wx, wy, e.x, e.y, e.w, e.h)) hitEntry = e;
        }

        boolean isDeleteMode = s.mode == QuestEditorMode.DELETE_COLLECTION_OBJECT_SELECT;

        if (hitEntry != null) {
            if (isDeleteMode) {
                EditorObjectRef ref = EditorObjectRef.entry(hitEntry.entryId);
                toggleCollectionObjectDelete(s, ref);
            } else {
                screen.editingEntryId = hitEntry.entryId;
                screen.openModal(QuestEditorScreen.ModalType.ENTRY_EDIT);
            }
            return true;
        }

        if (hitCat != null) {
            if (isDeleteMode) {
                EditorObjectRef ref = EditorObjectRef.category(hitCat.categoryId);
                toggleCollectionObjectDelete(s, ref);
            } else {
                screen.editingCategoryId = hitCat.categoryId;
                screen.openModal(QuestEditorScreen.ModalType.CATEGORY_EDIT);
            }
            return true;
        }
        return false;
    }

    private void toggleCollectionObjectDelete(QuestEditorState s, EditorObjectRef ref) {
        // 使用 Controller 内部判定的 key 结构检测是否存在
        String refKey = ref.type().name() + "::" + ref.ownerId() + "::" + ref.id();
        if (s.pendingDeleteCollectionObjectRefs.contains(refKey)) {
            controller.removeCollectionObjectFromPendingDelete(ref);
        } else {
            controller.addCollectionObjectToPendingDelete(ref);
        }
    }

    private String hitNode(QuestEditorLayoutEnvelope env, double wx, double wy) {
        if (!"PROGRESSION".equals(env.mode) || env.graphLayout == null) return null;
        for (var e : env.graphLayout.nodePositions.entrySet()) {
            if (inside(wx, wy, e.getValue().x, e.getValue().y, NODE_W, NODE_H)) return e.getKey();
        }
        return null;
    }

    private boolean inside(double px, double py, double x, double y, double w, double h) {
        return px >= x && px <= x + w && py >= y && py <= y + h;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        QuestEditorState s = controller.state();

        // 使用最新的 Controller API 处理右键增量移动
        if (isRightDraggingNode && draggingNodeId != null && button == 1) {
            if (!controller.selectedPhaseNodeIds().contains(draggingNodeId)) {
                controller.selectPhaseByNodeId(draggingNodeId);
            }
            // 通过直接传递鼠标 delta 的缩放偏移量实现平滑增量移动，彻底告别之前闪移和坐标取值为空的 bug
            controller.moveSelectedPhasesByDelta(dragX / s.zoom, dragY / s.zoom);
            screen.refreshLayout();
            return true;
        }

        // 处理左键画布平移
        if (isPanning && button == 0) {
            s.cameraX -= dragX / s.zoom;
            s.cameraY -= dragY / s.zoom;
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isPanning = false;
        isRightDraggingNode = false;
        draggingNodeId = null;
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseY < 30 || mouseX > screen.width - 140 || !screen.hasActiveQuest()) return false;
        QuestEditorState s = controller.state();
        double oldZoom = s.zoom;

        // 限制缩放级别
        s.zoom = Math.max(0.2, Math.min(3.0, s.zoom + delta * 0.1));

        // 镜头补偿修正：让缩放始终以鼠标悬停点为中心
        s.cameraX += (mouseX - screen.width / 2.0) * (1 / oldZoom - 1 / s.zoom);
        s.cameraY += (mouseY - screen.height / 2.0) * (1 / oldZoom - 1 / s.zoom);
        return true;
    }

    // 保留原始的 DDA 画线算法进行版本兼容
    private void drawLine(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        int dx = x2 - x1, dy = y2 - y1, steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) return;
        float xi = dx / (float) steps, yi = dy / (float) steps, x = x1, y = y1;
        for (int i = 0; i <= steps; i++) {
            g.fill((int) x, (int) y, (int) x + 2, (int) y + 2, color);
            x += xi;
            y += yi;
        }
    }

    private void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}