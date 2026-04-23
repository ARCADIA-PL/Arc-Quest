package org.com.arc_quest.client.ponder;

import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.PonderStoryBoard;
import net.minecraft.resources.ResourceLocation;
import org.com.arc_quest.Arc_quest;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Arc Quest 各功能模块的 Ponder 场景注册中心。
 *
 * <p><b>场景 ID 命名约定：</b>
 * <pre>
 *   任务阶段情报:  arc_quest:quest_phase/{questId}/{phaseId}
 *   对话系统:     arc_quest:dialogue/{dialogueId}
 *   交易系统:     arc_quest:trade/{shopId}
 *   通用教程:     arc_quest:tutorial/{topicId}
 * </pre>
 *
 * <p><b>结构文件路径（Ponder 加载规则）：</b>
 * <pre>
 *   ResourceLocation("arc_quest", "my_structure")
 *   → assets/arc_quest/ponder/my_structure.nbt
 * </pre>
 *
 * <p><b>注册示例：</b>
 * <pre>
 *   ArcQuestPonderSceneRegistry.registerQuestPhaseIntel(helper,
 *       "my_quest", "phase_defeat_boss",
 *       MyQuestScenes::showBossArena,
 *       MyQuestScenes::showBossWeakness);
 *
 *   // PhaseDefinition 中引用：
 *   ArcQuestPonderHelper.questPhaseId("my_quest", "phase_defeat_boss")
 *   // → ResourceLocation("arc_quest", "quest_phase/my_quest/phase_defeat_boss")
 * </pre>
 *
 * <p><b>结构文件路径约定：</b>
 * <pre>
 *   src/main/resources/data/arc_quest/ponder/{structureName}.nbt
 * </pre>
 */
public final class ArcQuestPonderSceneRegistry {

    /** 已注册的任务阶段情报场景 ID 集合，供 hasScene() 快速检测。 */
    static final Set<ResourceLocation> REGISTERED_QUEST_PHASE_IDS = ConcurrentHashMap.newKeySet();
    /** 已注册的对话场景 ID 集合。 */
    static final Set<ResourceLocation> REGISTERED_DIALOGUE_IDS = ConcurrentHashMap.newKeySet();
    /** 已注册的交易场景 ID 集合。 */
    static final Set<ResourceLocation> REGISTERED_TRADE_IDS = ConcurrentHashMap.newKeySet();
    /** 通用教程场景 ID 集合。 */
    static final Set<ResourceLocation> REGISTERED_TUTORIAL_IDS = ConcurrentHashMap.newKeySet();

    private ArcQuestPonderSceneRegistry() {}

    // ── 任务阶段情报 ──────────────────────────────────────────────────

    /**
     * 注册一个任务阶段的情报 Ponder 场景组。
     *
     * @param helper    来自 {@code PonderPlugin.registerScenes()} 的注册 Helper
     * @param questId   任务 ID（如 "demo_quest"，不含命名空间）
     * @param phaseId   阶段 ID（如 "phase_defeat_boss"）
     * @param structure 结构文件名（不含 .nbt，相对 ponder/ 目录）
     * @param scenes    场景构建函数，可传多个（形成多场景导航）
     */
    public static void registerQuestPhaseIntel(
            PonderSceneRegistrationHelper<ResourceLocation> helper,
            String questId,
            String phaseId,
            String structure,
            PonderStoryBoard... scenes) {

        ResourceLocation sceneId = ArcQuestPonderHelper.questPhaseId(questId, phaseId);
        REGISTERED_QUEST_PHASE_IDS.add(sceneId);

        var component = helper
                .withKeyFunction((ResourceLocation id) -> id)
                .forComponents(sceneId);

        ResourceLocation structureRl = ResourceLocation.fromNamespaceAndPath(Arc_quest.MOD_ID, structure);
        for (PonderStoryBoard scene : scenes) {
            component.addStoryBoard(structureRl, scene);
        }
    }

