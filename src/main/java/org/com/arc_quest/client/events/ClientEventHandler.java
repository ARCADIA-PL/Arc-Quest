package org.com.arc_quest.client.events;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.client.hud.quest.toast.QuestToastManager;
import org.com.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.com.arc_quest.client.hud.quest.ponder.QuestIntelPanel;
import org.com.arc_quest.client.hud.questmarker.QuestMarkerManager;
import org.com.arc_quest.quest.network.ClientQuestCache;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Arc_quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEventHandler {

    public static final KeyMapping KEY_OPEN_JOURNAL = new KeyMapping(
            "key.arc_quest.open_journal",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories.arc_quest"
    );

    private ClientEventHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (KEY_OPEN_JOURNAL.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new QuestJournalScreen());
            } else if (mc.screen instanceof QuestJournalScreen) {
                mc.screen.onClose();
            }
        }

        QuestToastManager.tick();
        QuestIntelPanel.tick();
    }

    @SubscribeEvent
    public static void onLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientQuestCache.INSTANCE.clear();
        QuestMarkerManager.INSTANCE.clear();
        QuestToastManager.clear();
    }
}
