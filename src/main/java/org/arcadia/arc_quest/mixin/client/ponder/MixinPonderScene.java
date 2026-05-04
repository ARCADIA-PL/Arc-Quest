package org.arcadia.arc_quest.mixin.client.ponder;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.quest.ponder.IntelPonderUIStub;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Backstops legacy ArcQuest ponder overlay calls that still pass a null PonderUI.
 */
@Mixin(value = PonderScene.class, remap = false)
public class MixinPonderScene {

    @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true)
    private void arcQuest$injectStubScreen(PonderUI screen, GuiGraphics graphics,
                                           float partialTicks, CallbackInfo ci) {
        if (screen != null) return;
        IntelPonderUIStub.OverlayContext context = IntelPonderUIStub.getActiveOverlayContext();
        if (context == null) return;
        PonderScene self = (PonderScene) (Object) this;
        IntelPonderUIStub.renderOverlaySafely(self, graphics, partialTicks, context.width(), context.height(), context.font());
        ci.cancel();
    }
}
