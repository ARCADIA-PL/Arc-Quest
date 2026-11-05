package org.arcadia.arc_quest.quest.service;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.ObjectiveItemResolver;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.quest.logic.CollectionRunAccess;
import org.arcadia.arc_quest.quest.logic.QuestEventSettlement;
import org.arcadia.arc_quest.inventory.PlayerItemAccess;

public final class QuestOfferService {

    private QuestOfferService() {
    }

    public static OfferSubmitResult submitOffer(ServerPlayer player, String questId, String phaseId, int objectiveIndex, int submitAmount) {
        if (submitAmount <= 0) return OfferSubmitResult.REJECTED;

        var offer = resolveOffer(player, questId, phaseId, objectiveIndex);
        if (offer == null) return OfferSubmitResult.REJECTED;
        var data = offer.data();
        var qdata = offer.runtime();
        var obj = offer.objective();
        int required = offer.required();
        int current = offer.current();
        int trySubmit = Math.min(submitAmount, required - current);

        java.util.Map<ResourceLocation, Integer> consumedItems = new java.util.LinkedHashMap<>();
        int consumed = consumeOfferItems(player, obj, qdata, trySubmit, consumedItems);
        if (consumed <= 0) return OfferSubmitResult.REJECTED;

        boolean reached = current + consumed >= required;
        var records = new java.util.LinkedHashMap<ResourceLocation, org.arcadia.arc_quest.quest.logic.CollectionRecordService.EventRules>();
        consumedItems.forEach((item, amount) -> records.put(item, org.arcadia.arc_quest.quest.logic.CollectionRecordService
                .freezeRules(player, obj.getType(), item, false)));
        try (var scope = QuestEventSettlement.begin(player)) {
            QuestProgressHandler.incrementObjective(player, questId, phaseId, objectiveIndex, consumed, required);
            consumedItems.forEach((item, amount) -> org.arcadia.arc_quest.quest.logic.CollectionRecordService
                    .applyFrozen(player, records.get(item), amount));
        }
        QuestRuntimeData after = data.getActiveQuest(questId);
        boolean phaseChanged = (after == null) || !after.isPhaseActive(phaseId);
        return new OfferSubmitResult(true, reached, phaseChanged);
    }

    /** Preview and payment share the frozen objective, visibility and active-phase checks. */
    public static int previewOfferable(ServerPlayer player, String questId, String phaseId, int objectiveIndex) {
        var offer = resolveOffer(player, questId, phaseId, objectiveIndex);
        return offer == null ? 0 : countOfferable(player, offer.objective(), offer.runtime());
    }

    private static ResolvedOffer resolveOffer(ServerPlayer player, String questId, String phaseId, int objectiveIndex) {
        if (player == null) return null;

        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return null;

        QuestRuntimeData qdata = data.getActiveQuest(questId);
        if (qdata == null || qdata.getState() != QuestState.ACTIVE || !qdata.isPhaseActive(phaseId)) return null;

        var qDef = CollectionRunAccess.resolve(player.server, qdata);
        if (qDef == null || (qDef.isCollectionQuest() && !qDef.hasCollectionSheets())) return null;

        var phase = qDef.getPhase(phaseId);
        if (phase == null) return null;

        if (objectiveIndex < 0 || objectiveIndex >= phase.getObjectives().size()) return null;
        ObjectiveEntry obj = phase.getObjectives().get(objectiveIndex);
        if (!isOfferLikeObjective(obj.getType())) return null;
        if (phase.hasCollectionSheet()) {
            if (qdata.getCollectionData() != null && phase.getCollectionSheet().getBindings().stream().anyMatch(binding ->
                    binding.getObjectiveIds().contains(obj.getObjectiveId()) && (qdata.getCollectionData().isSheetSettled(phaseId)
                    || qdata.getCollectionData().isBindingComplete(phaseId, binding.getBindingId()))))
                return null;
            var projection = org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector
                    .project(qDef, phase, qdata, data.getCollectionRecords());
            var bound = phase.getCollectionSheet().getBindings().stream()
                    .filter(binding -> binding.getObjectiveIds().contains(obj.getObjectiveId())).toList();
            if (!bound.isEmpty() && bound.stream().noneMatch(binding -> {
                var row = projection.binding(binding.getBindingId());
                return row != null && row.revealed();
            })) return null;
        }

        int required = QuestProgressHandler.resolveRequiredCount(player, obj, data);
        int current = qdata.getObjectiveProgress(phaseId, objectiveIndex);
        if (current >= required) return null;

        return new ResolvedOffer(data, qdata, obj, required, current);
    }

    private record ResolvedOffer(ArcQuestPlayer data, QuestRuntimeData runtime, ObjectiveEntry objective,
                                 int required, int current) {}

    public static boolean isOfferLikeObjective(ObjectiveType type) {
        return ObjectiveType.OFFER.equals(type) || ObjectiveType.DELIVER.equals(type);
    }

    public static int countOfferable(ServerPlayer player, ObjectiveEntry obj) {
        return countOfferable(player, obj, null);
    }

    public static int countOfferable(ServerPlayer player, ObjectiveEntry obj, QuestRuntimeData runtime) {
        if (player == null || !ObjectiveItemResolver.isItemObjective(obj)) return 0;
        return PlayerItemAccess.count(player, stack -> ObjectiveItemResolver.matches(obj, stack, runtime));
    }

    private static int consumeOfferItems(ServerPlayer player, ObjectiveEntry objective, QuestRuntimeData runtime, int need,
                                         java.util.Map<ResourceLocation, Integer> consumedItems) {
        if (need <= 0) return 0;

        var payment = PlayerItemAccess.remove(player,
                stack -> ObjectiveItemResolver.matches(objective, stack, runtime), need, true);
        for (var removed : payment.removed())
            consumedItems.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(removed.template().getItem()),
                    (int) removed.amount(), Integer::sum);
        return payment.amount();
    }

    public record OfferSubmitResult(boolean accepted, boolean objectiveReached, boolean phaseChanged) {
        public static final OfferSubmitResult REJECTED = new OfferSubmitResult(false, false, false);
    }
}
