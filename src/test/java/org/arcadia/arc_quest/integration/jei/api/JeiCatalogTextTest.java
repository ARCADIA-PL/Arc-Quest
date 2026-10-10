package org.arcadia.arc_quest.integration.jei.api;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.quest.api.ItemTagNames;
import org.arcadia.arc_quest.integration.jei.network.JeiCatalogCodec;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JeiCatalogTextTest {
    @BeforeAll static void bootstrap() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void vanillaNameAndSingleQuantityNeedNoSupplementalTooltip() {
        var ingredient = JeiIngredient.of(new ItemStack(Items.DIAMOND), 1, false);
        assertTrue(JeiCatalogText.tooltip(row(JeiCatalogEntry.Kind.QUEST_REWARD, List.of()), ingredient, false).isEmpty());
        var trade = new JeiIngredient(List.of(new ItemStack(Items.DIAMOND)), 8, true,
                Component.translatable("arc_quest.trade.item", new ItemStack(Items.DIAMOND).getHoverName(), 8));
        var lines = JeiCatalogText.tooltip(row(JeiCatalogEntry.Kind.TRADE, List.of()), trade, true);
        assertEquals(1, lines.size());
        assertEquals("arc_quest.jei.amount", key(lines.get(0)));
        assertEquals(8, ((TranslatableContents) lines.get(0).getContents()).getArgs()[0]);
        var source = new JeiCatalogEntry("trade", JeiCatalogEntry.Kind.TRADE, Component.literal("Title"),
                List.of(trade), List.of(), List.of(), "shop", "");
        var decoded = JeiCatalogCodec.decode(JeiCatalogCodec.encode(List.of(source))).get(0);
        assertEquals(lines, JeiCatalogText.tooltip(decoded, decoded.inputs().get(0), true), "Wire-normalized names must stay deduplicated");
    }

    @Test void tagAndCustomDescriptionsSurviveWithoutRepeatedTradeCounts() {
        Component tag = ItemTagNames.name(ResourceLocation.fromNamespaceAndPath("minecraft", "logs"));
        var ingredient = new JeiIngredient(List.of(new ItemStack(Items.OAK_LOG)), 2, true,
                Component.translatable("arc_quest.trade.item", tag, 2));
        var lines = JeiCatalogText.tooltip(row(JeiCatalogEntry.Kind.TRADE, List.of()), ingredient, true);
        assertEquals(List.of(tag, Component.translatableWithFallback("arc_quest.jei.amount", "Quantity: %s", 2)), lines);
        Component custom = Component.literal("Only enchanted tools");
        var special = new JeiIngredient(List.of(new ItemStack(Items.IRON_PICKAXE)), 1, true, custom);
        assertEquals(List.of(custom), JeiCatalogText.tooltip(row(JeiCatalogEntry.Kind.TRADE, List.of()), special, true));
    }

    @Test void exactDataAndSubmissionRemainVisibleButOutputDataAddsNoGenericWarning() {
        var requirement = new JeiIngredient(List.of(new ItemStack(Items.DIAMOND)), 1, true, Component.literal("Submit a gem"), true);
        var recipe = row(JeiCatalogEntry.Kind.QUEST_REQUIREMENT, List.of(Component.literal("Submit a gem")));
        var lines = JeiCatalogText.tooltip(recipe, requirement, true);
        assertEquals(List.of("arc_quest.jei.exact_data", "arc_quest.jei.submitted"), lines.stream().map(JeiCatalogTextTest::key).toList());
        assertTrue(JeiCatalogText.tooltip(recipe, requirement, false).isEmpty());
    }

    @Test void emptyTagIsExplainedAndAuthoredNotesArePreservedOnce() {
        Component authored = Component.literal("Bring wood to the ranger");
        Component tag = ItemTagNames.name(ResourceLocation.fromNamespaceAndPath("test", "logs"));
        var empty = new JeiIngredient(List.of(), 1, false, tag);
        var recipe = new JeiCatalogEntry("test", JeiCatalogEntry.Kind.QUEST_REQUIREMENT, Component.literal("Title"),
                List.of(empty), List.of(), List.of(Component.empty(), authored, authored.copy(),
                        Component.translatableWithFallback("arc_quest.jei.quest.phase", "Phase: %s", Component.literal("Title"))), "test", "");
        var notes = JeiCatalogText.notes(recipe);
        assertEquals(2, notes.size());
        assertEquals(authored, notes.get(0));
        assertEquals("arc_quest.jei.missing_items", key(notes.get(1)));
        var decoded = JeiCatalogCodec.decode(JeiCatalogCodec.encode(List.of(recipe))).get(0);
        assertEquals(notes.size(), JeiCatalogText.notes(decoded).size(), "Wire-normalized phase titles must stay deduplicated");
    }

    private static JeiCatalogEntry row(JeiCatalogEntry.Kind kind, List<Component> notes) {
        return new JeiCatalogEntry("test", kind, Component.literal("Title"), List.of(), List.of(), notes, "test", "");
    }
    private static String key(Component text) { return ((TranslatableContents) text.getContents()).getKey(); }
}
