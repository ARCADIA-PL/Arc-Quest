package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.client.hud.questmarker.QuestMarkerManager;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerState;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * S2C：服务端向客户端同步 Marker（版本化快照 + 增量）。
 */
public class S2CSyncMarkersPacket {
    public static final int MAX_MARKERS = 4096;
    private static final int MAX_ID_LENGTH = 512;
    private static final int MAX_LABEL_LENGTH = 1024;
    private static final int MAX_STYLE_HINTS = 64;
    private static final int MAX_STYLE_STRING_LENGTH = 256;

    public static final byte MODE_SNAPSHOT = 0;
    public static final byte MODE_DELTA = 1;

    public static final byte OP_CLEAR = 0;
    public static final byte OP_ADD = 1;
    public static final byte OP_REMOVE = 2;

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

    public static void encode(S2CSyncMarkersPacket pkt, FriendlyByteBuf buf) {
        buf.writeByte(pkt.mode);
        buf.writeLong(pkt.epoch);
        buf.writeLong(pkt.revision);

        if (pkt.mode == MODE_SNAPSHOT) {
            int count = Math.min(pkt.entries.size(), MAX_MARKERS);
            buf.writeInt(count);
            for (MarkerEntry e : pkt.entries.subList(0, count)) {
                writeEntry(buf, e);
            }
            return;
        }

        buf.writeByte(pkt.op);
        if (pkt.op == OP_ADD) {
            int count = Math.min(pkt.entries.size(), MAX_MARKERS);
            buf.writeInt(count);
            for (MarkerEntry e : pkt.entries.subList(0, count)) {
                writeEntry(buf, e);
            }
        } else if (pkt.op == OP_REMOVE) {
            buf.writeUtf(pkt.removeId);
        }
    }

    public static S2CSyncMarkersPacket decode(FriendlyByteBuf buf) {
        byte mode = buf.readByte();
        long epoch = buf.readLong();
        long revision = buf.readLong();

        if (mode == MODE_SNAPSHOT) {
            int count = readMarkerCount(buf);
            List<MarkerEntry> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                list.add(readEntry(buf));
            }
            return snapshot(epoch, revision, list);
        }

