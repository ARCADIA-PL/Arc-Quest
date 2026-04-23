package org.com.arc_quest.client.gui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.com.arc_quest.client.gui.HudAnimUtil;
import org.com.arc_quest.client.gui.HudRenderUtil;
import org.com.arc_quest.client.ponder.ArcQuestPonderSceneRegistry;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Quest Intel Ponder 面板。
 * 以全屏居中方式弹出，与 QuestSplashRenderer 风格对齐。
 * 功能：多场景切换、进度条（含点击跳转）、暂停/恢复、重播、文字气泡。
 */
public final class QuestIntelPanel {

    // ── 面板尺寸（固定，参考 SplashRenderer 用屏高比例）──────────────
    private static final int PANEL_W = 480;
    private static final int PANEL_H = 340;

    // 底部控制栏高度
    private static final int CTRL_H = 36;
    // 进度条区域
    private static final int PROG_H   = 4;
    private static final int PROG_PAD = 12;

    // 按钮尺寸与位置（相对于面板坐标系）
    private static final int BTN_Y   = PANEL_H - CTRL_H + 8;
    private static final int BTN_SZ  = 18;

    private static final Vector3f DIFFUSE_0 = new Vector3f(-0.2F, 1.0F, 0.7F).normalize();
    private static final Vector3f DIFFUSE_1 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();

    // ── 状态 ──────────────────────────────────────────────────────────
    @Nullable private static List<PonderScene> activeScenes = null;
    private static int   sceneIndex   = 0;
    private static int   themeColor   = 0x4FC3F7;
    private static float animProgress = 0f;
    private static boolean isClosing  = false;
    private static long lastTime      = 0;
    private static boolean isPaused   = false;

    // 场景渲染区域尺寸（供 MixinPonderScene/IntelPonderUIStub 使用）
    private static int lastSceneAreaW = PANEL_W;
    private static int lastSceneAreaH = PANEL_H - CTRL_H;

    // ── 弹出位置（全屏居中，每帧根据屏幕尺寸计算）────────────────────
    private static int drawX = 0, drawY = 0;

    private QuestIntelPanel() {}

    // ── 公开 API ─────────────────────────────────────────────────────

    public static void trigger(ResourceLocation sceneId, int theme,
                               int ax, int ay, int aw, int ah) {
        trigger(sceneId, theme);
    }

