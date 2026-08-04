package org.arcadia.arc_quest.quest.editor.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.client.editor.quest.QuestEditorClient;

public record S2CQuestEditorResultPacket(boolean success, long revision, long reloadEpoch, String message)
        implements CustomPacketPayload {
    public static final Type<S2CQuestEditorResultPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "editor_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CQuestEditorResultPacket> STREAM_CODEC =
            StreamCodec.ofMember(S2CQuestEditorResultPacket::encode, S2CQuestEditorResultPacket::decode);

    public static void encode(S2CQuestEditorResultPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.success);
        buffer.writeLong(packet.revision);
        buffer.writeLong(packet.reloadEpoch);
        buffer.writeUtf(packet.message, 4096);
    }

    public static S2CQuestEditorResultPacket decode(FriendlyByteBuf buffer) {
        return new S2CQuestEditorResultPacket(buffer.readBoolean(), buffer.readLong(), buffer.readLong(),
                buffer.readUtf(4096));
    }

    public static void handle(S2CQuestEditorResultPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> QuestEditorClient.handleResult(packet));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
