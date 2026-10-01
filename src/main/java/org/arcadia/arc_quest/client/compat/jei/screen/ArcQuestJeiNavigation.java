package org.arcadia.arc_quest.client.compat.jei.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.client.hud.gacha.GachaScreen;
import org.arcadia.arc_quest.client.hud.guide.GuideScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.JournalTypes;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.shop.AbstractTradeScreen;
import org.arcadia.arc_quest.client.hud.quest.history.QuestHistoryPanel;
import org.arcadia.arc_quest.client.compat.jei.JeiCatalogClient;
import org.arcadia.arc_quest.guide.network.ClientGuideCache;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogEntry;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;

/** Navigation never purchases, submits, starts a draw, grants content, or creates a shop session. */
public final class ArcQuestJeiNavigation {
    private ArcQuestJeiNavigation() {}

    public static boolean open(JeiCatalogEntry entry) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        entry = JeiCatalogClient.find(entry.id());
        if (entry == null) return false;
        return switch (entry.kind()) {
            case QUEST_REQUIREMENT, QUEST_REWARD -> openQuest(entry);
            case GUIDE -> openGuide(entry);
            case TRADE, GACHA -> returnToSession(entry);
        };
    }

    private static boolean openGuide(JeiCatalogEntry entry) {
        ResourceLocation id = ResourceLocation.tryParse(entry.navigationTarget());
        if (id == null || !ClientGuideCache.INSTANCE.isUnlocked(id)) return false;
        var guide = GuideRegistry.get(id);
        if (guide == null) return false;
        int page;
        try { page = Integer.parseInt(entry.navigationDetail()); }
        catch (NumberFormatException ignored) { return false; }
        if (page < 0 || page >= guide.getPageCount()) return false;
        if (guide.getItemAssociations().stream().noneMatch(association -> association.pageIndex() == page)) return false;
        return GuideScreen.tryOpen(id, page, true);
    }

    private static boolean openQuest(JeiCatalogEntry entry) {
        if (JeiCatalogClient.find(entry.id()) == null) return false;
        String id = entry.navigationTarget();
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null || QuestRegistry.get(location) == null) return false;
        ClientQuestCache cache = ClientQuestCache.INSTANCE;
        JournalTypes.Tab tab;
        if (cache.isQuestActive(id)) tab = JournalTypes.Tab.ACTIVE;
        else if (cache.isQuestCompleted(id)) tab = JournalTypes.Tab.COMPLETED;
        else if (cache.isQuestFailed(id)) tab = JournalTypes.Tab.FAILED;
        else return false;
        QuestJournalScreen journal = new QuestJournalScreen();
        Minecraft.getInstance().setScreen(journal);
        journal.setCurrentTab(tab);
        for (int index = 0; index < journal.getCurrentEntries().size(); index++) {
            if (journal.getCurrentEntries().get(index).questId().equals(id)) {
                journal.onEntrySelected(index);
                var progress = cache.getActiveQuest(id);
                if (!entry.navigationDetail().isBlank() && (progress == null || !progress.isPhaseActive(entry.navigationDetail()))) {
                    QuestHistoryPanel.triggerJei(entry);
                } else journal.getDetailPanel().focusJeiPhase(id, entry.navigationDetail());
                return true;
            }
        }
        return false;
    }

    private static boolean returnToSession(JeiCatalogEntry entry) {
        Screen parent = ArcQuestJeiScreenHandlers.parentScreen();
        boolean matches = parent instanceof AbstractTradeScreen trade && entry.kind() == JeiCatalogEntry.Kind.TRADE
                && trade.getShopId().equals(entry.navigationTarget()) && trade.canQueryJei();
        matches |= parent instanceof GachaScreen gacha && entry.kind() == JeiCatalogEntry.Kind.GACHA
                && gacha.getShopId().equals(entry.navigationTarget()) && gacha.canQueryJei();
        if (matches) Minecraft.getInstance().setScreen(parent);
        return matches;
    }
}
