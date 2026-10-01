package org.arcadia.arc_quest.client.compat.jei.screen;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.integration.jei.api.JeiIngredient;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JeiGuideIconIngredientsTest {
    @BeforeAll static void initialize() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void materialCoverQueriesOnlyItsDisplayedAuthorizedAlternative() {
        var source = new JeiIngredient(List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT)),
                5, false, Component.literal("metals"));
        var result = JeiScreenIngredients.guideIconIngredients(List.of(source), new ItemStack(Items.GOLD_INGOT));
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).alternatives().size());
        assertTrue(result.get(0).alternatives().get(0).is(Items.GOLD_INGOT));
        assertEquals(5, result.get(0).amount());
        assertEquals(2, source.alternatives().size());
    }

    @Test void decorativeBookKeepsExplicitAssociationsWithoutInventingBookRecipes() {
        var source = JeiIngredient.of(new ItemStack(Items.IRON_INGOT), 1, false);
        var result = JeiScreenIngredients.guideIconIngredients(List.of(source), new ItemStack(Items.BOOK));
        assertEquals(1, result.size());
        assertTrue(result.get(0).alternatives().get(0).is(Items.IRON_INGOT));
        assertFalse(result.stream().flatMap(ingredient -> ingredient.alternatives().stream())
                .anyMatch(stack -> stack.is(Items.BOOK)));
    }

    @Test void lockedOrRevokedGuideCannotGrantItsDecorativeCover() {
        assertTrue(JeiScreenIngredients.guideIconIngredients(List.of(), new ItemStack(Items.DIAMOND)).isEmpty());
    }

    @Test void explicitVariantMetadataRemainsServerAuthorized() {
        var named = new ItemStack(Items.IRON_SWORD);
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, named, tag -> tag.putString("guide_variant", "authorized"));
        var source = new JeiIngredient(List.of(named), 1, false, Component.empty(), true);
        var result = JeiScreenIngredients.guideIconIngredients(List.of(source), new ItemStack(Items.BOOK));
        assertTrue(result.get(0).exactNbt());
        assertEquals("authorized", result.get(0).alternatives().get(0).getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getString("guide_variant"));
    }
}
