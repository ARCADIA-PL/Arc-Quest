package org.arcadia.arc_quest.quest.service;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.quest.capability.IQuestCapability;
import org.arcadia.arc_quest.quest.capability.QuestCapabilityProvider;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.jetbrains.annotations.Nullable;

public final class QuestOfferService {

    private QuestOfferService() {}

    public static void submitOffer(ServerPlayer player, String questId, String phaseId, int objectiveIndex, int submitAmount) {
        if (submitAmount <= 0) return;

        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        if (cap == null) return;

        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data == null || !data.isPhaseActive(phaseId)) return;

        var qDef = QuestRegistry.get(ResourceLocation.parse(questId));
        if (qDef == null) return;

        var phase = qDef.getPhase(phaseId);
        if (phase == null) return;

        if (objectiveIndex < 0 || objectiveIndex >= phase.getObjectives().size()) return;
        ObjectiveEntry obj = phase.getObjectives().get(objectiveIndex);
        if (obj.getType() != ObjectiveType.OFFER) return;

        int required = Math.max(1, obj.getRequiredCount());
        int current = data.getObjectiveProgress(phaseId, objectiveIndex);
        if (current >= required) return;

        int remainNeed = required - current;
        int trySubmit = Math.min(submitAmount, remainNeed);

        String targetTag = obj.getExtra("target_tag");
        int consumed;
        if (targetTag != null && !targetTag.isEmpty()) {
            consumed = consumeOfferTagItems(player, targetTag, trySubmit);
        } else {
            consumed = consumeOfferItem(player, obj.getTargetId(), trySubmit);
        }

        if (consumed <= 0) return;

        QuestProgressHandler.incrementObjective(player, questId, phaseId, objectiveIndex, consumed);
    }

    public static int countOfferable(ServerPlayer player, ObjectiveEntry obj) {
        String targetTag = obj.getExtra("target_tag");
        if (targetTag != null && !targetTag.isEmpty()) {
            return countByTag(player, targetTag);
        }
        return countByItemId(player, obj.getTargetId());
    }

    private static int consumeOfferItem(ServerPlayer player, ResourceLocation itemId, int need) {
        if (need <= 0) return 0;
        Item target = ForgeRegistries.ITEMS.getValue(itemId);
        if (target == null) return 0;

        Inventory inv = player.getInventory();
        int left = need;

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty() || st.getItem() != target) continue;

            int take = Math.min(left, st.getCount());
            st.shrink(take);
            left -= take;
            if (left <= 0) break;
        }

        player.containerMenu.broadcastChanges();
        return need - left;
    }

    private static int consumeOfferTagItems(ServerPlayer player, String tagIdStr, int need) {
        if (need <= 0) return 0;
        @Nullable TagKey<Item> tag = parseItemTag(tagIdStr);
        if (tag == null) return 0;

        Inventory inv = player.getInventory();
        int left = need;

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty() || !st.is(tag)) continue;

            int take = Math.min(left, st.getCount());
            st.shrink(take);
            left -= take;
            if (left <= 0) break;
        }

        player.containerMenu.broadcastChanges();
        return need - left;
    }

    private static int countByItemId(ServerPlayer player, ResourceLocation itemId) {
        Item target = ForgeRegistries.ITEMS.getValue(itemId);
        if (target == null) return 0;

        int total = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && st.getItem() == target) total += st.getCount();
        }
        return total;
    }

    private static int countByTag(ServerPlayer player, String tagIdStr) {
        @Nullable TagKey<Item> tag = parseItemTag(tagIdStr);
        if (tag == null) return 0;

        int total = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && st.is(tag)) total += st.getCount();
        }
        return total;
    }

    @Nullable
    private static TagKey<Item> parseItemTag(String tagIdStr) {
        try {
            ResourceLocation id = ResourceLocation.parse(tagIdStr);
            return TagKey.create(Registries.ITEM, id);
        } catch (Exception ignored) {
            return null;
        }
    }
}