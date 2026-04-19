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

    /**
     * 空 dialogueId = 关闭对话。
     */
    private final String dialogueId;
    private final String nodeId;
    private final String speaker;
    private final String text;
    private final String[] choices;
    private final boolean isTerminal;
    private final boolean hasAutoNext;
    private final int delayMs;
    private final boolean isClose;

    /**
     * 关联的 NPC 实体网络 ID，-1 = 无实体。
     */
    private final int entityId;

    /**
     * 每个选项的最后选择时间戳（毫秒），0 = 未选择
     */
    private final long[] choiceLastSelectTimes;
    
    /**  每个选项选择时的 gameTime */
    private final long[] choicePurchaseGameTimes;
    
    /**  每个选项选择时的 dayTime */
    private final long[] choicePurchaseDayTimes;
    
    /**
     * 每个选项的冷却类型（ordinal）
     */
    private final int[] choiceCooldownTypes;
    
    /**
     * 每个选项的冷却值（秒或tick）
     */
    private final long[] choiceCooldownValues;
    
    /**
     * 每个选项的重置刻（仅 GAME_TICK 有效）
     */
    private final int[] choiceResetTimeTicks;

    /**
     * 原有构造器（向后兼容，entityId = -1）。
     */
    public S2COpenDialoguePacket(String dialogueId, String nodeId, String speaker,
                                 String text, String[] choices,
                                 boolean isTerminal, boolean hasAutoNext, int delayMs) {
        this(dialogueId, nodeId, speaker, text, choices, isTerminal, hasAutoNext, delayMs, -1);
    }

    /**
     * 完整构造器（带 entityId）。
     */
    public S2COpenDialoguePacket(String dialogueId, String nodeId, String speaker,
                                 String text, String[] choices,
                                 boolean isTerminal, boolean hasAutoNext, int delayMs,
                                 int entityId) {
        this(dialogueId, nodeId, speaker, text, choices, isTerminal, hasAutoNext, delayMs, entityId,
                null, null, null, null, null, null);
    }

    /**
     * 完整构造器（带冷却信息）。
     */
    public S2COpenDialoguePacket(String dialogueId, String nodeId, String speaker,
                                 String text, String[] choices,
                                 boolean isTerminal, boolean hasAutoNext, int delayMs,
                                 int entityId, long[] choiceLastSelectTimes,
                                 long[] choicePurchaseGameTimes, long[] choicePurchaseDayTimes,
                                 int[] choiceCooldownTypes, long[] choiceCooldownValues,
                                 int[] choiceResetTimeTicks) {
        this.dialogueId = dialogueId;
        this.nodeId = nodeId;
        this.speaker = speaker;
        this.text = text;
        this.choices = choices;
        this.isTerminal = isTerminal;
        this.hasAutoNext = hasAutoNext;
        this.delayMs = delayMs;
        this.isClose = false;
        this.entityId = entityId;
        this.choiceLastSelectTimes = choiceLastSelectTimes;
        this.choicePurchaseGameTimes = choicePurchaseGameTimes;
        this.choicePurchaseDayTimes = choicePurchaseDayTimes;
        this.choiceCooldownTypes = choiceCooldownTypes;
        this.choiceCooldownValues = choiceCooldownValues;
        this.choiceResetTimeTicks = choiceResetTimeTicks;
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
        this.entityId = -1;
        this.choiceLastSelectTimes = null;
        this.choicePurchaseGameTimes = null;
        this.choicePurchaseDayTimes = null;
        this.choiceCooldownTypes = null;
        this.choiceCooldownValues = null;
        this.choiceResetTimeTicks = null;
    }

    public static S2COpenDialoguePacket close() {
        return new S2COpenDialoguePacket();
    }

    // ── 序列化 ──

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
        int entityId = buf.readInt();

        // 反序列化冷却原始数据
        long[] lastSelectTimes = null;
        long[] purchaseGTs = null;
        long[] purchaseDTs = null;
        int[] cooldownTypes = null;
        long[] cooldownValues = null;
        int[] resetTimeTicks = null;
        
        if (buf.readBoolean()) {
            int cooldownCount = buf.readVarInt();
            lastSelectTimes = new long[cooldownCount];
            purchaseGTs = new long[cooldownCount];
            purchaseDTs = new long[cooldownCount];
            cooldownTypes = new int[cooldownCount];
            cooldownValues = new long[cooldownCount];
            resetTimeTicks = new int[cooldownCount];
            
            for (int i = 0; i < cooldownCount; i++) {
                lastSelectTimes[i] = buf.readLong();
                purchaseGTs[i] = buf.readLong();
                purchaseDTs[i] = buf.readLong();
                cooldownTypes[i] = buf.readVarInt();
                cooldownValues[i] = buf.readLong();
                resetTimeTicks[i] = buf.readVarInt();
            }
        }

        return new S2COpenDialoguePacket(dId, nId, spk, txt, choices, terminal, autoNext, delay, entityId,
                lastSelectTimes, purchaseGTs, purchaseDTs, cooldownTypes, cooldownValues, resetTimeTicks);
    }

    public static void handle(S2COpenDialoguePacket pkt,
                              Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (pkt.isClose) {
                if (mc.screen instanceof DialogueScreen ds) {
                    ds.startCloseAnimation();
                }
                return;
            }

            if (mc.screen instanceof DialogueScreen ds) {
                // 更新现有对话界面
                ds.updateNode(pkt.speaker, pkt.text, pkt.choices,
                        pkt.isTerminal, pkt.hasAutoNext, pkt.delayMs,
                        pkt.choiceLastSelectTimes, pkt.choicePurchaseGameTimes,
                        pkt.choicePurchaseDayTimes, pkt.choiceCooldownTypes,
                        pkt.choiceCooldownValues, pkt.choiceResetTimeTicks);
                ds.updateEntityId(pkt.entityId);
            } else {
                // 打开新对话界面
                mc.setScreen(new DialogueScreen(pkt.dialogueId, pkt.speaker,
                        pkt.text, pkt.choices, pkt.isTerminal, pkt.hasAutoNext,
                        pkt.delayMs, pkt.entityId,
                        pkt.choiceLastSelectTimes, pkt.choicePurchaseGameTimes,
                        pkt.choicePurchaseDayTimes, pkt.choiceCooldownTypes,
                        pkt.choiceCooldownValues, pkt.choiceResetTimeTicks));
            }
        });
        ctx.get().setPacketHandled(true);
    }

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
            buf.writeInt(entityId);

            // 序列化冷却原始数据
            if (choiceLastSelectTimes != null && choiceLastSelectTimes.length > 0) {
                buf.writeBoolean(true);
                buf.writeVarInt(choiceLastSelectTimes.length);
                for (int i = 0; i < choiceLastSelectTimes.length; i++) {
                    buf.writeLong(choiceLastSelectTimes[i]);
                    buf.writeLong(choicePurchaseGameTimes != null ? choicePurchaseGameTimes[i] : 0);
                    buf.writeLong(choicePurchaseDayTimes != null ? choicePurchaseDayTimes[i] : 0);
                    buf.writeVarInt(choiceCooldownTypes[i]);
                    buf.writeLong(choiceCooldownValues[i]);
                    buf.writeVarInt(choiceResetTimeTicks[i]);
                }
            } else {
                buf.writeBoolean(false);
            }
        }
    }

    // ── Getter ──
    public String getDialogueId() {
        return dialogueId;
    }

    public int getEntityId() {
        return entityId;
    }
}