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

class JeiObjectiveCandidateTest {
    @BeforeAll static void initialize() { MinecraftRegistryTestBootstrap.initialize(); }
    @Test void displayedCandidateNarrowsTheAuthorizedTagWithoutChangingItsAmount() {
        var group = new JeiIngredient(List.of(new ItemStack(Items.OAK_LOG), new ItemStack(Items.BIRCH_LOG)), 42, false, Component.literal("logs"));
        var result = JeiScreenIngredients.candidateIngredients(List.of(group), new ItemStack(Items.BIRCH_LOG));
        assertEquals(1, result.size());
        assertEquals(42, result.get(0).amount());
        assertFalse(result.get(0).consumed());
        assertEquals(1, result.get(0).alternatives().size());
        assertTrue(result.get(0).alternatives().get(0).is(Items.BIRCH_LOG));
        assertEquals(2, group.alternatives().size());
    }
    @Test void decorationAndRevokedSourcesCannotCreateItemPermissions() {
        assertTrue(JeiScreenIngredients.candidateIngredients(List.of(), new ItemStack(Items.ZOMBIE_HEAD)).isEmpty());
        var source = new JeiIngredient(List.of(new ItemStack(Items.OAK_LOG)), 3, false, Component.empty());
        assertTrue(JeiScreenIngredients.candidateIngredients(List.of(source), new ItemStack(Items.DIAMOND)).isEmpty());
        assertTrue(JeiScreenIngredients.candidateIngredients(List.of(source), ItemStack.EMPTY).isEmpty());
    }
    @Test void fullTagIdentityIsRequiredForTheCurrentCandidate() {
        ItemStack expected = new ItemStack(Items.IRON_SWORD);
        expected.getOrCreateTag().putString("quest_key", "first");
        var source = new JeiIngredient(List.of(expected), 1, false, Component.empty(), true);
        ItemStack unrelated = new ItemStack(Items.IRON_SWORD);
        unrelated.getOrCreateTag().putString("quest_key", "second");
        assertTrue(JeiScreenIngredients.candidateIngredients(List.of(source), unrelated).isEmpty());
        assertEquals(1, JeiScreenIngredients.candidateIngredients(List.of(source), expected.copy()).size());
    }
    @Test void itemOnlyTargetsAcceptRenamedDisplayStacksButKeepAuthorizedMetadata() {
        var source = new JeiIngredient(List.of(new ItemStack(Items.OAK_LOG)), 12, false, Component.empty(), false);
        var renamed = new ItemStack(Items.OAK_LOG);
        renamed.setHoverName(Component.literal("Decorative name"));
        var result = JeiScreenIngredients.candidateIngredients(List.of(source), renamed);
        assertEquals(1, result.size());
        assertFalse(result.get(0).exactNbt());
        assertFalse(result.get(0).alternatives().get(0).hasCustomHoverName());
    }
}
