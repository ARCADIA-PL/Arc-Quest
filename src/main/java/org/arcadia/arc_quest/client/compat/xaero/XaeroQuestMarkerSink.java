package org.arcadia.arc_quest.client.compat.xaero;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.arcadia.arc_quest.client.compat.marker.QuestMarkerExternalSync;
import org.arcadia.arc_quest.config.ArcQuestConfig;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.common.minimap.waypoints.WaypointVisibilityType;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints;
import xaero.hud.minimap.world.container.MinimapWorldContainer;
import xaero.hud.minimap.world.container.MinimapWorldRootContainer;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class XaeroQuestMarkerSink implements QuestMarkerExternalSync.Sink {
    private static final int MAX_WAYPOINT_NAME_LENGTH = 128;
    private static final int RECONCILE_INTERVAL_TICKS = 20;

    private final Map<String, Projection> desired = new LinkedHashMap<>();
    private final Map<String, Projection> applied = new LinkedHashMap<>();
    private int sessionIdentity;
    private int reconcileTicker;
    private boolean disabled;

    @Override
    public void replaceAll(Collection<QuestMarkerData> markers) {
        Map<String, Projection> next = new LinkedHashMap<>();
        for (QuestMarkerData marker : markers) {
            Projection projection = Projection.from(marker);
            if (projection != null) next.put(projection.markerId(), projection);
        }
        runOnClientThread(() -> replaceProjected(next));
    }

    @Override
    public void clear() {
        runOnClientThread(this::clearProjected);
    }

    @Override
    public void tick() {
        if (disabled || ++reconcileTicker < RECONCILE_INTERVAL_TICKS) return;
        reconcileTicker = 0;
        syncDesired();
    }

    private void replaceProjected(Map<String, Projection> next) {
        if (disabled) return;
        try {
            desired.clear();
            desired.putAll(next);
            syncDesired();
        } catch (RuntimeException | LinkageError exception) {
            disable(exception);
        }
    }

    private void syncDesired() {
        if (disabled) return;
        try {
            SessionContext context = currentContext();
            if (context == null) return;
            if (sessionIdentity != context.identity()) {
                sessionIdentity = context.identity();
                applied.clear();
                XaeroQuestMarkerIconRegistry.clear();
            }
            if (desired.equals(applied)) return;
            reconcile(context.session(), context.root());
            applied.clear();
            applied.putAll(desired);
        } catch (RuntimeException | LinkageError exception) {
            disable(exception);
        }
    }

    private void clearProjected() {
        if (disabled) return;
        try {
            desired.clear();
            SessionContext context = currentContext();
            if (context != null) reconcile(context.session(), context.root());
            applied.clear();
            sessionIdentity = 0;
            reconcileTicker = 0;
            XaeroQuestMarkerIconRegistry.clear();
        } catch (RuntimeException | LinkageError exception) {
            disable(exception);
        }
    }

    private void runOnClientThread(Runnable operation) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isSameThread()) {
            operation.run();
        } else {
            minecraft.execute(operation);
        }
    }

    private static SessionContext currentContext() {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null) return null;
        MinimapWorldRootContainer root = session.getWorldManager().getAutoRootContainer();
        if (root == null) return null;
        int identity = 31 * System.identityHashCode(session) + System.identityHashCode(root);
        return new SessionContext(session, root, identity);
    }

    private void reconcile(MinimapSession session, MinimapWorldRootContainer root) {
        for (Map.Entry<String, Projection> entry : applied.entrySet()) {
            Projection replacement = desired.get(entry.getKey());
            if (entry.getValue().equals(replacement)) continue;
            remove(session, root, entry.getValue());
        }
        for (Map.Entry<String, Projection> entry : desired.entrySet()) {
            if (entry.getValue().equals(applied.get(entry.getKey()))) continue;
            add(session, root, entry.getValue());
        }
    }

    private static void add(MinimapSession session, MinimapWorldRootContainer root, Projection projection) {
        MinimapWorldContainer container = resolveContainer(session, root, projection.dimension());
        ThirdPartyWaypoints waypoints = container.getThirdPartyWaypointManager()
                .get(XaeroQuestMarkerIconRegistry.ORIGIN_ID);
        if (!waypoints.hasEnabledStateGetter()) {
            waypoints.setEnabledStateGetter(ArcQuestConfig::shouldSyncQuestMarkersToXaeroMinimap);
        }
        Waypoint waypoint = new Waypoint(projection.x(), projection.y(), projection.z(), projection.name(),
                projection.symbol(), nearestColor(projection.colorArgb()), WaypointPurpose.NORMAL);
        waypoint.setVisibility(WaypointVisibilityType.LOCAL);
        waypoints.add(projection.markerId(), waypoint);
        XaeroQuestMarkerIconRegistry.register(waypoint, projection.colorArgb());
    }

    private static void remove(MinimapSession session, MinimapWorldRootContainer root, Projection projection) {
        MinimapWorldContainer container = resolveContainer(session, root, projection.dimension());
        ThirdPartyWaypoints waypoints = container.getThirdPartyWaypointManager()
                .get(XaeroQuestMarkerIconRegistry.ORIGIN_ID);
        XaeroQuestMarkerIconRegistry.unregister(waypoints.get(projection.markerId()));
        waypoints.remove(projection.markerId());
    }

    private static MinimapWorldContainer resolveContainer(MinimapSession session,
                                                           MinimapWorldRootContainer root,
                                                           ResourceLocation dimensionId) {
        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, dimensionId);
        String directoryName = session.getDimensionHelper().getDimensionDirectoryName(dimensionKey);
        return root.addSubContainer(root.getPath().resolve(directoryName));
    }

    private void disable(Throwable exception) {
        disabled = true;
        desired.clear();
        applied.clear();
        XaeroQuestMarkerIconRegistry.clear();
        ArcQuestLog.error(ArcQuestLog.Category.COMPAT, "Disabled waypoint integration after an incompatible API failure", exception);
    }

    private static WaypointColor nearestColor(int colorArgb) {
        int red = colorArgb >>> 16 & 0xFF;
        int green = colorArgb >>> 8 & 0xFF;
        int blue = colorArgb & 0xFF;
        WaypointColor nearest = WaypointColor.WHITE;
        long nearestDistance = Long.MAX_VALUE;
        for (WaypointColor candidate : WaypointColor.values()) {
            int candidateColor = candidate.getHex();
            long redDelta = red - (candidateColor >>> 16 & 0xFF);
            long greenDelta = green - (candidateColor >>> 8 & 0xFF);
            long blueDelta = blue - (candidateColor & 0xFF);
            long distance = redDelta * redDelta + greenDelta * greenDelta + blueDelta * blueDelta;
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private record Projection(String markerId,
                              ResourceLocation dimension,
                              int x,
                              int y,
                              int z,
                              String name,
                              String symbol,
                              int colorArgb) {

        private static Projection from(QuestMarkerData marker) {
            if (marker == null || !marker.isActive()) return null;
            ResourceLocation dimension = ResourceLocation.tryParse(marker.getDimension());
            if (dimension == null) return null;
            String localizedLabel = marker.getLabelComponent().getString();
            String name = localizedLabel.isBlank()
                    ? marker.getId()
                    : localizedLabel;
            if (name.length() > MAX_WAYPOINT_NAME_LENGTH) name = name.substring(0, MAX_WAYPOINT_NAME_LENGTH);
            return new Projection(marker.getId(), dimension,
                    Mth.floor(marker.getWorldX()), Mth.floor(marker.getWorldY()), Mth.floor(marker.getWorldZ()),
                    name, symbol(name), marker.getColorARGB());
        }

        private static String symbol(String name) {
            int codePoint = name.codePoints().filter(Character::isLetterOrDigit).findFirst().orElse('Q');
            return new String(Character.toChars(codePoint)).toUpperCase(Locale.ROOT);
        }
    }

    private record SessionContext(MinimapSession session, MinimapWorldRootContainer root, int identity) {
    }
}
