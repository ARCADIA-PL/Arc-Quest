// file_name: EmbeddedPonderScenePanel.java
package org.arcadia.arc_quest.client.hud.ponder;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.guide.GuideListScreen;
import org.arcadia.arc_quest.client.ponder.ArcQuestPonderSceneRegistry;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EmbeddedPonderScenePanel {

    private static final Vector3f DIFFUSE_0 = new Vector3f(-0.2F, 1.0F, 0.7F).normalize();
    private static final Vector3f DIFFUSE_1 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
    private static final Set<ResourceLocation> WARNED_MISSING_SCENES = new HashSet<>();

    @Nullable private EmbeddedPonderSceneHandle handle;
    @Nullable private ResourceLocation boundSceneId;
    private int themeColor = 0x4FC3F7;

    private final Map<String, Float> buttonHoverStates = new HashMap<>();
    private long lastRenderTime = 0;

    public boolean bind(ResourceLocation sceneId, int themeColor, boolean autoplay) {
        if (sceneId == null) { unbind(); return false; }
        if (isBoundTo(sceneId)) return handle != null && handle.isValid();
        unbind();
        this.themeColor = themeColor;
        this.boundSceneId = sceneId;

        if (!ArcQuestPonderSceneRegistry.hasScene(sceneId) || !PonderIndex.getSceneAccess().doScenesExistForId(sceneId)) {
            warnMissing(sceneId); return false;
        }
        List<PonderScene> scenes = PonderIndex.getSceneAccess().compile(sceneId);
        if (scenes == null || scenes.isEmpty()) { warnMissing(sceneId); return false; }

        handle = new EmbeddedPonderSceneHandle(sceneId, scenes, !autoplay, themeColor);
        buttonHoverStates.clear();
        return handle.isValid();
    }

    public void unbind() {
        if (handle != null) handle.release();
        handle = null;
        boundSceneId = null;
    }

    public boolean isBoundTo(ResourceLocation sceneId) {
        return boundSceneId != null && boundSceneId.equals(sceneId) && handle != null && handle.isValid();
    }

    public void tick() { if (handle != null) handle.tick(); }

    public void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick, int alpha) {
        if (handle == null || !handle.isValid()) {
            renderMissing(g, x, y, w, h, alpha); return;
        }

        long now = System.currentTimeMillis();
        if (lastRenderTime == 0) lastRenderTime = now;
        float dt = Math.min((now - lastRenderTime) / 1000f, 0.1f);
        lastRenderTime = now;

        Font font = Minecraft.getInstance().font;
        PonderScene scene = handle.currentScene();

        // 背景
        g.fill(x, y, x + w, y + h, HudAnimUtil.withAlpha(0x000000, (int)(alpha * 0.4f)));

        int sceneX = x;
        int sceneY = y;
        int sceneW = w;
        int sceneH = h - 16; // 留出底部进度条空间

        render3DScene(g, scene, sceneX, sceneY, sceneW, sceneH, partialTick, alpha);

        // ── 全息 UI 层 ──
        float lx = mouseX - x;
        float ly = mouseY - y;

        boolean canPrev = handle.getSceneIndex() > 0;
        boolean canNext = handle.getSceneIndex() < handle.getSceneCount() - 1;

        drawHologramButton(g, font, "replay", x + w - 48, y + 4, 20, 18, "↺", lx, ly, w - 48, 4, alpha, true, dt);
        drawHologramButton(g, font, "pause", x + w - 24, y + 4, 20, 18, handle.isPaused() ? "▶" : "‖", lx, ly, w - 24, 4, alpha, true, dt);
        drawHologramButton(g, font, "prev", x + 4, y + h / 2 - 15, 14, 30, "◄", lx, ly, 4, h / 2 - 15, alpha, canPrev, dt);
        drawHologramButton(g, font, "next", x + w - 18, y + h / 2 - 15, 14, 30, "►", lx, ly, w - 18, h / 2 - 15, alpha, canNext, dt);

        renderMinimalProgressBar(g, scene, x, y + h, w, alpha);
    }

    private void renderMinimalProgressBar(GuiGraphics g, PonderScene scene, int x, int bottomY, int w, int alpha) {
        int pad = 12;
        int barY = bottomY - 8;
        int barX = x + pad;
        int barW = w - pad * 2;

        float progress = scene.getTotalTime() > 0 ? (float) scene.getCurrentTime() / scene.getTotalTime() : 0f;

        g.fill(barX, barY, barX + barW, barY + 1, HudAnimUtil.withAlpha(0x334455, (int) (alpha * 0.4f)));
        int curW = (int) (barW * progress);
        if (curW > 0) g.fill(barX, barY, barX + curW, barY + 1, HudAnimUtil.withAlpha(themeColor, alpha));
        g.fill(barX + curW - 1, barY - 1, barX + curW + 1, barY + 2, HudAnimUtil.withAlpha(0xFFFFFF, alpha));

        for (int k = 0; k < scene.getKeyframeCount(); k++) {
            float kf = scene.getTotalTime() > 0 ? (float) scene.getKeyframeTime(k) / scene.getTotalTime() : 0f;
            int kx = barX + (int) (barW * kf);
            g.fill(kx, barY - 1, kx + 1, barY + 2, HudAnimUtil.withAlpha(themeColor, alpha));
        }
    }

    private void drawHologramButton(GuiGraphics g, Font font, String id, int absX, int absY, int bw, int bh, String text, float lx, float ly, float localBtnX, float localBtnY, int alpha, boolean enabled, float dt) {
        if (!enabled) return;
        boolean hovered = lx >= localBtnX && lx <= localBtnX + bw && ly >= localBtnY && ly <= localBtnY + bh;

        float currentHover = buttonHoverStates.getOrDefault(id, 0f);
        currentHover += ((hovered ? 1f : 0f) - currentHover) * Math.min(1f, dt * 18f);
        buttonHoverStates.put(id, currentHover);

        int baseColor = 0x888888;
        int currentColor = interpolateColor(baseColor, themeColor, currentHover);

        g.pose().pushPose();
        float textScale = (text.length() == 1 ? 1.4f : 1.2f) + (0.35f * currentHover);
        g.pose().translate(absX + bw / 2f, absY + bh / 2f - (font.lineHeight * textScale) / 2f + 1, 0);
        g.pose().scale(textScale, textScale, 1f);
        g.drawCenteredString(font, text, 0, 0, HudAnimUtil.withAlpha(currentColor, alpha));
        g.pose().popPose();
    }

    private int interpolateColor(int c1, int c2, float t) {
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }

    public boolean mouseClicked(double mx, double my, int button, int x, int y, int w, int h) {
        if (handle == null || !handle.isValid() || button != 0) return false;

        float lx = (float) (mx - x);
        float ly = (float) (my - y);

        if (lx >= w - 24 && lx <= w - 4 && ly >= 4 && ly <= 22) { handle.togglePause(); playClickSound(); return true; }
        if (lx >= w - 48 && lx <= w - 28 && ly >= 4 && ly <= 22) { handle.replay(); playClickSound(); return true; }
        if (handle.getSceneIndex() > 0 && lx >= 4 && lx <= 18 && ly >= h / 2f - 15 && ly <= h / 2f + 15) { handle.scrollBack(); playClickSound(); return true; }
        if (handle.getSceneIndex() < handle.getSceneCount() - 1 && lx >= w - 18 && lx <= w - 4 && ly >= h / 2f - 15 && ly <= h / 2f + 15) { handle.scrollForward(); playClickSound(); return true; }

        int pad = 12;
        int barY = h - 8;
        int barX = pad;
        int barW = w - pad * 2;
        if (ly >= barY - 4 && ly <= barY + 4 && lx >= barX && lx <= barX + barW) {
            float t = (lx - barX) / barW;
            handle.seekToTime((int) (t * handle.currentScene().getTotalTime()));
            return true;
        }
        return false;
    }

    private void render3DScene(GuiGraphics g, PonderScene scene, int areaX, int areaY, int areaW, int areaH, float pt, int alpha) {
        SuperRenderTypeBuffer buffer = DefaultSuperRenderTypeBuffer.getInstance();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.backupProjectionMatrix();

        // 【核心动画修复】：赛博全息向内坍缩特效
        // 因为 3D 模型无法原生透明淡出，我们使用 Scissor 将其在 alpha 降低时从两侧向中心切割折叠！
        float collapseFactor = alpha / 255.0f;

        // 当达到 1 时保持原样，小于 1 时极速从中心收缩
        // 添加缓动让坍缩看起来更有力量感 (EaseInCubic)
        float easeWipe = collapseFactor * collapseFactor * collapseFactor;
        int currentW = (int) (areaW * easeWipe);
        int offsetX = (areaW - currentW) / 2;

        Screen currentScreen = Minecraft.getInstance().screen;
        if (currentScreen instanceof GuideListScreen gls) {
            gls.enableScissor(g, areaX + offsetX, areaY, areaX + offsetX + currentW, areaY + areaH);
        } else {
            g.enableScissor(areaX + offsetX, areaY, areaX + offsetX + currentW, areaY + areaH);
        }

        Matrix4f proj = new Matrix4f(RenderSystem.getProjectionMatrix());
        proj.translate(0, 0, 800);
        RenderSystem.setProjectionMatrix(proj, VertexSorting.DISTANCE_TO_ORIGIN);

        PoseStack ms = g.pose();
        ms.pushPose();
        ms.translate(areaX, areaY, -800);
        RenderSystem.setupLevelDiffuseLighting(DIFFUSE_0, DIFFUSE_1, ms.last().pose());

        scene.getTransform().updateScreenParams(areaW, areaH, 0);
        scene.getTransform().apply(ms, pt);
        scene.getTransform().updateSceneRVE(pt);
        scene.renderScene(buffer, g, pt);
        buffer.draw();

        ms.popPose();
        g.disableScissor();
        RenderSystem.restoreProjectionMatrix();
        RenderSystem.disableDepthTest();

        // 重新开启一次正常尺寸的剪裁框给 Overlay，不然文字会被切掉
        if (currentScreen instanceof GuideListScreen gls) {
            gls.enableScissor(g, areaX, areaY, areaX + areaW, areaY + areaH);
        } else {
            g.enableScissor(areaX, areaY, areaX + areaW, areaY + areaH);
        }

        ms.pushPose();
        ms.translate(areaX, areaY, 100);

        // 如果正在淡出，通过改颜色让附着文本变黑（文本是可以响应Shader颜色的）
        if (alpha < 255) RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha / 255.0F);
        scene.renderOverlay(null, g, pt);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        ms.popPose();
        g.disableScissor();
    }

    public void onScreenClosed() { unbind(); }

    private void renderMissing(GuiGraphics g, int x, int y, int w, int h, int alpha) {
        Font font = Minecraft.getInstance().font;
        g.drawCenteredString(font, "SCENE MISSING", x + w / 2, y + h / 2 - 10, HudAnimUtil.withAlpha(0xFF8888, alpha));
        if (boundSceneId != null) g.drawCenteredString(font, boundSceneId.toString(), x + w / 2, y + h / 2 + 4, HudAnimUtil.withAlpha(0xC0D0E0, alpha));
    }

    private void warnMissing(ResourceLocation sceneId) {
        if (WARNED_MISSING_SCENES.add(sceneId)) Arc_Quest.LOGGER.warn("[Guide] Missing embedded ponder scene '{}'", sceneId);
    }

    private void playClickSound() { Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)); }
}