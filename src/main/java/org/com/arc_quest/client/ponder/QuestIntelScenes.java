package org.com.arc_quest.client.ponder;

import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Arc Quest 内建示范 Ponder 场景。
 *
 * <p>这里展示两类示范：
 * <ol>
 *   <li><b>demoQuestPhase1_Overview</b>：任务阶段概览，展示基地板 + 文字说明</li>
 *   <li><b>demoQuestPhase1_Objective</b>：目标区域高亮，演示如何在特定方块旁显示提示</li>
 * </ol>
 *
 * <p>结构文件：{@code data/arc_quest/ponder/demo_quest_phase_1.nbt}
 * （实际内容由 Ponder 结构文件工具生成，这里用最小空场景代替）
 *
 * <p><b>场景编写规范：</b>
 * <pre>
 *   1. 先调用 scene.title()，必须与 lang 文件中的 key 对应
 *   2. 调用 scene.showBasePlate() 展示基板
 *   3. 用 util.select.xxx() 选择区域
 *   4. 用 scene.world().showSection() 分步揭示场景
 *   5. 用 scene.overlay().showText() 添加文字说明
 *   6. 用 scene.idle() 控制节奏
 *   7. 末尾调用 scene.markAsFinished()
 * </pre>
 */
public final class QuestIntelScenes {

    private QuestIntelScenes() {}

    /**
     * 示范场景 1：任务阶段概览。
     *
     * <p>展示整个结构，配合场景文字说明任务目标区域。
     * 注册方式见 {@link QuestPonderPlugin#registerScenes}。
     */
    public static void demoQuestPhase1_Overview(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("demo_quest_phase_1_overview", "Phase Overview: The Proving Ground");

        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        // 展示整个场景区域
        Selection allBlocks = util.select().everywhere();
        scene.world().showSection(allBlocks, Direction.DOWN);
        scene.idle(15);

        // 任务目标说明
        scene.overlay().showText(60)
                .text("This is the Proving Ground arena. Your goal in Phase 1 is to reach the center platform.")
                .pointAt(new Vec3(2.5, 2, 2.5))
                .attachKeyFrame();
        scene.idle(70);

        // 高亮目标区域
        Selection targetArea = util.select().fromTo(1, 0, 1, 3, 2, 3);
        scene.overlay().showOutline(PonderPalette.GREEN, "target", targetArea, 60);
        scene.overlay().showText(60)
                .text("Highlighted in green: the objective area. Stand inside to complete the phase.")
                .pointAt(new Vec3(2.5, 3, 2.5))
                .attachKeyFrame();
        scene.idle(70);

        scene.markAsFinished();
    }

    /**
     * 示范场景 2：目标详细说明。
     *
     * <p>摄像机旋转到特定角度，展示目标方块细节。
     */
    public static void demoQuestPhase1_Objective(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("demo_quest_phase_1_objective", "Objective: Activate the Beacon");

        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        // 先展示基础结构
        Selection base = util.select().layer(0);
        scene.world().showSection(base, Direction.DOWN);
        scene.idle(10);

        // 再逐步展示上层
        Selection upper = util.select().fromTo(0, 1, 0, 4, 4, 4);
        scene.world().showSection(upper, Direction.DOWN);
        scene.idle(15);

        // 指向目标方块（beacon 位置）
        BlockPos beaconPos = new BlockPos(2, 1, 2);
        scene.overlay().showText(60)
                .text("Locate and activate the Beacon at the center of the arena.")
                .pointAt(Vec3.atCenterOf(beaconPos))
                .attachKeyFrame();
        scene.idle(70);

        // 旋转摄像机强调
        scene.rotateCameraY(30);
        scene.idle(10);

        scene.overlay().showText(50)
                .text("Right-click the Beacon to complete your objective. The quest phase will auto-advance.")
                .pointAt(Vec3.atCenterOf(beaconPos))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(60);

        scene.markAsFinished();
    }
}
