package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import org.arcadia.arc_quest.quest.api.CollectionCategoryDefinition;
import org.arcadia.arc_quest.quest.api.CollectionQuestConfig;
import org.arcadia.arc_quest.quest.api.CollectionRewardNode;
import org.arcadia.arc_quest.quest.api.EntryRewardGrantMode;
import org.arcadia.arc_quest.quest.api.QuestText;
import org.arcadia.arc_quest.quest.api.RewardScope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JournalRewardTabsTest {
    @Test void surveyIncludesQuestMilestonesAndOnlyTheSelectedCategory() {
        var quest = node("survey", RewardScope.QUEST, EntryRewardGrantMode.MANUAL);
        var forest = node("forest", RewardScope.CATEGORY, EntryRewardGrantMode.MANUAL);
        var mine = node("mine", RewardScope.CATEGORY, EntryRewardGrantMode.MANUAL);
        var config = config(true, List.of(quest), category("forest", forest), category("mine", mine));
        assertEquals(List.of(quest, forest, mine), JournalRewardTabs.surveyNodes(config, ""));
        assertEquals(List.of(quest, forest), JournalRewardTabs.surveyNodes(config, "forest"));
        assertEquals(List.of(quest, mine), JournalRewardTabs.surveyNodes(config, "mine"));
        assertEquals(List.of(quest), JournalRewardTabs.surveyNodes(config, "missing"));
    }

    @Test void favoritesAreACatalogFilterAndKeepAllSurveyMilestonesAvailable() {
        var quest = node("survey", RewardScope.QUEST, EntryRewardGrantMode.MANUAL);
        var forest = node("forest", RewardScope.CATEGORY, EntryRewardGrantMode.MANUAL);
        var config = config(true, List.of(quest), category("forest", forest));
        assertEquals(JournalRewardTabs.surveyNodes(config, ""), JournalRewardTabs.surveyNodes(config, "\u0000favorites"));
        assertEquals(JournalRewardTabs.surveyNodes(config, ""), JournalRewardTabs.surveyNodes(config, null));
    }

    @Test void automaticMilestonesAndDisabledClaimsDoNotCreateManualSurveyControls() {
        var automatic = node("auto", RewardScope.QUEST, EntryRewardGrantMode.AUTO);
        var manual = node("manual", RewardScope.CATEGORY, EntryRewardGrantMode.MANUAL);
        assertEquals(List.of(manual), JournalRewardTabs.surveyNodes(
                config(true, List.of(automatic), category("forest", manual)), ""));
        assertTrue(JournalRewardTabs.surveyNodes(
                config(false, List.of(automatic), category("forest", manual)), "").isEmpty());
        assertTrue(JournalRewardTabs.surveyNodes(null, "").isEmpty());
    }

    @Test void availableTabsPreserveThePlayerSelectionAndFallbackWithoutDroppingRealPhaseRewards() {
        var primary = JournalRewardTabs.Tab.PRIMARY;
        var phase = JournalRewardTabs.Tab.PHASE;
        var chapter = JournalRewardTabs.Tab.CHAPTER;
        assertEquals(chapter, JournalRewardTabs.availableTab(chapter, true, true, true));
        assertEquals(phase, JournalRewardTabs.availableTab(phase, true, true, true));
        assertEquals(primary, JournalRewardTabs.availableTab(primary, true, true, true));
        assertEquals(phase, JournalRewardTabs.availableTab(primary, false, true, true));
        assertEquals(chapter, JournalRewardTabs.availableTab(primary, false, false, true));
        assertEquals(primary, JournalRewardTabs.availableTab(phase, true, false, true));
        assertEquals(primary, JournalRewardTabs.availableTab(chapter, true, true, false));
        assertEquals(phase, JournalRewardTabs.availableTab(chapter, false, true, false));
    }

    private static CollectionRewardNode node(String id, RewardScope scope, EntryRewardGrantMode mode) {
        return new CollectionRewardNode(id, scope, mode, List.of(), List.of(), null);
    }

    private static CollectionCategoryDefinition category(String id, CollectionRewardNode... nodes) {
        return new CollectionCategoryDefinition(id, QuestText.literal(id), null, 0, List.of(), List.of(nodes), List.of());
    }

    private static CollectionQuestConfig config(boolean manualClaims, List<CollectionRewardNode> quest,
                                               CollectionCategoryDefinition... categories) {
        return new CollectionQuestConfig(List.of(categories), List.of(), quest, null, null,
                false, manualClaims, true);
    }
}
