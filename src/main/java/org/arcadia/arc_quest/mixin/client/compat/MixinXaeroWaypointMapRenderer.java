package org.arcadia.arc_quest.mixin.client.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.arcadia.arc_quest.client.compat.xaero.XaeroQuestMarkerIconRegistry;
import org.arcadia.arc_quest.client.compat.xaero.XaeroQuestMarkerIconRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.element.render.MinimapElementRenderInfo;

@Pseudo
@Mixin(targets = "xaero.hud.minimap.waypoint.render.WaypointMapRenderer", remap = false)
public abstract class MixinXaeroWaypointMapRenderer {

    @Inject(
            method = "renderElement(Lxaero/common/minimap/waypoints/Waypoint;ZZDFDDLxaero/hud/minimap/element/render/MinimapElementRenderInfo;Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private void arcQuest$renderMarkerIcon(Waypoint waypoint,
                                           boolean pre,
                                           boolean outOfBounds,
                                           double optionalDepth,
                                           float optionalScale,
                                           double partialX,
                                           double partialY,
                                           MinimapElementRenderInfo renderInfo,
                                           GuiGraphics gui,
                                           MultiBufferSource.BufferSource bufferSource,
                                           CallbackInfoReturnable<Boolean> callback) {
        if (!XaeroQuestMarkerIconRegistry.isArcQuestWaypoint(waypoint)) return;
        if (outOfBounds) {
            callback.setReturnValue(false);
            return;
        }
        XaeroQuestMarkerIconRenderer.render(gui, XaeroQuestMarkerIconRegistry.resolveColor(waypoint));
        callback.setReturnValue(true);
    }
}
