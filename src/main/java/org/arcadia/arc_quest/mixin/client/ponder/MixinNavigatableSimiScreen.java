package org.arcadia.arc_quest.mixin.client.ponder;

import net.createmod.catnip.gui.NavigatableSimiScreen;
import net.minecraft.client.gui.screens.Screen;
import org.arcadia.arc_quest.client.ponder.TradePonderNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NavigatableSimiScreen.class)
public abstract class MixinNavigatableSimiScreen {
    @Inject(method = "onClose", at = @At("HEAD"), cancellable = true)
    private void arcQuest$returnToShop(CallbackInfo ci) {
        if (TradePonderNavigation.returnToShop((Screen) (Object) this)) ci.cancel();
    }
}
