package org.arcadia.arc_quest.quest.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.reward.*;

import java.util.ArrayList;
import java.util.List;

/** Server-only executable entitlement, captured when eligibility first arises. */
public final class CollectionRewardEntitlement {
    private CollectionRewardEntitlement() { }

    public static CompoundTag capture(CollectionEntryRewardDefinition reward, String questId,
                                      String definitionHash, String phaseId, String bindingId, ResourceLocation entryId) {
        CompoundTag tag = new CompoundTag();
        tag.putString("RewardId", reward.rewardId()); tag.putString("Trigger", reward.trigger().name());
        tag.putString("GrantMode", reward.grantMode().name()); tag.putString("OutcomeId", reward.outcomeId());
        tag.putString("Preview", reward.previewVisibility().name()); tag.putString("QuestId", questId);
        tag.putString("DefinitionHash", definitionHash); tag.putString("PhaseId", phaseId);
        tag.putString("BindingId", bindingId); tag.putString("EntryId", entryId.toString());
        ListTag payload = new ListTag();
        boolean complete = true;
        for (IReward part : reward.rewards()) {
            CompoundTag value = new CompoundTag();
            if (part instanceof ItemReward item) {
                value.putString("Type", "item"); value.putString("Item", BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
                value.putInt("Count", item.getCount());
            } else if (part instanceof FlagReward flag) {
                value.putString("Type", "flag"); value.putString("Flag", flag.getFlag()); value.putBoolean("Set", flag.isSet());
            } else if (part instanceof VariableReward variable) {
                value.putString("Type", "variable"); value.putString("Variable", variable.getVariableName());
                value.putString("Operation", variable.getOperation().name()); value.putInt("Value", variable.getValue());
            } else if (part instanceof CommandReward command) {
                value.putString("Type", "command"); value.putString("Command", command.getCommandTemplate());
            } else {
                value.putString("Type", "callback"); complete = false;
            }
            payload.add(value);
        }
        tag.put("Payload", payload); tag.putBoolean("SelfContained", complete);
        if (!complete && definitionHash.isBlank())
            throw new IllegalStateException("A custom collection reward needs an immutable definition factory: " + reward.rewardId());
        return tag;
    }

    public static CollectionEntryRewardDefinition restore(CompoundTag tag, CollectionRunDefinitionStore store) {
        if (!tag.getBoolean("SelfContained")) {
            QuestDefinition quest = store.resolve(tag.getString("DefinitionHash"), ResourceLocation.parse(tag.getString("QuestId")));
            var entry = quest.getCollectionConfig().getEntry(ResourceLocation.parse(tag.getString("EntryId")));
            var phase = quest.getPhase(tag.getString("PhaseId"));
            List<CollectionEntryRewardDefinition> candidates = new ArrayList<>();
            if (entry != null) candidates.addAll(entry.getRewards());
            if (phase != null && phase.hasCollectionSheet()) {
                var binding = phase.getCollectionSheet().getBinding(tag.getString("BindingId"));
                if (binding != null) candidates.addAll(binding.getRewards());
            }
            return candidates.stream().filter(reward -> reward.rewardId().equals(tag.getString("RewardId")))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Frozen reward provider is unavailable"));
        }
        return definition(tag, false);
    }

    /** Only item presentation is allowed in recipient packets. */
    public static CompoundTag presentation(CompoundTag serverTag) {
        CompoundTag safe = new CompoundTag();
        for (String key : List.of("RewardId", "Trigger", "GrantMode", "OutcomeId", "Preview", "EntryId"))
            safe.putString(key, serverTag.getString(key));
        ListTag items = new ListTag();
        for (Tag part : serverTag.getList("Payload", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) part;
            if (value.getString("Type").equals("item") && ResourceLocation.tryParse(value.getString("Item")) != null && value.getInt("Count") > 0) {
                CompoundTag item = new CompoundTag(); item.putString("Type", "item");
                item.putString("Item", value.getString("Item")); item.putInt("Count", value.getInt("Count")); items.add(item);
            }
        }
        safe.put("Payload", items); safe.putBoolean("SelfContained", true);
        return safe;
    }

    public static CollectionEntryRewardDefinition presentationDefinition(CompoundTag tag) { return definition(tag, true); }
    private static CollectionEntryRewardDefinition definition(CompoundTag tag, boolean presentationOnly) {
        List<IReward> rewards = new ArrayList<>();
        for (Tag part : tag.getList("Payload", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) part;
            String type = value.getString("Type");
            if (presentationOnly && !type.equals("item")) continue;
            switch (type) {
                case "item" -> {
                    var id = ResourceLocation.tryParse(value.getString("Item"));
                    if (id == null || !BuiltInRegistries.ITEM.containsKey(id) || value.getInt("Count") < 1)
                        throw new IllegalStateException("Entitled reward item is unavailable");
                    rewards.add(new ItemReward(BuiltInRegistries.ITEM.get(id), value.getInt("Count")));
                }
                case "flag" -> rewards.add(value.getBoolean("Set") ? FlagReward.set(value.getString("Flag")) : FlagReward.clear(value.getString("Flag")));
                case "variable" -> rewards.add(new VariableReward(value.getString("Variable"), VariableReward.Op.valueOf(value.getString("Operation")), value.getInt("Value")));
                case "command" -> rewards.add(new CommandReward(value.getString("Command")));
                default -> throw new IllegalStateException("Unknown collection reward payload");
            }
        }
        return new CollectionEntryRewardDefinition(tag.getString("RewardId"), CollectionEntryRewardTrigger.valueOf(tag.getString("Trigger")),
                EntryRewardGrantMode.valueOf(tag.getString("GrantMode")), rewards, tag.getString("OutcomeId"),
                CollectionRewardPreviewVisibility.valueOf(tag.getString("Preview")));
    }
}
