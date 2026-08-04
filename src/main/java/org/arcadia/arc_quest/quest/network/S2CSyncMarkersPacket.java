package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.client.hud.questmarker.QuestMarkerManager;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questmarker.internal.codec.MarkerLimits;
import org.arcadia.arc_quest.questmarker.internal.codec.MarkerNetworkCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class S2CSyncMarkersPacket implements CustomPacketPayload {

    public static final int MAX_MARKERS = MarkerLimits.MAX_MARKERS;

    public static final byte MODE_SNAPSHOT = 0;
    public static final byte MODE_DELTA = 1;

    public static final byte OP_CLEAR = 0;
    public static final byte OP_ADD = 1;
    public static final byte OP_REMOVE = 2;

    public static final Type<S2CSyncMarkersPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "sync_markers"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CSyncMarkersPacket> STREAM_CODEC =
            StreamCodec.ofMember(S2CSyncMarkersPacket::encode, S2CSyncMarkersPacket::decode);

    private final byte mode;
    private final long epoch;
    private final long revision;
    private final byte op;
    private final List<MarkerEntry> entries;
    private final String removeId;

    private S2CSyncMarkersPacket(byte mode,
                                 long epoch,
                                 long revision,
                                 byte op,
                                 List<MarkerEntry> entries,
                                 String removeId) {
        this.mode = mode;
        this.epoch = epoch;
        this.revision = revision;
        this.op = op;
        this.entries = entries;
        this.removeId = removeId;
    }

    public static S2CSyncMarkersPacket snapshot(long epoch, long revision, List<MarkerEntry> entries) {
        return new S2CSyncMarkersPacket(MODE_SNAPSHOT, epoch, revision, OP_ADD, entries, "");
    }

    public static S2CSyncMarkersPacket deltaClear(long epoch, long revision) {
        return new S2CSyncMarkersPacket(MODE_DELTA, epoch, revision, OP_CLEAR, List.of(), "");
    }

    public static S2CSyncMarkersPacket deltaAdd(long epoch, long revision, List<MarkerEntry> entries) {
        return new S2CSyncMarkersPacket(MODE_DELTA, epoch, revision, OP_ADD, entries, "");
    }

    public static S2CSyncMarkersPacket deltaRemove(long epoch, long revision, String removeId) {
        return new S2CSyncMarkersPacket(MODE_DELTA, epoch, revision, OP_REMOVE, List.of(), removeId);
    }

    public static void encode(S2CSyncMarkersPacket packet, FriendlyByteBuf buffer) {
        buffer.writeByte(packet.mode);
        buffer.writeLong(packet.epoch);
        buffer.writeLong(packet.revision);

        if (packet.mode == MODE_SNAPSHOT) {
            writeEntries(buffer, packet.entries);
            return;
        }

        buffer.writeByte(packet.op);
        if (packet.op == OP_ADD) {
            writeEntries(buffer, packet.entries);
        } else if (packet.op == OP_REMOVE) {
            buffer.writeUtf(packet.removeId, MarkerLimits.MAX_ID_LENGTH);
        }
    }

    public static S2CSyncMarkersPacket decode(FriendlyByteBuf buffer) {
        byte mode = buffer.readByte();
        long epoch = buffer.readLong();
        long revision = buffer.readLong();

        if (mode == MODE_SNAPSHOT) {
            return snapshot(epoch, revision, readEntries(buffer));
        }

        byte op = buffer.readByte();
        if (op == OP_ADD) return deltaAdd(epoch, revision, readEntries(buffer));
        if (op == OP_REMOVE) {
            return deltaRemove(epoch, revision, buffer.readUtf(MarkerLimits.MAX_ID_LENGTH));
        }
        return deltaClear(epoch, revision);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CSyncMarkersPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (packet.mode == MODE_SNAPSHOT) {
                List<QuestMarkerData> snapshot = packet.entries.stream()
                        .map(MarkerNetworkCodec::toMarkerData)
                        .toList();
                QuestMarkerManager.INSTANCE.applySnapshot(packet.epoch, packet.revision, snapshot);
                return;
            }

            QuestMarkerManager.INSTANCE.applyDelta(packet.epoch, packet.revision, markers -> {
                switch (packet.op) {
                    case OP_CLEAR -> markers.clear();
                    case OP_ADD -> {
                        for (MarkerEntry entry : packet.entries) {
                            QuestMarkerData marker = MarkerNetworkCodec.toMarkerData(entry);
                            markers.put(marker.getId(), marker);
                        }
                    }
                    case OP_REMOVE -> markers.remove(packet.removeId);
                }
            });
        });
    }

    private static void writeEntries(FriendlyByteBuf buffer, List<MarkerEntry> entries) {
        int count = Math.min(entries.size(), MAX_MARKERS);
        buffer.writeInt(count);
        for (MarkerEntry entry : entries.subList(0, count)) {
            MarkerNetworkCodec.writeEntry(buffer, entry);
        }
    }

    private static List<MarkerEntry> readEntries(FriendlyByteBuf buffer) {
        int count = MarkerNetworkCodec.readMarkerCount(buffer);
        List<MarkerEntry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(MarkerNetworkCodec.readEntry(buffer));
        }
        return entries;
    }

    public record MarkerEntry(
            String id,
            String type,
            double x,
            double y,
            double z,
            String label,
            String dimension,
            String questId,
            String phaseId,
            int objectiveIndex,
            int followEntityId,
            String followEntityUuid,
            String followEntityGuid,
            String attachPoint,
            int color,
            String state,
            boolean showDistance,
            boolean allowOffscreenArrow,
            int priority,
            Map<String, String> styleHints
    ) {
        public MarkerEntry {
            styleHints = styleHints == null ? Map.of() : Map.copyOf(styleHints);
        }
    }
}
