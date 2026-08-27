---
name: arcquest-addon-player-runtime
description: Read and mutate Arc Quest server-authoritative player state in Forge 1.20.1, including flags, variables, active quest runtime, conditions, tracking, persistence, synchronization, and safe event-driven integrations. Excludes datapack conditions.
---

# Arc Quest 玩家运行时

用于读取玩家任务状态、flag、variable、Guide 状态和追踪状态，以及编写代码条件与运行时联动。

## 必读

- [玩家状态与持久化](references/player-state.md)
- [玩家运行时 API 深入参考](references/api-reference.md)
- [条件与追踪](references/conditions-tracking.md)
- [安全修改模式](references/patterns.md)
- [玩家运行时需求实现配方](references/scenario-recipes.md)

完整源码见 [ScenarioPlayerStateFacade.java](../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioPlayerStateFacade.java)、[ScenarioQuestProgressAdapter.java](../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioQuestProgressAdapter.java) 和 [ScenarioEventDrivenVisibility.java](../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioEventDrivenVisibility.java)。

## 原则

- 只在服务端主线程获取或修改 `ArcQuestPlayer`。
- 读取使用 `ArcQuestPlayerManager.getOrCreate(player)`；可能未初始化且不希望触发加载时才用 `get(player)`。
- Quest 生命周期通过 `QuestProgressHandler`；追踪通过 `TrackedQuestService`/`QuestTrackingFacade`。
- `setFlag/removeFlag/setVariable/addVariable` 会标记状态并发布玩家状态事件，但跨系统即时显示仍需使用对应高层流程完成持久化和同步。
- 直接修改 `QuestRuntimeData` 是低层操作，不会自动补齐奖励、Marker、事件、追踪重算和网络同步。

## 条件约束

- `ICondition` 是 Quest/Phase/Guide/Trade 共用条件接口。
- 客户端预览可能调用 `testClient` 并传 `player == null`；自定义条件必须定义该情形。
- 组合条件优先使用 `and/or/negate`，任务依赖优先使用 `ICondition.questCompleted`，便于 Registry 交叉引用验证。
