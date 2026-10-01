package org.arcadia.arc_quest.client.compat.jei.screen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.client.compat.jei.JeiCatalogClient;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reads the real clipped hit map at the rendered icon center, without publishing synthetic regions. */
public final class ObjectiveIconJeiHitProbe {
    private ObjectiveIconJeiHitProbe() {}
    public static List<ItemStack> at(Screen screen, double x, double y) {
        return JeiScreenIngredients.underMouse(screen, x, y).map(JeiScreenIngredients.Region::stacks).orElse(List.of());
    }
    public static boolean allowsPrimaryClick(Screen screen, double x, double y) {
        return JeiScreenIngredients.underMouse(screen, x, y)
                .map(JeiScreenIngredients.Region::allowsPrimaryClick).orElse(false);
    }

    /** Use the actual icon edge; KILL/CUSTOM rows legitimately have no material region to authorize. */
    public static Optional<Point> ordinaryRowAt(Screen screen, double iconX, double iconY) {
        var icon = JeiScreenIngredients.underMouse(screen, iconX, iconY);
        if (icon.isEmpty() || !icon.get().allowsPrimaryClick()) return Optional.empty();
        double rowX = icon.get().bounds().right() + 2;
        if (rowX >= screen.width || iconY < 0 || iconY >= screen.height || allowsPrimaryClick(screen, rowX, iconY))
            return Optional.empty();
        return Optional.of(new Point(rowX, iconY));
    }

    /** Client-thread synchronous scope: replace only material snapshot references, never permissions or listeners. */
    public static EmptyCatalog emptyMaterialCatalog() {
        try {
            Field entries = JeiCatalogClient.class.getDeclaredField("entries"), byId = JeiCatalogClient.class.getDeclaredField("byId");
            entries.setAccessible(true); byId.setAccessible(true);
            var scope = new EmptyCatalog(entries, byId, entries.get(null), byId.get(null), JeiCatalogClient.isEnabled(), JeiCatalogClient.revision());
            try { entries.set(null, List.of()); byId.set(null, Map.of()); }
            catch (ReflectiveOperationException failure) { scope.close(); throw failure; }
            return scope;
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot scope the empty material-catalog audit", failure); }
    }
    public static final class EmptyCatalog implements AutoCloseable {
        private final Field entries, byId;
        private final Object previousEntries, previousById;
        private final boolean previousEnabled;
        private final long previousRevision;
        private boolean closed;
        private EmptyCatalog(Field entries, Field byId, Object previousEntries, Object previousById, boolean enabled, long revision) {
            this.entries = entries; this.byId = byId; this.previousEntries = previousEntries; this.previousById = previousById;
            previousEnabled = enabled; previousRevision = revision;
        }
        @Override public void close() {
            if (closed) return;
            try {
                entries.set(null, previousEntries); byId.set(null, previousById); closed = true;
                if (entries.get(null) != previousEntries || byId.get(null) != previousById
                        || JeiCatalogClient.isEnabled() != previousEnabled || JeiCatalogClient.revision() != previousRevision)
                    throw new IllegalStateException("Material catalog snapshot or permission state was not restored exactly");
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot restore the material-catalog audit", failure); }
        }
    }

    public record Point(double x, double y) {}
}
