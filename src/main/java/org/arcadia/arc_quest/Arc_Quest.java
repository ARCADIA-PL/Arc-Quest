package org.arcadia.arc_quest;

import com.mojang.logging.LogUtils;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.arcadia.arc_quest.client.events.ClientEventHandler;
import org.arcadia.arc_quest.client.hud.QuestHudOverlay;
import org.arcadia.arc_quest.client.hud.gacha.GachaResultOverlay;
import org.arcadia.arc_quest.client.hud.quest.splash.QuestSplashOverlay;
import org.arcadia.arc_quest.client.hud.quest.splash.QuestSplashRenderer;
import org.arcadia.arc_quest.client.hud.questmarker.MarkerHudRenderer;
import org.arcadia.arc_quest.client.ponder.QuestPonderPlugin;
import org.arcadia.arc_quest.client.util.GuiSoundManager;
import org.arcadia.arc_quest.dialogue.registry.EpicDialogueTrees;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.registry.ArcQuestContent;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.trade.gacha.registry.DemoGachaShops;
import org.arcadia.arc_quest.trade.registry.TradeContent;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;
import org.slf4j.Logger;

@SuppressWarnings("removal")
@Mod(Arc_Quest.MOD_ID)
public class Arc_Quest {
    public static final String MOD_ID = "arc_quest";
    public static final Logger LOGGER = LogUtils.getLogger();

    // TODO: 传送罗盘模块 可用于传送任务相关地点

    public Arc_Quest() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ArcQuestContent.registerAll();
            EpicDialogueTrees.registerAll();
            TradeContent.registerAll();

            DemoGachaShops.registerDemoShops();
            LOGGER.info("[ArcQuest] Demo gacha shops registered.");

            ArcQuestNetwork.register();

            QuestRegistry.freeze();
            TradeRegistry.freeze();
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            GuiSoundManager.initDefaults();
            PonderIndex.addPlugin(new QuestPonderPlugin());
            LOGGER.info("[ArcQuest] Client setup complete.");
        }

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("quest_hud", QuestHudOverlay.INSTANCE);
            event.registerAboveAll("quest_splash", QuestSplashOverlay.INSTANCE);
            event.registerAboveAll("gacha_result", GachaResultOverlay.INSTANCE);
            event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "quest_markers", MarkerHudRenderer.INSTANCE);

            LOGGER.info("[ArcQuest] Overlays registered.");
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(ClientEventHandler.KEY_OPEN_JOURNAL);
        }
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event) {
            if (QuestSplashRenderer.isActive()) {
                if (!(event.getOverlay().overlay() instanceof QuestSplashOverlay)) {
                    event.setCanceled(true);
                }
            }
        }
    }
}