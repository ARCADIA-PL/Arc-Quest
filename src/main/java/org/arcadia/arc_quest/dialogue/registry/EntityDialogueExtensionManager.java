package org.arcadia.arc_quest.dialogue.registry;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import com.google.common.collect.Lists;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import org.arcadia.arc_quest.dialogue.api.IEntityDialogueExtension;

import java.util.*;
import java.util.function.Consumer;

/**
 * 实体对话扩展管理器 - 管理所有注册的实体对话扩展。
 * <p>
 * 提供按实体类型查询扩展、执行扩展方法等功能。
 *
 * @author Arc Quest Team
 * @since 2.0
 */
public class EntityDialogueExtensionManager {

    /**
     * 单例实例
     */
    public static final EntityDialogueExtensionManager INSTANCE = new EntityDialogueExtensionManager();
    /**
     * 所有扩展列表
     */
    private List<IEntityDialogueExtension<?>> extensions = Lists.newArrayList();

    /**
     * 按实体类型分组的扩展映射表
     */
    private Map<EntityType<?>, List<IEntityDialogueExtension<?>>> extensionsByType = new HashMap<>();
    private boolean frozen;

    private EntityDialogueExtensionManager() {
    }

    /**
     * 无检查的交互条件判断（绕过泛型限制）
     */
    @SuppressWarnings("unchecked")
    private static boolean canInteractUnchecked(IEntityDialogueExtension<?> extension, Player player, Entity entity) {
        try {
            return ((IEntityDialogueExtension<Entity>) extension).canInteractWith(player, entity);
        } catch (ClassCastException e) {
            return false;
        }
    }

    /**
     * 注册扩展。
     *
     * @param extension 扩展实例
     */
    @SuppressWarnings("unchecked")
    public synchronized void register(IEntityDialogueExtension<?> extension) {
        if (frozen) {
            throw new IllegalStateException("EntityDialogueExtensionManager is frozen - cannot register extension for "
                    + extension.getEntityType());
        }
        EntityType<?> entityType = extension.getEntityType();

        // 注册到列表
        if (!extensions.contains(extension)) {
            extensions.add(extension);
            ArcQuestLog.debug(ArcQuestLog.Category.DIALOGUE, "Registered extension for entity type: {}",
                    entityType.getDescriptionId());
        }

        // 注册到映射表
        extensionsByType.computeIfAbsent(entityType, k -> new ArrayList<>()).add(extension);
    }

    /**
     * 批量注册扩展。
     *
     * @param extList 扩展列表
     */
    public synchronized void registerAll(List<IEntityDialogueExtension<?>> extList) {
        for (IEntityDialogueExtension<?> ext : extList) {
            register(ext);
        }
        ArcQuestLog.info(ArcQuestLog.Category.DIALOGUE, "Registered {} extensions", extList.size());
    }

    /**
     * 安全地执行实体扩展操作。
     * <p>
     * 只有满足交互条件的扩展才会被执行。
     *
     * @param player            玩家
     * @param entity            实体
     * @param extensionConsumer 扩展消费者
     */
    public void runIfExtensionExists(Player player, Entity entity, Consumer<IEntityDialogueExtension<?>> extensionConsumer) {
        if (entity == null) {
            return;
        }

        EntityType<?> entityType = entity.getType();
        if (!extensionsByType.containsKey(entityType)) {
            return;
        }

        List<IEntityDialogueExtension<?>> entityExtensions = extensionsByType.get(entityType);
        for (IEntityDialogueExtension<?> extension : entityExtensions) {
            // 检查是否允许交互（需要 unchecked 转换）
            if (canInteractUnchecked(extension, player, entity)) {
                try {
                    extensionConsumer.accept(extension);
                } catch (Exception e) {
                    ArcQuestLog.error(ArcQuestLog.Category.DIALOGUE, "Error executing extension for entity '{}': {}",
                            entityType.getDescriptionId(), e.getMessage(), e);
                }
            }
        }
    }

    /**
     * 获取指定实体类型的所有扩展。
     *
     * @param entityType 实体类型
     * @return 扩展列表（可能为空）
     */
    @SuppressWarnings("unchecked")
    public List<IEntityDialogueExtension<?>> getExtensionsForEntityType(EntityType<?> entityType) {
        return extensionsByType.getOrDefault(entityType, Collections.emptyList());
    }

    /**
     * 检查是否有扩展适用于指定实体类型。
     *
     * @param entityType 实体类型
     * @return true 有扩展，false 无扩展
     */
    public boolean hasExtensionsForEntityType(EntityType<?> entityType) {
        return extensionsByType.containsKey(entityType);
    }

    /**
     * 获取所有已注册的扩展数量。
     *
     * @return 扩展数量
     */
    public int getExtensionCount() {
        return extensions.size();
    }

    public synchronized void freeze() {
        if (frozen) return;
        frozen = true;
        extensions = List.copyOf(extensions);
        Map<EntityType<?>, List<IEntityDialogueExtension<?>>> published = new HashMap<>();
        extensionsByType.forEach((entityType, values) -> published.put(entityType, List.copyOf(values)));
        extensionsByType = Collections.unmodifiableMap(published);
        ArcQuestLog.info(ArcQuestLog.Category.DIALOGUE, "Frozen. extensions={}, entityTypes={}",
                extensions.size(), extensionsByType.size());
    }

    public boolean isFrozen() {
        return frozen;
    }

    /**
     * 清空所有扩展（用于测试或热重载）。
     */
    public synchronized void clear() {
        extensions = Lists.newArrayList();
        extensionsByType = new HashMap<>();
        frozen = false;
        ArcQuestLog.warn(ArcQuestLog.Category.DIALOGUE, "All extensions cleared");
    }
}
