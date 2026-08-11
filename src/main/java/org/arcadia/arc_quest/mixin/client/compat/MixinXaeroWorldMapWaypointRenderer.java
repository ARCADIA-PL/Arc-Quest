package org.arcadia.arc_quest.mixin.client.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.arcadia.arc_quest.client.compat.xaero.XaeroQuestMarkerIconRegistry;
import org.arcadia.arc_quest.client.compat.xaero.XaeroQuestMarkerIconRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.element.render.ElementRenderInfo;
import xaero.map.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;
import xaero.map.mods.gui.Waypoint;

@Pseudo
@Mixin(targets = "xaero.map.mods.gui.WaypointRenderer", remap = false)
public abstract class MixinXaeroWorldMapWaypointRenderer {

    private static final float ARC_QUEST_WORLD_MAP_ICON_SCALE = 1.5F;

    @Inject(
            method = "renderElement(Lxaero/map/mods/gui/Waypoint;ZDFDDLxaero/map/element/render/ElementRenderInfo;Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lxaero/map/graphics/renderer/multitexture/MultiTextureRenderTypeRendererProvider;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private void arcQuest$renderMarkerIcon(Waypoint waypoint,
                                           boolean hovered,
                                           double optionalDepth,
                                           float optionalScale,
                                           double partialX,
                                           double partialY,
                                           ElementRenderInfo renderInfo,
                                           GuiGraphics gui,
                                           MultiBufferSource.BufferSource bufferSource,
                                           MultiTextureRenderTypeRendererProvider rendererProvider,
                                           CallbackInfoReturnable<Boolean> callback) {
        if (!XaeroQuestMarkerIconRegistry.ORIGIN_ID.equals(waypoint.getThirdPartyOrigin())) return;

        int colorArgb = 0xFF000000 | waypoint.getColor();
        if (waypoint.getOriginal() instanceof xaero.common.minimap.waypoints.Waypoint minimapWaypoint) {
            colorArgb = XaeroQuestMarkerIconRegistry.resolveColor(minimapWaypoint);
        }
        XaeroQuestMarkerIconRenderer.render(gui, colorArgb, ARC_QUEST_WORLD_MAP_ICON_SCALE);
        if (hovered) {
            XaeroQuestMarkerIconRenderer.renderTooltip(gui, waypoint.getName(), colorArgb & 0x00FFFFFF);
        }
        callback.setReturnValue(true);
    }

    @Inject(
            method = "renderElementShadow(Lxaero/map/mods/gui/Waypoint;ZFDDLxaero/map/element/render/ElementRenderInfo;Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lxaero/map/graphics/renderer/multitexture/MultiTextureRenderTypeRendererProvider;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private void arcQuest$hideDefaultShadow(Waypoint waypoint,
                                            boolean hovered,
                                            float optionalScale,
                                            double partialX,
                                            double partialY,
                                            ElementRenderInfo renderInfo,
                                            GuiGraphics gui,
                                            MultiBufferSource.BufferSource bufferSource,
                                            MultiTextureRenderTypeRendererProvider rendererProvider,
                                            CallbackInfo callback) {
        if (XaeroQuestMarkerIconRegistry.ORIGIN_ID.equals(waypoint.getThirdPartyOrigin())) callback.cancel();
    }
}
