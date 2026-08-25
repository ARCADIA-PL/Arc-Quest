package org.arcadia.arc_quest.dialogue.network;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.core.identity.PlayerSessionRef;
import org.arcadia.arc_quest.dialogue.runtime.DialogueSessionManager;
import org.arcadia.arc_quest.questplayer.PlayerSessionEpochManager;
import org.arcadia.arc_quest.sync.RequestIdempotencyStore;

import java.util.UUID;
/**
 * 客户端→服务端：玩家选择对话选项 / 自动跳转请求。
 */
public final class C2SDialogueChoicePacket implements CustomPacketPayload {

    public static final int AUTO_ADVANCE = -1;
    public static final int CLOSE = -2;
    public static final int RESTORE_DIALOGUE = -3;
    public static final Type<C2SDialogueChoicePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "dialogue_choice"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SDialogueChoicePacket> STREAM_CODEC =
            StreamCodec.ofMember(C2SDialogueChoicePacket::encode, C2SDialogueChoicePacket::decode);

    private static final UUID LEGACY_SESSION_ID = new UUID(0L, 0L);
    /**
     * -1 = 自动跳转请求, -2 = 关闭对话, >=0 = 选择索引。
     */
    private final int choiceIndex;
    private final UUID sessionId;
    private final long expectedRevision;
    private final String expectedNodeId;
    private final String expectedChoiceId;
    private final long playerSessionEpoch;
    private final UUID requestId;

    public C2SDialogueChoicePacket(int choiceIndex) {
        this(choiceIndex, LEGACY_SESSION_ID, 0L, "", "", 0L, UUID.randomUUID());
    }

    public C2SDialogueChoicePacket(int choiceIndex, UUID sessionId, long expectedRevision,
                                   String expectedNodeId, String expectedChoiceId,
                                   long playerSessionEpoch) {
        this(choiceIndex, sessionId, expectedRevision, expectedNodeId, expectedChoiceId,
                playerSessionEpoch, UUID.randomUUID());
    }

    public C2SDialogueChoicePacket(int choiceIndex, UUID sessionId, long expectedRevision,
                                   String expectedNodeId, String expectedChoiceId,
                                   long playerSessionEpoch, UUID requestId) {
        this.choiceIndex = choiceIndex;
        this.sessionId = sessionId != null ? sessionId : LEGACY_SESSION_ID;
        this.expectedRevision = expectedRevision;
        this.expectedNodeId = expectedNodeId != null ? expectedNodeId : "";
        this.expectedChoiceId = expectedChoiceId != null ? expectedChoiceId : "";
        this.playerSessionEpoch = playerSessionEpoch;
        this.requestId = requestId != null ? requestId : RequestIdempotencyStore.LEGACY_REQUEST_ID;
    }

    public static C2SDialogueChoicePacket autoAdvance() {
        return new C2SDialogueChoicePacket(AUTO_ADVANCE);
    }

    public static C2SDialogueChoicePacket close() {
        return new C2SDialogueChoicePacket(CLOSE);
    }

    public static C2SDialogueChoicePacket restore() {
        return new C2SDialogueChoicePacket(RESTORE_DIALOGUE);
    }

    public static C2SDialogueChoicePacket choice(int choiceIndex, UUID sessionId, long expectedRevision,
                                                  String expectedNodeId, String expectedChoiceId,
                                                  long playerSessionEpoch) {
        return new C2SDialogueChoicePacket(choiceIndex, sessionId, expectedRevision,
                expectedNodeId, expectedChoiceId, playerSessionEpoch);
    }

    public static C2SDialogueChoicePacket autoAdvance(UUID sessionId, long expectedRevision,
                                                       String expectedNodeId, long playerSessionEpoch) {
        return new C2SDialogueChoicePacket(AUTO_ADVANCE, sessionId, expectedRevision,
                expectedNodeId, "", playerSessionEpoch);
    }

    public static C2SDialogueChoicePacket close(UUID sessionId, long expectedRevision, long playerSessionEpoch) {
        return new C2SDialogueChoicePacket(CLOSE, sessionId, expectedRevision, "", "", playerSessionEpoch);
    }

