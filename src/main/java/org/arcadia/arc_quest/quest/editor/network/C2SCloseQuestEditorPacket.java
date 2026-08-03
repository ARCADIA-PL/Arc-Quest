package org.arcadia.arc_quest.quest.editor.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.quest.editor.QuestEditorSessionService;

import java.util.function.Supplier;

public record C2SCloseQuestEditorPacket() {
    public static void encode(C2SCloseQuestEditorPacket packet, FriendlyByteBuf buffer) { }
    public static C2SCloseQuestEditorPacket decode(FriendlyByteBuf buffer) { return new C2SCloseQuestEditorPacket(); }
    public static void handle(C2SCloseQuestEditorPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get(); ServerPlayer sender = context.getSender();
        if (sender != null) context.enqueueWork(() -> QuestEditorSessionService.INSTANCE.close(sender.getUUID()));
        context.setPacketHandled(true);
    }
}
