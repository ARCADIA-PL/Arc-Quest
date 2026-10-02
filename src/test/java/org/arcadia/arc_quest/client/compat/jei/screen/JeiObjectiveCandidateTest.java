package org.arcadia.arc_quest.client.compat.jei.screen;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.integration.jei.api.JeiIngredient;
import org.arcadia.arc_quest.client.hud.quest.icon.IconFrameSelection;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconVisual;
import org.arcadia.arc_quest.quest.api.ObjectiveItemResolver;
import org.arcadia.arc_quest.quest.api.icon.ObjectiveIcons;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
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
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, expected, tag -> tag.putString("quest_key", "first"));
        var source = new JeiIngredient(List.of(expected), 1, false, Component.empty(), true);
        ItemStack unrelated = new ItemStack(Items.IRON_SWORD);
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, unrelated, tag -> tag.putString("quest_key", "second"));
        assertTrue(JeiScreenIngredients.candidateIngredients(List.of(source), unrelated).isEmpty());
        assertEquals(1, JeiScreenIngredients.candidateIngredients(List.of(source), expected.copy()).size());
    }
    @Test void itemOnlyTargetsAcceptRenamedDisplayStacksButKeepAuthorizedMetadata() {
        var source = new JeiIngredient(List.of(new ItemStack(Items.OAK_LOG)), 12, false, Component.empty(), false);
        var renamed = new ItemStack(Items.OAK_LOG);
        renamed.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Decorative name"));
        var result = JeiScreenIngredients.candidateIngredients(List.of(source), renamed);
        assertEquals(1, result.size());
        assertFalse(result.get(0).exactNbt());
        assertFalse(result.get(0).alternatives().get(0).has(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
    }

    @Test void explicitItemAndAliasLinkNonItemObjectivesWithoutMaterialSources() {
        var shown = new ItemStack(Items.IRON_SWORD);
        for (var objective : List.of(
                ObjectiveBuilder.kill(EntityType.ZOMBIE, 3).iconItem(Items.IRON_SWORD).build(),
                ObjectiveBuilder.custom(ResourceLocation.parse("test:inspect"), 2).iconTexture(Items.IRON_SWORD).build(),
                ObjectiveBuilder.nullObjective().iconItem(Items.IRON_SWORD).build())) {
            var result = JeiScreenIngredients.objectiveIconIngredients(objective, frame(shown), List.of());
            assertEquals(1, result.size());
            assertEquals(1, result.get(0).amount());
            assertFalse(result.get(0).consumed());
            assertTrue(result.get(0).alternatives().get(0).is(Items.IRON_SWORD));
            assertTrue(ObjectiveItemResolver.candidates(objective).isEmpty());
        }
    }

    @Test void explicitItemTakesPrecedenceOverDifferentRealItemOrTagRequirements() {
        var source = JeiIngredient.of(new ItemStack(Items.OAK_LOG), 42, true);
        for (var objective : List.of(
                ObjectiveBuilder.collect(Items.OAK_LOG, 42).iconItem(Items.CHEST).build(),
                ObjectiveBuilder.collectTag(ResourceLocation.parse("minecraft:logs"), 42).iconItem(Items.CHEST).build())) {
            var result = JeiScreenIngredients.objectiveIconIngredients(objective, frame(new ItemStack(Items.CHEST)), List.of(source));
            assertEquals(1, result.size());
            assertTrue(result.get(0).alternatives().get(0).is(Items.CHEST));
            assertEquals(1, result.get(0).amount());
            assertFalse(result.get(0).consumed());
            assertEquals(42, objective.getRequiredCount());
            assertTrue(source.alternatives().get(0).is(Items.OAK_LOG));
            assertEquals(42, source.amount());
            assertTrue(source.consumed());
        }
    }

    @Test void explicitItemRejectsHiddenMissingEmptyOrMismatchedSelections() {
        var authorized = List.of(JeiIngredient.of(new ItemStack(Items.DIAMOND), 5, false));
        for (var objective : List.of(
                ObjectiveBuilder.collect(Items.DIAMOND, 5).iconItem(Items.CHEST).hidden().build(),
                ObjectiveBuilder.collect(Items.DIAMOND, 5).iconItem("missing_addon:no_item").build(),
                ObjectiveBuilder.collect(Items.DIAMOND, 5).iconItem(Items.AIR).build(),
                ObjectiveBuilder.collect(Items.DIAMOND, 5).iconItem(Items.CHEST).build(),
                ObjectiveBuilder.collect(Items.DIAMOND, 5).noIcon().build())) {
            assertTrue(JeiScreenIngredients.objectiveIconIngredients(objective, frame(new ItemStack(Items.DIAMOND)), authorized).isEmpty());
            assertTrue(JeiScreenIngredients.objectiveIconIngredients(objective, frame(ItemStack.EMPTY), authorized).isEmpty());
        }
    }

    @Test void automaticProviderAndTextureIconsKeepTheirExistingLookupSemantics() {
        var group = new JeiIngredient(List.of(new ItemStack(Items.OAK_LOG), new ItemStack(Items.BIRCH_LOG)), 42, false, Component.literal("logs"));
        var auto = ObjectiveBuilder.collectTag(ResourceLocation.parse("minecraft:logs"), 42).build();
        var selected = frame(new ItemStack(Items.BIRCH_LOG));
        var narrowed = JeiScreenIngredients.objectiveIconIngredients(auto, selected, List.of(group));
        assertEquals(42, narrowed.get(0).amount());
        assertEquals(1, narrowed.get(0).alternatives().size());
        assertTrue(narrowed.get(0).alternatives().get(0).is(Items.BIRCH_LOG));
        assertTrue(JeiScreenIngredients.objectiveIconIngredients(auto, selected, List.of()).isEmpty());
        var provider = ObjectiveBuilder.kill(EntityType.ZOMBIE, 3).icon(ObjectiveIcons.provider("test:portrait")).build();
        assertTrue(JeiScreenIngredients.objectiveIconIngredients(provider, frame(new ItemStack(Items.ZOMBIE_HEAD)), List.of()).isEmpty());
        var texture = ObjectiveBuilder.collectTag(ResourceLocation.parse("minecraft:logs"), 42).iconTexture("test:icon.png").build();
        ObjectiveIconVisual visual = (graphics, x, y, size) -> {};
        var visualFrame = new IconFrameSelection("test/objective", "visual", ItemStack.EMPTY, visual, -1, 0, 0);
        assertEquals(List.of(group), JeiScreenIngredients.objectiveIconIngredients(texture, visualFrame, List.of(group)));
    }

    private static IconFrameSelection frame(ItemStack shown) {
        return new IconFrameSelection("test/objective", "selected", shown, null, 0, 1, 0);
    }
    @Test void currentAndUnpaidPriorRewardsWithOneIdAuthorizeTheirOwnOriginalItems() {
        var current = rewardRow("sample", Items.DIAMOND, 2, false, "current",
                org.arcadia.arc_quest.quest.api.CollectionRewardPreviewVisibility.PUBLIC);
        var prior = rewardRow("sample", Items.EMERALD, 7, true, "prior",
                org.arcadia.arc_quest.quest.api.CollectionRewardPreviewVisibility.UNLOCKED_ONLY);
        var unrelated = rewardRow("other", Items.COAL, 3, true, "prior",
                org.arcadia.arc_quest.quest.api.CollectionRewardPreviewVisibility.PUBLIC);
        var disclosed = List.of(current, prior, unrelated);
        var oldItem = JeiScreenIngredients.collectionEntryRewardIngredients(disclosed, "sample", new ItemStack(Items.EMERALD));
        assertEquals(1, oldItem.size());
        assertEquals(7, oldItem.get(0).amount());
        assertTrue(oldItem.get(0).alternatives().get(0).is(Items.EMERALD));
        var currentItem = JeiScreenIngredients.collectionEntryRewardIngredients(disclosed, "sample", new ItemStack(Items.DIAMOND));
        assertEquals(1, currentItem.size());
        assertEquals(2, currentItem.get(0).amount());
        assertTrue(JeiScreenIngredients.collectionEntryRewardIngredients(disclosed, "sample", new ItemStack(Items.COAL)).isEmpty());
    }

    @Test void unpaidPublicRewardCannotRevealAnotherRunsStillSecretPayload() {
        var secret = rewardRow("sample", Items.NETHERITE_INGOT, 5, false, "current",
                org.arcadia.arc_quest.quest.api.CollectionRewardPreviewVisibility.UNLOCKED_ONLY);
        var disclosed = rewardRow("sample", Items.EMERALD, 7, true, "prior",
                org.arcadia.arc_quest.quest.api.CollectionRewardPreviewVisibility.UNLOCKED_ONLY);
        assertTrue(JeiScreenIngredients.collectionEntryRewardIngredients(List.of(secret, disclosed), "sample",
                new ItemStack(Items.NETHERITE_INGOT)).isEmpty());
        assertEquals(1, JeiScreenIngredients.collectionEntryRewardIngredients(List.of(secret, disclosed), "sample",
                new ItemStack(Items.EMERALD)).size());
        var claimed = new org.arcadia.arc_quest.quest.api.CollectionEntryRewardProgress(secret.definition(), false, true, "completed");
        assertEquals(1, JeiScreenIngredients.collectionEntryRewardIngredients(List.of(claimed), "sample",
                new ItemStack(Items.NETHERITE_INGOT)).size());
    }

    private static org.arcadia.arc_quest.quest.api.CollectionEntryRewardProgress rewardRow(String id,
            net.minecraft.world.item.Item item, int amount, boolean unlocked, String run,
            org.arcadia.arc_quest.quest.api.CollectionRewardPreviewVisibility visibility) {
        var definition = new org.arcadia.arc_quest.quest.api.CollectionEntryRewardDefinition(id,
                org.arcadia.arc_quest.quest.api.CollectionEntryRewardTrigger.BINDING_COMPLETE,
                org.arcadia.arc_quest.quest.api.EntryRewardGrantMode.MANUAL,
                List.of(new org.arcadia.arc_quest.quest.reward.ItemReward(item, amount)), "", visibility);
        return new org.arcadia.arc_quest.quest.api.CollectionEntryRewardProgress(definition, unlocked, false, run);
    }
}