        byte op = buf.readByte();
        if (op == OP_ADD) {
            int count = readMarkerCount(buf);
            List<MarkerEntry> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                list.add(readEntry(buf));
            }
            return deltaAdd(epoch, revision, list);
        }
        if (op == OP_REMOVE) {
            return deltaRemove(epoch, revision, buf.readUtf(MAX_ID_LENGTH));
        }
        return deltaClear(epoch, revision);
    }

    public static void handle(S2CSyncMarkersPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (pkt.mode == MODE_SNAPSHOT) {
                List<QuestMarkerData> snapshot = pkt.entries.stream()
                        .map(S2CSyncMarkersPacket::toMarkerData)
                        .toList();
                QuestMarkerManager.INSTANCE.applySnapshot(pkt.epoch, pkt.revision, snapshot);
                return;
            }

            QuestMarkerManager.INSTANCE.applyDelta(pkt.epoch, pkt.revision, map -> {
                switch (pkt.op) {
                    case OP_CLEAR -> map.clear();
                    case OP_ADD -> {
                        for (MarkerEntry e : pkt.entries) {
                            QuestMarkerData data = toMarkerData(e);
                            map.put(data.getId(), data);
                        }
                    }
                    case OP_REMOVE -> map.remove(pkt.removeId);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }

    private static void writeEntry(FriendlyByteBuf buf, MarkerEntry e) {
        buf.writeUtf(e.id(), MAX_ID_LENGTH);
        buf.writeUtf(e.type(), MAX_ID_LENGTH);
        buf.writeDouble(e.x());
        buf.writeDouble(e.y());
        buf.writeDouble(e.z());
        buf.writeUtf(e.label(), MAX_LABEL_LENGTH);
        buf.writeUtf(e.dimension(), MAX_ID_LENGTH);
        buf.writeUtf(e.questId(), MAX_ID_LENGTH);
        buf.writeUtf(e.phaseId(), MAX_ID_LENGTH);
        buf.writeInt(e.objectiveIndex());
        buf.writeInt(e.followEntityId());
        buf.writeUtf(e.followEntityUuid(), MAX_ID_LENGTH);
        buf.writeUtf(e.followEntityGuid(), MAX_ID_LENGTH);
        buf.writeUtf(e.attachPoint(), MAX_ID_LENGTH);
        buf.writeInt(e.color());
        buf.writeUtf(e.state(), MAX_ID_LENGTH);
        buf.writeBoolean(e.showDistance());
        buf.writeBoolean(e.allowOffscreenArrow());
        buf.writeInt(e.priority());
        int styleCount = Math.min(e.styleHints().size(), MAX_STYLE_HINTS);
        buf.writeVarInt(styleCount);
        int written = 0;
        for (Map.Entry<String, String> style : e.styleHints().entrySet()) {
            if (written++ >= styleCount) break;
            buf.writeUtf(style.getKey(), MAX_STYLE_STRING_LENGTH);
            buf.writeUtf(style.getValue(), MAX_STYLE_STRING_LENGTH);
        }
    }

    private static MarkerEntry readEntry(FriendlyByteBuf buf) {
        return new MarkerEntry(
                buf.readUtf(MAX_ID_LENGTH),
                buf.readUtf(MAX_ID_LENGTH),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readUtf(MAX_LABEL_LENGTH),
                buf.readUtf(MAX_ID_LENGTH),
                buf.readUtf(MAX_ID_LENGTH),
                buf.readUtf(MAX_ID_LENGTH),
                buf.readInt(),
                buf.readInt(),
                buf.readUtf(MAX_ID_LENGTH),
                buf.readUtf(MAX_ID_LENGTH),
                buf.readUtf(MAX_ID_LENGTH),
                buf.readInt(),
                buf.readUtf(MAX_ID_LENGTH),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readInt(),
                readStyleHints(buf)
        );
    }

    private static QuestMarkerData toMarkerData(MarkerEntry e) {
        QuestMarkerType type;
        QuestMarkerState state;
        QuestMarkerData.EntityAttachPoint attachPoint;

        try {
            type = QuestMarkerType.valueOf(e.type()).canonical();
        } catch (IllegalArgumentException ex) {
            type = QuestMarkerType.CUSTOM;
        }

        try {
            state = QuestMarkerState.valueOf(e.state());
        } catch (IllegalArgumentException ex) {
            state = QuestMarkerState.ACTIVE;
        }

        try {
            attachPoint = QuestMarkerData.EntityAttachPoint.valueOf(e.attachPoint());
        } catch (IllegalArgumentException ex) {
            attachPoint = QuestMarkerData.EntityAttachPoint.HEAD;
        }

        return new QuestMarkerData.Builder(
                e.id(), e.x(), e.y(), e.z(), e.label())
                .dimension(e.dimension())
                .bindQuest(e.questId())
                .bindPhase(e.phaseId())
                .bindObjective(e.objectiveIndex())
                .followEntity(e.followEntityId(), e.followEntityUuid(), e.followEntityGuid(), attachPoint)
                .type(type)
                .state(state)
                .color(e.color())
                .showDistance(e.showDistance())
                .allowOffscreenArrow(e.allowOffscreenArrow())
                .priority(e.priority())
                .styleHints(e.styleHints())
                .persistent(false)
                .build();
    }

    private static int readMarkerCount(FriendlyByteBuf buffer) {
        int count = buffer.readInt();
        if (count < 0 || count > MAX_MARKERS) {
            throw new IllegalArgumentException("Marker count exceeds limit: " + count);
        }
        return count;
    }

    private static Map<String, String> readStyleHints(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_STYLE_HINTS) {
            throw new IllegalArgumentException("Marker style hint count exceeds limit: " + count);
        }
        Map<String, String> styles = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            styles.put(buffer.readUtf(MAX_STYLE_STRING_LENGTH), buffer.readUtf(MAX_STYLE_STRING_LENGTH));
        }
        return styles;
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
