package org.arcadia.arc_quest.client.hud.guide;

import org.arcadia.arc_quest.client.hud.style.HudStyleRenderer;
import org.arcadia.arc_quest.client.hud.style.HudDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiScreenIngredients;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.HudText;
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;
import org.arcadia.arc_quest.client.hud.ponder.EmbeddedPonderScenePanel;
import org.arcadia.arc_quest.config.ArcQuestTextConfig;
import org.arcadia.arc_quest.guide.api.*;
import org.arcadia.arc_quest.guide.network.ClientGuideCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** One responsive reader for automatic guide popups and manually opened guides. */
final class GuideReaderPanel {
    enum Action { NONE, CLOSE, PREVIOUS, NEXT, SETTINGS }
    private record Line(FormattedCharSequence text, int y, float scale, int color) {}
    private final List<Line> lines = new ArrayList<>();
    private GuideReaderLayout layout;
    private List<FormattedCharSequence> titleLines = List.of();
    private Component cachedDescription, cachedSummary, cachedTitle;
    private String cachedDescriptionText, cachedSummaryText, cachedTitleText;
    private Language cachedLanguage;
    private Font cachedFont;
    private int cachedWidth = -1, contentHeight, maxScroll;
    private float cachedScale;
    private double scroll, targetScroll, dragOffset;
    private boolean dragging;

    void reset() {
        scroll = targetScroll = 0;
        dragging = false;
        cachedWidth = -1;
    }

    void release() { dragging = false; }

