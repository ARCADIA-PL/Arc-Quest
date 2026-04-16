package org.com.arc_quest;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
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
import org.com.arc_quest.dialogue.registry.TestDialogues;
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

        // ─── Phase 1 + Phase 2：通用初始化 ───
        modEventBus.addListener(this::commonSetup);

        // ─── Forge 总线：服务端事件 ───
        MinecraftForge.EVENT_BUS.register(this);

        // ─── Capability / QuestEventManager 通过 @EventBusSubscriber 自动注册 ───
    }

    /**
     * 通用设置（客户端和服务端共享）。
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Phase 1：注册预设任务定义
            ArcQuestContent.registerAll();
            LOGGER.info("[ArcQuest] Quest definitions registered.");

            // Phase 1.5：注册测试对话
            TestDialogues.registerAll();
            LOGGER.info("[ArcQuest] Test dialogues registered.");

            // Phase 2：注册网络数据包
            ArcQuestNetwork.register();
            LOGGER.info("[ArcQuest] Network packets registered.");
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[ArcQuest] Server starting — {} quests loaded.",
                QuestRegistry.getAll().size());
    }

    /**
     * 玩家首次加入游戏时自动给予序章任务。
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var cap = player.getCapability(
                    QuestCapabilityProvider.QUEST_CAP
            ).orElse(null);
            
            if (cap.getAllActiveQuests().isEmpty()) {
                boolean success = QuestProgressHandler.acceptQuest(player, "arc_quest:epic_prologue");
                if (success) {
                    LOGGER.info("[ArcQuest] Auto-gave epic_prologue to player: {}", player.getName().getString());
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    //  客户端 — Phase 3：GUI / HUD / Toast
    // ═══════════════════════════════════════════════════════

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("[ArcQuest] Client setup complete.");
        }

        /**
         * 注册 HUD 叠加层。
         */
        @SubscribeEvent
        public static void onRegisterOverlays(net.minecraftforge.client.event.RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("quest_hud",
                    QuestHudOverlay.INSTANCE);
            LOGGER.info("[ArcQuest] Quest HUD overlay registered.");
        }

        /**
         * 注册快捷键。
         */
        @SubscribeEvent
        public static void onRegisterKeyMappings(net.minecraftforge.client.event.RegisterKeyMappingsEvent event) {
            event.register(ClientEventHandler.KEY_OPEN_JOURNAL);
            LOGGER.info("[ArcQuest] Keybinding registered.");
        }
    }
}