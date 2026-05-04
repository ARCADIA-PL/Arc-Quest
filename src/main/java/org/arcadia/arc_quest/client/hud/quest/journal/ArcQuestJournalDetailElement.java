package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.mutil.theme.ArcDrawUtil;

public class ArcQuestJournalDetailElement extends ArcGuiElement {
    private final QuestJournalScreen screen;

    public ArcQuestJournalDetailElement(QuestJournalScreen screen) {
        super(0, 0, 0, 0);
        this.screen = screen;
    }

    @Override
    protected void update(ArcGuiContext context, int refX, int refY) {
        this.x = JournalConstants.LIST_MARGIN + JournalConstants.LIST_WIDTH + JournalConstants.DETAIL_MARGIN + (int) screen.getJournalSlideOffset();
        this.y = 38 + JournalConstants.TAB_HEIGHT + 6;
        this.width = screen.getScaledWidth() - x - JournalConstants.DETAIL_MARGIN;
        this.height = screen.getScaledHeight() - 20 - y;
    }

    @Override
    public void draw(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        float effectiveAlpha = screen.getEffectiveAlpha() * inheritedOpacity;
        ArcDrawUtil.drawFrame(graphics, x, y, width, height, ArcDrawUtil.withAlpha(0x000000, (int) (0x44 * effectiveAlpha)), ArcDrawUtil.withAlpha(screen.getCurrentThemeColor(), (int) (0x55 * effectiveAlpha)));
        screen.getDetailPanel().render(graphics, x, y, width, height, context.mouseX(), context.mouseY(), screen.getThemeColor(), screen.getDt());
    }

    @Override
    public boolean onMouseClick(double mouseX, double mouseY, int button) {
        return button == 0 && !screen.isJournalClosing() && screen.getDetailPanel().mouseClicked(mouseX, mouseY, x, y, width, height);
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double distance) {
        return !screen.isJournalClosing() && screen.getDetailPanel().mouseScrolled(mouseX, mouseY, distance, x, y, width, height);
    }

    public boolean onMouseDragged(double mouseX, double mouseY) {
        return screen.getDetailPanel().mouseDragged(mouseX, mouseY, y, height);
    }

    @Override
    public void onMouseRelease(double mouseX, double mouseY, int button) {
        screen.getDetailPanel().mouseReleased(button);
    }
}
