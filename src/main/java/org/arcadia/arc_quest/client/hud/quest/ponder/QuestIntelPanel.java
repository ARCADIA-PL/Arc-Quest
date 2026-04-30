package org.arcadia.arc_quest.client.hud.quest.ponder;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.ponder.Ponder;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.ponder.ArcQuestPonderSceneRegistry;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Quest Intel Ponder 面板。
 * 完美移植 Genesis 的动画参数，呈现高级感全息机能面板。
 */
public final class QuestIntelPanel {

    // ── 面板尺寸（采用 16:9 基础宽屏比例）──────────────
    private static final int PANEL_W = 400;
    private static final int PANEL_H = 225;

    // 进度条边距
    private static final int PROG_PAD = 16;

    // 动画常量设计对标 Splash
    private static final float ENTER_TIME = 0.7f;
    private static final float EXIT_TIME = 0.5f;

    private static final Vector3f DIFFUSE_0 = new Vector3f(-0.2F, 1.0F, 0.7F).normalize();
    private static final Vector3f DIFFUSE_1 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
    // 交互悬浮状态插值追踪 (丝滑放大换色)
    private static final Map<String, Float> buttonHoverStates = new HashMap<>();
    // ── 状态 ──────────────────────────────────────────────────────────
    @Nullable
    private static List<PonderScene> activeScenes = null;
    private static int sceneIndex = 0;
    private static int themeColor = 0x4FC3F7;
    // 独立的时间轴动画状态
    private static float enterTimer = 0f;
    private static float exitTimer = 0f;
    private static boolean isClosing = false;
    private static long lastTime = 0;
    private static boolean isPaused = false;
    // 场景渲染区域尺寸
    private static int lastSceneAreaW = PANEL_W;
    private static int lastSceneAreaH = PANEL_H;

    // ── 渲染期动态坐标与缩放（供点击事件检测使用）────────────────────
    private static float currentScale = 1.0f;
    private static float currentDrawX = 0;
    private static float currentDrawY = 0;

    private QuestIntelPanel() {
    }

    // ── 公开 API ─────────────────────────────────────────────────────

    public static void trigger(ResourceLocation sceneId, int theme, int ax, int ay, int aw, int ah) {
        trigger(sceneId, theme);
    }

