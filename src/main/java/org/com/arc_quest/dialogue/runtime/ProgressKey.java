package org.com.arc_quest.dialogue.runtime;

/**
 * 类型安全的进度存储 Key。
 * <p>
 * 替代 {@code namespace + ":" + nodeId} 的字符串拼接,
 * 预计算并缓存 key 字符串和 hashCode。
 * <p>
 * <b>三种 Key 类型</b>:
 * <ul>
 *   <li>{@link #ofNode(String, String)} → "namespace:nodeId"</li>
 *   <li>{@link #ofChoice(String, String, int)} → "namespace:nodeId:index"</li>
 *   <li>{@link #ofDialogue(String, String)} → "namespace:dialogueId"</li>
 * </ul>
 * <p>
 * <b>性能特性</b>:
 * <ul>
 *   <li>keyString 在构造时一次性生成,后续查询零拼接</li>
 *   <li>hashCode 在构造时预计算,HashMap 查找零重算</li>
 *   <li>namespace 使用 String.intern() 共享引用</li>
 * </ul>
 */
public final class ProgressKey {

    private final String namespace;
    private final String id;
    private final int index;        // -1 = 非选项 Key
    private final String keyString; // 缓存
    private final int hash;         // 预计算

    private ProgressKey(String namespace, String id, int index) {
        this.namespace = namespace.intern(); // 共享 namespace 引用
        this.id = id;
        this.index = index;

        // 一次性生成,后续不再拼接
        if (index >= 0) {
            this.keyString = namespace + ":" + id + ":" + index;
        } else {
            this.keyString = namespace + ":" + id;
        }
        this.hash = keyString.hashCode();
    }

    // ═══════════════════════════════════════════════
    //  静态工厂
    // ═══════════════════════════════════════════════

    /** 节点 Key: "namespace:nodeId" */
    public static ProgressKey ofNode(String namespace, String nodeId) {
        return new ProgressKey(namespace, nodeId, -1);
    }

    /** 选项 Key: "namespace:nodeId:choiceIndex" */
    public static ProgressKey ofChoice(String namespace, String nodeId, int choiceIndex) {
        return new ProgressKey(namespace, nodeId, choiceIndex);
    }

    /** 对话树 Key: "namespace:dialogueId" */
    public static ProgressKey ofDialogue(String namespace, String dialogueId) {
        return new ProgressKey(namespace, dialogueId, -1);
    }

    // ═══════════════════════════════════════════════
    //  访问器
    // ═══════════════════════════════════════════════

    public String namespace()  { return namespace; }
    public String id()         { return id; }
    public int index()         { return index; }

    /**
     * 返回缓存的 key 字符串,用于 Map 查找和 NBT 序列化。
     * <b>零拼接开销</b>:构造时已生成。
     */
    public String toKeyString() { return keyString; }

    // ═══════════════════════════════════════════════
    //  equals / hashCode(基于缓存的 keyString)
    // ═══════════════════════════════════════════════

    @Override
    public int hashCode() { return hash; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProgressKey other)) return false;
        return this.hash == other.hash && this.keyString.equals(other.keyString);
    }

    @Override
    public String toString() { return keyString; }
}
