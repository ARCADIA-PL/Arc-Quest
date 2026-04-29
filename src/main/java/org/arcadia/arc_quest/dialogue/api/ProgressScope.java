package org.arcadia.arc_quest.dialogue.api;

/**
 * 对话进度作用域 - 控制对话历史记录的范围。
 */
public enum ProgressScope {

    /**
     * 对话树级别共享（默认）。
     * <p>
     * 所有绑定同一对话树的 NPC 共享进度。
     * 例如：村庄守卫A和村庄守卫B都使用 "epic_village_guard"，它们共享对话历史。
     */
    DIALOGUE_TREE,

    /**
     * NPC 实例级别独立。
     * <p>
     * 每个 NPC 实例有独立的进度。
     * 例如：村庄守卫A死亡后重生为村庄守卫B，B不会继承A的对话历史。
     */
    INSTANCE,

    /**
     * 自定义命名空间。
     * <p>
     * 通过 {@link org.arcadia.arc_quest.dialogue.extension.DialogueExtensionHandler#getProgressNamespace()}
     * 方法动态决定命名空间。
     */
    CUSTOM
}
