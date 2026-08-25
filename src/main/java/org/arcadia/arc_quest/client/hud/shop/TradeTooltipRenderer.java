package org.arcadia.arc_quest.client.hud.shop;


import org.arcadia.arc_quest.client.hud.HudText;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.trade.api.CostShortfallLine;
import org.arcadia.arc_quest.trade.api.TradeEntry;
import org.arcadia.arc_quest.trade.network.ClientTradeCache;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class TradeTooltipRenderer {

    private static final float HOVER_DELAY = 0.05f;
    private final AbstractTradeScreen screen;
    private final Font font;
    private float hoverTimer = 0f;
    private TradeEntry activeEntry = null;
    private TradeEntry hoveredEntry = null;

    private float tooltipAlpha = 0f;
    private float animBgX = 0, animBgY = 0, animBgW = 0, animBgH = 0;
    private int animThemeColor = -1;
    private float animProgress = 0f;
    private float feedbackScale = 1.0f;
    private float feedbackShake = 0f;

    // --- 分页系统状态 ---
    private int currentPage = 0; // 0: 交易数据 (Trade Data), 1: 物品数据 (Item Data)
    private boolean wasAKeyDown = false;
    private boolean wasDKeyDown = false;
    private float pageFadeAnim = 1.0f;

    public TradeTooltipRenderer(AbstractTradeScreen screen, Font font) {
        this.screen = screen;
        this.font = font;
    }

    public void triggerTradeSuccess() {
        feedbackScale = 1.15f;
    }

    public void triggerTradeFail() {
        feedbackShake = 6f;
    }

    public void updateAndRender(GuiGraphics g, TradeEntry newHovered, int mx, int my, float dt, boolean isClosing) {
        if (newHovered != hoveredEntry) {
            currentPage = 0;
            pageFadeAnim = 1.0f;
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

        if (activeEntry != null && tooltipAlpha > 0.5f) {
            long window = Minecraft.getInstance().getWindow().getWindow();
            boolean isAKeyDown = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_A) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT);
            boolean isDKeyDown = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_D) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT);

            boolean pageChanged = false;
            if (isAKeyDown && !wasAKeyDown && currentPage > 0) {
                currentPage--;
                pageChanged = true;
            }
            if (isDKeyDown && !wasDKeyDown && currentPage < 1) {
                currentPage++;
                pageChanged = true;
            }
            wasAKeyDown = isAKeyDown;
            wasDKeyDown = isDKeyDown;

            if (pageChanged) {
                pageFadeAnim = 0f;
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.3f, 1.5f);
                }
            }
        }

        if (pageFadeAnim < 1.0f) {
            pageFadeAnim += dt * 12f;
            if (pageFadeAnim > 1.0f) pageFadeAnim = 1.0f;
        }

        float targetAlpha = (hoveredEntry != null && hoverTimer >= HOVER_DELAY && !isClosing) ? 1f : 0f;
        tooltipAlpha = HudAnimUtil.smoothHalfLife(tooltipAlpha, targetAlpha, 0.05f, dt);

        if (tooltipAlpha > 0.02f && activeEntry != null) {
            int gi = new ArrayList<>(screen.getShop().getAllEntries()).indexOf(activeEntry);
            if (gi != -1) renderMorphingTooltip(g, activeEntry, gi, mx, my, dt, isClosing);
        } else {
            animBgW = 0;
            activeEntry = null;
        }
    }

    public void forceRefresh() {
        if (activeEntry != null && tooltipAlpha > 0.1f) animBgW = 0;
    }

    private TooltipData calcTooltipData(TradeEntry entry, int gi, int mx, int my) {
        TooltipData d = new TooltipData();
        d.themeColor = screen.getThemeColorForEntry(entry);
        ClientTradeCache cache = ClientTradeCache.INSTANCE;
        d.maxP = entry.getMaxPurchases();
        d.purchases = cache.getPurchaseCount(screen.getShopId(), gi);
        d.onCd = cache.isOnCooldown(screen.getShopId(), gi);

        Minecraft mc = Minecraft.getInstance();
        ItemStack stack = screen.getIconStackForEntry(entry);
        d.hasItem = !stack.isEmpty();
        d.vanillaLines = new ArrayList<>();

        if (d.hasItem && mc.player != null) {
            d.vanillaLines.addAll(stack.getTooltipLines(mc.player, mc.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL));
        }

        int padding = 10;
        d.extraDescLines = entry.getDescription() != null ? font.split(entry.getDescription(), 200) : new ArrayList<>();
        d.shortfalls = cache.getShortfall(screen.getShopId(), entry.getEntryId());

        ClientTradeCache.FeedbackSnapshot feedback = cache.feedbackSnapshot(screen.getShopId());
        boolean thisEntryFailed = feedback != null && feedback.lastFailedEntryId() != null && feedback.lastFailedEntryId().equals(entry.getEntryId());
        d.showShortfall = thisEntryFailed && !d.shortfalls.isEmpty();

        d.hasTradeInfo = d.showShortfall || d.maxP > 0 || d.onCd || !d.extraDescLines.isEmpty();
        d.hasItemInfo = d.vanillaLines.size() > 1;

        int totalW = 188;

        if (!d.vanillaLines.isEmpty() && (d.hasItem || entry.getRewardIcon() != null)) {
            totalW = Math.max(totalW, font.width(d.vanillaLines.get(0)) + 40);
        } else {
            totalW = Math.max(totalW, font.width(entry.getDisplayName()) + 40);
        }

        if (currentPage == 0) {
            if (d.hasTradeInfo) {
                for (var line : d.extraDescLines) totalW = Math.max(totalW, font.width(line) + padding * 2);
                if (d.showShortfall) {
                    totalW = Math.max(totalW, font.width(Component.translatable("arc_quest.gui.trade.tooltip.shortfall_summary")) + 40);
                    for (CostShortfallLine sf : d.shortfalls)
                        totalW = Math.max(totalW, font.width(sf.label()) + font.width("-" + sf.missing()) + 50);
                }
            }
        } else {
            if (!d.hasItemInfo) {
                totalW = Math.max(totalW, font.width(HudText.of("trade.no_item_data")) + padding * 2);
            } else {
                for (int i = 1; i < d.vanillaLines.size(); i++) {
                    totalW = Math.max(totalW, font.width(d.vanillaLines.get(i)) + padding * 2 + 12);
                }
            }
        }
        d.w = totalW;

        int totalH = padding * 2 + 16;

        if (currentPage == 0) {
            if (d.hasTradeInfo) {
                if (!d.extraDescLines.isEmpty()) totalH += 6 + d.extraDescLines.size() * font.lineHeight;
                if (d.showShortfall) totalH += 18 + (d.shortfalls.size() * 18);
                else {
                    if (d.maxP > 0) totalH += 18;
                    if (d.onCd) totalH += font.lineHeight + 4;
                }
            }
        } else {
            if (!d.hasItemInfo) {
                totalH += font.lineHeight + 4;
            } else {
                totalH += (d.vanillaLines.size() - 1) * font.lineHeight;
            }
        }
        totalH += 18;

        d.h = totalH;

        int yOffset = 18;
        d.x = mx - (d.w / 2);
        if (d.x < 5) d.x = 5;
        if (d.x + d.w > screen.width - 5) d.x = screen.width - d.w - 5;
        d.y = my + yOffset;
        if (d.y + d.h > screen.height - 5) d.y = my - d.h - yOffset;
        if (d.y < 5) d.y = 5;

        return d;
    }

    private void renderMorphingTooltip(GuiGraphics g, TradeEntry entry, int gi, int mx, int my, float dt, boolean isClosing) {
        TooltipData target = calcTooltipData(entry, gi, mx, my);

        if (!isClosing) {
            if (animBgW == 0 || Math.abs(animBgW - target.w) > 40) {
                animBgX = target.x;
                animBgY = target.y;
                animBgW = target.w;
                animBgH = target.h;
                animThemeColor = target.themeColor;
            } else {
                float ms = 18f;
                animBgX = HudAnimUtil.smoothHalfLife(animBgX, target.x, 0.04f, dt);
                animBgY = HudAnimUtil.smoothHalfLife(animBgY, target.y, 0.04f, dt);
                animBgW = HudAnimUtil.smoothHalfLife(animBgW, target.w, 0.04f, dt);
                animBgH = HudAnimUtil.smoothHalfLife(animBgH, target.h, 0.04f, dt);
                animThemeColor = screen.lerpColor(animThemeColor, target.themeColor, HudAnimUtil.expDecayFactor(10f, dt));
            }
        }

        float targetProgress = target.maxP > 0 ? Math.min(1f, (float) target.purchases / target.maxP) : 0f;
        animProgress = HudAnimUtil.smoothHalfLife(animProgress, targetProgress, 0.08f, dt);

        if (feedbackScale > 1.0f) {
            feedbackScale = HudAnimUtil.smoothHalfLife(feedbackScale, 1.0f, 0.06f, dt);
            if (Math.abs(feedbackScale - 1.0f) < 0.01f) feedbackScale = 1.0f;
        }

        int currentShake = 0;
        if (Math.abs(feedbackShake) > 0.1f) {
            currentShake = (int) (Math.sin(Util.getMillis() / 30.0) * feedbackShake);
            feedbackShake = HudAnimUtil.smoothHalfLife(feedbackShake, 0f, 0.05f, dt);
        } else feedbackShake = 0f;

        float scale = isClosing ? HudAnimUtil.easeInCubic(tooltipAlpha) : HudAnimUtil.easeOutCubic(tooltipAlpha);
        if (scale < 0.01f) return;

        int drawX = (int) animBgX + currentShake, drawY = (int) animBgY, drawW = (int) animBgW, drawH = (int) animBgH;
        float finalScale = isClosing ? scale : (scale * feedbackScale);

        g.pose().pushPose();
        g.pose().translate(0, 0, 400f);
        float centerX = drawX + drawW / 2f, centerY = drawY + drawH / 2f;
        g.pose().translate(centerX, centerY, 0);
        g.pose().scale(finalScale, finalScale, 1f);
        g.pose().translate(-centerX, -centerY, 0);

        int baseA = (int) (0x99 * tooltipAlpha);
        int borderA = (int) (0xFF * tooltipAlpha);
        int currentThemeColor = animThemeColor;

        if (gi == screen.getLastClickedGi()) {
            if (screen.isFeedbackSuccess() && screen.getFeedbackAnim() > 0) {
                borderA = Math.min(255, borderA + (int) (180 * screen.getFeedbackAnim()));
                currentThemeColor = screen.lerpColor(animThemeColor, 0x55FF55, screen.getFeedbackAnim());
            } else if (!screen.isFeedbackSuccess() && screen.getFeedbackAnim() > 0) {
                float intensity = screen.getFeedbackAnim();
                borderA = Math.min(255, borderA + (int) (180 * intensity));
                currentThemeColor = screen.lerpColor(animThemeColor, 0xFF3333, intensity);
            } else if (target.showShortfall) {
                borderA = Math.min(255, borderA + (int) (40 * tooltipAlpha));
                currentThemeColor = screen.lerpColor(animThemeColor, 0xAA4444, 0.35f);
            }
        }

        int borderColor = (borderA << 24) | (currentThemeColor & 0xFFFFFF);

        // ==========================================
        // 【核心修复】先画底板和边框！
        // 它们定义了绝对轮廓，不需要被 Scissor 裁剪，绝对不会被啃掉边缘！
        // ==========================================
        g.fill(drawX, drawY, drawX + drawW, drawY + drawH, (baseA << 24) | 0x050508);
        g.fillGradient(drawX, drawY, drawX + drawW, drawY + drawH, HudAnimUtil.withAlpha(currentThemeColor, (int) (65 * tooltipAlpha)), HudAnimUtil.withAlpha(currentThemeColor, 0));
        HudAnimUtil.drawFrame(g, drawX, drawY, drawW, drawH, 1, borderColor);

        // 开启裁剪区域：仅用来限制内部的长文本和进度条，防止变形时溢出
        boolean useScissor = Math.abs(finalScale - 1.0f) < 0.01f && currentShake == 0;
        if (useScissor) g.enableScissor(drawX, drawY, drawX + drawW, drawY + drawH);

        int padding = 10;
        int headerY = drawY + padding;
        int currentY = headerY;
        int safeAlpha = (int) (255 * scale);
        int contentAlpha = (int) (safeAlpha * HudAnimUtil.easeOutCubic(pageFadeAnim));

        // --- 头部文本 ---
        Component titleLine = target.vanillaLines.isEmpty() ? entry.getDisplayName() : target.vanillaLines.get(0);
        int titleTextX = drawX + padding;
        if (entry.getRewardIcon() != null || target.hasItem) {
            titleTextX += 22; // 为将要在 Pass 2 渲染的物品图标预留空间
        }
        g.drawString(font, titleLine, titleTextX, currentY + 4, HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha), true);

        currentY += 20;

        // --- PAGE 0: 交易特有信息 ---
        if (currentPage == 0 && target.hasTradeInfo) {
            if (!target.extraDescLines.isEmpty()) {
                for (var line : target.extraDescLines) {
                    g.drawString(font, line, drawX + padding, currentY, HudAnimUtil.withAlpha(0xBBBBBB, contentAlpha), true);
                    currentY += font.lineHeight;
                }
                currentY += 4;
            }

            if (target.maxP > 0 || target.onCd || target.showShortfall) {
                if (target.showShortfall) {
                    g.fill(drawX + padding, currentY, drawX + drawW - padding, currentY + 1, HudAnimUtil.withAlpha(0xFF3333, (int) (contentAlpha * 0.2f)));
                    g.fill(drawX + padding, currentY, drawX + padding + 40, currentY + 1, HudAnimUtil.withAlpha(0xFF3333, contentAlpha));
                    currentY += 6;
                    g.drawString(font, Component.translatable("arc_quest.gui.trade.tooltip.shortfall_summary"), drawX + padding, currentY, HudAnimUtil.withAlpha(0xFF5555, contentAlpha), true);
                    currentY += 12;
                } else {
                    g.fill(drawX + padding, currentY, drawX + drawW - padding, currentY + 1, HudAnimUtil.withAlpha(animThemeColor, (int) (contentAlpha * 0.3f)));
                    currentY += 6;
                }
            }

            if (target.showShortfall) {
                for (CostShortfallLine sf : target.shortfalls) {
                    g.fill(drawX + padding, currentY + 3, drawX + padding + 2, currentY + 7, HudAnimUtil.withAlpha(0xFF4444, contentAlpha));
                    g.drawString(font, sf.label(), drawX + padding + 6, currentY, HudAnimUtil.withAlpha(0xDDDDDD, contentAlpha), true);
                    if (sf.missing() > 0) {
                        String missingTxt = "-" + sf.missing();
                        g.drawString(font, missingTxt, drawX + drawW - padding - font.width(missingTxt), currentY, HudAnimUtil.withAlpha(0xFF3333, contentAlpha), true);
                    }
                    currentY += 10;
                    if (sf.required() > 0) {
                        String metaTxt = sf.owned() + " / " + sf.required();
                        g.pose().pushPose();
                        g.pose().translate(drawX + padding + 6, currentY, 0);
                        g.pose().scale(0.8f, 0.8f, 1f);
                        g.drawString(font, metaTxt, 0, 0, HudAnimUtil.withAlpha(0x888888, contentAlpha), false);
                        g.pose().popPose();

                        int barX = drawX + padding + 6 + (int) (font.width(metaTxt) * 0.8f) + 6, barW = drawW - padding * 2 - (barX - drawX) - 5;
                        if (barW > 10) {
                            int fillW = (int) (barW * Math.min(1f, (float) sf.owned() / sf.required()));
                            g.fill(barX, currentY + 2, barX + barW, currentY + 4, HudAnimUtil.withAlpha(0x442222, contentAlpha));
                            if (fillW > 0)
                                g.fill(barX, currentY + 2, barX + fillW, currentY + 4, HudAnimUtil.withAlpha(0xAA3333, contentAlpha));
                        }
                    }
                    currentY += 8;
                }
            }

            if (!target.showShortfall && target.maxP > 0) {
                g.drawString(font, Component.translatable("arc_quest.gui.trade.tooltip.limit", target.purchases, target.maxP).getString(), drawX + padding, currentY, HudAnimUtil.withAlpha(0xDDDDDD, contentAlpha), true);
                currentY += font.lineHeight + 2;
                int barW = drawW - padding * 2;
                g.fill(drawX + padding, currentY, drawX + padding + barW, currentY + 3, HudAnimUtil.withAlpha(0x333333, contentAlpha));
                if (animProgress > 0.01f) {
                    int filledW = (int) (barW * animProgress);
                    g.fill(drawX + padding, currentY, drawX + padding + filledW, currentY + 3, HudAnimUtil.withAlpha(animProgress >= 0.99f ? 0xAA3333 : (animProgress >= 0.75f ? 0xDD9933 : 0x33AA33), contentAlpha));
                    if (filledW > 2)
                        g.fill(drawX + padding + filledW - 2, currentY, drawX + padding + filledW, currentY + 3, HudAnimUtil.withAlpha(0xFFFFFF, (int) (contentAlpha * 0.6f)));
                }
                currentY += 9;
            }

            if (!target.showShortfall && target.onCd) {
                g.drawString(font, Component.translatable("arc_quest.gui.trade.tooltip.cooldown", ClientTradeCache.INSTANCE.getCooldownText(screen.getShopId(), gi)).getString(), drawX + padding, currentY, HudAnimUtil.withAlpha(0xFF5555, contentAlpha), true);
            }
        }

        // --- PAGE 1: 物品原版信息 ---
        if (currentPage == 1) {
            if (!target.hasItemInfo) {
                Component emptyHint = HudText.of("trade.no_item_data").withStyle(Style.EMPTY.withColor(0x555555));
                g.drawString(font, emptyHint, drawX + drawW / 2 - font.width(emptyHint) / 2, currentY + 2, HudAnimUtil.withAlpha(0x555555, contentAlpha), false);
            } else {
                for (int i = 1; i < target.vanillaLines.size(); i++) {
                    g.drawString(font, target.vanillaLines.get(i), drawX + padding, currentY, HudAnimUtil.withAlpha(0xFFFFFF, contentAlpha), true);
                    currentY += font.lineHeight;
                }
            }
        }

        // --- 底部强制 UI 操作指引 ---
        int navY = drawY + drawH - 14;
        g.fill(drawX + padding, navY - 4, drawX + drawW - padding, navY - 3, HudAnimUtil.withAlpha(animThemeColor, (int) (safeAlpha * 0.3f)));

        if (currentPage == 0) {
            Component hint = HudText.of("trade.item_data").withStyle(Style.EMPTY.withColor(0xAAAAAA).withBold(true));
            Component dot = Component.literal("● ○").withStyle(Style.EMPTY.withColor(animThemeColor));
            g.drawString(font, dot, drawX + padding, navY, HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha), true);
            g.drawString(font, hint, drawX + drawW - padding - font.width(hint), navY, HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha), true);
        } else {
            Component hint = HudText.of("trade.trade_data").withStyle(Style.EMPTY.withColor(0xAAAAAA).withBold(true));
            Component dot = Component.literal("○ ●").withStyle(Style.EMPTY.withColor(animThemeColor));
            g.drawString(font, hint, drawX + padding, navY, HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha), true);
            g.drawString(font, dot, drawX + drawW - padding - font.width(dot), navY, HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha), true);
        }

        // ==========================================
        // PASS 2: 3D 昂贵通道 (渲染物品图标)
        // ==========================================
        if (entry.getRewardIcon() != null) {
            screen.drawAdaptiveIcon(g, entry.getRewardIcon(), drawX + padding, headerY, 16, 16, scale);
        } else if (target.hasItem) {
            ItemStack stack = screen.getIconStackForEntry(entry);
            g.renderFakeItem(stack, drawX + padding, headerY);
        }

        if (useScissor) g.disableScissor();
        g.pose().popPose();
    }

    private static class TooltipData {
        int x, y, w, h;
        boolean hasItem;
        List<Component> vanillaLines;
        List<FormattedCharSequence> extraDescLines;
        boolean onCd;
        int purchases, maxP, themeColor;
        boolean showShortfall;
        List<CostShortfallLine> shortfalls;

        boolean hasTradeInfo;
        boolean hasItemInfo;
    }
}