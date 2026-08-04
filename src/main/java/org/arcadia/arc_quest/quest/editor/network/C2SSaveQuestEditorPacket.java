package org.arcadia.arc_quest.quest.editor.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.editor.QuestEditorSessionService;

import java.util.UUID;

public record C2SSaveQuestEditorPacket(UUID sessionId, long revision, String json) implements CustomPacketPayload {
    public static final Type<C2SSaveQuestEditorPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "editor_save"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SSaveQuestEditorPacket> STREAM_CODEC =
            StreamCodec.ofMember(C2SSaveQuestEditorPacket::encode, C2SSaveQuestEditorPacket::decode);

    public static void encode(C2SSaveQuestEditorPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
        buffer.writeLong(packet.revision);
        buffer.writeUtf(packet.json, QuestEditorSessionService.MAX_DOCUMENT_CHARS);
    }

    public static C2SSaveQuestEditorPacket decode(FriendlyByteBuf buffer) {
        return new C2SSaveQuestEditorPacket(buffer.readUUID(), buffer.readLong(),
                buffer.readUtf(QuestEditorSessionService.MAX_DOCUMENT_CHARS));
    }

    public static void handle(C2SSaveQuestEditorPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                QuestEditorSessionService.INSTANCE.save(player, packet.sessionId(), packet.revision(), packet.json());
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
