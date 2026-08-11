package org.arcadia.arc_quest.mixin.client.compat;

import org.arcadia.arc_quest.client.compat.xaero.XaeroQuestMarkerIconRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.mods.gui.Waypoint;
import xaero.map.mods.gui.WaypointRenderContext;

@Pseudo
@Mixin(targets = "xaero.map.mods.gui.WaypointReader", remap = false)
public abstract class MixinXaeroWorldMapWaypointReader {

    private static final int ARC_QUEST_INTERACTION_RADIUS = 22;

    @Inject(
            method = "getInteractionBoxLeft(Lxaero/map/mods/gui/Waypoint;Lxaero/map/mods/gui/WaypointRenderContext;F)I",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void arcQuest$expandLeft(Waypoint waypoint,
                                     WaypointRenderContext context,
                                     float optionalScale,
                                     CallbackInfoReturnable<Integer> callback) {
        if (isArcQuestWaypoint(waypoint)) callback.setReturnValue(-ARC_QUEST_INTERACTION_RADIUS);
    }

    @Inject(
            method = "getInteractionBoxRight(Lxaero/map/mods/gui/Waypoint;Lxaero/map/mods/gui/WaypointRenderContext;F)I",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void arcQuest$expandRight(Waypoint waypoint,
                                      WaypointRenderContext context,
                                      float optionalScale,
                                      CallbackInfoReturnable<Integer> callback) {
        if (isArcQuestWaypoint(waypoint)) callback.setReturnValue(ARC_QUEST_INTERACTION_RADIUS);
    }

    @Inject(
            method = "getInteractionBoxTop(Lxaero/map/mods/gui/Waypoint;Lxaero/map/mods/gui/WaypointRenderContext;F)I",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void arcQuest$expandTop(Waypoint waypoint,
                                    WaypointRenderContext context,
                                    float optionalScale,
                                    CallbackInfoReturnable<Integer> callback) {
        if (isArcQuestWaypoint(waypoint)) callback.setReturnValue(-ARC_QUEST_INTERACTION_RADIUS);
    }

    @Inject(
            method = "getInteractionBoxBottom(Lxaero/map/mods/gui/Waypoint;Lxaero/map/mods/gui/WaypointRenderContext;F)I",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void arcQuest$expandBottom(Waypoint waypoint,
                                       WaypointRenderContext context,
                                       float optionalScale,
                                       CallbackInfoReturnable<Integer> callback) {
        if (isArcQuestWaypoint(waypoint)) callback.setReturnValue(ARC_QUEST_INTERACTION_RADIUS);
    }

    private static boolean isArcQuestWaypoint(Waypoint waypoint) {
        return waypoint != null && XaeroQuestMarkerIconRegistry.ORIGIN_ID.equals(waypoint.getThirdPartyOrigin());
    }
}
