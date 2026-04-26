package org.com.arc_quest.client.questmarker;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.com.arc_quest.questmarker.api.QuestMarkerData;
import org.com.arc_quest.questmarker.api.QuestMarkerState;
import org.com.arc_quest.questmarker.api.QuestMarkerType;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 客户端任务标记注册表（主线程单线程访问，使用 HashMap）。
 * 支持本地持久化：退出/重进游戏后自动恢复 Marker。
 */
public final class QuestMarkerManager {

    public static final QuestMarkerManager INSTANCE = new QuestMarkerManager();

    private static final String FILE_NAME = "arc_quest_markers.dat";

    private final Map<String, QuestMarkerData> markers = new HashMap<>();
    private boolean loadedFromDisk = false;

    private QuestMarkerManager() {}

    public void add(QuestMarkerData data) {
        ensureLoaded();
        markers.put(data.getId(), data);
        saveToDisk();
    }

    public void remove(String id) {
        ensureLoaded();
        markers.remove(id);
        saveToDisk();
    }

    public void clear() {
        ensureLoaded();
        markers.clear();
        saveToDisk();
    }

    public QuestMarkerData get(String id) {
        ensureLoaded();
        return markers.get(id);
    }

    public Collection<QuestMarkerData> all() {
        ensureLoaded();
        return markers.values();
    }

    public boolean has(String id) {
        ensureLoaded();
        return markers.containsKey(id);
    }

    private void ensureLoaded() {
        if (loadedFromDisk) return;
        loadedFromDisk = true;
        loadFromDisk();
    }

    private void loadFromDisk() {
        try {
            Minecraft mc = Minecraft.getInstance();
            File file = new File(mc.gameDirectory, FILE_NAME);
            if (!file.exists()) return;

            CompoundTag root = NbtIo.readCompressed(file);
            if (root == null || !root.contains("markers", Tag.TAG_LIST)) return;

            ListTag list = root.getList("markers", Tag.TAG_COMPOUND);
            markers.clear();

            for (int i = 0; i < list.size(); i++) {
                CompoundTag t = list.getCompound(i);

                String id = t.getString("id");
                double x = t.getDouble("x");
                double y = t.getDouble("y");
                double z = t.getDouble("z");
                String label = t.getString("label");
                int color = t.getInt("color");

                QuestMarkerType type = parseType(t.getString("type"));
                QuestMarkerState state = parseState(t.getString("state"));
                boolean showDistance = t.contains("showDistance", Tag.TAG_BYTE) ? t.getBoolean("showDistance") : true;
                boolean allowOffscreen = t.contains("allowOffscreenArrow", Tag.TAG_BYTE) ? t.getBoolean("allowOffscreenArrow") : true;

                if (!id.isEmpty()) {
                    QuestMarkerData data = new QuestMarkerData.Builder(id, x, y, z, label)
                            .type(type)
                            .state(state)
                            .color(color)
                            .showDistance(showDistance)
                            .allowOffscreenArrow(allowOffscreen)
                            .build();
                    markers.put(id, data);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void saveToDisk() {
        try {
            Minecraft mc = Minecraft.getInstance();
            File file = new File(mc.gameDirectory, FILE_NAME);

            CompoundTag root = new CompoundTag();
            ListTag list = new ListTag();

            for (QuestMarkerData m : markers.values()) {
                CompoundTag t = new CompoundTag();
                t.putString("id", m.getId());
                t.putDouble("x", m.getWorldX());
                t.putDouble("y", m.getWorldY());
                t.putDouble("z", m.getWorldZ());
                t.putString("label", m.getLabel());
                t.putInt("color", m.getColorARGB());
                t.putString("type", m.getType().name());
                t.putString("state", m.getState().name());
                t.putBoolean("showDistance", m.isShowDistance());
                t.putBoolean("allowOffscreenArrow", m.isAllowOffscreenArrow());
                list.add(t);
            }

            root.put("markers", list);
            NbtIo.writeCompressed(root, file);
        } catch (Exception ignored) {
        }
    }

    private static QuestMarkerType parseType(String s) {
        try {
            return QuestMarkerType.valueOf(s);
        } catch (Exception e) {
            return QuestMarkerType.CUSTOM;
        }
    }

    private static QuestMarkerState parseState(String s) {
        try {
            return QuestMarkerState.valueOf(s);
        } catch (Exception e) {
            return QuestMarkerState.ACTIVE;
        }
    }
}
