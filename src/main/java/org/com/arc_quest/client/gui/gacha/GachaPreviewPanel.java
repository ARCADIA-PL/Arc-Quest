package org.com.arc_quest.client.gui.gacha;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.com.arc_quest.client.gui.HudAnimUtil;
import org.com.arc_quest.trade.gacha.api.GachaItem;
import org.com.arc_quest.trade.network.ClientGachaCache;

import java.util.List;

public class GachaPreviewPanel {

    private final GachaScreen parent;
    private int width, height;

    private double scrollOffset = 0;
    private double targetScroll = 0;
    private float[] hoverAnims;

    private float btnHoverAnim = 0f;

    // 【新增】按钮反馈动画（对标商店系统）
    private float feedbackAnim = 0f;
    private boolean feedbackSuccess = false;

    public GachaPreviewPanel(GachaScreen parent) {
        this.parent = parent;
    }

    public void init(int w, int h) {
        this.width = w;
        this.height = h;
        if (hoverAnims == null || hoverAnims.length != parent.getShopDef().getGachaPool().getItems().size()) {
            hoverAnims = new float[parent.getShopDef().getGachaPool().getItems().size()];
        }
    }

    public void render(GuiGraphics g, int mx, int my, float dt, float alpha, boolean waiting) {
        int pw = (int)(width * 0.8f);
        int ph = (int)(height * 0.85f);
        int px = (width - pw) / 2;
        int py = (height - ph) / 2;

        g.pose().pushPose();
        g.pose().translate(0, (1f - alpha) * 40f, 0); // 丝滑上浮入场

        // 1. 主背景板
        g.fill(px, py, px + pw, py + ph, (int)(180 * alpha) << 24 | 0x0A0A10);
        HudAnimUtil.drawFrame(g, px, py, pw, ph, 1, HudAnimUtil.withAlpha(parent.getShopDef().getThemeColor(), (int)(150 * alpha)));

        // 2. 标题区
        g.drawCenteredString(Minecraft.getInstance().font, parent.getShopDef().getDisplayName(), width / 2, py + 16, HudAnimUtil.withAlpha(0xFFFFFF, (int)(255 * alpha)));

        // 3. 保底进度区 (左侧)
        renderPityInfo(g, px + 20, py + 40, pw / 3, ph - 100, alpha);

        // 4. 奖池网格渲染 (右侧/中侧)
        int gridX = px + pw / 3 + 40;
        int gridY = py + 40;
        int gridW = pw - (pw / 3 + 60);
        int gridH = ph - 100;
        renderPoolGrid(g, gridX, gridY, gridW, gridH, mx, my, dt, alpha);

        // 5. 抽奖按钮 (底侧居中)
        renderDrawButton(g, px + pw/2 - 80, py + ph - 50, 160, 36, mx, my, dt, alpha, waiting);

        g.pose().popPose();
    }

    private void renderPityInfo(GuiGraphics g, int x, int y, int w, int h, float alpha) {
        var shopDef = parent.getShopDef();
        int pityThreshold = shopDef.getPityConfig() != null ? shopDef.getPityConfig().getPityThreshold() : 0;
        int progressPercent = ClientGachaCache.INSTANCE.getPityProgressPercent(parent.getShopId(), pityThreshold);
        int remaining = ClientGachaCache.INSTANCE.getPityRemaining(parent.getShopId(), pityThreshold);

        g.drawString(Minecraft.getInstance().font, "保底进度", x, y, HudAnimUtil.withAlpha(0xAAAAAA, (int)(255 * alpha)), true);

        // 进度条背景
        g.fill(x, y + 16, x + w - 20, y + 20, HudAnimUtil.withAlpha(0x333333, (int)(255 * alpha)));
        // 进度条填充
        int fillW = (int)((w - 20) * (progressPercent / 100f));
        if (fillW > 0) {
            g.fill(x, y + 16, x + fillW, y + 20, HudAnimUtil.withAlpha(shopDef.getThemeColor(), (int)(255 * alpha)));
            g.fill(x + fillW - 2, y + 15, x + fillW, y + 21, HudAnimUtil.withAlpha(0xFFFFFF, (int)(255 * alpha))); // 高光头
        }

        String countText = remaining > 0 ? "距离保底还剩: " + remaining + " 次" : (pityThreshold == 0 ? "无保底" : "下次必出！");
        g.drawString(Minecraft.getInstance().font, countText, x, y + 28, HudAnimUtil.withAlpha(0xFFFFFF, (int)(255 * alpha)), true);
    }

