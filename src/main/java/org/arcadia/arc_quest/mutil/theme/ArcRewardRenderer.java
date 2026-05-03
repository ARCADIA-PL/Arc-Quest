package org.arcadia.arc_quest.mutil.theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.quest.api.IReward;
import org.arcadia.arc_quest.quest.reward.ItemReward;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ArcRewardRenderer {
    private static final int ROW_HEIGHT = 16;
    private static final int ICON_TEXT_GAP = 3;
    private static final Map<String, RewardRenderCache> CACHE = new HashMap<>();

    private ArcRewardRenderer() {
    }

    public static int render(GuiGraphics graphics, List<IReward> rewards, int maxWidth, int alpha) {
        return render(graphics, rewards, maxWidth, alpha, 12);
    }

    public static int render(GuiGraphics graphics, List<IReward> rewards, int maxWidth, int alpha, int iconSize) {
        if (rewards == null || rewards.isEmpty() || alpha < 4) return 0;
        Font font = Minecraft.getInstance().font;
        int totalY = 0;
        for (IReward reward : rewards) {
            if (reward instanceof ItemReward itemReward) {
                totalY += renderItemReward(graphics, itemReward, font, maxWidth, alpha, totalY, iconSize);
            } else if (reward != null) {
                totalY += renderTextReward(graphics, reward.describe(), font, maxWidth, alpha, totalY);
            }
        }
        return totalY;
    }

    public static int measureHeight(List<IReward> rewards) {
        return rewards == null ? 0 : rewards.size() * ROW_HEIGHT;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static int renderItemReward(GuiGraphics graphics, ItemReward reward, Font font, int maxWidth, int alpha, int offsetY, int iconSize) {
        RewardRenderCache cache = getItemCache(reward);
        if (cache.stack.isEmpty()) return renderTextReward(graphics, reward.describe(), font, maxWidth, alpha, offsetY);
        graphics.pose().pushPose();
        graphics.pose().translate(0, offsetY, 0);
        graphics.pose().pushPose();
        float scale = iconSize / 16f;
        graphics.pose().scale(scale, scale, 1f);
        graphics.renderItem(cache.stack, 0, 0);
        graphics.pose().popPose();
        String safe = safeText(cache, cache.label, maxWidth - iconSize - ICON_TEXT_GAP - 2, font);
        graphics.drawString(font, safe, iconSize + ICON_TEXT_GAP, (ROW_HEIGHT - font.lineHeight) / 2, withAlpha(0xEEEEEE, alpha), false);
        graphics.pose().popPose();
        return ROW_HEIGHT;
    }

    private static int renderTextReward(GuiGraphics graphics, String text, Font font, int maxWidth, int alpha, int offsetY) {
        RewardRenderCache cache = getTextCache(text);
        String safe = safeText(cache, cache.prefixedText, maxWidth, font);
        graphics.drawString(font, safe, 0, offsetY + (ROW_HEIGHT - font.lineHeight) / 2, withAlpha(0xDDCCFF, alpha), false);
        return ROW_HEIGHT;
    }

    private static RewardRenderCache getItemCache(ItemReward reward) {
        Item item = reward.getItem();
        int count = reward.getCount();
        if (item == null || count < 1) return new RewardRenderCache();
        String key = "item:" + Item.getId(item) + ":" + count;
        return CACHE.computeIfAbsent(key, ignored -> {
            RewardRenderCache cache = new RewardRenderCache();
            cache.stack = new ItemStack(item, count);
            cache.label = cache.stack.getHoverName().getString() + (count > 1 ? " ×" + count : "");
            return cache;
        });
    }

    private static RewardRenderCache getTextCache(String text) {
        String safeText = text == null ? "" : text;
        return CACHE.computeIfAbsent("text:" + safeText, ignored -> {
            RewardRenderCache cache = new RewardRenderCache();
            cache.prefixedText = rewardPrefix(safeText) + safeText;
            return cache;
        });
    }

    private static String safeText(RewardRenderCache cache, String text, int maxWidth, Font font) {
        if (cache.lastMaxWidth != maxWidth) {
            cache.safeText = font.plainSubstrByWidth(text == null ? "" : text, Math.max(1, maxWidth));
            cache.lastMaxWidth = maxWidth;
        }
        return cache.safeText;
    }

    private static String rewardPrefix(String describe) {
        if (describe.startsWith("SetFlag(") || describe.startsWith("ClearFlag(")) return "§b⚑ ";
        if (describe.startsWith("Var(")) return "§e⬆ ";
        if (describe.startsWith("Command(")) return "§7⌘ ";
        return "§a✦ ";
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static class RewardRenderCache {
        ItemStack stack = ItemStack.EMPTY;
        String label = "";
        String prefixedText = "";
        int lastMaxWidth = Integer.MIN_VALUE;
        String safeText = "";
    }
}
