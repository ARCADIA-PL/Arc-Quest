package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;

public class ArcQuestJournalTabElement extends ArcGuiElement {
    private final QuestJournalScreen screen;

    public ArcQuestJournalTabElement(QuestJournalScreen screen) {
        super(0, 38, JournalConstants.LIST_WIDTH, JournalConstants.TAB_HEIGHT);
        this.screen = screen;
    }

    @Override
    protected void update(ArcGuiContext context, int refX, int refY) {
        this.x = JournalConstants.LIST_MARGIN - (int) screen.getJournalSlideOffset();
    }

    @Override
    public void draw(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        int safeAlpha = (int) (255 * screen.getEffectiveAlpha() * inheritedOpacity);
        screen.getTabPanel().render(graphics, context.mouseX(), context.mouseY(), safeAlpha, screen.getJournalSlideOffset(), screen.getThemeColor(), screen.getDt());
    }

    @Override
    public boolean onMouseClick(double mouseX, double mouseY, int button) {
        return button == 0 && !screen.isJournalClosing() && screen.getTabPanel().mouseClicked(mouseX, mouseY, x);
    }
}
