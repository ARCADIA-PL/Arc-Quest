package org.arcadia.arc_quest.client.hud.quest.arcmutil.toast;

import net.minecraft.client.Minecraft;
import org.arcadia.arc_quest.client.hud.quest.toast.QuestToastManager;
import org.arcadia.arc_quest.mutil.core.ArcGuiTickContext;
import org.arcadia.arc_quest.mutil.overlay.ArcOverlayRoot;

import java.util.ArrayList;
import java.util.List;

public class ArcQuestToastOverlayRoot extends ArcOverlayRoot {
    private final List<ArcQuestToastElement> elements = new ArrayList<>();

    public ArcQuestToastOverlayRoot(Minecraft minecraft) {
        super(minecraft, "arc_quest_toasts");
    }

    @Override
    protected void tick(ArcGuiTickContext context, int refX, int refY) {
        ensureElements(QuestToastManager.MAX_SLOTS);
        boolean anyVisible = false;
        for (int i = 0; i < elements.size(); i++) {
            ArcQuestToastViewModel toast = QuestToastManager.getActiveToast(i);
            elements.get(i).apply(toast, i);
            anyVisible |= toast != null;
        }
        setVisible(anyVisible);
    }

    private void ensureElements(int count) {
        while (elements.size() < count) {
            ArcQuestToastElement element = new ArcQuestToastElement();
            element.setVisible(false);
            elements.add(element);
            addChild(element);
        }
    }
}
