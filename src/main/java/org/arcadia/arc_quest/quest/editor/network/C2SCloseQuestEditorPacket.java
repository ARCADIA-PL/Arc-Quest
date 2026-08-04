package org.arcadia.arc_quest.quest.editor.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.editor.QuestEditorSessionService;

public record C2SCloseQuestEditorPacket() implements CustomPacketPayload {
    public static final Type<C2SCloseQuestEditorPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "editor_close"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SCloseQuestEditorPacket> STREAM_CODEC =
            StreamCodec.unit(new C2SCloseQuestEditorPacket());

    public static void handle(C2SCloseQuestEditorPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                QuestEditorSessionService.INSTANCE.close(player.getUUID());
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
