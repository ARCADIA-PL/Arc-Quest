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
