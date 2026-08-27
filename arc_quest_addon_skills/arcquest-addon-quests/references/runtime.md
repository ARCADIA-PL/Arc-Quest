# Quest 运行时推进与扩展点

## 权威线程与入口

所有 Quest 状态写入都在服务端主线程执行。Forge 游戏事件通常已经位于主线程；异步回调、WebSocket 或自建线程必须先调度到 `MinecraftServer#execute`。

正式入口是 `QuestProgressHandler`：

| 操作 | 入口 | 结果 |
| --- | --- | --- |
| 接受 | `acceptQuest` / `acceptQuestWithCode` | boolean 或 `QuestRejectCodeDictionary.Code` |
| 推进普通目标 | `incrementObjective(player, questId, phaseId, objectiveIndex, amount)` | 无返回；非法/非 active 状态不会推进 |
| 推进收集条目 | `incrementCollectionEntry(...)` | 更新 Collection 模式条目 |
| 选择分支 | `handlePlayerChoice` / `handlePlayerChoiceWithCode` | 校验 active Phase 和 Choice |
| 手动推进 | `confirmManualPhaseAdvance` | 用于 `autoAdvanceOnComplete(false)` |
| 失败 | `failQuest` | 正常失败流程、事件、同步和清理 |
| 放弃 | `abandonQuest` / `abandonQuestWithCode`、`abandonPhase` | 校验任务是否允许放弃 |
| 强制完成 | `forceCompleteResult` / `forceCompletePhaseResult` | 仍完整走目标完成、Phase 结算、奖励、Flag、事件、后继解锁和同步 |
| 同步 | `syncToClient` | 仅在确有额外低层修改时使用 |

优先使用返回拒绝码的 `...WithCode` / `...Result` 版本。旧 `void forceComplete` 和 `forceCompletePhase` 为二进制兼容保留，无法告诉调用方失败原因。

## 推进自定义 Objective

```text
@SubscribeEvent
public static void onSpecialAction(ExampleSpecialActionEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) return;

    QuestProgressHandler.incrementObjective(
            player,
            "example_addon:first_steps",
            "use_machine",
            0,
            1);
}
```

`objectiveIndex` 是当前 Definition 内的索引。外部持久契约建议先根据稳定 `objectiveId` 查找索引，不要把索引写进存档、配置或跨模组 API。Phase 必须处于 active 状态，`amount <= 0` 不生效。

如果事件可能重复发布，先确定事件自身是否已经代表唯一成功动作。不要通过客户端按键包直接增加进度；客户端请求必须由服务端重新验证真实操作。

## 正常完成语义

不要直接执行下列操作模拟完成：

```text
runtime.setState(QuestState.COMPLETED);
playerData.markCompleted(questId);
```

这种写法会绕过一部分或全部：

- Objective 与 Phase 完成处理；
- Phase/Quest 奖励发放；
- `setFlagOnComplete`；
- Marker 清理与触发；
- `QuestPhaseCompletedEvent`、`QuestCompletedEvent` 等事件；
- 后继 Phase 激活、自动追踪和客户端同步。

完整强制完成：

```text
QuestRejectCodeDictionary.Code result =
        QuestProgressHandler.forceCompletePhaseResult(
                player, "example_addon:first_steps", "collect_apples");
if (result != QuestRejectCodeDictionary.Code.OK) {
    // 使用附属日志记录 questId、phaseId、player UUID 和 result。
}
```

并行 Quest 的 `forceCompletePhaseResult` 只接受当前 active Phase。`forceCompleteResult` 会以有界循环处理当前 active Phase 及其后续激活阶段；若 Definition 形成无法收敛的流程，会返回 `UNKNOWN`，而不是无限循环。

## 接受规则

`QuestAcceptanceRuleRegistry.register(Rule)` 添加全局接受规则：

```text
QuestAcceptanceRuleRegistry.register((player, definition, data) -> {
    if (!definition.getId().getNamespace().equals(ExampleArcQuestAddon.MOD_ID)) return null;
    return hasRequiredCapability(player)
            ? null
            : QuestRejectCodeDictionary.Code.UNLOCK_CONDITION_NOT_MET;
});
```

规则返回 `null` 表示继续；返回非 null 拒绝码立即终止。Registry 当前没有公开注销入口，因此只在一次性公共初始化中注册。规则是全局热路径，先按 namespace/ID 快速过滤。

## 奖励策略

`QuestRewardPolicyRegistry.register(Policy)` 在奖励发放前评估：

- `GRANT`：按默认流程发放。
- `SKIP`：立即阻止该奖励，优先级最高。
- `REPLACE`：当前只是 Registry 中的预留结果，`QuestProgressHandler` 的发奖调用点并未消费该语义，仍会继续发放原奖励。

多个策略中任意 `SKIP` 会立即返回；若没有 `SKIP`，至少一个 `REPLACE` 会使 Registry 最终返回 `REPLACE`，但现阶段不会替换或跳过原奖励。附属不要用 `REPLACE` 实现替代奖励，否则很容易同时发放替代奖励和原奖励；当前可靠的拦截只有 `SKIP`。策略应按 reward 类型、context 和 namespace 快速过滤，并避免重复副作用。

## 追踪索引

ArcQ 用 `ObjectiveTracker`/`ObjectiveTypeIndex` 加速内置 Objective 事件分发。附属普通事件推进不应每 tick 遍历所有任务。优先：

1. 在事件发生时处理；
2. 用 Quest/Phase/Objective 稳定 ID 定位；
3. 调用 `incrementObjective`；
4. 仅在 Definition 或运行时索引确实发生外部变化后调用 `rebuildTrackingIndex`。

不要周期性调用 `rebuildTrackingIndex`。

## 事件顺序与副作用

运行时事件发布到 `MinecraftForge.EVENT_BUS`。典型成功链包含接受、开始、进度变化、Phase 完成/变化/激活、Quest 完成及 Marker 更新。监听者不应假设每个 Quest 都严格线性；并行阶段可能连续发布多个 Phase 事件。

在完成事件里执行额外奖励时需要幂等标记，推荐设置附属 namespace 的 flag，再持久化和同步由 ArcQ 高层流程接管。避免在事件监听器中再次强制完成同一 Quest，造成重入。
