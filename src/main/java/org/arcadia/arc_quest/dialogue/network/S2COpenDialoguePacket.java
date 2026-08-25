package org.arcadia.arc_quest.dialogue.network;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.arcadia.arc_quest.client.hud.dialogue.DialogueScreen;
import org.arcadia.arc_quest.client.hud.shop.SimpleTradePanel;
import org.arcadia.arc_quest.client.hud.shop.TradeScreen;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 服务端→客户端：打开/更新/关闭对话界面。
 */
public class S2COpenDialoguePacket {
    public static final UUID LEGACY_SESSION_ID = new UUID(0L, 0L);
    /**
     * 空 dialogueId = 关闭对话。
     */
    private final String dialogueId;
    private final String nodeId;
    private final Component speaker;
    private final Component text;
    private final Component[] choices;
    private final boolean isTerminal;
    private final boolean hasAutoNext;
    private final int delayMs;
    private final boolean isClose;
    private final Mode mode;
    /**
     * 关联的 NPC 实体网络 ID，-1 = 无实体。
     */
    private final int entityId;
    /**
     * 每个选项的最后选择时间戳（毫秒），0 = 未选择
     */
    private final long[] choiceLastSelectTimes;
    /**
     * 每个选项选择时的 gameTime
     */
    private final long[] choicePurchaseGameTimes;
    /**
     * 每个选项选择时的 dayTime
     */
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
    private final UUID sessionId;
    private final long revision;
    private final long playerSessionEpoch;

    /**
     * 原有构造器（向后兼容，entityId = -1）。
     */
    public S2COpenDialoguePacket(String dialogueId, String nodeId, Component speaker,
                                 Component text, Component[] choices,
                                 boolean isTerminal, boolean hasAutoNext, int delayMs) {
        this(dialogueId, nodeId, speaker, text, choices, isTerminal, hasAutoNext, delayMs, -1);
    }

    /**
     * 完整构造器（带 entityId）。
     */
    public S2COpenDialoguePacket(String dialogueId, String nodeId, Component speaker,
                                 Component text, Component[] choices,
                                 boolean isTerminal, boolean hasAutoNext, int delayMs,
                                 int entityId) {
        this(dialogueId, nodeId, speaker, text, choices, isTerminal, hasAutoNext, delayMs, entityId,
                null, null, null, null, null, null);
    }

