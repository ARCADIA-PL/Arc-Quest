package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.quest.api.IReward;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.reward.ItemReward;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JournalDetailRewards {
    private final QuestJournalScreen screen;
    private final JournalDetailPanel parent;

    private enum Tab { PHASE, CHAPTER }
    private Tab activeTab = Tab.PHASE;

    private double scrollX = 0;
    private double targetScrollX = 0;
    private boolean isDragging = false;
    private double lastMouseX = 0;

    // 动画状态
    private float animTabX = -1;
    private float animTabW = -1;
    private float itemsAlphaAnim = 1f;

    // 碰撞盒与父级裁剪区
    private final int[] phaseTabRect = new int[4];
    private final int[] chapterTabRect = new int[4];
    private final int[] rewardAreaRect = new int[4];
    private int parentClipY1 = 0;
    private int parentClipY2 = 0;
    private final Map<String, RewardCache> rewardCache = new HashMap<>();
    private final String phaseRewardText = Component.translatable("arc_quest.gui.journal.section.phase_rewards").getString();
    private final String chapterRewardText = Component.translatable("arc_quest.gui.journal.section.chapter_rewards").getString();

    private static class RewardCache {
        ItemStack stack = ItemStack.EMPTY;
        int width = -1;
        String text;
    }

    public JournalDetailRewards(QuestJournalScreen screen, JournalDetailPanel parent) {
        this.screen = screen;
        this.parent = parent;
    }

    private void safeScissor(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.disableScissor();
        if (x2 > x1 && y2 > y1) {
            screen.enableScissor(g, x1, y1, x2, y2);
        }
    }

    public int render(GuiGraphics g, QuestDefinition def, String selectedPhaseId, int x, int scrollAreaY, int scrollAreaW, int scrollAreaH, int mx, int my, int activeTheme, float dAlpha, int safeA, int localY, float dt) {
        this.parentClipY1 = scrollAreaY;
        this.parentClipY2 = scrollAreaY + scrollAreaH;

        List<IReward> phaseRewards = null;
        if (selectedPhaseId != null && !selectedPhaseId.isEmpty()) {
            var phase = def.getPhase(selectedPhaseId);
            if (phase != null && !phase.getPhaseRewards().isEmpty()) {
                phaseRewards = phase.getPhaseRewards();
            }
        }

        boolean hasPhaseRewards = phaseRewards != null && !phaseRewards.isEmpty();
        boolean hasChapterRewards = !def.getCompletionRewards().isEmpty();

        if (!hasPhaseRewards && !hasChapterRewards) {
            phaseTabRect[2] = 0; chapterTabRect[2] = 0; rewardAreaRect[2] = 0;
            return localY;
        }

        if (activeTab == Tab.PHASE && !hasPhaseRewards) activeTab = Tab.CHAPTER;
        if (activeTab == Tab.CHAPTER && !hasChapterRewards) activeTab = Tab.PHASE;

        localY += 5;
        Font font = screen.getFont();
        int localW = scrollAreaW - 24;
        int currentY = localY;

        String phaseTxt = phaseRewardText;
        String chapTxt = chapterRewardText;

        int totalTabW = 0;
        int phaseTw = font.width(phaseTxt);
        int chapTw = font.width(chapTxt);

        if (hasPhaseRewards) totalTabW += phaseTw;
        if (hasChapterRewards) {
            if (hasPhaseRewards) totalTabW += 20;
            totalTabW += chapTw;
        }

        int tabStartX = localW / 2 - totalTabW / 2;
        int phaseTabX = tabStartX;
        int chapTabX = tabStartX + (hasPhaseRewards ? phaseTw + 20 : 0);

        int absTopY = (int) (scrollAreaY + 12 - parent.getDetailScrollOffset() + currentY);
        phaseTabRect[2] = 0; chapterTabRect[2] = 0;

        // 渲染文本按钮
        if (hasPhaseRewards) {
            phaseTabRect[0] = x + 12 + phaseTabX - 4;
            phaseTabRect[1] = absTopY - 4;
            phaseTabRect[2] = phaseTw + 8;
            phaseTabRect[3] = font.lineHeight + 8;
            boolean hovered = isHovering(mx, my, phaseTabRect);
            int color = (activeTab == Tab.PHASE) ? activeTheme : (hovered ? 0xFFFFFF : 0x888888);
            g.drawString(font, phaseTxt, phaseTabX, currentY, HudAnimUtil.withAlpha(color, safeA), false);
        }

        if (hasChapterRewards) {
            chapterTabRect[0] = x + 12 + chapTabX - 4;
            chapterTabRect[1] = absTopY - 4;
            chapterTabRect[2] = chapTw + 8;
            chapterTabRect[3] = font.lineHeight + 8;
            boolean hovered = isHovering(mx, my, chapterTabRect);
            int color = (activeTab == Tab.CHAPTER) ? activeTheme : (hovered ? 0xFFFFFF : 0x888888);
            g.drawString(font, chapTxt, chapTabX, currentY, HudAnimUtil.withAlpha(color, safeA), false);
        }

        // 处理丝滑滑块动画
        int targetTabX = activeTab == Tab.PHASE ? phaseTabX : chapTabX;
        int targetTabW = activeTab == Tab.PHASE ? phaseTw : chapTw;

        if (animTabX < 0) {
            animTabX = targetTabX;
            animTabW = targetTabW;
        } else {
            animTabX = HudAnimUtil.lerp(animTabX, targetTabX, 0.2f, dt);
            animTabW = HudAnimUtil.lerp(animTabW, targetTabW, 0.2f, dt);
        }
        g.fill((int)animTabX, currentY + font.lineHeight + 1, (int)(animTabX + animTabW), currentY + font.lineHeight + 2, HudAnimUtil.withAlpha(activeTheme, safeA));

        currentY += 16;

        // 处理物品淡入动画
        itemsAlphaAnim = HudAnimUtil.lerp(itemsAlphaAnim, 1f, 0.15f, dt);
        float itemDAlpha = dAlpha * itemsAlphaAnim;
        int itemSafeA = (int) (safeA * itemsAlphaAnim);

        List<IReward> currentRewards = activeTab == Tab.PHASE ? phaseRewards : def.getCompletionRewards();
        int totalRewardsW = currentRewards.stream().mapToInt(r -> getRewardWidth(r, font)).sum() + (currentRewards.size() - 1) * 12;

        int maxScroll = Math.max(0, totalRewardsW - localW);
        targetScrollX = Math.max(0, Math.min(targetScrollX, maxScroll));
        scrollX += (targetScrollX - scrollX) * Math.min(1.0, dt * 14.0);

        int renderStartX = (totalRewardsW <= localW) ? (localW / 2 - totalRewardsW / 2) : (int) -scrollX;
        int absItemsY = (int) (scrollAreaY + 12 - parent.getDetailScrollOffset() + currentY);

        // 精准限制滚动碰撞盒：只覆盖实际渲染的物品区域
        int renderWidth = Math.min(totalRewardsW, localW);
        int hitStartX = (totalRewardsW <= localW) ? renderStartX : 0;
        rewardAreaRect[0] = x + 12 + hitStartX;
        rewardAreaRect[1] = absItemsY - 4;
        rewardAreaRect[2] = renderWidth;
        rewardAreaRect[3] = 32;

        boolean needsScissor = totalRewardsW > localW;
        // 使用安全裁剪替换原有的直接调用
        if (needsScissor) safeScissor(g, x + 12, scrollAreaY, x + 12 + localW, scrollAreaY + scrollAreaH);

        g.pose().pushPose();
        g.pose().translate(renderStartX, currentY, 0);

        int itemX = 0;
        for (IReward r : currentRewards) {
            int rW = getRewardWidth(r, font);
            int rH = 24;
            RewardCache cachedReward = getRewardCache(r, font);

            int absHitX = x + 12 + renderStartX + itemX;
            int absHitY = absItemsY;

            boolean inBounds = true;
            if (needsScissor && (absHitX + rW < x + 12 || absHitX > x + 12 + localW)) inBounds = false;

            boolean hovered = inBounds && mx >= absHitX && mx <= absHitX + rW &&
                    my >= absHitY && my <= absHitY + rH &&
                    my >= parentClipY1 && my <= parentClipY2;

            if (r instanceof ItemReward) {
                ItemStack stack = cachedReward.stack;
                int lineY = rH - 2;

                if (hovered && !isDragging) {
                    g.fill(itemX + 2, lineY - 1, itemX + rW - 2, lineY + 1, HudAnimUtil.withAlpha(activeTheme, (int) (255 * itemDAlpha)));
                    g.fill(itemX + 2, 2, itemX + rW - 2, lineY, HudAnimUtil.withAlpha(activeTheme, (int) (0x1A * itemDAlpha)));
                    screen.setHoveredRewardTooltip(stack);
                } else {
                    g.fill(itemX + 4, lineY, itemX + rW - 4, lineY + 1, HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x33 * itemDAlpha)));
                }

                if (itemDAlpha > 0.01f) {
                    g.pose().pushPose();
                    float itemCx = itemX + rW / 2f, itemCy = rH / 2f - 2f;
                    g.pose().translate(itemCx, itemCy, 0);
                    g.pose().scale(itemDAlpha, itemDAlpha, 1f);
                    g.pose().translate(-itemCx, -itemCy, 0);

                    g.renderItem(stack, itemX + 4, 2);
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 200);
                    g.renderItemDecorations(font, stack, itemX + 4, 2);
                    g.pose().popPose();
                    g.pose().popPose();
                }
            } else {
                int tickY1 = 6, tickY2 = 18;
                g.fill(itemX, tickY1, itemX + 2, tickY2, HudAnimUtil.withAlpha(activeTheme, (int) (0xAA * itemDAlpha)));
                g.pose().pushPose();
                g.pose().translate(itemX + 8, 8, 0);
                g.pose().scale(0.85f, 0.85f, 1f);
                g.drawString(font, cachedReward.text, 0, 0, HudAnimUtil.withAlpha(0xDDDDDD, itemSafeA), false);
                g.pose().popPose();
            }
            itemX += rW + 12;
        }

        g.pose().popPose();

        // 恢复父级裁剪时，也必须使用安全裁剪！
        if (needsScissor) safeScissor(g, x, scrollAreaY, x + scrollAreaW, scrollAreaY + scrollAreaH);

        currentY += 26;
        drawHorizontalCyberBase(g, 20, localW - 20, currentY, activeTheme, dAlpha);
        return currentY + 16;
    }

    private int getRewardWidth(IReward r, Font font) {
        return getRewardCache(r, font).width;
    }

    private RewardCache getRewardCache(IReward r, Font font) {
        String key = r instanceof ItemReward ir ? "item:" + ir.getItem() + ":" + ir.getCount() : "text:" + r.describe();
        return rewardCache.computeIfAbsent(key, k -> {
            RewardCache cache = new RewardCache();
            if (r instanceof ItemReward ir) {
                cache.stack = new ItemStack(ir.getItem(), ir.getCount());
                cache.width = 24;
            } else {
                cache.text = r.describe();
                cache.width = (int) (font.width(cache.text) * 0.85f) + 12;
            }
            return cache;
        });
    }

    private void switchToTab(Tab tab) {
        if (activeTab != tab) {
            activeTab = tab;
            scrollX = 0;
            targetScrollX = 0;
            itemsAlphaAnim = 0f;
            screen.playClick();
        }
    }

    public boolean mouseClicked(double mx, double my) {
        if (isHovering(mx, my, phaseTabRect)) { switchToTab(Tab.PHASE); return true; }
        if (isHovering(mx, my, chapterTabRect)) { switchToTab(Tab.CHAPTER); return true; }
        if (isHovering(mx, my, rewardAreaRect)) { isDragging = true; lastMouseX = mx; return true; }
        return false;
    }

    public boolean mouseDragged(double mx, double my) {
        if (isDragging) { targetScrollX += (lastMouseX - mx); lastMouseX = mx; return true; }
        return false;
    }

    public boolean mouseReleased(int button) {
        if (button == 0 && isDragging) { isDragging = false; return true; }
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        if (isHovering(mx, my, rewardAreaRect)) { targetScrollX -= delta * 30.0; return true; }
        return false;
    }

    private boolean isHovering(double mx, double my, int[] rect) {
        if (rect[2] == 0) return false;
        if (my < parentClipY1 || my > parentClipY2) return false;
        return mx >= rect[0] && mx <= rect[0] + rect[2] && my >= rect[1] && my <= rect[1] + rect[3];
    }

    private void drawHorizontalCyberBase(GuiGraphics g, int startX, int endX, int bottomY, int themeColor, float alphaPercentage) {
        if (alphaPercentage < 0.02f) return;
        int alpha = (int) (255 * alphaPercentage);
        if (alpha < 5) return;
        long time = Util.getMillis();
        float pulse = (float) (Math.sin(time / 600.0) * 0.5 + 0.5);
        int coreColor = themeColor & 0xFFFFFF;

        int glowHeight = 24;
        int glowMaxA = (int) (alpha * (0.10f + 0.15f * pulse));
        g.fillGradient(startX, bottomY - glowHeight, endX, bottomY, coreColor | (0 << 24), coreColor | (glowMaxA << 24));
        g.fillGradient(startX, bottomY - 3, endX, bottomY, coreColor | ((int)(alpha * 0.15f) << 24), coreColor | (alpha << 24));
        g.fillGradient(startX, bottomY - 1, endX, bottomY, coreColor | (alpha << 24), 0xFFFFFF | ((int)(alpha * 0.8f) << 24));
    }
}