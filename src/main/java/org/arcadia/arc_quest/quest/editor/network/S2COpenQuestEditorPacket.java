package org.arcadia.arc_quest.quest.editor.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.client.editor.quest.QuestEditorClient;
import org.arcadia.arc_quest.quest.editor.QuestEditorSessionService;

import java.util.UUID;

public record S2COpenQuestEditorPacket(UUID sessionId, ResourceLocation questId, String sourceFileName,
                                       long revision, long reloadEpoch, String json) implements CustomPacketPayload {
    public static final Type<S2COpenQuestEditorPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "editor_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2COpenQuestEditorPacket> STREAM_CODEC =
            StreamCodec.ofMember(S2COpenQuestEditorPacket::encode, S2COpenQuestEditorPacket::decode);

    public static void encode(S2COpenQuestEditorPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
        buffer.writeResourceLocation(packet.questId);
        buffer.writeUtf(packet.sourceFileName, 256);
        buffer.writeLong(packet.revision);
        buffer.writeLong(packet.reloadEpoch);
        buffer.writeUtf(packet.json, QuestEditorSessionService.MAX_DOCUMENT_CHARS);
    }

    public static S2COpenQuestEditorPacket decode(FriendlyByteBuf buffer) {
        return new S2COpenQuestEditorPacket(buffer.readUUID(), buffer.readResourceLocation(), buffer.readUtf(256),
                buffer.readLong(), buffer.readLong(), buffer.readUtf(QuestEditorSessionService.MAX_DOCUMENT_CHARS));
    }

    public static void handle(S2COpenQuestEditorPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> QuestEditorClient.open(packet));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
