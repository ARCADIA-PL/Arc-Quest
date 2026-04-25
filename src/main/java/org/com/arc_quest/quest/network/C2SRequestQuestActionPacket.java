package org.com.arc_quest.quest.network;

import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import org.com.arc_quest.quest.logic.QuestProgressHandler;
import org.com.arc_quest.quest.network.QuestRejectCodeDictionary.Code;
import org.com.arc_quest.quest.network.SyncObservability.Reason;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * C2S：客户端请求任务操作。
 */
public class C2SRequestQuestActionPacket {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Action action;
    private final String questId;
    private final int transitionIndex;
    private final String phaseId; // 仅 CHOOSE 时有效；其余可为空字符串

    private C2SRequestQuestActionPacket(Action action, String questId, int transitionIndex, String phaseId) {
        this.action = action;
        this.questId = questId;
        this.transitionIndex = transitionIndex;
        this.phaseId = phaseId == null ? "" : phaseId;
    }

    public static C2SRequestQuestActionPacket accept(String questId) {
        return new C2SRequestQuestActionPacket(Action.ACCEPT, questId, -1, "");
    }

    public static C2SRequestQuestActionPacket abandon(String questId) {
        return new C2SRequestQuestActionPacket(Action.ABANDON, questId, -1, "");
    }

    // 新：带 phaseId 的 choose
    public static C2SRequestQuestActionPacket choose(String questId, String phaseId, int transitionIndex) {
        return new C2SRequestQuestActionPacket(Action.CHOOSE, questId, transitionIndex, phaseId);
    }

    // 兼容旧调用：phaseId 为空
    public static C2SRequestQuestActionPacket choose(String questId, int transitionIndex) {
        return new C2SRequestQuestActionPacket(Action.CHOOSE, questId, transitionIndex, "");
    }

    public static void encode(C2SRequestQuestActionPacket pkt, FriendlyByteBuf buf) {
        buf.writeEnum(pkt.action);
        buf.writeUtf(pkt.questId, 256);
        buf.writeVarInt(pkt.transitionIndex);
        buf.writeUtf(pkt.phaseId, 256);
    }

    public static C2SRequestQuestActionPacket decode(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        String questId = buf.readUtf(256);
        int idx = buf.readVarInt();
        String phaseId = buf.readUtf(256);
        return new C2SRequestQuestActionPacket(action, questId, idx, phaseId);
    }

    public static void handle(C2SRequestQuestActionPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            switch (pkt.action) {
                case ACCEPT -> {
                    SyncObservability.trace("quest", pkt.questId, sender.getGameProfile().getName(),
                            SyncObservability.Stage.ACTION, Reason.QUEST_ACCEPT);
                    Code code = QuestProgressHandler.acceptQuestWithCode(sender, pkt.questId);
                    SyncObservability.trace("quest", pkt.questId, sender.getGameProfile().getName(),
                            SyncObservability.Stage.RESULT, toResultReason(pkt.action, code));
                    LOGGER.debug("[ArcQuest] C2S ACCEPT quest={}, code={}, player={}",
                            pkt.questId, code, sender.getGameProfile().getName());
                    ArcQuestNetwork.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> sender),
                            new S2CQuestActionResultPacket(pkt.action, pkt.questId, code)
                    );
                }
                case ABANDON -> {
                    SyncObservability.trace("quest", pkt.questId, sender.getGameProfile().getName(),
                            SyncObservability.Stage.ACTION, Reason.QUEST_ABANDON);
                    Code code = QuestProgressHandler.abandonQuestWithCode(sender, pkt.questId);
                    SyncObservability.trace("quest", pkt.questId, sender.getGameProfile().getName(),
                            SyncObservability.Stage.RESULT, toResultReason(pkt.action, code));
                    LOGGER.debug("[ArcQuest] C2S ABANDON quest={}, code={}, player={}",
                            pkt.questId, code, sender.getGameProfile().getName());
                    ArcQuestNetwork.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> sender),
                            new S2CQuestActionResultPacket(pkt.action, pkt.questId, code)
                    );
                }
                case CHOOSE -> {
                    SyncObservability.trace("quest", pkt.questId, sender.getGameProfile().getName(),
                            SyncObservability.Stage.ACTION, Reason.QUEST_CHOOSE);
                    Code code = QuestProgressHandler.handlePlayerChoiceWithCode(
                            sender, pkt.questId, pkt.phaseId, pkt.transitionIndex
                    );
                    SyncObservability.trace("quest", pkt.questId, sender.getGameProfile().getName(),
                            SyncObservability.Stage.RESULT, toResultReason(pkt.action, code));
                    LOGGER.debug("[ArcQuest] C2S CHOOSE quest={}, phase={}, idx={}, code={}, player={}",
                            pkt.questId, pkt.phaseId, pkt.transitionIndex, code, sender.getGameProfile().getName());
                    ArcQuestNetwork.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> sender),
                            new S2CQuestActionResultPacket(pkt.action, pkt.questId, code)
                    );
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static Reason toResultReason(Action action, Code code) {
        boolean ok = code == Code.OK;
        return switch (action) {
            case ACCEPT -> ok ? Reason.QUEST_ACCEPT_SUCCESS : Reason.QUEST_ACCEPT_REJECTED;
            case ABANDON -> ok ? Reason.QUEST_ABANDON_SUCCESS : Reason.QUEST_ABANDON_REJECTED;
            case CHOOSE -> ok ? Reason.QUEST_CHOOSE_SUCCESS : Reason.QUEST_CHOOSE_REJECTED;
        };
    }

    public enum Action {
        ACCEPT,
        ABANDON,
        CHOOSE
    }
}