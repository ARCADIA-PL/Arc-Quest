package org.com.arc_quest.dialogue.network;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.com.arc_quest.client.gui.dialogue.DialogueScreen;
import org.com.arc_quest.client.gui.shop.SimpleTradePanel;
import org.com.arc_quest.client.gui.shop.TradeScreen;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * 服务端→客户端：打开/更新/关闭对话界面。
 */
public class S2COpenDialoguePacket {

    private static final Logger LOGGER = LogUtils.getLogger();

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
     * 每个选项选择时的音效 ID 数组
     */
    @Nullable
    private final ResourceLocation[] choiceSelectSoundIds;

    /**
     * 当前匹配到的 SayIf 音效 ID
     */
    @Nullable
    private final ResourceLocation matchedSaySoundId;

    /**
     * 当前匹配到的 SayIf ID
     */
    @Nullable
    private final String matchedSayId;

    /**
     * 每个选项的 ID 数组
     */
    @Nullable
    private final String[] choiceIds;

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
        this(dialogueId, nodeId, speaker, text, choices, isTerminal, hasAutoNext, delayMs, entityId,
                choiceLastSelectTimes, choicePurchaseGameTimes, choicePurchaseDayTimes,
                choiceCooldownTypes, choiceCooldownValues, choiceResetTimeTicks, null, null);
    }

    /**
     * 完整构造器（带音效）。
     */
    public S2COpenDialoguePacket(String dialogueId, String nodeId, String speaker,
                                 String text, String[] choices,
                                 boolean isTerminal, boolean hasAutoNext, int delayMs,
                                 int entityId, long[] choiceLastSelectTimes,
                                 long[] choicePurchaseGameTimes, long[] choicePurchaseDayTimes,
                                 int[] choiceCooldownTypes, long[] choiceCooldownValues,
                                 int[] choiceResetTimeTicks, @Nullable ResourceLocation[] choiceSelectSoundIds) {
        this(dialogueId, nodeId, speaker, text, choices, isTerminal, hasAutoNext, delayMs, entityId,
                choiceLastSelectTimes, choicePurchaseGameTimes, choicePurchaseDayTimes,
                choiceCooldownTypes, choiceCooldownValues, choiceResetTimeTicks, 
                choiceSelectSoundIds, null);
    }

    /**
     * 完整构造器（带 SayIf 音效）。
     */
    public S2COpenDialoguePacket(String dialogueId, String nodeId, String speaker,
                                 String text, String[] choices,
                                 boolean isTerminal, boolean hasAutoNext, int delayMs,
                                 int entityId, long[] choiceLastSelectTimes,
                                 long[] choicePurchaseGameTimes, long[] choicePurchaseDayTimes,
                                 int[] choiceCooldownTypes, long[] choiceCooldownValues,
                                 int[] choiceResetTimeTicks, @Nullable ResourceLocation[] choiceSelectSoundIds,
                                 @Nullable ResourceLocation matchedSaySoundId) {
        this(dialogueId, nodeId, speaker, text, choices, isTerminal, hasAutoNext, delayMs, entityId,
                choiceLastSelectTimes, choicePurchaseGameTimes, choicePurchaseDayTimes,
                choiceCooldownTypes, choiceCooldownValues, choiceResetTimeTicks, 
                choiceSelectSoundIds, matchedSaySoundId, null, null);
    }

    /**
     * 完整构造器（带 SayIf ID 和 Choice IDs）。
     */
    public S2COpenDialoguePacket(String dialogueId, String nodeId, String speaker,
                                 String text, String[] choices,
                                 boolean isTerminal, boolean hasAutoNext, int delayMs,
                                 int entityId, long[] choiceLastSelectTimes,
                                 long[] choicePurchaseGameTimes, long[] choicePurchaseDayTimes,
                                 int[] choiceCooldownTypes, long[] choiceCooldownValues,
                                 int[] choiceResetTimeTicks, @Nullable ResourceLocation[] choiceSelectSoundIds,
                                 @Nullable ResourceLocation matchedSaySoundId,
                                 @Nullable String matchedSayId,
                                 @Nullable String[] choiceIds) {
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
        this.choiceSelectSoundIds = choiceSelectSoundIds;
        this.matchedSaySoundId = matchedSaySoundId;
        this.matchedSayId = matchedSayId;
        this.choiceIds = choiceIds;
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
        this.choiceSelectSoundIds = null;
        this.matchedSaySoundId = null;
        this.matchedSayId = null;
        this.choiceIds = null;
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

        // 反序列化选项音效 ID
        ResourceLocation[] choiceSounds = null;
        if (buf.readBoolean()) {
            int soundCount = buf.readVarInt();
            choiceSounds = new ResourceLocation[soundCount];
            for (int i = 0; i < soundCount; i++) {
                choiceSounds[i] = buf.readBoolean() ? buf.readResourceLocation() : null;
            }
        }

        // 反序列化 SayIf 音效 ID
        ResourceLocation saySoundId = buf.readBoolean() ? buf.readResourceLocation() : null;

        // 反序列化 SayIf ID
        String matchedSayId = buf.readBoolean() ? buf.readUtf() : null;

        // 反序列化 Choice IDs
        String[] choiceIds = null;
        if (buf.readBoolean()) {
            int idCount = buf.readVarInt();
            choiceIds = new String[idCount];
            for (int i = 0; i < idCount; i++) {
                choiceIds[i] = buf.readUtf();
            }
        }

        return new S2COpenDialoguePacket(dId, nId, spk, txt, choices, terminal, autoNext, delay, entityId,
                lastSelectTimes, purchaseGTs, purchaseDTs, cooldownTypes, cooldownValues, resetTimeTicks, 
                choiceSounds, saySoundId, matchedSayId, choiceIds);
    }

    public static void handle(S2COpenDialoguePacket pkt,
                              Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (pkt.isClose) {
                ClientDialogueCache.INSTANCE.closeSession();
                if (mc.screen instanceof DialogueScreen ds) {
                    ds.startCloseAnimation();
                }
                return;
            }

            // 更新缓存
            SoundEvent saySound = pkt.matchedSaySoundId != null ? 
                    ForgeRegistries.SOUND_EVENTS.getValue(pkt.matchedSaySoundId) : null;
            
            SoundEvent[] choiceSounds = new SoundEvent[pkt.choiceSelectSoundIds.length];
            for (int i = 0; i < pkt.choiceSelectSoundIds.length; i++) {
                if (pkt.choiceSelectSoundIds[i] != null) {
                    choiceSounds[i] = ForgeRegistries.SOUND_EVENTS.getValue(pkt.choiceSelectSoundIds[i]);
                }
            }

            ClientDialogueCache.INSTANCE.updateFromPacket(
                    pkt.dialogueId, pkt.nodeId, pkt.speaker, pkt.text, pkt.choices,
                    pkt.isTerminal, pkt.hasAutoNext, pkt.delayMs, pkt.entityId,
                    pkt.choiceLastSelectTimes, pkt.choicePurchaseGameTimes, pkt.choicePurchaseDayTimes,
                    pkt.choiceCooldownTypes, pkt.choiceCooldownValues, pkt.choiceResetTimeTicks,
                    saySound, choiceSounds,
                    pkt.matchedSayId, pkt.choiceIds
            );

            if (mc.screen instanceof DialogueScreen ds) {
                ds.updateNode(pkt.speaker, pkt.text, pkt.choices,
                        pkt.isTerminal, pkt.hasAutoNext, pkt.delayMs,
                        pkt.choiceLastSelectTimes, pkt.choicePurchaseGameTimes,
                        pkt.choicePurchaseDayTimes, pkt.choiceCooldownTypes,
                        pkt.choiceCooldownValues, pkt.choiceResetTimeTicks);
                ds.updateEntityId(pkt.entityId);
            } else if (mc.screen != null && 
                       (mc.screen instanceof TradeScreen || 
                        mc.screen instanceof SimpleTradePanel)) {
                // 如果当前是商店界面，忽略此包
            } else {
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

            // 序列化选项音效 ID 数组
            if (choiceSelectSoundIds != null && choiceSelectSoundIds.length > 0) {
                buf.writeBoolean(true);
                buf.writeVarInt(choiceSelectSoundIds.length);
                for (ResourceLocation rl : choiceSelectSoundIds) {
                    if (rl != null) {
                        buf.writeBoolean(true);
                        buf.writeResourceLocation(rl);
                    } else {
                        buf.writeBoolean(false);
                    }
                }
            } else {
                buf.writeBoolean(false);
            }
            // 序列化 SayIf 音效 ID
            if (matchedSaySoundId != null) {
                buf.writeBoolean(true);
                buf.writeResourceLocation(matchedSaySoundId);
            } else {
                buf.writeBoolean(false);
            }
            
            // 序列化 SayIf ID
            if (matchedSayId != null && !matchedSayId.isEmpty()) {
                buf.writeBoolean(true);
                buf.writeUtf(matchedSayId);
            } else {
                buf.writeBoolean(false);
            }
            
            // 序列化 Choice IDs
            if (choiceIds != null && choiceIds.length > 0) {
                buf.writeBoolean(true);
                buf.writeVarInt(choiceIds.length);
                for (String id : choiceIds) {
                    buf.writeUtf(id != null ? id : "");
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