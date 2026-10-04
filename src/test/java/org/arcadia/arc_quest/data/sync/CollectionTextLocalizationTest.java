package org.arcadia.arc_quest.data.sync;

import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.CollectionRecordState;
import org.arcadia.arc_quest.quest.data.CollectionRunDefinitionStore;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.spec.QuestTextSpec;
import org.arcadia.arc_quest.quest.spec.compile.QuestSpecCompiler;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonReader;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonWriter;
import org.arcadia.arc_quest.quest.spec.io.QuestTextComponentCodec;
import org.arcadia.arc_quest.quest.spec.validate.QuestSpecValidator;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CollectionTextLocalizationTest {
    private static final ResourceLocation ENTRY = ResourceLocation.parse("example:translated_archive");
    private final Language originalLanguage = Language.getInstance();

    @BeforeAll static void initialize() {
        MinecraftRegistryTestBootstrap.initialize();
        assertNotNull(QuestCategory.COLLECTION);
        assertNotNull(ObjectiveType.KILL);
    }

    @AfterEach void restoreLanguage() { Language.inject(originalLanguage); }

    @Test void everyConfiguredFieldPreservesItsComponentTreeThroughAuthorizedWireAndClientCompilation() throws Exception {
        language("en");
        var source = quest(false);
        var records = new CollectionRecordState();
        records.discover(ENTRY);
        var decoded = actualWire(source, records);
        var client = QuestSpecCompiler.compileClientPresentation(QuestSpecJsonReader.read(decoded), Map.of());
        var before = fields(source);
        var after = fields(client);
        assertEquals(13, after.size());
        for (int index = 0; index < before.size(); index++)
            assertEquals(tree(before.get(index)), tree(after.get(index)), "Field " + index);

        // Reuse the very same client components after a locale switch; nothing is resynchronized.
        assertTrue(after.stream().allMatch(component -> "Iron ingot archive!".equals(component.getString())));
        language("zh");
        assertTrue(after.stream().allMatch(component -> "铁锭档案！".equals(component.getString())));
        assertFalse(decoded.contains("Iron ingot"), "Server language must not enter the encoded definition");
        assertTrue(decoded.contains("item.minecraft.iron_ingot"));
        assertEquals(ChatFormatting.GOLD.getColor(), after.get(0).getStyle().getColor().getValue());
    }

    @Test void nativeComponentsAndFrozenAuthoringRemainLanguageIndependentAfterRestart() {
        language("en");
        QuestSpec document = CollectionDefinitionSpecExporter.quest(quest(false), null);
        var store = new CollectionRunDefinitionStore();
        String hash = store.freezeAuthoring(document);
        language("zh");
        assertEquals(hash, store.freezeAuthoring(document));
        var loaded = CollectionRunDefinitionStore.load(store.save(new CompoundTag()));
        var restored = loaded.resolve(hash, ResourceLocation.parse(document.id));
        for (Component component : fields(restored)) assertEquals("铁锭档案！", component.getString());
        language("en");
        for (Component component : fields(restored)) assertEquals("Iron ingot archive!", component.getString());
    }

    @Test void customRewardDisplayTreesSurviveSyncWithoutGrantingClientExecutionAuthority() throws Exception {
        IReward reward = new IReward() {
            @Override public void grant(net.minecraft.server.level.ServerPlayer player) { fail("Custom server grant was called"); }
            @Override public String describe() { return "server diagnostic"; }
            @Override public Component describeComponent() { return rich(); }
        };
        var source = quest(false, reward);
        var records = new CollectionRecordState(); records.discover(ENTRY);
        var restored = QuestSpecCompiler.compileClientPresentation(QuestSpecJsonReader.read(actualWire(source, records)), Map.of())
                .getCompletionRewards().get(0);
        assertEquals(tree(rich()), tree(restored.describeComponent()));
        language("zh");
        assertEquals("铁锭档案！", restored.describeComponent().getString());
        assertEquals("server diagnostic", reward.describe());
        assertThrows(UnsupportedOperationException.class, () -> restored.grant(null));
    }

    @Test void simpleLegacyTextDocumentsKeepTheirExistingShapeAndArguments() {
        var literal = CollectionDefinitionSpecExporter.text(QuestText.literal("Same text"), null);
        assertEquals("literal", literal.mode);
        assertEquals("Same text", literal.value);
        var simple = CollectionDefinitionSpecExporter.text(QuestText.translatable("example.simple",
                QuestText.Arg.constant("plain")), null);
        assertEquals("translatable", simple.mode);
        assertEquals("example.simple", simple.value);
        assertEquals(List.of("plain"), simple.args);
        assertEquals("literal", CollectionDefinitionSpecExporter.text(QuestText.literal(""), null).mode);
        assertEquals("translatable", CollectionDefinitionSpecExporter.text(QuestText.translatable("example.key"), null).mode);

        var document = CollectionDefinitionSpecExporter.quest(quest(false), null);
        document.displayName = simple;
        var result = QuestSpecCompiler.compileClientPresentation(
                QuestSpecJsonReader.read(QuestSpecJsonWriter.write(document)), Map.of());
        var translated = assertInstanceOf(TranslatableContents.class, result.getDisplayName().getContents());
        assertEquals("example.simple", translated.getKey());
        assertEquals("plain", translated.getArgs()[0]);
    }

    @Test void fallbackNumericArgumentsAndKeybindComponentsAreNotFlattened() {
        Component source = Component.translatableWithFallback("example.fallback", "Amount: %s", 7)
                .append(Component.keybind("key.inventory")).withStyle(ChatFormatting.ITALIC);
        var exported = CollectionDefinitionSpecExporter.text(QuestText.component(source), null);
        assertEquals("component", exported.mode);
        var restored = QuestTextComponentCodec.decode(exported.value);
        assertEquals(tree(source), tree(restored));
        assertEquals("Amount: %s", assertInstanceOf(TranslatableContents.class, restored.getContents()).getFallback());
        assertTrue(restored.getStyle().isItalic());
        assertEquals(1, restored.getSiblings().size());
        language("en");
        assertTrue(restored.getString().startsWith("Amount: 7"));
    }

    @Test void invalidNativeTreesAreRejectedAtTheExactTextFieldWithoutWeakeningOldValidation() {
        for (String invalid : List.of("", "null", "{}", "{broken", "[]")) {
            var spec = CollectionDefinitionSpecExporter.quest(quest(false), null);
            spec.displayName = QuestTextSpec.literal(invalid);
            spec.displayName.mode = "component";
            var report = new QuestSpecValidator().validate(spec);
            assertTrue(report.getIssues().stream().anyMatch(issue -> issue.path.equals("displayName.value")), invalid);
            assertThrows(IllegalArgumentException.class, () -> QuestTextComponentCodec.decode(invalid));
        }
        var spec = CollectionDefinitionSpecExporter.quest(quest(false), null);
        spec.displayName = QuestTextSpec.component(rich());
        spec.displayName.args.add("ignored");
        assertTrue(new QuestSpecValidator().validate(spec).getIssues().stream()
                .anyMatch(issue -> issue.path.equals("displayName.args")));
    }

    @Test void undiscoveredRichTextDoesNotExposeSecretKeysOrItsComponentSiblings() throws Exception {
        String wire = actualWire(quest(true), new CollectionRecordState());
        var spec = QuestSpecJsonReader.read(wire);
        var entry = spec.collectionConfig.entries.get(0);
        assertEquals("arc_quest.collection.entry.unknown", entry.displayName.value);
        assertEquals("", entry.description.value);
        assertTrue(entry.content.isEmpty());
        assertEquals("arc_quest.collection.entry.unknown", entry.outcomes.get(0).displayName.value);
        assertFalse(QuestSpecJsonWriter.write(entryQuest(entry)).contains("example.archive"));
        // Public clues intentionally remain authorized, including their nested translation tree.
        assertEquals("component", entry.publicClue.mode);
        var clue = QuestTextComponentCodec.decode(entry.publicClue.value);
        language("zh");
        assertEquals("铁锭档案！", clue.getString());
    }

    private static QuestSpec entryQuest(org.arcadia.arc_quest.quest.spec.CollectionEntrySpecData entry) {
        var spec = new QuestSpec();
        // Only private fields enter this assertion; the deliberately public clue is excluded.
        spec.collectionConfig = new org.arcadia.arc_quest.quest.spec.CollectionQuestSpecData();
        spec.collectionConfig.entries.add(entry);
        var copy = QuestSpecJsonReader.read(QuestSpecJsonWriter.write(spec));
        copy.collectionConfig.entries.get(0).publicClue = QuestTextSpec.literal("");
        return copy;
    }

    private static QuestDefinition quest(boolean hidden) {
        return quest(hidden, null);
    }

    private static QuestDefinition quest(boolean hidden, IReward customReward) {
        var text = QuestText.component(rich());
        var entry = CollectionEntryBuilder.create(ENTRY).category("field").displayName(text).description(text)
                .publicClue(text).outcome("study", text)
                .content(new CollectionContentBlock("notes", text, null, text))
                .visibility(hidden ? VisibilityMode.HIDDEN_BY_DEFAULT : VisibilityMode.VISIBLE_BY_DEFAULT,
                        HiddenPresentationMode.PLACEHOLDER).build();
        var builder = QuestBuilder.create("example:localized_collection").category(QuestCategory.COLLECTION).mode(QuestMode.COLLECTION)
                .displayName(text).description(text)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("field", text).entry(entry).build())
                .phase(PhaseBuilder.create("survey").displayName(text).description(text).story(text)
                        .objective(ObjectiveBuilder.kill(EntityType.ZOMBIE, 1).id("kill").display(text))
                        .collectionSheet(CollectionSheetBuilder.create()
                                .binding(EntryRequirementBuilder.create("entry", ENTRY).objective("kill").recordOutcome("study")))
                        .autoAdvanceOnComplete(false));
        if (customReward != null) builder.reward(customReward);
        return builder.build();
    }

    private static List<Component> fields(QuestDefinition quest) {
        var entry = quest.getCollectionConfig().getEntry(ENTRY);
        var phase = quest.getPhase("survey");
        var block = entry.getContent().get(0);
        return List.of(quest.getDisplayName(), quest.getDescription(),
                quest.getCollectionConfig().getCategories().get(0).getDisplayNameText().resolve(null, null),
                entry.getDisplayName(), entry.getDescription(), entry.getPublicClue(), entry.getOutcome("study").getDisplayName(),
                block.text().resolve(null, null), block.caption().resolve(null, null),
                phase.getDisplayName(), phase.getDescription(), phase.getStory(),
                phase.getObjectives().get(0).getDisplayText());
    }

    private static Component rich() {
        return Component.translatable("example.archive", Items.IRON_INGOT.getDescription())
                .withStyle(ChatFormatting.GOLD).append(Component.translatable("example.archive.tail"));
    }

    private static com.google.gson.JsonElement tree(Component component) {
        return JsonParser.parseString(QuestTextComponentCodec.encode(component));
    }

    private static String actualWire(QuestDefinition quest, CollectionRecordState records) throws Exception {
        var projected = CollectionContentDisclosure.project(DatapackContentSnapshot.empty(1), records,
                ignored -> true, ignored -> quest, null, List.of(quest));
        return DatapackContentCodec.decode(DatapackContentCodec.encode(projected))
                .documents(DatapackContentModule.QUEST).get(0);
    }

    private static void language(String locale) {
        var values = locale.equals("zh")
                ? Map.of("example.archive", "%s档案", "item.minecraft.iron_ingot", "铁锭", "example.archive.tail", "！")
                : Map.of("example.archive", "%s archive", "item.minecraft.iron_ingot", "Iron ingot", "example.archive.tail", "!");
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
