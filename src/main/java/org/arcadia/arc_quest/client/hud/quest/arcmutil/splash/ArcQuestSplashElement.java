package org.arcadia.arc_quest.client.hud.quest.arcmutil.splash;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;

public class ArcQuestSplashElement extends ArcGuiElement {
    public ArcQuestSplashElement() {
        super(0, 0, ArcQuestSplashViewModel.FRAME_W, ArcQuestSplashViewModel.FRAME_H);
    }

    @Override
    public void draw(GuiGraphics guiGraphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        ArcQuestSplashViewModel splash = ArcQuestSplashManager.active();
        if (splash == null || splash.quest == null || splash.texture == null) return;

        long now = context.nowMs();
        if (!splash.update(now)) {
            ArcQuestSplashManager.clear(splash);
            return;
        }

        long elapsed = now - splash.startTime;
        int frameW = ArcQuestSplashViewModel.FRAME_W;
        int frameH = ArcQuestSplashViewModel.FRAME_H;
        int screenWidth = context.screenWidth();
        int screenHeight = context.screenHeight();

        float finalScale = (screenHeight * 0.70f) / (float) frameH;

        Font font = Minecraft.getInstance().font;
        String titleStr = splash.quest.getDisplayName().getString();
        float textWidth = font.width(titleStr) * 1.45f;
        float targetLineWidth = Math.max(textWidth + 30, (frameW - 40) * finalScale);

        float alpha = 1f;

        float baseX = (screenWidth / 2f) - ((frameW * finalScale) / 2f);
        float currentY = (screenHeight / 2f) - ((frameH * finalScale) / 2f);

        float currentX = baseX;
        float scaleAnim = finalScale;
        float revealProgress = 1.0f;
        float wipeProgress = 0.0f;
        float textFade = 1f;
        float currentLineWidth = targetLineWidth;
        float textDriftX = 0f;

        float actualFlyDist = ArcQuestSplashViewModel.FLY_DISTANCE * finalScale;
        float actualDrift = ArcQuestSplashViewModel.MAX_DRIFT * finalScale;

        if (splash.state == ArcQuestSplashViewModel.State.ENTER) {
            float t = elapsed / ArcQuestSplashViewModel.TIME_ENTER;
            t = Math.min(1.0f, t);
            float easeOut = (float) (1.0 - Math.pow(1.0 - t, 5));

            revealProgress = easeOut;
            alpha = easeOut;
            scaleAnim = finalScale * (1.10f - 0.10f * easeOut);
            currentX = baseX - (1.0f - easeOut) * actualFlyDist * 2f;

            textFade = easeOut;
            currentLineWidth *= easeOut;
            textDriftX = -(1.0f - easeOut) * 25f;

        } else if (splash.state == ArcQuestSplashViewModel.State.HOLD) {
            float t = (elapsed - ArcQuestSplashViewModel.TIME_ENTER) / ArcQuestSplashViewModel.TIME_HOLD;
            t = Math.min(1.0f, t);
            float driftEase = (float) Math.sin(t * (Math.PI / 2));

            currentX = baseX + (driftEase * actualDrift);
            scaleAnim = finalScale;

        } else if (splash.state == ArcQuestSplashViewModel.State.EXIT) {
            float t = (now - splash.exitStartTime) / ArcQuestSplashViewModel.TIME_EXIT;
            t = Math.min(1.0f, t);
            float easeIn = (float) Math.pow(t, 4.0);

            wipeProgress = easeIn;
            currentX = splash.skipStartX + (easeIn * actualFlyDist * 1.5f);

            alpha = 1.0f - (float) Math.pow(t, 8.0);
            textFade = 1f - easeIn;
            textDriftX = easeIn * 40f;
        }

        splash.lastRenderX = currentX;

        if (revealProgress <= 0.001f || wipeProgress >= 0.999f) return;

        int themeColor = splash.themeColor();

        float absoluteRightEdge = currentX + (frameW * scaleAnim);
        float lineRightEdge = currentX + (20 * scaleAnim) + targetLineWidth + 50f;
        float maxDrawWidth = Math.max(absoluteRightEdge, lineRightEdge);

        int scX1 = (int) (currentX - 50);
        int scX2 = (int) (maxDrawWidth + 50);

        if (splash.state == ArcQuestSplashViewModel.State.ENTER) {
            scX2 = (int) (currentX + (maxDrawWidth - currentX) * revealProgress);
        } else if (splash.state == ArcQuestSplashViewModel.State.EXIT) {
            scX2 = (int) (currentX + (maxDrawWidth - currentX) * (1.0f - wipeProgress));
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 4000f);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int globalDimAlpha = (int) (alpha * 100 * inheritedOpacity);
        if (globalDimAlpha > 0) {
            guiGraphics.fill(0, 0, screenWidth, screenHeight, globalDimAlpha << 24);
        }

        guiGraphics.enableScissor(scX1, -1000, scX2, 10000);

        guiGraphics.pose().pushPose();
        float scaleOffsetW = (frameW * scaleAnim - frameW * finalScale) / 2f;
        float scaleOffsetH = (frameH * scaleAnim - frameH * finalScale) / 2f;

        guiGraphics.pose().translate(currentX - scaleOffsetW, currentY - scaleOffsetH, 0);
        guiGraphics.pose().scale(scaleAnim, scaleAnim, 1f);

        guiGraphics.setColor(1f, 1f, 1f, alpha * inheritedOpacity);
        guiGraphics.blit(splash.texture, 0, 0, 0, 0, frameW, frameH, frameW, frameH);
        guiGraphics.setColor(1f, 1f, 1f, 1f);

        int borderAlpha = Math.max(0, Math.min(255, (int) (alpha * inheritedOpacity * 255)));
        int borderColor = (borderAlpha << 24) | (themeColor & 0xFFFFFF);
        guiGraphics.fill(0, 0, 3, frameH, borderColor);

        int decColor = (borderAlpha << 24) | 0xFFFFFF;
        guiGraphics.fill(0, 0, 20, 2, decColor);
        guiGraphics.fill(0, 0, 2, 20, decColor);
        guiGraphics.fill(frameW - 20, frameH - 2, frameW, frameH, decColor);
        guiGraphics.fill(frameW - 2, frameH - 20, frameW, frameH, decColor);

        guiGraphics.pose().popPose();

        guiGraphics.pose().pushPose();

        float textStartX = currentX + (20 * finalScale) + textDriftX;
        float textStartY = currentY + (frameH * finalScale) - 65;

        guiGraphics.pose().translate(textStartX, textStartY, 50);

        int baseAlpha = Math.max(0, Math.min(255, (int) (textFade * inheritedOpacity * 255)));
        int titleColor = (baseAlpha << 24) | 0xFFFFFF;
        int subColor = (baseAlpha << 24) | 0xAAAAAA;
        int statusColor = (baseAlpha << 24) | (themeColor & 0xFFFFFF);
        int lineColor = (baseAlpha << 24) | (themeColor & 0xFFFFFF);

        if (baseAlpha > 5) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(0.85f, 0.85f, 1f);
            guiGraphics.drawString(font, "SYS.ARC_QUEST // " + splash.quest.getCategory().name(), 0, -22, subColor, true);
            guiGraphics.pose().popPose();

            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(1.1f, 1.1f, 1f);
            guiGraphics.drawString(font, splash.type.name().replace("_", " "), 0, -8, statusColor, true);
            guiGraphics.pose().popPose();

            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(1.45f, 1.45f, 1f);
            guiGraphics.drawString(font, titleStr, 0, 6, titleColor, true);
            guiGraphics.pose().popPose();
        }

