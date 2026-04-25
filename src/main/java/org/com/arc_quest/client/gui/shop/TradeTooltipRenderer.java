package org.com.arc_quest.client.gui.shop;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.com.arc_quest.client.gui.HudAnimUtil;
import org.com.arc_quest.trade.api.CostShortfallLine;
import org.com.arc_quest.trade.api.TradeEntry;
import org.com.arc_quest.trade.network.ClientTradeCache;

import java.util.ArrayList;
import java.util.List;

public class TradeTooltipRenderer {

    private final AbstractTradeScreen screen;
    private final Font font;

    private static final float HOVER_DELAY = 0.05f;
    private float hoverTimer = 0f;
    private TradeEntry activeEntry = null;
    private TradeEntry hoveredEntry = null;

    private float tooltipAlpha = 0f;
    private float animBgX = 0, animBgY = 0, animBgW = 0, animBgH = 0;
    private int animThemeColor = -1;
    private float animProgress = 0f;
    private float feedbackScale = 1.0f;
    private float feedbackShake = 0f;

    public TradeTooltipRenderer(AbstractTradeScreen screen, Font font) {
        this.screen = screen;
        this.font = font;
    }

    public void triggerTradeSuccess() { this.feedbackScale = 1.15f; }
    public void triggerTradeFail() { this.feedbackShake = 6f; }

    public void updateAndRender(GuiGraphics g, TradeEntry newHovered, int mx, int my, float dt, boolean isClosing) {
        if (newHovered != hoveredEntry) {
            if (newHovered != null && tooltipAlpha > 0.5f) {
                hoveredEntry = newHovered;
                activeEntry = newHovered;
                hoverTimer = HOVER_DELAY;
            } else {
                hoveredEntry = newHovered;
                hoverTimer = 0f;
            }
        }

        if (hoveredEntry != null && !isClosing && screen.getTransitionAnim() >= 0.9f) {
            if (hoverTimer < HOVER_DELAY) hoverTimer += dt;
            if (hoverTimer >= HOVER_DELAY) activeEntry = hoveredEntry;
        } else {
            hoverTimer = 0f;
        }

        float targetAlpha = (hoveredEntry != null && hoverTimer >= HOVER_DELAY && !isClosing) ? 1f : 0f;
        tooltipAlpha += (targetAlpha - tooltipAlpha) * Math.min(1f, dt * 15f);

        if (tooltipAlpha > 0.02f && activeEntry != null) {
            int gi = new ArrayList<>(screen.getShop().getAllEntries()).indexOf(activeEntry);
            if(gi != -1) renderMorphingTooltip(g, activeEntry, gi, mx, my, dt, isClosing);
        } else {
            animBgW = 0;
            activeEntry = null;
        }
    }

    public void forceRefresh() {
        if (activeEntry != null && tooltipAlpha > 0.1f) animBgW = 0;
    }

    private static class TooltipData {
        int x, y, w, h;
        List<FormattedCharSequence> descLines;
        boolean onCd; int purchases, maxP, themeColor;
        boolean showShortfall; List<CostShortfallLine> shortfalls;
    }

