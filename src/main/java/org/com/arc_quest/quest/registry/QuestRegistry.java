package org.com.arc_quest.quest.registry;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.com.arc_quest.quest.api.QuestCategory;
import org.com.arc_quest.quest.api.QuestDefinition;
import org.com.arc_quest.quest.condition.QuestCompletedCondition;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 全局任务注册表（单例）。
 * 所有 QuestDefinition 在 commonSetup 阶段注册，之后冻结为只读。
 */
public final class QuestRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<ResourceLocation, QuestDefinition> REGISTRY = new LinkedHashMap<>();
    private static boolean frozen = false;

    private QuestRegistry() {
    }

    // ════════════════════════════════════════
    //  注册
    // ════════════════════════════════════════

    /**
     * 注册一个任务定义。仅在冻结前可调用。
     *
     * @throws IllegalStateException 如果注册表已冻结或 ID 重复
     */
    public static void register(QuestDefinition definition) {
        if (frozen) {
            throw new IllegalStateException(
                    "QuestRegistry is frozen — cannot register '" + definition.getId()
                            + "' after commonSetup");
        }
        ResourceLocation id = definition.getId();
        if (REGISTRY.containsKey(id)) {
            throw new IllegalStateException("Duplicate quest ID: " + id);
        }
        REGISTRY.put(id, definition);
        LOGGER.info("[ArcQuest] Registered quest: {} ({}, {} phases)",
                id, definition.getCategory().getId(), definition.getPhaseIds().size());
    }

    /**
     * 冻结注册表。在 commonSetup 完成后调用。
     */
    public static void freeze() {
        frozen = true;
        LOGGER.info("[ArcQuest] QuestRegistry frozen. Total quests: {}", REGISTRY.size());

        // 验证跨任务引用的合法性
        validateCrossReferences();
    }

    // ════════════════════════════════════════
    //  查询
    // ════════════════════════════════════════

    @Nullable
    public static QuestDefinition get(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    public static QuestDefinition getOrThrow(ResourceLocation id) {
        QuestDefinition def = REGISTRY.get(id);
        if (def == null) {
            throw new NoSuchElementException("Unknown quest: " + id);
        }
        return def;
    }

    public static Collection<QuestDefinition> getAll() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    public static Set<ResourceLocation> getAllIds() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }

    /**
     * 按分类筛选
     */
    public static List<QuestDefinition> getByCategory(QuestCategory category) {
        return REGISTRY.values().stream()
                .filter(q -> q.getCategory() == category)
                .sorted(Comparator.comparingInt(QuestDefinition::getSortOrder))
                .collect(Collectors.toList());
    }

    public static int size() {
        return REGISTRY.size();
    }

    public static boolean isFrozen() {
        return frozen;
    }

    // ════════════════════════════════════════
    //  验证
    // ════════════════════════════════════════

    private static void validateCrossReferences() {
        int warnings = 0;
        for (QuestDefinition quest : REGISTRY.values()) {
            for (var cond : quest.getUnlockConditions()) {
                if (cond instanceof QuestCompletedCondition qcc) {
                    ResourceLocation ref = qcc.getRequiredQuestId();
                    if (!REGISTRY.containsKey(ref)) {
                        LOGGER.warn("[ArcQuest] Quest '{}' requires unknown quest '{}' as prerequisite",
                                quest.getId(), ref);
                        warnings++;
                    }
                }
            }
        }
        if (warnings > 0) {
            LOGGER.warn("[ArcQuest] Cross-reference validation: {} warning(s)", warnings);
        }
    }
    

}