package org.com.arc_quest.client.questmarker;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.com.arc_quest.questmarker.api.QuestMarkerData;
import org.com.arc_quest.questmarker.api.QuestMarkerType;

import java.util.HashMap;
import java.util.Map;

/**
 * 纯 2D 任务标记渲染（极简机能版）：
 * - 视野内：纯粹的呼吸菱形锁 (Holo-Rhombus)
 * - 视野外：微型锐利指针 (Minimal Pointer)
 */
public class MarkerHudRenderer {

    private static final float EDGE_PADDING = 30f;
    private static final float OFFSCREEN_INSET = 20f;

    private static final float POSITION_SMOOTH_ONSCREEN = 0.24f;
    private static final float POSITION_SMOOTH_OFFSCREEN = 0.14f;
    private static final float SCALE_SMOOTH = 0.20f;
    private static final float ANGLE_SMOOTH = 0.16f;
    private static final int OFFSCREEN_ANGLE_SECTORS = 8;

    private static final long OFFSCREEN_ENTER_DELAY_MS = 70L;
    private static final long OFFSCREEN_EXIT_DELAY_MS = 110L;

    private final Map<String, MarkerVisualState> stateMap = new HashMap<>();
    private long startMillis = System.currentTimeMillis();

    private static class MarkerVisualState {
        float x;
        float y;
        float scale;
        float angle;
        boolean offscreenStable;
        long switchTs;
        boolean initialized;
    }

    @SubscribeEvent
    public void onRenderHud(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.options.hideGui) return;

        GuiGraphics gui = event.getGuiGraphics();
        Player player = mc.player;
        Font font = mc.font;

        double px = player.getX();
        double py = player.getEyeY();
        double pz = player.getZ();

        long now = System.currentTimeMillis();
        float time = (now - startMillis) / 1000.0f;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        float cx = sw * 0.5f;
        float cy = sh * 0.5f;

        stateMap.keySet().removeIf(id -> !QuestMarkerManager.INSTANCE.has(id));

