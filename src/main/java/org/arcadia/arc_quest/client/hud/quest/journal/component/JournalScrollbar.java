package org.arcadia.arc_quest.client.hud.quest.journal.component;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;
import org.arcadia.arc_quest.client.hud.component.HudRect;

public final class JournalScrollbar {
    private final int trackWidth;
    private final int minimumThumbHeight;
    private boolean dragging;
    private double dragOffset;

    public JournalScrollbar() {
        this(4, 16);
    }

    public JournalScrollbar(int trackWidth, int minimumThumbHeight) {
        this.trackWidth = Math.max(1, trackWidth);
        this.minimumThumbHeight = Math.max(1, minimumThumbHeight);
    }

    public void render(GuiGraphics graphics, HudRect track, int contentHeight,
                       double scrollOffset, float alphaFactor, int thumbColor) {
        render(graphics, track, contentHeight, scrollOffset, alphaFactor, thumbColor, 120, 180);
    }

    public void render(GuiGraphics graphics, HudRect track, int contentHeight,
                       double scrollOffset, float alphaFactor, int thumbColor,
                       int idleThumbAlpha, int draggingThumbAlpha) {
        ScrollMetrics metrics = metrics(track, contentHeight, scrollOffset);
        if (!metrics.scrollable()) return;
        graphics.fill(track.x(), track.y(), track.x() + trackWidth, track.bottom(),
                HudAnimUtil.withAlpha(0x000000, (int) (40 * alphaFactor)));
        int thumbAlpha = (int) ((dragging ? draggingThumbAlpha : idleThumbAlpha) * alphaFactor);
        graphics.fill(track.x(), metrics.thumbY(), track.x() + trackWidth,
                metrics.thumbY() + metrics.thumbHeight(), HudAnimUtil.withAlpha(thumbColor, thumbAlpha));
    }

    public ScrollInteraction mouseClicked(double mouseX, double mouseY, HudRect track,
                                          int hitWidth, int contentHeight, double scrollOffset) {
        ScrollMetrics metrics = metrics(track, contentHeight, scrollOffset);
        if (!metrics.scrollable()) return ScrollInteraction.ignored(scrollOffset);
        int interactionWidth = Math.max(trackWidth, hitWidth);
        if (mouseX < track.x() || mouseX > track.x() + interactionWidth
                || mouseY < track.y() || mouseY > track.bottom()) {
            return ScrollInteraction.ignored(scrollOffset);
        }
        dragging = true;
        if (mouseY >= metrics.thumbY() && mouseY <= metrics.thumbY() + metrics.thumbHeight()) {
            dragOffset = mouseY - metrics.thumbY();
            return ScrollInteraction.consumed(scrollOffset);
        }
        dragOffset = metrics.thumbHeight() / 2.0;
        return ScrollInteraction.consumed(scrollFromMouse(mouseY, track, contentHeight));
    }

    public void requestPointer(double mouseX, double mouseY, HudRect track,
                               int hitWidth, int contentHeight, double scrollOffset) {
        ScrollMetrics metrics = metrics(track, contentHeight, scrollOffset);
        if (!metrics.scrollable()) return;
        int interactionWidth = Math.max(trackWidth, hitWidth);
        HudCursorManager.requestPointer(mouseX, mouseY,
                track.x(), track.y(), interactionWidth, track.height());
    }

    public ScrollInteraction mouseDragged(double mouseY, HudRect track, int contentHeight,
                                          double currentScrollOffset) {
        if (!dragging) return ScrollInteraction.ignored(currentScrollOffset);
        return ScrollInteraction.consumed(scrollFromMouse(mouseY, track, contentHeight));
    }

    public boolean mouseReleased(int button) {
        boolean wasDragging = dragging;
        if (button == 0) dragging = false;
        return wasDragging;
    }

    public boolean isDragging() {
        return dragging;
    }

    private double scrollFromMouse(double mouseY, HudRect track, int contentHeight) {
        ScrollMetrics metrics = metrics(track, contentHeight, 0);
        int maxScroll = Math.max(0, contentHeight - track.height());
        int travel = track.height() - metrics.thumbHeight();
        if (maxScroll <= 0 || travel <= 0) return 0;
        double progress = Math.max(0.0, Math.min(1.0,
                (mouseY - track.y() - dragOffset) / travel));
        return progress * maxScroll;
    }

    private ScrollMetrics metrics(HudRect track, int contentHeight, double scrollOffset) {
        int maxScroll = Math.max(0, contentHeight - track.height());
        if (maxScroll <= 0 || contentHeight <= 0 || track.height() <= 0) {
            return new ScrollMetrics(false, track.y(), track.height());
        }
        int thumbHeight = Math.max(minimumThumbHeight,
                (int) (((float) track.height() / contentHeight) * track.height()));
        thumbHeight = Math.min(track.height(), thumbHeight);
        int travel = Math.max(0, track.height() - thumbHeight);
        int thumbY = track.y() + (int) ((scrollOffset / maxScroll) * travel);
        return new ScrollMetrics(true, thumbY, thumbHeight);
    }

    private record ScrollMetrics(boolean scrollable, int thumbY, int thumbHeight) {
    }

    public record ScrollInteraction(boolean consumed, double scrollOffset) {
        private static ScrollInteraction ignored(double scrollOffset) {
            return new ScrollInteraction(false, scrollOffset);
        }

        private static ScrollInteraction consumed(double scrollOffset) {
            return new ScrollInteraction(true, scrollOffset);
        }
    }
}
