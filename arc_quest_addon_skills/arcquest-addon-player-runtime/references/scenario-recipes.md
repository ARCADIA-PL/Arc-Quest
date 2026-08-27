# 玩家运行时需求实现配方

## 需求：业务代码统一查询 Quest 状态

### 设计判断

大量 NPC、实体和外部玩法会重复 active/completed/phase 判断。建立无缓存的薄 Facade，内部通过 `ArcQuestPlayerManager` 查询，避免各模块复制 ID、null 处理和并行 Phase 误区。

完整代码：[ScenarioPlayerStateFacade.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioPlayerStateFacade.java)。

Facade 应：

- 接受 `ServerPlayer` 和稳定 ID；
- 不保存 Player/ArcQuestPlayer 强引用；
- active Phase 返回集合快照；
- 修改操作委托 Handler/Service；
- 不暴露 mutable 内部集合。

## 需求：确保任务已接受但不重复报错

```text
isQuestActive -> OK
isQuestCompleted -> completed rejection
otherwise -> acceptQuestWithCode
```

完整实现：[ScenarioQuestProgressAdapter.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioQuestProgressAdapter.java)。调用方保留拒绝码，区分前置不满足、Definition 缺失和不可重复完成。

## 需求：读取并行 Phase

使用 `QuestRuntimeData#getActivePhaseIds()` 或 `isPhaseActive(phaseId)`。`getCurrentPhaseId()` 只适合线性兼容/焦点显示，不能代表全部 active 分支。

返回给外部模块时使用 `Set.copyOf`，避免调用方修改 ArcQ 内部状态。

## 需求：追踪某个进行中任务

使用 `TrackedQuestService#setTrackedQuest`、`trackIfAbsent` 或更完整的 `QuestTrackingFacade`。高层流程会发布 tracking event、更新 revision、同步并重建 tracking Marker。

不要只调用 `ArcQuestPlayer#setTrackedQuestId` 后手工发包。追踪是表现焦点，不是 Quest active 状态；取消追踪不能放弃任务。

## 需求：flag 与 variable 驱动外部业务

- flag 用 namespace 化字符串表示离散事实；
- variable 用 int 表示有界计数；
- 外部 long 先做范围校验；
- 高频计数先在附属缓存聚合，再按事件/间隔写回；
- 监听 `PlayerProfileEvents` 时避免写回同一字段造成重入。

Quest/Phase Definition 自带的 `setFlagOnAccept/Enter/Complete` 优先于额外 listener，因为强制完成和补偿也会走同一链。

## 需求：外部异步结果推进任务

### 正确流程

```text
异步线程
  -> 只保留 player UUID + 纯业务结果
  -> MinecraftServer#execute
  -> 重新查在线 ServerPlayer
  -> QuestProgressAdapter
```

完整实现：[ScenarioQuestProgressAdapter.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioQuestProgressAdapter.java) 中的 `completePhaseFromAsync`。

不要捕获长期 `ServerPlayer`、Level、Entity 或 ArcQuestPlayer，也不要从异步线程读取世界或持久状态。

## 需求：任务状态控制大量剧情实体可见性

### 设计

监听状态变化事件重算玩家级快照：

```text
login/phase activated/phase completed/quest completed
  -> 查询一次目标 Quest
  -> 更新 UUID -> visibility
entity tick/render query
  -> O(1) 读取
logout
  -> 删除 UUID
```

完整代码：[ScenarioEventDrivenVisibility.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioEventDrivenVisibility.java)。

若一个玩家控制多个 actor，可把 value 改为不可变 bitset/actor ID 集合，并按 Quest/Phase 建反向索引。不要每 tick 遍历全部 Quest、Phase 和实体。

## 需求：外部持久状态补偿 ArcQ

明确事实来源：外部战斗胜利由外部系统为准，Quest 完成由 ArcQ 为准。登录补偿只将已经确认的外部事实转换为正常 `forceCompletePhaseResult`；每个分支先检查 active/completed，并保存 migration version。

不要双向覆盖：ArcQ completed 不应自动重写所有外部剧情字段，外部字段也不应每次登录重放全部 Quest 历史。

## 需求：只观察已加载玩家，不触发仓库加载

事件或高频观察路径使用 `ArcQuestPlayerManager.get(player)`；返回 null 就跳过。明确需要初始化的登录后业务使用 `getOrCreate(player)`。

不要调用 `unload`、`clearRuntimeState`、`deleteSnapshot` 等仓库生命周期方法；玩家登出、服务器停止和 clone 由 ArcQ 管理。

## 验证清单

- active/completed/failed 状态区分正确；
- 并行 Phase 返回完整 active set；
- Facade 不泄露 mutable 集合；
- 追踪变更发布 event、revision 和 Marker 刷新；
- flag/variable 更新无事件递归；
- 异步回调在玩家离线时安全丢弃；
- 可见性缓存登录初始化、事件更新、登出清理；
- 重登后 ArcQ 与外部事实来源无重复奖励；
- 热路径无磁盘 I/O 和全 Registry 扫描。
