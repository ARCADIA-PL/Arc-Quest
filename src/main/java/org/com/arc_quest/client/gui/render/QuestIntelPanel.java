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
import org.com.arc_quest.client.gui.HudAnimUtil;
import org.com.arc_quest.client.gui.HudRenderUtil;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 嵌入式 Ponder 情报面板。
 *
 * <p>不依赖 Screen 或 Overlay，可直接在任意 GUI 的 render() 末尾调用，
 * 将 PonderScene 缩放渲染到指定矩形区域内，外层仅提供美术边框装饰。
 *
 * <p>调用方式：
 * <pre>
 *   // 触发
 *   QuestIntelPanel.trigger(sceneId, themeColor);
 *   // 每帧渲染（在 JournalScreen.render 末尾）
 *   QuestIntelPanel.render(g, screenW, screenH, partialTick);
 *   // 关闭
 *   QuestIntelPanel.dismiss();
 * </pre>
 */
public final class QuestIntelPanel {

    private static final int FRAME_W = 320;
    private static final int FRAME_H = 400;
    private static final Vector3f DIFFUSE_0 = new Vector3f(-0.2F, 1.0F, 0.7F).normalize();
    private static final Vector3f DIFFUSE_1 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();

    @Nullable
    private static List<PonderScene> activeScenes = null;
    private static int sceneIndex = 0;
    private static int themeColor = 0x4FC3F7;
    private static float animProgress = 0f;
    private static boolean isClosing = false;
    private static long lastTime = 0;

    private QuestIntelPanel() {}

    public static void trigger(ResourceLocation sceneId, int theme) {
        List<PonderScene> scenes = PonderIndex.getSceneAccess().compile(sceneId);
        if (scenes == null || scenes.isEmpty()) return;

        activeScenes = scenes;
        sceneIndex = 0;
        themeColor = theme;
        animProgress = 0f;
        isClosing = false;
        lastTime = System.currentTimeMillis();
        scenes.get(0).begin();

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

    public static boolean isActive() {
        return activeScenes != null;
    }

    /** 驱动当前场景的 tick，需在游戏 tick 中调用（ClientEventHandler.onClientTick）。 */
    public static void tick() {
        if (activeScenes == null || isClosing) return;
        PonderUI.ponderTicks++;
        activeScenes.get(sceneIndex).tick();
    }

    /** 切换到下一个场景。 */
    public static void scrollForward() {
        if (activeScenes == null || sceneIndex >= activeScenes.size() - 1) return;
        activeScenes.get(sceneIndex).fadeOut();
        sceneIndex++;
        activeScenes.get(sceneIndex).begin();
    }

    /** 切换到上一个场景。 */
    public static void scrollBack() {
        if (activeScenes == null || sceneIndex <= 0) return;
        activeScenes.get(sceneIndex).fadeOut();
        sceneIndex--;
        activeScenes.get(sceneIndex).begin();
    }

    /** 重播当前场景。 */
    public static void replay() {
        if (activeScenes == null) return;
        activeScenes.get(sceneIndex).begin();
    }

    public static int getSceneIndex() { return sceneIndex; }

    public static int getSceneCount() { return activeScenes == null ? 0 : activeScenes.size(); }

    /**
     * 在屏幕居中渲染面板，需在 Journal render() 最后调用。
     *
     * @param g          GuiGraphics
     * @param screenW    屏幕宽度
     * @param screenH    屏幕高度
     * @param partialTick 部分 tick
     */
    public static void render(GuiGraphics g, int screenW, int screenH, float partialTick) {
        if (activeScenes == null) return;

        long now = System.currentTimeMillis();
        float dt = (now - lastTime) / 1000f;
        lastTime = now;
        if (dt > 0.1f) dt = 0.1f;

        if (isClosing) {
            animProgress -= dt * 5f;
            if (animProgress <= 0f) {
                activeScenes = null;
                return;
            }
        } else {
            animProgress += (1f - animProgress) * Math.min(1f, dt * 10f);
        }

        if (animProgress < 0.01f) return;

        float ease = HudAnimUtil.easeOutCubic(animProgress);
        int alpha = (int) (255 * ease);

        int panelX = (screenW - FRAME_W) / 2;
        int panelY = (screenH - FRAME_H) / 2;

        // 缩放动画：从屏幕中心弹出
        float scale = 0.85f + 0.15f * ease;
        int scaledW = (int) (FRAME_W * scale);
        int scaledH = (int) (FRAME_H * scale);
        int drawX = (screenW - scaledW) / 2;
        int drawY = (screenH - scaledH) / 2;

        g.pose().pushPose();
        g.pose().translate(0, 0, 500);

        // 全屏暗化遮罩
        g.fill(0, 0, screenW, screenH, (int) (180 * ease) << 24);

        // Scissor 裁剪到面板区域（含少量边距）
        g.enableScissor(drawX - 2, drawY - 2, drawX + scaledW + 2, drawY + scaledH + 2);

        g.pose().pushPose();
        g.pose().translate(drawX, drawY, 0);
        g.pose().scale(scale, scale, 1f);

        // 深色半透明背景
        int bgAlpha = (int) (0xCC * ease);
        g.fill(0, 0, FRAME_W, FRAME_H, (bgAlpha << 24) | 0x050810);

        // 机能边框（与 SplashRenderer 同款）
        HudRenderUtil.drawCyberneticEdge(g, 0, 0, FRAME_H, themeColor, alpha);

        // 四角折角装饰
        int decAlpha = alpha;
        int decColor = (decAlpha << 24) | 0xFFFFFF;
        g.fill(0, 0, 20, 2, decColor);
        g.fill(0, 0, 2, 20, decColor);
        g.fill(FRAME_W - 20, FRAME_H - 2, FRAME_W, FRAME_H, decColor);
        g.fill(FRAME_W - 2, FRAME_H - 20, FRAME_W, FRAME_H, decColor);

        // 顶部标题栏
        Font font = Minecraft.getInstance().font;
        PonderScene scene = activeScenes.get(sceneIndex);
        int titleAlpha = alpha;

        g.pose().pushPose();
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, "SYS.ARC_QUEST // PHASE INTEL", (int) (16 / 0.75f), (int) (14 / 0.75f),
                HudAnimUtil.withAlpha(0x888888, titleAlpha), true);
        g.pose().popPose();

        g.pose().pushPose();
        g.pose().scale(1.1f, 1.1f, 1f);
        g.drawString(font, scene.getTitle(), (int) (14 / 1.1f), (int) (26 / 1.1f),
                HudAnimUtil.withAlpha(0xFFFFFF, titleAlpha), true);
        g.pose().popPose();

        // 标题分隔线
        int lineColor = (alpha / 2 << 24) | (themeColor & 0xFFFFFF);
        g.fill(12, 42, 180, 43, lineColor);

        // Ponder 场景渲染区域（标题下方）
        int sceneAreaY = 46;
        int sceneAreaH = FRAME_H - sceneAreaY - 30;

        renderPonderScene(g, scene, 0, sceneAreaY, FRAME_W, sceneAreaH, partialTick, ease);

        // 底部操作提示
        renderBottomBar(g, font, alpha, sceneAreaY + sceneAreaH);

        g.pose().popPose();
        g.disableScissor();
        g.pose().popPose();
    }

