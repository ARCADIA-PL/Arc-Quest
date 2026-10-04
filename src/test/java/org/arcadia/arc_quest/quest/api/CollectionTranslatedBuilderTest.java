package org.arcadia.arc_quest.quest.api;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.guide.api.GuideMediaType;
import org.arcadia.arc_quest.quest.builder.CollectionEntryBuilder;
import org.arcadia.arc_quest.quest.builder.CollectionQuestConfigBuilder;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CollectionTranslatedBuilderTest {
    private static final ResourceLocation ENTRY = ResourceLocation.parse("example:translated_builder");
    private static final ResourceLocation IMAGE = ResourceLocation.parse("example:textures/codex/field.png");

    @BeforeAll static void initialize() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void translatedCategoriesKeepDefaultIndicesAndExplicitOrderWhenMixedWithLiteralLabels() {
        var categories = CollectionQuestConfigBuilder.create()
                .category("first", QuestText.translatable("example.category.first"))
                .category("priority", QuestText.translatable("example.category.priority"), -10)
                .category("literal", "example.category.literal")
                .category("literal_priority", "Literal priority", -10)
                .build().getCategories();

        assertEquals(List.of(0, -10, 2, -10), categories.stream()
                .map(CollectionCategoryDefinition::getSortOrder).toList());
        assertTranslated(categories.get(0).getDisplayNameText(), "example.category.first");
        assertTranslated(categories.get(1).getDisplayNameText(), "example.category.priority");
        assertFalse(resolve(categories.get(2).getDisplayNameText()).getContents() instanceof TranslatableContents);
        assertEquals("example.category.literal", resolve(categories.get(2).getDisplayNameText()).getString());
    }

    @Test void textAndImageAcceptTranslatedComponentsWithoutChangingContentDefaults() {
        Component argument = Component.translatable("example.subject.name").withStyle(ChatFormatting.GREEN);
        Component text = Component.translatable("example.archive.notes", argument)
                .append(Component.translatable("example.archive.footer")).withStyle(ChatFormatting.ITALIC);
        QuestText caption = QuestText.translatable("example.archive.caption");
        var entry = CollectionEntryBuilder.create(ENTRY).category("field")
                .text("notes", QuestText.component(text))
                .image("photo", IMAGE, 240, 120, caption).build();

        var notes = entry.getContent().get(0);
        var photo = entry.getContent().get(1);
        assertEquals(text, resolve(notes.text()));
        assertNull(notes.media());
        assertEquals("", resolve(notes.caption()).getString());
        assertSame(caption, photo.caption());
        assertTranslated(photo.caption(), "example.archive.caption");
        assertEquals(GuideMediaType.IMAGE, photo.media().getType());
        assertEquals(IMAGE, photo.media().getTexture());
        assertEquals(240, photo.media().getWidth());
        assertEquals(120, photo.media().getHeight());
        assertFalse(photo.media().isAutoplay());
        assertFalse(photo.media().isLoop());
        for (var block : entry.getContent()) {
            assertEquals(CollectionContentReveal.DISCOVERED, block.reveal());
            assertEquals(CollectionMediaFit.CONTAIN, block.fit());
            assertTrue(block.zoomable());
            assertEquals("", block.revealStepId());
        }
    }

    @Test void existingStringContentOverloadsStillTreatKeyLookingStringsAsLiteralText() {
        var entry = CollectionEntryBuilder.create(ENTRY).category("field")
                .text("notes", "example.archive.notes")
                .image("photo", IMAGE, 16, 16, "example.archive.caption").build();
        assertEquals("example.archive.notes", resolve(entry.getContent().get(0).text()).getString());
        assertFalse(resolve(entry.getContent().get(0).text()).getContents() instanceof TranslatableContents);
        assertEquals("example.archive.caption", resolve(entry.getContent().get(1).caption()).getString());
        assertFalse(resolve(entry.getContent().get(1).caption()).getContents() instanceof TranslatableContents);
    }

    @Test void translatedEntryLabelsCluesOutcomesAndDiscoveryTextRemainComponents() {
        var entry = CollectionEntryBuilder.create(ENTRY).category("field")
                .displayName(QuestText.translatable("example.entry.name"))
                .description(QuestText.translatable("example.entry.description"))
                .publicClue(QuestText.translatable("example.entry.clue"))
                .outcome("notes", QuestText.translatable("example.outcome.notes"))
                .discover(ObjectiveBuilder.custom(ENTRY, 1).id("discovery")
                        .display(QuestText.translatable("example.objective.discovery"))).build();
        assertTranslated(entry.getDisplayQuestText(), "example.entry.name");
        assertTranslated(entry.getDescriptionQuestText(), "example.entry.description");
        assertTranslated(entry.getPublicClueText(), "example.entry.clue");
        assertTranslated(entry.getOutcome("notes").displayName(), "example.outcome.notes");
        assertTranslated(entry.getDiscoveryObjectives().get(0).getDisplayQuestText(), "example.objective.discovery");
        assertTrue(entry.isUnifiedGameplay());
    }

    private static Component resolve(QuestText text) { return text.resolve(null, QuestTextContext.empty()); }
    private static void assertTranslated(QuestText text, String key) {
        var contents = assertInstanceOf(TranslatableContents.class, resolve(text).getContents());
        assertEquals(key, contents.getKey());
    }
}
