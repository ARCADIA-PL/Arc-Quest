package org.arcadia.arc_quest.trade.demo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.random.RandomGenerator;

/** 有界测试货架：保留未修改条目的 ID，新增商品使用上一轮空缺的槽位。 */
public record RefreshingTradeCatalog(long round, List<Listing> entries) {
    public static final int MAX_ENTRIES = 12, SLOT_COUNT = 24, PRODUCT_COUNT = 12;

    public RefreshingTradeCatalog {
        entries = List.copyOf(entries);
        if (round < 0 || entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("Invalid test catalog");
        var slots = new HashSet<Integer>();
        for (Listing listing : entries) {
            if (!slots.add(listing.slot())) throw new IllegalArgumentException("Duplicate test slot");
        }
    }

    public record Listing(int slot, int product, int price, int count) {
        public Listing {
            if (slot < 0 || slot >= SLOT_COUNT || product < 0 || product >= PRODUCT_COUNT
                    || price < 1 || price > 4 || count < 1 || count > 4) {
                throw new IllegalArgumentException("Invalid test listing");
            }
        }
    }

    public static RefreshingTradeCatalog initial(RandomGenerator random) {
        var entries = new ArrayList<Listing>();
        for (int i = 0; i < 6; i++) entries.add(listing(i, (i % 3) * 4 + random.nextInt(4), random));
        return new RefreshingTradeCatalog(0, entries);
    }

    public RefreshingTradeCatalog next(RandomGenerator random) {
        if (entries.isEmpty()) throw new IllegalStateException("Cannot refresh a stopped catalog");
        var next = new ArrayList<>(entries);
        int replaceIndex = random.nextInt(next.size());
        Listing previous = next.get(replaceIndex);
        int product = (previous.product() + 1 + random.nextInt(PRODUCT_COUNT - 1)) % PRODUCT_COUNT;
        next.set(replaceIndex, listing(previous.slot(), product, random));
        var unused = new ArrayList<Integer>();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            int candidate = slot;
            if (entries.stream().noneMatch(entry -> entry.slot() == candidate)) unused.add(slot);
        }
        if (next.size() == MAX_ENTRIES) {
            // 不移除刚替换的条目，使每轮都能测试“奖励变更”和“新上架”。
            int removeIndex = (replaceIndex + 1 + random.nextInt(next.size() - 1)) % next.size();
            next.remove(removeIndex);
        }
        next.add(listing(unused.get(random.nextInt(unused.size())), random.nextInt(PRODUCT_COUNT), random));
        next.sort(Comparator.comparingInt(Listing::slot));
        return new RefreshingTradeCatalog(round + 1, next);
    }

    private static Listing listing(int slot, int product, RandomGenerator random) {
        return new Listing(slot, product, 1 + random.nextInt(4), product >= 8 ? 1 : 1 + random.nextInt(4));
    }
}