    void render(Screen owner, GuiGraphics g, GuideDefinition guide, int page,
                EmbeddedPonderScenePanel ponder, int width, int height,
                int mouseX, int mouseY, float partialTick, float opacity, float delta,
                boolean closing, boolean settingsEnabled) {
        Font font = Minecraft.getInstance().font;
        float scale = (float) ArcQuestTextConfig.guideScale();
        var frame = GuideReaderLayout.frame(width, height);
        int pad = frame.width() < 240 ? 12 : 16;
        Component title = GuideClientTextResolver.resolve(guide.getTitle());
        titleLines = font.split(title, Math.max(1, frame.width() - pad * 2 - 26));
        GuideMediaDefinition media = guide.getPage(page).getMedia();
        // An explicit page image/scene always takes priority over the introductory icon.
        boolean iconOnly = page == 0 && guide.getVisualConfig().shouldRenderLargeIconOnIntro()
                && media.getType() == GuideMediaType.NONE;
        layout = GuideReaderLayout.measure(width, height, titleLines.size(), font.lineHeight,
                iconOnly || media.getType() != GuideMediaType.NONE, iconOnly, scale);
        var body = layout.body();
        rebuild(font, guide, page, title, Math.max(1, body.width() - 12), scale);
        targetScroll = Math.max(0, Math.min(maxScroll, targetScroll));
        scroll = Math.max(0, Math.min(maxScroll, scroll + (targetScroll - scroll) * Math.min(1, delta * 18)));
        int alpha = Math.max(0, Math.min(255, Math.round(opacity * 255)));
        int theme = guide.getCategory().getThemeColor();
        var panel = layout.panel();

        // Fade in place: hitboxes and clipping never diverge during the transition.
        HudDraw.glassFrame(g, panel.x(), panel.y(), panel.width(), panel.height(),
                color(0x111820, Math.round(opacity * 232)), color(0x536273, alpha * 2 / 3), opacity, theme);
        HudDraw.fill(g, panel.x(), panel.y(), panel.x() + 3, panel.bottom(), color(theme, alpha));
        HudDraw.drawString(g, font, HudText.of("guide.datablock"), layout.title().x(), panel.y() + 10,
                color(theme, alpha), false);
        var titleBox = layout.title();
        for (int i = 0; i < Math.min(2, titleLines.size()); i++)
            HudDraw.drawString(g, font, titleLines.get(i), titleBox.x(), titleBox.y() + i * (font.lineHeight + 3),
                    color(0xF4F7FB, alpha), false);
        HudDraw.fill(g, titleBox.x(), layout.dividerY(), panel.right() - pad, layout.dividerY() + 1,
                color(theme, alpha / 2));

        var mediaBox = layout.media();
        if (mediaBox.height() > 0) {
            if (iconOnly) {
                int size = Math.min(32, mediaBox.height());
                g.pose().pushPose();
                try {
                    g.pose().translate(mediaBox.x() + (mediaBox.width() - size) / 2f,
                            mediaBox.y() + (mediaBox.height() - size) / 2f, 0);
                    g.pose().scale(size / 16f, size / 16f, 1);
                    GuideIconRenderer.draw(g, guide.getVisualConfig(), 0, 0);
                    JeiScreenIngredients.guideIcon(owner, g, guide, page, guide.getVisualConfig().getIcon(), 0, 0, 16, 16);
                } finally { g.pose().popPose(); }
            } else GuideMediaRenderer.drawMedia(owner, g, mediaBox.x(), mediaBox.y(), mediaBox.width(),
                    mediaBox.height(), media, ponder, mouseX, mouseY, partialTick, alpha, theme);
        }

        JeiScreenIngredients.enableScissor(owner, g, body.x(), body.y(), body.right() - 10, body.bottom());
        try {
            for (Line line : lines) {
                float y = body.y() + line.y - (float) scroll;
                if (y + font.lineHeight * line.scale < body.y() || y >= body.bottom()) continue;
                g.pose().pushPose();
                try {
                    g.pose().translate(body.x(), y, 0);
                    g.pose().scale(line.scale, line.scale, 1);
                    HudDraw.drawString(g, font, line.text, 0, 0, color(line.color, alpha), false);
                } finally { g.pose().popPose(); }
            }
        } finally { JeiScreenIngredients.disableScissor(owner, g); }

        if (maxScroll > 0) {
            int thumb = GuideReaderLayout.thumbHeight(body.height(), contentHeight);
            int thumbY = body.y() + (int) Math.round((body.height() - thumb) * scroll / maxScroll);
            HudDraw.fill(g, body.right() - 4, body.y(), body.right() - 1, body.bottom(), color(0xFFFFFF, alpha / 9));
            HudDraw.fill(g, body.right() - 4, thumbY, body.right() - 1, thumbY + thumb, color(dragging ? 0xFFFFFF : theme, alpha));
        }

        HudDraw.fill(g, titleBox.x(), layout.footerY(), panel.right() - pad, layout.footerY() + 1, color(0xFFFFFF, alpha / 10));
        Component pageText = HudText.of("guide.page", page + 1, guide.getPageCount());
        HudDraw.drawString(g, font, pageText, panel.x() + (panel.width() - font.width(pageText)) / 2,
                layout.footerY() + 9, color(0xB8C4D2, alpha), false);
        if (settingsEnabled) button(g, font, layout.settings(), "Aa", mouseX, mouseY, theme, alpha, closing);
        if (page > 0) button(g, font, layout.previous(), "<", mouseX, mouseY, theme, alpha, closing);
        if (page + 1 < guide.getPageCount()) {
            button(g, font, layout.next(), ">", mouseX, mouseY, theme, alpha, closing);
            if (!ClientGuideCache.INSTANCE.isSeen(guide.getId())) HudRenderUtil.drawBreathingRedDot(g,
                    layout.next().right() - 2, layout.next().y() + 2, opacity);
        }
        var close = layout.close();
        boolean hover = !closing && close.contains(mouseX, mouseY);
        if (hover) HudDraw.fill(g, close.x(), close.y(), close.right(), close.bottom(), color(theme, alpha / 5));
        // Draw the cross geometrically, independent of missing font glyphs.
        for (int i = 0; i < 7; i++) {
            HudDraw.fill(g, close.x() + 8 + i, close.y() + 8 + i, close.x() + 9 + i, close.y() + 9 + i, color(0xE3EAF3, alpha));
            HudDraw.fill(g, close.x() + 14 - i, close.y() + 8 + i, close.x() + 15 - i, close.y() + 9 + i, color(0xE3EAF3, alpha));
        }
        HudCursorManager.requestPointer(hover);
        if (!closing && settingsEnabled && layout.settings().contains(mouseX, mouseY))
            g.renderTooltip(font, Component.translatable("gui.arc_quest.text_config.button"), mouseX, mouseY);
    }

    private void rebuild(Font font, GuideDefinition guide, int page, Component title, int width, float scale) {
        Component description = GuideClientTextResolver.resolve(guide.getPage(page).getDescriptionText().resolve(null, null));
        Component summary = GuideClientTextResolver.resolve(guide.getSummary());
        String descriptionText = description.getString(), summaryText = summary.getString(), titleText = title.getString();
        boolean changed = width != cachedWidth || scale != cachedScale || cachedFont != font
                || cachedLanguage != Language.getInstance() || !Objects.equals(description, cachedDescription)
                || !Objects.equals(summary, cachedSummary) || !Objects.equals(title, cachedTitle)
                || !Objects.equals(descriptionText, cachedDescriptionText)
                || !Objects.equals(summaryText, cachedSummaryText) || !Objects.equals(titleText, cachedTitleText);
        double fraction = maxScroll == 0 ? 0 : targetScroll / maxScroll;
        if (changed) {
            lines.clear();
            contentHeight = 0;
            // Long headings and summaries remain fully readable in the scrolling document.
            if (titleLines.size() > 2) append(font, title, width, scale, 0xF4F7FB, true);
            if (!summary.getString().isBlank()) append(font, summary, width, scale * .9f, 0xACBCCC, true);
            append(font, description, width, scale, 0xE2E8F0, false);
            cachedWidth = width;
            cachedScale = scale;
            cachedFont = font;
            cachedLanguage = Language.getInstance();
            cachedDescription = description.copy();
            cachedSummary = summary.copy();
            cachedTitle = title.copy();
            // Keybind components retain their identity when the actual binding changes.
            cachedDescriptionText = descriptionText;
            cachedSummaryText = summaryText;
            cachedTitleText = titleText;
        }
        int nextMax = Math.max(0, contentHeight - layout.body().height());
        if (changed || nextMax != maxScroll) {
            // Preserve the reading position when resizing or changing the text size.
            scroll = targetScroll = fraction * nextMax;
            dragging = false;
        }
        maxScroll = nextMax;
    }

