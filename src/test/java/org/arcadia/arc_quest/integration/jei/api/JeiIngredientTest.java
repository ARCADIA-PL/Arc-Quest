package org.arcadia.arc_quest.integration.jei.api;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JeiIngredientTest {
    @BeforeAll static void bootstrapMinecraftRegistries() {
        MinecraftRegistryTestBootstrap.initialize();
    }

    @Test void ordinaryAndTagExpandedAlternativesAcceptNbtVariantsButNotOtherItems() {
        var ingredient = new JeiIngredient(List.of(new ItemStack(Items.OAK_LOG), new ItemStack(Items.BIRCH_LOG)),
                200, true, Component.literal("logs"));
        var namedOak = new ItemStack(Items.OAK_LOG);
        namedOak.setHoverName(Component.literal("Named oak"));
        var taggedBirch = new ItemStack(Items.BIRCH_LOG);
        taggedBirch.getOrCreateTag().putString("custom", "value");
        assertFalse(ingredient.exactNbt());
        assertTrue(ingredient.matchesInput(namedOak));
        assertTrue(ingredient.matchesInput(taggedBirch));
        assertFalse(ingredient.matchesInput(new ItemStack(Items.STONE)));
        assertFalse(ingredient.matchesInput(ItemStack.EMPTY));
        assertFalse(ingredient.matchesInput(null));
        assertFalse(new JeiIngredient(List.of(), 1, true, Component.literal("unresolved tag")).matchesInput(namedOak));
        assertFalse(JeiIngredient.of(new ItemStack(Items.PAPER), 1, true).exactNbt());
    }

    @Test void exactMatchingCopiesPreserveTheFlagAndTagIdentityWithoutSharingMutableStacks() {
        var original = new ItemStack(Items.PAPER);
        original.getOrCreateTag().putString("token", "B");
        var ingredient = new JeiIngredient(List.of(original), 123, true, Component.literal("token"), true);
        var copy = new JeiIngredient(ingredient.alternatives(), ingredient.amount(), ingredient.consumed(),
                ingredient.description(), ingredient.exactNbt());
        original.getOrCreateTag().putString("token", "A");
        var returned = copy.alternatives().get(0);
        returned.getOrCreateTag().putString("token", "A");
        assertTrue(copy.exactNbt());
        assertTrue(copy.sameContent(ingredient));
        assertFalse(copy.matchesInput(original));
        assertTrue(copy.matchesInput(ingredient.alternatives().get(0)));
        assertFalse(copy.sameContent(new JeiIngredient(copy.alternatives(), copy.amount(), copy.consumed(), copy.description())));
        assertFalse(copy.matchesInput(ItemStack.EMPTY));
        assertFalse(copy.matchesInput(null));
    }
}