    public static void trigger(ResourceLocation sceneId, int theme) {
        if (sceneId == null) return;
        List<PonderScene> scenes = PonderIndex.getSceneAccess().compile(sceneId);
        if (scenes == null || scenes.isEmpty()) {
            Ponder.LOGGER.warn("[ArcQuest] No Ponder scenes for: {}", sceneId);
            return;
        }
        activeScenes = scenes;
        sceneIndex = 0;
        themeColor = theme;

        enterTimer = 0f;
        exitTimer = 0f;
        isClosing = false;
        isPaused = false;
        buttonHoverStates.clear(); // 清空悬停状态
        lastTime = System.currentTimeMillis();

        scenes.get(0).begin();
        IntelPonderUIStub.invalidate();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, 1.0F));
    }

    public static void dismiss() {
        if (!isClosing && activeScenes != null) {
            isClosing = true;
            exitTimer = 0f;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_OUT, 1.0F));
        }
    }

    public static boolean isActive() {
        return activeScenes != null;
    }

    public static boolean hasScene(ResourceLocation sceneId) {
        if (sceneId == null) return false;
        return ArcQuestPonderSceneRegistry.hasScene(sceneId) && PonderIndex.getSceneAccess().doScenesExistForId(sceneId);
    }

    public static void tick() {
        if (activeScenes == null || isClosing || isPaused) return;
        PonderUI.ponderTicks++;
        activeScenes.get(sceneIndex).tick();
    }

    public static void scrollForward() {
        if (activeScenes == null || sceneIndex >= activeScenes.size() - 1) return;
        activeScenes.get(sceneIndex).fadeOut();
        sceneIndex++;
        activeScenes.get(sceneIndex).begin();
        isPaused = false;
        IntelPonderUIStub.invalidate();
    }

    public static void scrollBack() {
        if (activeScenes == null || sceneIndex <= 0) return;
        activeScenes.get(sceneIndex).fadeOut();
        sceneIndex--;
        activeScenes.get(sceneIndex).begin();
        isPaused = false;
        IntelPonderUIStub.invalidate();
    }

    public static void replay() {
        if (activeScenes == null) return;
        activeScenes.get(sceneIndex).begin();
        isPaused = false;
    }

    public static void togglePause() {
        isPaused = !isPaused;
    }

    public static boolean isPaused() {
        return isPaused;
    }

    public static int getSceneIndex() {
        return sceneIndex;
    }

    public static int getSceneCount() {
        return activeScenes == null ? 0 : activeScenes.size();
    }

    @Nullable
    public static List<PonderScene> getActiveScenes() {
        return activeScenes;
    }

    public static int getLastSceneAreaW() {
        return lastSceneAreaW;
    }

    public static int getLastSceneAreaH() {
        return lastSceneAreaH;
    }

    // ── 渲染核心 ──────────────────────────────────────────────────────────

    public static void render(GuiGraphics g, int screenW, int screenH, float partialTick) {
        if (activeScenes == null) return;

        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastTime) / 1000f, 0.1f);
        lastTime = now;

        // 动态缩放，占据屏幕高度的 65%
        float finalScale = (screenH * 0.65f) / (float) PANEL_H;
        float baseX = (screenW / 2f) - ((PANEL_W * finalScale) / 2f);
        float baseY = (screenH / 2f) - ((PANEL_H * finalScale) / 2f);

        float scaleAnim = finalScale;
        float currentX = baseX;
        float currentY = baseY;

        float alphaF = 1.0f;
        float revealProgress = 1.0f;
        float wipeProgress = 0.0f;
        float actualFlyDist = 4.0f * finalScale;

        // 动画缓动逻辑计算
        if (isClosing) {
            exitTimer += dt;
            if (exitTimer >= EXIT_TIME) {
                activeScenes = null;
                return;
            }
            float t = Math.min(1.0f, exitTimer / EXIT_TIME);
            float easeIn = (float) Math.pow(t, 4.0); // Ease In Quart
            wipeProgress = easeIn;
            currentX = baseX - (easeIn * actualFlyDist * 1.5f);
            alphaF = 1.0f - (float) Math.pow(t, 8.0);
        } else {
            enterTimer = Math.min(ENTER_TIME, enterTimer + dt);
            float t = Math.min(1.0f, enterTimer / ENTER_TIME);
            float easeOut = (float) (1.0 - Math.pow(1.0 - t, 5)); // Ease Out Quint
            revealProgress = easeOut;
            alphaF = easeOut;
            scaleAnim = finalScale * (1.10f - 0.10f * easeOut); // 轻微缩放推镜
            currentX = baseX - (1.0f - easeOut) * actualFlyDist * 2f;
        }

        if (revealProgress <= 0.001f || wipeProgress >= 0.999f) return;

        // 计算带缩放推移偏移量的最终渲染原点
        float drawWidth = PANEL_W * scaleAnim;
        float drawHeight = PANEL_H * scaleAnim;
        float scaleOffsetW = (drawWidth - PANEL_W * finalScale) / 2f;
        float scaleOffsetH = (drawHeight - PANEL_H * finalScale) / 2f;

        currentDrawX = currentX - scaleOffsetW;
        currentDrawY = currentY - scaleOffsetH;
        currentScale = scaleAnim;

        // 动态裁切计算 (Scissor Wipe)
        int scX1 = (int) (currentDrawX - 10);
        int scX2 = (int) (currentDrawX + drawWidth + 10);

        if (isClosing) {
            scX2 = (int) (currentDrawX + drawWidth * (1.0f - wipeProgress));
        } else if (enterTimer < ENTER_TIME) {
            scX2 = (int) (currentDrawX + drawWidth * revealProgress);
        }

        g.pose().pushPose();
        g.pose().translate(0, 0, 4500); // 置顶 UI 层级

        g.enableScissor(scX1, (int) (currentDrawY - 10), scX2, (int) (currentDrawY + drawHeight + 10));
        g.pose().pushPose();
        g.pose().translate(currentDrawX, currentDrawY, 0);
        g.pose().scale(scaleAnim, scaleAnim, 1f);

        int alphaInt = Math.max(0, Math.min(255, (int) (255 * alphaF)));
        renderPanel(g, Minecraft.getInstance().font, alphaInt, alphaF, dt, partialTick);

        g.pose().popPose();
        g.disableScissor();
        g.pose().popPose();
    }

    private static void renderPanel(GuiGraphics g, Font font, int alpha, float alphaF, float dt, float pt) {
        int PW = PANEL_W, PH = PANEL_H;
        PonderScene scene = activeScenes.get(sceneIndex);

        // 获取鼠标局部坐标
        Minecraft mc = Minecraft.getInstance();
        double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();
        float lx = (float) ((mouseX - currentDrawX) / currentScale);
        float ly = (float) ((mouseY - currentDrawY) / currentScale);

        // 边框渲染
        int cyberEdgeWidth = 3;
        int bgAlpha = (int) (0xA0 * alphaF);
        int borderAlpha = (int) (0x66 * alphaF);
        int edgeAlpha = alpha;
        int borderRgb = 0xCCCCCC;

        // 背景暗化
        g.fill(cyberEdgeWidth, 0, PW, PH, HudAnimUtil.withAlpha(0x000000, bgAlpha));

        // 灰色框
        g.fill(cyberEdgeWidth, 0, PW, 1, HudAnimUtil.withAlpha(borderRgb, borderAlpha)); // 上
        g.fill(cyberEdgeWidth, PH - 1, PW, PH, HudAnimUtil.withAlpha(borderRgb, borderAlpha)); // 下
        g.fill(PW - 1, 0, PW, PH, HudAnimUtil.withAlpha(borderRgb, borderAlpha)); // 右

        // 左侧彩条
        HudRenderUtil.drawCyberneticEdge(g, 0, 0, PH, themeColor, edgeAlpha);

        //顶部信息排版
        int topBarH = 26;
        g.pose().pushPose();
        g.pose().scale(0.7f, 0.7f, 1f);
        g.drawString(font, "SYS.ARC_QUEST // PHASE INTEL", 16, 6, HudAnimUtil.withAlpha(0x667788, alpha), false);
        g.pose().popPose();

        String title = scene.getTitle();
        g.pose().pushPose();
        g.pose().scale(1.05f, 1.05f, 1f);
        g.drawString(font, title, (int) (14 / 1.05f), (int) (12 / 1.05f), HudAnimUtil.withAlpha(0xFFFFFF, alpha), true);
        g.pose().popPose();

        if (activeScenes.size() > 1) {
            String counter = (sceneIndex + 1) + " / " + activeScenes.size();
            g.pose().pushPose();
            g.pose().scale(0.75f, 0.75f, 1f);
            int titleW = font.width(title);
            g.drawString(font, counter, (int) ((20 + titleW * 1.05f) / 0.75f), (int) (16 / 0.75f), HudAnimUtil.withAlpha(0x4488AA, alpha), false);
            g.pose().popPose();
        }

        g.fill(10, topBarH - 1, PW - 10, topBarH, HudAnimUtil.withAlpha(borderRgb, borderAlpha));

        boolean canPrev = sceneIndex > 0;
        boolean canNext = activeScenes != null && sceneIndex < activeScenes.size() - 1;

        // 右上角系统控制 (重播 / 暂停)
        drawContentHologramButton(g, font, "replay", PW - 48, 4, 20, 18, "↺", lx, ly, alpha, themeColor, true, dt);
        drawContentHologramButton(g, font, "pause", PW - 24, 4, 20, 18, isPaused ? "▶" : "‖", lx, ly, alpha, themeColor, true, dt);

        // 两侧悬浮切换控制
        drawContentHologramButton(g, font, "prev", 4, PH / 2 - 15, 14, 30, "◄", lx, ly, alpha, themeColor, canPrev, dt);
        drawContentHologramButton(g, font, "next", PW - 18, PH / 2 - 15, 14, 30, "►", lx, ly, alpha, themeColor, canNext, dt);

        // ── Ponder 3D 场景层 ──────────────────────────────────────────
        int sceneX = 22;
        int sceneY = topBarH;
        int sceneW = PW - 44;
        int sceneH = PH - topBarH - 20;
        lastSceneAreaW = sceneW;
        lastSceneAreaH = sceneH;

        //思索渲染
        renderPonderScene(g, scene, sceneX, sceneY, sceneW, sceneH, pt);

        // ── 进度条 ────────────────────────────────────────────
        renderMinimalProgressBar(g, scene, alpha, alphaF);

        // 底部提示
        g.pose().pushPose();
        g.pose().scale(0.6f, 0.6f, 1f);
        String hint = "ESC / 点击外侧关闭";
        int hw = font.width(hint);
        g.drawString(font, hint, (int) ((PW / 2f - hw * 0.6f / 2f) / 0.6f), (int) ((PH - 6) / 0.6f), HudAnimUtil.withAlpha(0x445566, alpha), false);
        g.pose().popPose();
    }

    private static void renderMinimalProgressBar(GuiGraphics g, PonderScene scene, int alpha, float alphaF) {
        int barY = PANEL_H - 12;
        int barX = PROG_PAD;
        int barW = PANEL_W - PROG_PAD * 2;

        float progress = scene.getTotalTime() > 0 ? (float) scene.getCurrentTime() / scene.getTotalTime() : 0f;

        g.fill(barX, barY, barX + barW, barY + 1, HudAnimUtil.withAlpha(0x334455, (int) (alpha * 0.4f)));

        int curW = (int) (barW * progress);
        if (curW > 0) {
            g.fill(barX, barY, barX + curW, barY + 1, HudAnimUtil.withAlpha(themeColor, alpha));
        }

        g.fill(barX + curW - 1, barY - 1, barX + curW + 1, barY + 2, HudAnimUtil.withAlpha(0xFFFFFF, alpha));

        for (int k = 0; k < scene.getKeyframeCount(); k++) {
            float kf = scene.getTotalTime() > 0 ? (float) scene.getKeyframeTime(k) / scene.getTotalTime() : 0f;
            int kx = barX + (int) (barW * kf);
            g.fill(kx, barY - 1, kx + 1, barY + 2, HudAnimUtil.withAlpha(themeColor, alpha));
        }
    }

    private static void drawContentHologramButton(GuiGraphics g, Font font, String id, int x, int y, int w, int h, String text, float lx, float ly, int alpha, int colorTheme, boolean enabled, float dt) {
        if (!enabled) return;
        boolean hovered = lx >= x && lx <= x + w && ly >= y && ly <= y + h;

        float hoverTarget = hovered ? 1f : 0f;
        float currentHover = buttonHoverStates.getOrDefault(id, 0f);
        currentHover += (hoverTarget - currentHover) * Math.min(1f, dt * 18f);
        buttonHoverStates.put(id, currentHover);

        int baseColor = 0x888888;
        int currentColor = interpolateColor(baseColor, colorTheme, currentHover);
        int finalTextColor = HudAnimUtil.withAlpha(currentColor, alpha);

        g.pose().pushPose();
        float baseTextScale = text.length() == 1 ? 1.4f : 1.2f;
        float textScale = baseTextScale + (0.35f * currentHover);

        g.pose().translate(x + w / 2f, y + h / 2f - (font.lineHeight * textScale) / 2f + 1, 0);
        g.pose().scale(textScale, textScale, 1f);
        g.drawCenteredString(font, text, 0, 0, finalTextColor);
        g.pose().popPose();
    }

    private static int interpolateColor(int c1, int c2, float t) {
        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF;
        int g2 = (c2 >> 8) & 0xFF;
        int b2 = c2 & 0xFF;
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static void renderPonderScene(GuiGraphics g, PonderScene scene, int areaX, int areaY, int areaW, int areaH, float pt) {
        SuperRenderTypeBuffer buffer = DefaultSuperRenderTypeBuffer.getInstance();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.backupProjectionMatrix();

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
        RenderSystem.restoreProjectionMatrix();
        RenderSystem.disableDepthTest();

        ms.pushPose();
        ms.translate(areaX, areaY, 100);
        scene.renderOverlay(null, g, pt);
        ms.popPose();
    }

    public static boolean handleMouseClick(double mx, double my, int screenW, int screenH) {
        if (activeScenes == null || isClosing) return false;

        float scaledW = PANEL_W * currentScale;
        float scaledH = PANEL_H * currentScale;

        if (mx < currentDrawX || mx > currentDrawX + scaledW || my < currentDrawY || my > currentDrawY + scaledH) {
            dismiss();
            return true;
        }

        float lx = (float) ((mx - currentDrawX) / currentScale);
        float ly = (float) ((my - currentDrawY) / currentScale);

        int PW = PANEL_W, PH = PANEL_H;

        if (lx >= PW - 24 && lx <= PW - 4 && ly >= 4 && ly <= 22) {
            togglePause();
            playClickSound();
            return true;
        }

        if (lx >= PW - 48 && lx <= PW - 28 && ly >= 4 && ly <= 22) {
            replay();
            playClickSound();
            return true;
        }

        if (sceneIndex > 0 && lx >= 4 && lx <= 18 && ly >= PH / 2f - 15 && ly <= PH / 2f + 15) {
            scrollBack();
            playClickSound();
            return true;
        }

        if (sceneIndex < activeScenes.size() - 1 && lx >= PW - 18 && lx <= PW - 4 && ly >= PH / 2f - 15 && ly <= PH / 2f + 15) {
            scrollForward();
            playClickSound();
            return true;
        }

        int barY = PANEL_H - 12;
        int barX = PROG_PAD;
        int barW = PANEL_W - PROG_PAD * 2;
        if (ly >= barY - 4 && ly <= barY + 4 && lx >= barX && lx <= barX + barW) {
            float t = (lx - barX) / barW;
            PonderScene scene = activeScenes.get(sceneIndex);
            if (scene.getTotalTime() > 0) {
                int target = (int) (t * scene.getTotalTime());
                if (target < scene.getCurrentTime()) scene.begin();
                scene.seekToTime(target);
            }
            return true;
        }

        return true;
    }

    private static void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}