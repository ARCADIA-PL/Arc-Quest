package org.arcadia.arc_quest.client.hud.guide;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.api.GuideVisualConfig;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class GuideSplashRenderer {

    private static final long ENTER_MS = 350L;
    private static final long HOLD_MS = 3_200L;
    private static final long EXIT_MS = 450L;
    private static final Deque<ResourceLocation> QUEUE = new ArrayDeque<>();
    private static ResourceLocation activeGuideId;
    private static long startedAt;

    private GuideSplashRenderer() {
    }

    public static void trigger(ResourceLocation guideId) {
        GuideDefinition guide = GuideRegistry.get(guideId);
        if (guide == null || !guide.getVisualConfig().shouldShowUnlockPopup()) return;
        if (guideId.equals(activeGuideId) || QUEUE.contains(guideId)) return;
        if (activeGuideId == null) start(guideId);
        else if (QUEUE.size() < 8) QUEUE.addLast(guideId);
    }

    public static boolean isActive() {
        return activeGuideId != null;
    }

    public static void clear() {
        activeGuideId = null;
        QUEUE.clear();
        startedAt = 0L;
    }

    public static void render(GuiGraphics graphics, int screenWidth) {
        GuideDefinition guide = activeGuideId == null ? null : GuideRegistry.get(activeGuideId);
        if (guide == null) {
            advance();
            return;
        }

        long now = Util.getMillis();
        if (startedAt == 0L) startedAt = now;
        long elapsed = now - startedAt;
        long total = ENTER_MS + HOLD_MS + EXIT_MS;
        if (elapsed >= total) {
            advance();
            return;
        }

        float alpha;
        float offsetY;
        if (elapsed < ENTER_MS) {
            float t = HudAnimUtil.easeOutCubic(elapsed / (float) ENTER_MS);
            alpha = t;
            offsetY = -18f * (1f - t);
        } else if (elapsed < ENTER_MS + HOLD_MS) {
            alpha = 1f;
            offsetY = 0f;
        } else {
            float t = HudAnimUtil.easeInCubic((elapsed - ENTER_MS - HOLD_MS) / (float) EXIT_MS);
            alpha = 1f - t;
            offsetY = -10f * t;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int width = Math.min(310, Math.max(210, screenWidth - 24));
        int height = 82;
        int x = (screenWidth - width) / 2;
        int y = 14 + Math.round(offsetY);
        int theme = guide.getCategory().getThemeColor();
        int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
        GuideVisualConfig visual = guide.getVisualConfig();

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 4500);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        ItemStack icon = visual.getIcon();
        if (!visual.shouldRenderPopupBackground()) {
            if (!icon.isEmpty()) {
                RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
                graphics.pose().pushPose();
                graphics.pose().translate(screenWidth / 2f - GuideConstants.INTRO_ICON_SIZE / 2f,
                        y + (height - GuideConstants.INTRO_ICON_SIZE) / 2f, 0);
                graphics.pose().scale(GuideConstants.INTRO_ICON_SCALE, GuideConstants.INTRO_ICON_SCALE, 1f);
                graphics.renderItem(icon, 0, 0);
                graphics.pose().popPose();
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            }
            RenderSystem.disableBlend();
            graphics.pose().popPose();
            return;
        }

        graphics.fill(x, y, x + width, y + height, HudAnimUtil.withAlpha(0x090D12, (int) (220 * alpha)));
        graphics.setColor(1f, 1f, 1f, alpha * 0.58f);
        graphics.blit(visual.getPopupBackground(), x, y, 0, 0, width, height, width, height);
        graphics.setColor(1f, 1f, 1f, 1f);
        HudAnimUtil.drawFrame(graphics, x, y, width, height,
                HudAnimUtil.withAlpha(0x000000, 0), HudAnimUtil.withAlpha(0x7C8794, (int) (150 * alpha)));
        graphics.fill(x, y, x + 4, y + height, HudAnimUtil.withAlpha(theme, a));
        graphics.fill(x + 4, y + height - 2, x + width, y + height, HudAnimUtil.withAlpha(theme, (int) (100 * alpha)));

        int contentX = x + 16;
        if (!icon.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(x + 16, y + 22, 0);
            graphics.pose().scale(2.25f, 2.25f, 1f);
            graphics.renderItem(icon, 0, 0);
            graphics.pose().popPose();
            contentX = x + 66;
        }

        graphics.drawString(font, Component.translatable("arc_quest.guide.splash.status"),
                contentX, y + 12, HudAnimUtil.withAlpha(theme, a), true);
        int textWidth = Math.max(30, x + width - 12 - contentX);
        List<FormattedCharSequence> titleLines = font.split(guide.getTitle(), textWidth);
        if (!titleLines.isEmpty()) {
            graphics.drawString(font, titleLines.getFirst(), contentX, y + 28,
                    HudAnimUtil.withAlpha(0xFFFFFF, a), true);
        }
        List<FormattedCharSequence> summaryLines = font.split(guide.getSummary(), textWidth);
        for (int i = 0; i < Math.min(2, summaryLines.size()); i++) {
            graphics.drawString(font, summaryLines.get(i), contentX, y + 45 + i * (font.lineHeight + 1),
                    HudAnimUtil.withAlpha(0xC6CDD5, a), false);
        }

        RenderSystem.disableBlend();
        graphics.pose().popPose();
    }

    private static void start(ResourceLocation guideId) {
        activeGuideId = guideId;
        startedAt = 0L;
    }

    private static void advance() {
        ResourceLocation next = QUEUE.pollFirst();
        if (next == null) clear();
        else start(next);
    }
}
