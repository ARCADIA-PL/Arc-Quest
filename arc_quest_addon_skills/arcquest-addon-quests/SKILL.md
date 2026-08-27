---
name: arcquest-addon-quests
description: Implement Arc Quest code-defined quests for Forge 1.20.1 using QuestBuilder, PhaseBuilder, ObjectiveBuilder, conditions, rewards, groups, objective types, and authoritative runtime progression. Do not use for quest datapack JSON.
---

# Arc Quest 任务附属

用于代码注册 Quest、Phase、Objective、奖励、分支和任务组，以及从附属事件推进任务。

## 必读

- [定义与 Builder API](references/builders.md)
- [Quest API 深入参考](references/api-reference.md)
- [运行时推进与扩展点](references/runtime.md)
- [代码模式](references/patterns.md)
- [Quest 需求实现配方](references/scenario-recipes.md)

完整源码见 [ExampleQuestContent.java](../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ExampleQuestContent.java)、[ScenarioParallelQuestContent.java](../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioParallelQuestContent.java) 和 [ScenarioQuestProgressAdapter.java](../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioQuestProgressAdapter.java)。

## 实现顺序

1. 在 `ArcQuestRegistrationEvent.Quest` 中注册自定义 ObjectiveType（如需要）、Quest Group 和 Quest Definition。
2. 为每个 Objective 设置稳定 `objectiveId`；并行 Phase 或后续重排尤其不能依赖数组索引作为外部契约。
3. 用 `PhaseBuilder` 描述目标、奖励、转移、flag、Guide 和 Marker，再组装 `QuestBuilder`。
4. 外部游戏事件只在服务端调用 `QuestProgressHandler.incrementObjective(...)` 或更高层通知入口。
5. 接受、完成、失败、放弃和追踪使用公开 Handler/Service，不直接改运行时集合。

## 关键约束

- `ObjectiveBuilder.custom(customId, count)` 使用内置 `arc_quest:custom` 类型，`customId` 是目标键；附属自行监听事件后推进。
- `ObjectiveTypeRegistry.register(...)` 只注册类型元数据，不会自动提供进度监听器、网络包或客户端专用 renderer。
- `ICondition` 可能在客户端以 `player == null` 调用；依赖玩家实体的条件必须安全处理 null，或只用于明确的服务端路径。
- `IReward#grant` 在服务端执行。实现必须处理背包已满、异常和日志，并明确自身重复调用语义。
- 正常完成语义使用 `QuestProgressHandler.forceCompleteResult` / `forceCompletePhaseResult`；不要直接 `setState(COMPLETED)`。
