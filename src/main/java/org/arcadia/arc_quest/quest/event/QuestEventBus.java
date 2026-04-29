package org.arcadia.arc_quest.quest.event;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 轻量级任务事件总线。
 * <p>
 * 线程安全（使用 CopyOnWriteArrayList），
 * 但主要预期在渲染线程 / 服务端主线程上运行。
 * <p>
 * 支持两种订阅方式：
 * <ol>
 *   <li>全量监听：{@link #subscribe(IQuestChangeListener)}</li>
 *   <li>按事件类型监听：{@link #subscribe(QuestChangeEvent.Type, IQuestChangeListener)}</li>
 * </ol>
 *
 * <pre>
 *   // 在客户端初始化中：
 *   QuestEventBus.subscribe(QuestChangeEvent.Type.QUEST_COMPLETED, event -> {
 *       QuestSplashRenderer.trigger(event.getQuestId(), SplashType.COMPLETED);
 *   });
 * </pre>
 */
public final class QuestEventBus {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 全量监听器
     */
    private static final CopyOnWriteArrayList<IQuestChangeListener> GLOBAL_LISTENERS =
            new CopyOnWriteArrayList<>();

    /**
     * 按类型分桶的监听器
     */
    private static final EnumMap<QuestChangeEvent.Type, List<IQuestChangeListener>> TYPED_LISTENERS =
            new EnumMap<>(QuestChangeEvent.Type.class);

    static {
        for (QuestChangeEvent.Type type : QuestChangeEvent.Type.values()) {
            TYPED_LISTENERS.put(type, new CopyOnWriteArrayList<>());
        }
    }

    private QuestEventBus() {
    }

    // ════════════════════════════════════════
    //  订阅
    // ════════════════════════════════════════

    /**
     * 订阅所有事件类型
     */
    public static void subscribe(IQuestChangeListener listener) {
        GLOBAL_LISTENERS.addIfAbsent(listener);
    }

    /**
     * 仅订阅特定事件类型
     */
    public static void subscribe(QuestChangeEvent.Type type, IQuestChangeListener listener) {
        List<IQuestChangeListener> list = TYPED_LISTENERS.get(type);
        if (list instanceof CopyOnWriteArrayList<IQuestChangeListener> cow) {
            cow.addIfAbsent(listener);
        }
    }

    /**
     * 取消全量订阅
     */
    public static void unsubscribe(IQuestChangeListener listener) {
        GLOBAL_LISTENERS.remove(listener);
    }

    /**
     * 取消特定类型的订阅
     */
    public static void unsubscribe(QuestChangeEvent.Type type, IQuestChangeListener listener) {
        List<IQuestChangeListener> list = TYPED_LISTENERS.get(type);
        list.remove(listener);
    }

    /**
     * 取消某个监听器在所有位置的订阅
     */
    public static void unsubscribeAll(IQuestChangeListener listener) {
        GLOBAL_LISTENERS.remove(listener);
        for (List<IQuestChangeListener> list : TYPED_LISTENERS.values()) {
            list.remove(listener);
        }
    }

    // ════════════════════════════════════════
    //  发射事件
    // ════════════════════════════════════════

    /**
     * 发射事件到所有匹配的监听器。
     */
    public static void fire(QuestChangeEvent event) {
        // 全量监听器
        for (IQuestChangeListener listener : GLOBAL_LISTENERS) {
            try {
                listener.onQuestChanged(event);
            } catch (Exception e) {
                LOGGER.error("[ArcQuest] Exception in global quest listener", e);
            }
        }

        // 按类型分桶的监听器
        List<IQuestChangeListener> typed = TYPED_LISTENERS.get(event.getType());
        if (typed != null) {
            for (IQuestChangeListener listener : typed) {
                try {
                    listener.onQuestChanged(event);
                } catch (Exception e) {
                    LOGGER.error("[ArcQuest] Exception in typed quest listener for {}", event.getType(), e);
                }
            }
        }
    }

    /**
     * 清除所有监听器（用于 reload / 断开连接时清理）
     */
    public static void clearAll() {
        GLOBAL_LISTENERS.clear();
        for (List<IQuestChangeListener> list : TYPED_LISTENERS.values()) {
            list.clear();
        }
    }
}