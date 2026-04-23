package org.com.arc_quest.quest.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.com.arc_quest.dialogue.runtime.ICooldownRecord;

import java.util.HashMap;
import java.util.Map;

/**
 * 交易系统的玩家数据存储。
 * <p>
 * 类比 {@link GachaDataStore}，将购买次数和冷却时间戳从 {@code QuestCapabilityImpl}
 * 和 {@code DialogueProgressStore} 中统一迁移至此，使交易数据完全自治。
 * <p>
 * <b>NBT 兼容性：</b>购买次数沿用现有 {@code TradePurchases} key，格式不变，旧存档可正常读取。
 * 冷却时间戳新增 {@code TradeCooldowns} 子标签，同时在 {@link #deserialize} 时尝试从
 * {@code DialogueProgressStore} 的旧 {@code Trade} 分区迁移数据（一次性迁移）。
 */
public class TradeDataStore {

    private final Map<String, Map<String, Integer>> purchaseCounts = new HashMap<>();
    private final Map<String, Map<String, TradeCooldownEntry>> cooldowns = new HashMap<>();

    // ════════════════════════════════════════
    //  购买次数
    // ════════════════════════════════════════

    public int getPurchaseCount(String shopId, String entryId) {
        Map<String, Integer> shopData = purchaseCounts.get(shopId);
        return shopData == null ? 0 : shopData.getOrDefault(entryId, 0);
    }

    public void incrementPurchase(String shopId, String entryId) {
        purchaseCounts.computeIfAbsent(shopId, k -> new HashMap<>())
                      .merge(entryId, 1, Integer::sum);
    }

    // ════════════════════════════════════════
    //  冷却时间戳
    // ════════════════════════════════════════

    /**
     * 获取商品冷却记录。未记录时返回 {@link TradeCooldownEntry#EMPTY}。
     */
    public TradeCooldownEntry getCooldown(String shopId, String entryId) {
        Map<String, TradeCooldownEntry> shopCooldowns = cooldowns.get(shopId);
        if (shopCooldowns == null) return TradeCooldownEntry.EMPTY;
        return shopCooldowns.getOrDefault(entryId, TradeCooldownEntry.EMPTY);
    }

    /**
     * 记录商品冷却时间戳（三时钟快照）。
     */
    public void recordCooldown(String shopId, String entryId, long realTime, long gameTime, long dayTime) {
        cooldowns.computeIfAbsent(shopId, k -> new HashMap<>())
                 .put(entryId, new TradeCooldownEntry(realTime, gameTime, dayTime));
    }

    /**
     * 移除商品冷却时间戳。
     */
    public void removeCooldown(String shopId, String entryId) {
        Map<String, TradeCooldownEntry> shopCooldowns = cooldowns.get(shopId);
        if (shopCooldowns != null) shopCooldowns.remove(entryId);
    }

    // ════════════════════════════════════════
    //  复合重置（购买次数 + 冷却）
    // ════════════════════════════════════════

    /**
     * 重置购买次数并移除冷却时间戳。
     */
    public void resetEntry(String shopId, String entryId) {
        Map<String, Integer> shopData = purchaseCounts.get(shopId);
        if (shopData != null) shopData.remove(entryId);
        removeCooldown(shopId, entryId);
    }

    // ════════════════════════════════════════
    //  清空
    // ════════════════════════════════════════

    public void clear() {
        purchaseCounts.clear();
        cooldowns.clear();
    }

    // ════════════════════════════════════════
    //  序列化
    // ════════════════════════════════════════

    public CompoundTag serialize() {
        CompoundTag root = new CompoundTag();

        // 购买次数 —— 沿用旧格式（TradePurchases / shopId / entryId = int）
        CompoundTag purchasesTag = new CompoundTag();
        for (var shopEntry : purchaseCounts.entrySet()) {
            CompoundTag shopTag = new CompoundTag();
            shopEntry.getValue().forEach(shopTag::putInt);
            purchasesTag.put(shopEntry.getKey(), shopTag);
        }
        root.put("TradePurchases", purchasesTag);

        // 冷却时间戳 —— 新格式（TradeCooldowns / shopId / entryId / {r,g,d}）
        CompoundTag cooldownsTag = new CompoundTag();
        for (var shopEntry : cooldowns.entrySet()) {
            CompoundTag shopTag = new CompoundTag();
            for (var entryEntry : shopEntry.getValue().entrySet()) {
                CompoundTag ct = new CompoundTag();
                ct.putLong("r", entryEntry.getValue().realTime());
                ct.putLong("g", entryEntry.getValue().gameTime());
                ct.putLong("d", entryEntry.getValue().dayTime());
                shopTag.put(entryEntry.getKey(), ct);
            }
            cooldownsTag.put(shopEntry.getKey(), shopTag);
        }
        root.put("TradeCooldowns", cooldownsTag);

        return root;
    }

