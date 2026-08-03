package org.arcadia.arc_quest.quest.editor.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.quest.editor.QuestEditorSessionService;

import java.util.UUID;
import java.util.function.Supplier;

public record C2SSaveQuestEditorPacket(UUID sessionId, long revision, String json) {
    public static void encode(C2SSaveQuestEditorPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId); buffer.writeLong(packet.revision);
        buffer.writeUtf(packet.json, QuestEditorSessionService.MAX_DOCUMENT_CHARS);
    }
    public static C2SSaveQuestEditorPacket decode(FriendlyByteBuf buffer) {
        return new C2SSaveQuestEditorPacket(buffer.readUUID(), buffer.readLong(),
                buffer.readUtf(QuestEditorSessionService.MAX_DOCUMENT_CHARS));
    }
    public static void handle(C2SSaveQuestEditorPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get(); ServerPlayer sender = context.getSender();
        if (sender != null) context.enqueueWork(() -> QuestEditorSessionService.INSTANCE.save(sender,
                packet.sessionId, packet.revision, packet.json));
        context.setPacketHandled(true);
    }
}
