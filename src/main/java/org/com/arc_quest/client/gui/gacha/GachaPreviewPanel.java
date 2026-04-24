package org.com.arc_quest.client.gui.gacha;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.com.arc_quest.client.gui.HudAnimUtil;
import org.com.arc_quest.client.gui.HudRenderUtil;
import org.com.arc_quest.trade.api.CostShortfallLine;
import org.com.arc_quest.trade.gacha.api.GachaItem;
import org.com.arc_quest.trade.gacha.network.ClientGachaCache;

import java.util.ArrayList;
import java.util.List;

public class GachaPreviewPanel {

    // 过渡到 Roller
    private static final float WIPE_SPLIT_MULT = 1.4f;       // 上下撕裂位移幅度倍率
    private static final float WIPE_FADE_SPEED = 2.8f;       // 其余元素透明度衰减倍率
    private static final float WIPE_SHRINK_SPEED = 3.5f;     // 中心物品图标缩小倍率
    private static final float WIPE_SHRINK_POWER = 3.0f;     // 物品图标缩小的曲线次幂

    private final GachaScreen parent;
    private int width, height;

    private double scrollOffset = 0;
    private double targetScroll = 0;
    private float[] hoverAnims;

    private float btnHoverAnim = 0f;
    private float feedbackAnim = 0f;
    private boolean feedbackSuccess = false;
    private float shortfallTooltipAnim = 0f;
    private static final float SHORTFALL_TOOLTIP_DURATION = 1.6f;

    private int lastHoveredIndex = -1;
    private float previewSwitchAnim = 0f;

    private float previewAlphaAnim = 0f;
    private int tooltipHoverIndex = -1;
    private float tooltipHoverTimer = 0f;
    private float tooltipTipAlpha = 0f;
    private float animTipX = 0f, animTipY = 0f, animTipW = 0f, animTipH = 0f;
    private static final float TIP_HOVER_DELAY = 0.05f;

    private int lastHistorySize = -1;
    private float logRollAnim = 0f;

    private int snapshotPityProgress = -1;
    private List<ClientGachaCache.DrawRecord> snapshotHistory = new ArrayList<>();
    private boolean snapshotCanDraw = true;
    private int snapshotRemainingDraws = -1;
    private String snapshotFailReason = null;
    private List<CostShortfallLine> snapshotShortfall = new ArrayList<>();
    private String snapshotCooldownText = "";

    public GachaPreviewPanel(GachaScreen parent) {
        this.parent = parent;
    }

    public void init(int w, int h) {
        this.width = w;
        this.height = h;
        if (hoverAnims == null || hoverAnims.length != parent.getShopDef().getGachaPool().getItems().size()) {
            hoverAnims = new float[parent.getShopDef().getGachaPool().getItems().size()];
        }
        updateDataSnapshot();
    }

    public void updateDataSnapshot() {
        String shopId = parent.getShopId();
        ClientGachaCache cache = ClientGachaCache.INSTANCE;

        this.snapshotPityProgress = cache.getPityProgress(shopId);
        this.snapshotHistory = new ArrayList<>(cache.getDrawHistory(shopId));

        this.snapshotCanDraw = cache.canDraw(shopId);
        this.snapshotRemainingDraws = cache.getRemainingDraws(shopId);
        this.snapshotFailReason = cache.getLastFailReason(shopId);
        this.snapshotShortfall = new ArrayList<>(cache.getLastShortfall(shopId));
        this.snapshotCooldownText = cache.getCooldownText(shopId);
    }

    private record Layout(int termW, int termX, int gridX, int gridW, int mainCX, int topH, int gridY, int gridH, int btnW, int btnH, int btnX, int btnY) {}

    private Layout getLayout() {
        int termW = Math.max(140, Math.min(220, (int)(width * 0.22f)));
        int termX = width - 10 - termW;

        int gridW = Math.max(400, (int)(width * 0.55f));
        int gridX = width / 2 - gridW / 2;
        int mainCX = width / 2;

        int topH = Math.max(100, (int)(height * 0.28f));
        int btnH = 28;
        int btnW = Math.max(160, Math.min(280, (int)(gridW * 0.5f)));
        int btnX = mainCX - btnW / 2;
        int btnY = height - btnH - 15;

        int gridY = topH + 10;
        int gridH = btnY - gridY - 15;
        return new Layout(termW, termX, gridX, gridW, mainCX, topH, gridY, gridH, btnW, btnH, btnX, btnY);
    }

