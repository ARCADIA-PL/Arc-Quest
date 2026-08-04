package org.arcadia.arc_quest.questmarker.internal.codec;

import net.minecraft.network.FriendlyByteBuf;
import org.arcadia.arc_quest.quest.network.S2CSyncMarkersPacket;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerState;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerType;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MarkerNetworkCodec {

    private MarkerNetworkCodec() {
    }

    public static S2CSyncMarkersPacket.MarkerEntry toEntry(QuestMarkerData marker) {
        return new S2CSyncMarkersPacket.MarkerEntry(
                MarkerLimits.limit(marker.getId(), MarkerLimits.MAX_ID_LENGTH), marker.getType().name(),
                marker.getWorldX(), marker.getWorldY(), marker.getWorldZ(),
                MarkerLimits.limit(marker.getLabel(), MarkerLimits.MAX_LABEL_LENGTH),
                MarkerLimits.limit(marker.getDimension(), MarkerLimits.MAX_ID_LENGTH),
                MarkerLimits.limit(marker.getQuestId(), MarkerLimits.MAX_ID_LENGTH),
                MarkerLimits.limit(marker.getPhaseId(), MarkerLimits.MAX_ID_LENGTH), marker.getObjectiveIndex(),
                marker.getFollowEntityId(), MarkerLimits.limit(marker.getFollowEntityUuid(), MarkerLimits.MAX_ID_LENGTH),
                MarkerLimits.limit(marker.getFollowEntityGuid(), MarkerLimits.MAX_ID_LENGTH), marker.getAttachPoint().name(),
                marker.getColorARGB(), marker.getState().name(), marker.isShowDistance(), marker.isAllowOffscreenArrow(),
                marker.getPriority(), limitedStyles(marker.getStyleHints()));
    }

    public static QuestMarkerData toMarkerData(S2CSyncMarkersPacket.MarkerEntry entry) {
        QuestMarkerType type = parseEnum(QuestMarkerType.class, entry.type(), QuestMarkerType.CUSTOM).canonical();
        QuestMarkerState state = parseEnum(QuestMarkerState.class, entry.state(), QuestMarkerState.ACTIVE);
        QuestMarkerData.EntityAttachPoint attachPoint = parseEnum(QuestMarkerData.EntityAttachPoint.class,
                entry.attachPoint(), QuestMarkerData.EntityAttachPoint.HEAD);
        return new QuestMarkerData.Builder(entry.id(), entry.x(), entry.y(), entry.z(), entry.label())
                .dimension(entry.dimension()).bindQuest(entry.questId()).bindPhase(entry.phaseId())
                .bindObjective(entry.objectiveIndex())
                .followEntity(entry.followEntityId(), entry.followEntityUuid(), entry.followEntityGuid(), attachPoint)
                .type(type).state(state).color(entry.color()).showDistance(entry.showDistance())
                .allowOffscreenArrow(entry.allowOffscreenArrow()).priority(entry.priority())
                .styleHints(entry.styleHints()).persistent(false).build();
    }

    public static void writeEntry(FriendlyByteBuf buffer, S2CSyncMarkersPacket.MarkerEntry entry) {
        buffer.writeUtf(entry.id(), MarkerLimits.MAX_ID_LENGTH);
        buffer.writeUtf(entry.type(), MarkerLimits.MAX_ID_LENGTH);
        buffer.writeDouble(entry.x());
        buffer.writeDouble(entry.y());
        buffer.writeDouble(entry.z());
        buffer.writeUtf(entry.label(), MarkerLimits.MAX_LABEL_LENGTH);
        buffer.writeUtf(entry.dimension(), MarkerLimits.MAX_ID_LENGTH);
        buffer.writeUtf(entry.questId(), MarkerLimits.MAX_ID_LENGTH);
        buffer.writeUtf(entry.phaseId(), MarkerLimits.MAX_ID_LENGTH);
        buffer.writeInt(entry.objectiveIndex());
        buffer.writeInt(entry.followEntityId());
        buffer.writeUtf(entry.followEntityUuid(), MarkerLimits.MAX_ID_LENGTH);
        buffer.writeUtf(entry.followEntityGuid(), MarkerLimits.MAX_ID_LENGTH);
        buffer.writeUtf(entry.attachPoint(), MarkerLimits.MAX_ID_LENGTH);
        buffer.writeInt(entry.color());
        buffer.writeUtf(entry.state(), MarkerLimits.MAX_ID_LENGTH);
        buffer.writeBoolean(entry.showDistance());
        buffer.writeBoolean(entry.allowOffscreenArrow());
        buffer.writeInt(entry.priority());
        Map<String, String> styles = limitedStyles(entry.styleHints());
        buffer.writeVarInt(styles.size());
        styles.forEach((key, value) -> {
            buffer.writeUtf(key, MarkerLimits.MAX_STYLE_STRING_LENGTH);
            buffer.writeUtf(value, MarkerLimits.MAX_STYLE_STRING_LENGTH);
        });
    }

    public static S2CSyncMarkersPacket.MarkerEntry readEntry(FriendlyByteBuf buffer) {
        return new S2CSyncMarkersPacket.MarkerEntry(
                buffer.readUtf(MarkerLimits.MAX_ID_LENGTH), buffer.readUtf(MarkerLimits.MAX_ID_LENGTH),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readUtf(MarkerLimits.MAX_LABEL_LENGTH), buffer.readUtf(MarkerLimits.MAX_ID_LENGTH),
                buffer.readUtf(MarkerLimits.MAX_ID_LENGTH), buffer.readUtf(MarkerLimits.MAX_ID_LENGTH),
                buffer.readInt(), buffer.readInt(), buffer.readUtf(MarkerLimits.MAX_ID_LENGTH),
                buffer.readUtf(MarkerLimits.MAX_ID_LENGTH), buffer.readUtf(MarkerLimits.MAX_ID_LENGTH),
                buffer.readInt(), buffer.readUtf(MarkerLimits.MAX_ID_LENGTH), buffer.readBoolean(),
                buffer.readBoolean(), buffer.readInt(), readStyles(buffer));
    }

    public static int readMarkerCount(FriendlyByteBuf buffer) {
        int count = buffer.readInt();
        if (count < 0 || count > MarkerLimits.MAX_MARKERS) {
            throw new IllegalArgumentException("Marker count exceeds limit: " + count);
        }
        return count;
    }

    private static Map<String, String> readStyles(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MarkerLimits.MAX_STYLE_HINTS) {
            throw new IllegalArgumentException("Marker style hint count exceeds limit: " + count);
        }
        Map<String, String> styles = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            styles.put(buffer.readUtf(MarkerLimits.MAX_STYLE_STRING_LENGTH),
                    buffer.readUtf(MarkerLimits.MAX_STYLE_STRING_LENGTH));
        }
        return styles;
    }

    private static Map<String, String> limitedStyles(Map<String, String> styles) {
        Map<String, String> limited = new LinkedHashMap<>();
        if (styles == null) return limited;
        for (Map.Entry<String, String> entry : styles.entrySet()) {
            if (limited.size() >= MarkerLimits.MAX_STYLE_HINTS) break;
            limited.put(MarkerLimits.limit(entry.getKey(), MarkerLimits.MAX_STYLE_STRING_LENGTH),
                    MarkerLimits.limit(entry.getValue(), MarkerLimits.MAX_STYLE_STRING_LENGTH));
        }
        return limited;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
