package org.arcadia.arc_quest.quest.service;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.ObjectiveItemResolver;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;

public final class QuestOfferService {

    private QuestOfferService() {
    }

    public static OfferSubmitResult submitOffer(ServerPlayer player, String questId, String phaseId, int objectiveIndex, int submitAmount) {
        if (submitAmount <= 0) return OfferSubmitResult.REJECTED;

        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return OfferSubmitResult.REJECTED;

        QuestRuntimeData qdata = data.getActiveQuest(questId);
        if (qdata == null || qdata.getState() != QuestState.ACTIVE || !qdata.isPhaseActive(phaseId)) return OfferSubmitResult.REJECTED;

        var qDef = QuestRegistry.get(ResourceLocation.parse(questId));
        if (qDef == null || (qDef.isCollectionQuest() && !qDef.hasCollectionSheets())) return OfferSubmitResult.REJECTED;

        var phase = qDef.getPhase(phaseId);
        if (phase == null) return OfferSubmitResult.REJECTED;

        if (objectiveIndex < 0 || objectiveIndex >= phase.getObjectives().size()) return OfferSubmitResult.REJECTED;
        ObjectiveEntry obj = phase.getObjectives().get(objectiveIndex);
        if (!isOfferLikeObjective(obj.getType())) return OfferSubmitResult.REJECTED;
        if (phase.hasCollectionSheet()) {
            var projection = org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector
                    .project(qDef, phase, qdata, data.getCollectionRecords());
            var bound = phase.getCollectionSheet().getBindings().stream()
                    .filter(binding -> binding.getObjectiveIds().contains(obj.getObjectiveId())).toList();
            if (!bound.isEmpty() && bound.stream().noneMatch(binding -> {
                var row = projection.binding(binding.getBindingId());
                return row != null && row.revealed();
            })) return OfferSubmitResult.REJECTED;
        }

        int required = QuestProgressHandler.resolveRequiredCount(player, obj, data);
        int current = qdata.getObjectiveProgress(phaseId, objectiveIndex);
        if (current >= required) return OfferSubmitResult.REJECTED;

        int remainNeed = required - current;
        int trySubmit = Math.min(submitAmount, remainNeed);

        java.util.Map<ResourceLocation, Integer> consumedItems = new java.util.LinkedHashMap<>();
        int consumed = consumeOfferItems(player, obj, trySubmit, consumedItems);

        if (consumed <= 0) return OfferSubmitResult.REJECTED;

        int newProgress = current + consumed;
        boolean reached = newProgress >= required;

        QuestProgressHandler.incrementObjective(player, questId, phaseId, objectiveIndex, consumed, required);
        consumedItems.forEach((item, amount) -> org.arcadia.arc_quest.quest.logic.CollectionRecordService
                .dispatch(player, obj.getType(), item, amount));

        QuestRuntimeData after = data.getActiveQuest(questId);
        boolean phaseChanged = (after == null) || !after.isPhaseActive(phaseId);

        return new OfferSubmitResult(true, reached, phaseChanged);
    }

    public static boolean isOfferLikeObjective(ObjectiveType type) {
        return ObjectiveType.OFFER.equals(type) || ObjectiveType.DELIVER.equals(type);
    }

    public static int countOfferable(ServerPlayer player, ObjectiveEntry obj) {
        if (player == null || !ObjectiveItemResolver.isItemObjective(obj)) return 0;
        long total = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (ObjectiveItemResolver.matches(obj, stack)) total += stack.getCount();
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static int consumeOfferItems(ServerPlayer player, ObjectiveEntry objective, int need,
                                         java.util.Map<ResourceLocation, Integer> consumedItems) {
        if (need <= 0) return 0;

        Inventory inv = player.getInventory();
        int left = need;

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!ObjectiveItemResolver.matches(objective, st)) continue;

            int take = Math.min(left, st.getCount());
            consumedItems.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()), take, Integer::sum);
            st.shrink(take);
            left -= take;
            if (left <= 0) break;
        }

        player.containerMenu.broadcastChanges();
        return need - left;
    }

    public record OfferSubmitResult(boolean accepted, boolean objectiveReached, boolean phaseChanged) {
        public static final OfferSubmitResult REJECTED = new OfferSubmitResult(false, false, false);
    }
}
