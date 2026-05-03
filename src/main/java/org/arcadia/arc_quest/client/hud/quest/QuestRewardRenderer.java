package org.arcadia.arc_quest.client.hud.quest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.quest.api.IReward;
import org.arcadia.arc_quest.quest.reward.ItemReward;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 在任务日志详情面板中渲染奖励列表。
 * 支持 ItemReward（物品图标+数量）、FlagReward、VariableReward、CommandReward。
 * <p>
 * 调用方负责 pose 的 pushPose/popPose，此类在调用方的本地坐标系内绘制。
 */
public final class QuestRewardRenderer {

    private static final int ICON_SIZE = 12;
    private static final int ROW_HEIGHT = 16;
    private static final int ICON_TEXT_GAP = 3;
    private static final Map<String, RewardRenderCache> CACHE = new HashMap<>();

    private static class RewardRenderCache {
        ItemStack stack = ItemStack.EMPTY;
        String label;
        String prefixedText;
        int lastMaxWidth = Integer.MIN_VALUE;
        String safeText;
    }

    private QuestRewardRenderer() {
    }

    /**
     * 渲染奖励列表，返回消耗的总高度（像素）。
     *
     * @param g        渲染上下文（本地坐标系）
     * @param rewards  奖励列表
     * @param maxWidth 可用宽度
     * @param alpha    整体透明度 (0-255)
     * @return 总渲染高度
     */
    public static int render(GuiGraphics g, List<IReward> rewards, int maxWidth, int alpha) {
        return render(g, rewards, maxWidth, alpha, ICON_SIZE);
    }

    /**
     * 渲染奖励列表（支持自定义图标尺寸）。
     *
     * @param g        渲染上下文（本地坐标系）
     * @param rewards  奖励列表
     * @param maxWidth 可用宽度
     * @param alpha    整体透明度 (0-255)
     * @param iconSize 物品图标尺寸（像素），推荐范围 8-32
     * @return 总渲染高度
     */
    public static int render(GuiGraphics g, List<IReward> rewards, int maxWidth, int alpha, int iconSize) {
        if (rewards.isEmpty() || alpha < 4) return 0;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int totalY = 0;

        for (IReward reward : rewards) {
            if (reward instanceof ItemReward ir) {
                totalY += renderItemReward(g, ir, font, maxWidth, alpha, totalY, iconSize);
            } else {
                totalY += renderTextReward(g, reward.describe(), font, maxWidth, alpha, totalY);
            }
        }
        return totalY;
    }

    private static int renderItemReward(GuiGraphics g, ItemReward ir, Font font,
                                        int maxWidth, int alpha, int offsetY) {
        return renderItemReward(g, ir, font, maxWidth, alpha, offsetY, ICON_SIZE);
    }

    private static int renderItemReward(GuiGraphics g, ItemReward ir, Font font,
                                        int maxWidth, int alpha, int offsetY, int iconSize) {
        RewardRenderCache cache = getItemCache(ir);
        if (cache.stack.isEmpty()) {
            return renderTextReward(g, ir.describe(), font, maxWidth, alpha, offsetY);
        }

        g.pose().pushPose();
        g.pose().translate(0, offsetY, 0);

        g.pose().pushPose();
        float scale = iconSize / 16f;
        g.pose().scale(scale, scale, 1f);
        g.renderItem(cache.stack, 0, 0);
        g.pose().popPose();

        String safe = safeText(cache, cache.label, maxWidth - iconSize - ICON_TEXT_GAP - 2, font);
        g.drawString(font, safe, iconSize + ICON_TEXT_GAP, (ROW_HEIGHT - font.lineHeight) / 2, HudAnimUtil.withAlpha(0xEEEEEE, alpha), false);
        g.pose().popPose();

        return ROW_HEIGHT;
    }

    private static int renderTextReward(GuiGraphics g, String text, Font font,
                                        int maxWidth, int alpha, int offsetY) {
        RewardRenderCache cache = getTextCache(text);
        String safe = safeText(cache, cache.prefixedText, maxWidth, font);
        g.drawString(font, safe, 0, offsetY + (ROW_HEIGHT - font.lineHeight) / 2, HudAnimUtil.withAlpha(0xDDCCFF, alpha), false);
        return ROW_HEIGHT;
    }

    private static RewardRenderCache getItemCache(ItemReward reward) {
        Item item = reward.getItem();
        int count = reward.getCount();
        if (item == null || count < 1) return new RewardRenderCache();
        String key = "item:" + Item.getId(item) + ":" + count;
        return CACHE.computeIfAbsent(key, k -> {
            RewardRenderCache cache = new RewardRenderCache();
            cache.stack = new ItemStack(item, count);
            cache.label = cache.stack.getHoverName().getString() + (count > 1 ? " ×" + count : "");
            return cache;
        });
    }

    private static RewardRenderCache getTextCache(String text) {
        return CACHE.computeIfAbsent("text:" + text, k -> {
            RewardRenderCache cache = new RewardRenderCache();
            cache.prefixedText = rewardPrefix(text) + text;
            return cache;
        });
    }

    private static String safeText(RewardRenderCache cache, String text, int maxWidth, Font font) {
        if (cache.lastMaxWidth != maxWidth) {
            cache.safeText = font.plainSubstrByWidth(text, maxWidth);
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

    /**
     * 计算渲染奖励列表所需的总高度（不渲染，仅计算布局）。
     */
    public static int measureHeight(List<IReward> rewards) {
        return rewards.size() * ROW_HEIGHT;
    }
}
