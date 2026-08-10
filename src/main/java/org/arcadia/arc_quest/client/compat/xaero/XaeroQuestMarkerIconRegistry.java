package org.arcadia.arc_quest.client.compat.xaero;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.Arc_Quest;
import xaero.common.minimap.waypoints.Waypoint;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class XaeroQuestMarkerIconRegistry {

    public static final ResourceLocation ORIGIN_ID =
            ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "quest_markers");

    private static final Map<Waypoint, Integer> COLORS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private XaeroQuestMarkerIconRegistry() {
    }

    public static void register(Waypoint waypoint, int colorArgb) {
        if (waypoint != null) COLORS.put(waypoint, colorArgb);
    }

    public static void unregister(Waypoint waypoint) {
        if (waypoint != null) COLORS.remove(waypoint);
    }

    public static void clear() {
        COLORS.clear();
    }

    public static boolean isArcQuestWaypoint(Waypoint waypoint) {
        return waypoint != null && ORIGIN_ID.equals(waypoint.getThirdPartyOrigin());
    }

    public static int resolveColor(Waypoint waypoint) {
        Integer color = COLORS.get(waypoint);
        return color != null ? color : 0xFF000000 | waypoint.getActualColor();
    }
}
