package org.arcadia.arc_quest.client.events;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.client.compat.marker.QuestMarkerExternalSync;
import org.arcadia.arc_quest.client.data.sync.ClientDatapackContentReceiver;
import org.arcadia.arc_quest.client.hud.guide.GuideListScreen;
import org.arcadia.arc_quest.client.hud.guide.GuidePopupOverlay;
import org.arcadia.arc_quest.client.hud.guide.GuideScreen;
import org.arcadia.arc_quest.client.hud.guide.GuideSplashRenderer;
import org.arcadia.arc_quest.client.hud.QuestHudOverlay;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.history.QuestChangeHistoryStore;
import org.arcadia.arc_quest.client.hud.quest.ponder.QuestIntelPanel;
import org.arcadia.arc_quest.client.hud.quest.splash.QuestSplashRenderer;
import org.arcadia.arc_quest.client.hud.quest.story.QuestStoryPanel;
import org.arcadia.arc_quest.client.hud.quest.toast.QuestToastManager;
import org.arcadia.arc_quest.client.hud.quest.trackingmenu.QuestTrackingMenuScreen;
import org.arcadia.arc_quest.client.hud.questmarker.QuestMarkerManager;
import org.arcadia.arc_quest.dialogue.network.ClientDialogueCache;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.network.ClientGuideCache;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.trade.network.ClientTradeCache;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = Arc_Quest.MOD_ID, value = Dist.CLIENT)
public final class ClientEventHandler {
    private static final int TRACKING_MENU_HOLD_TICKS = 4;

    public static KeyMapping KEY_OPEN_JOURNAL;
    public static KeyMapping KEY_OPEN_GUIDE_LIST;
    public static KeyMapping KEY_OPEN_TRACKING_MENU;

    private static int trackingMenuHeldTicks;
    private static boolean trackingMenuOpenedForCurrentHold;

    private ClientEventHandler() {
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        KEY_OPEN_JOURNAL = createKeyMapping("key.arc_quest.open_journal", GLFW.GLFW_KEY_J);
        KEY_OPEN_GUIDE_LIST = createKeyMapping("key.arc_quest.open_guide_list", GLFW.GLFW_KEY_I);
        KEY_OPEN_TRACKING_MENU = createKeyMapping("key.arc_quest.open_tracking_menu", GLFW.GLFW_KEY_TAB);
        event.register(KEY_OPEN_JOURNAL);
        event.register(KEY_OPEN_GUIDE_LIST);
        event.register(KEY_OPEN_TRACKING_MENU);
    }

    private static KeyMapping createKeyMapping(String translationKey, int defaultKey) {
        return new KeyMapping(translationKey, KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, defaultKey, "key.categories.arc_quest");
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            resetTrackingMenuHold();
            return;
        }

        boolean guideScreenActive = mc.screen instanceof GuideScreen;
        if (!guideScreenActive && !GuidePopupOverlay.INSTANCE.isActive()) {
            ClientGuideCache.PendingOpenRequest request =
                    ClientGuideCache.INSTANCE.getPendingOpenRequest().orElse(null);
            if (request != null) {
                GuideDefinition guide = GuideRegistry.get(request.guideId());
                boolean screenPresent = mc.screen != null;
                boolean forceOpen = guide == null || guide.getVisualConfig().shouldForceOpenWithScreen();
                if (!screenPresent || forceOpen) {
                    ClientGuideCache.INSTANCE.consumePendingOpenRequest().ifPresent(consumedRequest -> {
                        // 相关处理说明。
                        // 相关处理说明。
                        boolean dialogueActive = ClientDialogueCache.INSTANCE.getCurrentSession() != null;
                        if (shouldOpenStandaloneGuide(screenPresent, dialogueActive)) {
                            GuideScreen.tryOpen(consumedRequest.guideId(), consumedRequest.initialPage(),
                                    consumedRequest.markSeenOnClose());
                        } else {
                            GuidePopupOverlay.INSTANCE.open(consumedRequest.guideId(), consumedRequest.initialPage(),
                                    consumedRequest.markSeenOnClose());
                        }
                    });
                }
            }
        }
        GuidePopupOverlay.INSTANCE.ensureInputScreen();
        GuidePopupOverlay.INSTANCE.tick();

