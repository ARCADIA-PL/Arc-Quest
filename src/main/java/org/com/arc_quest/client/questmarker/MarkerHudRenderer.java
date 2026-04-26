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

public class MarkerHudRenderer {

    private static final float EDGE_PADDING = 30f;
    private static final float OFFSCREEN_INSET = 25f;

    private static final float POSITION_SMOOTH_ONSCREEN = 0.24f;
    private static final float POSITION_SMOOTH_OFFSCREEN = 0.14f;

    private static final float ANIMATION_SPEED = 6.66f;

    // 【全新调整】形态切换的动画速度，数字越大越“迅速利落” (原为 2.5)
    private static final float TIER_TRANSITION_SPEED = 18.0f;

    private static final int OFFSCREEN_ANGLE_SECTORS = 8;

    private static final long OFFSCREEN_ENTER_DELAY_MS = 60L;
    private static final long OFFSCREEN_EXIT_DELAY_MS = 90L;

    private final Map<String, MarkerVisualState> stateMap = new HashMap<>();
    private long lastTimeMs = System.currentTimeMillis();

    // ==========================================
    // 距离形态配置 API (供外部调用修改)
    // ==========================================
    private static double nearDistanceThreshold = 25.0;
    private static double farDistanceThreshold = 50.0;

    public static void setDistanceThresholds(double near, double far) {
        nearDistanceThreshold = near;
        farDistanceThreshold = Math.max(near, far);
    }

    public static double getNearDistanceThreshold() { return nearDistanceThreshold; }
    public static double getFarDistanceThreshold() { return farDistanceThreshold; }

    public enum DistanceTier {
        NEAR(0.0f),    // 近距离：完全展开
        MEDIUM(1.0f),  // 中距离：部分收缩
        FAR(2.0f);     // 远距离：极简折叠

        private final float targetValue;

        DistanceTier(float targetValue) {
            this.targetValue = targetValue;
        }

        public float getTargetValue() { return targetValue; }

        public static DistanceTier getTierForDistance(double distance) {
            if (distance < nearDistanceThreshold) return NEAR;
            if (distance < farDistanceThreshold) return MEDIUM;
            return FAR;
        }
    }

    private static class MarkerVisualState {
        float x;
        float y;
        float angle;
        boolean offscreenStable;
        long switchTs;
        float transitionProgress;
        float distanceTier; // 当前距离形态插值 (0.0~2.0)
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
        float dt = (now - lastTimeMs) / 1000.0f;
        if (dt > 0.1f) dt = 0.1f;
        lastTimeMs = now;
        float time = now / 1000.0f;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        float cx = sw * 0.5f;
        float cy = sh * 0.5f;

        stateMap.keySet().removeIf(id -> !QuestMarkerManager.INSTANCE.has(id));
        String currentDim = player.level().dimension().location().toString();

        for (QuestMarkerData marker : QuestMarkerManager.INSTANCE.all()) {
            if (!marker.isActive()) continue;
            if (!currentDim.equals(marker.getDimension())) continue;

            MarkerProjection.ScreenResult proj = MarkerProjection.project(
                    marker.getWorldX(), marker.getWorldY(), marker.getWorldZ(), EDGE_PADDING);

            double dist = marker.distanceTo(px, py, pz);
            MarkerVisualState st = stateMap.computeIfAbsent(marker.getId(), k -> new MarkerVisualState());

            // 计算该目标的理想形态目标值
            float targetTier = DistanceTier.getTierForDistance(dist).getTargetValue();

            if (!st.initialized) {
                st.x = proj.x;
                st.y = proj.y;
                st.angle = normalizeAngle(proj.edgeAngle);
                st.offscreenStable = !proj.onScreen;
                st.transitionProgress = st.offscreenStable ? 1.0f : 0.0f;
                st.distanceTier = targetTier;
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

                if (st.offscreenStable) {
                    st.transitionProgress = Math.min(1.0f, st.transitionProgress + dt * ANIMATION_SPEED);
                } else {
                    st.transitionProgress = Math.max(0.0f, st.transitionProgress - dt * ANIMATION_SPEED);
                }

                // 【核心修改】计算多形态快速折叠插值，引入 Math.min 防止低帧率时越界闪烁
                float tierLerpFactor = Math.min(1.0f, dt * TIER_TRANSITION_SPEED);
                st.distanceTier = lerp(st.distanceTier, targetTier, tierLerpFactor);

                float[] anchor = insetFromEdge(proj.x, proj.y, cx, cy, OFFSCREEN_INSET);
                float blendedTargetX = lerp(proj.x, anchor[0], st.transitionProgress);
                float blendedTargetY = lerp(proj.y, anchor[1], st.transitionProgress);

                float pSmooth = st.offscreenStable ? POSITION_SMOOTH_OFFSCREEN : POSITION_SMOOTH_ONSCREEN;
                st.x = lerp(st.x, blendedTargetX, pSmooth);
                st.y = lerp(st.y, blendedTargetY, pSmooth);

                float targetAngle = st.offscreenStable ? snapAngleToSectors(normalizeAngle(proj.edgeAngle), OFFSCREEN_ANGLE_SECTORS) : 0f;
                st.angle = lerpAngle(st.angle, targetAngle, dt * 15f);
            }

            int color = normalizeColor(marker.getColorARGB());

            // 保留你的原始光源配置
            float baseLightX = 0.15f;
            float baseLightY = -1.0f;
            float deflectX = (cx - st.x) / cx * 0.35f;
            float deflectY = (cy - st.y) / cy * 0.35f;

            float lx = baseLightX + deflectX;
            float ly = baseLightY + deflectY;
            float lightLen = (float)Math.sqrt(lx * lx + ly * ly);
            lx = lightLen > 0 ? lx / lightLen : 0;
            ly = lightLen > 0 ? ly / lightLen : -1;

            if (st.transitionProgress < 0.99f) {
                renderOnScreenMarker(gui, font, marker, st.x, st.y, color, dist, time, st.transitionProgress, lx, ly, st.distanceTier);
            }
            if (st.transitionProgress > 0.01f && marker.isAllowOffscreenArrow()) {
                renderOffscreenMarker(gui, font, marker, st.x, st.y, color, dist, st.angle, st.transitionProgress, lx, ly, st.distanceTier);
            }
        }
    }