    private void renderPoolGrid(GuiGraphics g, int x, int y, int w, int h, int mx, int my, float dt, float alpha) {
        List<GachaItem> items = parent.getShopDef().getGachaPool().getItems();
        int cols = Math.max(1, w / 70);
        int gap = 10;
        int cardSize = (w - (cols - 1) * gap) / cols;

        scrollOffset += (targetScroll - scrollOffset) * Math.min(1.0, dt * 15.0);

        g.enableScissor(x, y, x + w, y + h);
        for (int i = 0; i < items.size(); i++) {
            GachaItem item = items.get(i);
            int row = i / cols;
            int col = i % cols;
            int drawX = x + col * (cardSize + gap);
            int drawY = y + row * (cardSize + gap) - (int)scrollOffset;

            if (drawY + cardSize < y || drawY > y + h) continue; // Culling

            boolean hov = mx >= drawX && mx < drawX + cardSize && my >= drawY && my < drawY + cardSize && my >= y && my <= y + h;
            hoverAnims[i] = HudAnimUtil.step(hoverAnims[i], hov ? 1f : 0f, 10f, dt);
            float hEase = HudAnimUtil.easeOutCubic(hoverAnims[i]);

            int themeC = parent.getShopDef().getEffectiveThemeColor(item);
            int bgA = (int) ((0x15 + 0x33 * hEase) * alpha);
            int bdA = (int) ((0x44 + 0x88 * hEase) * alpha);

            float scale = 1.0f + hEase * 0.05f;
            g.pose().pushPose();
            g.pose().translate(drawX + cardSize/2f, drawY + cardSize/2f, 0);
            g.pose().scale(scale, scale, 1f);
            g.pose().translate(-(drawX + cardSize/2f), -(drawY + cardSize/2f), 0);

            // 卡片底板
            g.fill(drawX, drawY, drawX + cardSize, drawY + cardSize, (bgA << 24) | 0x05050A);
            g.fillGradient(drawX, drawY, drawX + cardSize, drawY + cardSize, HudAnimUtil.withAlpha(themeC, (int)(80 * alpha * hEase)), 0);
            HudAnimUtil.drawFrame(g, drawX, drawY, cardSize, cardSize, 1, (bdA << 24) | (themeC & 0xFFFFFF));

            // 渲染物品 (居中放大)
            g.pose().pushPose();
            g.pose().translate(drawX + cardSize/2f - 8, drawY + cardSize/2f - 8, 0);
            g.pose().scale(1.5f, 1.5f, 1f);
            g.renderItem(item.getItemStack(), 0, 0);
            g.pose().popPose();

            g.pose().popPose();
        }
        g.disableScissor();

        // 简单限制滚动
        int maxScroll = Math.max(0, ((items.size() + cols - 1) / cols) * (cardSize + gap) - h);
        targetScroll = Math.max(0, Math.min(targetScroll, maxScroll));
    }