    /**
     * 完整构造器（带冷却信息）。
     */
    public S2COpenDialoguePacket(String dialogueId, String nodeId, Component speaker,
                                 Component text, Component[] choices,
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
    public S2COpenDialoguePacket(String dialogueId, String nodeId, Component speaker,
                                 Component text, Component[] choices,
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
    public S2COpenDialoguePacket(String dialogueId, String nodeId, Component speaker,
                                 Component text, Component[] choices,
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
    public S2COpenDialoguePacket(String dialogueId, String nodeId, Component speaker,
                                 Component text, Component[] choices,
                                 boolean isTerminal, boolean hasAutoNext, int delayMs,
                                 int entityId, long[] choiceLastSelectTimes,
                                 long[] choicePurchaseGameTimes, long[] choicePurchaseDayTimes,
                                 int[] choiceCooldownTypes, long[] choiceCooldownValues,
                                 int[] choiceResetTimeTicks, @Nullable ResourceLocation[] choiceSelectSoundIds,
                                 @Nullable ResourceLocation matchedSaySoundId,
                                 @Nullable String matchedSayId,
                                 @Nullable String[] choiceIds) {
        this(dialogueId, nodeId, speaker, text, choices, isTerminal, hasAutoNext, delayMs,
                entityId, choiceLastSelectTimes, choicePurchaseGameTimes, choicePurchaseDayTimes,
                choiceCooldownTypes, choiceCooldownValues, choiceResetTimeTicks,
                choiceSelectSoundIds, matchedSaySoundId, matchedSayId, choiceIds,
                LEGACY_SESSION_ID, 0L, 0L, Mode.OPEN);
    }

    public S2COpenDialoguePacket(String dialogueId, String nodeId, Component speaker,
                                 Component text, Component[] choices,
                                 boolean isTerminal, boolean hasAutoNext, int delayMs,
                                 int entityId, long[] choiceLastSelectTimes,
                                 long[] choicePurchaseGameTimes, long[] choicePurchaseDayTimes,
                                 int[] choiceCooldownTypes, long[] choiceCooldownValues,
                                 int[] choiceResetTimeTicks, @Nullable ResourceLocation[] choiceSelectSoundIds,
                                 @Nullable ResourceLocation matchedSaySoundId,
                                 @Nullable String matchedSayId,
                                 @Nullable String[] choiceIds,
                                 UUID sessionId, long revision, long playerSessionEpoch) {
        this(dialogueId, nodeId, speaker, text, choices, isTerminal, hasAutoNext, delayMs,
                entityId, choiceLastSelectTimes, choicePurchaseGameTimes, choicePurchaseDayTimes,
                choiceCooldownTypes, choiceCooldownValues, choiceResetTimeTicks,
                choiceSelectSoundIds, matchedSaySoundId, matchedSayId, choiceIds,
                sessionId, revision, playerSessionEpoch, Mode.OPEN);
    }

    private S2COpenDialoguePacket(String dialogueId, String nodeId, Component speaker,
                                  Component text, Component[] choices,
                                  boolean isTerminal, boolean hasAutoNext, int delayMs,
                                  int entityId, long[] choiceLastSelectTimes,
                                  long[] choicePurchaseGameTimes, long[] choicePurchaseDayTimes,
                                  int[] choiceCooldownTypes, long[] choiceCooldownValues,
                                  int[] choiceResetTimeTicks, @Nullable ResourceLocation[] choiceSelectSoundIds,
                                   @Nullable ResourceLocation matchedSaySoundId,
                                   @Nullable String matchedSayId,
                                   @Nullable String[] choiceIds,
                                   UUID sessionId,
                                   long revision,
                                   long playerSessionEpoch,
                                   Mode mode) {
        this.dialogueId = dialogueId;
        this.nodeId = nodeId;
        this.speaker = speaker;
        this.text = text;
        this.choices = choices;
        this.isTerminal = isTerminal;
        this.hasAutoNext = hasAutoNext;
        this.delayMs = delayMs;
        isClose = mode == Mode.CLOSE;
        this.mode = mode;
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
        this.sessionId = sessionId != null ? sessionId : LEGACY_SESSION_ID;
        this.revision = revision;
        this.playerSessionEpoch = playerSessionEpoch;
    }

    private S2COpenDialoguePacket() {
        dialogueId = "";
        nodeId = "";
        speaker = Component.empty();
        text = Component.empty();
        choices = new Component[0];
        isTerminal = true;
        hasAutoNext = false;
        delayMs = 0;
        isClose = true;
        mode = Mode.CLOSE;
        entityId = -1;
        choiceLastSelectTimes = null;
        choicePurchaseGameTimes = null;
        choicePurchaseDayTimes = null;
        choiceCooldownTypes = null;
        choiceCooldownValues = null;
        choiceResetTimeTicks = null;
        choiceSelectSoundIds = null;
        matchedSaySoundId = null;
        matchedSayId = null;
        choiceIds = null;
        sessionId = LEGACY_SESSION_ID;
        revision = 0L;
        playerSessionEpoch = 0L;
    }

    public static S2COpenDialoguePacket close() {
        return new S2COpenDialoguePacket();
    }

    public static S2COpenDialoguePacket close(UUID sessionId, long revision, long playerSessionEpoch) {
        return new S2COpenDialoguePacket("", "", Component.empty(), Component.empty(), new Component[0],
                true, false, 0, -1, null, null, null, null, null, null,
                null, null, null, null, sessionId, revision, playerSessionEpoch, Mode.CLOSE);
    }

    public static S2COpenDialoguePacket updateFrom(S2COpenDialoguePacket source) {
        return new S2COpenDialoguePacket(
                source.dialogueId,
                source.nodeId,
                source.speaker,
                source.text,
                source.choices,
                source.isTerminal,
                source.hasAutoNext,
                source.delayMs,
                source.entityId,
                source.choiceLastSelectTimes,
                source.choicePurchaseGameTimes,
                source.choicePurchaseDayTimes,
                source.choiceCooldownTypes,
                source.choiceCooldownValues,
                source.choiceResetTimeTicks,
                source.choiceSelectSoundIds,
                source.matchedSaySoundId,
                source.matchedSayId,
                source.choiceIds,
                source.sessionId,
                source.revision,
                source.playerSessionEpoch,
                Mode.UPDATE
        );
    }

    public static S2COpenDialoguePacket decode(FriendlyByteBuf buf) {
        Mode mode = buf.readEnum(Mode.class);
        UUID sessionId = buf.readUUID();
        long revision = buf.readLong();
        long playerSessionEpoch = buf.readLong();
        if (mode == Mode.CLOSE) return close(sessionId, revision, playerSessionEpoch);

        String dId = buf.readUtf();
        String nId = buf.readUtf();
        Component spk = buf.readComponent();
        Component txt = buf.readComponent();
        int count = buf.readVarInt();
        Component[] choices = new Component[count];
        for (int i = 0; i < count; i++) {
            choices[i] = buf.readComponent();
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
                choiceSounds, saySoundId, matchedSayId, choiceIds,
                sessionId, revision, playerSessionEpoch, mode);
    }

    // ── 序列化 ──

    public static void handle(S2COpenDialoguePacket pkt,
                              Supplier<NetworkEvent.Context> ctx) {
        ClientHandler.handle(pkt, ctx);
    }

    private static final class ClientHandler {
        private static void handle(S2COpenDialoguePacket pkt,
                                   Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (pkt.mode == Mode.CLOSE || pkt.isClose) {
                boolean legacyClose = LEGACY_SESSION_ID.equals(pkt.sessionId);
                boolean closed = ClientDialogueCache.INSTANCE.closeSession(pkt.sessionId, pkt.playerSessionEpoch);
                if (closed && mc.screen instanceof DialogueScreen ds
                        && (legacyClose || ds.matchesSession(pkt.sessionId, pkt.dialogueId))) {
                    ds.startCloseAnimation();
                }
                return;
            }

            // 更新缓存
            SoundEvent saySound = pkt.matchedSaySoundId != null ?
                    ForgeRegistries.SOUND_EVENTS.getValue(pkt.matchedSaySoundId) : null;

            SoundEvent[] choiceSounds = new SoundEvent[pkt.choiceSelectSoundIds != null ? pkt.choiceSelectSoundIds.length : 0];
            for (int i = 0; i < choiceSounds.length; i++) {
                if (pkt.choiceSelectSoundIds[i] != null) {
                    choiceSounds[i] = ForgeRegistries.SOUND_EVENTS.getValue(pkt.choiceSelectSoundIds[i]);
                }
            }

            boolean accepted = ClientDialogueCache.INSTANCE.updateFromPacket(
                    pkt.sessionId, pkt.revision, pkt.playerSessionEpoch, pkt.mode == Mode.OPEN,
                    pkt.dialogueId, pkt.nodeId, pkt.speaker, pkt.text, pkt.choices,
                    pkt.isTerminal, pkt.hasAutoNext, pkt.delayMs, pkt.entityId,
                    pkt.choiceLastSelectTimes, pkt.choicePurchaseGameTimes, pkt.choicePurchaseDayTimes,
                    pkt.choiceCooldownTypes, pkt.choiceCooldownValues, pkt.choiceResetTimeTicks,
                    saySound, choiceSounds,
                    pkt.matchedSayId, pkt.choiceIds
            );
            if (!accepted) return;

            if (mc.screen instanceof DialogueScreen ds) {
                if (ds.matchesSession(pkt.sessionId, pkt.dialogueId)) {
                    ds.updateNode(pkt.speaker, pkt.text, pkt.choices,
                            pkt.isTerminal, pkt.hasAutoNext, pkt.delayMs,
                            pkt.choiceLastSelectTimes, pkt.choicePurchaseGameTimes,
                            pkt.choicePurchaseDayTimes, pkt.choiceCooldownTypes,
                            pkt.choiceCooldownValues, pkt.choiceResetTimeTicks);
                    ds.updateEntityId(pkt.entityId);
                    ds.updateSessionMetadata(pkt.sessionId, pkt.revision, pkt.playerSessionEpoch);
                } else if (pkt.mode == Mode.UPDATE) {
                    ArcQuestLog.warn(ArcQuestLog.Category.DIALOGUE_NETWORK, "Ignored UPDATE for non-current screen session: {}", pkt.sessionId);
                } else {
                    openScreen(pkt);
                }
            } else if (pkt.mode == Mode.UPDATE) {
                ArcQuestLog.debug(ArcQuestLog.Category.DIALOGUE_NETWORK, "Ignore UPDATE packet when no DialogueScreen is active: {}", pkt.dialogueId);
            } else if (mc.screen != null &&
                    (mc.screen instanceof TradeScreen ||
                            mc.screen instanceof SimpleTradePanel)) {
                // 如果当前是商店界面，忽略此包
            } else {
                openScreen(pkt);
            }
        });
        ctx.get().setPacketHandled(true);
        }

    private static void openScreen(S2COpenDialoguePacket pkt) {
        Minecraft.getInstance().setScreen(new DialogueScreen(pkt.dialogueId, pkt.speaker,
                pkt.text, pkt.choices, pkt.isTerminal, pkt.hasAutoNext,
                pkt.delayMs, pkt.entityId,
                pkt.choiceLastSelectTimes, pkt.choicePurchaseGameTimes,
                pkt.choicePurchaseDayTimes, pkt.choiceCooldownTypes,
                pkt.choiceCooldownValues, pkt.choiceResetTimeTicks,
                pkt.sessionId, pkt.revision, pkt.playerSessionEpoch));
    }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(mode);
        buf.writeUUID(sessionId);
        buf.writeLong(revision);
        buf.writeLong(playerSessionEpoch);
        if (mode != Mode.CLOSE && !isClose) {
            buf.writeUtf(dialogueId);
            buf.writeUtf(nodeId);
            buf.writeComponent(speaker != null ? speaker : Component.empty());
            buf.writeComponent(text != null ? text : Component.empty());
            buf.writeVarInt(choices.length);
            for (Component c : choices) {
                buf.writeComponent(c != null ? c : Component.empty());
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

    // ── 访问器 ──
    public String getDialogueId() {
        return dialogueId;
    }

    public int getEntityId() {
        return entityId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public long getRevision() {
        return revision;
    }

    public long getPlayerSessionEpoch() {
        return playerSessionEpoch;
    }

    public enum Mode {
        OPEN,
        UPDATE,
        CLOSE
    }
}
