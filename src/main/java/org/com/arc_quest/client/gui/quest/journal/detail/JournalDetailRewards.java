package org.com.arc_quest.client.gui.quest.journal.detail;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.com.arc_quest.client.gui.HudAnimUtil;
import org.com.arc_quest.client.gui.quest.journal.QuestJournalScreen;
import org.com.arc_quest.quest.api.IReward;
import org.com.arc_quest.quest.api.QuestDefinition;
import org.com.arc_quest.quest.reward.ItemReward;

public class JournalDetailRewards {
    private final QuestJournalScreen screen;
    private final JournalDetailPanel parent;

    public JournalDetailRewards(QuestJournalScreen screen, JournalDetailPanel parent) {
        this.screen = screen;
        this.parent = parent;
    }

    public int render(GuiGraphics g, QuestDefinition def, int x, int scrollAreaY, int scrollAreaW, int scrollAreaH, int mx, int my, int activeTheme, float dAlpha, int safeA, int localY) {
        if (def.getCompletionRewards().isEmpty()) return localY;

        Font font = screen.getFont();
        localY += 8;
        int boxW = scrollAreaW - 24;
        int bA = (int) (255 * dAlpha);

        int tempX = 12, rows = 1;
        for (IReward r : def.getCompletionRewards()) {
            int rWidth = (r instanceof ItemReward) ? 28 : (int) (font.width(">" + r.describe()) * 0.75f) + 12;
            if (tempX + rWidth > boxW - 16 && tempX > 12) { tempX = 12; rows++; }
            tempX += rWidth;
        }
        int boxH = 24 + rows * 28;

        HudAnimUtil.drawFrame(g, 0, localY, boxW, boxH, HudAnimUtil.withAlpha(0x000000, (int) (0x55 * dAlpha)), HudAnimUtil.withAlpha(activeTheme, (int) (0x66 * dAlpha)));

        g.pose().pushPose(); g.pose().translate(8, localY + 6, 0); g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, Component.translatable("arc_quest.gui.journal.section.chapter_rewards").getString(), 0, 0, HudAnimUtil.withAlpha(0xFFDD88, safeA), true);
        g.pose().popPose();

        int startX = 12, startY = localY + 22;
        for (IReward r : def.getCompletionRewards()) {
            int rWidth = (r instanceof ItemReward) ? 28 : (int) (font.width(">" + r.describe()) * 0.75f) + 12;
            if (startX + rWidth > boxW - 16 && startX > 12) { startX = 12; startY += 28; }

            if (r instanceof ItemReward ir) {
                ItemStack stack = new ItemStack(ir.getItem(), ir.getCount());
                HudAnimUtil.drawFrame(g, startX - 2, startY - 2, 20, 20, HudAnimUtil.withAlpha(0x000000, (int) (0x33 * dAlpha)), HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x44 * dAlpha)));

                if (dAlpha > 0.01f) {
                    g.pose().pushPose();
                    float itemCenterX = startX + 8f, itemCenterY = startY + 8f;
                    g.pose().translate(itemCenterX, itemCenterY, 0); g.pose().scale(dAlpha, dAlpha, 1f); g.pose().translate(-itemCenterX, -itemCenterY, 0);
                    g.renderItem(stack, startX, startY);
                    g.pose().pushPose(); g.pose().translate(0, 0, 200); g.renderItemDecorations(font, stack, startX, startY); g.pose().popPose();
                    g.pose().popPose();
                }

                int absX = x + 12 + startX, absY = scrollAreaY + 12 - (int) parent.getDetailScrollOffset() + startY;
                if (mx >= absX && mx <= absX + 16 && my >= absY && my <= absY + 16 && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH) {
                    g.fill(startX - 1, startY - 1, startX + 17, startY + 17, HudAnimUtil.withAlpha(0xFFFFFF, (int) (bA * 0.25f)));
                    screen.setHoveredRewardTooltip(stack);
                }
            } else {
                g.pose().pushPose(); g.pose().translate(startX, startY + 4, 0); g.pose().scale(0.75f, 0.75f, 1f);
                g.drawString(font, ">" + r.describe(), 0, 0, HudAnimUtil.withAlpha(0x88AAFF, safeA), false);
                g.pose().popPose();
            }
            startX += rWidth;
        }
        return localY + boxH + 8;
    }
}