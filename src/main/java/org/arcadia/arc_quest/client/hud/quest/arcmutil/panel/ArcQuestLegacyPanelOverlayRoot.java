package org.arcadia.arc_quest.client.hud.quest.arcmutil.panel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.collection.ArcQuestCollectionHistoryElement;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.collection.ArcQuestCollectionHistoryManager;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.story.ArcQuestStoryPanelElement;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.history.ArcQuestHistoryPanelElement;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.offer.ArcQuestOfferPanelElement;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.intel.ArcQuestIntelPanelElement;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.overlay.ArcOverlayRoot;

public class ArcQuestLegacyPanelOverlayRoot extends ArcOverlayRoot {
    private final ArcQuestCollectionHistoryElement collectionHistoryElement = new ArcQuestCollectionHistoryElement();
    private final ArcQuestStoryPanelElement storyPanelElement = new ArcQuestStoryPanelElement();
    private final ArcQuestOfferPanelElement offerPanelElement = new ArcQuestOfferPanelElement();
    private final ArcQuestHistoryPanelElement historyPanelElement = new ArcQuestHistoryPanelElement();
    private final ArcQuestIntelPanelElement intelPanelElement = new ArcQuestIntelPanelElement();

    public ArcQuestLegacyPanelOverlayRoot(Minecraft minecraft) {
        super(minecraft, "arc_quest_legacy_panel_bridge");
        addChild(collectionHistoryElement);
        addChild(storyPanelElement);
        addChild(offerPanelElement);
        addChild(historyPanelElement);
        addChild(intelPanelElement);
    }

    @Override
    public void draw(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (!isAnyPanelActive()) return;
        Minecraft mc = Minecraft.getInstance();
        int mouseX = (int) (mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth());
        int mouseY = (int) (mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight());
        if (ArcQuestIntelPanelElement.isActive()) intelPanelElement.draw(graphics, context, refX, refY, inheritedOpacity);
        if (ArcQuestOfferPanelElement.isActive()) offerPanelElement.draw(graphics, context, refX, refY, inheritedOpacity);
        if (ArcQuestHistoryPanelElement.isActive()) historyPanelElement.draw(graphics, context, refX, refY, inheritedOpacity);
        else if (ArcQuestCollectionHistoryManager.isActive()) collectionHistoryElement.draw(graphics, context, refX, refY, inheritedOpacity);
        if (ArcQuestStoryPanelElement.isActive()) storyPanelElement.draw(graphics, context, refX, refY, inheritedOpacity);
    }

    public static boolean isAnyPanelActive() {
        return ArcQuestIntelPanelElement.isActive()
                || ArcQuestOfferPanelElement.isActive()
                || ArcQuestHistoryPanelElement.isActive()
                || ArcQuestCollectionHistoryManager.isActive()
                || ArcQuestStoryPanelElement.isActive();
    }
}
