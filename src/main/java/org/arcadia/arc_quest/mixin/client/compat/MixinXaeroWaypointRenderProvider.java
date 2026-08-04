package org.arcadia.arc_quest.mixin.client.compat;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.Arc_Quest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.element.render.MinimapElementRenderLocation;
import xaero.hud.minimap.waypoint.render.AbstractWaypointRenderContext;

import java.util.function.Predicate;
import java.util.stream.Stream;

@Pseudo
@Mixin(targets = "xaero.hud.minimap.waypoint.render.AbstractWaypointRenderProvider", remap = false)
public abstract class MixinXaeroWaypointRenderProvider {

    private static final ResourceLocation ARC_QUEST_MARKER_ORIGIN =
            ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "quest_markers");

    @Redirect(
            method = "begin(Lxaero/hud/minimap/element/render/MinimapElementRenderLocation;Lxaero/hud/minimap/waypoint/render/AbstractWaypointRenderContext;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/stream/Stream;filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;"),
            require = 0,
            remap = false)
    private Stream<Waypoint> arcQuest$filterWorldMarkers(Stream<Waypoint> stream,
                                                         Predicate<? super Waypoint> originalFilter,
                                                         MinimapElementRenderLocation location,
                                                         AbstractWaypointRenderContext context) {
        Stream<Waypoint> filtered = stream.filter(originalFilter);
        if (location != MinimapElementRenderLocation.IN_WORLD) return filtered;
        return filtered.filter(waypoint -> !ARC_QUEST_MARKER_ORIGIN.equals(waypoint.getThirdPartyOrigin()));
    }
}
