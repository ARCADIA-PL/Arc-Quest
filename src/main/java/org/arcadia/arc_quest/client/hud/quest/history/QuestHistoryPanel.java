package org.arcadia.arc_quest.client.hud.quest.history;


import org.arcadia.arc_quest.client.hud.HudText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.QuestHudOverlay;
import org.arcadia.arc_quest.client.hud.quest.graph.GraphBounds;
import org.arcadia.arc_quest.client.hud.quest.graph.GraphViewportController;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.PhaseTransition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class QuestHistoryPanel {

    private static final int SCREEN_DIM_ALPHA = 24;
    private static final int PANEL_BACKGROUND_ALPHA = 176;

    private static final int PANEL_W = 780;
    private static final int PANEL_H = 380;
    private static final int TOP_BAR_H = 24;
    private static final int TREE_MARGIN = 5;
    private static final int DETAIL_VERTICAL_MARGIN = 16;
    private static final float MIN_ZOOM = 0.52f;
    private static final float MAX_ZOOM = 1.55f;
    private static final float DEFAULT_ZOOM = 1.15f;
    private static final float ENTER_TIME = 0.7f;
    private static final float EXIT_TIME = 0.5f;
    private static final float FOCUS_DELAY_TIME = 0.6f;
    private static final List<QuestHistoryNodeData> renderNodes = new ArrayList<>();
    private static final Map<String, QuestHistoryNodeData> nodeMap = new HashMap<>();
    private static final QuestHistoryDetailPanel DETAIL_PANEL = new QuestHistoryDetailPanel();
    private static final GraphViewportController VIEWPORT =
            new GraphViewportController(MIN_ZOOM, MAX_ZOOM, DEFAULT_ZOOM);

    private static boolean active;
    private static boolean closing;
    private static boolean panning;
    private static boolean pendingFocusActive;
    private static long lastRenderMs;
    private static float enterTimer;
    private static float exitTimer;
    private static float focusDelayTimer;
    private static String questId;
    private static int themeColor = 0x5AD7FF;
    private static float currentScale = 1f;
    private static float currentDrawX;
    private static float currentDrawY;
    private static float titleVisibility = 1f;
    private static double lastDragX;
    private static double lastDragY;

    private QuestHistoryPanel() {
    }

    public static void trigger(String requestedQuestId) {
        ResourceLocation location = ResourceLocation.tryParse(requestedQuestId);
        QuestDefinition definition = location == null ? null : QuestRegistry.get(location);
        if (definition != null && definition.isCollectionQuest()) {
            CollectionHistoryPanel.trigger(requestedQuestId);
            return;
        }
        questId = requestedQuestId;
        themeColor = ClientQuestCache.INSTANCE.getQuestThemeColor(requestedQuestId, 0x5AD7FF);
        active = true;
        closing = false;
        panning = false;
        enterTimer = 0f;
        exitTimer = 0f;
        lastRenderMs = System.currentTimeMillis();
        VIEWPORT.reset();
        DETAIL_PANEL.reset();
        QuestHistoryNodeRenderer.reset();
        titleVisibility = 1f;
        buildGraphData();
        TreeBounds initialTree = treeBounds();
        fitCameraToGraph(initialTree.width(), initialTree.height());
        pendingFocusActive = true;
        focusDelayTimer = 0f;
    }

    public static boolean isActive() {
        return active;
    }

    public static void clearClientSession() {
        active = false;
        closing = false;
        panning = false;
        pendingFocusActive = false;
        questId = null;
        renderNodes.clear();
        nodeMap.clear();
        VIEWPORT.reset();
        DETAIL_PANEL.reset();
        QuestHistoryNodeRenderer.reset();
    }

    public static void close() {
        if (!active || closing) return;
        closing = true;
        panning = false;
        exitTimer = 0f;
        DETAIL_PANEL.close();
    }

    public static boolean keyPressed(int keyCode) {
        if (!active || closing) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_E) {
            if (DETAIL_PANEL.isOpen()) DETAIL_PANEL.close();
            else close();
            return true;
        }
        return false;
    }

    public static boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || closing) return false;
        float scaledW = PANEL_W * currentScale;
        float scaledH = PANEL_H * currentScale;
        if (mouseX < currentDrawX || mouseX > currentDrawX + scaledW
                || mouseY < currentDrawY || mouseY > currentDrawY + scaledH) {
            close();
            return true;
        }

        float localX = (float) ((mouseX - currentDrawX) / currentScale);
        float localY = (float) ((mouseY - currentDrawY) / currentScale);
        if (DETAIL_PANEL.mouseClicked(localX, localY, PANEL_W - 3, detailTop(), detailHeight(), button)) {
            return true;
        }

        TreeBounds tree = treeBounds();
        if (!tree.contains(localX, localY)) return true;
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            focusOnActivePhase();
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return true;

        QuestHistoryNodeData clicked = findNodeAt(localX, localY, tree);
        if (clicked != null) {
            if (!clicked.reached()) return true;
            DETAIL_PANEL.select(clicked);
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) focusOnNode(clicked.id(), true);
            else ensureNodeVisible(clicked);
            playClick(1.35f);
            return true;
        }

        DETAIL_PANEL.close();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            panning = true;
            lastDragX = localX;
            lastDragY = localY;
        }
        return true;
    }

    public static boolean mouseDragged(double mouseX, double mouseY) {
        if (!active || closing || !panning) return false;
        if (GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT)
                != GLFW.GLFW_PRESS) {
            panning = false;
            return true;
        }
        float localX = (float) ((mouseX - currentDrawX) / currentScale);
        float localY = (float) ((mouseY - currentDrawY) / currentScale);
        VIEWPORT.panBy((float) (localX - lastDragX), (float) (localY - lastDragY));
        lastDragX = localX;
        lastDragY = localY;
        return true;
    }

    public static boolean mouseReleased(int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) panning = false;
        return active;
    }

    public static boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!active || closing) return false;
        float localX = (float) ((mouseX - currentDrawX) / currentScale);
        float localY = (float) ((mouseY - currentDrawY) / currentScale);
        if (DETAIL_PANEL.mouseScrolled(localX, localY, delta, PANEL_W - 3, detailTop(), detailHeight())) {
            return true;
        }
        TreeBounds tree = treeBounds();
        if (!tree.contains(localX, localY)) return true;
        VIEWPORT.zoomAt(localX - tree.x(), localY - tree.y(), (float) delta * 0.12f);
        return true;
    }

    public static void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!active) return;
        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        if (minecraft.screen instanceof QuestJournalScreen journalScreen) {
            screenWidth = journalScreen.getScaledWidth();
            screenHeight = journalScreen.getScaledHeight();
        }

        long now = System.currentTimeMillis();
        float deltaTime = Math.min((now - lastRenderMs) / 1000f, 0.1f);
        lastRenderMs = now;
        DETAIL_PANEL.update(deltaTime);
        updateDelayedFocus(deltaTime);

        float finalScale = Math.min((screenWidth * 0.95f) / PANEL_W, (screenHeight * 0.82f) / PANEL_H);
        float baseX = screenWidth / 2f - PANEL_W * finalScale / 2f;
        float baseY = screenHeight / 2f - PANEL_H * finalScale / 2f;
        AnimationFrame frame = updatePanelAnimation(deltaTime, finalScale, baseX, baseY);
        if (frame == null) return;

        currentDrawX = frame.x();
        currentDrawY = frame.y();
        currentScale = frame.scale();
        int scissorX1 = (int) (currentDrawX - 10);
        int scissorX2 = (int) (currentDrawX + PANEL_W * currentScale + 10);
        if (closing) scissorX2 = (int) (currentDrawX + PANEL_W * currentScale * (1f - frame.wipe()));
        else if (enterTimer < ENTER_TIME) scissorX2 = (int) (currentDrawX + PANEL_W * currentScale * frame.reveal());

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 4500);
        graphics.fill(-1000, -1000, screenWidth + 1000, screenHeight + 1000,
                HudAnimUtil.withAlpha(0x000000, Math.round(SCREEN_DIM_ALPHA * frame.alpha())));
        enableScissor(graphics, scissorX1, (int) (currentDrawY - 10), scissorX2,
                (int) (currentDrawY + PANEL_H * currentScale + 10));
        graphics.pose().pushPose();
        graphics.pose().translate(currentDrawX, currentDrawY, 0);
        graphics.pose().scale(currentScale, currentScale, 1f);
        renderPanel(graphics, minecraft.font, frame.alpha(), deltaTime, mouseX, mouseY);
        graphics.pose().popPose();
        graphics.disableScissor();
        graphics.pose().popPose();
    }

    private static void renderPanel(GuiGraphics graphics, Font font, float alphaFactor, float deltaTime,
                                    int mouseX, int mouseY) {
        int alpha = Math.round(255 * alphaFactor);
        float localX = (mouseX - currentDrawX) / currentScale;
        float localY = (mouseY - currentDrawY) / currentScale;
        graphics.fill(3, 0, PANEL_W, PANEL_H,
                HudAnimUtil.withAlpha(0x05060A, Math.round(PANEL_BACKGROUND_ALPHA * alphaFactor)));
        drawFrame(graphics, 3, 0, PANEL_W - 3, PANEL_H, 1,
                HudAnimUtil.withAlpha(0xCCCCCC, Math.round(102 * alphaFactor)));
        HudRenderUtil.drawCyberneticEdge(graphics, 0, 0, PANEL_H, themeColor, alpha);
        graphics.pose().pushPose();
        graphics.pose().scale(0.85f, 0.85f, 1f);
        graphics.drawString(font, HudText.of("history.phase_header"), 16, 7,
                HudAnimUtil.withAlpha(0x718091, alpha), false);
        graphics.pose().popPose();
        graphics.fill(10, TOP_BAR_H - 1, PANEL_W - 10, TOP_BAR_H,
                HudAnimUtil.withAlpha(0xCCCCCC, Math.round(90 * alphaFactor)));

        VIEWPORT.update(deltaTime);
        titleVisibility = HudAnimUtil.smoothExp(titleVisibility,
                VIEWPORT.zoom() >= 0.68f ? 1f : 0f, 12f, deltaTime);
        TreeBounds tree = treeBounds();
        renderGrid(graphics, tree, alphaFactor);
        renderTree(graphics, font, tree, localX, localY, alpha, alphaFactor, deltaTime);

        QuestRuntimeData runtime = ClientQuestCache.INSTANCE.getActiveQuest(questId);
        DETAIL_PANEL.render(graphics, font, runtime, PANEL_W - 3, detailTop(), detailHeight(),
                themeColor, alphaFactor, currentDrawX, currentDrawY, currentScale, localX, localY, deltaTime);
    }

    private static void renderTree(GuiGraphics graphics, Font font, TreeBounds tree, float mouseX, float mouseY,
                                   int alpha, float alphaFactor, float deltaTime) {
        enableScissor(graphics,
                Math.round(currentDrawX + tree.x() * currentScale),
                Math.round(currentDrawY + tree.y() * currentScale),
                Math.round(currentDrawX + (tree.x() + tree.width()) * currentScale),
                Math.round(currentDrawY + (tree.y() + tree.height()) * currentScale));
        graphics.pose().pushPose();
        graphics.pose().translate(tree.x() + VIEWPORT.panX(), tree.y() + VIEWPORT.panY(), 0);
        graphics.pose().scale(VIEWPORT.zoom(), VIEWPORT.zoom(), 1f);
        QuestDefinition definition = getDefinition();
        if (definition != null) {
            for (QuestHistoryNodeData node : renderNodes) {
                for (PhaseTransition transition : node.phase().getTransitions()) {
                    for (String targetPhaseId : transition.getTargetPhaseIds()) {
                        QuestHistoryNodeData target = nodeMap.get(targetPhaseId);
                        if (target != null) drawConnection(graphics, node, target, alphaFactor);
                    }
                }
            }
            boolean inTree = tree.contains(mouseX, mouseY);
            for (QuestHistoryNodeData node : renderNodes) {
                boolean hovered = node.reached() && inTree && isInsideNode(mouseX, mouseY, tree, node);
                if (hovered && Minecraft.getInstance().screen instanceof QuestJournalScreen journalScreen) {
                    journalScreen.requestPointerCursor();
                }
                graphics.pose().pushPose();
                graphics.pose().translate(node.x(), node.y(), 2f);
                QuestHistoryNodeRenderer.render(graphics, font, node, hovered, DETAIL_PANEL.isSelected(node.id()),
                        themeColor, alphaFactor, titleVisibility, deltaTime);
                graphics.pose().popPose();
            }
        }
        graphics.pose().popPose();
        graphics.disableScissor();
    }

    private static void renderGrid(GuiGraphics graphics, TreeBounds tree, float alphaFactor) {
        int gridColor = HudAnimUtil.withAlpha(0xFFFFFF, Math.round(5 * alphaFactor));
        for (int x = tree.x() + 15; x < tree.x() + tree.width(); x += 15) {
            graphics.fill(x, tree.y(), x + 1, tree.y() + tree.height(), gridColor);
        }
        for (int y = tree.y() + 15; y < tree.y() + tree.height(); y += 15) {
            graphics.fill(tree.x(), y, tree.x() + tree.width(), y + 1, gridColor);
        }
    }

    private static void drawConnection(GuiGraphics graphics, QuestHistoryNodeData source,
                                       QuestHistoryNodeData target, float alphaFactor) {
        int sourceX = source.x() + QuestHistoryNodeRenderer.CARD_WIDTH / 2;
        int targetX = target.x() - QuestHistoryNodeRenderer.CARD_WIDTH / 2;
        int midX = (sourceX + targetX) / 2;
        int color = source.completed() ? 0x69E79A : source.active() ? themeColor : 0x4B535E;
        int lineColor = HudAnimUtil.withAlpha(color, Math.round((source.completed() ? 170 : 95) * alphaFactor));
        graphics.fill(sourceX, source.y() - 1, midX, source.y() + 1, lineColor);
        graphics.fill(midX - 1, Math.min(source.y(), target.y()), midX + 1, Math.max(source.y(), target.y()), lineColor);
        graphics.fill(midX, target.y() - 1, targetX, target.y() + 1, lineColor);
        graphics.fill(sourceX - 2, source.y() - 2, sourceX + 2, source.y() + 2, lineColor);
        graphics.fill(targetX - 2, target.y() - 2, targetX + 2, target.y() + 2, lineColor);
    }

    private static void buildGraphData() {
        renderNodes.clear();
        nodeMap.clear();
        QuestDefinition definition = getDefinition();
        if (definition == null) return;
        QuestRuntimeData runtime = ClientQuestCache.INSTANCE.getActiveQuest(questId);
        renderNodes.addAll(QuestHistoryGraphBuilder.build(questId, definition, runtime));
        for (QuestHistoryNodeData node : renderNodes) nodeMap.put(node.id(), node);
    }

    private static void fitCameraToGraph(int viewWidth, int viewHeight) {
        if (renderNodes.isEmpty()) return;
        int halfWidth = QuestHistoryNodeRenderer.CARD_WIDTH / 2;
        int halfHeight = QuestHistoryNodeRenderer.CARD_HEIGHT / 2;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (QuestHistoryNodeData node : renderNodes) {
            minX = Math.min(minX, node.x() - halfWidth - QuestHistoryNodeRenderer.VISUAL_LEFT_OVERHANG);
            maxX = Math.max(maxX, node.x() + halfWidth + QuestHistoryNodeRenderer.VISUAL_RIGHT_OVERHANG);
            minY = Math.min(minY, node.y() - halfHeight);
            maxY = Math.max(maxY, node.y() + halfHeight + QuestHistoryNodeRenderer.VISUAL_BOTTOM_OVERHANG);
        }
        VIEWPORT.fit(new GraphBounds(minX, minY, maxX, maxY), viewWidth, viewHeight, true);
    }

    private static QuestHistoryNodeData findNodeAt(float mouseX, float mouseY, TreeBounds tree) {
        for (QuestHistoryNodeData node : renderNodes) {
            if (isInsideNode(mouseX, mouseY, tree, node)) return node;
        }
        return null;
    }

    private static boolean isInsideNode(float mouseX, float mouseY, TreeBounds tree, QuestHistoryNodeData node) {
        float centerX = tree.x() + VIEWPORT.panX() + node.x() * VIEWPORT.zoom();
        float centerY = tree.y() + VIEWPORT.panY() + node.y() * VIEWPORT.zoom();
        return Math.abs(mouseX - centerX) <= QuestHistoryNodeRenderer.CARD_WIDTH * VIEWPORT.zoom() / 2f
                && Math.abs(mouseY - centerY) <= QuestHistoryNodeRenderer.CARD_HEIGHT * VIEWPORT.zoom() / 2f;
    }

    private static void focusOnNode(String phaseId, boolean emphasize) {
        QuestHistoryNodeData node = nodeMap.get(phaseId);
        if (node == null) return;
        int reservedWidth = DETAIL_PANEL.isOpen() ? QuestHistoryDetailPanel.WIDTH + 5 : 0;
        TreeBounds focusTree = treeBounds(reservedWidth);
        VIEWPORT.focus(node.x(), node.y(), focusTree.width(), focusTree.height(),
                emphasize ? 1.05f : MIN_ZOOM);
    }

    private static void ensureNodeVisible(QuestHistoryNodeData node) {
        focusOnNode(node.id(), false);
    }

    private static void focusOnActivePhase() {
        QuestRuntimeData runtime = ClientQuestCache.INSTANCE.getActiveQuest(questId);
        if (runtime == null) return;
        String phaseId = null;
        String trackedQuest = QuestHudOverlay.INSTANCE.getTrackedQuestId();
        String trackedPhase = QuestHudOverlay.INSTANCE.getTrackedPhaseId();
        if (questId.equals(trackedQuest) && trackedPhase != null && runtime.isPhaseActive(trackedPhase)) phaseId = trackedPhase;
        if (phaseId == null && runtime.isPhaseActive(runtime.getCurrentPhaseId())) phaseId = runtime.getCurrentPhaseId();
        if (phaseId == null) phaseId = runtime.getActivePhaseIds().stream().filter(nodeMap::containsKey).findFirst().orElse(null);
        if (phaseId != null) {
            focusOnNode(phaseId, true);
            playClick(1.2f);
        }
    }

    private static void updateDelayedFocus(float deltaTime) {
        if (!pendingFocusActive || closing) return;
        focusDelayTimer += deltaTime;
        if (focusDelayTimer >= FOCUS_DELAY_TIME) {
            pendingFocusActive = false;
            focusOnActivePhase();
        }
    }

    private static AnimationFrame updatePanelAnimation(float deltaTime, float finalScale, float baseX, float baseY) {
        float scale = finalScale;
        float x = baseX;
        float alpha = 1f;
        float reveal = 1f;
        float wipe = 0f;
        if (closing) {
            exitTimer += deltaTime;
            if (exitTimer >= EXIT_TIME) {
                active = false;
                closing = false;
                DETAIL_PANEL.reset();
                return null;
            }
            float progress = Math.min(1f, exitTimer / EXIT_TIME);
            wipe = (float) Math.pow(progress, 4);
            x = baseX - wipe * 6f * finalScale;
            alpha = 1f - (float) Math.pow(progress, 8);
        } else {
            enterTimer = Math.min(ENTER_TIME, enterTimer + deltaTime);
            float progress = Math.min(1f, enterTimer / ENTER_TIME);
            reveal = HudAnimUtil.easeOutQuintic(progress);
            alpha = reveal;
            scale = finalScale * (1.08f - 0.08f * reveal);
            x = baseX - (1f - reveal) * 8f * finalScale;
        }
        if (reveal <= 0.001f || wipe >= 0.999f) return null;
        float drawWidth = PANEL_W * scale;
        float drawHeight = PANEL_H * scale;
        float drawX = x - (drawWidth - PANEL_W * finalScale) / 2f;
        float drawY = baseY - (drawHeight - PANEL_H * finalScale) / 2f;
        return new AnimationFrame(drawX, drawY, scale, alpha, reveal, wipe);
    }

    private static TreeBounds treeBounds() {
        return treeBounds(DETAIL_PANEL.getReservedWidth());
    }

    private static TreeBounds treeBounds(int reservedWidth) {
        int width = Math.max(120, PANEL_W - TREE_MARGIN * 2 - Math.max(0, reservedWidth));
        return new TreeBounds(TREE_MARGIN, TOP_BAR_H + 3, width, PANEL_H - TOP_BAR_H - TREE_MARGIN - 3);
    }

    private static int detailTop() {
        return TOP_BAR_H + DETAIL_VERTICAL_MARGIN;
    }

    private static int detailHeight() {
        return PANEL_H - detailTop() - DETAIL_VERTICAL_MARGIN;
    }

    private static QuestDefinition getDefinition() {
        ResourceLocation location = ResourceLocation.tryParse(questId);
        return location == null ? null : QuestRegistry.get(location);
    }

    private static void playClick(float pitch) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, pitch);
        }
    }

    private static void enableScissor(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        if (Minecraft.getInstance().screen instanceof QuestJournalScreen journalScreen) {
            journalScreen.enableScissor(graphics, x1, y1, x2, y2);
        } else {
            graphics.enableScissor(x1, y1, x2, y2);
        }
    }

    private static void drawFrame(GuiGraphics graphics, int x, int y, int width, int height, int thickness, int color) {
        graphics.fill(x, y, x + width, y + thickness, color);
        graphics.fill(x, y + height - thickness, x + width, y + height, color);
        graphics.fill(x, y + thickness, x + thickness, y + height - thickness, color);
        graphics.fill(x + width - thickness, y + thickness, x + width, y + height - thickness, color);
    }

    private record TreeBounds(int x, int y, int width, int height) {
        private boolean contains(float mouseX, float mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private record AnimationFrame(float x, float y, float scale, float alpha, float reveal, float wipe) {
    }
}