    public void render(GuiGraphics g, int mx, int my, float dt, float easeProgress, float contentScale, boolean waiting, boolean isClosing, float rollTransition) {
        Layout l = getLayout();
        if (easeProgress <= 0.01f) return;

        boolean isWiping = rollTransition > 0.001f;

        // 【极速擦除升级】：采用二次幂加速曲线，让开始位移的瞬间更具爆发力
        float rollEase = isWiping ? (float) Math.pow(rollTransition, 1.8) : 0f;

        // 【极速透明升级】：按配置的超高倍率在擦除前期瞬间降下所有元素的 Alpha
        float wipeAlphaMult = isWiping ? Math.max(0f, 1f - (rollTransition * WIPE_FADE_SPEED)) : 1f;
        float finalGlobalAlpha = Math.max(0, easeProgress) * wipeAlphaMult;

        int bgAlpha = (int) (0x77 * easeProgress * (1.0f - rollEase));
        if (bgAlpha > 0) g.fill(0, 0, width, height, bgAlpha << 24);

        if (!isWiping) {
            renderContent(g, l, mx, my, dt, finalGlobalAlpha, easeProgress, false, isClosing, 0, height, 0f, waiting, 0f);
        } else {
            int centerY = height / 2;

            // 【极速位移升级】：应用幅度配置倍率，让上下半区以夸张的速度扯开
            float splitOffset = (height / 2f) * rollEase * WIPE_SPLIT_MULT;
            int scissorGap = (int) ((height / 2f) * rollEase * 1.05f * WIPE_SPLIT_MULT);

            int topScY1 = 0;
            int topScY2 = Math.max(0, centerY - scissorGap);
            if (topScY2 > topScY1) {
                renderContent(g, l, -1000, -1000, dt, finalGlobalAlpha, easeProgress, true, isClosing, topScY1, topScY2, -splitOffset, waiting, rollTransition);
            }

            int botScY1 = Math.min(height, centerY + scissorGap);
            int botScY2 = height;
            if (botScY2 > botScY1) {
                renderContent(g, l, -1000, -1000, dt, finalGlobalAlpha, easeProgress, true, isClosing, botScY1, botScY2, splitOffset, waiting, rollTransition);
            }
        }
    }

    private void renderContent(GuiGraphics g, Layout l, int mx, int my, float dt, float alpha, float easeProgress, boolean isWiping, boolean isClosing, int wipeScY1, int wipeScY2, float splitOffset, boolean waiting, float rollTransition) {
        float outEase = (float) Math.pow(1.0f - easeProgress, 3.0);

        float slideX = outEase * 25f;
        float slideY = outEase * 15f;

        if (isWiping) g.enableScissor(0, wipeScY1, width, wipeScY2);
        g.pose().pushPose();
        g.pose().translate(0, -slideY + splitOffset, 0);
        renderTopPreview(g, l, alpha, isWiping, isClosing, rollTransition);
        g.pose().popPose();
        if (isWiping) g.disableScissor();

        g.pose().pushPose();
        g.pose().translate(-slideX, splitOffset, 0);
        renderItemGrid(g, l, mx + (int)slideX, (int)(my - splitOffset), dt, alpha, easeProgress, isWiping, isClosing, wipeScY1, wipeScY2, splitOffset);
        g.pose().popPose();

        if (isWiping) g.enableScissor(0, wipeScY1, width, wipeScY2);
        g.pose().pushPose();
        g.pose().translate(0, slideY + splitOffset, 0);
        renderGlassButton(g, l, mx, (int)(my - slideY - splitOffset), dt, alpha, waiting, isWiping, isClosing);
        g.pose().popPose();
        if (isWiping) g.disableScissor();

        if (isWiping) g.enableScissor(0, wipeScY1, width, wipeScY2);
        g.pose().pushPose();
        g.pose().translate(slideX, splitOffset, 0);
        renderRightTerminalTracker(g, l, dt, alpha, isWiping, isClosing);
        g.pose().popPose();
        if (isWiping) g.disableScissor();
    }

    private void renderTopPreview(GuiGraphics g, Layout l, float alpha, boolean isWiping, boolean isClosing, float rollTransition) {
        int cy = l.topH() / 2 + 5;
        float adaptiveScale = Math.min(1.5f, Math.max(0.9f, height / 450f));

        g.pose().pushPose();
        g.pose().translate(l.mainCX(), cy, 0);
        g.pose().scale(adaptiveScale, adaptiveScale, 1f);

        int titleAlpha = (int)(255 * alpha);
        if (titleAlpha > 5) {
            g.pose().pushPose();
            g.pose().scale(1.4f, 1.4f, 1f);
            g.drawCenteredString(Minecraft.getInstance().font, parent.getShopDef().getDisplayName(), 0, - (cy / 2), HudAnimUtil.withAlpha(0xFFFFFF, titleAlpha));
            g.pose().popPose();
        }

        float textAlphaF = alpha * (1f - previewAlphaAnim);
        int targetAlpha = (int)(255 * textAlphaF);
        if (targetAlpha > 5 && !isWiping) {
            g.drawCenteredString(Minecraft.getInstance().font, "// SELECT TARGET //", 0, 0, HudAnimUtil.withAlpha(0x555555, targetAlpha));
        }

        List<GachaItem> items = parent.getShopDef().getGachaPool().getItems();
        if (lastHoveredIndex >= 0 && lastHoveredIndex < items.size() && previewAlphaAnim > 0.01f) {
            GachaItem item = items.get(lastHoveredIndex);
            int themeC = parent.getShopDef().getEffectiveThemeColor(item);

            if (!isWiping && !isClosing) {
                previewSwitchAnim = HudAnimUtil.step(previewSwitchAnim, 1f, 8f, 0.016f);
            }
            float ease = HudAnimUtil.easeOutCubic(previewSwitchAnim);

            int safeA = (int)(255 * alpha * ease * previewAlphaAnim);
            float pulseScale = 1.0f + (float)Math.sin(Util.getMillis() / 600.0) * 0.02f;

            float breatheAlpha = 0.6f + 0.4f * (float)Math.sin(Util.getMillis() / 250.0);
            int glowA = (int)(150 * alpha * ease * previewAlphaAnim * breatheAlpha);

            g.fill(-50, 18, 50, 20, HudAnimUtil.withAlpha(themeC, safeA));
            g.fillGradient(-65, -5, 65, 18, 0x00000000, HudAnimUtil.withAlpha(themeC, glowA));

            // 【急速缩小升级】：计算擦除状态下特有的极速坍缩缩放比
            float wipeShrinkScale = isWiping ? Math.max(0f, 1f - (rollTransition * WIPE_SHRINK_SPEED)) : 1f;

            float dynamicScale = isClosing ? (float)Math.pow(alpha, 4.0) : (float)Math.pow(alpha, 0.5);
            // 将擦除的急速坍缩次幂叠加上去
            dynamicScale *= (float)Math.pow(wipeShrinkScale, WIPE_SHRINK_POWER);

            float finalIconScale = 2.5f * pulseScale * dynamicScale;

            g.pose().pushPose();
            g.pose().translate(0, -2, 0);
            g.pose().scale(finalIconScale, finalIconScale, 1f);
            g.pose().translate(-8, -8, 0);
            g.renderItem(item.getItemStack(), 0, 0);
            g.pose().popPose();

            if (safeA > 5) {
                g.drawCenteredString(Minecraft.getInstance().font, item.getItemStack().getHoverName().getString(), 0, 28, HudAnimUtil.withAlpha(themeC, safeA));
                renderCompactPityBar(g, 0, 42, 100, 4, safeA, themeC);
            }
        }
        g.pose().popPose();
    }

