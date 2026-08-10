package org.arcadia.arc_quest.mixin.client.compat;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.arcadia.arc_quest.client.compat.xaero.XaeroQuestMarkerIconRegistry;
import org.arcadia.arc_quest.client.compat.xaero.XaeroQuestMarkerIconRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.minimap.render.MinimapRendererHelper;
import xaero.common.minimap.waypoints.Waypoint;

@Pseudo
@Mixin(targets = "xaero.hud.minimap.waypoint.render.WaypointMapRenderer", remap = false)
public abstract class MixinXaeroWaypointMapRenderer {

    @Inject(
            method = "drawIconOnGUI(Lnet/minecraft/client/gui/GuiGraphics;Lxaero/common/minimap/render/MinimapRendererHelper;Lxaero/common/minimap/waypoints/Waypoint;IIILnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private void arcQuest$renderMarkerIcon(GuiGraphics gui,
                                           MinimapRendererHelper helper,
                                           Waypoint waypoint,
                                           int centerX,
                                           int centerY,
                                           int opacity,
                                           MultiBufferSource.BufferSource bufferSource,
                                           VertexConsumer texturedIconConsumer,
                                           VertexConsumer waypointBackgroundConsumer,
                                           CallbackInfo callback) {
        if (!XaeroQuestMarkerIconRegistry.isArcQuestWaypoint(waypoint)) return;
        XaeroQuestMarkerIconRenderer.render(gui, waypointBackgroundConsumer,
                centerX, centerY, opacity, XaeroQuestMarkerIconRegistry.resolveColor(waypoint));
        callback.cancel();
    }
}
