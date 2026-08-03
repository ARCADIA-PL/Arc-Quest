package org.arcadia.arc_quest.quest.editor.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.client.editor.quest.QuestEditorClient;
import org.arcadia.arc_quest.quest.editor.QuestEditorSessionService;

import java.util.UUID;
import java.util.function.Supplier;

public record S2COpenQuestEditorPacket(UUID sessionId, ResourceLocation questId, String sourceFileName,
                                       long revision, long reloadEpoch, String json) {
    public static void encode(S2COpenQuestEditorPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId); buffer.writeResourceLocation(packet.questId);
        buffer.writeUtf(packet.sourceFileName, 256); buffer.writeLong(packet.revision); buffer.writeLong(packet.reloadEpoch);
        buffer.writeUtf(packet.json, QuestEditorSessionService.MAX_DOCUMENT_CHARS);
    }
    public static S2COpenQuestEditorPacket decode(FriendlyByteBuf buffer) {
        return new S2COpenQuestEditorPacket(buffer.readUUID(), buffer.readResourceLocation(), buffer.readUtf(256),
                buffer.readLong(), buffer.readLong(), buffer.readUtf(QuestEditorSessionService.MAX_DOCUMENT_CHARS));
    }
    public static void handle(S2COpenQuestEditorPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get(); context.enqueueWork(() -> QuestEditorClient.open(packet));
        context.setPacketHandled(true);
    }
}
