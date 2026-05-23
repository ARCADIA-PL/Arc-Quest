package org.arcadia.arc_quest.client.hud.quest.splash;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.SplashType;

import java.util.Locale;

public class QuestSplashRenderer {
    private static final float TIME_ENTER = 700f;
    private static final float TIME_HOLD = 2900f;
    private static final float TIME_EXIT = 500f;
    private static final float MAX_DRIFT = 3.0f;
    private static final float FLY_DISTANCE = 4.0f;

    private static QuestDefinition activeQuest = null;
    private static SplashType activeType = null;
    private static ResourceLocation activeTexture = null;
    private static State currentState = State.ENTER;
    private static long startTime = 0;
    private static long exitStartTime = 0;
    private static float skipStartX = 0f;
    private static float lastRenderX = 0f;

    public static void trigger(QuestDefinition quest, SplashType type, ResourceLocation texture) {
        if (quest != null && texture != null) {
            activeQuest = quest;
            activeType = type;
            activeTexture = texture;
            startTime = Util.getMillis();

            // 重置状态
            currentState = State.ENTER;
            exitStartTime = 0;
            skipStartX = 0f;
            lastRenderX = 0f;
        }
    }

    public static boolean isActive() {
        return activeQuest != null;
    }

    public static boolean mouseClicked() {
        if (!isActive() || currentState == State.EXIT) return false;

        if (currentState == State.ENTER) return false;

        currentState = State.EXIT;
        exitStartTime = Util.getMillis();
        skipStartX = lastRenderX;

        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.5f));
        return true;
    }

    public static void render(GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (activeQuest == null || activeTexture == null) return;

        long now = Util.getMillis();
        long elapsed = now - startTime;

        if (currentState == State.ENTER && elapsed >= TIME_ENTER) {
            currentState = State.HOLD;
        }

        if (currentState == State.HOLD && elapsed >= TIME_ENTER + TIME_HOLD) {
            currentState = State.EXIT;
            exitStartTime = now;
            skipStartX = lastRenderX;
        }

        if (currentState == State.EXIT) {
            long exitElapsed = now - exitStartTime;
            if (exitElapsed >= TIME_EXIT) {
                activeQuest = null;
                return;
            }
        }

        int frameW = 400;
        int frameH = 225;

        float finalScale = (screenHeight * 0.70f) / (float) frameH;

        Font font = Minecraft.getInstance().font;
        String titleStr = activeQuest.getDisplayName().getString();
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

        float actualFlyDist = FLY_DISTANCE * finalScale;
        float actualDrift = MAX_DRIFT * finalScale;

        if (currentState == State.ENTER) {
            float t = elapsed / TIME_ENTER;
            t = Math.min(1.0f, t);
            float easeOut = (float) (1.0 - Math.pow(1.0 - t, 5));

            revealProgress = easeOut;
            alpha = easeOut;
            scaleAnim = finalScale * (1.10f - 0.10f * easeOut);
            currentX = baseX - (1.0f - easeOut) * actualFlyDist * 2f;

            textFade = easeOut;
            currentLineWidth *= easeOut;
            textDriftX = -(1.0f - easeOut) * 25f;

        } else if (currentState == State.HOLD) {
            float t = (elapsed - TIME_ENTER) / TIME_HOLD;
            t = Math.min(1.0f, t);
            float driftEase = (float) Math.sin(t * (Math.PI / 2));

            currentX = baseX + (driftEase * actualDrift);
            scaleAnim = finalScale;

        } else if (currentState == State.EXIT) {
            float t = (now - exitStartTime) / TIME_EXIT;
            t = Math.min(1.0f, t);
            float easeIn = (float) Math.pow(t, 4.0);

            wipeProgress = easeIn;
            currentX = skipStartX + (easeIn * actualFlyDist * 1.5f);

            alpha = 1.0f - (float) Math.pow(t, 8.0);
            textFade = 1f - easeIn;
            textDriftX = easeIn * 40f;
        }

        lastRenderX = currentX;

        if (revealProgress <= 0.001f || wipeProgress >= 0.999f) return;

        int themeColor = activeType == SplashType.QUEST_FAILED ? 0xFF1111 : activeQuest.getVisualConfig().getThemeColor();

        float absoluteRightEdge = currentX + (frameW * scaleAnim);
        float lineRightEdge = currentX + (20 * scaleAnim) + targetLineWidth + 50f;
        float maxDrawWidth = Math.max(absoluteRightEdge, lineRightEdge);

        int scX1 = (int) (currentX - 50);
        int scX2 = (int) (maxDrawWidth + 50);

        if (currentState == State.ENTER) {
            scX2 = (int) (currentX + (maxDrawWidth - currentX) * revealProgress);
        } else if (currentState == State.EXIT) {
            scX2 = (int) (currentX + (maxDrawWidth - currentX) * (1.0f - wipeProgress));
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 4000f);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int globalDimAlpha = (int) (alpha * 100);
        if (globalDimAlpha > 0) {
            guiGraphics.fill(0, 0, screenWidth, screenHeight, globalDimAlpha << 24);
        }

        guiGraphics.enableScissor(scX1, -1000, scX2, 10000);

        guiGraphics.pose().pushPose();
        float scaleOffsetW = (frameW * scaleAnim - frameW * finalScale) / 2f;
        float scaleOffsetH = (frameH * scaleAnim - frameH * finalScale) / 2f;

        guiGraphics.pose().translate(currentX - scaleOffsetW, currentY - scaleOffsetH, 0);
        guiGraphics.pose().scale(scaleAnim, scaleAnim, 1f);

        guiGraphics.setColor(1f, 1f, 1f, alpha);
        guiGraphics.blit(activeTexture, 0, 0, 0, 0, frameW, frameH, frameW, frameH);
        guiGraphics.setColor(1f, 1f, 1f, 1f);

        int borderAlpha = Math.max(0, Math.min(255, (int) (alpha * 255)));
        int borderColor = (borderAlpha << 24) | (themeColor & 0xFFFFFF);
        guiGraphics.fill(0, 0, 3, frameH, borderColor);

        int decColor = (borderAlpha << 24) | 0xFFFFFF;
        guiGraphics.fill(0, 0, 20, 2, decColor); // 左上横
        guiGraphics.fill(0, 0, 2, 20, decColor); // 左上竖
        guiGraphics.fill(frameW - 20, frameH - 2, frameW, frameH, decColor); // 右下横
        guiGraphics.fill(frameW - 2, frameH - 20, frameW, frameH, decColor); // 右下竖

        guiGraphics.pose().popPose();

        guiGraphics.pose().pushPose();

        float textStartX = currentX + (20 * finalScale) + textDriftX;
        float textStartY = currentY + (frameH * finalScale) - 65;

        guiGraphics.pose().translate(textStartX, textStartY, 50);

        int baseAlpha = Math.max(0, Math.min(255, (int) (textFade * 255)));
        int titleColor = (baseAlpha << 24) | 0xFFFFFF;
        int subColor = (baseAlpha << 24) | 0xAAAAAA;
        int statusColor = (baseAlpha << 24) | (themeColor & 0xFFFFFF);
        int lineColor = (baseAlpha << 24) | (themeColor & 0xFFFFFF);

        if (baseAlpha > 5) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(0.85f, 0.85f, 1f);
            guiGraphics.drawString(font, "SYS.ARC_QUEST // " + activeQuest.getCategory().getPathToken().toUpperCase(Locale.ROOT), 0, -22, subColor, true);
            guiGraphics.pose().popPose();

            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(1.1f, 1.1f, 1f);
            guiGraphics.drawString(font, activeType.name().replace("_", " "), 0, -8, statusColor, true);
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

            if (currentState == State.HOLD) {
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

        // 恢复渲染管线状态
        RenderSystem.disableBlend();
        guiGraphics.disableScissor();
        RenderSystem.enableDepthTest();
        guiGraphics.pose().popPose();
    }

    private enum State {ENTER, HOLD, EXIT}
}