    public static void trigger(ResourceLocation sceneId, int theme) {
        if (sceneId == null) return;
        List<PonderScene> scenes = PonderIndex.getSceneAccess().compile(sceneId);
        if (scenes == null || scenes.isEmpty()) {
            net.createmod.ponder.Ponder.LOGGER.warn("[ArcQuest] No Ponder scenes for: {}", sceneId);
            return;
        }
        activeScenes = scenes;
        sceneIndex   = 0;
        themeColor   = theme;
        animProgress = 0f;
        isClosing    = false;
        isPaused     = false;
        lastTime     = System.currentTimeMillis();
        scenes.get(0).begin();
        IntelPonderUIStub.invalidate();
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, 1.0F));
    }

    public static void dismiss() {
        if (!isClosing && activeScenes != null) {
            isClosing = true;
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_OUT, 1.0F));
        }
    }

    public static boolean isActive()  { return activeScenes != null; }

    public static boolean hasScene(ResourceLocation sceneId) {
        if (sceneId == null) return false;
        return ArcQuestPonderSceneRegistry.hasScene(sceneId)
                && PonderIndex.getSceneAccess().doScenesExistForId(sceneId);
    }

    /** 每 game-tick 调用一次（ClientEventHandler.onClientTick）。 */
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

    public static void togglePause() { isPaused = !isPaused; }
    public static boolean isPaused()  { return isPaused; }

    public static int getSceneIndex() { return sceneIndex; }
    public static int getSceneCount() { return activeScenes == null ? 0 : activeScenes.size(); }

    @Nullable
    public static List<PonderScene> getActiveScenes() { return activeScenes; }

    public static int getLastSceneAreaW() { return lastSceneAreaW; }
    public static int getLastSceneAreaH() { return lastSceneAreaH; }

    // ── 渲染 ──────────────────────────────────────────────────────────

    public static void render(GuiGraphics g, int screenW, int screenH, float partialTick) {
        if (activeScenes == null) return;

        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastTime) / 1000f, 0.1f);
        lastTime = now;

        if (isClosing) {
            animProgress -= dt * 7f;
            if (animProgress <= 0f) { activeScenes = null; return; }
        } else {
            animProgress = Mth.clamp(animProgress + dt * 10f, 0f, 1f);
        }
        if (animProgress < 0.01f) return;

        float ease  = HudAnimUtil.easeOutCubic(animProgress);
        int   alpha = (int)(255 * ease);

        // 全屏居中（参考 SplashRenderer：baseX = screenW/2 - frameW/2）
        drawX = (screenW - PANEL_W) / 2;
        drawY = (screenH - PANEL_H) / 2;

        // 弹出缩放（从中心）
        float scale = 0.90f + 0.10f * ease;
        int sW = (int)(PANEL_W * scale), sH = (int)(PANEL_H * scale);
        int ox = drawX + (PANEL_W - sW) / 2;
        int oy = drawY + (PANEL_H - sH) / 2;

        g.pose().pushPose();
        g.pose().translate(0, 0, 4500);  // 高于 Splash(4000)

        // 全屏暗化
        g.fill(0, 0, screenW, screenH, HudAnimUtil.withAlpha(0x000000, (int)(160 * ease)));

        // Scissor 裁剪
        g.enableScissor(ox - 2, oy - 2, ox + sW + 2, oy + sH + 2);
        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        g.pose().scale(scale, scale, 1f);

        renderPanel(g, Minecraft.getInstance().font, alpha, partialTick);

        g.pose().popPose();
        g.disableScissor();
        g.pose().popPose();
    }

    private static void renderPanel(GuiGraphics g, Font font, int alpha, float pt) {
        int PW = PANEL_W, PH = PANEL_H;
        PonderScene scene = activeScenes.get(sceneIndex);

        // ── 背景 ──────────────────────────────────────────────────────
        g.fill(0, 0, PW, PH, HudAnimUtil.withAlpha(0x030609, alpha));

        // ── 机能左侧竖线 ──────────────────────────────────────────────
        HudRenderUtil.drawCyberneticEdge(g, 0, 0, PH, themeColor, alpha);

        // ── 四角折角 ──────────────────────────────────────────────────
        int dec = HudAnimUtil.withAlpha(0xFFFFFF, alpha);
        g.fill(0,      0,      18,    2,  dec);
        g.fill(0,      0,      2,     18, dec);
        g.fill(PW-18,  PH-2,   PW,   PH,  dec);
        g.fill(PW-2,   PH-18,  PW,   PH,  dec);

        // ── 顶部小标签 ────────────────────────────────────────────────
        int titleAreaH = 40;
        g.pose().pushPose();
        g.pose().scale(0.68f, 0.68f, 1f);
        g.drawString(font, "SYS.ARC_QUEST // PHASE INTEL",
                (int)(14/0.68f), (int)(10/0.68f),
                HudAnimUtil.withAlpha(0x556677, alpha), false);
        g.pose().popPose();

        // ── 场景标题 ──────────────────────────────────────────────────
        String title = scene.getTitle();
        g.pose().pushPose();
        g.pose().scale(1.05f, 1.05f, 1f);
        g.drawString(font, title, (int)(14/1.05f), (int)(20/1.05f),
                HudAnimUtil.withAlpha(0xFFFFFF, alpha), true);
        g.pose().popPose();

        // ── 场景计数 (1 / N) ─────────────────────────────────────────
        if (activeScenes.size() > 1) {
            String counter = (sceneIndex + 1) + " / " + activeScenes.size();
            int cw = font.width(counter);
            g.pose().pushPose();
            g.pose().scale(0.72f, 0.72f, 1f);
            g.drawString(font, counter,
                    (int)((PW - 14 - cw * 0.72f) / 0.72f), (int)(22/0.72f),
                    HudAnimUtil.withAlpha(0x4488AA, alpha), false);
            g.pose().popPose();
        }

        // ── 分隔线 ────────────────────────────────────────────────────
        g.fill(10, titleAreaH - 2, PW - 10, titleAreaH - 1,
                HudAnimUtil.withAlpha(themeColor, alpha / 3));

        // ── Ponder 3D 场景区 ──────────────────────────────────────────
        int sceneY = titleAreaH;
        int sceneH = PH - sceneY - CTRL_H;
        lastSceneAreaW = PW;
        lastSceneAreaH = sceneH;

        renderPonderScene(g, scene, 0, sceneY, PW, sceneH, pt);

        // ── 控制栏背景 ────────────────────────────────────────────────
        int ctrlY = PH - CTRL_H;
        g.fill(0, ctrlY, PW, PH, HudAnimUtil.withAlpha(0x010305, alpha));
        g.fill(0, ctrlY, PW, ctrlY + 1, HudAnimUtil.withAlpha(themeColor, alpha / 5));

        // ── 进度条 ────────────────────────────────────────────────────
        renderProgressBar(g, scene, alpha, ctrlY);

        // ── 控制按钮 ─────────────────────────────────────────────────
        renderControlButtons(g, font, alpha, ctrlY);
    }

    // ── 进度条 ────────────────────────────────────────────────────────

    private static void renderProgressBar(GuiGraphics g, PonderScene scene,
                                          int alpha, int ctrlY) {
        int barY  = ctrlY + 4;
        int barX  = PROG_PAD;
        int barW  = PANEL_W - PROG_PAD * 2;

        float progress = scene.getTotalTime() > 0
                ? (float) scene.getCurrentTime() / scene.getTotalTime() : 0f;

        // 轨道
        g.fill(barX, barY, barX + barW, barY + PROG_H,
                HudAnimUtil.withAlpha(0x223344, alpha));

        // 填充
        int fillW = (int)(barW * Mth.clamp(progress, 0f, 1f));
        if (fillW > 0) {
            g.fill(barX, barY, barX + fillW, barY + PROG_H,
                    HudAnimUtil.withAlpha(themeColor, alpha));
            // 发光端点
            if (fillW > 2) {
                g.fill(barX + fillW - 2, barY - 1, barX + fillW, barY + PROG_H + 1,
                        HudAnimUtil.withAlpha(0xFFFFFF, alpha / 2));
            }
        }

        // 关键帧标记
        for (int k = 0; k < scene.getKeyframeCount(); k++) {
            float kf = scene.getTotalTime() > 0
                    ? (float) scene.getKeyframeTime(k) / scene.getTotalTime() : 0f;
            int kx = barX + (int)(barW * kf);
            g.fill(kx - 1, barY - 1, kx + 1, barY + PROG_H + 1,
                    HudAnimUtil.withAlpha(0xFFCC44, alpha));
        }
    }

    // ── 控制按钮：◄  ‖/▶  ↺  ► ──────────────────────────────────────

    private static void renderControlButtons(GuiGraphics g, Font font, int alpha, int ctrlY) {
        int midX = PANEL_W / 2;
        int bY   = ctrlY + PROG_H + 8;
        int gap  = 26;

        // 布局（相对面板坐标）：[◄ prev] [‖/▶ pause] [↺ replay] [► next]
        int xPrev   = midX - gap * 2 - BTN_SZ / 2;
        int xPause  = midX - gap     - BTN_SZ / 2;
        int xReplay = midX + gap     - BTN_SZ / 2;
        int xNext   = midX + gap * 2 - BTN_SZ / 2;

        boolean canPrev = sceneIndex > 0;
        boolean canNext = activeScenes != null && sceneIndex < activeScenes.size() - 1;

        drawCtrlBtn(g, font, xPrev,   bY, "◄", canPrev  ? 0xAABBCC : 0x334455, alpha);
        drawCtrlBtn(g, font, xPause,  bY, isPaused ? "▶" : "‖", 0xCCDDEE, alpha);
        drawCtrlBtn(g, font, xReplay, bY, "↺", 0xCCDDEE, alpha);
        drawCtrlBtn(g, font, xNext,   bY, "►", canNext  ? 0xAABBCC : 0x334455, alpha);

        // 关闭提示
        g.pose().pushPose();
        g.pose().scale(0.65f, 0.65f, 1f);
        String hint = "ESC / 点击背景关闭";
        int hw = font.width(hint);
        g.drawString(font, hint,
                (int)((PANEL_W / 2f - hw * 0.65f / 2f) / 0.65f),
                (int)((bY + BTN_SZ + 2) / 0.65f),
                HudAnimUtil.withAlpha(0x334455, alpha), false);
        g.pose().popPose();
    }

    private static void drawCtrlBtn(GuiGraphics g, Font font, int x, int y,
                                    String icon, int color, int alpha) {
        g.fill(x, y, x + BTN_SZ, y + BTN_SZ,
                HudAnimUtil.withAlpha(0x0A1520, alpha));
        g.fill(x, y, x + BTN_SZ, y + 1,
                HudAnimUtil.withAlpha(color, alpha / 3));
        int tw = font.width(icon);
        int th = font.lineHeight;
        g.drawString(font, icon,
                x + (BTN_SZ - tw) / 2,
                y + (BTN_SZ - th) / 2,
                HudAnimUtil.withAlpha(color, alpha), false);
    }

    // ── Ponder 3D 场景渲染 ────────────────────────────────────────────

    private static void renderPonderScene(GuiGraphics g, PonderScene scene,
                                          int areaX, int areaY, int areaW, int areaH,
                                          float pt) {
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

        // 文字气泡 overlay（通过 MixinPonderScene + IntelPonderUIStub 提供 stub screen）
        ms.pushPose();
        ms.translate(areaX, areaY, 100);
        scene.renderOverlay(null, g, pt);
        ms.popPose();
    }

    // ── 鼠标点击转发（由 QuestJournalScreen.mouseClicked 调用）──────────

    /**
     * 处理面板内的鼠标点击。
     * 坐标为屏幕坐标。返回 true 表示已消费。
     */
    public static boolean handleMouseClick(double mx, double my, int screenW, int screenH) {
        if (activeScenes == null) return false;

        // 背景区域点击关闭
        int ox = (drawX == 0 && screenW > 0) ? (screenW - PANEL_W) / 2 : drawX;
        int oy = (drawY == 0 && screenH > 0) ? (screenH - PANEL_H) / 2 : drawY;
        float sc = 0.90f + 0.10f * HudAnimUtil.easeOutCubic(animProgress);
        int sW = (int)(PANEL_W * sc), sH = (int)(PANEL_H * sc);
        int aox = ox + (PANEL_W - sW) / 2;
        int aoy = oy + (PANEL_H - sH) / 2;

        if (mx < aox || mx > aox + sW || my < aoy || my > aoy + sH) {
            dismiss(); return true;
        }

        // 转换到面板局部坐标
        float lx = (float)((mx - aox) / sc);
        float ly = (float)((my - aoy) / sc);

        // 进度条点击
        int ctrlY = PANEL_H - CTRL_H;
        if (ly >= ctrlY + 2 && ly <= ctrlY + 4 + PROG_H + 2) {
            int barX = PROG_PAD, barW = PANEL_W - PROG_PAD * 2;
            if (lx >= barX && lx <= barX + barW) {
                float t = (lx - barX) / barW;
                PonderScene scene = activeScenes.get(sceneIndex);
                if (scene.getTotalTime() > 0) {
                    int target = (int)(t * scene.getTotalTime());
                    if (target < scene.getCurrentTime()) scene.begin();
                    scene.seekToTime(target);
                }
                return true;
            }
        }

        // 控制按钮点击
        int midX = PANEL_W / 2;
        int bY   = ctrlY + PROG_H + 8;
        int gap  = 26;
        int[][] btns = {
            { midX - gap*2 - BTN_SZ/2, bY },  // ◄ prev
            { midX - gap   - BTN_SZ/2, bY },  // ‖/▶ pause
            { midX + gap   - BTN_SZ/2, bY },  // ↺ replay
            { midX + gap*2 - BTN_SZ/2, bY },  // ► next
        };
        for (int i = 0; i < btns.length; i++) {
            int bx = btns[i][0], by = btns[i][1];
            if (lx >= bx && lx <= bx + BTN_SZ && ly >= by && ly <= by + BTN_SZ) {
                switch (i) {
                    case 0 -> scrollBack();
                    case 1 -> togglePause();
                    case 2 -> replay();
                    case 3 -> scrollForward();
                }
                return true;
            }
        }

        return true; // 面板内部点击不穿透
    }
}