    public static C2SDialogueChoicePacket restore(UUID sessionId, long expectedRevision,
                                                   String expectedNodeId, long playerSessionEpoch) {
        return new C2SDialogueChoicePacket(RESTORE_DIALOGUE, sessionId, expectedRevision,
                expectedNodeId, "", playerSessionEpoch);
    }

    // ── 序列化 ──

    public static C2SDialogueChoicePacket decode(FriendlyByteBuf buf) {
        return new C2SDialogueChoicePacket(
                buf.readVarInt(),
                buf.readUUID(),
                buf.readLong(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readLong(),
                buf.readUUID()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SDialogueChoicePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }

            boolean legacy = LEGACY_SESSION_ID.equals(pkt.sessionId) && pkt.playerSessionEpoch == 0L;
            if (legacy) {
                switch (pkt.choiceIndex) {
                    case CLOSE -> DialogueSessionManager.INSTANCE.endDialogue(player);
                    case AUTO_ADVANCE -> DialogueSessionManager.INSTANCE.handleAutoAdvance(player);
                    case RESTORE_DIALOGUE -> DialogueSessionManager.INSTANCE.handleRestoreDialogue(player);
                    default -> DialogueSessionManager.INSTANCE.handleChoice(player, pkt.choiceIndex);
                }
                return;
            }

            if (!PlayerSessionEpochManager.matches(player, pkt.playerSessionEpoch)) {
                ArcQuestLog.warn(ArcQuestLog.Category.DIALOGUE_NETWORK, "Rejected stale player session: player={}, requestId={}, epoch={}",
                        player.getUUID(), pkt.requestId, pkt.playerSessionEpoch);
                return;
            }

            PlayerSessionRef playerSessionRef = new PlayerSessionRef(player.getUUID(), pkt.playerSessionEpoch);
            if (!RequestIdempotencyStore.INSTANCE.claim(playerSessionRef, pkt.requestId)) {
                ArcQuestLog.debug(ArcQuestLog.Category.DIALOGUE_NETWORK, "Ignored duplicate request: player={}, requestId={}, sessionId={}",
                        player.getUUID(), pkt.requestId, pkt.sessionId);
                return;
            }

            switch (pkt.choiceIndex) {
                case CLOSE -> DialogueSessionManager.INSTANCE.handleClose(
                        player, pkt.sessionId, pkt.expectedRevision, pkt.playerSessionEpoch);
                case AUTO_ADVANCE -> DialogueSessionManager.INSTANCE.handleAutoAdvance(
                        player, pkt.sessionId, pkt.expectedRevision, pkt.expectedNodeId, pkt.playerSessionEpoch);
                case RESTORE_DIALOGUE -> DialogueSessionManager.INSTANCE.handleRestoreDialogue(
                        player, pkt.sessionId, pkt.expectedRevision, pkt.expectedNodeId, pkt.playerSessionEpoch);
                default -> DialogueSessionManager.INSTANCE.handleChoice(
                        player, pkt.choiceIndex, pkt.sessionId, pkt.expectedRevision,
                        pkt.expectedNodeId, pkt.expectedChoiceId, pkt.playerSessionEpoch);
            }
        });
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(choiceIndex);
        buf.writeUUID(sessionId);
        buf.writeLong(expectedRevision);
        buf.writeUtf(expectedNodeId);
        buf.writeUtf(expectedChoiceId);
        buf.writeLong(playerSessionEpoch);
        buf.writeUUID(requestId);
    }
    public UUID getRequestId() {
        return requestId;
    }

    public int getChoiceIndex() {
        return choiceIndex;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public long getExpectedRevision() {
        return expectedRevision;
    }

    public String getExpectedNodeId() {
        return expectedNodeId;
    }

    public String getExpectedChoiceId() {
        return expectedChoiceId;
    }

    public long getPlayerSessionEpoch() {
        return playerSessionEpoch;
    }
}
