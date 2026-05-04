package org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.history;

import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.QuestHudOverlay;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.collection.ArcQuestCollectionHistoryManager;
import org.arcadia.arc_quest.mutil.animation.ArcPanelTransition;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.mutil.screen.ArcScissorUtil;
import org.arcadia.arc_quest.mutil.theme.ArcDrawUtil;
import org.arcadia.arc_quest.mutil.theme.ArcPanelChrome;
import org.arcadia.arc_quest.mutil.text.ArcTextLayoutUtil;
import org.arcadia.arc_quest.mutil.theme.ArcRewardRenderer;
import org.arcadia.arc_quest.mutil.theme.ArcTooltipRenderer;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.quest.api.IReward;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.PhaseTransition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.reward.ItemReward;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class ArcQuestHistoryPanelElement extends ArcGuiElement {

    private static final int PANEL_W = 630;
    private static final int PANEL_H = 270;
    private static final float MIN_ZOOM = 1f;
    private static final float MAX_ZOOM = 1.9f;
    private static final float ENTER_TIME = 0.7f;
    private static final float EXIT_TIME = 0.5f;
    private static final ArcPanelTransition TRANSITION = new ArcPanelTransition(PANEL_W, PANEL_H, ENTER_TIME, EXIT_TIME);
    private static final List<NodeData> renderNodes = new ArrayList<>();
    private static final Map<String, NodeData> nodeMap = new HashMap<>();
    private static final Component REWARDS_TITLE = Component.literal("REWARDS").withStyle(Style.EMPTY.withBold(true));
    private static final float FOCUS_DELAY_TIME = 0.6f;
    private static boolean active = false;
    private static boolean closing = false;
    private static long lastRenderMs = 0L;
    private static float enterTimer = 0f;
    private static float exitTimer = 0f;
    private static String questId;
    private static int themeColor = 0x5AD7FF;
    private static float currentScale = 1.0f;
    private static float currentDrawX = 0;
    private static float currentDrawY = 0;
    private static float zoom = 1.0f;
    private static float panX = 0f;
    private static float panY = 0f;
    private static float targetZoom = 1.0f;
    private static float targetPanX = 0f;
    private static float targetPanY = 0f;
    private static boolean panning = false;
    private static double lastDragX = 0;
    private static double lastDragY = 0;
    private static String pinnedPhaseId = null;
    private static PhaseDefinition activeTooltipPhase = null;
    private static PhaseDefinition renderingTooltipPhase = null;
    private static float tooltipAnimProgress = 0f;
    private static ItemStack hoveredRewardStack = ItemStack.EMPTY;
    private static float animTipX = 0;
    private static float animTipY = 0;
    private static float animTipW = 0;
    private static float animTipH = 0;
    private static boolean pendingFocusActive = false;
    private static float focusDelayTimer = 0f;

    public ArcQuestHistoryPanelElement() {
        super(0, 0, PANEL_W, PANEL_H);
    }

    public static void trigger(String qid) {
        QuestDefinition modeDef = QuestRegistry.get(ResourceLocation.tryParse(qid));
        if (modeDef != null && modeDef.isCollectionQuest()) {
            ArcQuestCollectionHistoryManager.trigger(qid);
            return;
        }
        questId = qid;
        themeColor = ClientQuestCache.INSTANCE.getQuestThemeColor(qid, 0x5AD7FF);
        active = true;
        closing = false;
        TRANSITION.reset();
        lastRenderMs = System.currentTimeMillis();
        zoom = 1.0f;
        panX = 0f;
        panY = 0f;
        targetZoom = 1.0f;
        targetPanX = 0f;
        targetPanY = 0f;
        panning = false;
        pinnedPhaseId = null;
        activeTooltipPhase = null;
        renderingTooltipPhase = null;
        hoveredRewardStack = ItemStack.EMPTY;
        tooltipAnimProgress = 0f;
        animTipW = 0;
        buildGraphData();
        fitCameraToGraph(PANEL_W - 8, PANEL_H - 32);
        pendingFocusActive = true;
        focusDelayTimer = 0f;
    }

    public static boolean isActive() {
        return active;
    }

    public static void close() {
        if (!active || closing) return;
        closing = true;
        TRANSITION.close();
    }

    public static boolean keyPressed(int keyCode) {
        if (!active || closing) return false;
        if (keyCode == 256 || keyCode == 69) {
            close();
            return true;
        }
        return false;
    }

    public static boolean mouseClicked(double mx, double my, int button) {
        if (!active || closing) return false;
        float scaledW = PANEL_W * currentScale, scaledH = PANEL_H * currentScale;
        if (mx < currentDrawX || mx > currentDrawX + scaledW || my < currentDrawY || my > currentDrawY + scaledH) {
            close();
            return true;
        }

        float lx = (float) ((mx - currentDrawX) / currentScale), ly = (float) ((my - currentDrawY) / currentScale);
        int treeX = 4, treeY = 28, treeW = PANEL_W - 8, treeH = PANEL_H - 32;
        boolean inBounds = lx >= treeX && lx <= treeX + treeW && ly >= treeY && ly <= treeY + treeH;

        if (inBounds) {
            if (button == 2) {
                focusOnActivePhase();
                return true;
            }
            if (button == 0 || button == 1) {
                String clickedNodeId = null;
                for (NodeData node : renderNodes) {
                    if (Math.abs(lx - (treeX + panX + node.x * zoom)) <= 15 * zoom && Math.abs(ly - (treeY + panY + node.y * zoom)) <= 15 * zoom) {
                        clickedNodeId = node.id;
                        break;
                    }
                }
                if (clickedNodeId != null) {
                    if (clickedNodeId.equals(pinnedPhaseId)) pinnedPhaseId = null;
                    else {
                        pinnedPhaseId = clickedNodeId;
                        if (button == 1) focusOnNode(clickedNodeId);
                        if (Minecraft.getInstance().player != null)
                            Minecraft.getInstance().player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.4f);
                    }
                    return true;
                } else {
                    pinnedPhaseId = null;
                    panning = true;
                    lastDragX = lx;
                    lastDragY = ly;
                }
            }
        }
        return true;
    }

    public static boolean mouseDragged(double mx, double my) {
        if (!active || closing || !panning) return false;
        if (GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            panning = false;
            return true;
        }
        float lx = (float) ((mx - currentDrawX) / currentScale), ly = (float) ((my - currentDrawY) / currentScale);
        targetPanX += (lx - lastDragX);
        targetPanY += (ly - lastDragY);
        lastDragX = lx;
        lastDragY = ly;
        return true;
    }

    public static boolean mouseReleased(int button) {
        if (button == 0) panning = false;
        return active;
    }

    public static boolean mouseScrolled(double mx, double my, double delta) {
        if (!active || closing) return false;
        float lx = (float) ((mx - currentDrawX) / currentScale), ly = (float) ((my - currentDrawY) / currentScale);
        int treeX = 4, treeY = 28, treeW = PANEL_W - 8, treeH = PANEL_H - 32;

        if (lx >= treeX && lx <= treeX + treeW && ly >= treeY && ly <= treeY + treeH) {
            float oldZoom = targetZoom;
            targetZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, targetZoom + (float) delta * 0.15f));
            targetPanX = (float) (lx - treeX - ((lx - treeX - targetPanX) / oldZoom) * targetZoom);
            targetPanY = (float) (ly - treeY - ((ly - treeY - targetPanY) / oldZoom) * targetZoom);
            return true;
        }
        return true;
    }

    private static void focusOnNode(String nodeId) {
        if (nodeId == null || !nodeMap.containsKey(nodeId)) return;
        NodeData targetNode = nodeMap.get(nodeId);
        targetZoom = 1.55f;
        targetPanX = ((PANEL_W - 8) / 2f) - targetNode.x * targetZoom;
        targetPanY = ((PANEL_H - 32) / 2f) - targetNode.y * targetZoom;
    }

    private static void focusOnActivePhase() {
        QuestRuntimeData runtime = ClientQuestCache.INSTANCE.getActiveQuest(questId);
        if (runtime == null) return;
        String targetPhase = null;
        String trackedQuest = QuestHudOverlay.INSTANCE.getTrackedQuestId(), trackedPhase = QuestHudOverlay.INSTANCE.getTrackedPhaseId();

        if (questId.equals(trackedQuest) && trackedPhase != null && nodeMap.containsKey(trackedPhase) && runtime.isPhaseActive(trackedPhase))
            targetPhase = trackedPhase;
        if (targetPhase == null) {
            String current = runtime.getCurrentPhaseId();
            if (current != null && runtime.isPhaseActive(current) && nodeMap.containsKey(current))
                targetPhase = current;
        }
        if (targetPhase == null) {
            for (String pid : runtime.getActivePhaseIds())
                if (nodeMap.containsKey(pid)) {
                    targetPhase = pid;
                    break;
                }
        }

        if (targetPhase != null && nodeMap.containsKey(targetPhase)) {
            pinnedPhaseId = null;
            focusOnNode(targetPhase);
            if (Minecraft.getInstance().player != null)
                Minecraft.getInstance().player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.2f);
        }
    }

    @Override
    public void draw(GuiGraphics g, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        int mx = context.mouseX(), my = context.mouseY();
        int screenW = context.screenWidth(), screenH = context.screenHeight();

        if (mc.screen instanceof QuestJournalScreen qjs) {
            screenW = qjs.getScaledWidth();
            screenH = qjs.getScaledHeight();
        }

        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastRenderMs) / 1000f, 0.1f);
        lastRenderMs = now;

        if (pendingFocusActive && !closing) {
            focusDelayTimer += dt;
            if (focusDelayTimer >= FOCUS_DELAY_TIME) {
                pendingFocusActive = false;
                focusOnActivePhase();
            }
        }

        float finalScale = Math.min((screenW * 0.90f) / (float) PANEL_W, (screenH * 0.65f) / (float) PANEL_H);
        ArcPanelTransition.Frame frame = TRANSITION.update(dt, screenW, screenH, finalScale);
        if (TRANSITION.isFinished()) {
            active = false;
            closing = false;
            return;
        }
        if (!frame.visible()) return;

        currentDrawX = frame.drawX();
        currentDrawY = frame.drawY();
        currentScale = frame.scale();
        float alphaF = frame.alpha();

        activeTooltipPhase = null;
        hoveredRewardStack = ItemStack.EMPTY;

        g.pose().pushPose();
        g.pose().translate(0, 0, 4500);
        ArcDrawUtil.fillFullscreenDim(g, screenW, screenH, (int) (35 * alphaF));

        ArcScissorUtil.enableScreenAware(g, frame.scissorX1(), frame.scissorY1(), frame.scissorX2(), frame.scissorY2());

        g.pose().pushPose();
        g.pose().translate(currentDrawX, currentDrawY, 0);
        g.pose().scale(currentScale, currentScale, 1f);

        renderPanel(g, mc.font, ArcDrawUtil.clampAlpha((int) (255 * alphaF)), alphaF, dt, mx, my);

        g.pose().popPose();
        ArcScissorUtil.disable(g);
        g.pose().popPose();
        if (!closing) {
            handleTooltipAnimation(dt);
            if (tooltipAnimProgress > 0.01f && renderingTooltipPhase != null) {
                int anchorX = mx, anchorY = my;
                if (pinnedPhaseId != null && nodeMap.containsKey(pinnedPhaseId)) {
                    NodeData pNode = nodeMap.get(pinnedPhaseId);
                    anchorX = (int) (currentDrawX + (4 + panX + pNode.x * zoom) * currentScale);
                    anchorY = (int) (currentDrawY + (24 + panY + pNode.y * zoom) * currentScale);
                }
                renderAwesomeTooltip(g, mc.font, renderingTooltipPhase, anchorX, anchorY, screenW, screenH, dt, mx, my);
            }
        }

        if (!hoveredRewardStack.isEmpty() && !closing) {
            Minecraft mcForTip = Minecraft.getInstance();
            if (mcForTip.player != null) {
                List<Component> lines = hoveredRewardStack.getTooltipLines(
                        mcForTip.player,
                        mcForTip.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL
                );
                ArcTooltipRenderer.renderCyber(g, mcForTip.font, lines, mx, my, screenW, screenH, themeColor);
            }
        }
    }

    private static void renderPanel(GuiGraphics g, Font font, int alpha, float alphaF, float dt, int mx, int my) {
        int PW = PANEL_W, PH = PANEL_H;
        float lx = (float) ((mx - currentDrawX) / currentScale), ly = (float) ((my - currentDrawY) / currentScale);
        int borderAlpha = (int) (0x66 * alphaF), borderRgb = 0xCCCCCC;

        ArcPanelChrome.drawQuestPanel(g, 0, 0, PW, PH, 0x05060A, (int) (0xDD * alphaF), borderRgb, borderAlpha, themeColor, alpha);

        int topBarH = 22;
        ArcPanelChrome.drawHeader(g, font, "SYS.ARC_QUEST // TOPOLOGY MAP [ 21:9 ULTRAWIDE ]  >> MOUSE-3: FOCUS CURRENT", 0.85f, 16, 6, ArcPanelChrome.DEFAULT_HEADER_RGB, alpha);
        ArcPanelChrome.drawTopDivider(g, PW, topBarH, borderRgb, borderAlpha);

        ArcPanelChrome.drawGrid(g, 1, topBarH, PW - 2, PH - topBarH - 1, 15, 0xFFFFFF, (int) (4 * alphaF));

        zoom += (targetZoom - zoom) * Math.min(1f, dt * 15f);
        panX += (targetPanX - panX) * Math.min(1f, dt * 15f);
        panY += (targetPanY - panY) * Math.min(1f, dt * 15f);

        int treeX = 4, treeY = topBarH + 2, treeW = PW - 8, treeH = PH - topBarH - 6;
        boolean inBounds = lx >= treeX && lx <= treeX + treeW && ly >= treeY && ly <= treeY + treeH;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof QuestJournalScreen) {
            ArcScissorUtil.enableScreenAware(g, (int) (currentDrawX + treeX * currentScale), (int) (currentDrawY + treeY * currentScale),
                    (int) (currentDrawX + (treeX + treeW) * currentScale), (int) (currentDrawY + (treeY + treeH) * currentScale));
        }

        g.pose().pushPose();
        g.pose().translate(treeX + panX, treeY + panY, 0);
        g.pose().scale(zoom, zoom, 1f);

        QuestDefinition def = QuestRegistry.get(ResourceLocation.tryParse(questId));
        if (def != null) {
            for (NodeData node : renderNodes) {
                PhaseDefinition phase = def.getPhase(node.id);
                if (phase == null) continue;
                for (PhaseTransition tr : phase.getTransitions()) {
                    NodeData target = nodeMap.get(tr.getTargetPhaseId());
                    if (target != null)
                        drawOrthogonalLine(g, node.x, node.y, target.x, target.y, node.completed, alphaF, themeColor);
                }
            }

            String hoveredPhaseId = null;
            long time = Util.getMillis();

            for (NodeData node : renderNodes) {
                boolean isHovered = inBounds && Math.abs(lx - (treeX + panX + node.x * zoom)) <= 15 * zoom && Math.abs(ly - (treeY + panY + node.y * zoom)) <= 15 * zoom;
                if (isHovered) hoveredPhaseId = node.id;
                boolean isPinned = node.id.equals(pinnedPhaseId);

                int nodeColor = node.completed ? 0x66FF88 : (node.active ? themeColor : 0x555555);
                int glowA = (int) ((node.completed ? 80 + 30 * Math.sin(time / 800.0) : (node.active ? 140 + 40 * Math.sin(time / 500.0) : 20 + 10 * Math.sin(time / 1000.0))) * alphaF);
                float nodeScale = node.active ? 1.0f + 0.05f * (float) Math.sin(time / 500.0) : 1.0f;

                if (isHovered || isPinned) {
                    glowA = (int) (200 * alphaF);
                    nodeScale = 1.15f;
                    nodeColor = node.active ? themeColor : (node.completed ? 0x99FFBB : 0xAAAAAA);
                }

                g.pose().pushPose();
                g.pose().translate(node.x, node.y, 0);
                g.pose().scale(nodeScale, nodeScale, 1f);
                g.pose().mulPose(Axis.ZP.rotationDegrees(45));

                if (glowA > 0) g.fill(-8, -8, 8, 8, HudAnimUtil.withAlpha(nodeColor, glowA));
                g.fill(-6, -6, 6, 6, HudAnimUtil.withAlpha(0x222222, alpha));
                g.fill(-5, -5, 5, 5, HudAnimUtil.withAlpha(nodeColor, (int) (220 * alphaF)));
                g.pose().popPose();

                g.pose().pushPose();
                g.pose().translate(node.x, node.y + 14, 0);
                g.pose().scale(0.6f, 0.6f, 1f);
                g.drawString(font, node.displayName, -node.displayNameWidth / 2, 0, HudAnimUtil.withAlpha(nodeColor, (int) ((isHovered || isPinned || node.active ? 255 : 150) * alphaF)), false);
                g.pose().popPose();
            }
            activeTooltipPhase = (pinnedPhaseId != null) ? def.getPhase(pinnedPhaseId) : (hoveredPhaseId != null ? def.getPhase(hoveredPhaseId) : null);
        }

        g.pose().popPose();
        if (mc.screen instanceof QuestJournalScreen) ArcScissorUtil.disable(g);
    }

    private static void handleTooltipAnimation(float dt) {
        if (activeTooltipPhase != null) {
            if (renderingTooltipPhase != activeTooltipPhase) {
                renderingTooltipPhase = activeTooltipPhase;
                tooltipAnimProgress = 0f;
            }
            tooltipAnimProgress = Math.min(1.0f, tooltipAnimProgress + dt * 10f);
        } else {
            tooltipAnimProgress = Math.max(0.0f, tooltipAnimProgress - dt * 15f);
            if (tooltipAnimProgress <= 0) renderingTooltipPhase = null;
        }
    }

    private static void renderAwesomeTooltip(GuiGraphics g, Font font, PhaseDefinition phase, int anchorX, int anchorY, int screenW, int screenH, float dt, int realMx, int realMy) {
        if (phase == null) return;
        PhaseTooltipData tooltipData = buildPhaseTooltipData(phase, font);

        int padding = 10, cyberEdgeWidth = 3, targetW = tooltipData.targetW, targetH = tooltipData.targetH;
        int targetX = anchorX + 16, targetY = anchorY + 16;
        if (targetX + targetW > screenW) targetX = anchorX - targetW - 8;
        if (targetY + targetH > screenH) targetY = screenH - targetH - 2;
        if (targetY < 0) targetY = 2;

        if (animTipW == 0 || Math.abs(animTipW - targetW) > 50 || tooltipAnimProgress < 0.1f) {
            animTipX = targetX;
            animTipY = targetY;
            animTipW = targetW;
            animTipH = targetH;
        } else {
            float morphSpeed = 18f;
            animTipX += (targetX - animTipX) * Math.min(1f, dt * morphSpeed);
            animTipY += (targetY - animTipY) * Math.min(1f, dt * morphSpeed);
            animTipW += (targetW - animTipW) * Math.min(1f, dt * morphSpeed);
            animTipH += (targetH - animTipH) * Math.min(1f, dt * morphSpeed);
        }

        float easeScale = (float) (1.0 - Math.pow(1.0 - tooltipAnimProgress, 4));
        if (easeScale < 0.01f) return;

        int drawX = (int) animTipX, drawY = (int) animTipY, drawW = (int) animTipW, drawH = (int) animTipH;
        ArcTooltipRenderer.CyberFrame frame = ArcTooltipRenderer.beginMorphingCyberFrame(g, drawX, drawY, drawW, drawH, easeScale, themeColor, 6000);
        if (!frame.visible()) return;

        int baseAlpha = frame.alpha();
        int contentX = frame.contentX(), contentY = frame.contentY();

        // =========================================================================
        // PASS 1: 纯 2D 通道 (绘制 Tooltip 内所有文本和框)
        // =========================================================================
        g.drawString(font, tooltipData.titleComponent, contentX, contentY, HudAnimUtil.withAlpha(0xFFFFFF, baseAlpha), false);
        contentY += font.lineHeight;

        if (!tooltipData.descLines.isEmpty()) {
            contentY += 4;
            g.fill(contentX, contentY, drawX + drawW - padding, contentY + 1, HudAnimUtil.withAlpha(0xFFFFFF, (int) (baseAlpha * 0.15)));
            contentY += 4;
            for (FormattedCharSequence line : tooltipData.descLines) {
                g.drawString(font, line, contentX, contentY, HudAnimUtil.withAlpha(0xAAAAAA, baseAlpha), false);
                contentY += font.lineHeight;
            }
        }

        int rewardStartY = contentY; // 记录 Y 轴起始点以备 PASS 2 使用

        if (!tooltipData.rewards.isEmpty()) {
            contentY += 6;
            g.drawString(font, REWARDS_TITLE, contentX, contentY, HudAnimUtil.withAlpha(0xFFCC00, baseAlpha), false);
            contentY += font.lineHeight + 4;

            float localMouseX = (realMx - frame.centerX()) / easeScale + frame.centerX(), localMouseY = (realMy - frame.centerY()) / easeScale + frame.centerY();

            ArcRewardRenderer.HoverResult hover = ArcRewardRenderer.renderTooltipRows(g, font, tooltipData.rewards, contentX, contentY, drawX + drawW - padding - contentX, baseAlpha, themeColor, localMouseX, localMouseY);
            if (!hover.hoveredStack().isEmpty()) hoveredRewardStack = hover.hoveredStack();
            contentY += ArcRewardRenderer.measureTooltipRowsHeight(tooltipData.rewards);
        }

        // =========================================================================
        // PASS 2: 纯 3D 通道 (在同样的位置叠加物品模型)
        // =========================================================================
        if (!tooltipData.rewards.isEmpty()) {
            contentY = rewardStartY + 6 + font.lineHeight + 4;
            ArcRewardRenderer.renderTooltipRowItems(g, font, tooltipData.rewards, contentX, contentY);
        }

        ArcTooltipRenderer.endMorphingCyberFrame(g, frame);
    }


    private static void drawOrthogonalLine(GuiGraphics g, int x1, int y1, int x2, int y2, boolean isCompleted, float alphaF, int theme) {
        int color = isCompleted ? HudAnimUtil.withAlpha(0x66FF88, (int) (180 * alphaF)) : HudAnimUtil.withAlpha(0x555555, (int) (100 * alphaF));
        int midX = (x1 + x2) / 2;
        g.fill(x1, y1 - 1, midX, y1 + 1, color);
        g.fill(midX - 1, Math.min(y1, y2), midX + 1, Math.max(y1, y2), color);
        g.fill(midX, y2 - 1, x2, y2 + 1, color);
    }

    private static void buildGraphData() {
        renderNodes.clear();
        nodeMap.clear();
        QuestDefinition def = QuestRegistry.get(ResourceLocation.tryParse(questId));
        QuestRuntimeData runtime = ClientQuestCache.INSTANCE.getActiveQuest(questId);
        if (def == null || runtime == null) return;

        Set<String> phaseOrder = def.getPhaseIds(), completed = runtime.getCompletedPhaseIds(), active = runtime.getActivePhaseIds();
        Map<String, Integer> depth = new HashMap<>();
        for (String id : phaseOrder) depth.put(id, 0);
        for (String id : phaseOrder) {
            PhaseDefinition p = def.getPhase(id);
            if (p != null) {
                int curD = depth.getOrDefault(id, 0);
                for (PhaseTransition tr : p.getTransitions())
                    depth.put(tr.getTargetPhaseId(), Math.max(depth.getOrDefault(tr.getTargetPhaseId(), 0), curD + 1));
            }
        }

        Map<Integer, List<String>> nodesByDepth = new HashMap<>();
        for (String pid : phaseOrder)
            nodesByDepth.computeIfAbsent(depth.getOrDefault(pid, 0), k -> new ArrayList<>()).add(pid);

        final int dx = 60, dy = 40;
        for (Map.Entry<Integer, List<String>> entry : nodesByDepth.entrySet()) {
            int d = entry.getKey(), totalInLayer = entry.getValue().size();
            for (int i = 0; i < totalInLayer; i++) {
                String pid = entry.getValue().get(i);
                PhaseDefinition phase = def.getPhase(pid);
                if (phase == null) continue;
                Font font = Minecraft.getInstance().font;
                String displayName = ClientQuestCache.INSTANCE.getPhaseDisplayName(questId, pid);
                NodeData nd = new NodeData(pid, d * dx, (int) ((i - (totalInLayer - 1) / 2.0f) * dy), completed.contains(pid), !completed.contains(pid) && active.contains(pid), displayName, font.width(displayName), buildPhaseTooltipData(phase, font));
                renderNodes.add(nd);
                nodeMap.put(pid, nd);
            }
        }
    }


    private static PhaseTooltipData buildPhaseTooltipData(PhaseDefinition phase, Font font) {
        PhaseTooltipData data = new PhaseTooltipData();
        data.title = phase.getDisplayName().getString();
        data.titleComponent = Component.literal(data.title).withStyle(Style.EMPTY.withBold(true));

        ArcTextLayoutUtil.TooltipText tooltipText = ArcTextLayoutUtil.tooltipText(font, data.titleComponent, phase.getDescription(), 180);
        data.titleWidth = tooltipText.titleWidth();
        data.descLines = tooltipText.descLines();
        data.textMaxWidth = tooltipText.textMaxWidth();

        if (!phase.getPhaseRewards().isEmpty()) {
            data.textMaxWidth = Math.max(data.textMaxWidth, font.width(REWARDS_TITLE));
            List<ArcRewardRenderer.Row> rewards = new ArrayList<>(phase.getPhaseRewards().size());
            for (IReward reward : phase.getPhaseRewards()) {
                ArcRewardRenderer.Row rd;
                if (reward instanceof ItemReward ir) {
                    ItemStack stack = new ItemStack(ir.getItem(), Math.min(64, ir.getCount()));
                    String text = stack.getHoverName().getString() + (ir.getCount() > 1 ? " x" + ir.getCount() : "");
                    rd = new ArcRewardRenderer.Row(stack, text, 24 + font.width(text), true);
                } else {
                    String text = reward.describe();
                    rd = new ArcRewardRenderer.Row(ItemStack.EMPTY, text, 24 + font.width(text), false);
                }
                data.textMaxWidth = Math.max(data.textMaxWidth, rd.width());
                rewards.add(rd);
            }
            data.rewards = rewards;
        }

        data.targetW = data.textMaxWidth + 20 + 3;
        data.targetH = 10 + font.lineHeight;
        if (!data.descLines.isEmpty()) data.targetH += 6 + data.descLines.size() * font.lineHeight;
        if (!data.rewards.isEmpty()) data.targetH += 10 + font.lineHeight + data.rewards.size() * 22;
        data.targetH += 10;
        return data;
    }
    private static void fitCameraToGraph(int viewW, int viewH) {
        if (renderNodes.isEmpty()) return;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (NodeData n : renderNodes) {
            if (n.x < minX) minX = n.x;
            if (n.x > maxX) maxX = n.x;
            if (n.y < minY) minY = n.y;
            if (n.y > maxY) maxY = n.y;
        }
        int treeW = Math.max(1, maxX - minX), treeH = Math.max(1, maxY - minY);
        targetZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, Math.min((float) viewW / (treeW + 60), (float) viewH / (treeH + 60))));
        targetPanX = (viewW - treeW * targetZoom) / 2f - minX * targetZoom;
        targetPanY = (viewH - treeH * targetZoom) / 2f - minY * targetZoom;
        zoom = targetZoom;
        panX = targetPanX;
        panY = targetPanY;
    }

    private static class PhaseTooltipData {
        String title = "";
        Component titleComponent = Component.empty();
        int titleWidth = 0;
        List<FormattedCharSequence> descLines = List.of();
        int descMaxWidth = 0;
        List<ArcRewardRenderer.Row> rewards = List.of();
        int textMaxWidth = 0;
        int targetW = 0;
        int targetH = 0;
    }

    private record NodeData(String id, int x, int y, boolean completed, boolean active, String displayName,
                            int displayNameWidth, PhaseTooltipData tooltipData) {
    }
}