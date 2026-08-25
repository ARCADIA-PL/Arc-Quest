package org.arcadia.arc_quest.quest.data;
import org.arcadia.arc_quest.util.log.ArcQuestLog;
import org.slf4j.Logger;

import net.minecraft.nbt.CompoundTag;

import java.util.function.Consumer;

/**
 * NBT 版本管理器。
 *
 * <p>统一管理 Attachment 的 NBT 版本号,支持链式自动迁移。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 在玩家数据管理类中
 * private static final NbtVersionManager VERSION_MANAGER = new NbtVersionManager(
 *     "arc_quest:player_data",
 *     3,  // 当前最新版本
 *     logger
 * );
 *
 * static {
 *     // v0 → v1: 添加 flags 字段
 *     VERSION_MANAGER.addMigration(0, 1, tag -> {
 *         if (!tag.contains("flags")) {
 *             tag.put("flags", new CompoundTag());
 *         }
 *     });
 *
 *     // v1 → v2: 重构 objectives 结构
 *     VERSION_MANAGER.addMigration(1, 2, tag -> {
 *         CompoundTag objectives = tag.getCompound("objectives");
 *         // ... 迁移逻辑
 *         tag.put("objectives", objectives);
 *     });
 *
 *     // v2 →添加 variables 字段
 *     VERSION_MANAGER.addMigration(2, 3, tag -> {
 *         if (!tag.contains("variables")) {
 *             tag.put("variables", new CompoundTag());
 *         }
 *     });
 * }
 *
 * @Override
 * public void loadNbt(CompoundTag tag) {
 *     VERSION_MANAGER.migrate(tag);  // 自动从 v0/v1/v2 → v3
 *     // ... 读取数据
 * }
 * }</pre>
 *
 * <h2>迁移策略</h2>
 * <ul>
 *   <li><b>链式迁移</b>: v0 → v1 → v2 → v3,每步独立可测试</li>
 *   <li><b>幂等性</b>: 多次调用 migrate() 不会重复迁移</li>
 *   <li><b>日志记录</b>: 每次迁移都会记录日志,便于调试</li>
 *   <li><b>异常安全</b>: 迁移失败会抛出 RuntimeException,阻止加载损坏数据</li>
 * </ul>
 */
public final class NbtVersionManager {
    // 统一使用 _ArcQuestVer
    private static final String VERSION_KEY = "_ArcQuestVer";

    private final String dataName;
    private final int currentVersion;

    // 迁移函数数组
    private final Consumer<CompoundTag>[] migrations;

    @SuppressWarnings("unchecked")
    public NbtVersionManager(String dataName, int currentVersion, Logger logger) {
        this.dataName = dataName;
        this.currentVersion = currentVersion;
        migrations = new Consumer[currentVersion];
    }

    /**
     * 添加从 fromVersion 到 fromVersion+1 的迁移函数。
     *
     * @param fromVersion 起始版本
     * @param toVersion   目标版本(必须 = fromVersion + 1)
     * @param migration   迁移函数,接收 CompoundTag 并原地修改
     * @throws IllegalArgumentException 如果 toVersion != fromVersion + 1
     */
    public void addMigration(int fromVersion, int toVersion, Consumer<CompoundTag> migration) {
        if (toVersion != fromVersion + 1) {
            throw new IllegalArgumentException(
                    String.format("Invalid version jump: %d → %d. Must be sequential.",
                            fromVersion, toVersion)
            );
        }
        if (fromVersion < 0 || fromVersion >= currentVersion) {
            throw new IllegalArgumentException(
                    String.format("Version %d out of range [0, %d)",
                            fromVersion, currentVersion)
            );
        }
        migrations[fromVersion] = migration;
    }

    /**
     * 执行迁移，将 tag 升级到最新版本。
     *
     * @param tag 要迁移的 NBT 标签
     */
    public void migrate(CompoundTag tag) {
        int storedVersion = tag.getInt(VERSION_KEY);

        if (storedVersion == currentVersion) {
            // 已是最新版本
            return;
        }

        if (storedVersion > currentVersion) {
            throw new RuntimeException(
                    String.format("[%s] Stored version %d is higher than current version %d. " +
                            "Downgrade is not supported!", dataName, storedVersion, currentVersion)
            );
        }

        ArcQuestLog.info(ArcQuestLog.Category.PERSISTENCE, "[{}] Migrating NBT data from v{} to v{}...",
                dataName, storedVersion, currentVersion);

        // 链式迁移
        for (int v = storedVersion; v < currentVersion; v++) {
            if (migrations[v] == null) {
                throw new RuntimeException(
                        String.format("[%s] No migration defined for v%d → v%d",
                                dataName, v, v + 1)
                );
            }

            try {
                ArcQuestLog.debug(ArcQuestLog.Category.PERSISTENCE, "[{}] Applying migration v{} → v{}...",
                        dataName, v, v + 1);
                migrations[v].accept(tag);
            } catch (Exception e) {
                throw new RuntimeException(
                        String.format("[%s] Migration v%d → v%d failed: %s",
                                dataName, v, v + 1, e.getMessage()),
                        e
                );
            }
        }

        // 保存版本号
        tag.putInt(VERSION_KEY, currentVersion);
        ArcQuestLog.info(ArcQuestLog.Category.PERSISTENCE, "[{}] Migration complete. Now at v{}.", dataName, currentVersion);
    }

    /**
     * 获取当前最新版本号。
     */
    public int getCurrentVersion() {
        return currentVersion;
    }

    /**
     * 为新的 CompoundTag 设置最新版本号。
     *
     * @param tag 新创建的 NBT 标签
     */
    public void setInitialVersion(CompoundTag tag) {
        tag.putInt(VERSION_KEY, currentVersion);
    }
}
