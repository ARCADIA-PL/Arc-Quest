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

import java.util.HashMap;
import java.util.Map;

/**
 * 纯 2D 任务标记渲染：
 * 采用清澈透亮的机能风调色，调度独立的棱形和光标渲染器。
 */
public class MarkerHudRenderer {

    private static final float EDGE_PADDING = 30f;
    private static final float OFFSCREEN_INSET = 25f;

    private static final float POSITION_SMOOTH_ONSCREEN = 0.24f;
    private static final float POSITION_SMOOTH_OFFSCREEN = 0.14f;
    private static final int OFFSCREEN_ANGLE_SECTORS = 8;

    private static final long OFFSCREEN_ENTER_DELAY_MS = 70L;
    private static final long OFFSCREEN_EXIT_DELAY_MS = 110L;

    private final Map<String, MarkerVisualState> stateMap = new HashMap<>();
    private long startMillis = System.currentTimeMillis();

    private static class MarkerVisualState {
        float x;
        float y;
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

            MarkerVisualState st = stateMap.computeIfAbsent(marker.getId(), k -> new MarkerVisualState());

            if (!st.initialized) {
                st.x = proj.x;
                st.y = proj.y;
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

                if (st.offscreenStable) {
                    st.angle = snapAngleToSectors(normalizeAngle(proj.edgeAngle), OFFSCREEN_ANGLE_SECTORS);
                } else {
                    st.angle = 0f;
                }
            }

            int color = normalizeColor(marker.getColorARGB());

            if (!st.offscreenStable) {
                renderOnScreenMarker(gui, font, marker, st.x, st.y, color, dist, time);
            } else if (marker.isAllowOffscreenArrow()) {
                float[] anchor = insetFromEdge(st.x, st.y, cx, cy, OFFSCREEN_INSET);
                renderOffscreenMarker(gui, font, marker, anchor[0], anchor[1], color, dist, st.angle);
            }
        }
    }

    private void renderOnScreenMarker(GuiGraphics gui, Font font,
                                      QuestMarkerData marker,
                                      float x, float y,
                                      int color, double dist, float time) {
        int alphaInt = (color >> 24) & 0xFF;
        int accentColor = withAlpha(color, alphaInt);

        gui.pose().pushPose();
        gui.pose().translate((int) x, (int) y, 0);

        // 呼吸计算 (0.0 -> 1.0)
        float breath = (float) (Math.sin(time * 3.5f) * 0.5 + 0.5);

        // 绘制透亮机能菱形
        MarkerRhombusRenderer.draw(gui, accentColor, breath);

        // 绘制文本
        String name = marker.getLabel();
        int nameW = font.width(name);
        int textX = -nameW / 2;
        int textY = -18;

        drawTextWithBlackOutline(gui, font, name, textX, textY, accentColor, alphaInt);

        if (marker.isShowDistance()) {
            String distText = String.format("%.0fm", dist);
            int distW = font.width(distText);
            drawTextWithBlackOutline(gui, font, distText, -distW / 2, 12, accentColor, alphaInt);
        }

        gui.pose().popPose();
    }

    private void renderOffscreenMarker(GuiGraphics gui, Font font,
                                       QuestMarkerData marker,
                                       float x, float y,
                                       int color, double dist, float angle) {
        int alphaInt = (color >> 24) & 0xFF;
        int accentColor = withAlpha(color, alphaInt);

        gui.pose().pushPose();
        gui.pose().translate((int) x, (int) y, 0);

        gui.pose().pushPose();
        float rotDeg = (float) Math.toDegrees(angle) + 90f;
        gui.pose().mulPose(Axis.ZP.rotationDegrees(rotDeg));

        // 绘制凌厉矢量光标
        MarkerPointerRenderer.draw(gui, accentColor);

        gui.pose().popPose();

        String distText = String.format("%.0fm", dist);
        int distW = font.width(distText);
        float textOffset = 16f;

        float txRel = -(float) Math.cos(angle) * textOffset;
        float tyRel = -(float) Math.sin(angle) * textOffset;

        int drawX = (int) (txRel - distW / 2.0f);
        int drawY = (int) (tyRel - font.lineHeight / 2.0f);

        drawTextWithBlackOutline(gui, font, distText, drawX, drawY, accentColor, alphaInt);

        gui.pose().popPose();
    }

    /**
     * 绘制带纯黑半透明四向描边的文本（保证透亮背景下的可读性，不再使用突兀的色块底）
     */
    private void drawTextWithBlackOutline(GuiGraphics gui, Font font, String text, int x, int y, int textColor, int alpha) {
        // 描边保持和主题相同的透明度，保证融合感
        int outlineColor = (alpha << 24) | 0x000000;

        gui.drawString(font, text, x - 1, y, outlineColor, false);
        gui.drawString(font, text, x + 1, y, outlineColor, false);
        gui.drawString(font, text, x, y - 1, outlineColor, false);
        gui.drawString(font, text, x, y + 1, outlineColor, false);
        gui.drawString(font, text, x, y, textColor, false);
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

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static float normalizeAngle(float a) {
        while (a > Math.PI) a -= (float) (Math.PI * 2.0);
        while (a < -Math.PI) a += (float) (Math.PI * 2.0);
        return a;
    }

    private static float snapAngleToSectors(float angle, int sectors) {
        float step = (float) (Math.PI * 2.0 / sectors);
        return Math.round(angle / step) * step;
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