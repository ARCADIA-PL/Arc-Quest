package org.arcadia.arc_quest.trade.runtime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashMap;
import java.util.Map;

/** 玩家独立的商店观察记录；仅在服务端主线程更新，随玩家数据持久化。 */
public final class TradeUpdateStore {
    public static final int ADDED = 1, UNLOCKED = 2, PRICE = 4, REWARD = 8, RESTOCKED = 16;
    public static final int ALL_REASONS = 31;
    public static final int MAX_ENTRIES = 1024;
    private static final int MAX_SHOPS = 128, MAX_TOTAL_ENTRIES = 8192;
    private static final long MAX_SAVED_REVISION = Long.MAX_VALUE / 2;
    private final LinkedHashMap<String, Map<String, Observation>> shops = new LinkedHashMap<>();
    private long revision;
    private boolean dirty;

    public record Snapshot(String price, String reward, boolean visible, boolean unlocked, boolean soldOut) { }
    public record Notice(int reasons, long revision) { }
    private record Observation(Snapshot snapshot, Notice notice) { }

    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }

    public void observe(String shopId, Map<String, Snapshot> current) {
        if (!validId(shopId) || current.size() > MAX_ENTRIES) return;
        Map<String, Observation> previous = shops.get(shopId);
        Map<String, Observation> next = new LinkedHashMap<>();
        current.forEach((id, snapshot) -> {
            if (!validId(id)) return;
            Observation old = previous == null ? null : previous.get(id);
            int changes = 0;
            if (previous != null) {
                if (old == null) changes = ADDED;
                else {
                    Snapshot before = old.snapshot();
                    if ((!before.visible() && snapshot.visible()) || (!before.unlocked() && snapshot.unlocked())) changes |= UNLOCKED;
                    if (!before.price().equals(snapshot.price())) changes |= PRICE;
                    if (!before.reward().equals(snapshot.reward())) changes |= REWARD;
                    if (before.soldOut() && !snapshot.soldOut()) changes |= RESTOCKED;
                }
            }
            Notice notice = old == null ? new Notice(0, 0) : old.notice();
            // 隐藏期间保留变化，但不通过网络泄露未解锁的商品。
            if (changes != 0) notice = new Notice(notice.reasons() | changes, ++revision);
            next.put(id, new Observation(snapshot, notice));
        });
        if (!next.equals(previous)) dirty = true;
        shops.remove(shopId);
        shops.put(shopId, next);
        trim();
    }

    public Map<String, Notice> pending(String shopId) {
        Map<String, Notice> result = new LinkedHashMap<>();
        Map<String, Observation> entries = shops.get(shopId);
        if (entries != null) entries.forEach((id, observation) -> {
            if (observation.snapshot().visible() && observation.notice().reasons() != 0) result.put(id, observation.notice());
        });
        return result;
    }

    public boolean acknowledge(String shopId, String entryId, long expectedRevision) {
        Map<String, Observation> entries = shops.get(shopId);
        Observation entry = entries == null ? null : entries.get(entryId);
        if (entry == null || !entry.snapshot().visible() || entry.notice().reasons() == 0
                || expectedRevision != entry.notice().revision()) return false;
        entries.put(entryId, new Observation(entry.snapshot(), new Notice(0, expectedRevision)));
        dirty = true;
        return true;
    }

    public void clear() { shops.clear(); dirty = true; }

    public void copyFrom(TradeUpdateStore source) {
        if (source == this) return;
        shops.clear();
        source.shops.forEach((id, entries) -> shops.put(id, new LinkedHashMap<>(entries)));
        revision = source.revision;
        dirty = source.dirty;
    }

    public CompoundTag serialize() {
        CompoundTag root = new CompoundTag();
        root.putLong("Revision", revision);
        CompoundTag shopTags = new CompoundTag();
        shops.forEach((shopId, entries) -> {
            CompoundTag entryTags = new CompoundTag();
            entries.forEach((id, observation) -> {
                Snapshot s = observation.snapshot();
                CompoundTag tag = new CompoundTag();
                tag.putString("Price", s.price());
                tag.putString("Reward", s.reward());
                tag.putBoolean("Visible", s.visible());
                tag.putBoolean("Unlocked", s.unlocked());
                tag.putBoolean("SoldOut", s.soldOut());
                tag.putInt("Reasons", observation.notice().reasons());
                tag.putLong("Revision", observation.notice().revision());
                entryTags.put(id, tag);
            });
            shopTags.put(shopId, entryTags);
        });
        root.put("Shops", shopTags);
        return root;
    }

    public void deserialize(CompoundTag root) {
        shops.clear();
        long savedRevision = root.getLong("Revision");
        revision = savedRevision >= 0 && savedRevision <= MAX_SAVED_REVISION ? savedRevision : 0;
        CompoundTag shopTags = root.getCompound("Shops");
        int total = 0;
        for (String shopId : shopTags.getAllKeys()) {
            if (shops.size() >= MAX_SHOPS || total >= MAX_TOTAL_ENTRIES) break;
            if (!validId(shopId) || !shopTags.contains(shopId, Tag.TAG_COMPOUND)) continue;
            Map<String, Observation> entries = new LinkedHashMap<>();
            CompoundTag entryTags = shopTags.getCompound(shopId);
            for (String id : entryTags.getAllKeys()) {
                if (entries.size() >= MAX_ENTRIES || total >= MAX_TOTAL_ENTRIES) break;
                if (!validId(id) || !entryTags.contains(id, Tag.TAG_COMPOUND)) continue;
                CompoundTag tag = entryTags.getCompound(id);
                String price = tag.getString("Price"), reward = tag.getString("Reward");
                if (price.length() != 64 || reward.length() != 64) continue;
                long entryRevision = Math.max(0, tag.getLong("Revision"));
                if (entryRevision > MAX_SAVED_REVISION) continue;
                revision = Math.max(revision, entryRevision);
                entries.put(id, new Observation(new Snapshot(price, reward, tag.getBoolean("Visible"),
                        tag.getBoolean("Unlocked"), tag.getBoolean("SoldOut")),
                        new Notice(entryRevision == 0 ? 0 : tag.getInt("Reasons") & ALL_REASONS, entryRevision)));
                total++;
            }
            shops.put(shopId, entries);
        }
        dirty = false;
    }

    private void trim() {
        int total = shops.values().stream().mapToInt(Map::size).sum();
        while (shops.size() > MAX_SHOPS || total > MAX_TOTAL_ENTRIES) {
            var iterator = shops.entrySet().iterator();
            total -= iterator.next().getValue().size();
            iterator.remove();
            dirty = true;
        }
    }

    public static boolean validId(String id) { return id != null && !id.isEmpty() && id.length() <= 256; }
}
