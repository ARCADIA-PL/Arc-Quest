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
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
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

    // 绝美的 21:9 终极极客比例！
    private static final int PANEL_W = 630;
    private static final int PANEL_H = 270;

    private static final float ENTER_TIME = 0.7f;
    private static final float EXIT_TIME = 0.5f;

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

    private record NodeData(String id, int x, int y, boolean completed, boolean active) {}
    private static final List<NodeData> renderNodes = new ArrayList<>();
    private static final Map<String, NodeData> nodeMap = new HashMap<>();

    private static List<Component> hoveredCustomTooltip = null;
    private static ItemStack hoveredRewardStack = ItemStack.EMPTY;

    private QuestHistoryPanel() {}

    public static void trigger(String qid) {
        questId = qid;
        themeColor = ClientQuestCache.INSTANCE.getQuestThemeColor(qid, 0x5AD7FF);
        active = true;
        closing = false;
        enterTimer = 0f;
        exitTimer = 0f;
        lastRenderMs = System.currentTimeMillis();

        zoom = 1.0f; panX = 0f; panY = 0f;
        targetZoom = 1.0f; targetPanX = 0f; targetPanY = 0f;
        panning = false;
        hoveredCustomTooltip = null;
        hoveredRewardStack = ItemStack.EMPTY;
        hoveredRewardStack = ItemStack.EMPTY;

        buildGraphData();
        fitCameraToGraph(PANEL_W - 8, PANEL_H - 32);
    }

    public static boolean isActive() { return active; }

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
        if (!active || closing || button != 0) return false;

        float scaledW = PANEL_W * currentScale;
        float scaledH = PANEL_H * currentScale;

        if (mx < currentDrawX || mx > currentDrawX + scaledW || my < currentDrawY || my > currentDrawY + scaledH) {
            close();
            return true;
        }

        float lx = (float) ((mx - currentDrawX) / currentScale);
        float ly = (float) ((my - currentDrawY) / currentScale);

        int treeX = 4, treeY = 28, treeW = PANEL_W - 8, treeH = PANEL_H - 32;
        boolean inBounds = lx >= treeX && lx <= treeX + treeW && ly >= treeY && ly <= treeY + treeH;

        if (inBounds) {
            panning = true;
            lastDragX = lx;
            lastDragY = ly;
        }
        return true;
    }

    public static boolean mouseDragged(double mx, double my) {
        if (!active || closing) return false;
        float lx = (float) ((mx - currentDrawX) / currentScale);
        float ly = (float) ((my - currentDrawY) / currentScale);

        if (panning) {
            if (GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
                panning = false;
                return true;
            }
            targetPanX += (lx - lastDragX);
            targetPanY += (ly - lastDragY);
            lastDragX = lx;
            lastDragY = ly;
            return true;
        }
        return false;
    }

    public static boolean mouseReleased(int button) {
        if (button == 0) panning = false;
        return active;
    }

    public static boolean mouseScrolled(double mx, double my, double delta) {
        if (!active || closing) return false;
        float lx = (float) ((mx - currentDrawX) / currentScale);
        float ly = (float) ((my - currentDrawY) / currentScale);

        int treeX = 4, treeY = 28, treeW = PANEL_W - 8, treeH = PANEL_H - 32;
        boolean inBounds = lx >= treeX && lx <= treeX + treeW && ly >= treeY && ly <= treeY + treeH;

        if (inBounds) {
            float zoomSpeed = 0.15f;
            float oldZoom = targetZoom;

            targetZoom = Math.max(0.3f, Math.min(2.5f, targetZoom + (float) delta * zoomSpeed));

            double mouseWorldX = (lx - treeX - targetPanX) / oldZoom;
            double mouseWorldY = (ly - treeY - targetPanY) / oldZoom;

            targetPanX = (float) (lx - treeX - mouseWorldX * targetZoom);
            targetPanY = (float) (ly - treeY - mouseWorldY * targetZoom);
            return true;
        }
        return true;
    }

    public static void render(GuiGraphics g, int mx, int my, float partialTick) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        if (mc.screen instanceof QuestJournalScreen qjs) {
            screenW = qjs.getScaledWidth();
            screenH = qjs.getScaledHeight();
        }

        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastRenderMs) / 1000f, 0.1f);
        lastRenderMs = now;

        // ==========================================
        // 极致视网膜级自适应缩放算法！(双轴约束)
        // 保证在任何窗口比例下，这个 21:9 的面板都不会溢出边缘！
        // ==========================================
        float targetScaleW = (screenW * 0.90f) / (float) PANEL_W;
        float targetScaleH = (screenH * 0.65f) / (float) PANEL_H;
        float finalScale = Math.min(targetScaleW, targetScaleH); // 取最小限制，绝对不超框！

        float baseX = (screenW / 2f) - ((PANEL_W * finalScale) / 2f);
        float baseY = (screenH / 2f) - ((PANEL_H * finalScale) / 2f);
        float scaleAnim = finalScale;
        float currentX = baseX;
        float currentY = baseY;
        float alphaF = 1.0f;
        float revealProgress = 1.0f;
        float wipeProgress = 0.0f;
        float actualFlyDist = 4.0f * finalScale;

        if (closing) {
            exitTimer += dt;
            if (exitTimer >= EXIT_TIME) { active = false; closing = false; return; }
            float t = Math.min(1.0f, exitTimer / EXIT_TIME);
            float easeIn = (float) Math.pow(t, 4.0);
            wipeProgress = easeIn;
            currentX = baseX - (easeIn * actualFlyDist * 1.5f);
            alphaF = 1.0f - (float) Math.pow(t, 8.0);
        } else {
            enterTimer = Math.min(ENTER_TIME, enterTimer + dt);
            float t = Math.min(1.0f, enterTimer / ENTER_TIME);
            float easeOut = (float) (1.0 - Math.pow(1.0 - t, 5));
            revealProgress = easeOut;
            alphaF = easeOut;
            scaleAnim = finalScale * (1.10f - 0.10f * easeOut);
            currentX = baseX - (1.0f - easeOut) * actualFlyDist * 2f;
        }

        if (revealProgress <= 0.001f || wipeProgress >= 0.999f) return;

        float drawWidth = PANEL_W * scaleAnim;
        float drawHeight = PANEL_H * scaleAnim;
        float scaleOffsetW = (drawWidth - PANEL_W * finalScale) / 2f;
        float scaleOffsetH = (drawHeight - PANEL_H * finalScale) / 2f;
        currentDrawX = currentX - scaleOffsetW;
        currentDrawY = currentY - scaleOffsetH;
        currentScale = scaleAnim;

        int scX1 = (int) (currentDrawX - 10);
        int scX2 = (int) (currentDrawX + drawWidth + 10);
        if (closing) { scX2 = (int) (currentDrawX + drawWidth * (1.0f - wipeProgress)); }
        else if (enterTimer < ENTER_TIME) { scX2 = (int) (currentDrawX + drawWidth * revealProgress); }

        hoveredCustomTooltip = null;
        hoveredRewardStack = ItemStack.EMPTY;

        g.pose().pushPose();
        g.pose().translate(0, 0, 4500);

        // ==========================================
        // 极致通透黑幕！暗度暴降至 35！
        // ==========================================
        g.fill(-1000, -1000, screenW + 1000, screenH + 1000, HudAnimUtil.withAlpha(0x000000, (int) (35 * alphaF)));

        if (mc.screen instanceof QuestJournalScreen qjs) {
            qjs.enableScissor(g, scX1, (int) (currentDrawY - 10), scX2, (int) (currentDrawY + drawHeight + 10));
        } else {
            g.enableScissor(scX1, (int) (currentDrawY - 10), scX2, (int) (currentDrawY + drawHeight + 10));
        }

        g.pose().pushPose();
        g.pose().translate(currentDrawX, currentDrawY, 0);
        g.pose().scale(scaleAnim, scaleAnim, 1f);

        int alphaInt = Math.max(0, Math.min(255, (int) (255 * alphaF)));
        renderPanel(g, mc.font, alphaInt, alphaF, dt, mx, my);

        g.pose().popPose();
        if (mc.screen instanceof QuestJournalScreen qjs) g.disableScissor();

        g.pose().popPose();

        if (!closing && mc.screen instanceof QuestJournalScreen qjs) {
            if (hoveredCustomTooltip != null && !hoveredCustomTooltip.isEmpty()) {
                qjs.setHoveredCustomTooltip(hoveredCustomTooltip);
            }
            if (!hoveredRewardStack.isEmpty()) {
                qjs.setHoveredRewardTooltip(hoveredRewardStack);
            }
        }
    }

    private static void renderPanel(GuiGraphics g, Font font, int alpha, float alphaF, float dt, int mx, int my) {
        int PW = PANEL_W, PH = PANEL_H;
        float lx = (float) ((mx - currentDrawX) / currentScale);
        float ly = (float) ((my - currentDrawY) / currentScale);

        int cyberEdgeWidth = 3;
        int bgAlpha = (int) (0xDD * alphaF);
        int borderAlpha = (int) (0x66 * alphaF);
        int borderRgb = 0xCCCCCC;

        g.fill(cyberEdgeWidth, 0, PW, PH, HudAnimUtil.withAlpha(0x05060A, bgAlpha));
        g.fill(cyberEdgeWidth, 0, PW, 1, HudAnimUtil.withAlpha(borderRgb, borderAlpha));
        g.fill(cyberEdgeWidth, PH - 1, PW, PH, HudAnimUtil.withAlpha(borderRgb, borderAlpha));
        g.fill(PW - 1, 0, PW, PH, HudAnimUtil.withAlpha(borderRgb, borderAlpha));
        HudRenderUtil.drawCyberneticEdge(g, 0, 0, PH, themeColor, alpha);

        int topBarH = 22;
        g.pose().pushPose();
        g.pose().scale(0.85f, 0.85f, 1f);
        g.drawString(font, "SYS.ARC_QUEST // TOPOLOGY MAP [ 21:9 ULTRAWIDE ]", 16, 6, HudAnimUtil.withAlpha(0x667788, alpha), false);
        g.pose().popPose();
        g.fill(10, topBarH - 1, PW - 10, topBarH, HudAnimUtil.withAlpha(borderRgb, borderAlpha));

        for (int i = 15; i < PW; i += 15) g.fill(i, topBarH, i + 1, PH - 1, HudAnimUtil.withAlpha(0xFFFFFF, (int) (4 * alphaF)));
        for (int i = topBarH + 15; i < PH; i += 15) g.fill(1, i, PW - 1, i + 1, HudAnimUtil.withAlpha(0xFFFFFF, (int) (4 * alphaF)));

        zoom += (targetZoom - zoom) * Math.min(1f, dt * 15f);
        panX += (targetPanX - panX) * Math.min(1f, dt * 15f);
        panY += (targetPanY - panY) * Math.min(1f, dt * 15f);

        int treeX = 4, treeY = topBarH + 2, treeW = PW - 8, treeH = PH - topBarH - 6;
        boolean inBounds = lx >= treeX && lx <= treeX + treeW && ly >= treeY && ly <= treeY + treeH;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof QuestJournalScreen qjs) {
            float absX = currentDrawX + treeX * currentScale;
            float absY = currentDrawY + treeY * currentScale;
            float absW = treeW * currentScale;
            float absH = treeH * currentScale;
            qjs.enableScissor(g, (int)absX, (int)absY, (int)(absX + absW), (int)(absY + absH));
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
                    if (target == null) continue;
                    drawOrthogonalLine(g, node.x, node.y, target.x, target.y, node.completed, alphaF, themeColor);
                }
            }

            String hoveredPhaseId = null;

            for (NodeData node : renderNodes) {
                int r = 6;
                int nodeColor = node.completed ? 0x66FF88 : (node.active ? themeColor : 0x555555);

                double nodeScreenX = treeX + panX + node.x * zoom;
                double nodeScreenY = treeY + panY + node.y * zoom;
                double hitRadius = r * 2.5 * zoom;

                boolean isHovered = inBounds && Math.abs(lx - nodeScreenX) <= hitRadius && Math.abs(ly - nodeScreenY) <= hitRadius;
                if (isHovered) hoveredPhaseId = node.id;

                int glowA = (int) ((node.active ? 150 + 50 * Math.sin(Util.getMillis() / 200.0) : (isHovered ? 200 : 0)) * alphaF);

                g.pose().pushPose();
                g.pose().translate(node.x, node.y, 0);
                g.pose().mulPose(Axis.ZP.rotationDegrees(45));

                if (glowA > 0) g.fill(-r - 2, -r - 2, r + 2, r + 2, HudAnimUtil.withAlpha(nodeColor, glowA));
                g.fill(-r, -r, r, r, HudAnimUtil.withAlpha(0x222222, alpha));
                g.fill(-r + 1, -r + 1, r - 1, r - 1, HudAnimUtil.withAlpha(nodeColor, (int) (220 * alphaF)));
                g.pose().popPose();

                String phaseName = ClientQuestCache.INSTANCE.getPhaseDisplayName(questId, node.id);
                int tw = font.width(phaseName);
                int textAlpha = (int) ((isHovered || node.active ? 255 : 150) * alphaF);

                g.pose().pushPose();
                g.pose().translate(node.x, node.y + 12, 0);
                g.pose().scale(0.6f, 0.6f, 1f);
                g.drawString(font, phaseName, -tw / 2, 0, HudAnimUtil.withAlpha(nodeColor, textAlpha), true);
                g.pose().popPose();
            }

            if (hoveredPhaseId != null) buildPhaseTooltip(def.getPhase(hoveredPhaseId));
        }

        g.pose().popPose();
        if (mc.screen instanceof QuestJournalScreen qjs) g.disableScissor();
    }

    private static void drawOrthogonalLine(GuiGraphics g, int x1, int y1, int x2, int y2, boolean isCompleted, float alphaF, int theme) {
        int color = isCompleted ? HudAnimUtil.withAlpha(0x66FF88, (int)(180 * alphaF)) : HudAnimUtil.withAlpha(0x555555, (int)(100 * alphaF));
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

        Set<String> phaseOrder = def.getPhaseIds();
        Set<String> completed = runtime.getCompletedPhaseIds();
        Set<String> active = runtime.getActivePhaseIds();

        Map<String, Integer> depth = new HashMap<>();
        for (String id : phaseOrder) depth.put(id, 0);
        for (String id : phaseOrder) {
            PhaseDefinition p = def.getPhase(id);
            if (p != null) {
                int curD = depth.getOrDefault(id, 0);
                for (PhaseTransition tr : p.getTransitions()) {
                    depth.put(tr.getTargetPhaseId(), Math.max(depth.getOrDefault(tr.getTargetPhaseId(), 0), curD + 1));
                }
            }
        }

        Map<Integer, List<String>> nodesByDepth = new HashMap<>();
        for (String pid : phaseOrder) {
            int d = depth.getOrDefault(pid, 0);
            nodesByDepth.computeIfAbsent(d, k -> new ArrayList<>()).add(pid);
        }

        final int dx = 60;
        final int dy = 40;

        for (Map.Entry<Integer, List<String>> entry : nodesByDepth.entrySet()) {
            int d = entry.getKey();
            List<String> nodesInLayer = entry.getValue();
            int totalInLayer = nodesInLayer.size();

            for (int i = 0; i < totalInLayer; i++) {
                String pid = nodesInLayer.get(i);
                int x = d * dx;
                int y = (int) ((i - (totalInLayer - 1) / 2.0f) * dy);

                boolean isCompleted = completed.contains(pid);
                boolean isActive = !isCompleted && active.contains(pid);

                NodeData nd = new NodeData(pid, x, y, isCompleted, isActive);
                renderNodes.add(nd);
                nodeMap.put(pid, nd);
            }
        }
    }

    private static void fitCameraToGraph(int viewW, int viewH) {
        if (renderNodes.isEmpty()) return;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;

        for (NodeData n : renderNodes) {
            if (n.x < minX) minX = n.x;
            if (n.x > maxX) maxX = n.x;
            if (n.y < minY) minY = n.y;
            if (n.y > maxY) maxY = n.y;
        }

        int treeW = Math.max(1, maxX - minX);
        int treeH = Math.max(1, maxY - minY);

        targetZoom = Math.min((float) viewW / (treeW + 60), (float) viewH / (treeH + 60));
        targetZoom = Math.max(0.4f, Math.min(1.5f, targetZoom));

        targetPanX = (viewW - treeW * targetZoom) / 2f - minX * targetZoom;
        targetPanY = (viewH - treeH * targetZoom) / 2f - minY * targetZoom;

        zoom = targetZoom; panX = targetPanX; panY = targetPanY;
    }

    private static void buildPhaseTooltip(PhaseDefinition phase) {
        if (phase == null) return;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(phase.getDisplayName().getString()).withStyle(Style.EMPTY.withColor(0xFFFFFF).withBold(true)));

        if (!phase.getDescription().getString().isEmpty()) {
            lines.add(Component.literal(phase.getDescription().getString()).withStyle(Style.EMPTY.withColor(0xAAAAAA)));
        }

        List<String> rewards = new ArrayList<>();
        for (IReward reward : phase.getPhaseRewards()) {
            if (reward instanceof ItemReward ir) {
                ItemStack st = new ItemStack(ir.getItem(), Math.min(64, ir.getCount()));
                rewards.add(st.getHoverName().getString() + (ir.getCount() > 1 ? " x" + ir.getCount() : ""));
            } else {
                rewards.add(reward.describe());
            }
        }

        if (!rewards.isEmpty()) {
            lines.add(Component.empty());
            lines.add(Component.literal("Rewards:").withStyle(Style.EMPTY.withColor(0xFFCC00)));
            for (String r : rewards) {
                lines.add(Component.literal("■ " + r).withStyle(Style.EMPTY.withColor(0xFFFFFF)));
            }
        }
        hoveredCustomTooltip = lines;
        for (IReward reward : phase.getPhaseRewards()) {
            if (reward instanceof ItemReward ir) {
                hoveredRewardStack = new ItemStack(ir.getItem(), Math.min(64, ir.getCount()));
                break;
            }
        }
    }
}