package org.com.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.com.arc_quest.client.questmarker.QuestMarkerManager;
import org.com.arc_quest.questmarker.api.QuestMarkerData;
import org.com.arc_quest.questmarker.api.QuestMarkerState;
import org.com.arc_quest.questmarker.api.QuestMarkerType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S2C：服务端向客户端同步 Marker 列表。
 * <p>
 * 支持三种操作：CLEAR（全清）、ADD（批量添加）、REMOVE（单个移除）。
 */
public class S2CSyncMarkersPacket {

    public static final byte OP_CLEAR  = 0;
    public static final byte OP_ADD    = 1;
    public static final byte OP_REMOVE = 2;

    private final byte op;
    private final List<MarkerEntry> entries;
    private final String removeId;

    /** 清空所有标记 */
    public S2CSyncMarkersPacket() {
        this.op = OP_CLEAR;
        this.entries = List.of();
        this.removeId = "";
    }

    /** 批量添加标记 */
    public S2CSyncMarkersPacket(List<MarkerEntry> entries) {
        this.op = OP_ADD;
        this.entries = entries;
        this.removeId = "";
    }

    /** 移除单个标记 */
    public S2CSyncMarkersPacket(String removeId) {
        this.op = OP_REMOVE;
        this.entries = List.of();
        this.removeId = removeId;
    }

    public static void encode(S2CSyncMarkersPacket pkt, FriendlyByteBuf buf) {
        buf.writeByte(pkt.op);
        if (pkt.op == OP_ADD) {
            buf.writeInt(pkt.entries.size());
            for (MarkerEntry e : pkt.entries) {
                buf.writeUtf(e.id);
                buf.writeUtf(e.type);
                buf.writeDouble(e.x);
                buf.writeDouble(e.y);
                buf.writeDouble(e.z);
                buf.writeUtf(e.label);
                buf.writeInt(e.color);
            }
        } else if (pkt.op == OP_REMOVE) {
            buf.writeUtf(pkt.removeId);
        }
    }

    public static S2CSyncMarkersPacket decode(FriendlyByteBuf buf) {
        byte op = buf.readByte();
        if (op == OP_ADD) {
            int count = buf.readInt();
            List<MarkerEntry> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                list.add(new MarkerEntry(
                        buf.readUtf(), buf.readUtf(),
                        buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readUtf(), buf.readInt()));
            }
            return new S2CSyncMarkersPacket(list);
        } else if (op == OP_REMOVE) {
            return new S2CSyncMarkersPacket(buf.readUtf());
        }
        return new S2CSyncMarkersPacket();
    }

    public static void handle(S2CSyncMarkersPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            switch (pkt.op) {
                case OP_CLEAR -> QuestMarkerManager.INSTANCE.clear();
                case OP_ADD -> {
                    for (MarkerEntry e : pkt.entries) {
                        QuestMarkerType type;
                        try {
                            type = QuestMarkerType.valueOf(e.type);
                        } catch (IllegalArgumentException ex) {
                            type = QuestMarkerType.CUSTOM;
                        }
                        QuestMarkerData data = new QuestMarkerData.Builder(
                                e.id, e.x, e.y, e.z, e.label)
                                .type(type)
                                .color(e.color)
                                .build();
                        QuestMarkerManager.INSTANCE.add(data);
                    }
                }
                case OP_REMOVE -> QuestMarkerManager.INSTANCE.remove(pkt.removeId);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public record MarkerEntry(String id, String type, double x, double y, double z, String label, int color) {}
}
