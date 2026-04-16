package org.com.arc_quest.data;

import com.mojang.logging.LogUtils;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.dialogue.registry.TestDialogues;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

/**
 * 数据包重载监听器（仅用于触发代码注册的任务重新加载）。
 * <p>
 * Arc Quest 采用纯代码驱动，所有任务在 {@link org.com.arc_quest.quest.registry.ArcQuestContent} 中注册。
 * 执行 /reload 命令时会记录日志，方便开发时确认系统状态。
 */
@Mod.EventBusSubscriber(modid = Arc_quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ArcQuestReloadListener extends SimplePreparableReloadListener<Void> {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new ArcQuestReloadListener());
        LOGGER.info("[ArcQuest] Registered datapack reload listener (code-driven quests).");
    }

    @Override
    protected @NotNull Void prepare(@NotNull ResourceManager manager,
                                    @NotNull ProfilerFiller profiler) {
        profiler.startTick();
        profiler.endTick();
        return null;
    }

    @Override
    protected void apply(@NotNull Void result,
                         @NotNull ResourceManager manager,
                         @NotNull ProfilerFiller profiler) {
        profiler.startTick();

        // 重新注册测试对话（防止reload后丢失）
        TestDialogues.registerAll();
        LOGGER.info("[ArcQuest] Test dialogues re-registered after reload.");

        profiler.endTick();
    }
}
