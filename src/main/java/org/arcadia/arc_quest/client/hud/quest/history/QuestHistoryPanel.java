package org.arcadia.arc_quest.client.hud.quest.history;

import com.mojang.blaze3d.systems.RenderSystem;
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
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.QuestHudOverlay;
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

public final class QuestHistoryPanel {

    private static final int PANEL_W = 630;
    private static final int PANEL_H = 270;
    private static final float MIN_ZOOM = 1f;
    private static final float MAX_ZOOM = 1.9f;
    private static final float ENTER_TIME = 0.7f;
    private static final float EXIT_TIME = 0.5f;
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

    private QuestHistoryPanel() {
    }

    // 【终极优化：内联矩形拼接边框】
    private static void drawFastFrame(GuiGraphics g, int x, int y, int w, int h, int thickness, int color) {
        g.fill(x, y, x + w, y + thickness, color);
        g.fill(x, y + h - thickness, x + w, y + h, color);
        g.fill(x, y + thickness, x + thickness, y + h - thickness, color);
        g.fill(x + w - thickness, y + thickness, x + w, y + h - thickness, color);
    }

    public static void trigger(String qid) {
        QuestDefinition modeDef = QuestRegistry.get(ResourceLocation.tryParse(qid));
        if (modeDef != null && modeDef.isCollectionQuest()) {
            CollectionHistoryPanel.trigger(qid);
            return;
        }
        questId = qid;
        themeColor = ClientQuestCache.INSTANCE.getQuestThemeColor(qid, 0x5AD7FF);
        active = true;
        closing = false;
        enterTimer = 0f;
        exitTimer = 0f;
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
        exitTimer = 0f;
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

    public static void render(GuiGraphics g, int mx, int my, float partialTick) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth(), screenH = mc.getWindow().getGuiScaledHeight();

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
        float baseX = (screenW / 2f) - ((PANEL_W * finalScale) / 2f), baseY = (screenH / 2f) - ((PANEL_H * finalScale) / 2f);
        float scaleAnim = finalScale, currentX = baseX, currentY = baseY, alphaF = 1.0f, revealProgress = 1.0f, wipeProgress = 0.0f;

        if (closing) {
            exitTimer += dt;
            if (exitTimer >= EXIT_TIME) {
                active = false;
                closing = false;
                return;
            }
            float t = Math.min(1.0f, exitTimer / EXIT_TIME);
            wipeProgress = (float) Math.pow(t, 4.0);
            currentX = baseX - (wipeProgress * 4.0f * finalScale * 1.5f);
            alphaF = 1.0f - (float) Math.pow(t, 8.0);
        } else {
            enterTimer = Math.min(ENTER_TIME, enterTimer + dt);
            float t = Math.min(1.0f, enterTimer / ENTER_TIME);
            revealProgress = (float) (1.0 - Math.pow(1.0 - t, 5));
            alphaF = revealProgress;
            scaleAnim = finalScale * (1.10f - 0.10f * revealProgress);
            currentX = baseX - (1.0f - revealProgress) * 4.0f * finalScale * 2f;
        }

        if (revealProgress <= 0.001f || wipeProgress >= 0.999f) return;

        float drawWidth = PANEL_W * scaleAnim, drawHeight = PANEL_H * scaleAnim;
        currentDrawX = currentX - (drawWidth - PANEL_W * finalScale) / 2f;
        currentDrawY = currentY - (drawHeight - PANEL_H * finalScale) / 2f;
        currentScale = scaleAnim;

        int scX1 = (int) (currentDrawX - 10), scX2 = (int) (currentDrawX + drawWidth + 10);
        if (closing) scX2 = (int) (currentDrawX + drawWidth * (1.0f - wipeProgress));
        else if (enterTimer < ENTER_TIME) scX2 = (int) (currentDrawX + drawWidth * revealProgress);

        activeTooltipPhase = null;
        hoveredRewardStack = ItemStack.EMPTY;

        g.pose().pushPose();
        g.pose().translate(0, 0, 4500);
        g.fill(-1000, -1000, screenW + 1000, screenH + 1000, HudAnimUtil.withAlpha(0x000000, (int) (35 * alphaF)));

        if (mc.screen instanceof QuestJournalScreen qjs)
            qjs.enableScissor(g, scX1, (int) (currentDrawY - 10), scX2, (int) (currentDrawY + drawHeight + 10));
        else g.enableScissor(scX1, (int) (currentDrawY - 10), scX2, (int) (currentDrawY + drawHeight + 10));

        g.pose().pushPose();
        g.pose().translate(currentDrawX, currentDrawY, 0);
        g.pose().scale(scaleAnim, scaleAnim, 1f);

        renderPanel(g, mc.font, Math.max(0, Math.min(255, (int) (255 * alphaF))), alphaF, dt, mx, my);

        g.pose().popPose();
        if (mc.screen instanceof QuestJournalScreen qjs) g.disableScissor();
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
                renderCyberTooltip(g, mcForTip.font, lines, mx, my, themeColor);
            }
        }
    }

    private static void renderPanel(GuiGraphics g, Font font, int alpha, float alphaF, float dt, int mx, int my) {
        int PW = PANEL_W, PH = PANEL_H;
        float lx = (float) ((mx - currentDrawX) / currentScale), ly = (float) ((my - currentDrawY) / currentScale);
        int borderAlpha = (int) (0x66 * alphaF), borderRgb = 0xCCCCCC;

        g.fill(3, 0, PW, PH, HudAnimUtil.withAlpha(0x05060A, (int) (0xDD * alphaF)));
        drawFastFrame(g, 3, 0, PW - 3, PH, 1, HudAnimUtil.withAlpha(borderRgb, borderAlpha));
        HudRenderUtil.drawCyberneticEdge(g, 0, 0, PH, themeColor, alpha);

        int topBarH = 22;
        g.pose().pushPose();
        g.pose().scale(0.85f, 0.85f, 1f);
        g.drawString(font, "SYS.ARC_QUEST // TOPOLOGY MAP [ 21:9 ULTRAWIDE ]  >> MOUSE-3: FOCUS CURRENT", 16, 6, HudAnimUtil.withAlpha(0x667788, alpha), false);
        g.pose().popPose();
        g.fill(10, topBarH - 1, PW - 10, topBarH, HudAnimUtil.withAlpha(borderRgb, borderAlpha));

        for (int i = 15; i < PW; i += 15)
            g.fill(i, topBarH, i + 1, PH - 1, HudAnimUtil.withAlpha(0xFFFFFF, (int) (4 * alphaF)));
        for (int i = topBarH + 15; i < PH; i += 15)
            g.fill(1, i, PW - 1, i + 1, HudAnimUtil.withAlpha(0xFFFFFF, (int) (4 * alphaF)));

        zoom += (targetZoom - zoom) * Math.min(1f, dt * 15f);
        panX += (targetPanX - panX) * Math.min(1f, dt * 15f);
        panY += (targetPanY - panY) * Math.min(1f, dt * 15f);

        int treeX = 4, treeY = topBarH + 2, treeW = PW - 8, treeH = PH - topBarH - 6;
        boolean inBounds = lx >= treeX && lx <= treeX + treeW && ly >= treeY && ly <= treeY + treeH;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof QuestJournalScreen qjs) {
            qjs.enableScissor(g, (int) (currentDrawX + treeX * currentScale), (int) (currentDrawY + treeY * currentScale),
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
        if (mc.screen instanceof QuestJournalScreen qjs) g.disableScissor();
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
        int baseAlpha = Math.min(255, Math.max(0, (int) (255 * easeScale))), bgAlpha = (int) (0xD0 * easeScale), borderAlpha = (int) (0x66 * easeScale);

        g.pose().pushPose();
        g.pose().translate(0, 0, 6000);
        float centerX = drawX + drawW / 2f, centerY = drawY + drawH / 2f;
        g.pose().translate(centerX, centerY, 0);
        g.pose().scale(easeScale, easeScale, 1f);
        g.pose().translate(-centerX, -centerY, 0);

        g.fill(drawX + cyberEdgeWidth, drawY, drawX + drawW, drawY + drawH, HudAnimUtil.withAlpha(0x000000, bgAlpha));
        drawFastFrame(g, drawX + cyberEdgeWidth, drawY, drawW - cyberEdgeWidth, drawH, 1, HudAnimUtil.withAlpha(0xCCCCCC, borderAlpha));
        HudRenderUtil.drawCyberneticEdge(g, drawX, drawY, drawH, themeColor, baseAlpha);

        Minecraft mc = Minecraft.getInstance();
        boolean scissored = true;
        if (mc.screen instanceof QuestJournalScreen qjs)
            qjs.enableScissor(g, drawX, drawY, drawX + drawW, drawY + drawH);
        else g.enableScissor(drawX, drawY, drawX + drawW, drawY + drawH);

        int contentX = drawX + cyberEdgeWidth + padding, contentY = drawY + padding;

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

            float localMouseX = (realMx - centerX) / easeScale + centerX, localMouseY = (realMy - centerY) / easeScale + centerY;

            for (RewardRenderData reward : tooltipData.rewards) {
                g.fill(contentX, contentY, drawX + drawW - padding, contentY + 20, HudAnimUtil.withAlpha(0xFFFFFF, (int) (baseAlpha * 0.05)));
                if (!reward.item)
                    g.drawString(font, "■", contentX + 6, contentY + 6, HudAnimUtil.withAlpha(themeColor, baseAlpha), false);
                g.drawString(font, reward.text, contentX + 24, contentY + 6, HudAnimUtil.withAlpha(reward.item ? 0xFFFFFF : 0xDDDDDD, baseAlpha), false);

                if (reward.item && localMouseX >= contentX && localMouseX <= contentX + 24 && localMouseY >= contentY && localMouseY <= contentY + 22) {
                    hoveredRewardStack = reward.stack;
                }
                contentY += 22;
            }
        }

        // =========================================================================
        // PASS 2: 纯 3D 通道 (在同样的位置叠加物品模型)
        // =========================================================================
        if (!tooltipData.rewards.isEmpty()) {
            contentY = rewardStartY + 6 + font.lineHeight + 4; // 重置 Y 坐标到奖励区起始点
            RenderSystem.enableDepthTest();
            for (RewardRenderData reward : tooltipData.rewards) {
                if (reward.item) {
                    g.renderItem(reward.stack, contentX + 2, contentY + 2);
                    g.renderItemDecorations(font, reward.stack, contentX + 2, contentY + 2);
                }
                contentY += 22;
            }
            RenderSystem.disableDepthTest();
        }

        if (scissored) g.disableScissor();
        g.pose().popPose();
    }

    private static TooltipLayout getRewardTooltipLayout(ItemStack stack, Minecraft mc, boolean advanced) {
        TooltipLayout layout = new TooltipLayout();
        if (mc.player != null) {
            layout.lines = stack.getTooltipLines(mc.player, advanced ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL);
            for (Component line : layout.lines) {
                int lw = mc.font.width(line);
                if (lw > layout.textMaxWidth) layout.textMaxWidth = lw;
            }
        }
        return layout;
    }

    private static void renderCyberTooltip(GuiGraphics g, Font font, List<Component> lines, int mouseX, int mouseY, int theme) {
        if (lines == null || lines.isEmpty()) return;
        int textMaxWidth = 0;
        for (Component line : lines) {
            int lw = font.width(line);
            if (lw > textMaxWidth) textMaxWidth = lw;
        }
        int padding = 6, cyberEdgeWidth = 3, drawW = textMaxWidth + padding * 2 + cyberEdgeWidth + 2, drawH = lines.size() * font.lineHeight + padding * 2;
        Minecraft mc = Minecraft.getInstance();
        int drawX = mouseX + 12, drawY = mouseY - 12;
        if (drawX + drawW > mc.getWindow().getGuiScaledWidth()) drawX = mouseX - drawW - 8;
        if (drawY + drawH > mc.getWindow().getGuiScaledHeight())
            drawY = mc.getWindow().getGuiScaledHeight() - drawH - 2;
        if (drawY < 2) drawY = 2;

        g.pose().pushPose();
        g.pose().translate(0, 0, 8000);
        g.fill(drawX + cyberEdgeWidth, drawY, drawX + drawW, drawY + drawH, HudAnimUtil.withAlpha(0x000000, 0xD0));
        drawFastFrame(g, drawX + cyberEdgeWidth, drawY, drawW - cyberEdgeWidth, drawH, 1, HudAnimUtil.withAlpha(0xCCCCCC, 0x66));
        HudRenderUtil.drawCyberneticEdge(g, drawX, drawY, drawH, theme, 0xFF);

        int textX = drawX + cyberEdgeWidth + padding + 1, textY = drawY + padding;
        for (Component line : lines) {
            g.drawString(font, line, textX, textY, HudAnimUtil.withAlpha(0xFFFFFF, 0xFF), false);
            textY += font.lineHeight;
        }
        g.pose().popPose();
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
        data.titleWidth = font.width(data.title);
        data.descLines = font.split(phase.getDescription(), 200 - 20);
        data.textMaxWidth = data.titleWidth;
        for (FormattedCharSequence line : data.descLines)
            data.textMaxWidth = Math.max(data.textMaxWidth, font.width(line));

        if (!phase.getPhaseRewards().isEmpty()) {
            data.textMaxWidth = Math.max(data.textMaxWidth, font.width(REWARDS_TITLE));
            List<RewardRenderData> rewards = new ArrayList<>(phase.getPhaseRewards().size());
            for (IReward reward : phase.getPhaseRewards()) {
                RewardRenderData rd = new RewardRenderData();
                if (reward instanceof ItemReward ir) {
                    rd.item = true;
                    rd.stack = new ItemStack(ir.getItem(), Math.min(64, ir.getCount()));
                    rd.text = rd.stack.getHoverName().getString() + (ir.getCount() > 1 ? " x" + ir.getCount() : "");
                } else {
                    rd.item = false;
                    rd.text = reward.describe();
                }
                rd.width = 24 + font.width(rd.text);
                data.textMaxWidth = Math.max(data.textMaxWidth, rd.width);
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

    private static class TooltipLayout {
        List<Component> lines = List.of();
        int textMaxWidth = 0;
    }

    private static class RewardRenderData {
        ItemStack stack = ItemStack.EMPTY;
        String text = "";
        int width = 0;
        boolean item;
    }

    private static class PhaseTooltipData {
        String title = "";
        Component titleComponent = Component.empty();
        int titleWidth = 0;
        List<FormattedCharSequence> descLines = List.of();
        int descMaxWidth = 0;
        List<RewardRenderData> rewards = List.of();
        int textMaxWidth = 0;
        int targetW = 0;
        int targetH = 0;
    }

    private record NodeData(String id, int x, int y, boolean completed, boolean active, String displayName,
                            int displayNameWidth, PhaseTooltipData tooltipData) {
    }
}