    private void renderDrawButton(GuiGraphics g, int x, int y, int w, int h, int mx, int my, float dt, float alpha, boolean waiting) {
        // 【修复】检查冷却和限购状态（对标商店系统）
        boolean onCooldown = ClientGachaCache.INSTANCE.isOnCooldown(parent.getShopId());
        int maxDraws = parent.getShopDef().getMaxDraws();
        int currentDraws = ClientGachaCache.INSTANCE.getTotalDraws(parent.getShopId());
        boolean limitReached = maxDraws > 0 && currentDraws >= maxDraws;
        
        // 【新增】检查成本是否充足（对标商店 canBuy）
        boolean costInsufficient = !waiting && !onCooldown && !limitReached && !ClientGachaCache.INSTANCE.canDraw(parent.getShopId());
        
        boolean canDraw = !waiting && !onCooldown && !limitReached && !costInsufficient;
        
        boolean hov = canDraw && mx >= x && mx < x + w && my >= y && my < y + h;
        btnHoverAnim = HudAnimUtil.step(btnHoverAnim, hov ? 1f : 0f, 12f, dt);
        float hEase = HudAnimUtil.easeOutCubic(btnHoverAnim);

        // 【新增】更新反馈动画
        if (feedbackAnim > 0) feedbackAnim = Math.max(0, feedbackAnim - dt * 2.5f);

        int baseColor;
        if (waiting || onCooldown || limitReached) {
            baseColor = 0x555555; // 灰色：冷却/限购
        } else if (costInsufficient) {
            baseColor = 0xFF5555; // 红色：成本不足
        } else {
            baseColor = parent.getShopDef().getThemeColor(); // 主题色：可抽取
        }
        
        // 【新增】反馈动画时增强边框亮度
        int bgAlpha = (int)((0x33 + 0x55 * hEase) * alpha);
        if (feedbackAnim > 0) {
            bgAlpha = Math.min(255, bgAlpha + (int)(80 * feedbackAnim * alpha));
        }

        g.fill(x, y, x + w, y + h, HudAnimUtil.withAlpha(baseColor, bgAlpha));
        
        // 【新增】反馈动画时增强边框
        int borderAlpha = (int)((0xAA + 0x55 * hEase) * alpha);
        if (feedbackAnim > 0) {
            borderAlpha = Math.min(255, borderAlpha + (int)(180 * feedbackAnim * alpha));
            baseColor = feedbackSuccess ? 0x55FF55 : 0xFF5555; // 成功绿色 / 失败红色
        }
        HudAnimUtil.drawFrame(g, x, y, w, h, 1, HudAnimUtil.withAlpha(baseColor, borderAlpha));

        // 显示冷却文本或正常文本
        String text;
        if (waiting) {
            text = "请稍候...";
        } else if (limitReached) {
            text = "已达上限";
        } else if (onCooldown) {
            String cooldownText = ClientGachaCache.INSTANCE.getCooldownText(parent.getShopId());
            text = cooldownText.isEmpty() ? "冷却中..." : cooldownText;
        } else if (costInsufficient) {
            text = "成本不足";
        } else {
            text = "抽取 1 次";
        }
        
        // 【新增】反馈动画时抖动效果
        int shakeX = 0;
        if (feedbackAnim > 0 && !feedbackSuccess) {
            shakeX = (int)(Math.sin(Util.getMillis() / 30.0) * feedbackAnim * 6);
        }
        
        g.drawCenteredString(Minecraft.getInstance().font, text, x + w/2 + shakeX, y + h/2 - 4, HudAnimUtil.withAlpha(0xFFFFFF, (int)(255 * alpha)));
    }

    public boolean mouseClicked(double mx, double my) {
        int pw = (int)(width * 0.8f), ph = (int)(height * 0.85f);
        int px = (width - pw) / 2, py = (height - ph) / 2;
        int btnX = px + pw/2 - 80, btnY = py + ph - 50, btnW = 160, btnH = 36;

        if (mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + btnH) {
            // 【修复】检查冷却和限购状态
            boolean onCooldown = ClientGachaCache.INSTANCE.isOnCooldown(parent.getShopId());
            int maxDraws = parent.getShopDef().getMaxDraws();
            int currentDraws = ClientGachaCache.INSTANCE.getTotalDraws(parent.getShopId());
            boolean limitReached = maxDraws > 0 && currentDraws >= maxDraws;
            
            // 【新增】检查成本是否充足（对标商店 canBuy）
            boolean costInsufficient = !onCooldown && !limitReached && !ClientGachaCache.INSTANCE.canDraw(parent.getShopId());
            
            if (onCooldown || limitReached || costInsufficient) {
                // 【新增】触发失败反馈动画
                feedbackSuccess = false;
                feedbackAnim = 1f;
                
                // 播放失败音效
                Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS, 0.8f)
                );
                return true; // 阻止抽奖
            }
            
            // 状态正常，发送抽奖请求
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            parent.startDrawRequest();
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double delta) {
        targetScroll -= delta * 40;
        return true;
    }
}