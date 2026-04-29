package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.client.hud.questmarker.QuestMarkerManager;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerState;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S2C：服务端向客户端同步 Marker（版本化快照 + 增量）。
 */
public class S2CSyncMarkersPacket {

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

    public static void encode(S2CSyncMarkersPacket pkt, FriendlyByteBuf buf) {
        buf.writeByte(pkt.mode);
        buf.writeLong(pkt.epoch);
        buf.writeLong(pkt.revision);

        if (pkt.mode == MODE_SNAPSHOT) {
            buf.writeInt(pkt.entries.size());
            for (MarkerEntry e : pkt.entries) {
                writeEntry(buf, e);
            }
            return;
        }

        buf.writeByte(pkt.op);
        if (pkt.op == OP_ADD) {
            buf.writeInt(pkt.entries.size());
            for (MarkerEntry e : pkt.entries) {
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
            int count = buf.readInt();
            List<MarkerEntry> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                list.add(readEntry(buf));
            }
            return snapshot(epoch, revision, list);
        }

        byte op = buf.readByte();
        if (op == OP_ADD) {
            int count = buf.readInt();
            List<MarkerEntry> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                list.add(readEntry(buf));
            }
            return deltaAdd(epoch, revision, list);
        }
        if (op == OP_REMOVE) {
            return deltaRemove(epoch, revision, buf.readUtf());
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
        buf.writeUtf(e.id());
        buf.writeUtf(e.type());
        buf.writeDouble(e.x());
        buf.writeDouble(e.y());
        buf.writeDouble(e.z());
        buf.writeUtf(e.label());
        buf.writeUtf(e.dimension());
        buf.writeUtf(e.questId());
        buf.writeUtf(e.phaseId());
        buf.writeInt(e.objectiveIndex());
        buf.writeInt(e.followEntityId());
        buf.writeUtf(e.followEntityUuid());
        buf.writeUtf(e.followEntityGuid());
        buf.writeUtf(e.attachPoint());
        buf.writeInt(e.color());
        buf.writeUtf(e.state());
        buf.writeBoolean(e.showDistance());
        buf.writeBoolean(e.allowOffscreenArrow());
    }

    private static MarkerEntry readEntry(FriendlyByteBuf buf) {
        return new MarkerEntry(
                buf.readUtf(),
                buf.readUtf(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readInt(),
                buf.readInt(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readInt(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readBoolean()
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
                .build();
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
            boolean allowOffscreenArrow
    ) {
    }
}
