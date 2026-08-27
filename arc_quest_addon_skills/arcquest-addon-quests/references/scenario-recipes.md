# Quest 需求实现配方

## 需求：线性采集并回报

### 场景

玩家接受任务，收集指定物品，再与 NPC 对话回报；每个阶段有自己的奖励，任务结束设置剧情 flag。

### ArcQ 实现路径

```text
QuestBuilder
  -> gather Phase: collect Objective -> thenGoTo(report)
  -> report Phase: custom Objective
  -> Phase/Quest rewards
  -> setFlagOnComplete
Dialogue action
  -> incrementObjective(report, index 0, amount 1)
```

完整定义：[ExampleQuestContent.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ExampleQuestContent.java)。运行时推进：[ExampleQuestRuntimeHooks.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ExampleQuestRuntimeHooks.java)。

### 关键判断

- `collect` 由 ArcQ 内置追踪器处理；
- “与负责人确认”是附属业务事件，使用 `custom` Objective；
- 外部系统保存稳定 `phaseId` 和 `objectiveId`，不要只保存数组索引；
- 对话只通知业务 adapter，不直接修改 `QuestRuntimeData`。

## 需求：一个起点同时展开多个并行分支

### 场景

玩家开始设施巡览后，同时激活市场、工坊和档案馆三个 Phase；切换追踪焦点只改变临时导航，不应终止其他分支。

### ArcQ 实现路径

```text
bootstrap(null Objective, hidden)
  -> thenGoTo(market, workshop, archive)
QuestCompletionPolicy.ALL
每个分支
  -> 独立 custom Objective
  -> 独立 trackingMarker
  -> 稳定 phaseId/objectiveId
```

完整代码：[ScenarioParallelQuestContent.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioParallelQuestContent.java)。

### 生命周期

1. 接受 Quest 后 bootstrap 成为 active；
2. 通过正常完成入口完成 bootstrap；
3. ArcQ 执行 fan-out transition，三个 Phase 同时 active；
4. 玩家选择 tracked Phase 时，ArcQ 只替换该 Phase 的 tracking Marker；
5. 每个外部事件只推进匹配分支；
6. `ALL` 在全部分支完成后结算 Quest。

### 常见错误

- 用 `getCurrentPhaseId()` 代表全部 active Phase；
- 切换追踪时删除其他 active Phase；
- 三个地点共用同一个 Marker ID；
- 用 UI 卡片顺序作为 phaseId；
- 直接 `activatePhase`，漏掉事件、Guide 和 Marker 刷新。

## 需求：任务只要求完成任意 N 个分支

使用 `QuestCompletionPolicy.N_OF_M` 并设置 `completionRequiredCount(N)`。仍然让所有可选分支拥有稳定 ID；完成达到阈值后 ArcQ 决定 Quest 结束。

适合“调查五个地点中的三个”。不适合“必须完成首领阶段”，后者使用 `SPECIFIC_PHASE` + `completionTargetPhase`，或把首领阶段设计为所有分支汇合后的后继。

## 需求：外部玩法事件增加计数

### 设计判断

外部战斗、训练、商店访问或小游戏结果不是 ArcQ 内置追踪事件，应集中转换成 `incrementObjective`。Adapter 先验证玩家、Quest、Phase 和增量，再调用 Handler。

完整实现：[ScenarioQuestProgressAdapter.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioQuestProgressAdapter.java)。

```text
外部事件
  -> 服务端玩家过滤
  -> Quest active 检查
  -> phaseId active 检查
  -> objectiveIndex/amount 边界
  -> QuestProgressHandler.incrementObjective
```

ArcQ 会处理进度 clamp、Objective 完成、Phase 完成、transition、事件与同步。不要在 adapter 中再次手工完成 Phase。

## 需求：叙事节点确认整阶段完成

当对话、过场或外部战斗结果本身代表整个阶段完成，而不是一个可累积计数时，使用：

```text
QuestProgressHandler.forceCompletePhaseResult(player, questId, phaseId)
```

结果型入口会走正常完成链，包括 Phase 奖励、flag、Guide、Marker、事件和后继激活。调用方应记录或处理拒绝码；不要把所有失败压成 boolean。

## 需求：强制完成整个任务但语义等同手动完成

使用 `forceCompleteResult`，不要直接写 completed set。适合管理员修复或外部系统确认整个任务已经达成。调用前仍要验证目标 Quest 与玩家权限。

## 需求：自定义外部货币奖励

实现 `IReward`：

- 构造时校验金额为正且有上限；
- `grant(ServerPlayer)` 只执行服务端事务；
- `describe()` 返回稳定、非敏感摘要；
- 外部经济 API 若非幂等，使用附属持久 transaction ID 防重复；
- 失败时记录 player UUID、reward type、amount 和 quest context；
- 不通过 Mixin 跳过 `grant` 制作“仅展示奖励”。

若背包物品奖励可能插入失败，明确是掉落、邮件、暂存还是整体失败，不要静默丢失。

## 需求：旧玩家外部剧情已完成，但 ArcQ 分支缺失

### 补偿策略

```text
玩家登录且数据加载完成
  -> 检查附属事实来源
  -> 检查 Quest active
  -> 对每个业务分支检查 isPhaseActive/isPhaseCompleted
  -> 仅补齐缺失分支
  -> 调用 forceCompletePhaseResult
  -> 写入附属 migration version
```

补偿必须可重复执行，且不能在已经 completed 的 Phase 上重复奖励。若 ArcQ 本身是唯一事实来源，不要维护第二套镜像进度。

## 需求：任务状态控制剧情实体显示

不要让每个实体 tick 扫描全部 Quest。监听 `QuestPhaseActivatedEvent`、`QuestPhaseCompletedEvent`、`QuestCompletedEvent` 和登录/登出事件，重算玩家级布尔快照，实体热路径只做 O(1) 查询。

完整实现：[ScenarioEventDrivenVisibility.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioEventDrivenVisibility.java)。

## 验证清单

- 正常接受、重复接受、前置拒绝；
- 线性 transition 与 fan-out transition；
- 并行 active set、tracked Phase 和重登恢复；
- Objective 增量超过 required count 时正确 clamp；
- Phase/Quest 奖励和 flag 只发一次；
- `ALL`、`N_OF_M`、`SPECIFIC_PHASE` 行为符合设计；
- 强制完成与手动完成的奖励、事件、Marker、后继一致；
- 外部异步结果切回服务器线程；
- 专用服务器无客户端类加载。
