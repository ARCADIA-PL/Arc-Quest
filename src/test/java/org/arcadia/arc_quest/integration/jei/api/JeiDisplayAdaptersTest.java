package org.arcadia.arc_quest.integration.jei.api;

import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import org.arcadia.arc_quest.testsupport.ItemStackTestData;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.quest.api.IReward;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.quest.reward.CommandReward;
import org.arcadia.arc_quest.quest.reward.ItemReward;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.arcadia.arc_quest.trade.offer.CommandTradeOffer;
import org.arcadia.arc_quest.trade.offer.CompositeTradeOffer;
import org.arcadia.arc_quest.trade.offer.ItemTradeOffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class JeiDisplayAdaptersTest {
    @BeforeAll static void bootstrapMinecraftRegistries() {
        MinecraftRegistryTestBootstrap.initialize();
    }

    @Test void templateNbtAndNameArePreservedButNeitherSourceNorReturnedStacksCanMutateTheSnapshot() {
        var template = new ItemStack(Items.DIAMOND, 12);
        ItemStackTestData.setString(template, "test_payload", "original");
        template.set(DataComponents.CUSTOM_NAME, Component.literal("Named gem"));
        var offer = new ItemTradeOffer(template, 7, true, null);
        ItemStackTestData.setString(template, "test_payload", "modified source");
        var ingredient = JeiDisplayAdapters.offer(offer, null, true).ingredients().get(0);
        assertEquals(7, ingredient.amount());
        assertTrue(ingredient.consumed());
        assertTrue(offer.requiresExactNbt());
        assertTrue(ingredient.exactNbt());
        ItemStack returned = ingredient.alternatives().get(0);
        assertEquals(1, returned.getCount());
        assertEquals("Named gem", returned.getHoverName().getString());
        assertEquals("original", ItemStackTestData.read(returned).getString("test_payload"));
        ItemStackTestData.setString(returned, "test_payload", "modified snapshot");
        returned.setCount(60);
        assertEquals("original", ItemStackTestData.read(ingredient.alternatives().get(0)).getString("test_payload"));
        assertEquals(1, ingredient.alternatives().get(0).getCount());
        assertEquals("original", ItemStackTestData.read(offer.getDisplayStacks().get(0)).getString("test_payload"));
        assertEquals(7, ((TranslatableContents) ingredient.description().getContents()).getArgs()[1]);
    }

    @Test void templateCostsMatchOnlyTheConfiguredNbtWhilePlainCostsAcceptVariants() {
        var tokenB = new ItemStack(Items.PAPER);
        ItemStackTestData.setString(tokenB, "token", "B");
        var tokenA = new ItemStack(Items.PAPER);
        ItemStackTestData.setString(tokenA, "token", "A");
        var exact = JeiDisplayAdapters.offer(new ItemTradeOffer(tokenB, 100, true, null), null, true).ingredients().get(0);
        assertTrue(exact.exactNbt());
        assertTrue(exact.matchesInput(tokenB));
        tokenB.setCount(64);
        assertTrue(exact.matchesInput(tokenB));
        assertFalse(exact.matchesInput(tokenA));
        assertFalse(exact.matchesInput(new ItemStack(Items.PAPER)));
        var plainOffer = ItemTradeOffer.cost(Items.PAPER, 100);
        var plain = JeiDisplayAdapters.offer(plainOffer, null, true).ingredients().get(0);
        assertFalse(plainOffer.requiresExactNbt());
        assertFalse(plain.exactNbt());
        assertTrue(plain.matchesInput(tokenA));
        assertTrue(plain.matchesInput(tokenB));
    }

    @Test void evenAnUntaggedStackTemplateRequiresExactTagsButOutputMatchingIsNotAnInputConstraint() {
        var plainStack = new ItemStack(Items.PAPER);
        var namedStack = new ItemStack(Items.PAPER);
        namedStack.set(DataComponents.CUSTOM_NAME, Component.literal("Special paper"));
        var templateCost = new ItemTradeOffer(plainStack, 2, true, null);
        var input = JeiDisplayAdapters.offer(templateCost, null, true).ingredients().get(0);
        assertTrue(templateCost.requiresExactNbt());
        assertTrue(input.exactNbt());
        assertTrue(input.matchesInput(plainStack));
        assertFalse(input.matchesInput(namedStack));
        var templateReward = new ItemTradeOffer(namedStack, 2, false, null);
        var output = JeiDisplayAdapters.offer(templateReward, null, false).ingredients().get(0);
        assertTrue(templateReward.requiresExactNbt());
        assertFalse(output.exactNbt());
        assertEquals("Special paper", output.alternatives().get(0).getHoverName().getString());
    }

    @Test void clientPreviewNeverInvokesTheServerQuantityResolver() {
        AtomicInteger calls = new AtomicInteger();
        var offer = ItemTradeOffer.cost(Items.APPLE, 3, player -> {
            calls.incrementAndGet();
            throw new AssertionError("Server quantity callback must not run without a server player");
        });
        var ingredient = JeiDisplayAdapters.offer(offer, null, true).ingredients().get(0);
        assertEquals(3, ingredient.amount());
        assertEquals(3, ((TranslatableContents) ingredient.description().getContents()).getArgs()[1]);
        assertEquals(0, calls.get());
    }

    @Test void invalidTagRewardAndRoleMismatchFailClosedBeforeTagLookup() {
        var tag = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("test", "possible_rewards"));
        assertThrows(IllegalArgumentException.class, () -> JeiDisplayAdapters.offer(ItemTradeOffer.rewardTag(tag, 2), null, false));
        assertThrows(IllegalArgumentException.class, () -> JeiDisplayAdapters.offer(ItemTradeOffer.cost(Items.DIAMOND, 2), null, false));
        assertThrows(IllegalArgumentException.class, () -> JeiDisplayAdapters.offer(ItemTradeOffer.reward(Items.DIAMOND, 2), null, true));
    }

    @Test void unresolvedCostTagsRetainTheRequirementWithoutFabricatingAMatchingItem() {
        var tag = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("test", "unbound_cost"));
        var ingredient = JeiDisplayAdapters.offer(ItemTradeOffer.costTag(tag, 5), null, true).ingredients().get(0);
        assertTrue(ingredient.alternatives().isEmpty());
        assertTrue(ingredient.consumed());
        assertFalse(ingredient.exactNbt());
        assertEquals(5, ingredient.amount());
        Component name = (Component) ((TranslatableContents) ingredient.description().getContents()).getArgs()[0];
        assertEquals("Unbound Cost (Test)", name.getString());
        assertEquals("tag.item.test.unbound_cost", ((TranslatableContents) name.getContents()).getKey());
    }

    @Test void compositesRemainAllRequiredAndAnInvalidChildCannotReturnPartialOutput() {
        var cost = new CompositeTradeOffer(List.of(ItemTradeOffer.cost(Items.DIAMOND, 2),
                new CompositeTradeOffer(List.of(ItemTradeOffer.cost(Items.APPLE, 3), new EffectOnlyOffer()))));
        var presentation = JeiDisplayAdapters.offer(cost, null, true);
        assertEquals(2, presentation.ingredients().size());
        assertEquals(2, presentation.ingredients().get(0).amount());
        assertEquals(3, presentation.ingredients().get(1).amount());
        assertTrue(presentation.ingredients().stream().allMatch(JeiIngredient::consumed));
        assertEquals(1, presentation.notes().size());
        var invalid = new CompositeTradeOffer(List.of(ItemTradeOffer.reward(Items.DIAMOND, 2),
                ItemTradeOffer.cost(Items.APPLE, 3)));
        assertThrows(IllegalArgumentException.class, () -> JeiDisplayAdapters.offer(invalid, null, false));
    }

    @Test void malformedCompositeCyclesAreRejectedButRepeatedValidOffersRemainSeparate() {
        List<ITradeOffer> children = new ArrayList<>();
        var cyclic = new CompositeTradeOffer(children);
        children.add(cyclic);
        assertThrows(IllegalArgumentException.class, () -> JeiDisplayAdapters.offer(cyclic, null, true));
        var repeated = ItemTradeOffer.cost(Items.APPLE, 2);
        assertEquals(2, JeiDisplayAdapters.offer(CompositeTradeOffer.of(repeated, repeated), null, true).ingredients().size());
    }

    @Test void invalidCompositeIsRejectedBeforeInvokingAnEarlierExtensionAdapter() {
        AtomicInteger calls = new AtomicInteger();
        JeiDisplayAdapters.registerOffer(ValidationProbeOffer.class, (offer, player, consumed) -> {
            calls.incrementAndGet();
            return JeiDisplayAdapters.Presentation.empty();
        });
        var tag = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("test", "invalid_reward"));
        var composite = CompositeTradeOffer.of(new ValidationProbeOffer(), ItemTradeOffer.rewardTag(tag, 1));
        assertThrows(IllegalArgumentException.class, () -> JeiDisplayAdapters.offer(composite, null, false));
        assertEquals(0, calls.get());
    }

    @Test void commandsAndDecorativeIconsNeverCreateInferredItemOutputs() {
        var trade = JeiDisplayAdapters.offer(new CommandTradeOffer("give @s minecraft:diamond 64"), null, false);
        var reward = JeiDisplayAdapters.reward(new CommandReward("give @s minecraft:diamond 64"), null);
        assertTrue(trade.ingredients().isEmpty());
        assertTrue(reward.ingredients().isEmpty());
        assertFalse(trade.notes().isEmpty());
        assertFalse(reward.notes().isEmpty());
        assertTrue(JeiDisplayAdapters.offer(new EffectOnlyOffer(), null, false).ingredients().isEmpty());
    }

    @Test void presentationNotesAreCopiedOnConstructionAndOnRead() {
        MutableComponent original = Component.literal("original");
        var presentation = new JeiDisplayAdapters.Presentation(List.of(), List.of(original));
        original.append(" source mutation");
        ((MutableComponent) presentation.notes().get(0)).append(" returned mutation");
        assertEquals("original", presentation.notes().get(0).getString());
        assertThrows(UnsupportedOperationException.class, () -> presentation.notes().clear());
    }

    @Test void ingredientAlternativesAreOrChoicesWithIndependentNbtAndAmount() {
        var apple = new ItemStack(Items.APPLE, 12);
        var pearRepresentation = new ItemStack(Items.GOLDEN_APPLE, 1);
        var alternatives = new ArrayList<>(List.of(apple, pearRepresentation));
        var ingredient = new JeiIngredient(alternatives, 20, true, Component.literal("any accepted fruit"));
        alternatives.clear();
        apple.setCount(0);
        assertEquals(2, ingredient.alternatives().size());
        assertEquals(20, ingredient.amount());
        assertTrue(ingredient.alternatives().stream().allMatch(stack -> stack.getCount() == 1));
    }

    @Test void itemRewardsUseRealItemsAndDoNotInvokeGrant() {
        var presentation = JeiDisplayAdapters.reward(new ItemReward(Items.DIAMOND, 17), null);
        assertEquals(17, presentation.ingredients().get(0).amount());
        assertFalse(presentation.ingredients().get(0).consumed());
        assertEquals(Items.DIAMOND, presentation.ingredients().get(0).alternatives().get(0).getItem());
        assertThrows(IllegalArgumentException.class, () -> JeiDisplayAdapters.reward(new ItemReward(Items.AIR, 1), null));
    }

    @Test void registeredOfferRewardAndObjectiveAdaptersSupplyTheirOwnReadOnlyMeaning() {
        var gem = new JeiIngredient(List.of(new ItemStack(Items.DIAMOND)), 4, false, Component.literal("custom gem reward"));
        JeiDisplayAdapters.registerOffer(CustomOffer.class, (offer, player, consumed) -> {
            assertSame(offer, offer.expected); assertNull(player); assertFalse(consumed);
            return new JeiDisplayAdapters.Presentation(List.of(gem), List.of());
        });
        JeiDisplayAdapters.registerReward(CustomReward.class, (reward, player) ->
                new JeiDisplayAdapters.Presentation(List.of(gem), List.of()));
        var type = new ObjectiveType(ResourceLocation.fromNamespaceAndPath("test", "adapter_objective"),
                true, false, "test.objective", true, "item");
        JeiDisplayAdapters.registerObjective(type.getId(), (objective, player) ->
                new JeiDisplayAdapters.Presentation(List.of(gem), List.of()));
        assertEquals(4, JeiDisplayAdapters.offer(new CustomOffer(), null, false).ingredients().get(0).amount());
        assertEquals(4, JeiDisplayAdapters.reward(new CustomReward(), null).ingredients().get(0).amount());
        var objective = new ObjectiveEntry(type, ResourceLocation.fromNamespaceAndPath("test", "target"),
                1, Component.literal("Custom target"), false, false, Map.of());
        assertEquals(4, JeiDisplayAdapters.objective(objective, null).ingredients().get(0).amount());
    }

    @Test void unregisteredCustomObjectivesDoNotInferItemsFromTargetIds() {
        var objective = new ObjectiveEntry(ObjectiveType.CUSTOM, ResourceLocation.fromNamespaceAndPath("minecraft", "diamond"),
                1, Component.literal("Decorative target"), false, false, Map.of());
        assertTrue(JeiDisplayAdapters.objective(objective, null).ingredients().isEmpty());
    }

    private static class EffectOnlyOffer implements ITradeOffer {
        public boolean canAfford(ServerPlayer player) { throw new AssertionError("Adapter cannot test payments"); }
        public void execute(ServerPlayer player) { throw new AssertionError("Adapter cannot execute offers"); }
        public Component describe() { return Component.literal("Custom effect"); }
        public ResourceLocation getIcon() { return ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/diamond.png"); }
        public String getType() { return "test_effect"; }
    }
    private static final class CustomOffer extends EffectOnlyOffer { final CustomOffer expected = this; }
    private static final class ValidationProbeOffer extends EffectOnlyOffer {}
    private static final class CustomReward implements IReward {
        public void grant(ServerPlayer player) { throw new AssertionError("Adapter cannot grant rewards"); }
        public String describe() { return "Custom reward"; }
    }
}
