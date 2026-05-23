package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.ponder.EmbeddedPonderScenePanel;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.api.GuideMediaDefinition;
import org.arcadia.arc_quest.guide.api.GuideMediaType;
import org.arcadia.arc_quest.guide.api.GuidePageDefinition;
import org.arcadia.arc_quest.guide.network.C2SMarkGuideSeenPacket;
import org.arcadia.arc_quest.guide.network.ClientGuideCache;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GuideScreen extends Screen {
    private final ResourceLocation guideId;
    private final boolean markSeenOnClose;
    private final EmbeddedPonderScenePanel ponderPanel = new EmbeddedPonderScenePanel();
    private final Map<Integer, List<FormattedCharSequence>> descCache = new HashMap<>();

    private GuideDefinition guide;
    private int currentPage, themeColor;

    private float transitionAlpha = 0f;
    private boolean isClosing = false;
    private float dt = 0f;
    private long lastRenderTime = 0;

    private double descScroll = 0.0, descTargetScroll = 0.0;
    private float prevHoverAnim, nextHoverAnim, closeHoverAnim;

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

        transitionAlpha = 0f;
        isClosing = false;
        lastRenderTime = 0;
        prevHoverAnim = nextHoverAnim = closeHoverAnim = 0f;
        descCache.clear();
        resetDesc();
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
    public void renderBackground(@NotNull GuiGraphics g) { }

    @Override
    public void onClose() {
        if (!isClosing) {
            isClosing = true;
            if (markSeenOnClose && minecraft != null && minecraft.player != null && !ClientGuideCache.INSTANCE.isSeen(guideId)) {
                ClientGuideCache.INSTANCE.applyLocalSeen(guideId);
                ArcQuestNetwork.sendMarkGuideSeen(new C2SMarkGuideSeenPacket(guideId.toString()));
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

    private int getPanelTopW() { return Math.max(260, Math.min(420, (int) (this.width * 0.28f))); }
    private int getSlantW() { return 36; }
    private int getPanelBottomW() { return getPanelTopW() - getSlantW(); }
    private int getPadX() { return 24; }
    private int getContentW() { return getPanelBottomW() - getPadX() * 2; }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isClosing) return true;

        float easeProgress = HudAnimUtil.easeOutCubic(transitionAlpha);
        int panelX = (int) -((1f - easeProgress) * (getPanelTopW() + 10f));

        int contentW = getContentW();
        int mediaH = (int) (contentW * 0.55f);
        int descY = 70 + mediaH + 16;
        int navY = this.height - 28;
        int descH = navY - 10 - descY;

        int contentH = lines(contentW).size() * font.lineHeight;

        if (hit(mouseX, mouseY, panelX + getPadX(), descY, contentW, descH)) {
            if (contentH > descH) {
                descTargetScroll = Math.max(0, Math.min(contentH - descH + 4, descTargetScroll - delta * 18.0));
            }
            return true;
        }
        if (delta < 0) { nextPage(); return true; }
        if (delta > 0) { previousPage(); return true; }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isClosing || button != 0) return super.mouseClicked(mouseX, mouseY, button);

        float easeProgress = HudAnimUtil.easeOutCubic(transitionAlpha);
        int panelX = (int) -((1f - easeProgress) * (getPanelTopW() + 10f));

        int padX = getPadX();
        int contentW = getContentW();
        int navY = this.height - 28;

        int nextBtnX = panelX + padX + contentW - 12;
        int prevBtnX = nextBtnX - 30;
        if (hit(mouseX, mouseY, prevBtnX - 10, navY - 6, 20, 20) && canPrev()) { previousPage(); return true; }
        if (hit(mouseX, mouseY, nextBtnX - 10, navY - 6, 20, 20) && canNext()) { nextPage(); return true; }

        int closeX = panelX + getPanelTopW() - 20;
        if (hit(mouseX, mouseY, closeX - 10, 6, 24, 24)) { onClose(); return true; }

        int mediaY = 70;
        int mediaH = (int) (contentW * 0.55f);
        if (currentMedia().getType() == GuideMediaType.PONDER) {
            if (ponderPanel.mouseClicked(mouseX, mouseY, button, panelX + padX, mediaY, contentW, mediaH)) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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

        int panelTopW = getPanelTopW();
        int panelBottomW = getPanelBottomW();
        int padX = getPadX();
        int contentW = getContentW();

        float easeProgress = isClosing ? HudAnimUtil.easeInCubic(transitionAlpha) : HudAnimUtil.easeOutCubic(transitionAlpha);
        int panelX = (int) -((1f - easeProgress) * (panelTopW + 10f));
        int safeAlpha = (int) (255 * transitionAlpha);
        if (safeAlpha <= 4) return;

        descScroll += (descTargetScroll - descScroll) * Math.min(1.0f, dt * 14f);

        int sh = this.height;

        int bgTint = HudAnimUtil.lerpColor(0x000000, themeColor, 0.04f);
        int slantAlpha = (int) (245 * transitionAlpha);
        int bgColor = HudAnimUtil.withAlpha(bgTint, slantAlpha);
        g.fill(panelX, 0, panelX + panelTopW, sh, bgColor);

        HudRenderUtil.drawCyberneticEdge(g, panelX, 0, sh, themeColor, safeAlpha);

        int slantEdgeAlpha = (int)(255 * transitionAlpha);
        for (int row = 0; row < sh; row++) {
            float t = row / (float) sh;
            int x = panelX + panelBottomW + (int)((panelTopW - panelBottomW) * (1f - t));
            g.fill(x, row, x + 1, row + 1, HudAnimUtil.withAlpha(themeColor, (int)(slantEdgeAlpha * (0.4f + 0.3f * t))));
        }

        int idY = 20, titleY = 34;
        int mediaY = 70;
        int mediaH = (int) (contentW * 0.55f);
        int descY = mediaY + mediaH + 16;
        int navY = sh - 28;
        int descH = navY - 10 - descY;

        g.drawString(font, guideId.toString().toUpperCase(), panelX + padX, idY, HudAnimUtil.withAlpha(0x666666, safeAlpha), false);

        String titleText = guide.getTitle().getString();
        String displayTitle = font.plainSubstrByWidth(titleText, panelTopW - padX * 2 - 30);
        g.drawString(font, displayTitle, panelX + padX, titleY, HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha), true);

        g.fill(panelX + padX, mediaY - 10, panelX + padX + contentW / 2, mediaY - 9,
                HudAnimUtil.withAlpha(themeColor, (int) (safeAlpha * 0.7F)));

        HudAnimUtil.drawFrame(g, panelX + padX - 1, mediaY - 1, contentW + 2, mediaH + 2,
                HudAnimUtil.withAlpha(0x000000, (int) (safeAlpha * 0.6f)),
                HudAnimUtil.withAlpha(themeColor, (int) (safeAlpha * 0.4f)));
        GuideMediaRenderer.drawMedia(this, g, panelX + padX, mediaY, contentW, mediaH, currentMedia(), ponderPanel, mouseX, mouseY, partialTick, safeAlpha, themeColor);

        List<FormattedCharSequence> wrapped = lines(contentW);
        int contentH = wrapped.size() * font.lineHeight;
        int maxScroll = Math.max(0, contentH - descH);
        descTargetScroll = Math.max(0, Math.min(maxScroll, descTargetScroll));

        g.enableScissor(panelX + padX, descY, panelX + padX + contentW, descY + descH);
        int sy = descY - (int) Math.round(descScroll);
        for (int i = 0; i < wrapped.size(); i++) {
            g.drawString(font, wrapped.get(i), panelX + padX, sy + i * font.lineHeight, HudAnimUtil.withAlpha(0xAAAAAA, safeAlpha));
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int rx = panelX + padX + contentW + 4;
            g.fill(rx, descY, rx + 2, descY + descH, HudAnimUtil.withAlpha(0x1A1A1A, safeAlpha));
            int th = Math.max(12, (int) (descH * (descH / (float) contentH)));
            int travel = Math.max(0, descH - th);
            int ty = descY + (int) (travel * (descScroll / (double) maxScroll));
            g.fill(rx, ty, rx + 2, ty + th, HudAnimUtil.withAlpha(themeColor, safeAlpha));
        }

        String pageStr = "PAGE " + (currentPage + 1) + " / " + guide.getPageCount();
        g.drawString(font, pageStr, panelX + padX, navY, HudAnimUtil.withAlpha(0x888888, safeAlpha), false);

        if (guide.getPageCount() > 1) {
            int btnW = 50, btnH = 16;
            int btnNextX = panelX + padX + contentW - btnW;
            int btnPrevX = btnNextX - btnW - 6;
            prevHoverAnim = HudAnimUtil.step(prevHoverAnim, hit(mouseX, mouseY, btnPrevX, navY - 2, btnW, btnH) && canPrev() ? 1f : 0f, 15f, dt);
            nextHoverAnim = HudAnimUtil.step(nextHoverAnim, hit(mouseX, mouseY, btnNextX, navY - 2, btnW, btnH) && canNext() ? 1f : 0f, 15f, dt);
            GuideNavigationControls.drawCyberButton(g, font, btnPrevX, navY - 2, btnW, btnH,
                    "< PREV", themeColor, transitionAlpha,
                    HudAnimUtil.easeOutCubic(prevHoverAnim), hit(mouseX, mouseY, btnPrevX, navY - 2, btnW, btnH) && canPrev());
            GuideNavigationControls.drawCyberButton(g, font, btnNextX, navY - 2, btnW, btnH,
                    "NEXT >", themeColor, transitionAlpha,
                    HudAnimUtil.easeOutCubic(nextHoverAnim), hit(mouseX, mouseY, btnNextX, navY - 2, btnW, btnH) && canNext());
        }

        int closeX = panelX + panelTopW - 20;
        int closeY = 16;
        closeHoverAnim = HudAnimUtil.step(closeHoverAnim, hit(mouseX, mouseY, closeX - 10, closeY - 10, 24, 24) ? 1f : 0f, 15f, dt);
        int closeColor = HudAnimUtil.withAlpha(themeColor, (int) (safeAlpha * (0.6f + 0.4f * HudAnimUtil.easeOutCubic(closeHoverAnim))));
        g.drawString(font, "\u2715", closeX - 4, closeY - 4, closeColor, false);
    }

    private void nextPage() { if (canNext()) { currentPage++; onPageChange(); } }
    private void previousPage() { if (canPrev()) { currentPage--; onPageChange(); } }
    private boolean canPrev() { return currentPage > 0; }
    private boolean canNext() { return guide != null && currentPage < guide.getPageCount() - 1; }

    private void onPageChange() {
        resetDesc();
        descCache.clear();
        refreshMediaBinding();
    }

    private void resetDesc() { descScroll = 0.0; descTargetScroll = 0.0; }

    private void refreshMediaBinding() {
        GuideMediaDefinition m = currentMedia();
        if (m.getType() == GuideMediaType.PONDER && m.getSceneId() != null)
            ponderPanel.bind(m.getSceneId(), themeColor, m.isAutoplay());
        else if (m.getType() != GuideMediaType.PONDER)
            ponderPanel.unbind();
    }

    private GuidePageDefinition page() { return guide.getPage(currentPage); }
    private GuideMediaDefinition currentMedia() { return page().getMedia(); }
    private List<FormattedCharSequence> lines(int width) {
        return descCache.computeIfAbsent(width, w -> font.split(page().getDescriptionText().resolve(null, null), w));
    }
    private int clampPage(int p) { return Math.max(0, Math.min(p, guide.getPageCount() - 1)); }
    private boolean hit(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx <= x + w && my >= y && my <= y + h; }
}
