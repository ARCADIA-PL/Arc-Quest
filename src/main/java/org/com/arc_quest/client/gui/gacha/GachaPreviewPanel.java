package org.com.arc_quest.client.gui.gacha;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.com.arc_quest.client.gui.HudAnimUtil;
import org.com.arc_quest.trade.gacha.api.GachaItem;
import org.com.arc_quest.trade.gacha.network.ClientGachaCache;

import java.util.ArrayList;
import java.util.List;

public class GachaPreviewPanel {

    private final GachaScreen parent;
    private int width, height;

    private double scrollOffset = 0;
    private double targetScroll = 0;
    private float[] hoverAnims;

    private float btnHoverAnim = 0f;
    private float feedbackAnim = 0f;
    private boolean feedbackSuccess = false;

    private int lastHoveredIndex = -1;
    private float previewSwitchAnim = 0f;

    private int lastHistorySize = -1;
    private float logRollAnim = 0f;

    // ★ 架构核心：本地视觉快照（防止网络包到达时直接产生剧透）
    private int snapshotPityProgress = -1;
    private List<ClientGachaCache.DrawRecord> snapshotHistory = new ArrayList<>();

    public GachaPreviewPanel(GachaScreen parent) {
        this.parent = parent;
    }

    public void init(int w, int h) {
        this.width = w;
        this.height = h;
        if (hoverAnims == null || hoverAnims.length != parent.getShopDef().getGachaPool().getItems().size()) {
            hoverAnims = new float[parent.getShopDef().getGachaPool().getItems().size()];
        }
        // 初始化时立刻拉取一次最新数据作为底色
        updateDataSnapshot();
    }

    // ★ 同步核心接口：由 GachaScreen 严格把控调用时机！
    public void updateDataSnapshot() {
        this.snapshotPityProgress = ClientGachaCache.INSTANCE.getPityProgress(parent.getShopId());
        // 必须深拷贝历史记录，防止渲染进程读取时发生并发异常或内容变动
        this.snapshotHistory = new ArrayList<>(ClientGachaCache.INSTANCE.getDrawHistory(parent.getShopId()));
    }

    public void render(GuiGraphics g, int mx, int my, float dt, float easeProgress, float contentScale, boolean waiting, boolean isClosing, float rollTransition) {
        if (contentScale <= 0.01f) return;

        float collapseEase = HudAnimUtil.easeInCubic(rollTransition);
        float globalAlphaMod = 1.0f - collapseEase;
        easeProgress *= globalAlphaMod;

        int bgAlpha = (int) (0x77 * easeProgress);
        if (bgAlpha > 0) g.fill(0, 0, width, height, bgAlpha << 24);

        if (easeProgress <= 0.01f) return;

        g.pose().pushPose();

        if (rollTransition > 0) {
            float shrink = 1.0f - collapseEase * 0.18f; // 加大一点坍缩深度
            g.pose().translate(width / 2f, height / 2f, 0);
            g.pose().scale(shrink, shrink, 1f);
            g.pose().translate(-width / 2f, -height / 2f, 0);
        }

        float slideOffset = isClosing ? (1.0f - easeProgress) * 250f : 0f;
        int mainCX = width / 2;

        // ★ 尺寸自适应宽容度升级：留出合理空间，极限下允许重叠
        int gridW = Math.min(520, width - 240);
        if (gridW < 240) gridW = 240;

        int gridX = mainCX - (gridW / 2);
        int gridY = (int) (height * 0.38f);
        int gridH = (int) (height * 0.42f);

        // 1. 中心组件
        g.pose().pushPose();
        g.pose().translate(slideOffset, 0, 0);

        renderTopPreview(g, mainCX, contentScale, easeProgress);
        renderItemGrid(g, gridX, gridY, gridW, gridH, mx - (int)slideOffset, my, dt, easeProgress, contentScale);

        int btnW = 220;
        int btnX = mainCX - (btnW / 2);
        int btnY = height - (int)(height * 0.12f) - 15;
        renderGlassButton(g, btnX, btnY, btnW, 30, mx - (int)slideOffset, my, dt, easeProgress, contentScale, waiting);
        g.pose().popPose();

        // 2. 右翼终端
        g.pose().pushPose();
        g.pose().translate(-slideOffset, 0, 0);

        // ★ 高级自适应：抛弃粗暴的隐藏，改为平滑缩放
        int requiredTermW = 160;
        int termX = width - 15 - requiredTermW; // 死死锚定右侧15像素边缘
        int termH = height - 60;

        float termScale = 1.0f;
        if (width < 700) {
            termScale = Math.max(0.6f, (float)width / 700f); // 极限压缩至 60% 保证可见
        }

        renderRightTerminalTracker(g, termX, 30, requiredTermW, termH, dt, easeProgress, contentScale * termScale);
        g.pose().popPose();

        g.pose().popPose();
    }

