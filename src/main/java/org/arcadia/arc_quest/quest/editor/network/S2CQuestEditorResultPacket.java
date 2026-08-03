package org.arcadia.arc_quest.quest.editor.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.client.editor.quest.QuestEditorClient;

import java.util.function.Supplier;

public record S2CQuestEditorResultPacket(boolean success, long revision, long reloadEpoch, String message) {
    public static void encode(S2CQuestEditorResultPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.success); buffer.writeLong(packet.revision); buffer.writeLong(packet.reloadEpoch);
        buffer.writeUtf(packet.message, 4096);
    }
    public static S2CQuestEditorResultPacket decode(FriendlyByteBuf buffer) {
        return new S2CQuestEditorResultPacket(buffer.readBoolean(), buffer.readLong(), buffer.readLong(), buffer.readUtf(4096));
    }
    public static void handle(S2CQuestEditorResultPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get(); context.enqueueWork(() -> QuestEditorClient.handleResult(packet));
        context.setPacketHandled(true);
    }
}
