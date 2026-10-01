package org.arcadia.arc_quest.quest.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ObjectiveItemResolverTest {
    @BeforeAll static void bootstrap() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void itemObjectivesShareIdentityMatchingAndFreshCountOneCandidates() {
        for (ObjectiveType type : List.of(ObjectiveType.COLLECT, ObjectiveType.CRAFT, ObjectiveType.OFFER, ObjectiveType.DELIVER)) {
            var objective = objective(type, "minecraft:apple", Map.of());
            var named = new ItemStack(Items.APPLE, 17);
            named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Custom apple"));
            org.arcadia.arc_quest.testsupport.ItemStackTestData.setString(named, "variant", "custom");
            assertTrue(ObjectiveItemResolver.matches(objective, named));
            assertTrue(ObjectiveItemResolver.matches(objective, ResourceLocation.parse("minecraft:apple")));
            assertFalse(ObjectiveItemResolver.matches(objective, new ItemStack(Items.STICK)));
            assertFalse(ObjectiveItemResolver.matches(objective, ItemStack.EMPTY));
            var candidates = ObjectiveItemResolver.candidates(objective);
            assertEquals(1, candidates.size());
            assertEquals(1, candidates.get(0).getCount());
            candidates.get(0).setCount(99);
            org.arcadia.arc_quest.testsupport.ItemStackTestData.setString(candidates.get(0), "mutated", "true");
            var fresh = ObjectiveItemResolver.candidates(objective).get(0);
            assertEquals(1, fresh.getCount());
            assertFalse(fresh.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA));
            assertEquals(List.of(ResourceLocation.parse("minecraft:apple")), ObjectiveItemResolver.targetIds(objective));
        }
    }

    @Test void missingAndInvalidTagsNeverFallBackToAValidItemTarget() {
        for (String tag : List.of("arc_quest:missing_test_tag", "Invalid Tag!")) {
            var objective = objective(ObjectiveType.COLLECT, "minecraft:apple", Map.of("target_tag", tag));
            assertTrue(ObjectiveItemResolver.candidates(objective).isEmpty());
            assertTrue(ObjectiveItemResolver.targetIds(objective).isEmpty());
            assertFalse(ObjectiveItemResolver.matches(objective, new ItemStack(Items.APPLE)));
            assertFalse(ObjectiveItemResolver.matches(objective, ResourceLocation.parse("minecraft:apple")));
        }
    }

    @Test void nonItemTargetsMissingItemsAndAirProduceNoCandidate() {
        for (ObjectiveEntry objective : List.of(
                objective(ObjectiveType.KILL, "minecraft:apple", Map.of()),
                objective(ObjectiveType.INTERACT, "minecraft:apple", Map.of()),
                objective(ObjectiveType.COLLECT, "arc_quest:missing_test_item", Map.of()),
                objective(ObjectiveType.COLLECT, "minecraft:air", Map.of()))) {
            assertTrue(ObjectiveItemResolver.candidates(objective).isEmpty());
            assertFalse(ObjectiveItemResolver.matches(objective, new ItemStack(Items.APPLE)));
        }
        assertTrue(ObjectiveItemResolver.candidates(null).isEmpty());
        assertFalse(ObjectiveItemResolver.matches(null, new ItemStack(Items.APPLE)));
    }

    private static ObjectiveEntry objective(ObjectiveType type, String target, Map<String, String> extra) {
        return new ObjectiveEntry(type, ResourceLocation.parse(target), 8, QuestText.literal("Test objective"),
                false, false, extra, List.of(), null);
    }
}
