package org.arcadia.arc_quest.data;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.data.reload.ArcQuestReloadCoordinator;
import org.arcadia.arc_quest.data.reload.ReloadSummary;
import org.arcadia.arc_quest.util.log.ArcQuestLog;
import org.jetbrains.annotations.NotNull;

@EventBusSubscriber(modid = Arc_Quest.MOD_ID)
public final class ArcQuestReloadListener extends SimplePreparableReloadListener<ArcQuestReloadCoordinator.ReloadPlan> {
    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new ArcQuestReloadListener());
        ArcQuestLog.info(ArcQuestLog.Category.QUEST_RELOAD, "Registered unified datapack reload coordinator.");
    }

    public static ReloadSummary reloadArcQuestDatapacksOnly(@NotNull ResourceManager manager) {
        ArcQuestReloadCoordinator.ReloadPlan plan = ArcQuestReloadCoordinator.INSTANCE.prepare(manager);
        return ArcQuestReloadCoordinator.INSTANCE.apply(plan);
    }

    @Override
    protected @NotNull ArcQuestReloadCoordinator.ReloadPlan prepare(@NotNull ResourceManager manager,
                                                                    @NotNull ProfilerFiller profiler) {
        profiler.push("arcquest_prepare");
        try {
            return ArcQuestReloadCoordinator.INSTANCE.prepare(manager);
        } finally {
            profiler.pop();
        }
    }

    @Override
    protected void apply(@NotNull ArcQuestReloadCoordinator.ReloadPlan plan,
                         @NotNull ResourceManager manager,
                         @NotNull ProfilerFiller profiler) {
        profiler.push("arcquest_apply");
        try {
            ArcQuestReloadCoordinator.INSTANCE.apply(plan);
        } finally {
            profiler.pop();
        }
    }
}
