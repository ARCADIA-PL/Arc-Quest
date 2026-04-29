package org.arcadia.arc_quest.quest.event;

/**
 * 任务变更监听器。客户端 UI 实现此接口来响应任务事件。
 */
@FunctionalInterface
public interface IQuestChangeListener {

    void onQuestChanged(QuestChangeEvent event);
}