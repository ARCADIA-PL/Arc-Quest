package org.arcadia.arc_quest.mixin;

import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.arcadia.arc_quest.quest.tracking.QuestEventManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerCreativeInventoryMixin {
    @Shadow public ServerPlayer player;

    /** Observe only inventory accepted by vanilla, never the creative menu's client-only cursor. */
    @Inject(method = "handleSetCreativeModeSlot", at = @At("RETURN"))
    private void arcquest$observeCreativeInventory(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        QuestEventManager.observeInventory(player);
    }
}
