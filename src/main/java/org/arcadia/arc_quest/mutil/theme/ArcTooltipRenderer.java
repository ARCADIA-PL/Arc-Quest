package org.arcadia.arc_quest.mutil.theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.mutil.screen.ArcScissorUtil;

import java.util.List;

public final class ArcTooltipRenderer {
    public static final int DEFAULT_PADDING = 6;
    public static final int DEFAULT_EDGE_WIDTH = 3;
    public static final int DEFAULT_BG_ALPHA = 0xD0;
    public static final int DEFAULT_BORDER_ALPHA = 0x66;

    private ArcTooltipRenderer() {
    }

    public static void render(GuiGraphics graphics, Font font, List<Component> lines, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (lines == null || lines.isEmpty()) return;
        List<FormattedCharSequence> visualLines = lines.stream().map(Component::getVisualOrderText).toList();
        graphics.renderTooltip(font, visualLines, mouseX, mouseY);
    }

    public static CyberLayout measure(Font font, List<Component> lines) {
        int textMaxWidth = 0;
        if (lines != null) {
            for (Component line : lines) {
                int lineWidth = font.width(line);
                if (lineWidth > textMaxWidth) textMaxWidth = lineWidth;
            }
        }
        return new CyberLayout(lines == null ? List.of() : lines, textMaxWidth);
    }

    public static void renderCyber(GuiGraphics graphics, Font font, List<Component> lines, int mouseX, int mouseY, int screenWidth, int screenHeight, int themeColor) {
        renderCyber(graphics, font, measure(font, lines), mouseX, mouseY, screenWidth, screenHeight, themeColor, 6000);
    }

    public static void renderCyber(GuiGraphics graphics, Font font, CyberLayout layout, int mouseX, int mouseY, int screenWidth, int screenHeight, int themeColor, int z) {
        if (layout == null || layout.lines().isEmpty()) return;
        int padding = DEFAULT_PADDING;
        int edgeWidth = DEFAULT_EDGE_WIDTH;
        int drawWidth = layout.textMaxWidth() + padding * 2 + edgeWidth + 2;
        int drawHeight = layout.lines().size() * font.lineHeight + padding * 2;
        int drawX = mouseX + 12;
        int drawY = mouseY - 12;
        if (drawX + drawWidth > screenWidth) drawX = mouseX - drawWidth - 8;
        if (drawY + drawHeight > screenHeight) drawY = screenHeight - drawHeight - 2;
        if (drawY < 2) drawY = 2;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, z);
        drawCyberFrame(graphics, drawX, drawY, drawWidth, drawHeight, themeColor, 0xFF, DEFAULT_BG_ALPHA, DEFAULT_BORDER_ALPHA, edgeWidth);
        ArcScissorUtil.enableGui(graphics, drawX, drawY, drawX + drawWidth, drawY + drawHeight);
        int textX = drawX + edgeWidth + padding + 1;
        int textY = drawY + padding;
        for (Component line : layout.lines()) {
            graphics.drawString(font, line, textX, textY, ArcDrawUtil.withAlpha(0xFFFFFF, 0xFF), true);
            textY += font.lineHeight;
        }
        ArcScissorUtil.disable(graphics);
        graphics.pose().popPose();
    }

    public static CyberFrame beginMorphingCyberFrame(GuiGraphics graphics, int drawX, int drawY, int drawWidth, int drawHeight, float easeScale, int themeColor, int z) {
        if (easeScale < 0.01f) return CyberFrame.hidden();
        int baseAlpha = ArcDrawUtil.clampAlpha((int) (255 * easeScale));
        int bgAlpha = ArcDrawUtil.clampAlpha((int) (DEFAULT_BG_ALPHA * easeScale));
        int borderAlpha = ArcDrawUtil.clampAlpha((int) (DEFAULT_BORDER_ALPHA * easeScale));
        float centerX = drawX + drawWidth / 2f;
        float centerY = drawY + drawHeight / 2f;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, z);
        graphics.pose().translate(centerX, centerY, 0);
        graphics.pose().scale(easeScale, easeScale, 1f);
        graphics.pose().translate(-centerX, -centerY, 0);
        drawCyberFrame(graphics, drawX, drawY, drawWidth, drawHeight, themeColor, baseAlpha, bgAlpha, borderAlpha, DEFAULT_EDGE_WIDTH);
        ArcScissorUtil.enableScreenAware(graphics, drawX, drawY, drawX + drawWidth, drawY + drawHeight);
        return new CyberFrame(true, drawX + DEFAULT_EDGE_WIDTH + 10, drawY + 10, drawWidth - DEFAULT_EDGE_WIDTH - 20, drawHeight - 20, baseAlpha, centerX, centerY, easeScale);
    }

    public static void endMorphingCyberFrame(GuiGraphics graphics, CyberFrame frame) {
        if (frame == null || !frame.visible()) return;
        ArcScissorUtil.disable(graphics);
        graphics.pose().popPose();
    }

    public static void drawCyberFrame(GuiGraphics graphics, int x, int y, int width, int height, int themeColor, int edgeAlpha, int bgAlpha, int borderAlpha, int edgeWidth) {
        int safeEdgeWidth = Math.max(1, edgeWidth);
        graphics.fill(x + safeEdgeWidth, y, x + width, y + height, ArcDrawUtil.withAlpha(0x000000, bgAlpha));
        ArcPanelChrome.drawFastFrame(graphics, x + safeEdgeWidth, y, width - safeEdgeWidth, height, 1, ArcDrawUtil.withAlpha(0xCCCCCC, borderAlpha));
        ArcDrawUtil.drawCyberneticEdge(graphics, x, y, height, themeColor, edgeAlpha, safeEdgeWidth);
    }

    public record CyberLayout(List<Component> lines, int textMaxWidth) {
    }

    public record CyberFrame(boolean visible, int contentX, int contentY, int contentWidth, int contentHeight,
                             int alpha, float centerX, float centerY, float scale) {
        static CyberFrame hidden() {
            return new CyberFrame(false, 0, 0, 0, 0, 0, 0f, 0f, 0f);
        }
    }
}
