package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
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
            var tags = ForgeRegistries.ITEMS.tags();
            if (tagId != null && tags != null) {
                TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
                for (Item taggedItem : tags.getTag(tagKey)) {
                    if (taggedItem != Items.AIR) return taggedItem;
                }
            }
        }

        return objective.getTargetId() == null
                ? null
                : ForgeRegistries.ITEMS.getValue(objective.getTargetId());
    }
}