        currentLineWidth = Math.max(0f, currentLineWidth);
        int lineY = 28;
        if (baseAlpha > 5 && currentLineWidth > 0) {
            guiGraphics.fill(0, lineY, (int) currentLineWidth, lineY + 1, lineColor);
            if (currentLineWidth > 5) {
                guiGraphics.fill((int) currentLineWidth, lineY - 2, (int) currentLineWidth + 4, lineY + 3, lineColor);
            }

            if (splash.state == ArcQuestSplashViewModel.State.HOLD) {
                float wave = (float) (Math.sin(now / 200.0) * 0.5 + 0.5);
                int blinkA = (int) (baseAlpha * (0.3f + 0.7f * wave));
                if (blinkA > 10) {
                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().scale(0.7f, 0.7f, 1f);
                    int promptColor = (blinkA << 24) | (themeColor & 0xFFFFFF);
                    guiGraphics.drawString(font, "[ CLICK TO DISMISS ]", 0, (int) (40 / 0.7f), promptColor, true);
                    guiGraphics.pose().popPose();
                }
            }
        }

        guiGraphics.pose().popPose();
        RenderSystem.disableBlend();
        guiGraphics.disableScissor();
        RenderSystem.enableDepthTest();
        guiGraphics.pose().popPose();
    }
}
