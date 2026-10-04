package org.arcadia.arc_quest.client.hud.quest.tracker;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.quest.api.CollectionEntryConfig;
import org.arcadia.arc_quest.quest.api.QuestMode;
import org.arcadia.arc_quest.quest.builder.CollectionQuestConfigBuilder;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.builder.PhaseBuilder;
import org.arcadia.arc_quest.quest.builder.QuestBuilder;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrackerCollectionLanguageTest {
    private final Language original = Language.getInstance();
    @BeforeAll static void initialize() { MinecraftRegistryTestBootstrap.initialize(); }
    @AfterEach void restoreLanguage() { Language.inject(original); }

    @Test void unchangedProgressCanReuseCachedTrackerRowsAfterSwitchingLanguage() {
        var entry = new CollectionEntryConfig("field", null, null, List.of(), null, 1,
                false, false, 0, null, List.of(), 0, true);
        var definition = QuestBuilder.create("test:localized_collection_tracker").mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("field", "Field").build())
                .phase(PhaseBuilder.create("survey").collectionEntryConfig(entry)
                        .objective(ObjectiveBuilder.custom(ResourceLocation.parse("test:subject"), 1))).build();
        var runtime = new QuestRuntimeData(definition.getId().toString(), "survey", 0, 0, 0, 0);
        var adapter = new TrackerCollectionProgressAdapter();
        language(Map.of("arc_quest.hud.tracker.collection_progress", "Collection progress",
                "arc_quest.hud.tracker.discovered_entries", "Discovered entries"));
        var rows = adapter.buildObjectives(definition, runtime, null);
        assertEquals("Collection progress", rows.get(0).getDisplayText().getString());
        assertEquals("Discovered entries", rows.get(1).getDisplayText().getString());
        assertInstanceOf(TranslatableContents.class, rows.get(0).getDisplayText().getContents());

        language(Map.of("arc_quest.hud.tracker.collection_progress", "图鉴进度",
                "arc_quest.hud.tracker.discovered_entries", "已发现条目"));
        assertSame(rows, adapter.buildObjectives(definition, runtime, null));
        assertEquals("图鉴进度", rows.get(0).getDisplayText().getString());
        assertEquals("已发现条目", rows.get(1).getDisplayText().getString());
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
