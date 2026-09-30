package org.arcadia.arc_quest.integration.jei.trade;

import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.api.ChapterShopType;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import java.util.List;

/** Mirrors the chapter-shop action gate without opening a session or disclosing locked chapters. */
public final class ChapterShopVisibility {
    private ChapterShopVisibility() {}

    public static boolean canDisplay(String shopId, ChapterShopType type, ArcQuestPlayer data) {
        List<QuestDefinition> chapters = QuestRegistry.getAll().stream()
                .filter(quest -> quest.hasChapterShop()
                        && (quest.getChapterShopType() == null ? ChapterShopType.TRADE : quest.getChapterShopType()) == type
                        && canonical(shopId).equals(canonical(quest.getChapterShopId())))
                .toList();
        return chapters.isEmpty() || chapters.stream().anyMatch(quest -> canAccess(
                data.isQuestActive(quest.getId().toString()),
                data.isQuestCompleted(quest.getId().toString()), quest.isChapterShopPersistent()));
    }

    static boolean canAccess(boolean active, boolean completed, boolean persistent) {
        return active || (completed && persistent);
    }

    private static String canonical(String id) {
        if (id == null || id.isBlank()) return "";
        return id.contains(":") ? id : Arc_Quest.MOD_ID + ":" + id;
    }
}
