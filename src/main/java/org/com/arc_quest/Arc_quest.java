package org.com.arc_quest;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
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
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.quest.registry.ArcQuestContent;
import org.com.arc_quest.trade.registry.TradeContent;
import org.slf4j.Logger;

@SuppressWarnings("removal")
@Mod(Arc_quest.MOD_ID)
public class Arc_quest {
    public static final String MOD_ID = "arc_quest";
    public static final Logger LOGGER = LogUtils.getLogger();

    //TODO 优化商品栏，鼠标悬停其上1s后有ToolTip和desc描述, 以及补全限购数量显示
    //TODO 商品栏区分购买条件和显示条件
    //TODO 完善任务系统的HUD，任务详情内可显示章节奖励和Phase奖励, Phase也可添加desc， 在任务追踪器和任务详情界面都可显示;如果是和实体相关的任务，任务详情界面可展示实体模型
    //TODO 任务系统HUD联动商店，新增章节商店按钮，每个章节可配置专属章节商店（可分为是否长期，长期则章节完成也可使用，非长期则不可，默认长期)
    public Arc_quest() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ArcQuestContent.registerAll();
            EpicDialogueTrees.registerAll();
            TradeContent.registerAll();
            ArcQuestNetwork.register();
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }

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