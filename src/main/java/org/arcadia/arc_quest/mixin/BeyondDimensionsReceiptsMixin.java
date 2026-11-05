package org.arcadia.arc_quest.mixin;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.nbt.CompoundTag;
import org.arcadia.arc_quest.integration.storage.StoragePaymentReceipts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet", remap = false)
public abstract class BeyondDimensionsReceiptsMixin implements StoragePaymentReceipts {
    @Unique private CompoundTag arcQuest$paymentReceipts = new CompoundTag();
    @Override public CompoundTag arcQuest$payments() { return arcQuest$paymentReceipts; }

    @Inject(method = "load", at = @At("RETURN"), remap = false)
    private static void arcQuest$loadPayments(CompoundTag tag, CallbackInfoReturnable<DimensionsNet> cir) {
        ((StoragePaymentReceipts) cir.getReturnValue()).arcQuest$payments().merge(tag.getCompound(TAG).copy());
    }

    // @Pseudo mod targets are absent from Mojang mappings; accept both dev and Forge SRG names.
    @Inject(method = {"save(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;",
            "m_7176_(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;"}, at = @At("RETURN"), remap = false)
    private void arcQuest$savePayments(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        if (!arcQuest$paymentReceipts.isEmpty()) cir.getReturnValue().put(TAG, arcQuest$paymentReceipts.copy());
        else cir.getReturnValue().remove(TAG);
    }
}