    private void append(Font font, Component text, int width, float scale, int color, boolean paragraphGap) {
        int advance = GuideReaderLayout.lineAdvance(font.lineHeight, scale);
        for (FormattedCharSequence line : font.split(text, Math.max(1, (int) Math.floor(width / scale)))) {
            lines.add(new Line(line, contentHeight, scale, color));
            contentHeight += advance;
        }
        if (paragraphGap) contentHeight += Math.max(6, Math.round(scale * 8));
    }

    private static void button(GuiGraphics g, Font font, GuideReaderLayout.Box b, String label,
                               int mx, int my, int theme, int alpha, boolean closing) {
        boolean hover = !closing && b.contains(mx, my);
        if (HudDraw.isStyled()) {
            HudStyleRenderer.labeledButton(g, font, Component.literal(label), b.x(), b.y(), b.width(), b.height(),
                    HudStyleRenderer.Action.SECONDARY, hover, !closing, alpha / 255f, theme);
            HudCursorManager.requestPointer(hover);
            return;
        }
        if (hover) HudDraw.fill(g, b.x(), b.y(), b.right(), b.bottom(), color(theme, alpha / 5));
        HudDraw.drawString(g, font, label, b.x() + (b.width() - font.width(label)) / 2,
                b.y() + (b.height() - font.lineHeight) / 2, color(hover ? 0xFFFFFF : 0xB8C4D2, alpha), false);
        HudCursorManager.requestPointer(hover);
    }

    Action click(double x, double y, int page, int pages, boolean settingsEnabled) {
        if (layout == null) return Action.NONE;
        if (!layout.panel().contains(x, y) || layout.close().contains(x, y)) return Action.CLOSE;
        if (page > 0 && layout.previous().contains(x, y)) return Action.PREVIOUS;
        if (page + 1 < pages && layout.next().contains(x, y)) return Action.NEXT;
        if (settingsEnabled && layout.settings().contains(x, y)) return Action.SETTINGS;
        var body = layout.body();
        if (maxScroll > 0 && x >= body.right() - 10 && x < body.right() && y >= body.y() && y < body.bottom()) {
            int thumb = GuideReaderLayout.thumbHeight(body.height(), contentHeight);
            double top = body.y() + (body.height() - thumb) * scroll / maxScroll;
            dragOffset = y >= top && y < top + thumb ? y - top : thumb / 2.0;
            dragging = true;
            drag(y);
        }
        return Action.NONE;
    }

    void drag(double y) {
        if (!dragging || layout == null) return;
        var body = layout.body();
        int travel = body.height() - GuideReaderLayout.thumbHeight(body.height(), contentHeight);
        if (travel <= 0) return;
        scroll = targetScroll = Math.max(0, Math.min(1, (y - body.y() - dragOffset) / travel)) * maxScroll;
    }

    void wheel(double delta) { targetScroll = Math.max(0, Math.min(maxScroll, targetScroll - delta * 32 * cachedScale)); }
    boolean scrollKey(int key) {
        if (layout == null) return false;
        if (key == 268) targetScroll = 0; // Home
        else if (key == 269) targetScroll = maxScroll; // End
        else if (key == 266 || key == 267) targetScroll = Math.max(0,
                Math.min(maxScroll, targetScroll + (key == 267 ? 1 : -1) * layout.body().height() * .85));
        else if (key == 264 || key == 265) wheel(key == 264 ? -1 : 1);
        else return false;
        return true;
    }

    boolean mediaClick(EmbeddedPonderScenePanel ponder, double x, double y, int button) {
        if (layout == null || layout.media().height() == 0) return false;
        var b = layout.media();
        return ponder.mouseClicked(x, y, button, b.x(), b.y(), b.width(), b.height());
    }

    private static int color(int rgb, int alpha) { return HudAnimUtil.withAlpha(rgb, alpha); }
}