    private void renderOnScreenMarker(GuiGraphics gui, Font font, QuestMarkerData marker, float x, float y, int color, double dist, float time, float progress, float lightX, float lightY, float tier) {
        float alphaFade = 1.0f - progress;
        int originalAlpha = (color >> 24) & 0xFF;
        int currentAlpha = (int) (originalAlpha * alphaFade);
        if (currentAlpha <= 5) return;
        int accentColor = withAlpha(color, currentAlpha);

        gui.pose().pushPose();
        gui.pose().translate(x, y, 0);

        float ease = progress * progress;
        float scaleX = 1.0f - ease;
        float scaleY = 1.0f + ease * 0.8f;
        gui.pose().scale(scaleX, scaleY, 1.0f);

        float breath = (float) (Math.sin(time * 3.5f) * 0.5 + 0.5);
        MarkerRhombusRenderer.draw(gui, accentColor, breath, lightX, lightY, tier);

        String name = marker.getLabel();
        drawTextWithBlackOutline(gui, font, name, -font.width(name) / 2, -18, accentColor, currentAlpha);
        if (marker.isShowDistance()) {
            String distText = String.format("%.0fm", dist);
            drawTextWithBlackOutline(gui, font, distText, -font.width(distText) / 2, 12, accentColor, currentAlpha);
        }

        gui.pose().popPose();
    }

    private void renderOffscreenMarker(GuiGraphics gui, Font font, QuestMarkerData marker, float x, float y, int color, double dist, float angle, float progress, float lightX, float lightY, float tier) {
        int originalAlpha = (color >> 24) & 0xFF;
        int currentAlpha = (int) (originalAlpha * progress);
        if (currentAlpha <= 5) return;
        int accentColor = withAlpha(color, currentAlpha);

        gui.pose().pushPose();
        gui.pose().translate(x, y, 0);
        gui.pose().pushPose();

        float ease = 1.0f - (1.0f - progress) * (1.0f - progress);
        float scaleX = ease;
        float scaleY = 1.0f + (1.0f - ease) * 0.8f;
        gui.pose().scale(scaleX, scaleY, 1.0f);

        float rotRad = angle + (float)Math.PI / 2f;
        float rotDeg = (float) Math.toDegrees(rotRad);
        gui.pose().mulPose(Axis.ZP.rotationDegrees(rotDeg));

        float cosA = (float)Math.cos(-rotRad);
        float sinA = (float)Math.sin(-rotRad);
        float localLightX = lightX * cosA - lightY * sinA;
        float localLightY = lightX * sinA + lightY * cosA;

        MarkerPointerRenderer.draw(gui, accentColor, localLightX, localLightY, tier);
        gui.pose().popPose();

        String distText = String.format("%.0fm", dist);
        int distW = font.width(distText);
        float textOffset = 16f;

        float txRel = -(float) Math.cos(angle) * textOffset;
        float tyRel = -(float) Math.sin(angle) * textOffset;
        drawTextWithBlackOutline(gui, font, distText, (int) (txRel - distW / 2.0f), (int) (tyRel - font.lineHeight / 2.0f), accentColor, currentAlpha);

        gui.pose().popPose();
    }

    private void drawTextWithBlackOutline(GuiGraphics gui, Font font, String text, int x, int y, int textColor, int alpha) {
        if (alpha <= 5) return;
        int outlineColor = (alpha << 24) | 0x000000;
        int mainColor = (alpha << 24) | (textColor & 0xFFFFFF);

        gui.drawString(font, text, x - 1, y, outlineColor, false);
        gui.drawString(font, text, x + 1, y, outlineColor, false);
        gui.drawString(font, text, x, y - 1, outlineColor, false);
        gui.drawString(font, text, x, y + 1, outlineColor, false);
        gui.drawString(font, text, x, y, mainColor, false);
    }

    private static float[] insetFromEdge(float x, float y, float cx, float cy, float inset) {
        float dx = x - cx;
        float dy = y - cy;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0e-6f) return new float[]{x, y};
        return new float[]{x - (dx / len) * inset, y - (dy / len) * inset};
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static float lerpAngle(float from, float to, float t) {
        float diff = normalizeAngle(to - from);
        return from + diff * t;
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