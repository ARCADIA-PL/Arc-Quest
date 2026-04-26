package org.com.arc_quest.client.questmarker;

import org.com.arc_quest.questmarker.api.QuestMarkerData;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 客户端任务标记缓存（由服务端权威同步）。
 */
public final class QuestMarkerManager {

    public static final QuestMarkerManager INSTANCE = new QuestMarkerManager();

    private final Map<String, QuestMarkerData> markers = new HashMap<>();

    private QuestMarkerManager() {}

    public void add(QuestMarkerData data) {
        markers.put(data.getId(), data);
    }

    public void remove(String id) {
        markers.remove(id);
    }

    public void clear() {
        markers.clear();
    }

    public QuestMarkerData get(String id) {
        return markers.get(id);
    }

    public Collection<QuestMarkerData> all() {
        return markers.values();
    }

    public boolean has(String id) {
        return markers.containsKey(id);
    }
}
