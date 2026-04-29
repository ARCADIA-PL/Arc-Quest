package org.arcadia.arc_quest.quest.api;

public enum QuestCompletionPolicy {
    /** 全部阶段完成 */
    ALL,
    /** 任意一个阶段完成 */
    ANY,
    /** 完成数量达到 N */
    N_OF_M,
    /** 指定某个阶段完成即任务完成 */
    SPECIFIC_PHASE
}