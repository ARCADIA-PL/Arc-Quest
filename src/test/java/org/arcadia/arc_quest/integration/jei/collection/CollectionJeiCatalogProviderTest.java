package org.arcadia.arc_quest.integration.jei.collection;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogEntry;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.*;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CollectionJeiCatalogProviderTest {
    @BeforeAll static void bootstrap() { MinecraftRegistryTestBootstrap.initialize(); }
    @Test void crossBookSourcesRequireBothKnownBindingsAndExactVariantDisclosure() {
        var data = new ArcQuestPlayer(UUID.randomUUID());
        var source = CollectionEntryBuilder.create("test:place").category("objects").displayName("Hidden place")
                .visibility(VisibilityMode.HIDDEN_BY_DEFAULT, HiddenPresentationMode.FULLY_HIDDEN).build();
        var subject = new CollectionSubjectSpec(ResourceLocation.parse("arc_quest:item_stack"), ResourceLocation.parse("minecraft:paper"),
                1, "{gem:\"test:g\",rarity:\"test:epic\"}", new StackMatchSpec(StackMatchSpec.Mode.NBT_SUBSET, ""), Map.of());
        var gem = CollectionEntryBuilder.create("test:gem").category("objects").displayName("Gem")
                .presentation(new CollectionEntryPresentation(null, List.of(new CollectionVariantDefinition("epic", QuestText.literal("Epic"),
                        subject, "", CollectionContentReveal.DISCOVERED)), "epic", List.of())).build();
        var sourceQuest = quest("test:places", source, List.of());
        var relation = new CollectionSourceSetDefinition(1, ResourceLocation.parse("test:sources"), List.of(
                new CollectionSourceSetDefinition.Relation(ResourceLocation.parse("test:drop"), source.getEntryId(), gem.getEntryId(),
                        List.of("epic"), "Exploration", QuestText.literal("Confirmed route"), 0)));
        var targetQuest = quest("test:gems", gem, List.of(relation));
        Map<String,QuestDefinition> definitions = Map.of("test:places", sourceQuest, "test:gems", targetQuest);
        data.addActiveQuest(run(targetQuest));
        var rows = collect(data, definitions);
        assertTrue(rows.isEmpty(), "Undiscovered variant exposes neither its stack nor source");
        data.getCollectionRecords().discover(gem.getEntryId());
        rows = collect(data, definitions);
        assertEquals(1, rows.size()); assertEquals(JeiCatalogEntry.Kind.COLLECTION_INFO, rows.get(0).kind());
        data.addActiveQuest(run(sourceQuest));
        assertEquals(1, collect(data, definitions).size(), "An unknown source binding cannot reveal a place name");
        data.getCollectionRecords().discover(source.getEntryId());
        rows = collect(data, definitions);
        assertEquals(2, rows.size());
        var sourceRow = rows.stream().filter(r -> r.kind() == JeiCatalogEntry.Kind.COLLECTION_SOURCE).findFirst().orElseThrow();
        assertEquals("epic", sourceRow.collectionNavigation().variantId());
        assertTrue(sourceRow.inputs().isEmpty()); assertFalse(sourceRow.outputs().isEmpty());
        assertTrue(sourceRow.notes().stream().anyMatch(note -> note.getString().contains("Hidden place")));
        data.removeActiveQuest("test:places");
        assertEquals(1, collect(data, definitions).size(), "Authorization removal revokes cross-book sources");
    }
    private static List<JeiCatalogEntry> collect(ArcQuestPlayer data, Map<String,QuestDefinition> definitions) {
        var rows = new ArrayList<JeiCatalogEntry>();
        CollectionJeiCatalogProvider.collectKnown(null, data, run -> definitions.get(run.getQuestId()), rows::add);
        return rows;
    }
    private static QuestDefinition quest(String id, CollectionEntryDefinition entry, List<CollectionSourceSetDefinition> sources) {
        return QuestBuilder.create(id).mode(QuestMode.COLLECTION).collectionConfig(CollectionQuestConfigBuilder.create()
                .category("objects", "Objects").entry(entry).build().withSourceSets(sources))
                .phase(PhaseBuilder.create("sheet").collectionSheet(CollectionSheetBuilder.create().binding(
                        EntryRequirementBuilder.create("binding", entry.getEntryId()).discovered()))).build();
    }
    private static QuestRuntimeData run(QuestDefinition quest) {
        var runtime = new QuestRuntimeData(quest.getId().toString(), "sheet", 0, 0, 0, 0);
        var collection = new CollectionRuntimeData(); collection.initializeSheet("sheet", List.of("binding"), 1, Set.of());
        runtime.setCollectionData(collection); return runtime;
    }
}
