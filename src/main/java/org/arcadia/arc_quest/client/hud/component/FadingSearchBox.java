package org.arcadia.arc_quest.client.hud.component;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Native text editing with the same alpha-aware text and caret drawing as the guide search. */
public final class FadingSearchBox extends EditBox {
    private final Font searchFont;
    private Component hint = Component.empty();
    private int textRgb = 0xE0E0E0, cursorRgb = 0xFFFFFF;
    private int selectionAnchor, displayStart;
    private long blinkAt;

    public FadingSearchBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
        searchFont = font;
        setBordered(false);
    }

    @Override public void setHint(Component hint) { super.setHint(hint); this.hint = hint; }
    @Override public void setTextColor(int color) { super.setTextColor(color); textRgb = color & 0xFFFFFF; }
    public void setCursorColor(int color) { cursorRgb = color & 0xFFFFFF; }

    @Override public void setCursorPosition(int position) {
        super.setCursorPosition(position);
        blinkAt = Util.getMillis();
    }

    @Override public void setHighlightPos(int position) {
        super.setHighlightPos(position);
        selectionAnchor = Math.max(0, Math.min(getValue().length(), position));
    }

    @Override public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (focused) blinkAt = Util.getMillis();
    }

    private String visibleText() {
        String value = getValue();
        int cursor = getCursorPosition();
        displayStart = Math.max(0, Math.min(displayStart, value.length()));
        if (cursor < displayStart) displayStart = cursor;
        int available = Math.max(1, getInnerWidth() - 1);
        String beforeCursor = value.substring(displayStart, cursor);
        if (searchFont.width(beforeCursor) > available) {
            displayStart = cursor - searchFont.plainSubstrByWidth(beforeCursor, available, true).length();
        }
        return searchFont.plainSubstrByWidth(value.substring(displayStart), available);
    }

    @Override public void onClick(double mouseX, double mouseY) {
        String visible = visibleText();
        int offset = Math.max(0, (int) (mouseX - getX()));
        moveCursorTo(displayStart + searchFont.plainSubstrByWidth(visible, offset).length());
    }

    @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        // Font interprets alpha 0..3 as unspecified and would draw fully opaque text.
        if (!isVisible() || a <= 3) return;
        String visible = visibleText();
        int cursor = getCursorPosition();
        int start = Math.max(0, Math.min(visible.length(), Math.min(cursor, selectionAnchor) - displayStart));
        int end = Math.max(0, Math.min(visible.length(), Math.max(cursor, selectionAnchor) - displayStart));
        if (end > start) {
            int left = searchFont.width(visible.substring(0, start));
            int right = searchFont.width(visible.substring(0, end));
            graphics.fill(getX() + left, getY() - 1, getX() + right, getY() + searchFont.lineHeight + 1,
                    (Math.round(a * .45f) << 24) | 0x668CB0);
        }
        if (getValue().isEmpty() && !isFocused()) {
            graphics.drawString(searchFont, hint, getX(), getY(), (a << 24) | textRgb, false);
        } else if (!visible.isEmpty()) {
            graphics.drawString(searchFont, visible, getX(), getY(), (a << 24) | textRgb, false);
        }
        if (isFocused() && (Util.getMillis() - blinkAt) % 1000 < 500) {
            int caret = Math.max(0, Math.min(visible.length(), cursor - displayStart));
            int cursorX = getX() + searchFont.width(visible.substring(0, caret));
            graphics.fill(cursorX, getY() - 1, cursorX + 1, getY() + searchFont.lineHeight + 1,
                    (a << 24) | cursorRgb);
        }
    }
}
