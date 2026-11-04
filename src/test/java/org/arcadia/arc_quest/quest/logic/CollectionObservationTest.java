package org.arcadia.arc_quest.quest.logic;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.CollectionEntryBuilder;
import org.arcadia.arc_quest.quest.data.CollectionRecordState;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

class CollectionObservationTest {
    @BeforeAll
    static void initialize() {
        MinecraftRegistryTestBootstrap.initialize();
    }

    private static CollectionSubjectSpec subject() {
        return new CollectionSubjectSpec(
                ResourceLocation.parse("arc_quest:item_stack"),
                ResourceLocation.parse("minecraft:diamond"),
                1,
                "{gem:royalty,quality:2b}",
                new StackMatchSpec(StackMatchSpec.Mode.NBT_SUBSET, "{gem:royalty,quality:2b}"),
                Map.of());
    }

    @Test
    void typedSubsetIgnoresExtraFieldsButRejectsWrongNbtTypesAndOrderedLists() {
        var expected = subject().resolveStack();
        var match = subject().identity();
        var actual = expected.copy();
        actual.getOrCreateTag().putString("unrelated", "extra");
        assertTrue(match.matches(expected, actual));
        actual.getOrCreateTag().putInt("quality", 2);
        assertFalse(match.matches(expected, actual));
        var listExpected = new ItemStack(Items.DIAMOND);
        listExpected.setTag(StackMatchSpec.parse("{values:[1,2]}"));
        var reversed = new ItemStack(Items.DIAMOND);
        reversed.setTag(StackMatchSpec.parse("{values:[2,1]}"));
        assertFalse(
                new StackMatchSpec(StackMatchSpec.Mode.NBT_SUBSET, "")
                        .matches(listExpected, reversed));
    }

    @Test
    void actualQualityObservationDoesNotNeedOrFabricateBindingCompletion() {
        var rule = new CollectionObservationRule("royalty_rare", 1, subject(), "quality/rare");
        var entry =
                CollectionEntryBuilder.create("example:royalty")
                        .category("gems")
                        .item(Items.DIAMOND)
                        .outcome("quality/rare", "Rare")
                        .outcome("quality/epic", "Epic")
                        .observation(rule)
                        .build();
        var records = new CollectionRecordState();
        assertTrue(
                CollectionOutcomeService.recordObservation(
                        records, entry, rule, subject().resolveStack(), 0, false));
        assertTrue(records.isDiscovered(entry.getEntryId()));
        assertTrue(records.hasOutcome(entry.getEntryId(), "quality/rare"));
        assertFalse(records.hasOutcome(entry.getEntryId(), "quality/epic"));
        assertFalse(
                CollectionOutcomeService.recordObservation(
                        records, entry, rule, subject().resolveStack(), 0, false));
        records.resetQuest("example:book", Set.of(entry.getEntryId()));
        long generation = records.getGeneration(entry.getEntryId());
        assertFalse(
                CollectionOutcomeService.recordObservation(
                        records, entry, rule, subject().resolveStack(), 0, true));
        assertFalse(
                CollectionOutcomeService.recordObservation(
                        records, entry, rule, subject().resolveStack(), generation, false));
        assertTrue(
                CollectionOutcomeService.recordObservation(
                        records, entry, rule, subject().resolveStack(), generation, true));
    }

    @Test
    void undeclaredRuleAndWrongActualStackCannotWriteKnowledge() {
        var rule = new CollectionObservationRule("royalty_rare", 1, subject(), "quality/rare");
        var entry =
                CollectionEntryBuilder.create("example:royalty")
                        .category("gems")
                        .item(Items.DIAMOND)
                        .outcome("quality/rare", "Rare")
                        .build();
        var records = new CollectionRecordState();
        assertFalse(
                CollectionOutcomeService.recordObservation(
                        records, entry, rule, subject().resolveStack(), 0, true));
        var configured = entry.withObservationRules(List.of(rule));
        assertFalse(
                CollectionOutcomeService.recordObservation(
                        records, configured, rule, new ItemStack(Items.DIAMOND), 0, true));
        assertFalse(records.isDiscovered(entry.getEntryId()));
    }
}
