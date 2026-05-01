package org.arcadia.arc_quest.client.hud.shop;

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
    private float pageFadeAnim = 1.0f; // 切换页面时的淡入动画

    public TradeTooltipRenderer(AbstractTradeScreen screen, Font font) {
        this.screen = screen;
        this.font = font;
    }

    public void triggerTradeSuccess() {
        this.feedbackScale = 1.15f;
    }

    public void triggerTradeFail() {
        this.feedbackShake = 6f;
    }

    public void updateAndRender(GuiGraphics g, TradeEntry newHovered, int mx, int my, float dt, boolean isClosing) {
        // 切换悬停物品时重置状态
        if (newHovered != hoveredEntry) {
            currentPage = 0; // 默认永远回到第0页 (Trade Data)
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

        // --- 处理 A/D 翻页按键 (强制双页) ---
        if (activeEntry != null && tooltipAlpha > 0.5f) {
            long window = Minecraft.getInstance().getWindow().getWindow();
            boolean isAKeyDown = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_A) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT);
            boolean isDKeyDown = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_D) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT);

            boolean pageChanged = false;
            if (isAKeyDown && !wasAKeyDown && currentPage > 0) {
                currentPage--; // 1 -> 0
                pageChanged = true;
            }
            if (isDKeyDown && !wasDKeyDown && currentPage < 1) {
                currentPage++; // 0 -> 1
                pageChanged = true;
            }
            wasAKeyDown = isAKeyDown;
            wasDKeyDown = isDKeyDown;

            if (pageChanged) {
                pageFadeAnim = 0f; // 触发淡入重载动画
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
        tooltipAlpha += (targetAlpha - tooltipAlpha) * Math.min(1f, dt * 15f);

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

        // 占位符逻辑判断
        d.hasTradeInfo = d.showShortfall || d.maxP > 0 || d.onCd || !d.extraDescLines.isEmpty();
        d.hasItemInfo = d.vanillaLines.size() > 1;

        // --- 动态计算当前页面的宽度 ---
        int totalW = 188; // 基础最小宽度

        // 头部宽度计算 (两个页面都显示)
        if (!d.vanillaLines.isEmpty() && (d.hasItem || entry.getRewardIcon() != null)) {
            totalW = Math.max(totalW, font.width(d.vanillaLines.get(0)) + 40);
        } else {
            totalW = Math.max(totalW, font.width(entry.getDisplayName()) + 40);
        }

        if (currentPage == 0) {
            // Page 0: Trade Data (移除空占位符，仅当有数据时扩充宽度)
            if (d.hasTradeInfo) {
                for (var line : d.extraDescLines) totalW = Math.max(totalW, font.width(line) + padding * 2);
                if (d.showShortfall) {
                    totalW = Math.max(totalW, font.width(Component.translatable("arc_quest.gui.trade.tooltip.shortfall_summary")) + 40);
                    for (CostShortfallLine sf : d.shortfalls)
                        totalW = Math.max(totalW, font.width(sf.label()) + font.width("-" + sf.missing()) + 50);
                }
            }
        } else {
            // Page 1: Item Data
            if (!d.hasItemInfo) {
                totalW = Math.max(totalW, font.width("[ NO ADDITIONAL ITEM DATA ]") + padding * 2);
            } else {
                for (int i = 1; i < d.vanillaLines.size(); i++) {
                    totalW = Math.max(totalW, font.width(d.vanillaLines.get(i)) + padding * 2 + 12);
                }
            }
        }
        d.w = totalW;

        // --- 动态计算当前页面的高度 ---
        int totalH = padding * 2 + 16; // 基础上下边距 + 图标高度

        if (currentPage == 0) {
            // Page 0: Trade Data (移除空占位符，仅当有数据时增加高度)
            if (d.hasTradeInfo) {
                if (!d.extraDescLines.isEmpty()) totalH += 6 + d.extraDescLines.size() * font.lineHeight;
                if (d.showShortfall) totalH += 18 + (d.shortfalls.size() * 18);
                else {
                    if (d.maxP > 0) totalH += 18;
                    if (d.onCd) totalH += font.lineHeight + 4;
                }
            }
        } else {
            // Page 1: Item Data
            if (!d.hasItemInfo) {
                totalH += font.lineHeight + 4; // 占位符高度
            } else {
                totalH += (d.vanillaLines.size() - 1) * font.lineHeight; // 原版其他行高度
            }
        }
        totalH += 18; // 强制预留底部导航栏的高度

        d.h = totalH;

        // 位置计算
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

        // 外框变形逻辑
        if (!isClosing) {
            if (animBgW == 0 || Math.abs(animBgW - target.w) > 40) {
                animBgX = target.x; animBgY = target.y; animBgW = target.w; animBgH = target.h;
                animThemeColor = target.themeColor;
            } else {
                float ms = 18f; // 加快变形速度
                animBgX += (target.x - animBgX) * Math.min(1f, dt * ms);
                animBgY += (target.y - animBgY) * Math.min(1f, dt * ms);
                animBgW += (target.w - animBgW) * Math.min(1f, dt * ms);
                animBgH += (target.h - animBgH) * Math.min(1f, dt * ms);
                animThemeColor = screen.lerpColor(animThemeColor, target.themeColor, Math.min(1f, dt * 10f));
            }
        }

        float targetProgress = target.maxP > 0 ? Math.min(1f, (float) target.purchases / target.maxP) : 0f;
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

        int drawX = (int) animBgX + currentShake, drawY = (int) animBgY, drawW = (int) animBgW, drawH = (int) animBgH;
        float finalScale = isClosing ? scale : (scale * feedbackScale);

        // --- 基础渲染 ---
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
        g.fill(drawX, drawY, drawX + drawW, drawY + drawH, (baseA << 24) | 0x050508);
        g.fillGradient(drawX, drawY, drawX + drawW, drawY + drawH, HudAnimUtil.withAlpha(currentThemeColor, (int) (65 * tooltipAlpha)), HudAnimUtil.withAlpha(currentThemeColor, 0));
        HudAnimUtil.drawFrame(g, drawX, drawY, drawW, drawH, 1, borderColor);

        boolean useScissor = Math.abs(finalScale - 1.0f) < 0.01f && currentShake == 0;
        if (useScissor) g.enableScissor(drawX, drawY, drawX + drawW, drawY + drawH);

        int padding = 10, currentY = drawY + padding;
        int safeAlpha = (int) (255 * scale);
        // 内容渐变透明度（切换页面时文本拥有独立淡入）
        int contentAlpha = (int) (safeAlpha * HudAnimUtil.easeOutCubic(pageFadeAnim));

        // --- 头部：图标与标题 (所有页面固定显示) ---
        Component titleLine = target.vanillaLines.isEmpty() ? entry.getDisplayName() : target.vanillaLines.get(0);
        if (entry.getRewardIcon() != null) {
            screen.drawAdaptiveIcon(g, entry.getRewardIcon(), drawX + padding, currentY, 16, 16, scale);
            g.drawString(font, titleLine, drawX + padding + 22, currentY + 4, HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha), true);
        } else if (target.hasItem) {
            ItemStack stack = screen.getIconStackForEntry(entry);
            g.renderItem(stack, drawX + padding, currentY);
            g.drawString(font, titleLine, drawX + padding + 22, currentY + 4, HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha), true);
        } else {
            g.drawString(font, titleLine, drawX + padding, currentY + 4, HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha), true);
        }
        currentY += 20;

        // --- PAGE 0: 交易特有信息 (默认主页) ---
        if (currentPage == 0) {
            if (target.hasTradeInfo) { // 仅当拥有交易数据时才渲染内容
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
                                if (fillW > 0) g.fill(barX, currentY + 2, barX + fillW, currentY + 4, HudAnimUtil.withAlpha(0xAA3333, contentAlpha));
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
                        if (filledW > 2) g.fill(drawX + padding + filledW - 2, currentY, drawX + padding + filledW, currentY + 3, HudAnimUtil.withAlpha(0xFFFFFF, (int) (contentAlpha * 0.6f)));
                    }
                    currentY += 9;
                }

                if (!target.showShortfall && target.onCd)
                    g.drawString(font, Component.translatable("arc_quest.gui.trade.tooltip.cooldown", ClientTradeCache.INSTANCE.getCooldownText(screen.getShopId(), gi)).getString(), drawX + padding, currentY, HudAnimUtil.withAlpha(0xFF5555, contentAlpha), true);
            }
        }

        // --- PAGE 1: 物品原版信息 ---
        if (currentPage == 1) {
            if (!target.hasItemInfo) {
                // 如果没有原版Lore、属性等信息，显示科技感占位符
                Component emptyHint = Component.literal("[ NO ADDITIONAL ITEM DATA ]").withStyle(Style.EMPTY.withColor(0x555555));
                g.drawString(font, emptyHint, drawX + drawW / 2 - font.width(emptyHint) / 2, currentY + 2, HudAnimUtil.withAlpha(0x555555, contentAlpha), false);
                currentY += font.lineHeight + 4;
            } else {
                for (int i = 1; i < target.vanillaLines.size(); i++) {
                    g.drawString(font, target.vanillaLines.get(i), drawX + padding, currentY, HudAnimUtil.withAlpha(0xFFFFFF, contentAlpha), true);
                    currentY += font.lineHeight;
                }
                currentY += 4;
            }
        }

        // --- 底部强制 UI 操作指引 ---
        int navY = drawY + drawH - 14; // 固定在底部
        g.fill(drawX + padding, navY - 4, drawX + drawW - padding, navY - 3, HudAnimUtil.withAlpha(animThemeColor, (int) (safeAlpha * 0.3f)));

        if (currentPage == 0) {
            // 当前是 Trade Data, 提示可以按 D 查看 Item Data
            Component hint = Component.literal("ITEM DATA [D] ▶").withStyle(Style.EMPTY.withColor(0xAAAAAA).withBold(true));
            Component dot = Component.literal("● ○").withStyle(Style.EMPTY.withColor(animThemeColor));
            g.drawString(font, dot, drawX + padding, navY, HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha), true);
            g.drawString(font, hint, drawX + drawW - padding - font.width(hint), navY, HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha), true);
        } else {
            // 当前是 Item Data, 提示可以按 A 返回 Trade Data
            Component hint = Component.literal("◀ [A] TRADE DATA").withStyle(Style.EMPTY.withColor(0xAAAAAA).withBold(true));
            Component dot = Component.literal("○ ●").withStyle(Style.EMPTY.withColor(animThemeColor));
            g.drawString(font, hint, drawX + padding, navY, HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha), true);
            g.drawString(font, dot, drawX + drawW - padding - font.width(dot), navY, HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha), true);
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

        // 用于判断是否需要渲染科技感空占位符
        boolean hasTradeInfo;
        boolean hasItemInfo;
    }
}