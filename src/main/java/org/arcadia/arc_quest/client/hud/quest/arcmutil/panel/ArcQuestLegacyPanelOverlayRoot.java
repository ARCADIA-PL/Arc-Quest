package org.arcadia.arc_quest.client.hud.quest.arcmutil.panel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.collection.ArcQuestCollectionHistoryElement;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.collection.ArcQuestCollectionHistoryManager;
import org.arcadia.arc_quest.client.hud.quest.history.QuestHistoryPanel;
import org.arcadia.arc_quest.client.hud.quest.offer.QuestOfferPanel;
import org.arcadia.arc_quest.client.hud.quest.ponder.QuestIntelPanel;
import org.arcadia.arc_quest.client.hud.quest.story.QuestStoryPanel;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.overlay.ArcOverlayRoot;

public class ArcQuestLegacyPanelOverlayRoot extends ArcOverlayRoot {
    private final ArcQuestCollectionHistoryElement collectionHistoryElement = new ArcQuestCollectionHistoryElement();

    public ArcQuestLegacyPanelOverlayRoot(Minecraft minecraft) {
        super(minecraft, "arc_quest_legacy_panel_bridge");
        addChild(collectionHistoryElement);
    }

    @Override
    public void draw(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (!isAnyPanelActive()) return;
        Minecraft mc = Minecraft.getInstance();
        int mouseX = (int) (mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth());
        int mouseY = (int) (mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight());
        if (QuestIntelPanel.isActive()) QuestIntelPanel.render(graphics, context.screenWidth(), context.screenHeight(), context.partialTick());
        if (QuestOfferPanel.isActive()) QuestOfferPanel.render(graphics, mouseX, mouseY, context.partialTick());
        if (QuestHistoryPanel.isActive()) QuestHistoryPanel.render(graphics, mouseX, mouseY, context.partialTick());
        else if (ArcQuestCollectionHistoryManager.isActive()) collectionHistoryElement.draw(graphics, context, refX, refY, inheritedOpacity);
        if (QuestStoryPanel.isActive()) QuestStoryPanel.render(graphics, mouseX, mouseY, context.partialTick());
    }

    public static boolean isAnyPanelActive() {
        return QuestIntelPanel.isActive()
                || QuestOfferPanel.isActive()
                || QuestHistoryPanel.isActive()
                || ArcQuestCollectionHistoryManager.isActive()
                || QuestStoryPanel.isActive();
    }
}
