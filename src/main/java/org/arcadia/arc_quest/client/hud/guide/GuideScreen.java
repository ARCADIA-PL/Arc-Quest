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
    private float openAnim = 0.0F;
    private float closeAnim = 0.0F;
    private float pageTransitionAnim = 1.0F;
    private boolean closing;
    private double descScroll = 0.0, descTargetScroll = 0.0;
    private long lastRenderTime;
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
        openAnim = 0.0F;
        closeAnim = 0.0F;
        closing = false;
        pageTransitionAnim = 1.0F;
        lastRenderTime = System.currentTimeMillis();
        prevHoverAnim = nextHoverAnim = closeHoverAnim = 0f;
        descCache.clear();
        resetDesc();
        refreshMediaBinding();
    }

    @Override
    public void tick() {
        super.tick();
        long now = System.currentTimeMillis();
        float dt = (now - lastRenderTime) / 1000f;
        if (dt <= 0f || dt > 0.3f) dt = 1f / 60f;
        lastRenderTime = now;

        if (closing) {
            closeAnim = HudAnimUtil.advanceByDuration(closeAnim, GuideConstants.CLOSE_DURATION, dt);
            if (closeAnim >= 1.0F && minecraft != null) minecraft.setScreen(null);
            ponderPanel.tick();
            return;
        }

        openAnim = HudAnimUtil.advanceByDuration(openAnim, GuideConstants.OPEN_DURATION, dt);
        descScroll += (descTargetScroll - descScroll) * Math.min(1.0f, dt * GuideConstants.SCROLL_SPEED);
        pageTransitionAnim += (1.0F - pageTransitionAnim) * Math.min(1.0f, dt * 8f);
        ponderPanel.tick();
    }

    @Override
    public void removed() {
        ponderPanel.onScreenClosed();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void onClose() {
        if (!closing) {
            closing = true;
            closeAnim = 0.0F;
            lastRenderTime = System.currentTimeMillis();
            if (markSeenOnClose && minecraft != null && minecraft.player != null
                    && !ClientGuideCache.INSTANCE.isSeen(guideId)) {
                ClientGuideCache.INSTANCE.applyLocalSeen(guideId);
                ArcQuestNetwork.sendMarkGuideSeen(new C2SMarkGuideSeenPacket(guideId.toString()));
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (closing) return true;
        if (keyCode == 256 || (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode))) {
            onClose(); return true;
        }
        if (keyCode == 263 || keyCode == 65) { previousPage(); return true; }
        if (keyCode == 262 || keyCode == 68) { nextPage(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (closing) return true;
        int pnlW = panelW();
        int pnlX = width - pnlW;
        int descW = pnlW - 40;
        int descH = height - 246;
        int contentH = lines(descW).size() * GuideScreenLayout.textLineHeight();
        if (hit(mouseX, mouseY, pnlX + 20, 194, descW, descH)) {
            if (contentH > descH) {
                descTargetScroll = Math.max(0, Math.min(contentH - descH + 4, descTargetScroll - delta * 14.0));
            }
            return true;
        }
        if (delta < 0) { nextPage(); return true; }
        if (delta > 0) { previousPage(); return true; }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing || button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int pnlW = panelW();
        int pnlX = width - pnlW;
        int by = height - 32;
        int btnW = 60;
        int btnH = 18;

        if (hit(mouseX, mouseY, pnlX + 18, by, btnW, btnH) && canPrev()) { previousPage(); return true; }
        if (hit(mouseX, mouseY, pnlX + 18 + btnW + 10, by, btnW, btnH) && canNext()) { nextPage(); return true; }
        if (hit(mouseX, mouseY, pnlX + pnlW - 28, 14, 18, 18)) { onClose(); return true; }

        if (currentMedia().getType() == GuideMediaType.PONDER && ponderPanel.mouseClicked(mouseX, mouseY, button, pnlX + 12, 74, pnlW - 24, 110))
            return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (guide == null) return;
        float reveal = HudAnimUtil.easeOutCubic(openAnim);
        float exitReveal = HudAnimUtil.easeInCubic(closeAnim);
        float effective = Math.max(0f, Math.min(1f, reveal * (1f - exitReveal)));
        int alpha = (int) (255 * effective);
        if (alpha <= 4) return;

        float dt = (System.currentTimeMillis() - lastRenderTime) / 1000f;
        if (dt <= 0f || dt > 0.3f) dt = 1f / 60f;

        int bgTint = HudAnimUtil.lerpColor(0x000000, themeColor, 0.05f);
        g.fill(0, 0, width, height, HudAnimUtil.withAlpha(bgTint, (int) (180 * effective)));

        int pnlW = panelW();
        int pnlX = width - pnlW;
        int pnlH = height - 4;

        g.fill(pnlX, 0, width, pnlH, HudAnimUtil.withAlpha(0x000000, (int) (0x55 * effective)));
        HudRenderUtil.drawCyberneticEdge(g, pnlX, 0, pnlH, themeColor, (int) (0xFF * effective));

        int tx = pnlX + 16;
        g.drawString(font, guideId.toString().toUpperCase(), tx, 18, HudAnimUtil.withAlpha(0x888888, alpha), false);

        String titleText = guide.getTitle().getString();
        int titleMaxW = pnlW - 32;
        String displayTitle = font.plainSubstrByWidth(titleText, titleMaxW);
        float titleScale = 0.95f + 0.05f * reveal;
        g.pose().pushPose();
        g.pose().translate(tx, 30, 0);
        g.pose().scale(titleScale, titleScale, 1f);
        g.drawString(font, displayTitle, 0, 0, HudAnimUtil.withAlpha(0xFFFFFF, (int) (alpha * pageTransitionAnim)), true);
        g.pose().popPose();

        g.fill(tx, 44, pnlX + pnlW - 18, 45, HudAnimUtil.withAlpha(themeColor, (int) (alpha * 0.6F * pageTransitionAnim)));
        g.fill(tx, 45, pnlX + pnlW - 18, 46, HudAnimUtil.withAlpha(themeColor, (int) (alpha * 0.2F * pageTransitionAnim)));

        int mediaW = pnlW - 24;
        int mediaH = 110;
        GuideMediaRenderer.drawMedia(this, g, pnlX + 12, 55, mediaW, mediaH, currentMedia(), ponderPanel, mouseX, mouseY, partialTick,
                (int) (alpha * pageTransitionAnim), themeColor);

        int descY = 175;
        int descH = height - 226;
        List<FormattedCharSequence> wrapped = lines(mediaW);
        int contentH = wrapped.size() * GuideScreenLayout.textLineHeight();
        int max = Math.max(0, contentH - descH);
        descTargetScroll = Math.max(0, Math.min(max, descTargetScroll));
        descScroll = Math.max(0, Math.min(max, descScroll));

        g.enableScissor(pnlX + 18, descY, pnlX + pnlW - 18, descY + descH);
        int sy = descY - (int) Math.round(descScroll);
        for (int i = 0; i < wrapped.size(); i++) {
            g.drawString(font, wrapped.get(i), tx, sy + i * GuideScreenLayout.textLineHeight(),
                    HudAnimUtil.withAlpha(0xCCCCCC, (int) (alpha * pageTransitionAnim)));
        }
        g.disableScissor();

        if (max > 0) {
            int rx = pnlX + pnlW - 8;
            g.fill(rx, descY, rx + 2, descY + descH, HudAnimUtil.withAlpha(0x111823, alpha));
            int th = Math.max(12, (int) (descH * (descH / (float) contentH)));
            int travel = Math.max(0, descH - th);
            int ty = descY + (int) (travel * (descScroll / (double) max));
            g.fill(rx, ty, rx + 2, ty + th, HudAnimUtil.withAlpha(themeColor, alpha));
        }

        int by = height - 32;
        g.fill(pnlX + 18, by - 8, pnlX + pnlW - 18, by - 7, HudAnimUtil.withAlpha(themeColor, (int) (alpha * 0.3F)));

        int btnW = 60, btnH = 18;
        prevHoverAnim = HudAnimUtil.step(prevHoverAnim, hit(mouseX, mouseY, pnlX + 18, by, btnW, btnH) && canPrev() ? 1f : 0f, 8f, dt);
        nextHoverAnim = HudAnimUtil.step(nextHoverAnim, hit(mouseX, mouseY, pnlX + 18 + btnW + 10, by, btnW, btnH) && canNext() ? 1f : 0f, 8f, dt);
        GuideNavigationControls.drawCyberButton(g, font, pnlX + 18, by, btnW, btnH, canPrev() ? "< PREV" : "", themeColor, effective,
                HudAnimUtil.easeOutCubic(prevHoverAnim), hit(mouseX, mouseY, pnlX + 18, by, btnW, btnH) && canPrev());
        GuideNavigationControls.drawCyberButton(g, font, pnlX + 18 + btnW + 10, by, btnW, btnH, canNext() ? "NEXT >" : "", themeColor, effective,
                HudAnimUtil.easeOutCubic(nextHoverAnim), hit(mouseX, mouseY, pnlX + 18 + btnW + 10, by, btnW, btnH) && canNext());

        String pageStr = "PAGE " + (currentPage + 1) + " / " + guide.getPageCount();
        g.drawString(font, pageStr, pnlX + pnlW - 18 - font.width(pageStr), by + 4, HudAnimUtil.withAlpha(0x888888, alpha), false);

        closeHoverAnim = HudAnimUtil.step(closeHoverAnim, hit(mouseX, mouseY, pnlX + pnlW - 28, 14, 18, 18) ? 1f : 0f, 10f, dt);
        int closeColor = HudAnimUtil.withAlpha(themeColor, (int) (alpha * (0.6f + 0.4f * HudAnimUtil.easeOutCubic(closeHoverAnim))));
        g.drawString(font, "\u2715", pnlX + pnlW - 24, 16, closeColor, false);
    }

    private void nextPage() { if (canNext()) { currentPage++; onPageChange(); } }
    private void previousPage() { if (canPrev()) { currentPage--; onPageChange(); } }
    private boolean canPrev() { return currentPage > 0; }
    private boolean canNext() { return guide != null && currentPage < guide.getPageCount() - 1; }

    private void onPageChange() {
        resetDesc();
        descCache.clear();
        pageTransitionAnim = 0.0F;
        refreshMediaBinding();
    }

    private void resetDesc() { descScroll = 0.0; descTargetScroll = 0.0; }

    private void refreshMediaBinding() {
        GuideMediaDefinition m = currentMedia();
        if (m.getType() == GuideMediaType.PONDER && m.getSceneId() != null)
            ponderPanel.bind(m.getSceneId(), themeColor, m.isAutoplay());
        else ponderPanel.unbind();
    }

    private GuidePageDefinition page() { return guide.getPage(currentPage); }
    private GuideMediaDefinition currentMedia() { return page().getMedia(); }

    private List<FormattedCharSequence> lines(int width) {
        return descCache.computeIfAbsent(width, w -> font.split(page().getDescriptionText().resolve(null, null), w));
    }

    private int panelW() { return GuideScreenLayout.panelWidth(width); }
    private int clampPage(int p) { return Math.max(0, Math.min(p, guide.getPageCount() - 1)); }
    private boolean hit(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx <= x + w && my >= y && my <= y + h; }
}
