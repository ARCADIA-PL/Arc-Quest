package org.arcadia.arc_quest.client.ponder;

import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.api.registration.*;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.Arc_Quest;

/**
 * Arc Quest 的 Ponder 插件入口，通过 {@code PonderIndex.addPlugin()} 注册。
 *
 * <p>各功能模块的场景统一由 {@link ArcQuestPonderSceneRegistry} 的静态方法注册。
 *
 * <p><b>添加新场景的步骤：</b>
 * <ol>
 *   <li>在对应的场景类（如 {@link QuestIntelScenes}）中编写场景方法</li>
 *   <li>在 {@link #registerQuestPhaseScenes} 中调用
 *       {@code ArcQuestPonderSceneRegistry.registerQuestPhaseIntel(...)}</li>
 *   <li>将 {@link ArcQuestPonderHelper#questPhaseId(String, String)} 返回值
 *       设置到 {@code PhaseDefinition} 的 {@code intelSceneId} 字段</li>
 *   <li>提供对应的 .nbt 结构文件到
 *       {@code resources/assets/arc_quest/ponder/} 目录</li>
 * </ol>
 */
public class QuestPonderPlugin implements PonderPlugin {

    /**
     * 注册任务阶段情报场景。
     *
     * <p>questId / phaseId 均支持两种形式：
     * <ul>
     *   <li>纯 path：{@code "epic_prologue"}</li>
     *   <li>完整 RL：{@code "arc_quest:epic_prologue"}（自动提取 path）</li>
     * </ul>
     *
     * <p>生成的 sceneId 格式：{@code arc_quest:quest_phase/{questPath}/{phasePath}}
     * <p>结构文件路径：{@code assets/arc_quest/ponder/{questPath}_{phasePath}.nbt}
     */
    private static void registerQuestPhaseScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        // ── 序章 epic_prologue — 防御村庄阶段 defend_village ─────────────
        // sceneId  : arc_quest:quest_phase/epic_prologue/defend_village
        // nbt 文件 : assets/arc_quest/ponder/epic_prologue_defend_village.nbt
        // 在 PhaseDefinition 中绑定：
        //   .intelSceneId(ArcQuestPonderHelper.questPhaseId("arc_quest:epic_prologue", "arc_quest:defend_village"))
        ArcQuestPonderSceneRegistry.registerQuestPhaseIntel(helper,
                "arc_quest:epic_prologue",
                "arc_quest:defend_village",
                ArcQuestPonderHelper.questPhaseStructure("arc_quest:epic_prologue", "arc_quest:defend_village"),
                QuestIntelScenes::demoQuestPhase1_Overview,
                QuestIntelScenes::demoQuestPhase1_Objective);

        // ── 后续在此处继续添加 ────────────────────────────────────────────
        // ArcQuestPonderSceneRegistry.registerQuestPhaseIntel(helper,
        //         "arc_quest:chapter1", "arc_quest:mine_ores",
        //         ArcQuestPonderHelper.questPhaseStructure("arc_quest:chapter1", "arc_quest:mine_ores"),
        //         QuestIntelScenes::chapter1MineOres);
    }

    @Override
    public String getModId() {
        return Arc_Quest.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        registerQuestPhaseScenes(helper);
        // registerDialogueScenes(helper);
        // registerTradeScenes(helper);
        // registerTutorialScenes(helper);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        PonderPlugin.super.registerTags(helper);
    }

    @Override
    public void registerSharedText(SharedTextRegistrationHelper helper) {
        PonderPlugin.super.registerSharedText(helper);
    }

    @Override
    public void onPonderLevelRestore(PonderLevel ponderLevel) {
        PonderPlugin.super.onPonderLevelRestore(ponderLevel);
    }

    @Override
    public void indexExclusions(IndexExclusionHelper helper) {
        PonderPlugin.super.indexExclusions(helper);
    }
}