    private TooltipData calcTooltipData(TradeEntry entry, int gi, int mx, int my) {
        TooltipData d = new TooltipData();
        d.themeColor = screen.getThemeColorForEntry(entry);
        ClientTradeCache cache = ClientTradeCache.INSTANCE;
        d.maxP = entry.getMaxPurchases();
        d.purchases = cache.getPurchaseCount(screen.getShopId(), gi);
        d.onCd = cache.isOnCooldown(screen.getShopId(), gi);

        int padding = 10;
        d.descLines = entry.getDescription() != null ? font.split(entry.getDescription(), 180) : new ArrayList<>();
        d.shortfalls = ClientTradeCache.INSTANCE.getShortfall(screen.getShopId(), entry.getEntryId());

        ClientTradeCache.FeedbackSnapshot feedback = ClientTradeCache.INSTANCE.feedbackSnapshot(screen.getShopId());
        boolean thisEntryFailed = feedback != null && feedback.lastFailedEntryId() != null && feedback.lastFailedEntryId().equals(entry.getEntryId());
        d.showShortfall = thisEntryFailed && !d.shortfalls.isEmpty();

        int titleW = font.width(entry.getDisplayName());
        int totalW = Math.max(188, titleW + 40);

        if (d.showShortfall) {
            totalW = Math.max(totalW, font.width(Component.translatable("arc_quest.gui.trade.tooltip.shortfall_summary")) + 40);
            for (CostShortfallLine sf : d.shortfalls) totalW = Math.max(totalW, font.width(sf.label()) + font.width("-" + sf.missing()) + 50);
        } else {
            for (var line : d.descLines) totalW = Math.max(totalW, font.width(line));
        }
        d.w = totalW + padding * 2;

        int totalH = padding * 2 + 16;
        if (d.showShortfall) totalH += 18 + (d.shortfalls.size() * 18);
        else {
            if (!d.descLines.isEmpty()) totalH += 6 + d.descLines.size() * font.lineHeight;
            if (d.maxP > 0) totalH += 18;
            if (d.onCd) totalH += font.lineHeight + 4;
        }
        d.h = totalH;

        int yOffset = 18;
        d.x = mx - (d.w / 2);
        if (d.x < 5) d.x = 5; if (d.x + d.w > screen.width - 5) d.x = screen.width - d.w - 5;
        d.y = my + yOffset;
        if (d.y + d.h > screen.height - 5) d.y = my - d.h - yOffset;
        if (d.y < 5) d.y = 5;

        return d;
    }

