package org.com.arc_quest.quest.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;
import org.com.arc_quest.client.gui.quest.QuestToastManager;

import java.util.function.Supplier;

/**
 * S2C：Quest action 的标准结果回包（含 reject code）。
 */
public class S2CQuestActionResultPacket {

    private final C2SRequestQuestActionPacket.Action action;
    private final String questId;
    private final String codeName;

    public S2CQuestActionResultPacket(C2SRequestQuestActionPacket.Action action,
                                      String questId,
                                      QuestRejectCodeDictionary.Code code) {
        this.action = action;
        this.questId = questId;
        this.codeName = (code != null ? code : QuestRejectCodeDictionary.Code.UNKNOWN).name();
    }

    private S2CQuestActionResultPacket(C2SRequestQuestActionPacket.Action action,
                                       String questId,
                                       String codeName) {
        this.action = action;
        this.questId = questId;
        this.codeName = codeName;
    }

    public static void encode(S2CQuestActionResultPacket pkt, FriendlyByteBuf buf) {
        buf.writeEnum(pkt.action);
        buf.writeUtf(pkt.questId);
        buf.writeUtf(pkt.codeName);
    }

    public static S2CQuestActionResultPacket decode(FriendlyByteBuf buf) {
        return new S2CQuestActionResultPacket(
                buf.readEnum(C2SRequestQuestActionPacket.Action.class),
                buf.readUtf(),
                buf.readUtf()
        );
    }

    public static void handle(S2CQuestActionResultPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            QuestRejectCodeDictionary.Code code;
            try {
                code = QuestRejectCodeDictionary.Code.valueOf(pkt.codeName);
            } catch (Exception ignored) {
                code = QuestRejectCodeDictionary.Code.UNKNOWN;
            }

            if (code == QuestRejectCodeDictionary.Code.OK) {
                return;
            }

            Component msg = toClientMessage(pkt.action, pkt.questId, code);
            mc.player.displayClientMessage(msg, true);
            QuestToastManager.show(QuestToastManager.ToastType.QUEST_FAILED, msg.getString());
        });
        ctx.get().setPacketHandled(true);
    }

    private static Component toClientMessage(C2SRequestQuestActionPacket.Action action,
                                             String questId,
                                             QuestRejectCodeDictionary.Code code) {
        String actionText = switch (action) {
            case ACCEPT -> "接受任务";
            case ABANDON -> "放弃任务";
            case CHOOSE -> "选择分支";
        };

        String reason = switch (code) {
            case QUEST_NOT_FOUND -> "任务不存在";
            case ALREADY_ACTIVE -> "任务已在进行中";
            case ALREADY_COMPLETED_NOT_REPEATABLE -> "任务已完成且不可重复";
            case UNLOCK_CONDITION_NOT_MET -> "未满足前置条件";
            case NO_INITIAL_PHASE -> "任务缺少初始阶段";
            case NOT_ACTIVE -> "任务未激活";
            case PHASE_NOT_FOUND -> "当前阶段不存在";
            case INVALID_CHOICE_INDEX -> "分支索引无效";
            case CHOICE_CONDITION_NOT_MET -> "分支条件不满足";
            case CHOICE_TARGET_PHASE_MISSING -> "分支目标阶段缺失";
            default -> "未知原因";
        };

        return Component.literal("[Quest] " + actionText + "失败: " + reason + " (" + questId + ")");
    }
}
