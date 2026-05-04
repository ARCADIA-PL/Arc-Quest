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
}
