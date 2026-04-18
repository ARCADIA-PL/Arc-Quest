package org.com.arc_quest.dialogue.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.com.arc_quest.dialogue.runtime.DialogueSessionManager;

import java.util.function.Supplier;

/**
 * 客户端→服务端：玩家选择对话选项 / 自动跳转请求。
 */
public class C2SDialogueChoicePacket {

    public static final int AUTO_ADVANCE = -1;
    public static final int CLOSE = -2;
    /**
     * -1 = 自动跳转请求, -2 = 关闭对话, >=0 = 选择索引。
     */
    private final int choiceIndex;

    public C2SDialogueChoicePacket(int choiceIndex) {
        this.choiceIndex = choiceIndex;
    }

    public static C2SDialogueChoicePacket autoAdvance() {
        return new C2SDialogueChoicePacket(AUTO_ADVANCE);
    }

    public static C2SDialogueChoicePacket close() {
        return new C2SDialogueChoicePacket(CLOSE);
    }

    // ── 序列化 ──

    public static C2SDialogueChoicePacket decode(FriendlyByteBuf buf) {
        return new C2SDialogueChoicePacket(buf.readVarInt());
    }

    public static void handle(C2SDialogueChoicePacket pkt,
                              Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            switch (pkt.choiceIndex) {
                case CLOSE -> DialogueSessionManager.INSTANCE.endDialogue(player);
                case AUTO_ADVANCE -> DialogueSessionManager.INSTANCE.handleAutoAdvance(player);
                default -> DialogueSessionManager.INSTANCE.handleChoice(player, pkt.choiceIndex);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(choiceIndex);
    }
}