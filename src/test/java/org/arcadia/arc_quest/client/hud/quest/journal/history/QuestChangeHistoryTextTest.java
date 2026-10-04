package org.arcadia.arc_quest.client.hud.quest.journal.history;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QuestChangeHistoryTextTest {
    private final Language original = Language.getInstance();
    @AfterEach void restoreLanguage() { Language.inject(original); }

    @Test void historyTypesAndFiltersResolveInTheCurrentClientLanguage() {
        Component type = QuestChangeHistoryType.COLLECTION_ENTRY_DISCOVERED.displayNameComponent();
        Component category = QuestChangeHistoryCategory.COLLECTION.shortLabelComponent();
        language(Map.of("arc_quest.hud.history.type.collection_entry_discovered", "Entry discovered",
                "arc_quest.hud.history.category.collection", "COLL"));
        assertEquals("Entry discovered", type.getString());
        assertEquals("COLL", category.getString());
        language(Map.of("arc_quest.hud.history.type.collection_entry_discovered", "发现条目",
                "arc_quest.hud.history.category.collection", "图鉴"));
        assertEquals("发现条目", type.getString());
        assertEquals("图鉴", category.getString());
        assertEquals("COLL", QuestChangeHistoryCategory.COLLECTION.shortLabel());
        assertEquals("arc_quest.hud.history.type.collection_entry_discovered",
                QuestChangeHistoryType.COLLECTION_ENTRY_DISCOVERED.displayName());
    }

    private void language(Map<String, String> values) {
        Language.inject(new Language() {
            @Override public String getOrDefault(String key, String fallback) { return values.getOrDefault(key, fallback); }
            @Override public boolean has(String key) { return values.containsKey(key); }
            @Override public boolean isDefaultRightToLeft() { return false; }
            @Override public FormattedCharSequence getVisualOrder(FormattedText text) {
                return FormattedCharSequence.forward(text.getString(), Style.EMPTY);
            }
        });
    }
}
