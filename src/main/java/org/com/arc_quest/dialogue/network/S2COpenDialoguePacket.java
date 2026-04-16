package org.com.arc_quest.dialogue.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.com.arc_quest.client.gui.DialogueScreen;

import java.util.function.Supplier;

/**
 * 服务端→客户端：打开/更新/关闭对话界面。
 */
public class S2COpenDialoguePacket {

    /** 空 dialogueId = 关闭对话。 */
    private final String dialogueId;
    private final String nodeId;
    private final String speaker;
    private final String text;
    private final String[] choices;
    private final boolean isTerminal;
    private final boolean hasAutoNext;
    private final int delayMs;
    private final boolean isClose;

    public S2COpenDialoguePacket(String dialogueId, String nodeId, String speaker,
                                 String text, String[] choices,
                                 boolean isTerminal, boolean hasAutoNext, int delayMs) {
        this.dialogueId = dialogueId;
        this.nodeId = nodeId;
        this.speaker = speaker;
        this.text = text;
        this.choices = choices;
        this.isTerminal = isTerminal;
        this.hasAutoNext = hasAutoNext;
        this.delayMs = delayMs;
        this.isClose = false;
    }

    private S2COpenDialoguePacket() {
        this.dialogueId = "";
        this.nodeId = "";
        this.speaker = "";
        this.text = "";
        this.choices = new String[0];
        this.isTerminal = true;
        this.hasAutoNext = false;
        this.delayMs = 0;
        this.isClose = true;
    }

    public static S2COpenDialoguePacket close() {
        return new S2COpenDialoguePacket();
    }

    // ── 序列化 ──

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(isClose);
        if (!isClose) {
            buf.writeUtf(dialogueId);
            buf.writeUtf(nodeId);
            buf.writeUtf(speaker);
            buf.writeUtf(text, 4096);
            buf.writeVarInt(choices.length);
            for (String c : choices) {
                buf.writeUtf(c, 512);
            }
            buf.writeBoolean(isTerminal);
            buf.writeBoolean(hasAutoNext);
            buf.writeVarInt(delayMs);
        }
    }

    public static S2COpenDialoguePacket decode(FriendlyByteBuf buf) {
        boolean close = buf.readBoolean();
        if (close) return close();

        String dId = buf.readUtf();
        String nId = buf.readUtf();
        String spk = buf.readUtf();
        String txt = buf.readUtf(4096);
        int count = buf.readVarInt();
        String[] choices = new String[count];
        for (int i = 0; i < count; i++) {
            choices[i] = buf.readUtf(512);
        }
        boolean terminal = buf.readBoolean();
        boolean autoNext = buf.readBoolean();
        int delay = buf.readVarInt();

        return new S2COpenDialoguePacket(dId, nId, spk, txt, choices, terminal, autoNext, delay);
    }

    public static void handle(S2COpenDialoguePacket pkt,
                              Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (pkt.isClose) {
                if (mc.screen instanceof DialogueScreen) {
                    mc.setScreen(null);
                }
                return;
            }

            if (mc.screen instanceof DialogueScreen ds) {
                // 更新现有对话界面
                ds.updateNode(pkt.speaker, pkt.text, pkt.choices,
                        pkt.isTerminal, pkt.hasAutoNext, pkt.delayMs);
            } else {
                // 打开新对话界面
                mc.setScreen(new DialogueScreen(pkt.dialogueId, pkt.speaker,
                        pkt.text, pkt.choices, pkt.isTerminal, pkt.hasAutoNext, pkt.delayMs));
            }
        });
        ctx.get().setPacketHandled(true);
    }

    // ── Getter ──
    public String getDialogueId() { return dialogueId; }
}