package org.arcadia.arc_quest.quest.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.client.events.ClientHudEvents;
import org.arcadia.arc_quest.client.hud.QuestHudOverlay;
import org.arcadia.arc_quest.client.hud.quest.toast.QuestToastManager;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.SplashType;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class S2CSyncQuestStatePacket implements CustomPacketPayload {

    public static final Type<S2CSyncQuestStatePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "sync_quest_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CSyncQuestStatePacket> STREAM_CODEC =
            StreamCodec.ofMember(S2CSyncQuestStatePacket::encode, S2CSyncQuestStatePacket::decode);

    private final QuestRuntimeData data;
    private final long playerSessionEpoch;
    private final long baseRevision;
    private final long newRevision;

    public S2CSyncQuestStatePacket(QuestRuntimeData data) {
        this(data, 0L, 0L, 0L);
    }

    public S2CSyncQuestStatePacket(QuestRuntimeData data, long playerSessionEpoch,
                                   long baseRevision, long newRevision) {
        this.data = Objects.requireNonNull(data, "data").copy();
        this.playerSessionEpoch = Math.max(0L, playerSessionEpoch);
        this.baseRevision = Math.max(0L, baseRevision);
        this.newRevision = Math.max(0L, newRevision);
    }

    public static void encode(S2CSyncQuestStatePacket pkt, FriendlyByteBuf buf) {
        buf.writeLong(pkt.playerSessionEpoch);
        buf.writeLong(pkt.baseRevision);
        buf.writeLong(pkt.newRevision);
        pkt.data.writeToNetwork(buf);
    }

    public static S2CSyncQuestStatePacket decode(FriendlyByteBuf buf) {
        long playerSessionEpoch = buf.readLong();
        long baseRevision = buf.readLong();
        long newRevision = buf.readLong();
        return new S2CSyncQuestStatePacket(
                QuestRuntimeData.readFromNetwork(buf), playerSessionEpoch, baseRevision, newRevision);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CSyncQuestStatePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!ClientQuestCache.INSTANCE.acceptDelta(
                    pkt.playerSessionEpoch, pkt.baseRevision, pkt.newRevision)) return;
            ResourceLocation questRl = ResourceLocation.tryParse(pkt.data.getQuestId());
            QuestDefinition def = questRl != null ? QuestRegistry.get(questRl) : null;
            Component name = def != null ? def.getDisplayName() : Component.literal(pkt.data.getQuestId());

            QuestRuntimeData previousData = ClientQuestCache.INSTANCE.getActiveQuest(pkt.data.getQuestId());
            Set<String> previousActivePhases = previousData != null
                    ? new LinkedHashSet<>(previousData.getActivePhaseIds()) : Set.of();
            Set<String> previousCompletedPhases = previousData != null
                    ? new LinkedHashSet<>(previousData.getCompletedPhaseIds()) : Set.of();
            boolean isNewQuest = !ClientQuestCache.INSTANCE.isQuestActive(pkt.data.getQuestId());

            ClientQuestCache.INSTANCE.updateQuest(pkt.data);

            Set<String> completedPhases = addedIds(pkt.data.getCompletedPhaseIds(), previousCompletedPhases);
            Set<String> startedPhases = addedIds(pkt.data.getActivePhaseIds(), previousActivePhases);

            switch (pkt.data.getState()) {
                case ACTIVE -> {
                    if (isNewQuest) {
                        QuestToastManager.show(QuestToastManager.ToastType.QUEST_ACCEPTED, name);
                        if (def != null) ClientHudEvents.handleVisualTrigger(def, SplashType.QUEST_ACQUIRED, null);
                    }

                    triggerPhaseVisuals(def, completedPhases, SplashType.PHASE_COMPLETE);
                    triggerPhaseVisuals(def, startedPhases, SplashType.PHASE_START);

                    if (def != null && Minecraft.getInstance().player != null) {
                        Set<String> activePhases = pkt.data.getActivePhaseIds();
                        for (String phaseId : activePhases) {
                            PhaseDefinition phase = def.getPhase(phaseId);
                            if (phase == null || !phase.hasChoices()) continue;

                            int[] progress = pkt.data.getAllProgress(phaseId);
                            boolean allCompleted = true;
                            for (int i = 0; i < phase.getObjectives().size(); i++) {
                                if (i >= progress.length || progress[i] < phase.getObjectives().get(i).getRequiredCount()) {
                                    allCompleted = false;
                                    break;
                                }
                            }

                            if (allCompleted) {
                                QuestHudOverlay.INSTANCE.showBranchChoiceToast(pkt.data.getQuestId(), phaseId);
                                break;
                            }
                        }
                    }
                }
                case COMPLETED -> {
                    triggerPhaseVisuals(def, completedPhases, SplashType.PHASE_COMPLETE);
                    QuestToastManager.show(QuestToastManager.ToastType.QUEST_COMPLETED, name);
                    if (def != null) ClientHudEvents.handleVisualTrigger(def, SplashType.QUEST_COMPLETED, null);
                }
                case FAILED -> {
                }
            }
        });
    }

    private static Set<String> addedIds(Set<String> current, Set<String> previous) {
        Set<String> added = new LinkedHashSet<>(current);
        added.removeAll(previous);
        return added;
    }

    private static void triggerPhaseVisuals(QuestDefinition quest, Set<String> phaseIds, SplashType type) {
        if (quest == null || phaseIds.isEmpty()) return;
        for (String phaseId : phaseIds) {
            ClientHudEvents.handleVisualTrigger(quest, type, phaseId);
        }
    }

    public long getPlayerSessionEpoch() {
        return playerSessionEpoch;
    }

    public long getBaseRevision() {
        return baseRevision;
    }

    public long getNewRevision() {
        return newRevision;
    }
}
