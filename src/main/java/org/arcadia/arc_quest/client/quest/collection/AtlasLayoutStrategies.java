package org.arcadia.arc_quest.client.quest.collection;

import net.minecraft.resources.ResourceLocation;

import org.arcadia.arc_quest.quest.registry.CollectionAtlasLayoutRegistry.Scope;

import java.util.*;

/** Pure authorized layout extension. Missing client implementation safely uses the scope's grid. */
public final class AtlasLayoutStrategies {
    public record Context(
            Scope scope,
            List<String> ids,
            int columns,
            double cellWidth,
            double cellHeight,
            Map<String, Double> parameters) {
        public Context {
            ids = List.copyOf(ids);
            parameters = Map.copyOf(parameters);
        }
    }

    public record Placement(String id, double x, double y) {}

    @FunctionalInterface
    public interface Strategy {
        List<Placement> layout(Context context);
    }

    private static final Map<ResourceLocation, Strategy> STRATEGIES = new HashMap<>();

    private AtlasLayoutStrategies() {}

    public record Size(double width, double height) {}

    /** Each column reserves its own width; each row reserves only its tallest member. */
    public static Map<String, Placement> packedGrid(List<String> ids, Map<String, Size> sizes,
            int columns, double gapX, double gapY) {
        int cols = Math.max(1, Math.min(columns, Math.max(1, ids.size())));
        double[] widths = new double[cols];
        for (int i = 0; i < ids.size(); i++) widths[i % cols] = Math.max(widths[i % cols], sizes.get(ids.get(i)).width());
        Map<String, Placement> result = new LinkedHashMap<>();
        double y = 0;
        for (int start = 0; start < ids.size(); start += cols) {
            double x = 0, height = 0;
            for (int col = 0; col < cols && start + col < ids.size(); col++) {
                String id = ids.get(start + col);
                result.put(id, new Placement(id, x, y));
                height = Math.max(height, sizes.get(id).height());
                x += widths[col] + gapX;
            }
            y += height + gapY;
        }
        return Map.copyOf(result);
    }

    public static synchronized void register(ResourceLocation id, Strategy strategy) {
        if (STRATEGIES.putIfAbsent(id, Objects.requireNonNull(strategy)) != null)
            throw new IllegalArgumentException("Duplicate client layout: " + id);
    }

    public static Map<String, Placement> layout(ResourceLocation type, Context context) {
        var strategy = STRATEGIES.get(type);
        List<Placement> result = null;
        if (strategy != null)
            try {
                result = strategy.layout(context);
            } catch (RuntimeException invalid) {
                result = null;
            }
        Map<String, Placement> accepted = new LinkedHashMap<>();
        if (result != null)
            for (var p : result) {
                if (p == null
                        || !context.ids().contains(p.id())
                        || !Double.isFinite(p.x())
                        || !Double.isFinite(p.y())
                        || Math.abs(p.x()) > 1000000
                        || Math.abs(p.y()) > 1000000
                        || accepted.putIfAbsent(p.id(), p) != null) {
                    accepted.clear();
                    break;
                }
            }
        for (int i = 0; i < context.ids().size(); i++) {
            String id = context.ids().get(i);
            accepted.putIfAbsent(
                    id,
                    new Placement(
                            id,
                            i % Math.max(1, context.columns()) * context.cellWidth(),
                            i / Math.max(1, context.columns()) * context.cellHeight()));
        }
        return Map.copyOf(accepted);
    }
}
