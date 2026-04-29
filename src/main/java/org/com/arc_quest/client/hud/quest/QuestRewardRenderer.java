package org.com.arc_quest.client.hud.quest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.com.arc_quest.client.hud.HudAnimUtil;
import org.com.arc_quest.quest.api.IReward;
import org.com.arc_quest.quest.reward.ItemReward;

import java.util.List;

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

    private QuestRewardRenderer() {}

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
     * @param g         渲染上下文（本地坐标系）
     * @param rewards   奖励列表
     * @param maxWidth  可用宽度
     * @param alpha     整体透明度 (0-255)
     * @param iconSize  物品图标尺寸（像素），推荐范围 8-32
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
        // 直接使用 getter 方法获取物品和数量
        Item item = ir.getItem();
        int count = ir.getCount();
        
        if (item == null || count < 1) {
            return renderTextReward(g, ir.describe(), font, maxWidth, alpha, offsetY);
        }
        
        ItemStack stack = new ItemStack(item, count);
        
        g.pose().pushPose();
        g.pose().translate(0, offsetY, 0);
        
        // 物品图标渲染（自动适配任意分辨率纹理，缩放到 iconSize）
        // Minecraft 物品图标标准为 16x16，通过缩放矩阵适配到目标尺寸
        // 无论原始纹理是 16x16、32x32 还是 64x64，都会正确缩放
        g.pose().pushPose();
        float scale = iconSize / 16f;  // 从标准 16x16 缩放到目标尺寸
        g.pose().scale(scale, scale, 1f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();

        String label = stack.getHoverName().getString() + (count > 1 ? " ×" + count : "");
        String safe = font.plainSubstrByWidth(label, maxWidth - iconSize - ICON_TEXT_GAP - 2);
        g.drawString(font, safe, iconSize + ICON_TEXT_GAP, (ROW_HEIGHT - font.lineHeight) / 2, HudAnimUtil.withAlpha(0xEEEEEE, alpha), false);
        g.pose().popPose();
        
        return ROW_HEIGHT;
    }

    private static int renderTextReward(GuiGraphics g, String text, Font font,
                                        int maxWidth, int alpha, int offsetY) {
        String prefix = rewardPrefix(text);
        String safe = font.plainSubstrByWidth(prefix + text, maxWidth);
        g.drawString(font, safe, 0, offsetY + (ROW_HEIGHT - font.lineHeight) / 2, HudAnimUtil.withAlpha(0xDDCCFF, alpha), false);
        return ROW_HEIGHT;
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