    private static void renderPonderScene(GuiGraphics g, PonderScene scene,
                                          int areaX, int areaY, int areaW, int areaH,
                                          float partialTick, float ease) {
        SuperRenderTypeBuffer buffer = DefaultSuperRenderTypeBuffer.getInstance();

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.backupProjectionMatrix();

        // 修改投影矩阵（与 PonderUI 完全一致）
        Matrix4f proj = new Matrix4f(RenderSystem.getProjectionMatrix());
        proj.translate(0, 0, 800);
        RenderSystem.setProjectionMatrix(proj, VertexSorting.DISTANCE_TO_ORIGIN);

        PoseStack ms = g.pose();
        ms.pushPose();
        ms.translate(areaX, areaY, -800);

        RenderSystem.setupLevelDiffuseLighting(DIFFUSE_0, DIFFUSE_1, ms.last().pose());

        // 告知场景其渲染区域大小，场景会在该尺寸内自动居中投影
        scene.getTransform().updateScreenParams(areaW, areaH, 0);
        scene.getTransform().apply(ms, partialTick);
        scene.getTransform().updateSceneRVE(partialTick);
        scene.renderScene(buffer, g, partialTick);
        buffer.draw();

        ms.popPose();
        RenderSystem.restoreProjectionMatrix();
        RenderSystem.disableDepthTest();

        // 场景文字 Overlay（文字气泡等）
        ms.pushPose();
        ms.translate(areaX, areaY, 100);
        scene.renderOverlay(null, g, partialTick);
        ms.popPose();
    }

    private static void renderBottomBar(GuiGraphics g, Font font, int alpha, int y) {
        // 导航提示
        String navHint;
        if (activeScenes != null && activeScenes.size() > 1) {
            navHint = (sceneIndex + 1) + " / " + activeScenes.size() + "  ◄ ► 切换  |  点击关闭";
        } else {
            navHint = "点击任意处关闭";
        }

        g.pose().pushPose();
        g.pose().scale(0.75f, 0.75f, 1f);
        int textX = (int) (FRAME_W / 2f / 0.75f);
        int textY = (int) (y / 0.75f) + 6;
        g.drawCenteredString(font, navHint, textX, textY, HudAnimUtil.withAlpha(0x555555, alpha));
        g.pose().popPose();
    }
}
