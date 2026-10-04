package org.arcadia.arc_quest.quest.reward;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.quest.api.IReward;

import java.util.Locale;

/** Deferred, server-safe reward labels shared by quest HUDs and recipe integrations. */
public final class QuestRewardText {
    private QuestRewardText() { }

    public static Component describe(IReward reward) {
        if (reward instanceof ItemReward item) {
            Component name = item.getItem() == null ? Component.empty() : new ItemStack(item.getItem()).getHoverName();
            return text("item", name, item.getCount());
        }
        if (reward instanceof FlagReward flag) return text(flag.isSet() ? "flag_set" : "flag_clear", flag.getFlag());
        if (reward instanceof VariableReward variable) return text("variable_" + variable.getOperation().name().toLowerCase(Locale.ROOT),
                variable.getVariableName(), variable.getValue());
        // Commands are executable server data, not player-facing reward descriptions.
        if (reward instanceof CommandReward) return text("command");
        return Component.literal(reward == null ? "" : reward.describe());
    }

    private static Component text(String suffix, Object... arguments) {
        return Component.translatable("arc_quest.reward." + suffix, arguments);
    }
}
