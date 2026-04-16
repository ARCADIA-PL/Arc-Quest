package org.com.arc_quest.client.gui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.com.arc_quest.quest.api.VisualAsset;

import java.util.ArrayDeque;
import java.util.Queue;

public class QuestSplashRenderer {

    // ── 动画时间轴 ──
    private static final float TIME_ENTER = 600f;
    private static final float TIME_HOLD  = 2500f;
    private static final float TIME_EXIT  = 500f;
    private static final float FLY_DIST   = 15f;
    private static final float DRIFT_DIST = 2f;

    // ── 弹幕队列 ──
    private record SplashRequest(VisualAsset asset, String title, String subtitle, int themeColor) {}
    private static final Queue<SplashRequest> queue = new ArrayDeque<>();

    private static SplashRequest currentSplash = null;
    private static long currentStartTime = 0;

    /**
     * 触发一个新立绘（自动进入队列）
     */
    public static void trigger(VisualAsset asset, String title, String subtitle, int themeColor) {
        if (asset != null && asset.enabled() && asset.texture() != null) {
            queue.offer(new SplashRequest(asset, title, subtitle, themeColor));
        }
    }

    public static void render(GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        long now = Util.getMillis();

        // 队列管理
        if (currentSplash == null) {
            if (queue.isEmpty()) return;
            currentSplash = queue.poll();
            currentStartTime = now;
        }

        long elapsed = now - currentStartTime;
        float totalTime = TIME_ENTER + TIME_HOLD + TIME_EXIT;

        if (elapsed >= totalTime) {
            currentSplash = null; // 动画结束，下一帧加载新弹幕
            return;
        }

        // ── 尺寸与比例 ──
        int frameW = 240;
        int frameH = 135;
        float finalScale = (screenHeight * 0.35f) / (float) frameH; // 动态适配屏幕

        float textW = Minecraft.getInstance().font.width(currentSplash.title) * 1.2f;
        float targetLineWidth = Math.max(textW + 30, frameW * finalScale - 20);

        // ── 动画数学计算 ──
        float alpha = 1f;
        float scaleAnim = finalScale;
        float revealProgress = 1f;
        float wipeProgress = 0f;
        float currentX;
        float textDriftX = 0f;
        float lineW = targetLineWidth;

        float baseX = Math.max(20f, screenWidth * 0.03f); // 左侧留白
        float baseY = screenHeight / 2f - (frameH * finalScale) / 2f; // 垂直居中

        if (elapsed < TIME_ENTER) { // 入场
            float t = Math.min(1f, elapsed / TIME_ENTER);
            float ease = 1f - (float)Math.pow(1f - t, 5); // easeOutQuint

            revealProgress = ease;
            alpha = ease;
            scaleAnim = finalScale * (1.1f - 0.1f * ease);
            currentX = baseX - (1f - ease) * FLY_DIST;
            lineW *= ease;
            textDriftX = -(1f - ease) * 15f;

        } else if (elapsed < TIME_ENTER + TIME_HOLD) { // 待机呼吸
            float t = (elapsed - TIME_ENTER) / TIME_HOLD;
            float driftEase = (float)Math.sin(t * Math.PI);

            currentX = baseX + (driftEase * DRIFT_DIST);

        } else { // 退场
            float t = Math.min(1f, (elapsed - TIME_ENTER - TIME_HOLD) / TIME_EXIT);
            float ease = (float)Math.pow(t, 4); // easeInQuart

            wipeProgress = ease;
            alpha = 1f - (float)Math.pow(t, 6);
            currentX = baseX + DRIFT_DIST + (ease * FLY_DIST);
            lineW *= (1f - ease);
            textDriftX = ease * 25f;
        }

        if (alpha < 0.01f) return;

        // ── Scissor 裁剪遮罩设置 ──
        float drawW = Math.max(frameW * scaleAnim, lineW + 40f);
        int scLeft = (int)currentX - 20;
        int scRight = (int)(currentX + drawW + 20);

        if (elapsed < TIME_ENTER) {
            scRight = (int)(currentX + drawW * revealProgress);
        } else if (elapsed >= TIME_ENTER + TIME_HOLD) {
            scRight = (int)(currentX + drawW * (1f - wipeProgress));
        }

        g.enableScissor(scLeft, 0, scRight, screenHeight);

        // ── 渲染底板与立绘 ──
        g.pose().pushPose();
        g.pose().translate(currentX, baseY, 0);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int bgAlpha = (int)(alpha * 0xDD);
        g.fill(0, 0, (int)(frameW * finalScale), (int)(frameH * finalScale), (bgAlpha << 24) | 0x1A1A1A);

        int accentColor = currentSplash.themeColor & 0xFFFFFF;
        g.fill(0, 0, 3, (int)(frameH * finalScale), ((int)(alpha * 255) << 24) | accentColor);

        // 立绘渲染（应用 VisualAsset 的缩放与偏移）
        VisualAsset asset = currentSplash.asset;
        g.pose().pushPose();
        // 修正中心点进行缩放
        g.pose().translate((frameW * finalScale)/2f, (frameH * finalScale)/2f, 0);
        g.pose().scale(scaleAnim * asset.scale(), scaleAnim * asset.scale(), 1f);
        g.pose().translate(-(frameW * finalScale)/2f, -(frameH * finalScale)/2f, 0);

        g.pose().translate(asset.offsetX() * finalScale, asset.offsetY() * finalScale, 0);

        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        g.blit(asset.texture(), 0, 0, 0, 0, (int)(frameW * finalScale), (int)(frameH * finalScale), (int)(frameW * finalScale), (int)(frameH * finalScale));
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        g.pose().popPose();

        // ── 渲染文字 ──
        float textX = 15 * finalScale + textDriftX;
        float textY = frameH * finalScale - 35;

        int textColor = ((int)(alpha * 255) << 24) | 0xFFFFFF;
        int subColor = ((int)(alpha * 255) << 24) | 0xAAAAAA;

        g.pose().pushPose();
        g.pose().translate(textX, textY, 0);
        g.pose().scale(0.8f, 0.8f, 1f);
        g.drawString(Minecraft.getInstance().font, currentSplash.subtitle, 0, -10, subColor, true);
        g.pose().popPose();

        g.pose().pushPose();
        g.pose().translate(textX, textY + 5, 0);
        g.pose().scale(1.1f, 1.1f, 1f);
        g.drawString(Minecraft.getInstance().font, currentSplash.title, 0, 0, textColor, true);
        g.pose().popPose();

        // 装饰线
        if (lineW > 2) {
            int lineCol = ((int)(alpha * 255) << 24) | accentColor;
            g.fill((int)textX, (int)textY + 22, (int)(textX + lineW), (int)textY + 23, lineCol);
            if (lineW > 10) g.fill((int)(textX + lineW), (int)textY + 21, (int)(textX + lineW) + 3, (int)textY + 24, lineCol);
        }

        RenderSystem.disableBlend();
        g.pose().popPose();
        g.disableScissor();
    }
}