// 文件名: org.com.arc_quest.client.gui.render.QuestSplashRenderer.java
package org.com.arc_quest.client.gui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.com.arc_quest.quest.api.QuestDefinition;
import org.com.arc_quest.quest.api.SplashType;

public class QuestSplashRenderer {
    private static QuestDefinition activeQuest = null;
    private static SplashType activeType = null;
    private static ResourceLocation activeTexture = null;
    private static long startTime = 0;

    // 完美继承 Ares Genesis 的顶级时间轴配置
    private static final float TIME_ENTER = 700f;
    private static final float TIME_HOLD  = 2900f;
    private static final float TIME_EXIT  = 500f;

    private static final float MAX_DRIFT = 3.0f;
    private static final float FLY_DISTANCE = 4.0f;

    public static void trigger(QuestDefinition quest, SplashType type, ResourceLocation texture) {
        if (quest != null && texture != null) {
            activeQuest = quest;
            activeType = type;
            activeTexture = texture;
            startTime = Util.getMillis();
        }
    }

    public static boolean isActive() {
        return activeQuest != null;
    }

    public static void render(GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (activeQuest == null || activeTexture == null) return;

        long elapsed = Util.getMillis() - startTime;
        float totalTime = TIME_ENTER + TIME_HOLD + TIME_EXIT;

        if (elapsed >= totalTime) {
            activeQuest = null;
            return;
        }

        // 采用 16:9 标准宽屏立绘比例
        int frameW = 400;
        int frameH = 225;

        // 动态缩放：占据屏幕高度的 70% (黄金视觉比例)
        float finalScale = (screenHeight * 0.70f) / (float) frameH;

        Font font = Minecraft.getInstance().font;
        String titleStr = activeQuest.getDisplayName().getString();
        float textWidth = font.width(titleStr) * 1.45f;
        float targetLineWidth = Math.max(textWidth + 30, (frameW - 40) * finalScale);

        float alpha = 1f;

        // 【核心修改】精准锚定屏幕绝对正中央
        float baseX = (screenWidth / 2f) - ((frameW * finalScale) / 2f);
        float currentY = (screenHeight / 2f) - ((frameH * finalScale) / 2f);

        float currentX;

        float scaleAnim = finalScale;
        float revealProgress = 1.0f;
        float wipeProgress = 0.0f;

        float textFade = 1f;
        float currentLineWidth = targetLineWidth;
        float textDriftX = 0f;

        float actualFlyDist = FLY_DISTANCE * finalScale;
        float actualDrift = MAX_DRIFT * finalScale;

        // 1:1 移植 Genesis 的数学缓动曲线
        if (elapsed < TIME_ENTER) {
            float t = elapsed / TIME_ENTER;
            t = Math.min(1.0f, t);
            float easeOut = (float)(1.0 - Math.pow(1.0 - t, 5)); // Ease Out Quint

            revealProgress = easeOut;
            alpha = easeOut;
            scaleAnim = finalScale * (1.10f - 0.10f * easeOut); // 轻微缩放推镜
            currentX = baseX - (1.0f - easeOut) * actualFlyDist * 2f;

            textFade = easeOut;
            currentLineWidth *= easeOut;
            textDriftX = -(1.0f - easeOut) * 25f;

        } else if (elapsed < TIME_ENTER + TIME_HOLD) {
            float t = (elapsed - TIME_ENTER) / TIME_HOLD;
            t = Math.min(1.0f, t);
            float driftEase = (float) Math.sin(t * (Math.PI / 2)); // 丝滑正弦漂移

            currentX = baseX + (driftEase * actualDrift);
            scaleAnim = finalScale;

        } else {
            float t = (elapsed - TIME_ENTER - TIME_HOLD) / TIME_EXIT;
            t = Math.min(1.0f, t);
            float easeIn = (float)Math.pow(t, 4.0); // Ease In Quart

            wipeProgress = easeIn;
            float startExitX = baseX + actualDrift;
            currentX = startExitX + (easeIn * actualFlyDist * 1.5f);

            alpha = 1.0f - (float)Math.pow(t, 8.0);
            textFade = 1f - easeIn;
            textDriftX = easeIn * 40f;
        }

        if (revealProgress <= 0.001f || wipeProgress >= 0.999f) return;

        int themeColor = activeType == SplashType.QUEST_FAILED ? 0xFF1111 : activeQuest.getVisualConfig().getThemeColor();

        // 丝滑裁切 (Scissor Wipe) 逻辑
        float absoluteRightEdge = currentX + (frameW * scaleAnim);
        float lineRightEdge = currentX + (20 * scaleAnim) + targetLineWidth + 50f;
        float maxDrawWidth = Math.max(absoluteRightEdge, lineRightEdge);

        int scX1 = (int)(currentX - 50);
        int scX2 = (int)(maxDrawWidth + 50);

        if (elapsed < TIME_ENTER) {
            scX2 = (int)(currentX + (maxDrawWidth - currentX) * revealProgress);
        } else if (elapsed >= TIME_ENTER + TIME_HOLD) {
            scX2 = (int)(currentX + (maxDrawWidth - currentX) * (1.0f - wipeProgress));
        }

        // ==========================================
        // 0. 极其轻微的全局暗化 (仅仅 20% 的透明度)
        // ==========================================
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 4000f);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int globalDimAlpha = (int)(alpha * 100); // 极限克制
        if (globalDimAlpha > 0) {
            guiGraphics.fill(0, 0, screenWidth, screenHeight, globalDimAlpha << 24);
        }

