package org.arcadia.arc_quest.client.hud.quest.journal.detail;

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
        if (!hovered || !ObjectiveType.COLLECT.equals(objective.getType()) || objective.hasTargetTag()
                || objective.getTargetId() == null) {
            return;
        }

        Item targetItem = ForgeRegistries.ITEMS.getValue(objective.getTargetId());
        if (targetItem == null || targetItem == Items.AIR) {
            return;
        }

        screen.setHoveredRewardTooltip(new ItemStack(targetItem));
    }
}
