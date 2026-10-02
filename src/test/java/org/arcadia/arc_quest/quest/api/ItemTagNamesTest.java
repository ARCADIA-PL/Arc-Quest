package org.arcadia.arc_quest.quest.api;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.data.sync.CollectionDefinitionSpecExporter;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.spec.PhaseSpec;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.spec.compile.QuestSpecCompiler;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ItemTagNamesTest {
    private static final ResourceLocation LOGS = ResourceLocation.parse("minecraft:logs");
    private final Language original = Language.getInstance();
    @BeforeAll static void setup() { MinecraftRegistryTestBootstrap.initialize(); }
    @AfterEach void restoreLanguage() { Language.inject(original); }

    @Test void componentDefersTranslationAndHonorsTheCurrentResourcePackName() {
        Component label = ItemTagNames.name(LOGS);
        assertEquals("tag.item.minecraft.logs", assertInstanceOf(TranslatableContents.class, label.getContents()).getKey());
        language(Map.of("tag.item.minecraft.logs", "Logs"));
        assertEquals("Logs", label.getString());
        language(Map.of("tag.item.minecraft.logs", "资源包原木名称"));
        assertEquals("资源包原木名称", label.getString());
    }

    @Test void unknownTagsHaveReadableDistinctFallbacksWithoutAnInternalRuleDump() {
        language(Map.of());
        assertEquals("Ancient Planks (Example)", ItemTagNames.name(ResourceLocation.parse("example:ancient_planks")).getString());
        assertEquals("Copper Ingots", ItemTagNames.name(ResourceLocation.parse("forge:ingots/copper")).getString());
        assertEquals("tag.item.c.ingots.copper", ItemTagNames.translationKey(ResourceLocation.parse("c:ingots/copper")));
        assertFalse(ItemTagNames.name(ResourceLocation.parse("example:ancient_planks")).getString().contains("#"));
    }

    @Test void collectAndOfferTagsKeepCountsAndMatchingWhileChangingLocale() {
        language(Map.of("tag.item.minecraft.logs", "Logs", "arc_quest.obj.collect", "Collect %1$s × %2$s", "arc_quest.obj.deliver", "Submit %1$s × %2$s"));
        var collect = ObjectiveBuilder.collectTag(LOGS, 8).build();
        var offer = ObjectiveBuilder.offerTag(LOGS, 5).build();
        assertEquals("Collect Logs × 8", collect.getDisplayText().getString());
        assertEquals("Submit Logs × 5", offer.getDisplayText().getString());
        language(Map.of("tag.item.minecraft.logs", "原木", "arc_quest.obj.collect", "获得 %1$s × %2$s", "arc_quest.obj.deliver", "提交 %1$s × %2$s"));
        assertEquals("获得 原木 × 8", collect.getDisplayText().getString());
        assertEquals("提交 原木 × 5", offer.getDisplayText().getString());
        assertEquals(LOGS, collect.getTargetTagResourceLocation());
        assertEquals(LOGS, offer.getTargetTagResourceLocation());
        assertEquals(8, collect.getRequiredCount());
        assertEquals(5, offer.getRequiredCount());
    }

    @Test void aCopiedDefinitionRebindsTheTagAfterTextArgumentsWereFlattenedByExport() {
        language(Map.of("tag.item.minecraft.logs", "Logs", "arc_quest.obj.collect", "Collect %1$s × %2$s"));
        var exported = new QuestSpec(); exported.id = "example:portable_tag_names"; exported.initialPhaseId = "gather";
        exported.category = QuestCategory.ADVENTURE.getId().toString();
        var phase = new PhaseSpec(); phase.phaseId = "gather";
        phase.objectives.add(CollectionDefinitionSpecExporter.objective(ObjectiveBuilder.collectTag(LOGS, 8).id("logs").build(), null));
        exported.phases.add(phase);
        assertEquals("Logs", exported.phases.get(0).objectives.get(0).displayText.args.get(0));
        language(Map.of("tag.item.minecraft.logs", "原木", "arc_quest.obj.collect", "获得 %1$s × %2$s"));
        var copied = new QuestSpecCompiler().compile(exported).getPhase("gather").getObjectives().get(0);
        assertEquals("获得 原木 × 8", copied.getDisplayText().getString());
        assertEquals(LOGS, copied.getTargetTagResourceLocation());
    }

    @Test void anAuthorsCustomDescriptionDoesNotBecomeAnAutomaticObjectiveLabel() {
        var custom = ObjectiveBuilder.collectTag(LOGS, 8).display(Component.literal("Bring enough firewood for camp")).build();
        assertEquals("Bring enough firewood for camp", custom.getDisplayText().getString());
        var translated = ObjectiveBuilder.offerTag(LOGS, 2).display(Component.translatable("example.custom", "Camp supplies")).build();
        assertEquals("example.custom", assertInstanceOf(TranslatableContents.class, translated.getDisplayText().getContents()).getKey());
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
