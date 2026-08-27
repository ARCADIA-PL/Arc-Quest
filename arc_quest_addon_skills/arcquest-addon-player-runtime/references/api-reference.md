# 玩家运行时 API 深入参考

## 权威状态所有权

`ArcQuestPlayer` 是单个玩家的 ArcQ 服务端状态聚合，包含：

- active/completed/failed Quest；
- Quest tracking 和 tracked Phase；
- flags、variables；
- Guide unlocked/seen/page progress；
- Trade/Gacha 数据；
- Marker 快照；
- Dialogue progress store；
- dirty kind 和持久化快照信息。

使用 `ArcQuestPlayerManager.getOrCreate(ServerPlayer)` 获取。它可能触发仓库加载，所以只在服务端玩家已就绪的生命周期调用。`get(player)` 适合“没有已加载状态就不做任何事”的观察路径。

## `ArcQuestPlayerManager`

附属常用：

- `get(player)`：返回已加载状态或 null；
- `getOrCreate(player)`：加载/创建状态；
- `persistSnapshot(player, data)`：低层显式持久化；

以下方法属于仓库/生命周期管理，普通附属不应调用：`setRepository`、`persistAndUnload`、`deleteSnapshot`、`unload`、`clearRuntimeState`。玩家登出、服务器停止和 clone 由 ArcQ 自己管理。

## Quest 状态读取

`ArcQuestPlayer` 提供：

- `getActiveQuest(questId)`；
- `getAllActiveQuests()`；
- `isQuestActive/isQuestCompleted/isQuestFailed`；
- `getCompletedQuests/getFailedQuests`；
- `getCompletedQuestLocations()`。

返回的集合或 RuntimeData 不应由附属任意修改。读取并行 Phase 时：

```text
QuestRuntimeData#getActivePhaseIds()
QuestRuntimeData#getCompletedPhaseIds()
QuestRuntimeData#isPhaseActive(phaseId)
QuestRuntimeData#isPhaseCompleted(phaseId)
```

`getCurrentPhaseId()` 是线性兼容/当前焦点概念，不能代表全部 active Phase。

## Quest 变更

接受、Objective 推进、Phase/Quest 完成、失败和放弃必须调用 `QuestProgressHandler`。不要直接调用：

```text
ArcQuestPlayer#addActiveQuest/removeActiveQuest/markCompleted/markFailed
QuestRuntimeData#setState/activatePhase/completePhase/setObjectiveProgress
```

这些低层方法不会独立保证奖励、flag、Marker、transition、事件、追踪重算、持久化和客户端同步全部发生。

## Flags

常用读取/写入：

- `hasFlag(String)`；
- `getAllFlags()`；
- `setFlag(String)`；
- `removeFlag(String)`。

写入会标记 dirty 并发布 `PlayerProfileEvents.FlagChanged`。监听该事件时不要无条件再写同一 flag，避免重入。

Flag 是全局字符串键。附属使用 `example_addon:story/intro_seen` 一类 namespace 化格式，避免和其他附属碰撞。不要把翻译文本或玩家名作为 flag。

## Variables

- `getVariable(key)`：缺失通常按 0；
- `getAllVariables()`；
- `setVariable(key, value)`；
- `incrementVariable(key, amount)`。

变量是 int。外部 long/货币/计时数据必须先做范围校验，不能静默溢出。变化发布 `PlayerProfileEvents.VariableChanged`，包含 old/new/delta。

对于高频计数，先在附属自己的有界缓存聚合，再按合理频率写 ArcQ；不要每 tick 无变化写回。

## Guide 状态

读取：

- `isGuideUnlocked`、`isGuideSeen`；
- `getUnlockedGuides`、`getSeenGuides`；
- `getGuideProgress`、`getAllGuideProgress`。

业务解锁使用 `GuideUnlockService`。低层 `unlockGuide`、`markGuideSeen`、`setGuideProgress` 只适合 ArcQ 内部或明确补偿路径。

## Tracking

### Quest tracking

高层兼容 Service：

- `TrackedQuestService#setTrackedQuest(player, questId/null)`；
- `trackIfAbsent`；
- `ensureTrackedQuest`；
- `reconcile(player, reason)`。

更完整的 `QuestTrackingFacade` 返回 `QuestTrackingResult`：

```text
track
untrack
onQuestAccepted
onQuestTerminated
onQuestReset
reconcile
snapshot
```

使用结果对象区分是否 changed、拒绝原因和 revision。不要直接 `setTrackedQuestId` 后手工发包。

### tracked Phase

并行任务的焦点通过 `ArcQuestPlayer#getTrackedPhaseId` / `setTrackedPhaseId` 持久化。切换焦点必须走对应高层追踪流程，以便 tracking Marker 自动替换；直接写 phaseId 可能漏 Marker 刷新和事件。

追踪状态不是业务 active 状态。取消追踪不能终止 Quest 或 Phase。

## Dirty 与持久化

`ArcQuestPlayer.DirtyKind` 区分变化范围。ArcQ 的 tick/lifecycle 会持久化和同步。附属通过公开高层 API 修改时不需要每次显式 `persistSnapshot`。

不要：

- 在每个事件后同步磁盘写入；
- 保存 `ArcQuestPlayer` 静态强引用；
- 玩家离线后继续异步修改旧对象；
- 直接调用 `clearDirty` 假装已保存；
- 修改 `serializeNBT` 结果后期待反向生效。

## 条件求值

`ICondition` 接收玩家、completed Quest、flags 和 variables 快照。服务端手工求值时优先构造 `QuestConditionContext` 并使用 `CoreProcessors`，或调用已有 Definition 的 `canUnlock/canEnter`；不要在多个模块复制条件组合规则。

客户端预览可能 `player == null`。需要实体/世界条件时明确只用于服务端入口，并给客户端退化结果，不要在 null 时崩溃。

## 外部异步系统

数据库、WebSocket 或 HTTP 回调不能直接读写 ArcQ 状态。正确流程：

```text
异步线程只解析纯数据
  -> 记录 player UUID 和业务结果
  -> MinecraftServer#execute
  -> 重新按 UUID 获取在线 ServerPlayer
  -> 再 getOrCreate 和调用 ArcQ Service
```

不要把旧 `ServerPlayer`、Level、Entity 或 ArcQuestPlayer 强引用捕获到长生命周期 Future。

## 状态事件

- `PlayerProfileEvents.FlagChanged`；
- `PlayerProfileEvents.VariableChanged`；
- Quest lifecycle events；
- `TrackedQuestChangedEvent`。

`TrackedQuestChangedEvent` 可能在客户端和服务端各发布一次，检查：

- `isClientSide/isServerSide`；
- `isAuthoritative`；
- `getReason`；
- `getRevision`。

服务端奖励和持久化只响应 authoritative server event。
