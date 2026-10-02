package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiScreenIngredients;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;
import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconAlpha;
import org.arcadia.arc_quest.quest.api.CollectionRewardNode;
import org.arcadia.arc_quest.quest.api.IReward;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.C2SClaimCollectionRewardPacket;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.reward.ItemReward;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The same compact reward tabs serve normal phases and collection survey milestones. */
public class JournalDetailRewards {
    private final QuestJournalScreen screen;
    private final JournalDetailPanel parent;
    private final int[] primaryTabRect = new int[4], phaseTabRect = new int[4], chapterTabRect = new int[4], rewardAreaRect = new int[4];
    private final Map<String, RewardCache> rewardCache = new HashMap<>();
    private final List<HudRect> itemHits = new ArrayList<>();
    private final List<ClaimHit> claimHits = new ArrayList<>();
    private JournalRewardTabs.Tab activeTab = JournalRewardTabs.Tab.PRIMARY;
    private double scrollX, targetScrollX, lastMouseX;
    private int maxScroll;
    private boolean isDragging, interactive;
    private float animTabX = -1, animTabW = -1, itemsAlphaAnim = 1f;
    private int parentClipY1, parentClipY2, statusCellWidth;
    private QuestDefinition sourceDefinition;
    private String sourceSelection = "";
    private boolean collectionSource;
    private List<RewardGroup> surveyGroups = List.of();
    private Language rewardLanguage;

    public JournalDetailRewards(QuestJournalScreen screen, JournalDetailPanel parent) {
        this.screen = screen;
        this.parent = parent;
    }

    public int render(GuiGraphics g, QuestDefinition def, String selectedPhaseId,
                      int x, int scrollAreaY, int scrollAreaW, int scrollAreaH, int mx, int my,
                      int activeTheme, float dAlpha, int safeA, int localY, float dt) {
        prepareSource(def, selectedPhaseId, false);
        var phase = selectedPhaseId == null ? null : def.getPhase(selectedPhaseId);
        List<IReward> phaseRewards = phase == null ? List.of() : phase.getPhaseRewards();
        return renderTabs(g, def, Component.translatable("arc_quest.gui.journal.section.phase_rewards"),
                phaseRewards.isEmpty() ? List.of() : List.of(new RewardGroup(phaseRewards, null)),
                List.of(),
                x, scrollAreaY, scrollAreaW, scrollAreaH, mx, my, activeTheme, dAlpha, safeA, localY, dt);
    }

    public int renderCollection(GuiGraphics g, QuestDefinition def, String selectedCategory,
                                int x, int scrollAreaY, int scrollAreaW, int scrollAreaH, int mx, int my,
                                int activeTheme, float dAlpha, int safeA, int localY, float dt) {
        return renderCollection(g, def, selectedCategory, null, x, scrollAreaY, scrollAreaW, scrollAreaH,
                mx, my, activeTheme, dAlpha, safeA, localY, dt);
    }

    public int renderCollection(GuiGraphics g, QuestDefinition def, String selectedCategory, String selectedPhaseId,
                                int x, int scrollAreaY, int scrollAreaW, int scrollAreaH, int mx, int my,
                                int activeTheme, float dAlpha, int safeA, int localY, float dt) {
        String category = JournalRewardTabs.normalizeCategory(selectedCategory);
        if (prepareSource(def, category + "\n" + (selectedPhaseId == null ? "" : selectedPhaseId), true)) {
            surveyGroups = JournalRewardTabs.surveyNodes(def.getCollectionConfig(), category).stream()
                    .map(node -> new RewardGroup(node.getRewards(), node)).toList();
        }
        var phase = selectedPhaseId == null ? null : def.getPhase(selectedPhaseId);
        Component title = Component.literal("▸ ").append(Component.translatable("arc_quest.gui.collection.milestone_rewards"));
        return renderTabs(g, def, title, surveyGroups, phase == null ? List.of() : phase.getPhaseRewards(),
                x, scrollAreaY, scrollAreaW, scrollAreaH, mx, my, activeTheme, dAlpha, safeA, localY, dt);
    }

