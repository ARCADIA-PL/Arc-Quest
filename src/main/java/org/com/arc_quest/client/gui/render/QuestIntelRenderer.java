package org.com.arc_quest.client.gui.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.registries.ForgeRegistries;
import org.com.arc_quest.client.gui.HudAnimUtil;
import org.com.arc_quest.client.gui.HudRenderUtil;
import org.com.arc_quest.client.gui.quest.QuestEntityModelRenderer;

public class QuestIntelRenderer {
    private static ResourceLocation activeEntityId = null;
    private static int themeColor = 0x4FC3F7;
    private static float animProgress = 0f;
    private static boolean isClosing = false;
    private static long lastTime = 0;

    public static void trigger(ResourceLocation entityId, int theme) {
        activeEntityId = entityId;
        themeColor = theme;
        animProgress = 0f;
        isClosing = false;
        lastTime = System.currentTimeMillis();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, 1.0F));
    }

    public static void dismiss() {
        if (!isClosing && activeEntityId != null) {
            isClosing = true;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_OUT, 1.0F));
        }
    }

    public static boolean isActive() {
        return activeEntityId != null;
    }

    public static void render(GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        if (activeEntityId == null) return;

        long now = System.currentTimeMillis();
        float dt = (now - lastTime) / 1000f;
        lastTime = now;
        if (dt > 0.1f) dt = 0.1f;

        if (isClosing) {
            animProgress -= dt * 6f; // 极速收起
            if (animProgress <= 0) {
                activeEntityId = null;
                return;
            }
        } else {
            animProgress += (1f - animProgress) * Math.min(1f, dt * 12f);
        }

        if (animProgress < 0.01f) return;

        float ease = HudAnimUtil.easeOutCubic(animProgress);
        int alpha = (int) (255 * ease);

        int frameW = 320;
        int frameH = 400;

        // 【致命修复 1】：强行抬高 Z 轴，彻底压盖底层 JournalScreen！
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);

        // 绘制全屏黑幕遮罩
        g.fill(0, 0, screenWidth, screenHeight, (int) (180 * ease) << 24);

        float baseX = (screenWidth / 2f) - (frameW / 2f);
        float baseY = (screenHeight / 2f) - (frameH / 2f);

        float currentX = baseX - (1f - ease) * 100f;
        int scX1 = (int) currentX - 50;
        int scX2 = (int) (currentX + (frameW * ease) + 50);

        // 【致命修复 2】：Scissor 坐标严禁出现负数和超界，否则 OpenGL 高度直接归零！
        g.enableScissor(Math.max(0, scX1), 0, Math.min(screenWidth, scX2), screenHeight);

        g.pose().pushPose();
        g.pose().translate(currentX, baseY, 0);

        int bgAlpha = (int) (0.85f * alpha);
        g.fill(0, 0, frameW, frameH, (bgAlpha << 24) | 0x0A0B10);

        HudRenderUtil.drawCyberneticEdge(g, 0, 0, frameH, themeColor, alpha);

        int decColor = (alpha << 24) | 0xFFFFFF;
        g.fill(0, 0, 20, 2, decColor);
        g.fill(0, 0, 2, 20, decColor);
        g.fill(frameW - 20, frameH - 2, frameW, frameH, decColor);
        g.fill(frameW - 2, frameH - 20, frameW, frameH, decColor);

        Font font = Minecraft.getInstance().font;

        g.pose().pushPose();
        g.pose().scale(0.85f, 0.85f, 1f);
        g.drawString(font, "SYS.ARC_QUEST // TARGET INTEL", 16, 16, HudAnimUtil.withAlpha(0x888888, alpha), true);
        g.pose().popPose();

        String eName = "UNKNOWN TARGET";
        if (ForgeRegistries.ENTITY_TYPES.containsKey(activeEntityId)) {
            eName = ForgeRegistries.ENTITY_TYPES.getValue(activeEntityId).getDescription().getString();
        }

        g.pose().pushPose();
        g.pose().scale(1.4f, 1.4f, 1f);
        g.drawString(font, eName, (int) (14 / 1.4f), (int) (32 / 1.4f), HudAnimUtil.withAlpha(0xFFFFFF, alpha), true);
        g.pose().popPose();

        int lineAlpha = (int) (alpha * 0.5f);
        g.fill(12, 48, 180, 49, (lineAlpha << 24) | (themeColor & 0xFFFFFF));

        g.drawCenteredString(font, "CLICK ANYWHERE TO CLOSE", frameW / 2, frameH - 20, HudAnimUtil.withAlpha(0x555555, alpha));

        // 渲染实体
        QuestEntityModelRenderer.render(g, activeEntityId, frameW / 2, frameH - 60, 130, ease);

        g.pose().popPose();
        g.disableScissor();
        g.pose().popPose(); // 弹出最高级 Z 轴
    }
}