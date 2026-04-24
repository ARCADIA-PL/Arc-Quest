package org.com.arc_quest.quest.network;

import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.com.arc_quest.quest.logic.QuestProgressHandler;
import org.com.arc_quest.quest.network.QuestRejectCodeDictionary.Code;
import org.com.arc_quest.quest.network.SyncObservability.Reason;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * C2S：客户端请求任务操作。
 * <p>
 * 支持三种操作：
 * <ul>
 *   <li>{@link Action#ACCEPT}  — 接受任务</li>
 *   <li>{@link Action#ABANDON} — 放弃任务</li>
 *   <li>{@link Action#CHOOSE}  — 选择分支（需附带 transitionIndex）</li>
 * </ul>
 * <p>
 * <b>安全设计</b>：所有逻辑验证在服务端的 {@link QuestProgressHandler} 中进行，
 * 客户端无法伪造进度。
 */
public class C2SRequestQuestActionPacket {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final Action action;
    private final String questId;
    private final int transitionIndex; // 仅 CHOOSE 时有效

    private C2SRequestQuestActionPacket(Action action, String questId, int transitionIndex) {
        this.action = action;
        this.questId = questId;
        this.transitionIndex = transitionIndex;
    }

    // ── 工厂方法 ──────────────────────────────────────

    public static C2SRequestQuestActionPacket accept(String questId) {
        return new C2SRequestQuestActionPacket(Action.ACCEPT, questId, -1);
    }

    public static C2SRequestQuestActionPacket abandon(String questId) {
        return new C2SRequestQuestActionPacket(Action.ABANDON, questId, -1);
    }

    public static C2SRequestQuestActionPacket choose(String questId, int transitionIndex) {
        return new C2SRequestQuestActionPacket(Action.CHOOSE, questId, transitionIndex);
    }

    public static void encode(C2SRequestQuestActionPacket pkt, FriendlyByteBuf buf) {
        buf.writeEnum(pkt.action);
        buf.writeUtf(pkt.questId, 256);
        buf.writeVarInt(pkt.transitionIndex);
    }

    // ── 编码 ──────────────────────────────────────────

    public static C2SRequestQuestActionPacket decode(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        String questId = buf.readUtf(256);
        int idx = buf.readVarInt();
        return new C2SRequestQuestActionPacket(action, questId, idx);
    }

    // ── 解码 ──────────────────────────────────────────

    public static void handle(C2SRequestQuestActionPacket pkt,
                              Supplier<NetworkEvent.Context> ctx) {
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
                }
                case ABANDON -> {
                    SyncObservability.trace("quest", pkt.questId, sender.getGameProfile().getName(),
                            SyncObservability.Stage.ACTION, Reason.QUEST_ABANDON);
                    Code code = QuestProgressHandler.abandonQuestWithCode(sender, pkt.questId);
                    SyncObservability.trace("quest", pkt.questId, sender.getGameProfile().getName(),
                            SyncObservability.Stage.RESULT, toResultReason(pkt.action, code));
                    LOGGER.debug("[ArcQuest] C2S ABANDON quest={}, code={}, player={}",
                            pkt.questId, code, sender.getGameProfile().getName());
                }
                case CHOOSE -> {
                    SyncObservability.trace("quest", pkt.questId, sender.getGameProfile().getName(),
                            SyncObservability.Stage.ACTION, Reason.QUEST_CHOOSE);
                    Code code = QuestProgressHandler.handlePlayerChoiceWithCode(sender, pkt.questId, pkt.transitionIndex);
                    SyncObservability.trace("quest", pkt.questId, sender.getGameProfile().getName(),
                            SyncObservability.Stage.RESULT, toResultReason(pkt.action, code));
                    LOGGER.debug("[ArcQuest] C2S CHOOSE quest={}, idx={}, code={}, player={}",
                            pkt.questId, pkt.transitionIndex, code, sender.getGameProfile().getName());
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

    // ── 处理（服务端）─────────────────────────────────

    public enum Action {
        ACCEPT,
        ABANDON,
        CHOOSE
    }
}