        if (KEY_OPEN_JOURNAL.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new QuestJournalScreen());
            } else if (mc.screen instanceof QuestJournalScreen) {
                mc.screen.onClose();
            }
        }

        tickTrackingMenuKey(mc);

        if (KEY_OPEN_GUIDE_LIST.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new GuideListScreen());
            } else if (mc.screen instanceof GuideListScreen) {
                mc.screen.onClose();
            }
        }

        QuestToastManager.tick();
        QuestIntelPanel.tick();
        QuestMarkerExternalSync.tick();
    }

    private static void tickTrackingMenuKey(Minecraft minecraft) {
        KEY_OPEN_TRACKING_MENU.consumeClick();

        if (!isTrackingMenuControlPhysicallyDown(minecraft)) {
            resetTrackingMenuHold();
            return;
        }

        if (trackingMenuHeldTicks <= TRACKING_MENU_HOLD_TICKS) {
            trackingMenuHeldTicks++;
        }
        if (!trackingMenuOpenedForCurrentHold
                && trackingMenuHeldTicks > TRACKING_MENU_HOLD_TICKS
                && minecraft.screen == null) {
            trackingMenuOpenedForCurrentHold = true;
            minecraft.setScreen(new QuestTrackingMenuScreen());
        }
    }

    private static void resetTrackingMenuHold() {
        trackingMenuHeldTicks = 0;
        trackingMenuOpenedForCurrentHold = false;
    }

    private static boolean isTrackingMenuControlPhysicallyDown(Minecraft minecraft) {
        InputConstants.Key key = KEY_OPEN_TRACKING_MENU.getKey();
        long window = minecraft.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return InputConstants.isKeyDown(window, key.getValue());
        }
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return true;
    }

    static boolean shouldOpenStandaloneGuide(boolean screenPresent, boolean dialogueActive) {
        return !screenPresent && !dialogueActive;
    }

    @SubscribeEvent
    public static void onLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        QuestMarkerExternalSync.replaceAll(QuestMarkerManager.INSTANCE.all());
    }

    @SubscribeEvent
    public static void onQuestMarkerSnapshot(QuestMarkerClientSnapshotEvent event) {
        QuestMarkerExternalSync.replaceAll(event.markers());
    }

    @SubscribeEvent
    public static void onLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        resetTrackingMenuHold();
        QuestMarkerExternalSync.clear();
        ClientDatapackContentReceiver.INSTANCE.clear();
        ClientGuideCache.INSTANCE.clear();
        ClientQuestCache.INSTANCE.clear();
        QuestChangeHistoryStore.INSTANCE.flushAndResetClientSession();
        ClientTradeCache.INSTANCE.clear();
        QuestMarkerManager.INSTANCE.clear();
        QuestToastManager.clear();
        QuestSplashRenderer.clear();
        GuideSplashRenderer.clear();
        GuidePopupOverlay.INSTANCE.clear();
        QuestStoryPanel.clearClientSession();
        QuestHudOverlay.INSTANCE.clearClientSession();
    }

    /** 相关处理说明。 */
    @SubscribeEvent
    public static void onClientWorldUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        QuestMarkerExternalSync.clear();
        ClientGuideCache.INSTANCE.clear();
        ClientQuestCache.INSTANCE.clear();
        QuestChangeHistoryStore.INSTANCE.flushAndResetClientSession();
        ClientTradeCache.INSTANCE.clear();
        QuestMarkerManager.INSTANCE.clear();
        QuestToastManager.clear();
        QuestSplashRenderer.clear();
        GuideSplashRenderer.clear();
        GuidePopupOverlay.INSTANCE.clear();
        QuestStoryPanel.clearClientSession();
        QuestHudOverlay.INSTANCE.clearClientSession();
    }
}
