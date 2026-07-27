package org.arcadia.arc_quest;

import com.mojang.logging.LogUtils;
import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.api.distmarker.Dist;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.loading.FMLLoader;
import org.arcadia.arc_quest.api.event.registry.ArcQuestRegistrationEvent;
import org.arcadia.arc_quest.client.events.ClientEventHandler;
import org.arcadia.arc_quest.client.hud.QuestHudOverlay;
import org.arcadia.arc_quest.client.hud.guide.GuideSplashOverlay;
import org.arcadia.arc_quest.client.hud.gacha.GachaResultOverlay;
import org.arcadia.arc_quest.client.hud.quest.splash.QuestSplashOverlay;
import org.arcadia.arc_quest.client.hud.quest.splash.QuestSplashRenderer;
import org.arcadia.arc_quest.client.hud.questmarker.MarkerHudRenderer;
import org.arcadia.arc_quest.client.ponder.QuestPonderPlugin;
import org.arcadia.arc_quest.client.util.GuiSoundManager;
import org.arcadia.arc_quest.data.ArcQuestDataGenerators;
import org.arcadia.arc_quest.guide.registry.ArcQuestGuideContent;
import org.arcadia.arc_quest.guide.registry.GuideGroupRegistry;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.registry.QuestGroupRegistry;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;
import org.slf4j.Logger;

@Mod(Arc_Quest.MOD_ID)
public class Arc_Quest {
    public static final String MOD_ID = "arc_quest";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Arc_Quest(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, org.arcadia.arc_quest.config.ArcQuestConfig.SPEC);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ArcQuestNetwork::register);
        NeoForge.EVENT_BUS.register(this);

        // Register mod bus event listeners (replaces deprecated @EventBusSubscriber(bus=MOD))
        modEventBus.addListener(ArcQuestDataGenerators::gatherData);
        if (FMLLoader.getDist().isClient()) {
            modEventBus.addListener(ClientModEvents::onClientSetup);
            modEventBus.addListener(ClientModEvents::onRegisterLayers);
            modEventBus.addListener(ClientModEvents::onRegisterKeyMappings);
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModLoader.postEvent(new ArcQuestRegistrationEvent.Quest());
            ModLoader.postEvent(new ArcQuestRegistrationEvent.Npc());
            ModLoader.postEvent(new ArcQuestRegistrationEvent.Dialogue());
            ModLoader.postEvent(new ArcQuestRegistrationEvent.Trade());
            ModLoader.postEvent(new ArcQuestRegistrationEvent.Gacha());
            ModLoader.postEvent(new ArcQuestRegistrationEvent.Guide());

            ArcQuestGuideContent.registerAll();
            QuestRegistry.freeze();
            QuestGroupRegistry.freeze();
            TradeRegistry.freeze();
            GuideRegistry.freeze();
            GuideGroupRegistry.freeze();
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }

    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            GuiSoundManager.initDefaults();
            PonderIndex.addPlugin(new QuestPonderPlugin());
            LOGGER.info("[ArcQuest] Client setup complete.");
        }

        @SubscribeEvent
        public static void onRegisterLayers(RegisterGuiLayersEvent event) {
            event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(MOD_ID, "quest_hud"), QuestHudOverlay.INSTANCE);
            event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(MOD_ID, "quest_splash"), QuestSplashOverlay.INSTANCE);
            event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(MOD_ID, "guide_splash"), GuideSplashOverlay.INSTANCE);
            event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(MOD_ID, "gacha_result"), GachaResultOverlay.INSTANCE);
            event.registerAbove(VanillaGuiLayers.CROSSHAIR, ResourceLocation.fromNamespaceAndPath(MOD_ID, "quest_markers"), MarkerHudRenderer.INSTANCE);
            LOGGER.info("[ArcQuest] Overlays registered.");
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(ClientEventHandler.KEY_OPEN_JOURNAL);
            event.register(ClientEventHandler.KEY_OPEN_GUIDE_LIST);
        }
    }

    @EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {
            if (QuestSplashRenderer.isActive()) {
                if (!event.getName().equals(ResourceLocation.fromNamespaceAndPath(MOD_ID, "quest_splash"))) {
                    event.setCanceled(true);
                }
            }
        }
    }
}
