package org.com.arc_quest;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.com.arc_quest.client.events.ClientEventHandler;
import org.com.arc_quest.client.gui.QuestHudOverlay;
import org.com.arc_quest.client.gui.render.QuestSplashOverlay;
import org.com.arc_quest.client.gui.render.QuestSplashRenderer;
import org.com.arc_quest.dialogue.registry.EpicDialogueTrees;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.com.arc_quest.quest.logic.QuestProgressHandler;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.quest.registry.ArcQuestContent;
import org.com.arc_quest.quest.registry.QuestRegistry;
import org.slf4j.Logger;

@SuppressWarnings("removal")
@Mod(Arc_quest.MOD_ID)
public class Arc_quest {
    public static final String MOD_ID = "arc_quest";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Arc_quest() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ArcQuestContent.registerAll();
            EpicDialogueTrees.registerAll();
            ArcQuestNetwork.register();
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {}

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("[ArcQuest] Client setup complete.");
        }

        @SubscribeEvent
        public static void onRegisterOverlays(net.minecraftforge.client.event.RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("quest_hud", QuestHudOverlay.INSTANCE);
            event.registerAboveAll("quest_splash", QuestSplashOverlay.INSTANCE);
            LOGGER.info("[ArcQuest] Overlays registered.");
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(net.minecraftforge.client.event.RegisterKeyMappingsEvent event) {
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