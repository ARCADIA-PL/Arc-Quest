package org.arcadia.arc_quest.client.editor.quest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.editor.network.C2SCloseQuestEditorPacket;
import org.arcadia.arc_quest.quest.editor.network.S2COpenQuestEditorPacket;
import org.arcadia.arc_quest.quest.editor.network.S2CQuestEditorResultPacket;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonReader;

@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class QuestEditorClient {
    private static S2COpenQuestEditorPacket pendingOpen;
    private static int pendingDelayTicks;

    private QuestEditorClient() {
    }

    public static void open(S2COpenQuestEditorPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof QuestEditorScreen screen && screen.isSession(packet.sessionId())) {
            return;
        }
        pendingOpen = packet;
        pendingDelayTicks = 1;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pendingOpen == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            pendingOpen = null;
            pendingDelayTicks = 0;
            return;
        }
        if (pendingDelayTicks > 0) {
            pendingDelayTicks--;
            return;
        }
        if (minecraft.screen instanceof ChatScreen) return;

        S2COpenQuestEditorPacket packet = pendingOpen;
        pendingOpen = null;
        try {
            if (minecraft.screen instanceof QuestEditorScreen screen && screen.isSession(packet.sessionId())) {
                return;
            }
            minecraft.setScreen(new QuestEditorScreen(packet.sessionId(), packet.questId(),
                    packet.sourceFileName(), packet.revision(), packet.reloadEpoch(),
                    QuestSpecJsonReader.read(packet.json())));
        } catch (Exception exception) {
            ArcQuestNetwork.sendQuestEditorClose(new C2SCloseQuestEditorPacket());
            minecraft.player.displayClientMessage(
                    Component.literal("\u65e0\u6cd5\u6253\u5f00\u4efb\u52a1\u7f16\u8f91\u5668: "
                            + exception.getMessage()), false);
        }
    }

    public static void handleResult(S2CQuestEditorResultPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof QuestEditorScreen screen) screen.handleSaveResult(packet);
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(packet.message()), false);
        }
    }
}
