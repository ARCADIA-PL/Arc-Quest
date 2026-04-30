package org.arcadia.arc_quest.dialogue.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

/**
 * 注册条件表 —— 允许在 .sayIf() 中使用自定义条件。
 * <p>
 * 支持两种使用方式：
 * <ol>
 *   <li><b>手动注册</b>：{@code RegisteredConditions.register("name", predicate)}</li>
 *   <li><b>自动注册</b>：{@code RegisteredConditions.autoRegister(predicate)} - 返回自动生成的名称</li>
 * </ol>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 方式1：手动注册
 * RegisteredConditions.register("has_diamond", (player, npc) -> {
 *     return player.getInventory().contains(new ItemStack(Items.DIAMOND));
 * });
 * .sayIf(new DialogueCondition.ReferencedCondition("has_diamond"), "你有钻石")
 *
 * // 方式2：自动注册（推荐）
 * String refName = RegisteredConditions.autoRegister((player, npc) -> {
 *     return player.getHealth() > 10.0f;
 * });
 * .sayIf(new DialogueCondition.ReferencedCondition(refName), "生命值充足")
 * }</pre>
 */
public final class RegisteredConditions {

    private static final Map<String, BiPredicate<ServerPlayer, Entity>> REGISTRY = new ConcurrentHashMap<>();

    private RegisteredConditions() {
    }

    /**
     * 注册一个命名自定义条件。
     *
     * @param name      条件名称（唯一标识）
     * @param predicate 条件判断函数
     */
    public static void register(String name, BiPredicate<ServerPlayer, Entity> predicate) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Condition name must not be null or empty");
        }
        if (predicate == null) {
            throw new IllegalArgumentException("Predicate must not be null");
        }
        REGISTRY.put(name, predicate);
    }

    /**
     * 自动注册一个条件，返回生成的引用名称。
     * <p>
     * 使用哈希值作为名称，相同的 lambda 会返回相同的名称（基于对象 identity）。
     *
     * @param predicate 条件判断函数
     * @return 自动生成的引用名称
     */
    public static String autoRegister(BiPredicate<ServerPlayer, Entity> predicate) {
        if (predicate == null) {
            throw new IllegalArgumentException("Predicate must not be null");
        }

        // 使用 System.identityHashCode 生成唯一名称
        // 注意：这基于对象引用，相同的 lambda 字面量可能生成不同的 hash
        int hash = System.identityHashCode(predicate);
        String name = "auto_" + Integer.toHexString(hash);

        // 如果已存在，直接返回
        if (REGISTRY.containsKey(name)) {
            return name;
        }

        REGISTRY.put(name, predicate);
        return name;
    }

    /**
     * 获取已注册的条件。
     *
     * @param name 条件名称
     * @return 条件判断函数，如果未注册则返回 null
     */
    public static BiPredicate<ServerPlayer, Entity> get(String name) {
        return REGISTRY.get(name);
    }

    /**
     * 检查条件是否已注册。
     *
     * @param name 条件名称
     * @return true 如果已注册
     */
    public static boolean isRegistered(String name) {
        return REGISTRY.containsKey(name);
    }

    /**
     * 注销一个条件。
     *
     * @param name 条件名称
     * @return 被注销的条件，如果未注册则返回 null
     */
    public static BiPredicate<ServerPlayer, Entity> unregister(String name) {
        return REGISTRY.remove(name);
    }

    /**
     * 清空所有注册的条件。
     */
    public static void clear() {
        REGISTRY.clear();
    }
}