        // ==========================================
        // 1. 立绘渲染层
        // ==========================================
        guiGraphics.enableScissor(scX1, -1000, scX2, 10000);

        guiGraphics.pose().pushPose();
        float scaleOffsetW = (frameW * scaleAnim - frameW * finalScale) / 2f;
        float scaleOffsetH = (frameH * scaleAnim - frameH * finalScale) / 2f;

        guiGraphics.pose().translate(currentX - scaleOffsetW, currentY - scaleOffsetH, 0);
        guiGraphics.pose().scale(scaleAnim, scaleAnim, 1f);

        // 立绘本身
        guiGraphics.setColor(1f, 1f, 1f, alpha);
        guiGraphics.blit(activeTexture, 0, 0, 0, 0, frameW, frameH, frameW, frameH);
        guiGraphics.setColor(1f, 1f, 1f, 1f);

        // 雕花：左侧的主题色机能线
        int borderAlpha = Math.max(0, Math.min(255, (int)(alpha * 255)));
        int borderColor = (borderAlpha << 24) | (themeColor & 0xFFFFFF);
        guiGraphics.fill(0, 0, 3, frameH, borderColor);

        // 雕花：四个角的高级感边框折角 (Bracket Accents)
        int decColor = (borderAlpha << 24) | 0xFFFFFF;
        guiGraphics.fill(0, 0, 20, 2, decColor); // 左上横
        guiGraphics.fill(0, 0, 2, 20, decColor); // 左上竖
        guiGraphics.fill(frameW - 20, frameH - 2, frameW, frameH, decColor); // 右下横
        guiGraphics.fill(frameW - 2, frameH - 20, frameW, frameH, decColor); // 右下竖

        guiGraphics.pose().popPose();

        // ==========================================
        // 2. 排版与装饰层 (贴紧立绘左下角，带视差漂移)
        // ==========================================
        guiGraphics.pose().pushPose();

        float textStartX = currentX + (20 * finalScale) + textDriftX;
        float textStartY = currentY + (frameH * finalScale) - 65; // 锚定在立绘内部靠下

        guiGraphics.pose().translate(textStartX, textStartY, 50);

        int baseAlpha = Math.max(0, Math.min(255, (int)(textFade * 255)));
        int titleColor = (baseAlpha << 24) | 0xFFFFFF;
        int subColor = (baseAlpha << 24) | 0xAAAAAA;
        int statusColor = (baseAlpha << 24) | (themeColor & 0xFFFFFF);
        int lineColor = (baseAlpha << 24) | (themeColor & 0xFFFFFF);

        if (baseAlpha > 5) {
            // [雕花] 顶层小字：系统分类
            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(0.85f, 0.85f, 1f);
            guiGraphics.drawString(font, "SYS.ARC_QUEST // " + activeQuest.getCategory().name(), 0, -22, subColor, true);
            guiGraphics.pose().popPose();

            // [雕花] 状态高亮：ACCEPTED / COMPLETED / FAILED
            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(1.1f, 1.1f, 1f);
            guiGraphics.drawString(font, activeType.name().replace("_", " "), 0, -8, statusColor, true);
            guiGraphics.pose().popPose();

            // 主标题：任务名称
            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(1.45f, 1.45f, 1f);
            guiGraphics.drawString(font, titleStr, 0, 6, titleColor, true);
            guiGraphics.pose().popPose();
        }

        // [雕花] 底层机能拉线与游标
        currentLineWidth = Math.max(0f, currentLineWidth);
        int lineY = 28;
        if (baseAlpha > 5 && currentLineWidth > 0) {
            guiGraphics.fill(0, lineY, (int)currentLineWidth, lineY + 1, lineColor);
            if (currentLineWidth > 5) {
                // 末尾的小方块游标
                guiGraphics.fill((int)currentLineWidth, lineY - 2, (int)currentLineWidth + 4, lineY + 3, lineColor);
            }
        }

        guiGraphics.pose().popPose();

        // 恢复渲染管线状态
        RenderSystem.disableBlend();
        guiGraphics.disableScissor();
        RenderSystem.enableDepthTest();
        guiGraphics.pose().popPose();
    }
}