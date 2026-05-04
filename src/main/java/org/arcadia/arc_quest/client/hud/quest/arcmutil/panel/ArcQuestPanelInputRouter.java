package org.arcadia.arc_quest.client.hud.quest.arcmutil.panel;

import net.minecraft.client.Minecraft;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.collection.ArcQuestCollectionHistoryManager;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.history.ArcQuestHistoryPanelElement;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.intel.ArcQuestIntelPanelElement;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.offer.ArcQuestOfferPanelElement;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.story.ArcQuestStoryPanelElement;

public final class ArcQuestPanelInputRouter {
    private ArcQuestPanelInputRouter() {
    }

    public static boolean isAnyPanelActive() {
        return ArcQuestIntelPanelElement.isActive()
                || ArcQuestOfferPanelElement.isActive()
                || ArcQuestHistoryPanelElement.isActive()
                || ArcQuestCollectionHistoryManager.isActive()
                || ArcQuestStoryPanelElement.isActive();
    }

    public static boolean mouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        if (ArcQuestIntelPanelElement.isActive()) {
            if (button == 0) ArcQuestIntelPanelElement.handleMouseClick(mouseX, mouseY, screenWidth, screenHeight);
            return true;
        }
        if (ArcQuestOfferPanelElement.isActive()) return ArcQuestOfferPanelElement.mouseClicked(mouseX, mouseY, button);
        if (ArcQuestHistoryPanelElement.isActive()) return ArcQuestHistoryPanelElement.mouseClicked(mouseX, mouseY, button);
        if (ArcQuestCollectionHistoryManager.isActive()) {
            ArcQuestCollectionHistoryManager.close();
            return true;
        }
        if (ArcQuestStoryPanelElement.isActive()) return ArcQuestStoryPanelElement.mouseClicked(mouseX, mouseY, button);
        return false;
    }

    public static boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (ArcQuestHistoryPanelElement.isActive()) return ArcQuestHistoryPanelElement.mouseReleased(button);
        return isAnyPanelActive();
    }

    public static boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (ArcQuestHistoryPanelElement.isActive()) return ArcQuestHistoryPanelElement.mouseDragged(mouseX, mouseY);
        return isAnyPanelActive();
    }

    public static boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (ArcQuestIntelPanelElement.isActive()) {
            if (delta > 0) ArcQuestIntelPanelElement.scrollBack();
            else if (delta < 0) ArcQuestIntelPanelElement.scrollForward();
            return true;
        }
        if (ArcQuestHistoryPanelElement.isActive()) return ArcQuestHistoryPanelElement.mouseScrolled(mouseX, mouseY, delta);
        return isAnyPanelActive();
    }

    public static boolean keyPressed(int keyCode, int scanCode) {
        if (ArcQuestIntelPanelElement.isActive()) {
            Minecraft mc = Minecraft.getInstance();
            if (keyCode == 256 || mc.options.keyInventory.matches(keyCode, scanCode)) {
                ArcQuestIntelPanelElement.dismiss();
            } else if (keyCode == 32) {
                ArcQuestIntelPanelElement.togglePause();
            } else if (mc.options.keyLeft.matches(keyCode, scanCode)) {
                ArcQuestIntelPanelElement.scrollBack();
            } else if (mc.options.keyRight.matches(keyCode, scanCode)) {
                ArcQuestIntelPanelElement.scrollForward();
            }
            return true;
        }
        if (ArcQuestOfferPanelElement.isActive()) return ArcQuestOfferPanelElement.keyPressed(keyCode);
        if (ArcQuestHistoryPanelElement.isActive()) return ArcQuestHistoryPanelElement.keyPressed(keyCode);
        if (ArcQuestCollectionHistoryManager.isActive()) return ArcQuestCollectionHistoryManager.keyPressed(keyCode);
        if (ArcQuestStoryPanelElement.isActive()) return ArcQuestStoryPanelElement.keyPressed(keyCode);
        return false;
    }

    public static void closeActivePanel() {
        if (ArcQuestIntelPanelElement.isActive()) ArcQuestIntelPanelElement.dismiss();
        else if (ArcQuestOfferPanelElement.isActive()) ArcQuestOfferPanelElement.close();
        else if (ArcQuestHistoryPanelElement.isActive()) ArcQuestHistoryPanelElement.close();
        else if (ArcQuestCollectionHistoryManager.isActive()) ArcQuestCollectionHistoryManager.close();
        else if (ArcQuestStoryPanelElement.isActive()) ArcQuestStoryPanelElement.close();
    }
}