    private void renderMorphingTooltip(GuiGraphics g, TradeEntry entry, int gi, int mx, int my, float dt, boolean isClosing) {
        TooltipData target = calcTooltipData(entry, gi, mx, my);

        if (!isClosing) {
            if (animBgW == 0 || Math.abs(animBgW - target.w) > 20) {
                animBgX = target.x; animBgY = target.y; animBgW = target.w; animBgH = target.h; animThemeColor = target.themeColor;
            } else {
                float ms = 15f;
                animBgX += (target.x - animBgX) * Math.min(1f, dt * ms);
                animBgY += (target.y - animBgY) * Math.min(1f, dt * ms);
                animBgW += (target.w - animBgW) * Math.min(1f, dt * ms);
                animBgH += (target.h - animBgH) * Math.min(1f, dt * ms);
                animThemeColor = screen.lerpColor(animThemeColor, target.themeColor, Math.min(1f, dt * 10f));
            }
        }

        float targetProgress = target.maxP > 0 ? Math.min(1f, (float)target.purchases / target.maxP) : 0f;
        animProgress += (targetProgress - animProgress) * Math.min(1f, dt * 8f);

        if (feedbackScale > 1.0f) {
            feedbackScale += (1.0f - feedbackScale) * Math.min(1f, dt * 12f);
            if (Math.abs(feedbackScale - 1.0f) < 0.01f) feedbackScale = 1.0f;
        }

        int currentShake = 0;
        if (Math.abs(feedbackShake) > 0.1f) {
            currentShake = (int) (Math.sin(Util.getMillis() / 30.0) * feedbackShake);
            feedbackShake *= Math.max(0, 1f - dt * 15f);
        } else feedbackShake = 0f;

        float scale = isClosing ? HudAnimUtil.easeInCubic(tooltipAlpha) : HudAnimUtil.easeOutCubic(tooltipAlpha);
        if (scale < 0.01f) return;

        int drawX = (int)animBgX + currentShake, drawY = (int)animBgY, drawW = (int)animBgW, drawH = (int)animBgH;
        float finalScale = isClosing ? scale : (scale * feedbackScale);

        g.pose().pushPose();
        g.pose().translate(0, 0, 400f);
        float centerX = drawX + drawW / 2f, centerY = drawY + drawH / 2f;
        g.pose().translate(centerX, centerY, 0); g.pose().scale(finalScale, finalScale, 1f); g.pose().translate(-centerX, -centerY, 0);

        int baseA = (int) (0x99 * tooltipAlpha);
        int borderA = (int) (0xFF * tooltipAlpha);
        int currentThemeColor = animThemeColor;

        if (gi == screen.getLastClickedGi()) {
            if (screen.isFeedbackSuccess() && screen.getFeedbackAnim() > 0) {
                borderA = Math.min(255, borderA + (int)(180 * screen.getFeedbackAnim()));
                currentThemeColor = screen.lerpColor(animThemeColor, 0x55FF55, screen.getFeedbackAnim());
            } else if (!screen.isFeedbackSuccess() && (screen.getFeedbackAnim() > 0 || target.showShortfall)) {
                float syncBreath = (float) (Math.sin(Util.getMillis() / 150.0) * 0.5 + 0.5);
                float intensity = Math.max(screen.getFeedbackAnim(), target.showShortfall ? (syncBreath * 0.6f + 0.4f) : 0f);
                borderA = Math.min(255, borderA + (int)(180 * intensity));
                currentThemeColor = screen.lerpColor(animThemeColor, 0xFF3333, intensity);
            }
        }

        int borderColor = (borderA << 24) | (currentThemeColor & 0xFFFFFF);
        g.fill(drawX, drawY, drawX + drawW, drawY + drawH, (baseA << 24) | 0x050508);
        g.fillGradient(drawX, drawY, drawX + drawW, drawY + drawH, HudAnimUtil.withAlpha(currentThemeColor, (int)(65 * tooltipAlpha)), HudAnimUtil.withAlpha(currentThemeColor, 0));
        HudAnimUtil.drawFrame(g, drawX, drawY, drawW, drawH, 1, borderColor);

        boolean useScissor = Math.abs(finalScale - 1.0f) < 0.01f && currentShake == 0;
        if (useScissor) g.enableScissor(drawX, drawY, drawX + drawW, drawY + drawH);

        int padding = 10, currentY = drawY + padding;
        int safeAlpha = (int)(255 * scale);
        int titleColor = HudAnimUtil.withAlpha(animThemeColor, safeAlpha);

        if (entry.getRewardIcon() != null) {
            screen.drawAdaptiveIcon(g, entry.getRewardIcon(), drawX + padding, currentY, 16, 16, scale);
            g.drawString(font, entry.getDisplayName(), drawX + padding + 22, currentY + 4, titleColor, true);
        } else {
            ItemStack stack = screen.getIconStackForEntry(entry);
            if (!stack.isEmpty()) { g.renderItem(stack, drawX + padding, currentY); g.drawString(font, entry.getDisplayName(), drawX + padding + 22, currentY + 4, titleColor, true); }
            else g.drawString(font, entry.getDisplayName(), drawX + padding, currentY + 4, titleColor, true);
        }
        currentY += 20;

        if (!target.descLines.isEmpty() || target.maxP > 0 || target.onCd || target.showShortfall) {
            if (target.showShortfall) {
                g.fill(drawX + padding, currentY, drawX + target.w - padding, currentY + 1, HudAnimUtil.withAlpha(0xFF3333, (int)(safeAlpha * 0.2f)));
                g.fill(drawX + padding, currentY, drawX + padding + 40, currentY + 1, HudAnimUtil.withAlpha(0xFF3333, safeAlpha));
                currentY += 6;
                g.drawString(font, Component.translatable("arc_quest.gui.trade.tooltip.shortfall_summary"), drawX + padding, currentY, HudAnimUtil.withAlpha(0xFF5555, safeAlpha), true);
                currentY += 12;
            } else {
                g.fill(drawX + padding, currentY, drawX + target.w - padding, currentY + 1, HudAnimUtil.withAlpha(animThemeColor, (int)(safeAlpha * 0.3f)));
                currentY += 6;
            }
        }

        if (target.showShortfall) {
            for (CostShortfallLine sf : target.shortfalls) {
                g.fill(drawX + padding, currentY + 3, drawX + padding + 2, currentY + 7, HudAnimUtil.withAlpha(0xFF4444, safeAlpha));
                g.drawString(font, sf.label(), drawX + padding + 6, currentY, HudAnimUtil.withAlpha(0xDDDDDD, safeAlpha), true);
                if (sf.missing() > 0) {
                    String missingTxt = "-" + sf.missing();
                    g.drawString(font, missingTxt, drawX + target.w - padding - font.width(missingTxt), currentY, HudAnimUtil.withAlpha(0xFF3333, safeAlpha), true);
                }
                currentY += 10;
                if (sf.required() > 0) {
                    String metaTxt = sf.owned() + " / " + sf.required();
                    g.pose().pushPose(); g.pose().translate(drawX + padding + 6, currentY, 0); g.pose().scale(0.8f, 0.8f, 1f);
                    g.drawString(font, metaTxt, 0, 0, HudAnimUtil.withAlpha(0x888888, safeAlpha), false); g.pose().popPose();
                    int barX = drawX + padding + 6 + (int)(font.width(metaTxt) * 0.8f) + 6, barW = target.w - padding * 2 - (barX - drawX) - 5;
                    if (barW > 10) {
                        int fillW = (int)(barW * Math.min(1f, (float)sf.owned() / sf.required()));
                        g.fill(barX, currentY + 2, barX + barW, currentY + 4, HudAnimUtil.withAlpha(0x442222, safeAlpha));
                        if (fillW > 0) g.fill(barX, currentY + 2, barX + fillW, currentY + 4, HudAnimUtil.withAlpha(0xAA3333, safeAlpha));
                    }
                }
                currentY += 8;
            }
        } else if (!target.descLines.isEmpty()) {
            for (var line : target.descLines) { g.drawString(font, line, drawX + padding, currentY, HudAnimUtil.withAlpha(0xBBBBBB, safeAlpha), true); currentY += font.lineHeight; }
            currentY += 4;
        }

        if (!target.showShortfall && target.maxP > 0) {
            g.drawString(font, Component.translatable("arc_quest.gui.trade.tooltip.limit", target.purchases, target.maxP).getString(), drawX + padding, currentY, HudAnimUtil.withAlpha(0xDDDDDD, safeAlpha), true);
            currentY += font.lineHeight + 2;
            int barW = target.w - padding * 2;
            g.fill(drawX + padding, currentY, drawX + padding + barW, currentY + 3, HudAnimUtil.withAlpha(0x333333, safeAlpha));
            if (animProgress > 0.01f) {
                int filledW = (int)(barW * animProgress);
                g.fill(drawX + padding, currentY, drawX + padding + filledW, currentY + 3, HudAnimUtil.withAlpha(animProgress >= 0.99f ? 0xAA3333 : (animProgress >= 0.75f ? 0xDD9933 : 0x33AA33), safeAlpha));
                if (filledW > 2) g.fill(drawX + padding + filledW - 2, currentY, drawX + padding + filledW, currentY + 3, HudAnimUtil.withAlpha(0xFFFFFF, (int)(safeAlpha * 0.6f)));
            }
            currentY += 9;
        }

        if (!target.showShortfall && target.onCd) g.drawString(font, Component.translatable("arc_quest.gui.trade.tooltip.cooldown", ClientTradeCache.INSTANCE.getCooldownText(screen.getShopId(), gi)).getString(), drawX + padding, currentY, HudAnimUtil.withAlpha(0xFF5555, safeAlpha), true);

        if (useScissor) g.disableScissor();
        g.pose().popPose();
    }
}