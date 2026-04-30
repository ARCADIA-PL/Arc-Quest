package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.quest.api.IReward;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.reward.ItemReward;

import java.util.List;

public class JournalDetailRewards {
    private final QuestJournalScreen screen;
    private final JournalDetailPanel parent;

    public JournalDetailRewards(QuestJournalScreen screen, JournalDetailPanel parent) {
        this.screen = screen;
        this.parent = parent;
    }

    public int render(GuiGraphics g, QuestDefinition def, String selectedPhaseId, int x, int scrollAreaY, int scrollAreaW, int scrollAreaH, int mx, int my, int activeTheme, float dAlpha, int safeA, int localY) {

        boolean hasPhaseRewards = false;
        List<IReward> phaseRewards = null;
        if (selectedPhaseId != null && !selectedPhaseId.isEmpty()) {
            var phase = def.getPhase(selectedPhaseId);
            if (phase != null && !phase.getPhaseRewards().isEmpty()) {
                hasPhaseRewards = true;
                phaseRewards = phase.getPhaseRewards();
            }
        }

        boolean hasChapterRewards = !def.getCompletionRewards().isEmpty();
        if (!hasPhaseRewards && !hasChapterRewards) return localY;

        localY += 12; // 顶部留白

        int startY = localY;

        // 渲染阶段奖励节点
        if (hasPhaseRewards) {
            localY = renderRewardGroup(g, Component.translatable("arc_quest.gui.journal.section.phase_rewards"),
                    phaseRewards, 0xFFCC66, activeTheme, x, scrollAreaY, scrollAreaW, scrollAreaH, mx, my, dAlpha, safeA, localY, !hasChapterRewards);
        }

        // 渲染最终章节奖励节点
        if (hasChapterRewards) {
            localY = renderRewardGroup(g, Component.translatable("arc_quest.gui.journal.section.chapter_rewards"),
                    def.getCompletionRewards(), 0xFFDD88, activeTheme, x, scrollAreaY, scrollAreaW, scrollAreaH, mx, my, dAlpha, safeA, localY, true);
        }

        // 绘制科幻主轴线 (连接上下两个节点，如果没有下节点则只画一点点)
        if (dAlpha > 0.05f) {
            int axisX = 8;
            int axisStartY = startY;
            int axisEndY = localY - 16; // 稍微不要画到底
            int lineA = (int) (0x66 * dAlpha);
            g.fill(axisX, axisStartY, axisX + 1, axisEndY, HudAnimUtil.withAlpha(activeTheme, lineA));
        }

        return localY + 4;
    }

    /**
     * 统一的奖励节点渲染器，抽取重复逻辑，实现模块化绘制
     */
    private int renderRewardGroup(GuiGraphics g, Component title, List<IReward> rewards, int titleColor, int activeTheme,
                                  int x, int scrollAreaY, int scrollAreaW, int scrollAreaH,
                                  int mx, int my, float dAlpha, int safeA, int localY, boolean isLastNode) {
        Font font = screen.getFont();

        // 分支节点横线
        int axisX = 8;
        int branchY = localY + 6;
        g.fill(axisX, branchY, axisX + 8, branchY + 1, HudAnimUtil.withAlpha(activeTheme, (int) (0x88 * dAlpha)));

        int boxMarginLeft = 16;
        int boxW = scrollAreaW - boxMarginLeft - 16;
        int bA = (int) (255 * dAlpha);

        // 预计算高度与换行
        int tempX = 8, rows = 1;
        for (IReward r : rewards) {
            int rWidth = (r instanceof ItemReward) ? 28 : (int) (font.width(">" + r.describe()) * 0.75f) + 12;
            if (tempX + rWidth > boxW - 8 && tempX > 8) {
                tempX = 8;
                rows++;
            }
            tempX += rWidth;
        }
        int boxH = 20 + rows * 28;

        // 绘制机能风科技面板背景
        HudAnimUtil.drawFrame(g, boxMarginLeft, localY, boxW, boxH,
                HudAnimUtil.withAlpha(0x000000, (int) (0x44 * dAlpha)),
                HudAnimUtil.withAlpha(activeTheme, (int) (0x55 * dAlpha)));

        // 节点标题
        g.pose().pushPose();
        g.pose().translate(boxMarginLeft + 6, localY + 4, 0);
        g.pose().scale(0.8f, 0.8f, 1f);
        g.drawString(font, title.getString(), 0, 0, HudAnimUtil.withAlpha(titleColor, safeA), true);
        g.pose().popPose();

        // 渲染内部奖励内容
        int startX = 8, itemStartY = localY + 18;
        for (IReward r : rewards) {
            int rWidth = (r instanceof ItemReward) ? 28 : (int) (font.width(">" + r.describe()) * 0.75f) + 12;
            if (startX + rWidth > boxW - 8 && startX > 8) {
                startX = 8;
                itemStartY += 28;
            }

            if (r instanceof ItemReward ir) {
                ItemStack stack = new ItemStack(ir.getItem(), ir.getCount());
                int absDrawX = boxMarginLeft + startX;

                // 物品框
                HudAnimUtil.drawFrame(g, absDrawX - 2, itemStartY - 2, 20, 20,
                        HudAnimUtil.withAlpha(0x000000, (int) (0x55 * dAlpha)),
                        HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x33 * dAlpha)));

                // 悬停判定（严格按照原有的视口偏移计算逻辑）
                int absPickX = x + 12 + absDrawX;
                int absPickY = scrollAreaY + 12 - (int) parent.getDetailScrollOffset() + itemStartY;
                boolean isHovered = mx >= absPickX && mx <= absPickX + 16 && my >= absPickY && my <= absPickY + 16 && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH;

                if (isHovered) {
                    // 悬停时的赛博发光反馈
                    HudAnimUtil.drawFrame(g, absDrawX - 2, itemStartY - 2, 20, 20,
                            HudAnimUtil.withAlpha(activeTheme, (int) (0x44 * dAlpha)),
                            HudAnimUtil.withAlpha(activeTheme, (int) (0xAA * dAlpha)));
                    screen.setHoveredRewardTooltip(stack);
                }

                if (dAlpha > 0.01f) {
                    g.pose().pushPose();
                    float itemCenterX = absDrawX + 8f, itemCenterY = itemStartY + 8f;
                    g.pose().translate(itemCenterX, itemCenterY, 0);
                    g.pose().scale(dAlpha, dAlpha, 1f);
                    g.pose().translate(-itemCenterX, -itemCenterY, 0);
                    g.renderItem(stack, absDrawX, itemStartY);

                    g.pose().pushPose();
                    g.pose().translate(0, 0, 200);
                    g.renderItemDecorations(font, stack, absDrawX, itemStartY);
                    g.pose().popPose();

                    g.pose().popPose();
                }
            } else {
                g.pose().pushPose();
                g.pose().translate(boxMarginLeft + startX, itemStartY + 4, 0);
                g.pose().scale(0.75f, 0.75f, 1f);
                g.drawString(font, "> " + r.describe(), 0, 0, HudAnimUtil.withAlpha(0x88AAFF, safeA), false);
                g.pose().popPose();
            }
            startX += rWidth;
        }

        // 节点之间的间距
        return localY + boxH + (isLastNode ? 0 : 12);
    }
}