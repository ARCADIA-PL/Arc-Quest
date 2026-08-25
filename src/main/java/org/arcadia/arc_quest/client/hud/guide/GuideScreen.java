// file_name: GuideScreen.java
package org.arcadia.arc_quest.client.hud.guide;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.ponder.EmbeddedPonderScenePanel;
import org.arcadia.arc_quest.client.config.ArcQuestTextSettingsButton;
import org.arcadia.arc_quest.config.ArcQuestTextConfig;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.api.GuideMediaDefinition;
import org.arcadia.arc_quest.guide.api.GuideMediaType;
import org.arcadia.arc_quest.guide.api.GuidePageDefinition;
import org.arcadia.arc_quest.guide.network.C2SUpdateGuideProgressPacket;
import org.arcadia.arc_quest.guide.network.ClientGuideCache;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class GuideScreen extends Screen {
    private final ResourceLocation guideId;
    private final boolean markSeenOnClose;
    private final EmbeddedPonderScenePanel ponderPanel = new EmbeddedPonderScenePanel();

    private GuideDefinition guide;
    private int currentPage, themeColor;

    private float transitionAlpha = 0f;
    private boolean isClosing = false;
    private float dt = 0f;
    private long lastRenderTime = 0;

    // 高级滚动控制
    private double descScroll = 0.0, descTargetScroll = 0.0;
    private boolean isDraggingScrollbar = false;
    private double dragThumbYOffset = 0.0;
    private int cachedMaxScroll = 0;
    private int cachedDescH = 0;

    // 悬停动画状态
    private float closeHoverAnim = 0f;
    private float prevHoverAnim = 0f;
    private float nextHoverAnim = 0f;

    // 高级文本排版缓存
    private final List<RenderLine> cachedLines = new ArrayList<>();
    private int cachedContentHeight = 0;
    private int lastCachedWidth = -1;

    public GuideScreen(GuideDefinition guide, int initialPage, boolean markSeenOnClose) {
        super(guide.getTitle());
        this.guideId = guide.getId();
        this.markSeenOnClose = markSeenOnClose;
        this.guide = guide;
        this.currentPage = clampPage(initialPage);
        this.themeColor = guide.getCategory().getThemeColor();
    }

    public static boolean tryOpen(ResourceLocation guideId, int initialPage, boolean markSeenOnClose) {
        GuideDefinition guide = GuideRegistry.get(guideId);
        Minecraft mc = Minecraft.getInstance();
        if (guide == null || mc == null) return false;
        mc.setScreen(new GuideScreen(guide, initialPage, markSeenOnClose));
        return true;
    }

    @Override
    protected void init() {
        super.init();
        GuideDefinition resolved = GuideRegistry.get(guideId);
        if (resolved == null) { if (minecraft != null) minecraft.setScreen(null); return; }
        guide = resolved;
        currentPage = clampPage(currentPage);
        themeColor = guide.getCategory().getThemeColor();
        if (ClientGuideCache.INSTANCE.isUnlocked(guideId)) {
            ClientGuideCache.INSTANCE.applyLocalProgress(guideId, currentPage);
            ArcQuestNetwork.sendGuideProgress(new C2SUpdateGuideProgressPacket(guideId, currentPage));
        }

        transitionAlpha = 0f;
        isClosing = false;
        lastRenderTime = 0;

        closeHoverAnim = 0f;
        prevHoverAnim = 0f;
        nextHoverAnim = 0f;

        resetDesc();
        lastCachedWidth = -1;
        refreshMediaBinding();
    }

    @Override
    public void tick() {
        super.tick();
        ponderPanel.tick();
    }

    @Override
    public void removed() {
        ponderPanel.onScreenClosed();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) { }

    @Override
    public void onClose() {
        if (!isClosing) {
            isClosing = true;
            if (markSeenOnClose && minecraft != null && minecraft.player != null) {
                GuideCompletionClient.completeIfFinalPage(guide, guideId, currentPage);
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isClosing) return true;
        if (keyCode == 256 || (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode))) {
            onClose(); return true;
        }
        if (keyCode == 263 || keyCode == 65) { previousPage(); return true; }
        if (keyCode == 262 || keyCode == 68) { nextPage(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // --------------------------------------------------------
    // 尺寸控制: 放宽限制，恢复最佳阅读比例
    // --------------------------------------------------------
    private int getPanelW() { return GuideConstants.guidePanelWidth(this.width); }
    private int getPanelH() { return Math.min(this.height - 40, 580); }
    private int getPanelY() { return Math.max(20, (this.height - getPanelH()) / 2); }

    private int getPadLeft() { return 22; }
    private int getPadRight() { return 20; }
    private int getContentW() { return getPanelW() - getPadLeft() - getPadRight(); }

    private float textScale() { return (float) ArcQuestTextConfig.guideScale(); }

    private List<FormattedCharSequence> getSummaryLines() {
        if (guide == null || guide.getSummary().getString().isBlank()) return List.of();
        int width = Math.max(20, (int) ((getContentW() - 12) / 0.82f));
        List<FormattedCharSequence> lines = font.split(guide.getSummary(), width);
        return lines.size() <= 3 ? lines : lines.subList(0, 3);
    }

    private int getHeaderDividerY() {
        return getPanelY() + 20 + 24 + getSummaryLines().size() * 9;
    }

    private int getMediaY() {
        return getHeaderDividerY() + 12;
    }

    private int getMediaH() {
        if (isIconOnlyIntro()) return GuideConstants.INTRO_ICON_SECTION_HEIGHT;
        if (currentMedia().getType() == GuideMediaType.NONE
                && !(currentPage == 0 && guide.getVisualConfig().shouldRenderLargeIconOnIntro())) {
            return 0;
        }
        int aspectHeight = (int) (getContentW() * 9.0f / 16.0f);
        int reservedForDescriptionAndNavigation = 92;
        int available = getPanelY() + getPanelH() - getMediaY() - reservedForDescriptionAndNavigation;
        return Math.max(48, Math.min(aspectHeight, available));
    }

    private boolean isIconOnlyIntro() {
        return currentPage == 0
                && guide.getVisualConfig().shouldRenderLargeIconOnIntro()
                && currentMedia().getType() == GuideMediaType.NONE;
    }

    private int getDescriptionContentHeight() {
        return cachedContentHeight + 6;
    }

    private int getPanelX(float alpha, boolean closing) {
        float ease = closing ? HudAnimUtil.easeInCubic(alpha) : HudAnimUtil.easeOutCubic(alpha);
        int startX = -getPanelW() - 20;
        return (int) (startX + (0 - startX) * ease);
    }

    // --------------------------------------------------------
    // 高级排版引擎 (仿 QuestStoryPanel)
    // --------------------------------------------------------
    private void buildTextCache(int width) {
        if (lastCachedWidth == width && !cachedLines.isEmpty()) return;
        cachedLines.clear();
        cachedContentHeight = 0;
        lastCachedWidth = width;

        if (guide == null || guide.getPage(currentPage) == null) return;
        int currentY = 0;
        float textScale = textScale();
        int lineHeight = font.lineHeight + 5; // 增加行距，更舒适

        for (FormattedCharSequence line : font.split(
                GuideClientTextResolver.resolve(guide.getPage(currentPage).getDescriptionText().resolve(null, null)),
                Math.max(1, Math.round(width / textScale)))) {
            cachedLines.add(new RenderLine(line, currentY));
            currentY += lineHeight;
        }
        if (!cachedLines.isEmpty()) currentY += 8;
        cachedContentHeight = currentY;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double delta = scrollY;
        if (isClosing) return true;

        int panelX = getPanelX(transitionAlpha, false);
        int contentW = getContentW();
        int descY = getDescY();

        if (hit(mouseX, mouseY, panelX + getPadLeft(), descY, contentW, cachedDescH)) {
            if (cachedMaxScroll > 0) {
                descTargetScroll = Math.max(0, Math.min(cachedMaxScroll, descTargetScroll - delta * 20.0));
            }
            return true;
        }

        if (delta < 0) { nextPage(); return true; }
        if (delta > 0) { previousPage(); return true; }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        if (isDraggingScrollbar && cachedMaxScroll > 0) {
            int descY = getDescY();
            int thumbH = Math.max(16, (int) (cachedDescH * (cachedDescH / (float) getDescriptionContentHeight())));
            int trackH = cachedDescH - thumbH;

            double rawPercentage = (my - dragThumbYOffset - descY) / (double) trackH;
            descTargetScroll = Math.max(0.0, Math.min(1.0, rawPercentage)) * cachedMaxScroll;
            return true;
        }
        return super.mouseDragged(mx, my, button, dragX, dragY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ArcQuestTextSettingsButton.mouseClicked(this, mouseX, mouseY, button)) return true;
        if (isClosing || button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int panelX = getPanelX(transitionAlpha, false);
        int panelY = getPanelY();
        int panelW = getPanelW();
        int navY = panelY + getPanelH() - 24;

        // 图形导航按钮 Hitbox
        int btnArea = 16;
        int curX = panelX + panelW - getPadRight();

        if (canNext()) {
            curX -= btnArea;
            if (hit(mouseX, mouseY, curX - 4, navY - 4, btnArea + 8, btnArea + 8)) { nextPage(); return true; }
            curX -= 8; // btn gap
        }
        if (canPrev()) {
            curX -= btnArea;
            if (hit(mouseX, mouseY, curX - 4, navY - 4, btnArea + 8, btnArea + 8)) { previousPage(); return true; }
        }

        // 滑条拖拽 Hitbox
        if (cachedMaxScroll > 0) {
            int descY = getDescY();
            int rx = panelX + panelW - 11;
            if (hit(mouseX, mouseY, rx - 4, descY, 12, cachedDescH)) {
                isDraggingScrollbar = true;
                int thumbH = Math.max(16, (int) (cachedDescH * (cachedDescH / (float) getDescriptionContentHeight())));
                int trackH = cachedDescH - thumbH;
                int ty = descY + (int) (trackH * (descScroll / (double) cachedMaxScroll));

                if (mouseY >= ty && mouseY <= ty + thumbH) dragThumbYOffset = mouseY - ty;
                else {
                    dragThumbYOffset = thumbH / 2.0;
                    double rawPercentage = (mouseY - dragThumbYOffset - descY) / (double) trackH;
                    descTargetScroll = Math.max(0.0, Math.min(1.0, rawPercentage)) * cachedMaxScroll;
                }
                return true;
            }
        }

        int closeX = panelX + panelW - 24;
        int closeY = panelY + 16;
        if (hit(mouseX, mouseY, closeX - 4, closeY - 4, 16, 16)) { onClose(); return true; }

        int mediaY = getMediaY();
        int contentW = getContentW();
        int mediaH = getMediaH();
        if (currentMedia().getType() == GuideMediaType.PONDER) {
            if (ponderPanel.mouseClicked(mouseX, mouseY, button, panelX + getPadLeft(), mediaY, contentW, mediaH)) return true;
        }
        if (!hit(mouseX, mouseY, panelX, panelY, panelW, getPanelH())) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDraggingScrollbar) {
            isDraggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private int getDescY() {
        return getMediaY() + getMediaH() + 14;
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (guide == null) return;

        long now = Util.getMillis();
        if (lastRenderTime == 0) lastRenderTime = now;
        dt = (now - lastRenderTime) / 1000f;
        lastRenderTime = now;
        if (dt > 0.1f) dt = 0.1f;

        transitionAlpha = HudAnimUtil.lerp(transitionAlpha, isClosing ? 0f : 1f, isClosing ? 0.2f : 0.12f, dt);
        if (isClosing && transitionAlpha <= 0.01f) {
            if (minecraft != null && minecraft.screen == this) minecraft.setScreen(null);
            return;
        }

        int panelW = getPanelW();
        int panelH = getPanelH();
        int padL = getPadLeft();
        int padR = getPadRight();
        int contentW = getContentW();

        int panelX = getPanelX(transitionAlpha, isClosing);
        int panelY = getPanelY();

        int safeAlpha = (int) (255 * transitionAlpha);
        if (safeAlpha <= 20) return;

        descScroll += (descTargetScroll - descScroll) * Math.min(1.0f, dt * 16f);

        // ==========================================
        // 1. 底板与边框 (通透全息、完全贴边)
        // ==========================================
        int bgA = (int) (transitionAlpha * 0x90);
        int accentA = (int) (transitionAlpha * 255);

        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, HudAnimUtil.withAlpha(0x111214, bgA));
        g.fill(panelX, panelY, panelX + panelW, panelY + 1, HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x22 * transitionAlpha)));
        g.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x22 * transitionAlpha)));
        HudRenderUtil.drawCyberneticEdge(g, panelX, panelY, panelH, themeColor, accentA);

        // 机能点缀纹理
        int decorX = panelX + panelW - 6;
        g.fill(decorX, panelY + 16, decorX + 2, panelY + 24, HudAnimUtil.withAlpha(themeColor, (int) (0xAA * transitionAlpha)));
        g.fill(decorX, panelY + 28, decorX + 2, panelY + 44, HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x33 * transitionAlpha)));
        g.fill(decorX, panelY + panelH - 40, decorX + 2, panelY + panelH - 16, HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x1A * transitionAlpha)));

        // ==========================================
        // 2. 布局推演
        // ==========================================
        int textBaseX = panelX + padL;
        int titleY = panelY + 20;
        int dividerY = getHeaderDividerY();
        int mediaY = getMediaY();
        int mediaH = getMediaH();
        int descY = getDescY();
        int navY = panelY + panelH - 24;

        cachedDescH = Math.max(24, navY - 16 - descY);
        buildTextCache(contentW - 14); // 减去滑条空间

        // ==========================================
        // 3. Header 渲染
        // ==========================================
        String guideTitle = font.plainSubstrByWidth(guide.getTitle().getString(), contentW - 20);
        g.drawString(font, "GUIDE DATABLOCK", textBaseX, titleY, HudAnimUtil.withAlpha(0x778899, accentA), false);
        g.drawString(font, guideTitle, textBaseX, titleY + 10, HudAnimUtil.withAlpha(0xFFFFFF, accentA), true);

        List<FormattedCharSequence> summaryLines = getSummaryLines();
        if (!summaryLines.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(textBaseX, titleY + 24, 0);
            g.pose().scale(0.82f, 0.82f, 1f);
            for (FormattedCharSequence line : summaryLines) {
                g.drawString(font, line, 0, 0, HudAnimUtil.withAlpha(0xAAB3BD, accentA), false);
                g.pose().translate(0, font.lineHeight + 2, 0);
            }
            g.pose().popPose();
        }

        g.fill(textBaseX, dividerY, textBaseX + contentW, dividerY + 1, HudAnimUtil.withAlpha(themeColor, (int)(accentA * 0.8f)));
        g.fill(textBaseX, dividerY, textBaseX + 2, dividerY + 6, HudAnimUtil.withAlpha(themeColor, accentA));

        // ==========================================
        // 4. Media 区域
        // ==========================================
        boolean iconOnlyIntro = isIconOnlyIntro();
        if (mediaH > 0 && !iconOnlyIntro) {
            HudAnimUtil.drawFrame(g, textBaseX - 1, mediaY - 1, contentW + 2, mediaH + 2,
                    HudAnimUtil.withAlpha(0x000000, safeAlpha),
                    HudAnimUtil.withAlpha(0x333333, safeAlpha));
        }
        if (transitionAlpha >= 0.38f
                && currentPage == 0
                && guide.getVisualConfig().shouldRenderLargeIconOnIntro()) {
            g.pose().pushPose();
            g.pose().translate(textBaseX + contentW / 2f - GuideConstants.INTRO_ICON_SIZE / 2f,
                    mediaY + (mediaH - GuideConstants.INTRO_ICON_SIZE) / 2f, 0);
            g.pose().scale(GuideConstants.INTRO_ICON_SCALE, GuideConstants.INTRO_ICON_SCALE, 1f);
            g.renderItem(guide.getVisualConfig().getIcon(), 0, 0);
            g.pose().popPose();
        } else if (mediaH > 0) {
            GuideMediaRenderer.drawMedia(this, g, textBaseX, mediaY, contentW, mediaH, currentMedia(), ponderPanel, mouseX, mouseY, partialTick, safeAlpha, themeColor);
        }

        // ==========================================
        // 5. Description 渲染 (带渐变掩膜的高级排版)
        // ==========================================
        cachedMaxScroll = Math.max(0, getDescriptionContentHeight() - cachedDescH);
        descTargetScroll = Math.max(0, Math.min(cachedMaxScroll, descTargetScroll));

        g.enableScissor(textBaseX, descY, textBaseX + contentW, descY + cachedDescH);
        int sy = descY - (int) Math.round(descScroll);

        for (RenderLine line : cachedLines) {
            int lineY = sy + line.yOffset;
            // 简单视锥剔除优化
            if (lineY + font.lineHeight >= descY && lineY <= descY + cachedDescH) {
                g.pose().pushPose();
                g.pose().translate(textBaseX, lineY, 0);
                g.pose().scale(textScale(), textScale(), 1f);
                g.drawString(font, line.text, 0, 0, HudAnimUtil.withAlpha(0xDDDDDD, safeAlpha), true);
                g.pose().popPose();
            }
        }
        g.disableScissor();

        // 渐变掩膜 (同 Phase 卡片风格)
        if (cachedMaxScroll > 0) {
            int gradientW = contentW - 12;
            if (descScroll > 1.0)
                g.fillGradient(textBaseX, descY, textBaseX + gradientW, descY + 8, HudAnimUtil.withAlpha(0x111214, safeAlpha), HudAnimUtil.withAlpha(0x111214, 0));
            if (descScroll < cachedMaxScroll - 1.0)
                g.fillGradient(textBaseX, descY + cachedDescH - 8, textBaseX + gradientW, descY + cachedDescH, HudAnimUtil.withAlpha(0x111214, 0), HudAnimUtil.withAlpha(0x111214, safeAlpha));

            // 滑条轨迹与滑块
            int rx = panelX + panelW - 11;
            g.fill(rx, descY, rx + 4, descY + cachedDescH, HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x18 * transitionAlpha)));
            int th = Math.max(16, (int) (cachedDescH * (cachedDescH / (float) getDescriptionContentHeight())));
            int travel = Math.max(0, cachedDescH - th);
            int ty = descY + (int) (travel * (descScroll / (double) cachedMaxScroll));

            int thumbColor = isDraggingScrollbar ? 0xFFFFFF : themeColor;
            g.fill(rx, ty, rx + 4, ty + th, HudAnimUtil.withAlpha(thumbColor, safeAlpha));
        }

        // ==========================================
        // 6. 极简底部导航 (图形化按钮)
        // ==========================================
        String pageStr = String.format("PAGE %d / %d", currentPage + 1, guide.getPageCount());
        g.drawString(font, pageStr, textBaseX, navY, HudAnimUtil.withAlpha(0x555555, safeAlpha), false);

        int curBtnX = panelX + panelW - padR;
        int btnSize = 16;

        if (canNext()) {
            curBtnX -= btnSize;
            boolean hoverN = hit(mouseX, mouseY, curBtnX - 4, navY - 4, btnSize + 8, btnSize + 8);
            nextHoverAnim = HudAnimUtil.step(nextHoverAnim, hoverN ? 1f : 0f, 15f, dt);

            g.pose().pushPose();
            float s = 1.0f + 0.1f * nextHoverAnim;
            g.pose().translate(curBtnX + btnSize / 2f, navY + btnSize / 2f - 4, 0);
            g.pose().scale(s, s, 1f);
            g.drawString(font, ">", -font.width(">") / 2f, -font.lineHeight / 2f + 1, HudAnimUtil.withAlpha(HudAnimUtil.lerpColor(0x666666, themeColor, nextHoverAnim), safeAlpha), false);
            g.pose().popPose();
            if (!ClientGuideCache.INSTANCE.isSeen(guideId)) {
                HudRenderUtil.drawBreathingRedDot(g, curBtnX + btnSize - 1, navY - 1,
                        safeAlpha / 255f);
            }

            curBtnX -= 8; // 间距
        }

        if (canPrev()) {
            curBtnX -= btnSize;
            boolean hoverP = hit(mouseX, mouseY, curBtnX - 4, navY - 4, btnSize + 8, btnSize + 8);
            prevHoverAnim = HudAnimUtil.step(prevHoverAnim, hoverP ? 1f : 0f, 15f, dt);

            g.pose().pushPose();
            float s = 1.0f + 0.1f * prevHoverAnim;
            g.pose().translate(curBtnX + btnSize / 2f, navY + btnSize / 2f - 4, 0);
            g.pose().scale(s, s, 1f);
            g.drawString(font, "<", -font.width("<") / 2f, -font.lineHeight / 2f + 1, HudAnimUtil.withAlpha(HudAnimUtil.lerpColor(0x666666, themeColor, prevHoverAnim), safeAlpha), false);
            g.pose().popPose();
        }

        // ==========================================
        // 7. 关闭按钮 [X]
        // ==========================================
        int closeX = panelX + panelW - 24;
        int closeY = panelY + 16;
        closeHoverAnim = HudAnimUtil.step(closeHoverAnim, hit(mouseX, mouseY, closeX - 4, closeY - 4, 16, 16) ? 1f : 0f, 15f, dt);
        int closeColor = HudAnimUtil.withAlpha(themeColor, (int) (safeAlpha * (0.6f + 0.4f * HudAnimUtil.easeOutCubic(closeHoverAnim))));
        g.drawString(font, "\u2715", closeX, closeY, closeColor, false);
        ArcQuestTextSettingsButton.render(g, font, mouseX, mouseY);
    }

    private void nextPage() {
        if (canNext()) {
            currentPage++;
            onPageChange();
            GuideCompletionClient.completeIfFinalPage(guide, guideId, currentPage);
        }
    }
    private void previousPage() { if (canPrev()) { currentPage--; onPageChange(); } }

    // 严格的逻辑判断，确保首页无 PREV，尾页无 NEXT
    private boolean canPrev() { return currentPage > 0; }
    private boolean canNext() { return guide != null && currentPage < guide.getPageCount() - 1; }

    private void onPageChange() {
        ClientGuideCache.INSTANCE.applyLocalProgress(guideId, currentPage);
        ArcQuestNetwork.sendGuideProgress(new C2SUpdateGuideProgressPacket(guideId, currentPage));
        resetDesc();
        lastCachedWidth = -1; // 强制刷新排版
        refreshMediaBinding();
    }

    private void resetDesc() {
        descScroll = 0.0;
        descTargetScroll = 0.0;
        isDraggingScrollbar = false;
    }

    private void refreshMediaBinding() {
        GuideMediaDefinition m = currentMedia();
        if (m.getType() == GuideMediaType.PONDER && m.getSceneId() != null)
            ponderPanel.bind(m.getSceneId(), themeColor, m.isAutoplay());
        else if (m.getType() != GuideMediaType.PONDER)
            ponderPanel.unbind();
    }

    private GuidePageDefinition page() { return guide.getPage(currentPage); }
    private GuideMediaDefinition currentMedia() { return page().getMedia(); }
    private int clampPage(int p) { return Math.max(0, Math.min(p, guide.getPageCount() - 1)); }
    private boolean hit(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx <= x + w && my >= y && my <= y + h; }

    private static class RenderLine {
        FormattedCharSequence text;
        int yOffset;
        RenderLine(FormattedCharSequence t, int y) { text = t; yOffset = y; }
    }
}
