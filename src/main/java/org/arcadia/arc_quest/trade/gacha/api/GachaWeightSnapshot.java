package org.arcadia.arc_quest.trade.gacha.api;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongUnaryOperator;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** 一次抽取只评估一次可见性和动态权重，选择区间与概率使用同一份快照。 */
final class GachaWeightSnapshot {
    private final List<Entry> entries;
    private final long total;

    GachaWeightSnapshot(List<GachaItem> items, Predicate<GachaItem> visible, ToIntFunction<GachaItem> weight) {
        List<Entry> evaluated = new ArrayList<>(items.size());
        long sum = 0;
        for (GachaItem item : items) {
            if (!visible.test(item)) continue;
            int value = Math.max(0, weight.applyAsInt(item));
            if (value == 0) continue;
            evaluated.add(new Entry(item, value));
            sum += value;
        }
        entries = List.copyOf(evaluated);
        total = sum;
    }

    GachaItem draw(LongUnaryOperator random) {
        if (total == 0) return null;
        long ticket = random.applyAsLong(total);
        if (ticket < 0 || ticket >= total) throw new IllegalArgumentException("Random ticket outside pool weight");
        for (Entry entry : entries) {
            if (ticket < entry.weight) return entry.item;
            ticket -= entry.weight;
        }
        throw new IllegalStateException("Inconsistent gacha weight snapshot");
    }

    double probability(String itemId) {
        if (total == 0) return 0;
        for (Entry entry : entries) {
            if (entry.item.getItemId().equals(itemId)) return entry.weight * 100.0 / total;
        }
        return 0;
    }

    private record Entry(GachaItem item, int weight) { }
}
