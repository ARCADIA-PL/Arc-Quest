package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import org.arcadia.arc_quest.quest.api.CollectionQuestConfig;
import org.arcadia.arc_quest.quest.api.CollectionRewardNode;
import org.arcadia.arc_quest.quest.api.EntryRewardGrantMode;

import java.util.ArrayList;
import java.util.List;

/** Reward source selection stays independent of drawing and input capture. */
final class JournalRewardTabs {
    enum Tab { PRIMARY, PHASE, RELATED, CHAPTER }

    private JournalRewardTabs() {}

    static Tab availableTab(Tab preferred, boolean hasPrimary, boolean hasPhase, boolean hasChapter) {
        return availableTab(preferred, hasPrimary, hasPhase, false, hasChapter);
    }
    static Tab availableTab(Tab preferred, boolean hasPrimary, boolean hasPhase, boolean hasRelated, boolean hasChapter) {
        boolean available = switch (preferred) { case PRIMARY -> hasPrimary; case PHASE -> hasPhase; case RELATED -> hasRelated; case CHAPTER -> hasChapter; };
        if (available) return preferred;
        return hasPrimary ? Tab.PRIMARY : hasPhase ? Tab.PHASE : hasRelated ? Tab.RELATED : hasChapter ? Tab.CHAPTER : preferred;
    }

    static String normalizeCategory(String category) {
        return category == null || category.startsWith("\u0000") ? "" : category;
    }

    static List<CollectionRewardNode> surveyNodes(CollectionQuestConfig config, String selectedCategory) {
        if (config == null || !config.isAllowManualRewardClaim()) return List.of();
        String categoryId = normalizeCategory(selectedCategory);
        List<CollectionRewardNode> nodes = new ArrayList<>(config.getQuestRewardNodes());
        for (var category : config.getCategories()) {
            if (categoryId.isEmpty() || categoryId.equals(category.getCategoryId())) nodes.addAll(category.getRewardNodes());
        }
        return nodes.stream().filter(node -> node.getGrantMode() == EntryRewardGrantMode.MANUAL).toList();
    }
}
