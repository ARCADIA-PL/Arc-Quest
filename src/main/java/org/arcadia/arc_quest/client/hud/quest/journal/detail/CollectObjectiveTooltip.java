package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.ObjectiveType;

final class CollectObjectiveTooltip {
    private CollectObjectiveTooltip() {
    }

    static void request(QuestJournalScreen screen, ObjectiveEntry objective, boolean hovered) {
        if (!hovered || !ObjectiveType.COLLECT.equals(objective.getType())) {
            return;
        }

        Item targetItem = resolveTargetItem(objective);
        if (targetItem == null || targetItem == Items.AIR) {
            return;
        }

        screen.setHoveredRewardTooltip(new ItemStack(targetItem));
    }

    private static Item resolveTargetItem(ObjectiveEntry objective) {
        if (objective.hasTargetTag()) {
            ResourceLocation tagId = objective.getTargetTagResourceLocation();
            if (tagId != null) {
                TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
                var taggedItems = BuiltInRegistries.ITEM.getTag(tagKey);
                if (taggedItems.isPresent()) {
                    for (var holder : taggedItems.get()) {
                        Item taggedItem = holder.value();
                        if (taggedItem != Items.AIR) return taggedItem;
                    }
                }
            }
        }

        return objective.getTargetId() == null
                ? null
                : BuiltInRegistries.ITEM.get(objective.getTargetId());
    }
}
