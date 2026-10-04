package org.arcadia.arc_quest.client.hud.quest.journal.history;

import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.builder.PhaseBuilder;
import org.arcadia.arc_quest.quest.builder.QuestBuilder;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.spec.io.QuestTextComponentCodec;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QuestChangeHistoryPersistenceTextTest {
    private final Language originalLanguage = Language.getInstance();
    private final QuestChangeHistoryStore store = new QuestChangeHistoryStore();
    @TempDir Path temporaryDirectory;

    @BeforeAll static void initialize() { MinecraftRegistryTestBootstrap.initialize(); }

    @AfterEach void restore() {
        Language.inject(originalLanguage);
        store.flushAndResetClientSession();
    }

    @Test void recordedAuthorTreesSurviveRealPersistenceDefinitionRemovalAndLanguageSwitching() throws Exception {
        language(false);
        Component questName = Component.translatable("history.test.quest", Component.translatable("history.test.region"))
                .withStyle(ChatFormatting.GOLD).append(Component.translatable("history.test.suffix"));
        Component phaseName = Component.translatable("history.test.phase", Component.translatable("history.test.subject"));
        Component objectiveName = Component.translatable("history.test.objective",
                        Component.translatableWithFallback("history.test.subject", "Unknown creature").withStyle(ChatFormatting.GREEN))
                .withStyle(ChatFormatting.AQUA).append(Component.translatable("history.test.note"));
        Component entryName = Component.translatable("history.test.entry", Component.translatable("history.test.subject"))
                .withStyle(ChatFormatting.YELLOW);
        QuestDefinition definition = QuestBuilder.create("test:history_text_snapshot").displayName(questName)
                .phase(PhaseBuilder.create("survey").displayName(phaseName)
                        .objective(ObjectiveBuilder.custom(ResourceLocation.parse("test:subject"), 3).display(objectiveName)))
                .phase(PhaseBuilder.create("return").displayName(Component.translatable("history.test.return"))
                        .objective(ObjectiveBuilder.custom(ResourceLocation.parse("test:return"), 1))).build();
        Map<ResourceLocation, QuestDefinition> previous = QuestRegistry.getDatapackSnapshot();
        Map<ResourceLocation, QuestDefinition> installed = new LinkedHashMap<>(previous);
        installed.put(definition.getId(), definition);
        QuestRegistry.replaceDatapackSnapshot(installed);
        QuestRegistry.replaceClientPresentationSnapshot(Map.of(definition.getId(), definition));
        try {
            String questId = definition.getId().toString();
            store.recordQuestAccepted(questId);
            store.recordPhaseAdded(questId, "survey");
            store.recordObjectiveProgress(questId, "survey", 0, 0, 2, 3);
            store.recordObjectiveCompleted(questId, "survey", 0, 3);
            store.recordCollectionEntryEvent(questId, "survey", "zombie", entryName, true);
            store.recordCollectionEntryEvent(questId, "survey", "zombie", entryName, false);
            store.recordPhaseAdvanced(questId, "survey", "return");
            store.recordPhaseSwitched(questId, "survey", "return");
            QuestChangeHistoryPersistence.HistoryFile file = new QuestChangeHistoryPersistence.HistoryFile();
            file.entries = store.query(new QuestChangeHistoryFilters());
            assertEquals(8, file.entries.size());
            Path path = temporaryDirectory.resolve("history.json");
            QuestChangeHistoryPersistence.save(path, file);
            String saved = Files.readString(path);
            assertTrue(saved.contains("history.test.quest"));
            assertTrue(saved.contains("history.test.objective"));
            assertTrue(saved.contains("Unknown creature"));
            assertFalse(saved.contains("questNameSnapshot"));
            assertFalse(saved.contains("phaseNameSnapshot"));
            assertFalse(saved.contains("detailSnapshot"));

            QuestRegistry.replaceDatapackSnapshot(previous);
            QuestRegistry.replaceClientPresentationSnapshot(Map.of());
            assertNull(QuestRegistry.get(definition.getId()));
            List<QuestChangeHistoryEntry> loaded = QuestChangeHistoryPersistence.load(path).entries;
            assertEquals(8, loaded.size());
            QuestChangeHistoryEntry accepted = find(loaded, QuestChangeHistoryType.QUEST_ACCEPTED);
            QuestChangeHistoryEntry phase = find(loaded, QuestChangeHistoryType.PHASE_ADDED);
            QuestChangeHistoryEntry progress = find(loaded, QuestChangeHistoryType.OBJECTIVE_PROGRESS);
            QuestChangeHistoryEntry objective = find(loaded, QuestChangeHistoryType.OBJECTIVE_COMPLETED);
            QuestChangeHistoryEntry discovered = find(loaded, QuestChangeHistoryType.COLLECTION_ENTRY_DISCOVERED);
            QuestChangeHistoryEntry completed = find(loaded, QuestChangeHistoryType.COLLECTION_ENTRY_COMPLETED);
            QuestChangeHistoryEntry advanced = find(loaded, QuestChangeHistoryType.PHASE_ADVANCED);
            QuestChangeHistoryEntry switched = find(loaded, QuestChangeHistoryType.PHASE_SWITCHED);
            Component loadedQuestName = accepted.questNameComponent();
            Component loadedPhaseName = phase.phaseNameComponent();
            Component loadedObjectiveName = progress.detailComponent();
            Component loadedEntryName = discovered.detailComponent();
            Component loadedAdvance = advanced.detailComponent();
            assertEquals(questName, loadedQuestName);
            assertEquals(phaseName, loadedPhaseName);
            assertEquals(objectiveName, loadedObjectiveName);
            assertEquals(entryName, loadedEntryName);
            assertEquals(objectiveName, objective.detailComponent());
            assertEquals(entryName, completed.detailComponent());
            assertEquals("Survey of Forest!", loadedQuestName.getString());
            assertEquals("Inspect Zombies", loadedPhaseName.getString());
            assertEquals("Defeat Zombies safely", loadedObjectiveName.getString());
            assertEquals("Record Zombies", loadedEntryName.getString());
            assertEquals("Inspect Zombies -> Return", loadedAdvance.getString());
            String eventId = progress.id;
            String dedupeKey = progress.dedupeKey();

            language(true);
            assertEquals("林地调查！", loadedQuestName.getString());
            assertEquals("调查僵尸", loadedPhaseName.getString());
            assertEquals("击败僵尸，注意安全", loadedObjectiveName.getString());
            assertEquals("收录僵尸", loadedEntryName.getString());
            assertEquals("调查僵尸 -> 返回", loadedAdvance.getString());
            assertEquals("林地调查！", accepted.questNameComponent().getString());
            assertEquals("调查僵尸", phase.phaseNameComponent().getString());
            assertEquals("击败僵尸，注意安全", objective.detailComponent().getString());
            assertEquals("收录僵尸", completed.detailComponent().getString());
            assertEquals(ChatFormatting.GOLD.getColor().intValue(), loadedQuestName.getStyle().getColor().getValue());
            assertEquals(ChatFormatting.AQUA.getColor().intValue(), loadedObjectiveName.getStyle().getColor().getValue());
            Component nested = assertInstanceOf(Component.class,
                    assertInstanceOf(TranslatableContents.class, loadedObjectiveName.getContents()).getArgs()[0]);
            assertEquals("Unknown creature", assertInstanceOf(TranslatableContents.class, nested.getContents()).getFallback());
            assertEquals(ChatFormatting.GREEN.getColor().intValue(), nested.getStyle().getColor().getValue());
            assertEquals("Survey of Forest!", accepted.questName);
            assertEquals("Inspect Zombies", phase.phaseName);
            assertEquals("0/3", progress.beforeValue);
            assertEquals("2/3", progress.afterValue);
            assertEquals("3/3", objective.afterValue);
            assertEquals("survey", advanced.beforeValue);
            assertEquals("return", advanced.afterValue);
            assertEquals("Inspect Zombies", switched.beforeValue);
            assertEquals("Return", switched.afterValue);
            assertEquals(eventId, progress.id);
            assertEquals(dedupeKey, progress.dedupeKey());
        } finally {
            QuestRegistry.replaceDatapackSnapshot(previous);
            QuestRegistry.clearClientPresentationSnapshot();
        }
    }

    @Test void oldAndDamagedTextSnapshotsKeepTheOriginalLogInsteadOfLosingTheHistoryFile() throws Exception {
        Path path = temporaryDirectory.resolve("history.json");
        Files.writeString(path, """
                {"schemaVersion":1,"entries":[
                  {"questName":"Original quest","phaseName":"Original phase","detail":"history.test.subject"},
                  {"questName":"Broken name","questNameComponentJson":"{oops",
                   "phaseName":"Broken phase","phaseNameComponentJson":"null",
                   "detail":"Original detail","detailComponentJson":"{\\"unexpected\\":true}"},
                  {"questName":null,"phaseName":null,"detail":null,"detailComponentJson":""}
                ]}
                """);
        List<QuestChangeHistoryEntry> loaded = QuestChangeHistoryPersistence.load(path).entries;
        assertEquals(3, loaded.size());
        language(true);
        assertEquals("Original quest", loaded.get(0).questNameComponent().getString());
        assertEquals("Original phase", loaded.get(0).phaseNameComponent().getString());
        assertEquals("history.test.subject", loaded.get(0).detailComponent().getString());
        assertEquals("Broken name", loaded.get(1).questNameComponent().getString());
        assertEquals("Broken phase", loaded.get(1).phaseNameComponent().getString());
        assertEquals("Original detail", loaded.get(1).detailComponent().getString());
        assertEquals("", loaded.get(2).questNameComponent().getString());
        assertEquals("", loaded.get(2).phaseNameComponent().getString());
        assertEquals("", loaded.get(2).detailComponent().getString());
        assertTrue(Files.exists(path));
        assertFalse(Files.exists(path.resolveSibling("arc_quest_change_history.json.broken")));
        QuestChangeHistoryPersistence.save(path, QuestChangeHistoryPersistence.load(path));
        assertEquals(3, QuestChangeHistoryPersistence.load(path).entries.size());
    }

    @Test void cachedSnapshotsFollowFieldChangesAndReturnSafeCopies() {
        language(false);
        QuestChangeHistoryEntry entry = new QuestChangeHistoryEntry();
        entry.setQuestName(Component.translatable("history.test.region").withStyle(ChatFormatting.GOLD));
        entry.setPhaseName(Component.translatable("history.test.return"));
        entry.setDetail(Component.translatable("history.test.subject"));
        assertInstanceOf(MutableComponent.class, entry.questNameComponent()).append(" changed").withStyle(ChatFormatting.RED);
        assertEquals("Forest", entry.questNameComponent().getString());
        assertEquals(ChatFormatting.GOLD.getColor().intValue(), entry.questNameComponent().getStyle().getColor().getValue());
        assertEquals("Return", entry.phaseNameComponent().getString());
        assertEquals("Zombies", entry.detailComponent().getString());
        entry.detailComponentJson = QuestTextComponentCodec.encode(Component.translatable("history.test.return"));
        assertEquals("Return", entry.detailComponent().getString());
        entry.detail = "Changed fallback";
        entry.detailComponentJson = "{invalid";
        assertEquals("Changed fallback", entry.detailComponent().getString());
        entry.detail = "Updated fallback";
        assertEquals("Updated fallback", entry.detailComponent().getString());
        entry.questNameComponentJson = null;
        entry.questName = "Old quest";
        assertEquals("Old quest", entry.questNameComponent().getString());
        entry.phaseNameComponentJson = null;
        entry.phaseName = "Old phase";
        assertEquals("Old phase", entry.phaseNameComponent().getString());
        entry.setDetail(null);
        assertEquals("", entry.detailComponent().getString());
    }

    @Test void changingLanguageDoesNotChangeDeduplicationOrInterpretDiagnosticIdsAsKeys() {
        language(false);
        store.recordCollectionEntryEvent("test:missing", "survey", "binding",
                Component.translatable("history.test.subject"), true);
        QuestChangeHistoryEntry first = store.query(null).get(0);
        String eventId = first.id;
        String dedupeKey = first.dedupeKey();
        language(true);
        store.recordCollectionEntryEvent("test:missing", "survey", "binding",
                Component.translatable("history.test.subject"), true);
        assertEquals(1, store.query(null).size());
        assertEquals("僵尸", first.detailComponent().getString());
        assertEquals(eventId, first.id);
        assertEquals(dedupeKey, first.dedupeKey());
        store.recordCollectionRewardUnlocked("test:missing", "history.test.subject");
        store.recordCollectionCategoryCompleted("test:missing", "history.test.region");
        store.recordCollectionEntryEvent("test:missing", "survey", "legacy", "history.test.subject", false);
        List<QuestChangeHistoryEntry> entries = store.query(null);
        assertEquals(4, entries.size());
        assertEquals("history.test.subject", find(entries, QuestChangeHistoryType.COLLECTION_REWARD_UNLOCKED).detailComponent().getString());
        assertEquals("history.test.region", find(entries, QuestChangeHistoryType.COLLECTION_CATEGORY_COMPLETED).detailComponent().getString());
        assertEquals("history.test.subject", find(entries, QuestChangeHistoryType.COLLECTION_ENTRY_COMPLETED).detailComponent().getString());
    }

    private static QuestChangeHistoryEntry find(List<QuestChangeHistoryEntry> entries, QuestChangeHistoryType type) {
        return entries.stream().filter(entry -> entry.type == type).findFirst().orElseThrow();
    }

    private void language(boolean chinese) {
        Map<String, String> values = Map.of(
                "history.test.quest", chinese ? "%s调查" : "Survey of %s",
                "history.test.region", chinese ? "林地" : "Forest",
                "history.test.suffix", chinese ? "！" : "!",
                "history.test.phase", chinese ? "调查%s" : "Inspect %s",
                "history.test.subject", chinese ? "僵尸" : "Zombies",
                "history.test.objective", chinese ? "击败%s" : "Defeat %s",
                "history.test.note", chinese ? "，注意安全" : " safely",
                "history.test.entry", chinese ? "收录%s" : "Record %s",
                "history.test.return", chinese ? "返回" : "Return");
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