        for (QuestMarkerData marker : QuestMarkerManager.INSTANCE.all()) {
            if (!marker.isActive()) continue;

            MarkerProjection.ScreenResult proj = MarkerProjection.project(
                    marker.getWorldX(), marker.getWorldY(), marker.getWorldZ(), EDGE_PADDING);

            double dist = marker.distanceTo(px, py, pz);
            float targetScale = distanceToScale(dist);

            MarkerVisualState st = stateMap.computeIfAbsent(marker.getId(), k -> new MarkerVisualState());

            if (!st.initialized) {
                st.x = proj.x;
                st.y = proj.y;
                st.scale = targetScale;
                st.angle = normalizeAngle(proj.edgeAngle);
                st.offscreenStable = !proj.onScreen;
                st.switchTs = now;
                st.initialized = true;
            } else {
                boolean targetOff = !proj.onScreen;
                if (targetOff != st.offscreenStable) {
                    long need = targetOff ? OFFSCREEN_ENTER_DELAY_MS : OFFSCREEN_EXIT_DELAY_MS;
                    if (now - st.switchTs >= need) {
                        st.offscreenStable = targetOff;
                        st.switchTs = now;
                    }
                } else {
                    st.switchTs = now;
                }

                float pSmooth = st.offscreenStable ? POSITION_SMOOTH_OFFSCREEN : POSITION_SMOOTH_ONSCREEN;
                st.x = lerp(st.x, proj.x, pSmooth);
                st.y = lerp(st.y, proj.y, pSmooth);
                st.scale = lerp(st.scale, targetScale, SCALE_SMOOTH);

                float targetAngle = normalizeAngle(proj.edgeAngle);
                if (st.offscreenStable) {
                    targetAngle = snapAngleToSectors(targetAngle, OFFSCREEN_ANGLE_SECTORS);
                }
                st.angle = lerpAngle(st.angle, targetAngle, ANGLE_SMOOTH);
            }

            int color = normalizeColor(marker.getColorARGB());
            int textColor = withAlpha(0x00FFFFFF, (int) (220 * distanceToAlpha(dist)));

            if (!st.offscreenStable) {
                renderOnScreenMarker(gui, font, marker, st.x, st.y, st.scale, color, textColor, dist, time);
            } else if (marker.isAllowOffscreenArrow()) {
                float[] anchor = insetFromEdge(st.x, st.y, cx, cy, OFFSCREEN_INSET);
                // 移除了 behind 的单独文本处理，采用更纯粹的视觉引导
                renderOffscreenMarker(gui, font, marker, anchor[0], anchor[1], st.scale, color, textColor, dist, st.angle);
            }
        }
    }

    private void renderOnScreenMarker(GuiGraphics gui, Font font,
                                      QuestMarkerData marker,
                                      float x, float y, float scale,
                                      int color, int textColor,
                                      double dist, float time) {
        int alphaInt = (color >> 24) & 0xFF;
        float alphaFloat = alphaInt / 255.0f;
        int brightColor = withAlpha(0xFFFFFF, alphaInt);
        int darkBgColor = withAlpha(0x000000, (int)(150 * alphaFloat));

        gui.pose().pushPose();
        gui.pose().translate(x, y, 0);

        // 缩放层：只对图形应用缩放
        gui.pose().pushPose();
        gui.pose().scale(scale, scale, 1f);

        // 核心菱形锁定框 (旋转45度 + 生命周期呼吸)
        // 去除了繁杂的对准标线，只保留最纯粹的居中图形
        gui.pose().pushPose();
        gui.pose().mulPose(Axis.ZP.rotationDegrees(45f));
        float breath = (float) (Math.sin(time * 3.5f) * 0.5 + 0.5);
        int coreAlpha = (int) ((140 + 115 * breath) * alphaFloat);
        int coreColor = withAlpha(color, coreAlpha);
        MarkerRenderUtil.drawRhombus(gui, coreColor, brightColor, darkBgColor);
        gui.pose().popPose();

        gui.pose().popPose(); // 结束图形缩放

        // 文本绘制 (因为图形变简洁了，文字向中心靠拢)
        String name = marker.getLabel();
        int nameW = font.width(name);
        gui.drawString(font, name, (int) (-nameW / 2.0f), (int) (-18.0f * scale), textColor, true);

        // 距离展示
        if (marker.isShowDistance()) {
            String distText = String.format("[ %.0fm ]", dist);
            float textScale = 0.8f;
            int distW = font.width(distText);

            gui.pose().pushPose();
            gui.pose().translate(0, 14.0f * scale, 0); // 从 16 收紧到 14
            gui.pose().scale(textScale, textScale, 1f);
            gui.fill(-distW / 2 - 2, -1, distW / 2 + 2, font.lineHeight - 1, darkBgColor);
            gui.drawString(font, distText, -distW / 2, 0, brightColor, false);
            gui.pose().popPose();
        }

        gui.pose().popPose();
    }

    private void renderOffscreenMarker(GuiGraphics gui, Font font,
                                       QuestMarkerData marker,
                                       float x, float y, float scale,
                                       int color, int textColor,
                                       double dist, float angle) {
        int alphaInt = (color >> 24) & 0xFF;
        int brightColor = withAlpha(0xFFFFFF, alphaInt);
        int darkBgColor = withAlpha(0x000000, (int)(150 * (alphaInt / 255f)));

        gui.pose().pushPose();
        gui.pose().translate(x, y, 0);

        // 图形层旋转与缩放 (去除了不必要的放大倍率，保持克制)
        gui.pose().pushPose();
        gui.pose().scale(scale, scale, 1f);
        float rotDeg = (float) Math.toDegrees(angle) + 90f;
        gui.pose().mulPose(Axis.ZP.rotationDegrees(rotDeg));

        // 绘制微型锐利指针
        MarkerRenderUtil.drawMinimalPointer(gui, color, brightColor, darkBgColor);
        gui.pose().popPose();

        // 距离文本跟随 (因为箭头极度缩小，文本也需要贴得更近)
        String distText = String.format("%.0fm", dist);
        int distW = font.width(distText);
        float textOffset = 15f * scale; // 从 22 缩小到 15

        float txRel = -(float) Math.cos(angle) * textOffset;
        float tyRel = -(float) Math.sin(angle) * textOffset;
        gui.drawString(font, distText, (int) (txRel - distW / 2.0f), (int) (tyRel - font.lineHeight / 2.0f), textColor, true);

        gui.pose().popPose();
    }

    private static float[] insetFromEdge(float x, float y, float cx, float cy, float inset) {
        float dx = x - cx;
        float dy = y - cy;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0e-6f) return new float[]{x, y};
        float ux = dx / len;
        float uy = dy / len;
        return new float[]{x - ux * inset, y - uy * inset};
    }

    private static float distanceToScale(double dist) {
        return (float) clamp(1.45 - (dist / 120.0), 0.62, 1.28);
    }

    private static float distanceToAlpha(double dist) {
        return (float) clamp(1.15 - (dist / 180.0), 0.38, 1.0);
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static float normalizeAngle(float a) {
        while (a > Math.PI) a -= (float) (Math.PI * 2.0);
        while (a < -Math.PI) a += (float) (Math.PI * 2.0);
        return a;
    }

    private static float lerpAngle(float from, float to, float t) {
        float delta = normalizeAngle(to - from);
        return normalizeAngle(from + delta * t);
    }

    private static float snapAngleToSectors(float angle, int sectors) {
        float step = (float) (Math.PI * 2.0 / sectors);
        return Math.round(angle / step) * step;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int withAlpha(int rgb, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    private static int normalizeColor(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        return alpha == 0 ? 0xFFFFFFFF : argb;
    }
}