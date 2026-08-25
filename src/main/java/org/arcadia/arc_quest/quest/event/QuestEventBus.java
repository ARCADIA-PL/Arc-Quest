package org.arcadia.arc_quest.quest.event;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import org.arcadia.arc_quest.core.event.ListenerRegistry;

import java.util.EnumMap;

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
    /**
     * 全量监听器
     */
    private static final ListenerRegistry<IQuestChangeListener> GLOBAL_LISTENERS =
            new ListenerRegistry<>();

    /**
     * 按类型分桶的监听器
     */
    private static final EnumMap<QuestChangeEvent.Type, ListenerRegistry<IQuestChangeListener>> TYPED_LISTENERS =
            new EnumMap<>(QuestChangeEvent.Type.class);

    static {
        for (QuestChangeEvent.Type type : QuestChangeEvent.Type.values()) {
            TYPED_LISTENERS.put(type, new ListenerRegistry<>());
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
        GLOBAL_LISTENERS.subscribe(listener);
    }

    /**
     * 仅订阅特定事件类型
     */
    public static void subscribe(QuestChangeEvent.Type type, IQuestChangeListener listener) {
        ListenerRegistry<IQuestChangeListener> listeners = TYPED_LISTENERS.get(type);
        if (listeners != null) listeners.subscribe(listener);
    }

    /**
     * 取消全量订阅
     */
    public static void unsubscribe(IQuestChangeListener listener) {
        GLOBAL_LISTENERS.unsubscribe(listener);
    }

    /**
     * 取消特定类型的订阅
     */
    public static void unsubscribe(QuestChangeEvent.Type type, IQuestChangeListener listener) {
        ListenerRegistry<IQuestChangeListener> listeners = TYPED_LISTENERS.get(type);
        if (listeners != null) listeners.unsubscribe(listener);
    }

    /**
     * 取消某个监听器在所有位置的订阅
     */
    public static void unsubscribeAll(IQuestChangeListener listener) {
        GLOBAL_LISTENERS.unsubscribe(listener);
        for (ListenerRegistry<IQuestChangeListener> listeners : TYPED_LISTENERS.values()) {
            listeners.unsubscribe(listener);
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
        GLOBAL_LISTENERS.dispatch(
                listener -> listener.onQuestChanged(event),
                (listener, exception) -> ArcQuestLog.error(ArcQuestLog.Category.QUEST_PROGRESS, "Exception in global quest listener", exception));

        // 按类型分桶的监听器
        ListenerRegistry<IQuestChangeListener> typed = TYPED_LISTENERS.get(event.getType());
        if (typed != null) {
            typed.dispatch(
                    listener -> listener.onQuestChanged(event),
                    (listener, exception) -> ArcQuestLog.error(ArcQuestLog.Category.QUEST_PROGRESS,
                            "Exception in typed quest listener for {}", event.getType(), exception));
        }
    }

    /**
     * 清除所有监听器（用于 reload / 断开连接时清理）
     */
    public static void clearAll() {
        GLOBAL_LISTENERS.clear();
        for (ListenerRegistry<IQuestChangeListener> listeners : TYPED_LISTENERS.values()) {
            listeners.clear();
        }
    }
}
