package org.arcadia.arc_quest.questmarker.api;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 客户端任务标记表现扩展点。
 * 附属模组可以注册标签、颜色和可见性解析器，不需要修改标记核心类。
 */
public final class QuestMarkerPresentationRegistry {
    private static final List<LabelResolver> LABEL_RESOLVERS = new CopyOnWriteArrayList<>();
    private static final List<ColorResolver> COLOR_RESOLVERS = new CopyOnWriteArrayList<>();
    private static final List<VisibilityFilter> VISIBILITY_FILTERS = new CopyOnWriteArrayList<>();

    private QuestMarkerPresentationRegistry() {
    }

    public static void registerLabelResolver(LabelResolver resolver) {
        if (resolver != null) LABEL_RESOLVERS.add(resolver);
    }

    public static void registerColorResolver(ColorResolver resolver) {
        if (resolver != null) COLOR_RESOLVERS.add(resolver);
    }

    public static void registerVisibilityFilter(VisibilityFilter filter) {
        if (filter != null) VISIBILITY_FILTERS.add(filter);
    }

    public static Component resolveLabel(QuestMarkerData marker) {
        for (LabelResolver resolver : LABEL_RESOLVERS) {
            Component value = resolver.resolve(marker);
            if (value != null) return value;
        }
        return null;
    }

    public static Integer resolveColor(QuestMarkerData marker) {
        for (ColorResolver resolver : COLOR_RESOLVERS) {
            Integer value = resolver.resolve(marker);
            if (value != null) return value;
        }
        return null;
    }

    public static boolean isVisible(QuestMarkerData marker) {
        for (VisibilityFilter filter : VISIBILITY_FILTERS) {
            if (!filter.isVisible(marker)) return false;
        }
        return true;
    }

    @FunctionalInterface
    public interface LabelResolver {
        Component resolve(QuestMarkerData marker);
    }

    @FunctionalInterface
    public interface ColorResolver {
        Integer resolve(QuestMarkerData marker);
    }

    @FunctionalInterface
    public interface VisibilityFilter {
        boolean isVisible(QuestMarkerData marker);
    }
}