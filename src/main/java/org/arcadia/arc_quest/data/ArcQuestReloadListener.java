package org.arcadia.arc_quest.data;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.dialogue.io.DialogueDatapackHotReloadService;
import org.arcadia.arc_quest.dialogue.registry.DialogueRegistry;
import org.arcadia.arc_quest.npc.io.NpcDatapackHotReloadService;
import org.arcadia.arc_quest.trade.io.TradeDatapackHotReloadService;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ArcQuestReloadListener extends SimplePreparableReloadListener<Map<ResourceLocation, Object>> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DialogueDatapackHotReloadService DIALOGUE_HOT_RELOAD_SERVICE = new DialogueDatapackHotReloadService();
    private static final NpcDatapackHotReloadService NPC_HOT_RELOAD_SERVICE = new NpcDatapackHotReloadService();
    private static final TradeDatapackHotReloadService TRADE_HOT_RELOAD_SERVICE = new TradeDatapackHotReloadService();
    private static final ArcQuestDatapackHotReloadService HOT_RELOAD_SERVICE = new ArcQuestDatapackHotReloadService();

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new ArcQuestReloadListener());
        LOGGER.info("[ArcQuest] Registered datapack reload listener.");
    }

    public static int reloadArcQuestDatapacksOnly(@NotNull ResourceManager manager) {
        DialogueRegistry.INSTANCE.clearDatapack();
        TradeRegistry.clearDatapack();
        var dialogueResult = DIALOGUE_HOT_RELOAD_SERVICE.reload();
        var npcResult = NPC_HOT_RELOAD_SERVICE.reload();
        var tradeResult = TRADE_HOT_RELOAD_SERVICE.reload();
        var result = HOT_RELOAD_SERVICE.reload(manager);
        LOGGER.info("[ArcQuest] ArcQuest-only datapack reload complete. dialogueScanned={}, dialogueDiscovered={}, dialogueFailed={}, dialogueActiveDatapack={}, npcScanned={}, npcDiscovered={}, npcFailed={}, npcActiveBindings={}, tradeScanned={}, tradeLoaded={}, tradeFailed={}, scanned={}, loaded={}, failed={}, activeDatapack={}, merged={}, source={}",
                dialogueResult.scanned(), dialogueResult.discovered(), dialogueResult.failed(), dialogueResult.activeDatapack(),
                npcResult.scanned(), npcResult.discovered(), npcResult.failed(), npcResult.activeBindings(),
                tradeResult.scanned(), tradeResult.loaded(), tradeResult.failed(),
                result.scanned(), result.loaded(), result.failed(), result.activeDatapack(), result.merged(), result.usedFallback() ? "fallback" : "@datapack");
        return result.loaded();
    }

    @Override
    protected @NotNull Map<ResourceLocation, Object> prepare(@NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
        return Collections.emptyMap();
    }

    @Override
    protected void apply(@NotNull Map<ResourceLocation, Object> ignored,
                         @NotNull ResourceManager manager,
                         @NotNull ProfilerFiller profiler) {
        profiler.startTick();
        reloadArcQuestDatapacksOnly(manager);
        profiler.endTick();
    }
}