    private boolean prepareSource(QuestDefinition definition, String selection, boolean collection) {
        String normalized = selection == null ? "" : selection;
        boolean changed = definition != sourceDefinition || collection != collectionSource || !normalized.equals(sourceSelection);
        if (changed) {
            if (definition != sourceDefinition || collection != collectionSource) {
                activeTab = JournalRewardTabs.Tab.PRIMARY;
                rewardCache.clear();
            }
            sourceDefinition = definition;
            sourceSelection = normalized;
            collectionSource = collection;
            resetContentInteraction();
            animTabX = -1;
        }
        if (rewardLanguage != Language.getInstance()) {
            rewardLanguage = Language.getInstance();
            rewardCache.clear();
        }
        return changed;
    }

    private int renderTabs(GuiGraphics g, QuestDefinition def, Component primaryTitle, List<RewardGroup> primaryGroups, List<IReward> extraPhaseRewards,
                           int x, int scrollAreaY, int scrollAreaW, int scrollAreaH, int mx, int my,
                           int activeTheme, float dAlpha, int safeA, int localY, float dt) {
        parentClipY1 = scrollAreaY;
        parentClipY2 = scrollAreaY + scrollAreaH;
        interactive = screen.canInteractWithJournalBackground() && safeA > 8;
        if (!interactive) isDragging = false;
        clearHits();
        boolean hasPrimary = !primaryGroups.isEmpty(), hasPhase = !extraPhaseRewards.isEmpty(), hasChapter = !def.getCompletionRewards().isEmpty();
        int localW = Math.max(0, scrollAreaW - 24);
        if ((!hasPrimary && !hasPhase && !hasChapter) || localW == 0) return localY;

        var availableTab = JournalRewardTabs.availableTab(activeTab, hasPrimary, hasPhase, hasChapter);
        if (availableTab != activeTab) {
            activeTab = availableTab;
            resetContentInteraction();
        }

        Font font = screen.getFont();
        String primaryText = primaryTitle.getString();
        String phaseText = Component.translatable("arc_quest.gui.journal.section.phase_rewards").getString();
        String chapterText = Component.translatable("arc_quest.gui.journal.section.chapter_rewards").getString();
        statusCellWidth = Math.max(font.width(collectionText("claim")), Math.max(font.width(collectionText("claimed")),
                font.width(collectionText("locked")))) + 12;
        int tabCount = (hasPrimary ? 1 : 0) + (hasPhase ? 1 : 0) + (hasChapter ? 1 : 0);
        int titleWidth = (hasPrimary ? font.width(primaryText) : 0) + (hasPhase ? font.width(phaseText) : 0)
                + (hasChapter ? font.width(chapterText) : 0);
        // Keep four pixels of outer hit padding. Reduce spacing before clipping any title.
        int tabGap = tabCount < 2 ? 0 : Math.max(8, Math.min(20, (localW - 8 - titleWidth) / (tabCount - 1)));
        String[] titles = fitTabTitles(font, new String[]{hasPrimary ? primaryText : "", hasPhase ? phaseText : "",
                hasChapter ? chapterText : ""}, Math.max(0, localW - 8 - (tabCount - 1) * tabGap));
        primaryText = titles[0]; phaseText = titles[1]; chapterText = titles[2];
        int primaryW = font.width(primaryText), phaseW = font.width(phaseText), chapterW = font.width(chapterText);
        int totalTabW = primaryW + phaseW + chapterW + (tabCount - 1) * tabGap;
        int currentY = localY + 5;
        int primaryX = localW / 2 - totalTabW / 2;
        int phaseX = primaryX + (hasPrimary ? primaryW + tabGap : 0);
        int chapterX = phaseX + (hasPhase ? phaseW + tabGap : 0);
        int absTopY = absoluteY(scrollAreaY, currentY);
        if (hasPrimary) drawTab(g, primaryText, primaryX, currentY, x, absTopY, mx, my,
                activeTheme, safeA, localW, JournalRewardTabs.Tab.PRIMARY, primaryTabRect);
        if (hasPhase) drawTab(g, phaseText, phaseX, currentY, x, absTopY, mx, my,
                activeTheme, safeA, localW, JournalRewardTabs.Tab.PHASE, phaseTabRect);
        if (hasChapter) drawTab(g, chapterText, chapterX, currentY, x, absTopY, mx, my,
                activeTheme, safeA, localW, JournalRewardTabs.Tab.CHAPTER, chapterTabRect);

        int targetTabX = switch (activeTab) { case PRIMARY -> primaryX; case PHASE -> phaseX; case CHAPTER -> chapterX; };
        int targetTabW = switch (activeTab) { case PRIMARY -> primaryW; case PHASE -> phaseW; case CHAPTER -> chapterW; };
        if (animTabX < 0) { animTabX = targetTabX; animTabW = targetTabW; }
        else {
            animTabX = HudAnimUtil.lerp(animTabX, targetTabX, .2f, dt);
            animTabW = HudAnimUtil.lerp(animTabW, targetTabW, .2f, dt);
        }
        int underlineLeft = Math.min(localW, Math.max(0, (int) animTabX));
        int underlineRight = Math.min(localW, Math.max(underlineLeft, (int) (animTabX + animTabW)));
        g.fill(underlineLeft, currentY + font.lineHeight + 1,
                underlineRight, currentY + font.lineHeight + 2, HudAnimUtil.withAlpha(activeTheme, safeA));

        currentY += 16;
        itemsAlphaAnim = HudAnimUtil.lerp(itemsAlphaAnim, 1f, .15f, dt);
        float itemAlpha = dAlpha * itemsAlphaAnim;
        int itemSafeA = (int) (safeA * itemsAlphaAnim);
        List<RewardGroup> groups = switch (activeTab) {
            case PRIMARY -> primaryGroups;
            case PHASE -> List.of(new RewardGroup(extraPhaseRewards, null));
            case CHAPTER -> List.of(new RewardGroup(def.getCompletionRewards(), null));
        };
        int totalRewardsW = groups.stream().mapToInt(group -> groupWidth(group, font)).sum() + (groups.size() - 1) * 20;
        maxScroll = Math.max(0, totalRewardsW - localW);
        targetScrollX = Math.max(0, Math.min(targetScrollX, maxScroll));
        scrollX += (targetScrollX - scrollX) * Math.min(1.0, dt * 14.0);
        int renderStartX = totalRewardsW <= localW ? (localW - totalRewardsW) / 2 : (int) -scrollX;
        int absItemsY = absoluteY(scrollAreaY, currentY);
        rewardAreaRect[0] = x + 12 + (totalRewardsW <= localW ? renderStartX : 0);
        rewardAreaRect[1] = absItemsY - 4;
        rewardAreaRect[2] = Math.min(totalRewardsW, localW);
        rewardAreaRect[3] = 32;

        safeScissor(g, x + 12, scrollAreaY, x + 12 + localW, scrollAreaY + scrollAreaH);
        g.pose().pushPose();
        g.pose().translate(renderStartX, currentY, 0);
        List<Runnable> itemPass = new ArrayList<>();
        int groupX = 0;
        String questId = def.getId().toString();
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            RewardGroup group = groups.get(groupIndex);
            CollectionRewardNode node = group.node();
            boolean claimed = node != null && ClientQuestCache.INSTANCE.isCollectionRewardClaimed(questId, node.getRewardNodeId());
            boolean unlocked = node != null && ClientQuestCache.INSTANCE.isCollectionRewardUnlocked(questId, node.getRewardNodeId());
            int rewardX = groupX;
            for (IReward reward : group.rewards()) {
                drawReward(g, reward, node, questId, unlocked || claimed, rewardX,
                        x + 12 + renderStartX + rewardX, absItemsY, x + 12, localW,
                        mx, my, activeTheme, itemAlpha, itemSafeA, itemPass);
                rewardX += getRewardCache(reward, font).width + 12;
            }
            if (node != null) {
                int statusX = rewardX - (group.rewards().isEmpty() ? 0 : 4);
                drawNodeStatus(g, node, questId, claimed, unlocked, statusX,
                        x + 12 + renderStartX + statusX, absItemsY, x + 12, localW,
                        mx, my, activeTheme, itemSafeA);
            }
            groupX += groupWidth(group, font) + 20;
            if (groupIndex + 1 < groups.size())
                g.fill(groupX - 11, 5, groupX - 10, 19, HudAnimUtil.withAlpha(0xFFFFFF, itemSafeA / 5));
        }
        // Keep the existing reward strip's batched item pass, tooltip and animation behavior.
        for (Runnable itemRender : itemPass) itemRender.run();
        g.pose().popPose();
        safeScissor(g, x, scrollAreaY, x + scrollAreaW, scrollAreaY + scrollAreaH);
        if (!interactive) clearHits();
        currentY += 26;
        drawHorizontalCyberBase(g, 20, localW - 20, currentY, activeTheme, dAlpha);
        return currentY + 16;
    }

    private static String[] fitTabTitles(Font font, String[] titles, int budget) {
        int[] widths = {font.width(titles[0]), font.width(titles[1]), font.width(titles[2])};
        if (widths[0] + widths[1] + widths[2] <= budget) return titles;
        int low = 0, high = budget;
        // Water filling lets shorter labels keep their width while longer labels share the remainder.
        while (low < high) {
            int cap = (low + high + 1) / 2;
            int used = Math.min(widths[0], cap) + Math.min(widths[1], cap) + Math.min(widths[2], cap);
            if (used <= budget) low = cap; else high = cap - 1;
        }
        int remaining = budget - Math.min(widths[0], low) - Math.min(widths[1], low) - Math.min(widths[2], low);
        int ellipsisWidth = font.width("…");
        for (int i = 0; i < titles.length; i++) {
            int limit = Math.min(widths[i], low);
            if (widths[i] > limit && remaining > 0) { limit++; remaining--; }
            if (widths[i] > limit) titles[i] = limit < ellipsisWidth ? font.plainSubstrByWidth(titles[i], limit)
                    : font.plainSubstrByWidth(titles[i], limit - ellipsisWidth) + "…";
        }
        return titles;
    }

    private void drawTab(GuiGraphics g, String title, int localX, int localY, int x, int absTopY,
                         int mx, int my, int theme, int alpha, int localW, JournalRewardTabs.Tab tab, int[] rect) {
        Font font = screen.getFont();
        int hitLeft = Math.max(0, localX - 4), hitRight = Math.min(localW, localX + font.width(title) + 4);
        rect[0] = x + 12 + hitLeft;
        rect[1] = absTopY - 4;
        rect[2] = Math.max(0, hitRight - hitLeft);
        rect[3] = font.lineHeight + 8;
        boolean hovered = interactive && isHovering(mx, my, rect);
        HudCursorManager.requestPointer(hovered);
        int color = activeTab == tab ? theme : hovered ? 0xFFFFFF : 0x888888;
        if (alpha >= 4) g.drawString(font, title, localX, localY, HudAnimUtil.withAlpha(color, alpha), false);
    }

    private void drawReward(GuiGraphics g, IReward reward, CollectionRewardNode node, String questId,
                            boolean nodeAuthorized, int localX, int absX, int absY, int clipX, int clipW,
                            int mx, int my, int theme, float alpha, int safeA, List<Runnable> itemPass) {
        Font font = screen.getFont();
        RewardCache cached = getRewardCache(reward, font);
        int width = cached.width;
        if (safeA < 4 || !inBounds(absX, absY, width, 24, clipX, clipW)) return;
        HudRect hit = clippedHit(absX, absY, width, 24, clipX, clipW);
        boolean hovered = interactive && hit.contains(mx, my) && !isDragging && safeA > 8;
        if (reward instanceof ItemReward) {
            ItemStack stack = cached.stack;
            if (hovered) {
                g.fill(localX + 2, 21, localX + width - 2, 23, HudAnimUtil.withAlpha(theme, (int) (255 * alpha)));
                g.fill(localX + 2, 2, localX + width - 2, 22, HudAnimUtil.withAlpha(theme, (int) (0x1A * alpha)));
                screen.setHoveredRewardTooltip(stack);
            } else g.fill(localX + 4, 22, localX + width - 4, 23, HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x33 * alpha)));
            if (interactive && !isDragging && (node == null || nodeAuthorized) && safeA > 8) itemHits.add(hit);
            if (alpha <= .04f) return;
            itemPass.add(() -> {
                g.pose().pushPose();
                float centerX = localX + width / 2f, centerY = 10f;
                g.pose().translate(centerX, centerY, 0);
                g.pose().scale(alpha, alpha, 1f);
                g.pose().translate(-centerX, -centerY, 0);
                if (node != null) {
                    ObjectiveIconAlpha.renderItem(g, stack, localX + 4, 2, 16, alpha);
                    if (stack.getCount() > 1) {
                        String count = Integer.toString(stack.getCount());
                        g.pose().pushPose();
                        g.pose().translate(0, 0, 200);
                        g.drawString(font, count, localX + 21 - font.width(count), 11,
                                HudAnimUtil.withAlpha(0xFFFFFF, safeA), true);
                        g.pose().popPose();
                    }
                } else {
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
                    g.renderFakeItem(stack, localX + 4, 2);
                }
                if (interactive && !isDragging) {
                    if (node == null) JeiScreenIngredients.rewardIcon(screen, g, reward, stack, localX + 4, 2, 16, 16);
                    else if (nodeAuthorized) JeiScreenIngredients.collectionRewardItem(screen, g, questId, node.getRewardNodeId(), stack,
                            localX + 4, 2, 16, 16);
                }
                if (node == null && alpha >= .55f) {
                    g.pose().translate(0, 0, 200);
                    g.renderItemDecorations(font, stack, localX + 4, 2);
                }
                if (node == null) {
                    RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                    RenderSystem.disableBlend();
                }
                g.pose().popPose();
            });
        } else {
            g.fill(localX, 6, localX + 2, 18, HudAnimUtil.withAlpha(theme, (int) (0xAA * alpha)));
            g.pose().pushPose();
            g.pose().translate(localX + 8, 8, 0);
            g.pose().scale(.85f, .85f, 1f);
            g.drawString(font, cached.text, 0, 0, HudAnimUtil.withAlpha(0xDDDDDD, safeA), false);
            if (interactive && node == null && !isDragging)
                JeiScreenIngredients.reward(screen, g, reward, 0, 0, font.width(cached.text), font.lineHeight);
            g.pose().popPose();
        }
    }

    private void drawNodeStatus(GuiGraphics g, CollectionRewardNode node, String questId, boolean claimed, boolean unlocked,
                                int localX, int absX, int absY, int clipX, int clipW,
                                int mx, int my, int theme, int alpha) {
        Font font = screen.getFont();
        int width = statusCellWidth;
        if (alpha < 4 || !inBounds(absX, absY + 3, width, 18, clipX, clipW)) return;
        HudRect hit = clippedHit(absX, absY + 3, width, 18, clipX, clipW);
        boolean canClaim = unlocked && !claimed;
        boolean hovered = interactive && canClaim && !isDragging && alpha > 8 && hit.contains(mx, my);
        if (canClaim) {
            g.fill(localX, 3, localX + width, 21, HudAnimUtil.withAlpha(theme, alpha / (hovered ? 5 : 12)));
            g.fill(localX + 4, 20, localX + width - 4, 21, HudAnimUtil.withAlpha(theme, alpha / (hovered ? 1 : 3)));
            if (interactive && !isDragging && alpha > 8) claimHits.add(new ClaimHit(hit, questId, node.getRewardNodeId()));
        }
        Component label = collectionText(claimed ? "claimed" : unlocked ? "claim" : "locked");
        g.drawString(font, label, localX + (width - font.width(label)) / 2, 8,
                HudAnimUtil.withAlpha(hovered ? 0xFFFFFF : canClaim ? theme : 0x888888, alpha), false);
        HudCursorManager.requestPointer(hovered);
    }

    private int groupWidth(RewardGroup group, Font font) {
        int rewardWidth = group.rewards().stream().mapToInt(reward -> getRewardCache(reward, font).width).sum()
                + Math.max(0, group.rewards().size() - 1) * 12;
        return rewardWidth + (group.node() == null ? 0 : (group.rewards().isEmpty() ? 0 : 8) + statusCellWidth);
    }

    private static Component collectionText(String key) {
        return Component.translatable("arc_quest.gui.collection." + key);
    }

    private RewardCache getRewardCache(IReward reward, Font font) {
        String key = reward instanceof ItemReward item ? "item:" + item.getItem() + ":" + item.getCount() : "text:" + reward.describe();
        return rewardCache.computeIfAbsent(key, ignored -> {
            RewardCache cache = new RewardCache();
            if (reward instanceof ItemReward item) {
                cache.stack = new ItemStack(item.getItem(), item.getCount());
                cache.width = 24;
            } else {
                cache.text = reward.describe();
                cache.width = (int) (font.width(cache.text) * .85f) + 12;
            }
            return cache;
        });
    }

    private int absoluteY(int scrollAreaY, int localY) {
        return (int) (scrollAreaY + 12 - parent.getDetailScrollOffset() + localY);
    }

    private boolean inBounds(int x, int y, int width, int height, int clipX, int clipW) {
        return x + width > clipX && x < clipX + clipW && y + height > parentClipY1 && y < parentClipY2;
    }

    private HudRect clippedHit(int x, int y, int width, int height, int clipX, int clipW) {
        int left = Math.max(x, clipX), top = Math.max(y, parentClipY1);
        return new HudRect(left, top, Math.max(0, Math.min(x + width, clipX + clipW) - left),
                Math.max(0, Math.min(y + height, parentClipY2) - top));
    }

    private void safeScissor(GuiGraphics g, int x1, int y1, int x2, int y2) {
        screen.disableScissor(g);
        if (x2 > x1 && y2 > y1) screen.enableScissor(g, x1, y1, x2, y2);
    }

    private void clearHits() {
        primaryTabRect[2] = phaseTabRect[2] = chapterTabRect[2] = rewardAreaRect[2] = 0;
        itemHits.clear();
        claimHits.clear();
    }

    private void resetContentInteraction() {
        scrollX = targetScrollX = 0;
        maxScroll = 0;
        isDragging = false;
        clearHits();
    }

    private void switchToTab(JournalRewardTabs.Tab tab) {
        if (activeTab == tab) return;
        activeTab = tab;
        resetContentInteraction();
        itemsAlphaAnim = 0f;
        screen.playClick();
    }

    private boolean acceptsInput() {
        return interactive && screen.canInteractWithJournalBackground();
    }

    public boolean mouseClicked(double mx, double my) {
        if (!acceptsInput()) return false;
        if (isHovering(mx, my, primaryTabRect)) { switchToTab(JournalRewardTabs.Tab.PRIMARY); return true; }
        if (isHovering(mx, my, phaseTabRect)) { switchToTab(JournalRewardTabs.Tab.PHASE); return true; }
        if (isHovering(mx, my, chapterTabRect)) { switchToTab(JournalRewardTabs.Tab.CHAPTER); return true; }
        // The screen's JEI bridge owns item clicks; they must never begin a drag or claim a node.
        if (JeiScreenIngredients.isRuntimeAvailable() && itemHits.stream().anyMatch(hit -> hit.contains(mx, my))) return true;
        for (ClaimHit claim : claimHits) if (claim.bounds().contains(mx, my)) {
            if (screen.getMinecraft().getConnection() != null
                    && claim.questId().equals(screen.getSelectedQuestId())
                    && ClientQuestCache.INSTANCE.isCollectionRewardUnlocked(claim.questId(), claim.nodeId())
                    && !ClientQuestCache.INSTANCE.isCollectionRewardClaimed(claim.questId(), claim.nodeId())) {
                ArcQuestNetwork.sendClaimCollectionReward(C2SClaimCollectionRewardPacket.of(claim.questId(), claim.nodeId()));
                screen.playClick();
            }
            return true;
        }
        if (maxScroll > 0 && isHovering(mx, my, rewardAreaRect)) {
            isDragging = true;
            lastMouseX = mx;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mx, double my) {
        if (!acceptsInput() || !isDragging) return false;
        targetScrollX += lastMouseX - mx;
        lastMouseX = mx;
        return true;
    }

    public boolean mouseReleased(int button) {
        if (button != 0 || !isDragging) return false;
        isDragging = false;
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!acceptsInput() || maxScroll <= 0 || !isHovering(mx, my, rewardAreaRect)) return false;
        targetScrollX -= delta * 30;
        return true;
    }

    private boolean isHovering(double mx, double my, int[] rect) {
        return rect[2] > 0 && my >= parentClipY1 && my < parentClipY2
                && mx >= rect[0] && mx < rect[0] + rect[2] && my >= rect[1] && my < rect[1] + rect[3];
    }

    private void drawHorizontalCyberBase(GuiGraphics g, int startX, int endX, int bottomY, int themeColor, float alphaPercentage) {
        if (alphaPercentage < .02f || endX <= startX) return;
        int alpha = (int) (255 * alphaPercentage);
        if (alpha < 5) return;
        float pulse = (float) (Math.sin(Util.getMillis() / 600.0) * .5 + .5);
        int coreColor = themeColor & 0xFFFFFF;
        int glowMaxA = (int) (alpha * (.10f + .15f * pulse));
        g.fillGradient(startX, bottomY - 24, endX, bottomY, coreColor, coreColor | (glowMaxA << 24));
        g.fillGradient(startX, bottomY - 3, endX, bottomY, coreColor | ((int) (alpha * .15f) << 24), coreColor | (alpha << 24));
        g.fillGradient(startX, bottomY - 1, endX, bottomY, coreColor | (alpha << 24), 0xFFFFFF | ((int) (alpha * .8f) << 24));
    }

    private record RewardGroup(List<IReward> rewards, CollectionRewardNode node) {}
    private record ClaimHit(HudRect bounds, String questId, String nodeId) {}
    private static class RewardCache {
        ItemStack stack = ItemStack.EMPTY;
        int width;
        String text;
    }
}