    /**
     * 使用默认结构文件名（{questId}_{phaseId}）注册任务阶段情报场景。
     */
    public static void registerQuestPhaseIntel(
            PonderSceneRegistrationHelper<ResourceLocation> helper,
            String questId,
            String phaseId,
            PonderStoryBoard... scenes) {
        registerQuestPhaseIntel(helper, questId, phaseId,
                questId + "_" + phaseId, scenes);
    }

    // ── 对话系统（预留，后续对话模块扩展） ──────────────────────────────

    /**
     * 注册一个对话情景 Ponder 场景。
     * 触发：{@code ArcQuestPonderHelper.dialogueId(dialogueId)}
     */
    public static void registerDialogueScene(
            PonderSceneRegistrationHelper<ResourceLocation> helper,
            String dialogueId,
            String structure,
            PonderStoryBoard... scenes) {

        ResourceLocation sceneId = ArcQuestPonderHelper.dialogueId(dialogueId);
        REGISTERED_DIALOGUE_IDS.add(sceneId);

        var component = helper
                .withKeyFunction((ResourceLocation id) -> id)
                .forComponents(sceneId);

        ResourceLocation structureRl = ResourceLocation.fromNamespaceAndPath(Arc_quest.MOD_ID, structure);
        for (PonderStoryBoard scene : scenes) {
            component.addStoryBoard(structureRl, scene);
        }
    }

    // ── 交易系统（预留） ──────────────────────────────────────────────

    /**
     * 注册一个交易商店介绍 Ponder 场景。
     * 触发：{@code ArcQuestPonderHelper.tradeId(shopId)}
     */
    public static void registerTradeScene(
            PonderSceneRegistrationHelper<ResourceLocation> helper,
            String shopId,
            String structure,
            PonderStoryBoard... scenes) {

        ResourceLocation sceneId = ArcQuestPonderHelper.tradeId(shopId);
        REGISTERED_TRADE_IDS.add(sceneId);

        var component = helper
                .withKeyFunction((ResourceLocation id) -> id)
                .forComponents(sceneId);

        ResourceLocation structureRl = ResourceLocation.fromNamespaceAndPath(Arc_quest.MOD_ID, structure);
        for (PonderStoryBoard scene : scenes) {
            component.addStoryBoard(structureRl, scene);
        }
    }

    // ── 通用教程 ──────────────────────────────────────────────────────

    /**
     * 注册一个通用系统教程 Ponder 场景。
     * 触发：{@code ArcQuestPonderHelper.tutorialId(topicId)}
     */
    public static void registerTutorialScene(
            PonderSceneRegistrationHelper<ResourceLocation> helper,
            String topicId,
            String structure,
            PonderStoryBoard... scenes) {

        ResourceLocation sceneId = ArcQuestPonderHelper.tutorialId(topicId);
        REGISTERED_TUTORIAL_IDS.add(sceneId);

        var component = helper
                .withKeyFunction((ResourceLocation id) -> id)
                .forComponents(sceneId);

        ResourceLocation structureRl = ResourceLocation.fromNamespaceAndPath(Arc_quest.MOD_ID, structure);
        for (PonderStoryBoard scene : scenes) {
            component.addStoryBoard(structureRl, scene);
        }
    }

    // ── 查询工具 ──────────────────────────────────────────────────────

    /** 检查给定 sceneId 是否已注册过场景（任意类型）。 */
    public static boolean hasScene(ResourceLocation sceneId) {
        return REGISTERED_QUEST_PHASE_IDS.contains(sceneId)
                || REGISTERED_DIALOGUE_IDS.contains(sceneId)
                || REGISTERED_TRADE_IDS.contains(sceneId)
                || REGISTERED_TUTORIAL_IDS.contains(sceneId);
    }

    /** 检查给定任务阶段是否已注册情报场景。 */
    public static boolean hasQuestPhaseIntel(String questId, String phaseId) {
        return REGISTERED_QUEST_PHASE_IDS.contains(ArcQuestPonderHelper.questPhaseId(questId, phaseId));
    }
}
