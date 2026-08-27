# 条件与任务追踪

## `ICondition`

Quest、Phase、Guide、Trade 共用 `ICondition`。优先使用内置工厂，如 quest completed、flag、variable 和组合条件，而不是复制状态读取逻辑。

客户端预览可能通过 `testClient` 在 `player == null` 时执行。自定义条件必须明确：

- null 玩家时返回安全、保守的展示结果；或
- 只在明确服务端路径使用，并在实现中防止空指针。

条件应无副作用，不修改状态、不发包、不加载区块。

组合条件使用 `and`、`or`、`negate`。跨 Quest 引用使用完整 `ResourceLocation`，以便注册和重载校验。

## 正式追踪 Facade

`QuestTrackingFacade` 的公开实现是 `QuestTrackingManager.INSTANCE`：

- `track(player, questId)`；
- `untrack(player)`；
- `onQuestAccepted` / `onQuestTerminated` / `onQuestReset`；
- `reconcile(player, reason)`；
- `snapshot(player)`。

返回 `QuestTrackingResult`，包含 accepted、changed、before、after、reason 和 rejection。附属需要诊断结果时直接使用 facade。

兼容便捷层 `TrackedQuestService` 返回 boolean changed：

- `setTrackedQuest(player, questId/null)`；
- `trackIfAbsent`、`ensureTrackedQuest`；
- `reconcile` 和生命周期回调。

## 追踪状态语义

追踪只允许有效的 active Quest。切换时 manager：

1. 状态机校验；
2. 更新 Quest tracking snapshot/revision；
3. 发布服务端权威 `TrackedQuestChangedEvent`；
4. 重算 tracking Phase Marker；
5. 持久化并同步。

因此不要直接 `setTrackedQuestId` 模拟用户切换。并行 Quest 的聚焦 Phase 使用 `setTrackedPhaseId` 的高层现有界面流程；附属若需要新增切换入口，应提供对应 facade，而不是只改字段后漏掉 Marker 和同步。

`TrackedQuestChangedEvent` 也可能在客户端同步应用时发布。监听者必须检查 `isServerSide()` / `isClientSide()` 和 `isAuthoritative()`，服务端奖励逻辑只响应权威服务端事件。
