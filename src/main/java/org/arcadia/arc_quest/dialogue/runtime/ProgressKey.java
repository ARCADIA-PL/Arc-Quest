package org.arcadia.arc_quest.dialogue.runtime;

/**
 * 类型安全的进度存储 Key。
 * <p>
 * 替代 {@code namespace + ":" + nodeId} 的字符串拼接，
 * 预计算并缓存 key 字符串和 hashCode。
 * <p>
 * <b>四种 Key 类型</b>:
 * <ul>
 *   <li>{@link #ofNode(String, String)} → "namespace:nodeId" ({@link KeyType#NODE})</li>
 *   <li>{@link #ofDialogue(String, String)} → "namespace:dialogueId" ({@link KeyType#DIALOGUE})</li>
 *   <li>{@link #ofChoice(String, String, int)} → "namespace:nodeId:index" ({@link KeyType#CHOICE})</li>
 *   <li>{@link #ofTrade(String, String)} → "trade:shopId|entryId" ({@link KeyType#TRADE})</li>
 * </ul>
 * <p>
 * <b>性能特性</b>:
 * <ul>
 *   <li>keyString 在构造时一次性生成，后续查询零拼接</li>
 *   <li>hashCode 在构造时预计算，HashMap 查找零重算</li>
 *   <li>namespace 使用 String.intern() 共享引用</li>
 * </ul>
 */
public final class ProgressKey {

    private final String namespace;
    private final String id;
    private final int index;        // -1 = 非选项 Key
    private final KeyType keyType;  // 语义类型，用于 serialize 分区
    private final String keyString; // 缓存
    private final int hash;         // 预计算

    private ProgressKey(String namespace, String id, int index, KeyType keyType) {
        this.namespace = namespace.intern();
        this.id = id;
        this.index = index;
        this.keyType = keyType;

        if (index >= 0) {
            keyString = namespace + ":" + id + ":" + index;
        } else {
            keyString = namespace + ":" + id;
        }
        hash = keyString.hashCode();
    }

    /**
     * 节点 Key: "namespace:nodeId"
     */
    public static ProgressKey ofNode(String namespace, String nodeId) {
        return new ProgressKey(namespace, nodeId, -1, KeyType.NODE);
    }

    // ═══════════════════════════════════════════════
    //  静态工厂
    // ═══════════════════════════════════════════════

    /**
     * 选项 Key: "namespace:nodeId:choiceIndex"
     */
    public static ProgressKey ofChoice(String namespace, String nodeId, int choiceIndex) {
        return new ProgressKey(namespace, nodeId, choiceIndex, KeyType.CHOICE);
    }

    /**
     * 对话树 Key: "namespace:dialogueId"
     */
    public static ProgressKey ofDialogue(String namespace, String dialogueId) {
        return new ProgressKey(namespace, dialogueId, -1, KeyType.DIALOGUE);
    }

    /**
     * 交易冷却 Key: "trade:shopId|entryId"
     * <p>
     * 使用 {@code |} 分隔符避免与 ResourceLocation 的冒号格式歧义。
     *
     * @param shopId  商店 ID
     * @param entryId 商品 ID
     * @return 交易冷却的 ProgressKey
     */
    public static ProgressKey ofTrade(String shopId, String entryId) {
        return new ProgressKey("trade", shopId + "|" + entryId, -1, KeyType.TRADE);
    }

    public String namespace() {
        return namespace;
    }

    // ═══════════════════════════════════════════════
    //  访问器
    // ═══════════════════════════════════════════════

    public String id() {
        return id;
    }

    public int index() {
        return index;
    }

    public KeyType keyType() {
        return keyType;
    }

    /**
     * 返回缓存的 key 字符串，用于 Map 查找和 NBT 序列化。
     * <b>零拼接开销</b>：构造时已生成。
     */
    public String toKeyString() {
        return keyString;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    // ═══════════════════════════════════════════════
    //  equals / hashCode（基于缓存的 keyString）
    // ═══════════════════════════════════════════════

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProgressKey other)) return false;
        return hash == other.hash && keyString.equals(other.keyString);
    }

    @Override
    public String toString() {
        return keyString;
    }

    /**
     * Key 的语义类型，用于序列化分区时替代字符串格式猜测。
     */
    public enum KeyType {
        /**
         * 节点访问记录，格式：namespace:nodeId
         */
        NODE,
        /**
         * 对话树访问记录，格式：namespace:dialogueId
         */
        DIALOGUE,
        /**
         * 选项选择记录，格式：namespace:nodeId:choiceIndex
         */
        CHOICE,
        /**
         * 交易/商品冷却记录，格式：trade:shopId|entryId
         */
        TRADE
    }
}
