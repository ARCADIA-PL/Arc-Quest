package org.arcadia.arc_quest.client.hud.guide;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
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
import org.joml.Matrix4f;

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
        pageTransitionAnim += (1.0F - pageTransitionAnim) * Math.min(1.0f, dt * GuideConstants.PAGE_TRANSITION_SPEED);
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
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (closing) return true;
        if (keyCode == 256 || (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode))) {
            onClose();
            return true;
        }
        if (keyCode == 263 || keyCode == 65) { previousPage(); return true; }
        if (keyCode == 262 || keyCode == 68) { nextPage(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (closing) return true;
        GuideScreenLayout.SidebarLayout l = layout();
        int descW = l.baseW() - 24;
        int descH = l.h() - 240;
        int contentH = lines(descW).size() * GuideScreenLayout.TEXT_LINE_H;
        if (hit(mouseX, mouseY, 12, 180, descW, descH)) {
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
        GuideScreenLayout.SidebarLayout l = layout();
        int by = l.h() - 32;
        if (hit(mouseX, mouseY, 12, by, 16, 16) && canPrev()) { previousPage(); return true; }
        if (hit(mouseX, mouseY, 32, by, 16, 16) && canNext()) { nextPage(); return true; }
        if (hit(mouseX, mouseY, l.baseW() - 28, 10, 18, 18)) { onClose(); return true; }
        if (currentMedia().getType() == GuideMediaType.PONDER && ponderPanel.mouseClicked(mouseX, mouseY, button, 12, 54, l.baseW() - 24, 110))
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

        GuideScreenLayout.SidebarLayout l = layout();
        drawSlantedSidebar(g, l, alpha);

        int tx = 14;
        GuideNavigationControls.drawScaledText(this, g, tx, 18, GuideConstants.CAPTION_SCALE,
                guideId.toString().toUpperCase(), withAlpha(GuideConstants.MUTED, alpha));

        String titleText = guide.getTitle().getString();
        int titleMaxW = l.baseW() - 28;
        int titleTextW = (int) (font.width(titleText) * GuideConstants.HEADER_SCALE);
        String displayTitle = titleText;
        if (titleTextW > titleMaxW) {
            while (font.width(displayTitle + "...") * GuideConstants.HEADER_SCALE > titleMaxW && displayTitle.length() > 1)
                displayTitle = displayTitle.substring(0, displayTitle.length() - 1);
            displayTitle += "...";
        }
        GuideNavigationControls.drawScaledText(this, g, tx, 28, GuideConstants.HEADER_SCALE, displayTitle, withAlpha(GuideConstants.TEXT, alpha));

        g.fill(tx, 42, l.baseW() - 12, 43, withAlpha(themeColor, (int) (alpha * 0.6F)));
        g.fill(tx, 43, l.baseW() - 32, 44, withAlpha(themeColor, (int) (alpha * 0.2F)));

        int mediaW = l.baseW() - 24;
        int mediaH = 110;
        GuideMediaRenderer.drawMedia(this, g, 12, 54, mediaW, mediaH, currentMedia(), ponderPanel, mouseX, mouseY, partialTick,
                alpha, GuideConstants.SEC, GuideConstants.MUTED, GuideConstants.SUB, themeColor);

        float pageAlpha = (int) (255 * effective * pageTransitionAnim);
        int descY = 174;
        int descH = l.h() - 216;
        List<FormattedCharSequence> wrapped = lines(mediaW);
        int contentH = wrapped.size() * GuideScreenLayout.TEXT_LINE_H;
        int max = Math.max(0, contentH - descH);
        descTargetScroll = Math.max(0, Math.min(max, descTargetScroll));
        descScroll = Math.max(0, Math.min(max, descScroll));

        g.enableScissor(10, descY - 2, l.baseW() - 10, descY + descH + 2);
        int sy = descY - (int) Math.round(descScroll);
        for (int i = 0; i < wrapped.size(); i++) {
            g.drawString(font, wrapped.get(i), tx, sy + i * GuideScreenLayout.TEXT_LINE_H, withAlpha(GuideConstants.TEXT, (int) (alpha * pageTransitionAnim)));
        }
        g.disableScissor();

        if (max > 0) {
            int rx = l.baseW() - 8;
            g.fill(rx, descY, rx + 2, descY + descH, withAlpha(0x111823, alpha));
            int th = Math.max(12, (int) (descH * (descH / (float) contentH)));
            int travel = Math.max(0, descH - th);
            int ty = descY + (int) (travel * (descScroll / (double) max));
            g.fill(rx, ty, rx + 2, ty + th, withAlpha(themeColor, alpha));
        }

        int by = l.h() - 32;
        g.fill(12, by - 8, l.baseW() - 12, by - 7, withAlpha(themeColor, (int) (alpha * 0.3F)));

        boolean prevHover = hit(mouseX, mouseY, 12, by, 16, 16);
        boolean nextHover = hit(mouseX, mouseY, 32, by, 16, 16);
        boolean closeHover = hit(mouseX, mouseY, l.baseW() - 28, 10, 18, 18);
        GuideNavigationControls.drawArrowButton(g, 12, by, 16, true, canPrev(), prevHover, themeColor, alpha);
        GuideNavigationControls.drawArrowButton(g, 32, by, 16, false, canNext(), nextHover, themeColor, alpha);

        String guideAreaText = "SYS.LOC // PAGE " + (currentPage + 1) + " OF " + guide.getPageCount();
        GuideNavigationControls.drawScaledText(this, g, 56, by + 4, GuideConstants.CAPTION_SCALE, guideAreaText,
                withAlpha(GuideConstants.TEXT, alpha));

        int closeColor = closeHover ? withAlpha(0xFF5555, alpha) : withAlpha(GuideConstants.MUTED, alpha);
        GuideNavigationControls.drawScaledText(this, g, l.baseW() - 26, 12, GuideConstants.SMALL_SCALE, "\u2715", closeColor);
    }

    private void drawSlantedSidebar(GuiGraphics g, GuideScreenLayout.SidebarLayout l, int alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        Matrix4f matrix = g.pose().last().pose();

        int bgColor = GuideConstants.BG;
        float f = (float) (bgColor >> 24 & 255) / 255.0F * (alpha / 255.0F);
        float r = (float) (bgColor >> 16 & 255) / 255.0F;
        float gr = (float) (bgColor >> 8 & 255) / 255.0F;
        float b = (float) (bgColor & 255) / 255.0F;

        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, 0, 0, 0).color(r, gr, b, f).endVertex();
        buffer.vertex(matrix, l.baseW() + l.slant(), 0, 0).color(r, gr, b, f).endVertex();
        buffer.vertex(matrix, l.baseW(), l.h(), 0).color(r, gr, b, f).endVertex();
        buffer.vertex(matrix, 0, l.h(), 0).color(r, gr, b, f).endVertex();
        tesselator.end();

        float br = (float) (themeColor >> 16 & 255) / 255.0F;
        float bg = (float) (themeColor >> 8 & 255) / 255.0F;
        float bb = (float) (themeColor & 255) / 255.0F;
        float ba = alpha / 255.0F;

        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, l.baseW() + l.slant(), 0, 0).color(br, bg, bb, ba).endVertex();
        buffer.vertex(matrix, l.baseW(), l.h(), 0).color(br, bg, bb, ba).endVertex();
        buffer.vertex(matrix, l.baseW() + l.slant() + 2, 0, 0).color(br, bg, bb, ba * 0.3F).endVertex();
        buffer.vertex(matrix, l.baseW() + 2, l.h(), 0).color(br, bg, bb, ba * 0.3F).endVertex();
        buffer.vertex(matrix, 0, 0, 0).color(br, bg, bb, ba * 0.5F).endVertex();
        buffer.vertex(matrix, l.baseW() + l.slant(), 0, 0).color(br, bg, bb, ba * 0.5F).endVertex();
        tesselator.end();

        RenderSystem.disableBlend();
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

    private int clampPage(int p) { return Math.max(0, Math.min(p, guide.getPageCount() - 1)); }
    private boolean hit(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx <= x + w && my >= y && my <= y + h; }
    private GuideScreenLayout.SidebarLayout layout() { return GuideScreenLayout.computeSidebar(width, height); }
    private int withAlpha(int c, int a) { return ((a & 0xFF) << 24) | (c & 0x00FFFFFF); }
}
