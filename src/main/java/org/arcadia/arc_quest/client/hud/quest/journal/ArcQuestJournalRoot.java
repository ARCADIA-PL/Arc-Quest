package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.client.Minecraft;
import org.arcadia.arc_quest.mutil.screen.ArcScreenRoot;

public class ArcQuestJournalRoot extends ArcScreenRoot {
    private final ArcQuestJournalElement journalElement;

    public ArcQuestJournalRoot(Minecraft minecraft, QuestJournalScreen screen) {
        super(minecraft);
        this.journalElement = new ArcQuestJournalElement(screen);
        addChild(journalElement);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return journalElement.onMouseDragged(
                Math.round((float) mouseX / getUiScale()),
                Math.round((float) mouseY / getUiScale()),
                button,
                dragX / getUiScale(),
                dragY / getUiScale()
        );
    }

    public void resetListState() {
        journalElement.resetListState();
    }

    public int renderRewards(net.minecraft.client.gui.GuiGraphics graphics, org.arcadia.arc_quest.quest.api.QuestDefinition def, String selectedPhaseId, int detailX, int scrollAreaY, int scrollAreaW, int scrollAreaH, int mouseX, int mouseY, int activeTheme, float detailAlpha, int safeAlpha, int localY, float dt) {
        return journalElement.renderRewards(graphics, def, selectedPhaseId, detailX, scrollAreaY, scrollAreaW, scrollAreaH, mouseX, mouseY, activeTheme, detailAlpha, safeAlpha, localY, dt);
    }

    public boolean mouseClickedRewards(double mouseX, double mouseY) {
        return journalElement.mouseClickedRewards(mouseX, mouseY);
    }

    public boolean mouseDraggedRewards(double mouseX, double mouseY) {
        return journalElement.mouseDraggedRewards(mouseX, mouseY);
    }

    public boolean mouseReleasedRewards(int button) {
        return journalElement.mouseReleasedRewards(button);
    }

    public boolean mouseScrolledRewards(double mouseX, double mouseY, double delta) {
        return journalElement.mouseScrolledRewards(mouseX, mouseY, delta);
    }
}