    private void renderTopPreview(GuiGraphics g, int cx, float scale, float alpha) {
        int topH = (int) (height * 0.3f);
        int cy = topH / 2 + 10;

        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1f);

        g.pose().pushPose();
        g.pose().scale(1.5f, 1.5f, 1f);
        g.drawCenteredString(Minecraft.getInstance().font, parent.getShopDef().getDisplayName(), 0, - (cy / 2), HudAnimUtil.withAlpha(0xFFFFFF, (int)(255 * alpha)));
        g.pose().popPose();

        List<GachaItem> items = parent.getShopDef().getGachaPool().getItems();
        if (lastHoveredIndex >= 0 && lastHoveredIndex < items.size()) {
            GachaItem item = items.get(lastHoveredIndex);
            int themeC = parent.getShopDef().getEffectiveThemeColor(item);

            previewSwitchAnim = HudAnimUtil.step(previewSwitchAnim, 1f, 10f, 0.016f);
            float ease = HudAnimUtil.easeOutCubic(previewSwitchAnim);
            int safeA = (int)(255 * alpha * ease);
            float pulseScale = 1.0f + (float)Math.sin(Util.getMillis() / 600.0) * 0.02f;

            float breatheAlpha = 0.6f + 0.4f * (float)Math.sin(Util.getMillis() / 250.0);
            int glowA = (int)(120 * alpha * ease * breatheAlpha);

            g.fill(-40, 25, 40, 27, HudAnimUtil.withAlpha(themeC, safeA));
            g.fillGradient(-50, 0, 50, 25, 0x00000000, HudAnimUtil.withAlpha(themeC, glowA));

            g.pose().pushPose();
            g.pose().translate(0, 5, 0);
            g.pose().scale(2.5f * pulseScale, 2.5f * pulseScale, 1f);
            g.pose().translate(-8, -8, 0);
            g.renderItem(item.getItemStack(), 0, 0);
            g.pose().popPose();

            g.drawCenteredString(Minecraft.getInstance().font, item.getItemStack().getHoverName().getString(), 0, 35, HudAnimUtil.withAlpha(themeC, safeA));
            renderCompactPityBar(g, 0, 50, 100, 4, safeA, themeC);
        } else {
            g.drawCenteredString(Minecraft.getInstance().font, "// SELECT TARGET //", 0, 0, HudAnimUtil.withAlpha(0x555555, (int)(255 * alpha)));
        }
        g.pose().popPose();
    }

    private void renderCompactPityBar(GuiGraphics g, int centerX, int y, int w, int h, int alpha, int themeC) {
        var shopDef = parent.getShopDef();
        int pityThreshold = shopDef.getPityConfig() != null ? shopDef.getPityConfig().getPityThreshold() : 0;
        if (pityThreshold <= 0) return;

        // ★ 核心改动：仅从快照中读取进度！
        int current = this.snapshotPityProgress;
        if (current < 0) current = 0;
        float percent = (float) current / pityThreshold;

        int startX = centerX - w / 2;
        g.fill(startX, y, startX + w, y + h, HudAnimUtil.withAlpha(0x222222, alpha));
        int fillW = (int) (w * percent);
        g.fill(startX, y, startX + fillW, y + h, HudAnimUtil.withAlpha(themeC, alpha));

        String text = current + "/" + pityThreshold;
        g.pose().pushPose();
        g.pose().scale(0.65f, 0.65f, 1f);
        g.drawCenteredString(Minecraft.getInstance().font, text, (int)(centerX / 0.65f), (int)((y + h + 2) / 0.65f), HudAnimUtil.withAlpha(0xAAAAAA, alpha));
        g.pose().popPose();
    }

    private void renderItemGrid(GuiGraphics g, int x, int y, int w, int h, int mx, int my, float dt, float alpha, float contentScale) {
        List<GachaItem> items = parent.getShopDef().getGachaPool().getItems();

        int gap = 8;
        int cols = Math.max(4, (w + gap) / 80);
        int cardW = (w - (cols - 1) * gap) / cols;
        int cardH = (int) (cardW * 9.0f / 16.0f);

        int totalRows = (int) Math.ceil((double) items.size() / cols);
        int maxScroll = Math.max(0, totalRows * (cardH + gap) - h);

        targetScroll = Math.max(0, Math.min(targetScroll, maxScroll));
        scrollOffset += (targetScroll - scrollOffset) * Math.min(1.0, dt * 15.0);

        g.enableScissor(x - 20, y - 10, x + w + 20, y + h + 10);
        int currentHover = -1;

        for (int i = 0; i < items.size(); i++) {
            GachaItem item = items.get(i);
            int col = i % cols;
            int row = i / cols;

            int drawX = x + col * (cardW + gap);
            int drawY = y + row * (cardH + gap) - (int)scrollOffset;

            if (drawY + cardH < y - 20 || drawY > y + h + 20) continue;

            boolean hov = contentScale >= 0.99f && mx >= drawX && mx < drawX + cardW && my >= drawY && my < drawY + cardH;
            if (hov) currentHover = i;

            hoverAnims[i] = HudAnimUtil.step(hoverAnims[i], hov ? 1f : 0f, 15f, dt);
            float hEase = HudAnimUtil.easeOutCubic(hoverAnims[i]);
            int themeC = parent.getShopDef().getEffectiveThemeColor(item);
            float cardScale = 1.0f + hEase * 0.05f;

            g.pose().pushPose();
            g.pose().translate(drawX + cardW/2f, drawY + cardH/2f, 0);
            g.pose().scale(cardScale * contentScale, cardScale * contentScale, 1f);
            g.pose().translate(-(drawX + cardW/2f), -(drawY + cardH/2f), 0);

            int safeA = (int)(255 * alpha);
            int bgAlpha = (int) ((0x1A + 0x22 * hEase) * alpha);
            float breatheAlpha = 1.0f + 0.5f * (float)Math.sin(Util.getMillis() / 200.0);
            int pulseGlowA = (int)((30 + 50 * hEase * breatheAlpha) * alpha);

            g.fill(drawX, drawY, drawX + cardW, drawY + cardH, (bgAlpha << 24) | 0x05050A);
            g.fill(drawX, drawY, drawX + 4, drawY + cardH, HudAnimUtil.withAlpha(themeC, safeA));
            g.fillGradient(drawX + 4, drawY, drawX + cardW, drawY + cardH, HudAnimUtil.withAlpha(themeC, pulseGlowA), 0x00000000);

            g.pose().pushPose();
            g.pose().translate(drawX + cardW/2f, drawY + cardH/2f - 4, 0);
            g.pose().scale(1.3f, 1.3f, 1f);
            g.pose().translate(-8, -8, 0);
            g.renderItem(item.getItemStack(), 0, 0);
            g.pose().popPose();

            g.pose().pushPose();
            g.pose().translate(drawX + cardW - 4, drawY + cardH - 10, 0);
            g.pose().scale(0.8f, 0.8f, 1f);
            String name = item.getItemStack().getHoverName().getString();
            String prefix = ">_";
            int maxWidth = (int)((cardW - 12) / 0.8f);
            name = Minecraft.getInstance().font.plainSubstrByWidth(name, maxWidth - Minecraft.getInstance().font.width(prefix));
            g.drawString(Minecraft.getInstance().font, prefix + name, -Minecraft.getInstance().font.width(prefix + name), 0, HudAnimUtil.withAlpha(0xEEEEEE, safeA), true);
            g.pose().popPose();

            g.pose().popPose();
        }
        g.disableScissor();

        if (currentHover != -1 && currentHover != lastHoveredIndex) {
            lastHoveredIndex = currentHover;
            previewSwitchAnim = 0f;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.5f, 0.5f));
        }
    }

    private void renderRightTerminalTracker(GuiGraphics g, int x, int y, int contentW, int h, float dt, float alpha, float termScale) {
        int safeA = (int)(255 * alpha);
        if (safeA <= 5) return;
        int termColor = HudAnimUtil.blend(parent.getShopDef().getThemeColor(), 0x00FFFF, 0.15f);

        g.pose().pushPose();
        int spineX = x + contentW;
        g.pose().translate(spineX, y + h/2f, 0);
        g.pose().scale(termScale, termScale, 1f);
        g.pose().translate(-spineX, -(y + h/2f), 0);

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
        if (history.size() > lastHistorySize) {
            logRollAnim = 1.0f;
            lastHistorySize = history.size();
        }
        if (logRollAnim > 0) logRollAnim = Math.max(0, logRollAnim - dt * 6.0f);

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
                String itemName = gItem != null ? gItem.getItemStack().getHoverName().getString() : "Unknown";

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

    private void renderGlassButton(GuiGraphics g, int x, int y, int w, int h, int mx, int my, float dt, float alpha, float contentScale, boolean waiting) {
        boolean onCooldown = ClientGachaCache.INSTANCE.isOnCooldown(parent.getShopId());
        boolean costInsufficient = !waiting && !onCooldown && !ClientGachaCache.INSTANCE.canDraw(parent.getShopId());
        boolean canDraw = !waiting && !onCooldown && !costInsufficient;

        boolean hov = canDraw && mx >= x && mx < x + w && my >= y && my < y + h;
        btnHoverAnim = HudAnimUtil.step(btnHoverAnim, hov ? 1f : 0f, 15f, dt);
        float hEase = HudAnimUtil.easeOutCubic(btnHoverAnim);

        if (feedbackAnim > 0) feedbackAnim = Math.max(0, feedbackAnim - dt * 2.5f);
        int baseColor = waiting || onCooldown ? 0x666666 : (costInsufficient ? 0xFF5555 : parent.getShopDef().getThemeColor());

        g.pose().pushPose();
        g.pose().translate(x + w/2f, y + h/2f, 0);
        g.pose().scale(contentScale, contentScale, 1f);
        g.pose().translate(-(x + w/2f), -(y + h/2f), 0);

        int shakeX = (feedbackAnim > 0 && !feedbackSuccess) ? (int)(Math.sin(Util.getMillis() / 30.0) * feedbackAnim * 5) : 0;
        int drawX = x + shakeX;

        g.fill(drawX, y, drawX + w, y + h, HudAnimUtil.withAlpha(0x151515, (int)(200 * alpha)));
        g.fillGradient(drawX, y, drawX + w, y + h, HudAnimUtil.withAlpha(baseColor, (int)((40 + 60 * hEase) * alpha)), 0);
        HudAnimUtil.drawFrame(g, drawX, y, w, h, 1, HudAnimUtil.withAlpha(baseColor, (int)((150 + 105 * hEase) * alpha)));

        String text = waiting ? "DECRYPTING..." : (onCooldown ? "COOLDOWN" : (costInsufficient ? "INSUFFICIENT FUNDS" : "UNLOCK RECEPTACLE"));
        g.drawCenteredString(Minecraft.getInstance().font, text, drawX + w/2, y + h/2 - 4, HudAnimUtil.withAlpha(0xFFFFFF, (int)(255 * alpha)));

        g.pose().popPose();
    }

    public boolean mouseClicked(double mx, double my) {
        int mainCX = width / 2;
        int btnW = 220;
        int btnX = mainCX - (btnW / 2);
        int btnY = height - (int)(height * 0.12f) - 15;

        if (mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + 30) {
            boolean onCooldown = ClientGachaCache.INSTANCE.isOnCooldown(parent.getShopId());
            boolean costInsufficient = !onCooldown && !ClientGachaCache.INSTANCE.canDraw(parent.getShopId());
            if (onCooldown || costInsufficient) {
                feedbackSuccess = false; feedbackAnim = 1f;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.get(), 0.8f));
                return true;
            }
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0f));
            parent.startDrawRequest();
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double delta) {
        targetScroll -= delta * 45;
        return true;
    }
}