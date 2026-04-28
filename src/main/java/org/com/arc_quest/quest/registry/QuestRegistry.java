package org.com.arc_quest.quest.registry;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.quest.api.ICondition;
import org.com.arc_quest.quest.api.PhaseDefinition;
import org.com.arc_quest.quest.api.QuestCategory;
import org.com.arc_quest.quest.api.QuestDefinition;
import org.com.arc_quest.quest.api.QuestTextContext;
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

    private static Map<ResourceLocation, QuestDefinition> REGISTRY = new LinkedHashMap<>();
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
     * <p>
     * 【并发安全】冻结后转换为不可变Map，确保多线程读取安全。
     */
    public static void freeze() {
        frozen = true;
        
        // 【并发防护】转换为线程安全的不可变Map
        REGISTRY = Collections.unmodifiableMap(new LinkedHashMap<>(REGISTRY));
        
        LOGGER.info("[ArcQuest] QuestRegistry frozen. Total quests: {}", REGISTRY.size());

        // 验证跨任务引用的合法性
        validateCrossReferences();
    }

    // ════════════════════════════════════════
    //  查询
    // ════════════════════════════════════════

    /**
     * 获取任务定义（直接查找）。
     *
     * @param id 完整的资源位置
     * @return 任务定义，未找到返回 null
     */
    @Nullable
    public static QuestDefinition get(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    /**
     * 获取任务定义（支持智能命名空间解析）。
     * <p>
     * - 如果 questId 包含 ":"，直接解析并查找
     * - 如果不包含 ":"，自动添加 "arc_quest:" 前缀后查找
     *
     * @param questId 任务 ID（可以是 "epic_prologue" 或 "arc_quest:epic_prologue"）
     * @return 任务定义，未找到返回 null
     */
    @Nullable
    public static QuestDefinition get(String questId) {
        ResourceLocation location;
        if (questId.contains(":")) {
            location = ResourceLocation.tryParse(questId);
        } else {
            location = ResourceLocation.fromNamespaceAndPath(Arc_quest.MOD_ID, questId);
        }
        return location != null ? REGISTRY.get(location) : null;
    }

    /**
     * 获取任务定义，不存在则抛出异常。
     */
    public static QuestDefinition getOrThrow(ResourceLocation id) {
        QuestDefinition def = REGISTRY.get(id);
        if (def == null) {
            throw new NoSuchElementException("Unknown quest: " + id);
        }
        return def;
    }

    /**
     * 获取任务定义，不存在则抛出异常（支持智能命名空间解析）。
     */
    public static QuestDefinition getOrThrow(String questId) {
        QuestDefinition def = get(questId);
        if (def == null) {
            throw new NoSuchElementException("Unknown quest: " + questId);
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

    public record QuestTextBundle(
            Component questDisplayName,
            Component questDescription,
            Component phaseDisplayName,
            Component phaseDescription
    ) {}

    /**
     * 按 questId/phaseId 获取任务文本（支持动态 QuestText 解析）。
     * <p>
     * - questId 支持带/不带命名空间。
     * - phaseId 允许为 null 或空，此时使用任务当前 initial phase。
     * - player 为 null 时返回静态 fallback 文本。
     */
    @Nullable
    public static QuestTextBundle getQuestTextById(@Nullable ServerPlayer player,
                                                    String questId,
                                                    @Nullable String phaseId) {
        QuestDefinition quest = get(questId);
        if (quest == null) {
            return null;
        }

        String resolvedPhaseId = (phaseId == null || phaseId.isEmpty())
                ? quest.getInitialPhaseId()
                : phaseId;

        PhaseDefinition phase = quest.getPhase(resolvedPhaseId);
        if (phase == null) {
            phase = quest.getInitialPhase();
            resolvedPhaseId = phase != null ? phase.getPhaseId() : resolvedPhaseId;
        }

        QuestTextContext ctx = new QuestTextContext(quest, phase, resolvedPhaseId, Map.of());

        Component questName = player != null
                ? quest.getDisplayName(player, ctx)
                : quest.getDisplayName();

        Component questDesc = player != null
                ? quest.getDescription(player, ctx)
                : quest.getDescription();

        Component phaseName = phase != null
                ? (player != null ? phase.getDisplayName(player, ctx) : phase.getDisplayName())
                : Component.empty();

        Component phaseDesc = phase != null
                ? (player != null ? phase.getDescription(player, ctx) : phase.getDescription())
                : Component.empty();

        return new QuestTextBundle(questName, questDesc, phaseName, phaseDesc);
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
                if (cond instanceof ICondition.WithRequiredQuest wrq) {
                    ResourceLocation ref = wrq.getRequiredQuestId();
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