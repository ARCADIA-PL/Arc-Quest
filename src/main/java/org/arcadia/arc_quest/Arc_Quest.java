package org.arcadia.arc_quest;

import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
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
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.arcadia.arc_quest.client.events.ClientEventHandler;
import org.arcadia.arc_quest.client.hud.QuestHudOverlay;
import org.arcadia.arc_quest.client.hud.dialogue.DialogueScreen;
import org.arcadia.arc_quest.client.hud.guide.GuideSplashOverlay;
import org.arcadia.arc_quest.client.hud.gacha.GachaResultOverlay;
import org.arcadia.arc_quest.client.hud.quest.splash.QuestSplashOverlay;
import org.arcadia.arc_quest.client.hud.quest.splash.QuestSplashRenderer;
import org.arcadia.arc_quest.client.hud.questmarker.MarkerHudRenderer;
import org.arcadia.arc_quest.client.ponder.QuestPonderPlugin;
import org.arcadia.arc_quest.client.util.GuiSoundManager;
import org.arcadia.arc_quest.api.event.registry.ArcQuestRegistrationEvent;
import org.arcadia.arc_quest.config.ArcQuestConfig;
import org.arcadia.arc_quest.config.ArcQuestLogConfig;
import org.arcadia.arc_quest.config.ArcQuestTextConfig;
import org.arcadia.arc_quest.config.ArcQuestToastConfig;
import org.arcadia.arc_quest.dialogue.registry.EpicDialogueTrees;
import org.arcadia.arc_quest.dialogue.registry.DialogueRegistry;
import org.arcadia.arc_quest.dialogue.registry.EntityDialogueExtensionManager;
import org.arcadia.arc_quest.guide.registry.ArcQuestGuideContent;
import org.arcadia.arc_quest.guide.registry.GuideGroupRegistry;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.registry.ArcQuestContent;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questplayer.capability.ArcQuestCapabilities;
import org.arcadia.arc_quest.quest.registry.QuestGroupRegistry;
import org.arcadia.arc_quest.questmarker.runtime.BuiltInMarkTargetResolvers;
import org.arcadia.arc_quest.npc.runtime.NpcBindingRegistry;
import org.arcadia.arc_quest.trade.gacha.registry.GachaRegistry;
import org.arcadia.arc_quest.trade.gacha.registry.DemoGachaShops;
import org.arcadia.arc_quest.trade.registry.TradeContent;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;
import org.arcadia.arc_quest.util.log.ArcQuestLog;
import org.slf4j.Logger;

@SuppressWarnings("removal")
@Mod(Arc_Quest.MOD_ID)
public class Arc_Quest {
    public static final String MOD_ID = "arc_quest";
    public static final Logger LOGGER = ArcQuestLog.rawLogger();

    public Arc_Quest() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ArcQuestConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT,
                ArcQuestToastConfig.SPEC, ArcQuestToastConfig.FILE_NAME);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT,
                ArcQuestTextConfig.SPEC, ArcQuestTextConfig.FILE_NAME);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON,
                ArcQuestLogConfig.SPEC, ArcQuestLogConfig.FILE_NAME);
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onConfigLoading);
        modEventBus.addListener(ArcQuestCapabilities::register);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            BuiltInMarkTargetResolvers.registerAll();
            ArcQuestContent.registerAll();
            EpicDialogueTrees.registerAll();
            TradeContent.registerAll();
            ArcQuestGuideContent.registerAll();
            DemoGachaShops.registerDemoShops();
            ArcQuestLog.info(ArcQuestLog.Category.CORE, "Demo gacha shops registered.");
            ModLoader.get().postEvent(new ArcQuestRegistrationEvent.Quest());
            ModLoader.get().postEvent(new ArcQuestRegistrationEvent.Dialogue());
            ModLoader.get().postEvent(new ArcQuestRegistrationEvent.Npc());
            ModLoader.get().postEvent(new ArcQuestRegistrationEvent.Trade());
            ModLoader.get().postEvent(new ArcQuestRegistrationEvent.Gacha());
            ModLoader.get().postEvent(new ArcQuestRegistrationEvent.Guide());
            ArcQuestNetwork.register();
            QuestRegistry.freeze();
            QuestGroupRegistry.freeze();
            DialogueRegistry.INSTANCE.freeze();
            NpcBindingRegistry.INSTANCE.freeze();
            EntityDialogueExtensionManager.INSTANCE.freeze();
            TradeRegistry.freeze();
            GachaRegistry.freeze();
            GuideRegistry.freeze();
            GuideGroupRegistry.freeze();
        });
    }

    private void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == ArcQuestLogConfig.SPEC) {
            ArcQuestLog.loadFromConfig();
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onRegisterClientReloadListeners(
                net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                    manager -> org.arcadia.arc_quest.client.hud.dialogue.DialogueHistoryPanel.invalidateLayout());
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            GuiSoundManager.initDefaults();
            PonderIndex.addPlugin(new QuestPonderPlugin());
            ArcQuestLog.info(ArcQuestLog.Category.CORE, "Client setup complete.");
        }

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("quest_hud", QuestHudOverlay.INSTANCE);
            event.registerAboveAll("quest_splash", QuestSplashOverlay.INSTANCE);
            event.registerAboveAll("guide_splash", GuideSplashOverlay.INSTANCE);
            event.registerAboveAll("gacha_result", GachaResultOverlay.INSTANCE);
            event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "quest_markers", MarkerHudRenderer.INSTANCE);
            ArcQuestLog.info(ArcQuestLog.Category.CORE, "Overlays registered.");
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            ClientEventHandler.registerKeyMappings(event);
        }
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event) {
            if (event.getOverlay().id().equals(VanillaGuiOverlay.CHAT_PANEL.id())
                    && Minecraft.getInstance().screen instanceof DialogueScreen) {
                event.setCanceled(true);
                return;
            }
            if (QuestSplashRenderer.isActive()) {
                if (!(event.getOverlay().overlay() instanceof QuestSplashOverlay)) {
                    event.setCanceled(true);
                }
            }
        }
    }
}
