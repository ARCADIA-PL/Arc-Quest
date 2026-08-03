package org.arcadia.arc_quest.client.editor.quest;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.quest.editor.network.S2COpenQuestEditorPacket;
import org.arcadia.arc_quest.quest.editor.network.S2CQuestEditorResultPacket;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonReader;

public final class QuestEditorClient {
    private QuestEditorClient() {
    }

    public static void open(S2COpenQuestEditorPacket packet) {
        try {
            Minecraft.getInstance().setScreen(new QuestEditorScreen(packet.sessionId(), packet.questId(),
                    packet.sourceFileName(), packet.revision(), packet.reloadEpoch(), QuestSpecJsonReader.read(packet.json())));
        } catch (Exception exception) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(Component.literal("无法打开任务编辑器: " + exception.getMessage()), false);
            }
        }
    }

    public static void handleResult(S2CQuestEditorResultPacket packet) {
        if (Minecraft.getInstance().screen instanceof QuestEditorScreen screen) screen.handleSaveResult(packet);
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal(packet.message()), false);
        }
    }
}
