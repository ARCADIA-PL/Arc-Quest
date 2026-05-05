package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.mutil.theme.ArcDrawUtil;

public class ArcQuestJournalDetailScrollbarElement extends ArcGuiElement {
    private final QuestJournalScreen screen;
    private double scrollOffset = 0;
    private double targetScroll = 0;
    private double dragYOffset = 0;
    private boolean dragging = false;

    public ArcQuestJournalDetailScrollbarElement(QuestJournalScreen screen) {
        super(0, 0, 0, 0);
        this.screen = screen;
    }

    public double getScrollOffset() {
        return scrollOffset;
    }

    public void resetState() {
        scrollOffset = 0;
        targetScroll = 0;
        dragYOffset = 0;
        dragging = false;
    }

    public void tick(int scrollAreaH, int contentHeight, float dt) {
        clamp(scrollAreaH, contentHeight);
        scrollOffset += Math.abs(targetScroll - scrollOffset) > 0.5 ? (targetScroll - scrollOffset) * Math.min(1.0, dt * 14.0) : (targetScroll - scrollOffset);
    }

    public void render(GuiGraphics graphics, int x, int y, int viewHeight, int contentHeight) {
        int maxScroll = Math.max(0, contentHeight - viewHeight);
        if (maxScroll <= 0) return;
        int thumbH = Math.max(16, (int) (((float) viewHeight / contentHeight) * viewHeight));
        int thumbY = y + (int) ((scrollOffset / maxScroll) * (viewHeight - thumbH));
        graphics.fill(x, y, x + 4, y + viewHeight, ArcDrawUtil.withAlpha(0x000000, (int) (40 * screen.getEffectiveAlpha())));
        graphics.fill(x, thumbY, x + 4, thumbY + thumbH, ArcDrawUtil.withAlpha(0xFFFFFF, (int) ((dragging ? 180 : 120) * screen.getEffectiveAlpha())));
    }

    public boolean mouseClicked(double mouseX, double mouseY, int x, int y, int width, int scrollAreaH, int contentHeight) {
        int maxScroll = Math.max(0, contentHeight - scrollAreaH);
        if (maxScroll <= 0 || mouseX < x + width - 6 || mouseX > x + width || mouseY < y || mouseY > y + scrollAreaH) return false;
        dragging = true;
        int thumbH = Math.max(16, (int) (((float) scrollAreaH / contentHeight) * scrollAreaH));
        int thumbY = y + (int) ((scrollOffset / maxScroll) * (scrollAreaH - thumbH));
        if (mouseY >= thumbY && mouseY <= thumbY + thumbH) {
            dragYOffset = mouseY - thumbY;
        } else {
            dragYOffset = thumbH / 2.0;
            updateFromMouse(mouseY, y, scrollAreaH, contentHeight);
        }
        return true;
    }

    public boolean mouseDragged(double mouseY, int y, int scrollAreaH, int contentHeight) {
        if (!dragging) return false;
        updateFromMouse(mouseY, y, scrollAreaH, contentHeight);
        return true;
    }

    public boolean mouseReleased(int button) {
        boolean wasDragging = dragging;
        if (button == 0) dragging = false;
        return wasDragging;
    }

    public void mouseScrolled(double delta, int scrollAreaH, int contentHeight) {
        targetScroll -= delta * 25.0;
        clamp(scrollAreaH, contentHeight);
    }

    private void updateFromMouse(double mouseY, int y, int viewHeight, int contentHeight) {
        int maxScroll = Math.max(0, contentHeight - viewHeight);
        if (maxScroll <= 0) return;
        int thumbH = Math.max(16, (int) (((float) viewHeight / contentHeight) * viewHeight));
        targetScroll = Math.max(0.0, Math.min(1.0, (mouseY - y - dragYOffset) / (viewHeight - thumbH))) * maxScroll;
    }

    private void clamp(int scrollAreaH, int contentHeight) {
        targetScroll = Math.max(0, Math.min(targetScroll, Math.max(0, contentHeight - scrollAreaH)));
    }
}