    /**
     * 从自身序列化格式反序列化。
     *
     * @param root 来自 {@link #serialize()} 的标签
     */
    public void deserialize(CompoundTag root) {
        purchaseCounts.clear();
        cooldowns.clear();

        CompoundTag purchasesTag = root.getCompound("TradePurchases");
        for (String shopId : purchasesTag.getAllKeys()) {
            CompoundTag shopTag = purchasesTag.getCompound(shopId);
            Map<String, Integer> shopData = new HashMap<>();
            for (String entryId : shopTag.getAllKeys()) shopData.put(entryId, shopTag.getInt(entryId));
            purchaseCounts.put(shopId, shopData);
        }

        if (root.contains("TradeCooldowns", Tag.TAG_COMPOUND)) {
            CompoundTag cooldownsTag = root.getCompound("TradeCooldowns");
            for (String shopId : cooldownsTag.getAllKeys()) {
                CompoundTag shopTag = cooldownsTag.getCompound(shopId);
                Map<String, TradeCooldownEntry> shopCooldowns = new HashMap<>();
                for (String entryId : shopTag.getAllKeys()) {
                    CompoundTag ct = shopTag.getCompound(entryId);
                    shopCooldowns.put(entryId, new TradeCooldownEntry(
                            ct.getLong("r"), ct.getLong("g"),
                            ct.contains("d") ? ct.getLong("d") : -1L));
                }
                cooldowns.put(shopId, shopCooldowns);
            }
        }
    }

    /**
     * 从旧版本的顶层 NBT 反序列化（兼容 QuestCapabilityImpl v3 及以前格式）。
     * <p>
     * 购买次数从 {@code TradePurchases} key 读取（格式不变）；
     * 冷却时间戳原本存储在 {@code DialogueProgressStore} 的 {@code Trade} 分区，
     * 此处通过 {@code DialogueProgress.Trade} 子标签进行一次性迁移读取。
     *
     * @param root 玩家能力数据的顶层 NBT
     */
    public void deserializeLegacy(CompoundTag root) {
        purchaseCounts.clear();
        cooldowns.clear();

        CompoundTag purchasesTag = root.getCompound("TradePurchases");
        for (String shopId : purchasesTag.getAllKeys()) {
            CompoundTag shopTag = purchasesTag.getCompound(shopId);
            Map<String, Integer> shopData = new HashMap<>();
            for (String entryId : shopTag.getAllKeys()) shopData.put(entryId, shopTag.getInt(entryId));
            purchaseCounts.put(shopId, shopData);
        }

        // 从 DialogueProgressStore 的 Trade 分区迁移冷却时间戳
        if (root.contains("DialogueProgress", Tag.TAG_COMPOUND)) {
            CompoundTag dlgProgress = root.getCompound("DialogueProgress");
            if (dlgProgress.contains("Trade", Tag.TAG_COMPOUND)) {
                CompoundTag tradeTag = dlgProgress.getCompound("Trade");
                // key 格式为 "trade:shopId|entryId"，解析并写入 cooldowns
                for (String key : tradeTag.getAllKeys()) {
                    if (!key.startsWith("trade:")) continue;
                    String payload = key.substring("trade:".length());
                    int sep = payload.indexOf('|');
                    if (sep < 0) continue;
                    String shopId  = payload.substring(0, sep);
                    String entryId = payload.substring(sep + 1);
                    CompoundTag ct = tradeTag.getCompound(key);
                    cooldowns.computeIfAbsent(shopId, k -> new HashMap<>())
                             .put(entryId, new TradeCooldownEntry(
                                     ct.getLong("r"), ct.getLong("g"),
                                     ct.contains("d") ? ct.getLong("d") : -1L));
                }
            }
        }
    }

    // ════════════════════════════════════════
    //  TradeCooldownEntry
    // ════════════════════════════════════════

    /**
     * 交易商品冷却时间戳快照，实现 {@link ICooldownRecord}，
     * 可直接传入 {@link org.com.arc_quest.dialogue.runtime.UnifiedCooldownManager}。
     */
    public record TradeCooldownEntry(long realTime, long gameTime, long dayTime) implements ICooldownRecord {

        public static final TradeCooldownEntry EMPTY = new TradeCooldownEntry(0L, -1L, -1L);

        @Override
        public boolean exists() { return realTime > 0; }
    }
}
