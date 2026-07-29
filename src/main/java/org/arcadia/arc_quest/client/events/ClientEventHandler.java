package org.arcadia.arc_quest.client.events;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.client.hud.guide.GuideListScreen;
import org.arcadia.arc_quest.client.hud.guide.GuidePopupOverlay;
import org.arcadia.arc_quest.client.hud.guide.GuideScreen;
import org.arcadia.arc_quest.client.hud.guide.GuideSplashRenderer;
import org.arcadia.arc_quest.client.hud.QuestHudOverlay;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.ponder.QuestIntelPanel;
import org.arcadia.arc_quest.client.hud.quest.splash.QuestSplashRenderer;
import org.arcadia.arc_quest.client.hud.quest.toast.QuestToastManager;
import org.arcadia.arc_quest.client.hud.questmarker.QuestMarkerManager;
import org.arcadia.arc_quest.guide.network.ClientGuideCache;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.trade.network.ClientTradeCache;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = Arc_Quest.MOD_ID, value = Dist.CLIENT)
public final class ClientEventHandler {
    public static final KeyMapping KEY_OPEN_JOURNAL = new KeyMapping("key.arc_quest.open_journal", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.arc_quest");
    public static final KeyMapping KEY_OPEN_GUIDE_LIST = new KeyMapping("key.arc_quest.open_guide_list", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, "key.categories.arc_quest");

    private ClientEventHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean guideScreenActive = mc.screen instanceof GuideScreen;
        if (!guideScreenActive && !GuidePopupOverlay.INSTANCE.isActive()) {
            ClientGuideCache.INSTANCE.consumePendingOpenRequest().ifPresent(request -> {
                if (mc.screen == null) {
                    GuideScreen.tryOpen(request.guideId(), request.initialPage(), request.markSeenOnClose());
                } else {
                    GuidePopupOverlay.INSTANCE.open(
                            request.guideId(), request.initialPage(), request.markSeenOnClose());
                }
            });
        }
        GuidePopupOverlay.INSTANCE.tick();

        if (KEY_OPEN_JOURNAL.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new QuestJournalScreen());
            } else if (mc.screen instanceof QuestJournalScreen) {
                mc.screen.onClose();
            }
        }

        if (KEY_OPEN_GUIDE_LIST.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new GuideListScreen());
            } else if (mc.screen instanceof GuideListScreen) {
                mc.screen.onClose();
            }
        }

        QuestToastManager.tick();
        QuestIntelPanel.tick();
    }

    @SubscribeEvent
    public static void onLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientGuideCache.INSTANCE.clear();
        ClientQuestCache.INSTANCE.clear();
        ClientTradeCache.INSTANCE.clear();
        QuestMarkerManager.INSTANCE.clear();
        QuestToastManager.clear();
        QuestSplashRenderer.clear();
        GuideSplashRenderer.clear();
        GuidePopupOverlay.INSTANCE.clear();
        QuestHudOverlay.INSTANCE.clearClientSession();
    }

    /** LoggingOut is not guaranteed for every integrated-server world switch. */
    @SubscribeEvent
    public static void onClientWorldUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        ClientGuideCache.INSTANCE.clear();
        ClientQuestCache.INSTANCE.clear();
        ClientTradeCache.INSTANCE.clear();
        QuestMarkerManager.INSTANCE.clear();
        QuestToastManager.clear();
        QuestSplashRenderer.clear();
        GuideSplashRenderer.clear();
        GuidePopupOverlay.INSTANCE.clear();
        QuestHudOverlay.INSTANCE.clearClientSession();
    }
}