    private void renderCompactPityBar(GuiGraphics g, int centerX, int y, int w, int h, int alpha, int themeC) {
        var shopDef = parent.getShopDef();
        int pityThreshold = shopDef.getPityConfig() != null ? shopDef.getPityConfig().getPityThreshold() : 0;
        if (pityThreshold <= 0) return;

        int current = this.snapshotPityProgress;
        if (current < 0) current = 0;
        float percent = (float) current / pityThreshold;

        int startX = centerX - w / 2;
        g.fill(startX, y, startX + w, y + h, HudAnimUtil.withAlpha(0x222222, alpha));
        int fillW = (int) (w * percent);
        g.fill(startX, y, startX + fillW, y + h, HudAnimUtil.withAlpha(themeC, alpha));

        if (alpha > 5) {
            String text = current + "/" + pityThreshold;
            g.pose().pushPose();
            g.pose().scale(0.65f, 0.65f, 1f);
            g.drawCenteredString(Minecraft.getInstance().font, text, (int)(centerX / 0.65f), (int)((y + h + 2) / 0.65f), HudAnimUtil.withAlpha(0xAAAAAA, alpha));
            g.pose().popPose();
        }
    }

    private void renderItemGrid(GuiGraphics g, Layout l, int mx, int my, float dt, float alpha, float easeProgress, boolean isWiping, boolean isClosing, int wipeY1, int wipeY2, float splitOffset) {
        List<GachaItem> items = parent.getShopDef().getGachaPool().getItems();

        int gap = 8;
        int cols = Math.max(4, (l.gridW() + gap) / 100);
        int cardW = (l.gridW() - (cols - 1) * gap) / cols;
        int cardH = (int) (cardW * 0.5625f);

        int totalRows = (int) Math.ceil((double) items.size() / cols);
        int maxScroll = Math.max(0, totalRows * (cardH + gap) - l.gridH());

        if (!isClosing && !isWiping) {
            targetScroll = Math.max(0, Math.min(targetScroll, maxScroll));
            scrollOffset += (targetScroll - scrollOffset) * Math.min(1.0, dt * 10.0);
        }

        int gridScY1 = l.gridY() - 10;
        int gridScY2 = l.gridY() + l.gridH() + 10;

        if (isWiping) {
            int localWipeY1 = (int)(wipeY1 - splitOffset);
            int localWipeY2 = (int)(wipeY2 - splitOffset);
            gridScY1 = Math.max(gridScY1, localWipeY1);
            gridScY2 = Math.min(gridScY2, localWipeY2);
        }

        if (gridScY2 <= gridScY1) return;

        g.enableScissor(l.gridX() - 10, gridScY1, l.gridX() + l.gridW() + 10, gridScY2);

        int currentHover = -1;
        float responsiveScale = cardW / 110.0f;

        for (int i = 0; i < items.size(); i++) {
            float staggerProgress = Math.max(0f, Math.min(1f, easeProgress * 1.5f - i * 0.05f));
            float itemCascadeEase = HudAnimUtil.easeOutCubic(staggerProgress);
            if (itemCascadeEase <= 0.01f) continue;

            GachaItem item = items.get(i);
            int col = i % cols;
            int row = i / cols;

            int drawX = l.gridX() + col * (cardW + gap);
            int drawY = l.gridY() + row * (cardH + gap) - (int)scrollOffset;

            float cascadeYOffset = (1.0f - itemCascadeEase) * 10f;
            drawY += cascadeYOffset;

            if (drawY + cardH < l.gridY() - 20 || drawY > l.gridY() + l.gridH() + 20) continue;

            boolean hov = alpha >= 0.99f && mx >= drawX && mx < drawX + cardW && my >= drawY && my < drawY + cardH;
            if (hov) currentHover = i;

            if (!isWiping && !isClosing) {
                hoverAnims[i] = HudAnimUtil.step(hoverAnims[i], hov ? 1f : 0f, 10f, dt);
            }
            float hEase = HudAnimUtil.easeOutCubic(hoverAnims[i]);
            int themeC = parent.getShopDef().getEffectiveThemeColor(item);

            float baseCardScale = 0.9f;
            float cardScale = itemCascadeEase * baseCardScale * (1.0f + hEase * 0.05f);

            g.pose().pushPose();
            g.pose().translate(drawX + cardW/2f, drawY + cardH/2f, 0);
            g.pose().scale(cardScale, cardScale, 1f);
            g.pose().translate(-(drawX + cardW/2f), -(drawY + cardH/2f), 0);

            int safeA = (int)(255 * alpha * itemCascadeEase);
            int bgAlpha = (int) ((0x1A + 0x22 * hEase) * alpha * itemCascadeEase);
            float breatheAlpha = 1.0f + 0.5f * (float)Math.sin(Util.getMillis() / 200.0);
            int pulseGlowA = (int)((30 + 50 * hEase * breatheAlpha) * alpha * itemCascadeEase);

            g.fill(drawX, drawY, drawX + cardW, drawY + cardH, (bgAlpha << 24) | 0x05050A);

            HudRenderUtil.drawCyberneticEdge(g, drawX, drawY, cardH, themeC, safeA);

            g.fillGradient(drawX + 3, drawY, drawX + cardW, drawY + cardH, HudAnimUtil.withAlpha(themeC, pulseGlowA), 0x00000000);
            g.fillGradient(drawX + 3, drawY + cardH - (int)(24 * responsiveScale), drawX + cardW, drawY + cardH, 0x00000000, HudAnimUtil.withAlpha(0x000000, (int)(safeA * 0.9f)));

            g.pose().pushPose();
            g.pose().translate(drawX + cardW/2f, drawY + cardH/2f - (3 * responsiveScale), 0);
            float iconScale = 1.8f * responsiveScale;
            g.pose().scale(iconScale, iconScale, 1f);
            g.pose().translate(-8, -8, 0);
            g.renderItem(item.getItemStack(), 0, 0);
            g.pose().popPose();

            String name = item.getItemStack().getHoverName().getString();
            float textScale = Math.max(0.6f, 0.85f * responsiveScale);
            int maxTextW = (int)((cardW - 8) / textScale);
            if (Minecraft.getInstance().font.width(name) > maxTextW) {
                name = Minecraft.getInstance().font.plainSubstrByWidth(name, maxTextW - 6) + "..";
            }
            int rawTextW = Minecraft.getInstance().font.width(name);

            if (safeA > 5) {
                g.pose().pushPose();
                float textDrawX = drawX + cardW - (rawTextW * textScale) - 4;
                float textDrawY = drawY + cardH - (8 * textScale) - 4;
                g.pose().translate(textDrawX, textDrawY, 0);
                g.pose().scale(textScale, textScale, 1f);
                g.drawString(Minecraft.getInstance().font, name, 0, 0, HudAnimUtil.withAlpha(0xEEEEEE, safeA), true);
                g.pose().popPose();
            }

            g.pose().popPose();
        }
        g.disableScissor();

        if (!isWiping && !isClosing) {
            boolean hasHover = currentHover != -1;
            previewAlphaAnim = HudAnimUtil.step(previewAlphaAnim, hasHover ? 1f : 0f, 12f, dt);

            if (hasHover && currentHover != lastHoveredIndex) {
                lastHoveredIndex = currentHover;
                previewSwitchAnim = 0f;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.5f, 0.5f));
            }

