package org.arcadia.arc_quest.questmarker.internal.codec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerState;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerType;
import org.arcadia.arc_quest.questmarker.internal.MarkerModelAdapter;
import org.arcadia.arc_quest.questmarker.internal.model.MarkerPersistence;
import org.arcadia.arc_quest.questmarker.internal.model.MarkerSnapshot;
import org.arcadia.arc_quest.questmarker.internal.store.PlayerMarkerStore;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public final class MarkerNbtCodec {

    private MarkerNbtCodec() {
    }

    public static void writeToRoot(CompoundTag root, PlayerMarkerStore store) {
        ListTag markers = new ListTag();
        for (MarkerSnapshot snapshot : store.snapshots().values()) {
            if (snapshot.persistence() != MarkerPersistence.DURABLE || markers.size() >= MarkerLimits.MAX_MARKERS) continue;
            markers.add(writeMarker(MarkerModelAdapter.toPublic(snapshot)));
        }
        root.put("Markers", markers);

        ListTag consumed = new ListTag();
        store.consumedOneShotMarkers().stream()
                .filter(MarkerLimits::validId)
                .limit(MarkerLimits.MAX_MARKERS)
                .map(StringTag::valueOf)
                .forEach(consumed::add);
        root.put("ConsumedOneShotMarkers", consumed);
    }

    public static void readFromRoot(CompoundTag root,
                                    PlayerMarkerStore store,
                                    Function<String, QuestMarkerData.EntityAttachPoint> attachPointParser) {
        ListTag markers = root.getList("Markers", Tag.TAG_COMPOUND);
        int markerCount = Math.min(markers.size(), MarkerLimits.MAX_MARKERS);
        for (int index = 0; index < markerCount; index++) {
            QuestMarkerData marker = readMarker(markers.getCompound(index), attachPointParser);
            if (marker == null) continue;
            MarkerSnapshot snapshot = MarkerModelAdapter.fromPublic(marker);
            if (snapshot.persistence() == MarkerPersistence.DURABLE) store.upsert(marker);
        }

        ListTag consumed = root.getList("ConsumedOneShotMarkers", Tag.TAG_STRING);
        int consumedCount = Math.min(consumed.size(), MarkerLimits.MAX_MARKERS);
        for (int index = 0; index < consumedCount; index++) {
            String markerId = consumed.getString(index);
            if (MarkerLimits.validId(markerId)) store.consumeOneShot(markerId);
        }
        store.clearDirty();
    }

    private static CompoundTag writeMarker(QuestMarkerData marker) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", MarkerLimits.limit(marker.getId(), MarkerLimits.MAX_ID_LENGTH));
        tag.putDouble("x", marker.getWorldX());
        tag.putDouble("y", marker.getWorldY());
        tag.putDouble("z", marker.getWorldZ());
        tag.putString("label", MarkerLimits.limit(marker.getLabel(), MarkerLimits.MAX_LABEL_LENGTH));
        tag.putString("dimension", MarkerLimits.limit(marker.getDimension(), MarkerLimits.MAX_ID_LENGTH));
        tag.putString("questId", MarkerLimits.limit(marker.getQuestId(), MarkerLimits.MAX_ID_LENGTH));
        tag.putString("phaseId", MarkerLimits.limit(marker.getPhaseId(), MarkerLimits.MAX_ID_LENGTH));
        tag.putInt("objectiveIndex", marker.getObjectiveIndex());
        tag.putInt("color", marker.getColorARGB());
        tag.putString("type", marker.getType().name());
        tag.putString("state", marker.getState().name());
        tag.putBoolean("showDistance", marker.isShowDistance());
        tag.putBoolean("allowOffscreenArrow", marker.isAllowOffscreenArrow());
        tag.putInt("followEntityId", marker.getFollowEntityId());
        tag.putString("followEntityUuid", MarkerLimits.limit(marker.getFollowEntityUuid(), MarkerLimits.MAX_ID_LENGTH));
        tag.putString("followEntityGuid", MarkerLimits.limit(marker.getFollowEntityGuid(), MarkerLimits.MAX_ID_LENGTH));
        tag.putString("attachPoint", marker.getAttachPoint().name());
        tag.putInt("priority", marker.getPriority());
        tag.putBoolean("persistent", true);
        CompoundTag styles = new CompoundTag();
        marker.getStyleHints().entrySet().stream().limit(MarkerLimits.MAX_STYLE_HINTS).forEach(entry ->
                styles.putString(MarkerLimits.limit(entry.getKey(), MarkerLimits.MAX_STYLE_STRING_LENGTH),
                        MarkerLimits.limit(entry.getValue(), MarkerLimits.MAX_STYLE_STRING_LENGTH)));
        tag.put("styleHints", styles);
        return tag;
    }

    private static QuestMarkerData readMarker(CompoundTag tag,
                                              Function<String, QuestMarkerData.EntityAttachPoint> attachPointParser) {
        String id = tag.getString("id");
        if (!MarkerLimits.validId(id) || MarkerModelAdapter.isDerivedId(id)) return null;
        double x = tag.getDouble("x");
        double y = tag.getDouble("y");
        double z = tag.getDouble("z");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return null;

        QuestMarkerType type = parseEnum(QuestMarkerType.class, tag.getString("type"), QuestMarkerType.CUSTOM);
        QuestMarkerState state = parseEnum(QuestMarkerState.class, tag.getString("state"), QuestMarkerState.ACTIVE);
        return new QuestMarkerData.Builder(id, x, y, z,
                MarkerLimits.limit(tag.getString("label"), MarkerLimits.MAX_LABEL_LENGTH))
                .dimension(tag.contains("dimension", Tag.TAG_STRING) ? tag.getString("dimension") : "minecraft:overworld")
                .bindQuest(tag.contains("questId", Tag.TAG_STRING) ? tag.getString("questId") : "")
                .bindPhase(tag.contains("phaseId", Tag.TAG_STRING) ? tag.getString("phaseId") : "")
                .bindObjective(tag.contains("objectiveIndex", Tag.TAG_INT) ? tag.getInt("objectiveIndex") : -1)
                .followEntity(tag.contains("followEntityId", Tag.TAG_INT) ? tag.getInt("followEntityId") : -1,
                        tag.contains("followEntityUuid", Tag.TAG_STRING) ? tag.getString("followEntityUuid") : "",
                        tag.contains("followEntityGuid", Tag.TAG_STRING) ? tag.getString("followEntityGuid") : "",
                        attachPointParser.apply(tag.contains("attachPoint", Tag.TAG_STRING) ? tag.getString("attachPoint") : "HEAD"))
                .type(type).state(state).color(tag.getInt("color"))
                .showDistance(!tag.contains("showDistance", Tag.TAG_BYTE) || tag.getBoolean("showDistance"))
                .allowOffscreenArrow(!tag.contains("allowOffscreenArrow", Tag.TAG_BYTE) || tag.getBoolean("allowOffscreenArrow"))
                .priority(tag.contains("priority", Tag.TAG_INT) ? tag.getInt("priority") : 0)
                .styleHints(readStyles(tag))
                .persistent(!tag.contains("persistent", Tag.TAG_BYTE) || tag.getBoolean("persistent"))
                .build();
    }

    private static Map<String, String> readStyles(CompoundTag markerTag) {
        if (!markerTag.contains("styleHints", Tag.TAG_COMPOUND)) return Map.of();
        CompoundTag styles = markerTag.getCompound("styleHints");
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : styles.getAllKeys()) {
            if (result.size() >= MarkerLimits.MAX_STYLE_HINTS) break;
            String value = styles.getString(key);
            if (!key.isBlank() && !value.isBlank()) {
                result.put(MarkerLimits.limit(key, MarkerLimits.MAX_STYLE_STRING_LENGTH),
                        MarkerLimits.limit(value, MarkerLimits.MAX_STYLE_STRING_LENGTH));
            }
        }
        return result;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
