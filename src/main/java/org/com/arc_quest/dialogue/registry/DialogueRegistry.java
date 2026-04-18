package org.com.arc_quest.dialogue.registry;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.com.arc_quest.dialogue.api.DialogueTree;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * 全局对话树注册表（线程安全，支持热重载）。
 */
public final class DialogueRegistry {

    public static final DialogueRegistry INSTANCE = new DialogueRegistry();
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<String, DialogueTree> trees = new ConcurrentHashMap<>();

    /**
     * NPC ID（字符串标识） → 对话树 ID 映射。
     */
    private final Map<String, String> npcBindings = new ConcurrentHashMap<>();

    /**
     * 实体类型 → 固定对话树 ID。
     */
    private final Map<EntityType<?>, String> entityBindings = new ConcurrentHashMap<>();

    /**
     * 实体类型 → 动态对话选择器（可根据实体实例和玩家状态决定对话 ID）。
     */
    private final Map<EntityType<?>, BiFunction<Entity, ServerPlayer, String>> entityDynamicBindings =
            new ConcurrentHashMap<>();

    private DialogueRegistry() {
    }


    public void register(DialogueTree tree) {
        List<String> errors = tree.validate();
        if (!errors.isEmpty()) {
            LOGGER.warn("[DialogueRegistry] Dialogue '{}' has validation errors:", tree.dialogueId());
            errors.forEach(e -> LOGGER.warn("  - {}", e));
        }
        trees.put(tree.dialogueId(), tree);
        LOGGER.debug("[DialogueRegistry] Registered dialogue: {}", tree.dialogueId());
    }

    @Nullable
    public DialogueTree get(String dialogueId) {
        return trees.get(dialogueId);
    }

    public Collection<DialogueTree> getAll() {
        return Collections.unmodifiableCollection(trees.values());
    }

    public Set<String> getAllIds() {
        return Collections.unmodifiableSet(trees.keySet());
    }

    /**
     * 绑定 NPC ID 到对话树。
     */
    public void bindNpc(String npcId, String dialogueId) {
        npcBindings.put(npcId, dialogueId);
    }

    /**
     * 获取 NPC 绑定的对话树 ID。
     */
    @Nullable
    public String getDialogueForNpc(String npcId) {
        return npcBindings.get(npcId);
    }

    /**
     * 绑定实体类型到固定对话树 ID。
     * <p>
     * 适用于不实现 {@link org.com.arc_quest.dialogue.api.IDialogueNpc} 的原版/第三方实体。
     *
     * <pre>{@code
     * DialogueRegistry.INSTANCE.bindEntity(EntityType.VILLAGER, "arc_quest:villager_greeting");
     * }</pre>
     */
    public void bindEntity(EntityType<?> entityType, String dialogueId) {
        entityBindings.put(entityType, dialogueId);
        LOGGER.debug("[DialogueRegistry] Bound entity type {} → dialogue '{}'",
                entityType, dialogueId);
    }

    /**
     * 绑定实体类型到动态对话选择器。
     * <p>
     * 选择器根据具体实体实例和玩家状态返回对话 ID，返回 null 表示不触发对话。
     *
     * <pre>{@code
     * DialogueRegistry.INSTANCE.bindEntityDynamic(EntityType.VILLAGER, (entity, player) -> {
     *     Villager villager = (Villager) entity;
     *     if (villager.getVillagerData().getProfession() == VillagerProfession.WEAPONSMITH) {
     *         return "arc_quest:weaponsmith";
     *     }
     *     return "arc_quest:villager_generic";
     * });
     * }</pre>
     */
    public void bindEntityDynamic(EntityType<?> entityType,
                                  BiFunction<Entity, ServerPlayer, String> selector) {
        entityDynamicBindings.put(entityType, selector);
        LOGGER.debug("[DialogueRegistry] Bound dynamic entity type {} → selector", entityType);
    }

    /**
     * 根据实体和玩家获取对话树 ID。
     * <p>
     * 查找顺序：
     * <ol>
     *   <li>动态绑定（{@link #bindEntityDynamic}）</li>
     *   <li>固定绑定（{@link #bindEntity}）</li>
     * </ol>
     *
     * @param entity 目标实体
     * @param player 交互的玩家
     * @return 对话树 ID，null 表示无绑定
     */
    @Nullable
    public String getDialogueForEntity(Entity entity, ServerPlayer player) {
        EntityType<?> type = entity.getType();

        BiFunction<Entity, ServerPlayer, String> dynamicSelector = entityDynamicBindings.get(type);
        if (dynamicSelector != null) {
            try {
                String result = dynamicSelector.apply(entity, player);
                if (result != null) return result;
            } catch (Exception e) {
                LOGGER.error("[DialogueRegistry] Error in dynamic entity binding for {}: {}",
                        type, e.getMessage());
            }
        }

        return entityBindings.get(type);
    }

    /**
     * 清空所有（重载前调用）。
     */
    public void clearAll() {
        trees.clear();
        npcBindings.clear();
        entityBindings.clear();
        entityDynamicBindings.clear();
        LOGGER.info("[DialogueRegistry] Cleared all dialogue trees and bindings.");
    }
}