            if (currentHover != tooltipHoverIndex) {
                tooltipHoverIndex = currentHover;
                tooltipHoverTimer = 0f;
            }
            if (tooltipHoverIndex != -1) {
                if (tooltipHoverTimer < TIP_HOVER_DELAY) {
                    tooltipHoverTimer += dt;
                }
            } else {
                tooltipHoverTimer = 0f;
            }
        } else {
            tooltipHoverIndex = -1;
        }

        float tooltipTarget = (tooltipHoverIndex != -1 && tooltipHoverTimer >= TIP_HOVER_DELAY && !isClosing && !isWiping) ? 1f : 0f;
        tooltipTipAlpha += (tooltipTarget - tooltipTipAlpha) * Math.min(1f, dt * 15f);

        if (tooltipTipAlpha > 0.02f && lastHoveredIndex >= 0 && lastHoveredIndex < items.size()) {
            renderItemTooltip(g, items.get(lastHoveredIndex), mx, my, dt, alpha, isClosing || isWiping);
        } else if (tooltipTipAlpha <= 0.02f) {
            animTipW = 0f;
        }
    }

    private void renderItemTooltip(GuiGraphics g, GachaItem item, int mouseX, int mouseY, float dt, float alpha, boolean isClosing) {
        var font = Minecraft.getInstance().font;
        int padding = 10;
        int cyberEdgeWidth = 3;

        // --- 1. 数据准备与排版计算 ---
        String rarityVal = item.getRarity().getName().toUpperCase();
        String countVal = (item.getMinCount() == item.getMaxCount()) ?
                String.valueOf(item.getMinCount()) :
                item.getMinCount() + " - " + item.getMaxCount();
        String weightVal = String.valueOf(item.getBaseWeight());

        // 标签硬编码为纯英文，极大提升机能风 UI 的视觉秩序感
        String lblRarity = "RARITY";
        String lblYield = "YIELD";
        String lblWeight = "WEIGHT";

        int nameW = font.width(item.getItemStack().getHoverName());
        int maxKeyW = Math.max(font.width(lblRarity), Math.max(font.width(lblYield), font.width(lblWeight)));
        int maxValW = Math.max(font.width(rarityVal), Math.max(font.width(countVal), font.width(weightVal)));

        // 动态计算目标尺寸 (宽度: 物品名与键值对取最大值; 高度: 标题 + 分割线 + 3行数据 + 保底标识)
        int targetW = Math.max(nameW, maxKeyW + maxValW + 40) + padding * 2 + cyberEdgeWidth;
        int targetH = padding * 2 + 14 + 6 + (3 * 12) + (item.countsTowardsPity() ? 14 : 0);

        int targetX = mouseX + 12;
        int targetY = mouseY - 12;

        if (targetX + targetW > width) targetX = mouseX - targetW - 8;
        if (targetY + targetH > height) targetY = height - targetH - 2;
        if (targetY < 0) targetY = 2;

        // --- 2. 丝滑形变计算 (保留原有逻辑) ---
        if (animTipW == 0 || Math.abs(animTipW - targetW) > 50) {
            animTipX = targetX; animTipY = targetY;
            animTipW = targetW; animTipH = targetH;
        } else {
            float morphSpeed = 15f;
            animTipX += (targetX - animTipX) * Math.min(1f, dt * morphSpeed);
            animTipY += (targetY - animTipY) * Math.min(1f, dt * morphSpeed);
            animTipW += (targetW - animTipW) * Math.min(1f, dt * morphSpeed);
            animTipH += (targetH - animTipH) * Math.min(1f, dt * morphSpeed);
        }

        float scale = isClosing ? HudAnimUtil.easeInCubic(tooltipTipAlpha) : HudAnimUtil.easeOutCubic(tooltipTipAlpha);
        if (scale < 0.01f) return;

        int drawX = (int) animTipX;
        int drawY = (int) animTipY;
        int drawW = (int) animTipW;
        int drawH = (int) animTipH;

        float finalTipAlpha = tooltipTipAlpha * alpha;
        int bgAlpha = (int) (0x96 * finalTipAlpha);
        int borderAlpha = (int) (0x66 * finalTipAlpha);
        int safeAlpha = (int) (255 * finalTipAlpha);
        int themeColor = parent.getShopDef().getEffectiveThemeColor(item);

        // --- 3. 背景与边框渲染 ---
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);

        float centerX = drawX + drawW / 2f;
        float centerY = drawY + drawH / 2f;
        g.pose().translate(centerX, centerY, 0);
        g.pose().scale(scale, scale, 1f);
        g.pose().translate(-centerX, -centerY, 0);

        g.fill(drawX + cyberEdgeWidth, drawY, drawX + drawW, drawY + drawH, HudAnimUtil.withAlpha(0x050508, bgAlpha));
        g.fill(drawX + cyberEdgeWidth, drawY, drawX + drawW, drawY + 1, HudAnimUtil.withAlpha(0xCCCCCC, borderAlpha));
        g.fill(drawX + cyberEdgeWidth, drawY + drawH - 1, drawX + drawW, drawY + drawH, HudAnimUtil.withAlpha(0xCCCCCC, borderAlpha));
        g.fill(drawX + drawW - 1, drawY, drawX + drawW, drawY + drawH, HudAnimUtil.withAlpha(0xCCCCCC, borderAlpha));

        HudRenderUtil.drawCyberneticEdge(g, drawX, drawY, drawH, themeColor, safeAlpha);

        // --- 4. 高级机能风内部排版渲染 ---
        g.enableScissor(drawX, drawY, drawX + drawW, drawY + drawH);

        int currentY = drawY + padding;
        int leftX = drawX + cyberEdgeWidth + padding;
        int rightX = drawX + drawW - padding;

        // 物品标题
        g.drawString(font, item.getItemStack().getHoverName(), leftX, currentY, HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha), true);
        currentY += 14;

        // 主题色精美分割线
        g.fill(leftX, currentY, rightX, currentY + 1, HudAnimUtil.withAlpha(themeColor, (int)(safeAlpha * 0.3f)));
        g.fill(leftX, currentY, leftX + 20, currentY + 1, HudAnimUtil.withAlpha(themeColor, safeAlpha));
        currentY += 6;

        // RARITY
        g.drawString(font, lblRarity, leftX, currentY, HudAnimUtil.withAlpha(0x666666, safeAlpha), true);
        g.drawString(font, rarityVal, rightX - font.width(rarityVal), currentY, HudAnimUtil.withAlpha(0xDDDDDD, safeAlpha), true);
        currentY += 12;

        // YIELD
        g.drawString(font, lblYield, leftX, currentY, HudAnimUtil.withAlpha(0x666666, safeAlpha), true);
        g.drawString(font, countVal, rightX - font.width(countVal), currentY, HudAnimUtil.withAlpha(0xDDDDDD, safeAlpha), true);
        currentY += 12;

        // WEIGHT
        g.drawString(font, lblWeight, leftX, currentY, HudAnimUtil.withAlpha(0x666666, safeAlpha), true);
        g.drawString(font, weightVal, rightX - font.width(weightVal), currentY, HudAnimUtil.withAlpha(0xDDDDDD, safeAlpha), true);
        currentY += 12;

        // 保底标识 (如果计入保底，添加醒目提示)
        if (item.countsTowardsPity()) {
            currentY += 2;
            g.fill(leftX, currentY + 2, leftX + 2, currentY + 6, HudAnimUtil.withAlpha(themeColor, safeAlpha));
            g.drawString(font, Component.translatable("arc_quest.gui.gacha.tooltip.pity_enabled"), leftX + 6, currentY, HudAnimUtil.withAlpha(themeColor, safeAlpha), true);
        }

        g.disableScissor();
        g.pose().popPose();
    }



    private void renderShortfallTooltip(GuiGraphics g, Layout l, float alpha, int drawX) {
        var font = Minecraft.getInstance().font;
        List<CostShortfallLine> shortfalls = snapshotShortfall;
        if (shortfallTooltipAnim <= 0f || shortfalls == null || shortfalls.isEmpty()) {
            return;
        }

        float ease = HudAnimUtil.easeOutCubic(shortfallTooltipAnim);
        int safeAlpha = (int)(220 * alpha * ease);
        if (safeAlpha <= 5) return;

        // --- 1. 同步 TradeScreen 的排版尺寸计算 ---
        int padding = 10;
        Component summaryTitle = Component.translatable("arc_quest.gui.trade.tooltip.shortfall_summary");
        int boxW = font.width(summaryTitle) + 40;

        for (CostShortfallLine sf : shortfalls) {
            int lineW = font.width(sf.label()) + font.width("-" + sf.missing()) + 50;
            boxW = Math.max(boxW, lineW);
        }
        boxW += padding * 2;

        // 警告顶部分割线与标题高度(18) + 每个短缺项高度(18)
        int boxH = padding * 2 + 18 + (shortfalls.size() * 18);

        int boxX = drawX + l.btnW() / 2 - boxW / 2;
        int boxY = l.btnY() - boxH - 8;

        // --- 2. 背景与边框 ---
        g.fill(boxX, boxY, boxX + boxW, boxY + boxH, HudAnimUtil.withAlpha(0x050508, safeAlpha));
        g.fillGradient(boxX, boxY, boxX + boxW, boxY + boxH, HudAnimUtil.withAlpha(0xAA3333, (int)(40 * alpha * ease)), 0);
        HudAnimUtil.drawFrame(g, boxX, boxY, boxW, boxH, 1, HudAnimUtil.withAlpha(0xFF3333, safeAlpha));

        // --- 3. 核心可视化内容排版 ---
        int currentY = boxY + padding;

        // 机能风红色警告分割线
        g.fill(boxX + padding, currentY, boxX + boxW - padding, currentY + 1, HudAnimUtil.withAlpha(0xFF3333, (int)(safeAlpha * 0.2f)));
        g.fill(boxX + padding, currentY, boxX + padding + 40, currentY + 1, HudAnimUtil.withAlpha(0xFF3333, safeAlpha));
        currentY += 6;

        // 摘要标题
        g.drawString(font, summaryTitle, boxX + padding, currentY, HudAnimUtil.withAlpha(0xFF5555, safeAlpha), true);
        currentY += 12;

        // 短缺列表渲染 (完全复用高级数据可视化方案)
        for (CostShortfallLine sf : shortfalls) {
            // 左侧红点
            g.fill(boxX + padding, currentY + 3, boxX + padding + 2, currentY + 7, HudAnimUtil.withAlpha(0xFF4444, safeAlpha));

            // 物品名称
            g.drawString(font, sf.label(), boxX + padding + 6, currentY, HudAnimUtil.withAlpha(0xDDDDDD, safeAlpha), true);

            // 缺失数量 (右边缘对齐)
            if (sf.missing() > 0) {
                String missingTxt = "-" + sf.missing();
                int mWidth = font.width(missingTxt);
                g.drawString(font, missingTxt, boxX + boxW - padding - mWidth, currentY, HudAnimUtil.withAlpha(0xFF3333, safeAlpha), true);
            }

            currentY += 10;

            // 微型进度条与库存状态
            if (sf.required() > 0) {
                String metaTxt = sf.owned() + " / " + sf.required();

                g.pose().pushPose();
                g.pose().translate(boxX + padding + 6, currentY, 0);
                g.pose().scale(0.8f, 0.8f, 1f);
                g.drawString(font, metaTxt, 0, 0, HudAnimUtil.withAlpha(0x888888, safeAlpha), false);
                g.pose().popPose();

                int metaWidth = (int)(font.width(metaTxt) * 0.8f);
                int barX = boxX + padding + 6 + metaWidth + 6;
                int barW = boxW - padding * 2 - (barX - boxX) - 5;

                if (barW > 10) {
                    float pct = Math.min(1f, (float)sf.owned() / sf.required());
                    int fillW = (int)(barW * pct);
                    // 暗红底槽
                    g.fill(barX, currentY + 2, barX + barW, currentY + 4, HudAnimUtil.withAlpha(0x442222, safeAlpha));
                    // 亮红填充
                    if (fillW > 0) {
                        g.fill(barX, currentY + 2, barX + fillW, currentY + 4, HudAnimUtil.withAlpha(0xAA3333, safeAlpha));
                    }
                }
            }
            currentY += 8;
        }
    }

    private void renderRightTerminalTracker(GuiGraphics g, Layout l, float dt, float alpha, boolean isWiping, boolean isClosing) {
        int safeA = (int)(255 * alpha);
        if (safeA <= 5) return;
        int termColor = HudAnimUtil.blend(parent.getShopDef().getThemeColor(), 0x00FFFF, 0.15f);

        int h = height - 60;
        int y = 30;

        g.pose().pushPose();
        int spineX = l.termX() + l.termW();

        g.fill(spineX, y, spineX + 1, y + h, HudAnimUtil.withAlpha(termColor, (int)(safeA * 0.4f)));
        g.fill(spineX - 4, y, spineX + 2, y + 2, HudAnimUtil.withAlpha(termColor, safeA));
        g.fill(spineX - 4, y, spineX - 1, y + 8, HudAnimUtil.withAlpha(termColor, (int)(safeA * 0.6f)));
        g.fill(spineX - 4, y + h - 2, spineX + 2, y + h, HudAnimUtil.withAlpha(termColor, safeA));
        g.fill(spineX - 4, y + h - 8, spineX - 1, y + h, HudAnimUtil.withAlpha(termColor, (int)(safeA * 0.6f)));
        g.fill(spineX - 2, y + h/2 - 10, spineX + 2, y + h/2 + 10, HudAnimUtil.withAlpha(termColor, safeA));

        for(int tick = y + 20; tick < y + h - 20; tick += 40) {
            g.fill(spineX - 4, tick, spineX, tick + 1, HudAnimUtil.withAlpha(termColor, (int)(safeA * 0.2f)));
        }

        String title = "// UPLINK.LOG";
        int titleW = Minecraft.getInstance().font.width(title);
        int titleX = spineX - 10 - titleW;

        g.drawString(Minecraft.getInstance().font, title, titleX, y, HudAnimUtil.withAlpha(termColor, safeA), true);

        int textStartX = titleX + 4;
        int maxTextW = (spineX - 10) - textStartX;

        List<ClientGachaCache.DrawRecord> history = this.snapshotHistory;

        if (lastHistorySize == -1) lastHistorySize = history.size();

        if (!isWiping && !isClosing && history.size() > lastHistorySize) {
            logRollAnim = 1.0f;
            lastHistorySize = history.size();
        }
        if (!isWiping && !isClosing && logRollAnim > 0) {
            logRollAnim = Math.max(0, logRollAnim - dt * 6.0f);
        }

        float rollEase = HudAnimUtil.easeOutCubic(1.0f - logRollAnim);
        int startY = y + 22;
        float lineHeight = 14f;
        int maxRecords = Math.max(5, (int) ((h - 30) / lineHeight));
        int limit = Math.min(maxRecords, history.size());

        if (history.isEmpty()) {
            String emptyMsg = "NO RECORDS YET.";
            int emptyW = Minecraft.getInstance().font.width(emptyMsg);
            int emptyX = spineX - 10 - emptyW;
            g.drawString(Minecraft.getInstance().font, emptyMsg, emptyX, startY, HudAnimUtil.withAlpha(0x555555, safeA));
        } else {
            boolean isFull = history.size() >= maxRecords;
            for (int i = 0; i < limit; i++) {
                ClientGachaCache.DrawRecord rec = history.get(history.size() - 1 - i);

                GachaItem gItem = parent.getShopDef().getGachaPool().getItems().stream().filter(itm -> itm.getItemId().equals(rec.itemId())).findFirst().orElse(null);
                int itemColor = gItem != null ? parent.getShopDef().getEffectiveThemeColor(gItem) : 0xAAAAAA;
                String itemName = gItem != null ? gItem.getItemStack().getHoverName().getString() : Component.translatable("arc_quest.gui.gacha.unknown_item").getString();

                String text = (rec.pityTriggered() ? "[PITY]" : "> ") + itemName + " x" + rec.actualCount();
                text = Minecraft.getInstance().font.plainSubstrByWidth(text, maxTextW);

                float targetY = startY + i * lineHeight;
                float drawY = targetY - (1.0f - rollEase) * lineHeight;

                float itemAlphaMod = 1.0f;
                if (i == 0 && logRollAnim > 0) itemAlphaMod = rollEase;
                else if (isFull && i == limit - 1 && logRollAnim > 0) itemAlphaMod = 1.0f - rollEase;

                int finalA = (int)(safeA * itemAlphaMod);
                if (finalA > 5) {
                    g.drawString(Minecraft.getInstance().font, text, textStartX + 7, (int)drawY, HudAnimUtil.withAlpha(itemColor, finalA), true);
                }
            }
        }
        g.pose().popPose();
    }

    private void renderGlassButton(GuiGraphics g, Layout l, int mx, int my, float dt, float alpha, boolean waiting, boolean isWiping, boolean isClosing) {
        boolean onCooldown = snapshotCooldownText != null && !snapshotCooldownText.isEmpty();
        int remainingDraws = snapshotRemainingDraws;
        String cooldownText = snapshotCooldownText;
        String lastFailReason = snapshotFailReason;
        boolean hasShortfall = snapshotShortfall != null && !snapshotShortfall.isEmpty();

        boolean maxed = remainingDraws == 0;
        boolean insufficientFunds = isCannotAffordFailure(lastFailReason) || hasShortfall;
        boolean locked = !onCooldown && !maxed && !insufficientFunds && !snapshotCanDraw;

        boolean canInteract = !waiting && !onCooldown && !maxed && !locked && !insufficientFunds && snapshotCanDraw;
        boolean unavailable = !waiting && !canInteract;

        boolean hov = canInteract
                && mx >= l.btnX() && mx < l.btnX() + l.btnW()
                && my >= l.btnY() && my < l.btnY() + l.btnH();

        if (!isWiping && !isClosing) {
            btnHoverAnim = HudAnimUtil.step(btnHoverAnim, hov ? 1f : 0f, 10f, dt);
            if (feedbackAnim > 0) feedbackAnim = Math.max(0, feedbackAnim - dt * 2.5f);
            if (shortfallTooltipAnim > 0) shortfallTooltipAnim = Math.max(0, shortfallTooltipAnim - dt / SHORTFALL_TOOLTIP_DURATION);
        }

        float hEase = HudAnimUtil.easeOutCubic(btnHoverAnim);
        int baseColor = waiting
                ? 0x666666
                : onCooldown ? 0x777777
                : maxed ? 0x8A5A5A
                : locked ? 0x7A6A8A
                : insufficientFunds ? 0x8A5A5A
                : unavailable ? 0x888888
                : parent.getShopDef().getThemeColor();

        int shakeX = (feedbackAnim > 0 && !feedbackSuccess) ? (int)(Math.sin(Util.getMillis() / 30.0) * feedbackAnim * 5) : 0;
        int drawX = l.btnX() + shakeX;

        g.fill(drawX, l.btnY(), drawX + l.btnW(), l.btnY() + l.btnH(), HudAnimUtil.withAlpha(0x151515, (int)(200 * alpha)));
        g.fillGradient(drawX, l.btnY(), drawX + l.btnW(), l.btnY() + l.btnH(), HudAnimUtil.withAlpha(baseColor, (int)((40 + 60 * hEase) * alpha)), 0);
        HudAnimUtil.drawFrame(g, drawX, l.btnY(), l.btnW(), l.btnH(), 1, HudAnimUtil.withAlpha(baseColor, (int)((150 + 105 * hEase) * alpha)));

        int btnTextAlpha = (int)(255 * alpha);
        if (btnTextAlpha > 5) {
            Component text = waiting
                    ? Component.translatable("arc_quest.gui.gacha.btn.decrypting")
                    : HudRenderUtil.resolveGachaFailButtonText(lastFailReason, onCooldown, maxed, locked, insufficientFunds, cooldownText);

            g.drawCenteredString(
                    Minecraft.getInstance().font,
                    text,
                    drawX + l.btnW() / 2,
                    l.btnY() + l.btnH() / 2 - 4,
                    HudAnimUtil.withAlpha(0xFFFFFF, btnTextAlpha)
            );
        }

        renderShortfallTooltip(g, l, alpha, drawX);
    }

    public boolean mouseClicked(double mx, double my) {
        Layout l = getLayout();
        if (mx >= l.btnX() && mx < l.btnX() + l.btnW() && my >= l.btnY() && my < l.btnY() + l.btnH()) {
            boolean onCooldown = snapshotCooldownText != null && !snapshotCooldownText.isEmpty();
            boolean hasShortfall = snapshotShortfall != null && !snapshotShortfall.isEmpty();
            boolean insufficientFunds = isCannotAffordFailure(snapshotFailReason) || hasShortfall;
            boolean maxed = snapshotRemainingDraws == 0;
            boolean locked = !onCooldown && !maxed && !insufficientFunds && !snapshotCanDraw;

            boolean canInteract = !onCooldown && !maxed && !locked && !insufficientFunds && snapshotCanDraw;

            if (!canInteract) {
                feedbackSuccess = false;
                feedbackAnim = 1f;
                if (insufficientFunds) {
                    shortfallTooltipAnim = 1f;
                }
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.get(), 0.8f)
                );
                return true;
            }

            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0f)
            );
            parent.startDrawRequest();
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double delta) {
        targetScroll -= delta * 45;
        return true;
    }

    private static boolean isCannotAffordFailure(String failReason) {
        if (failReason == null || failReason.isEmpty()) {
            return false;
        }
        String key = failReason.toLowerCase();
        return "cannot_afford".equals(key)
                || key.contains("cannot_afford")
                || key.contains("insufficient")
                || key.endsWith(".cannot_afford");
    }
}