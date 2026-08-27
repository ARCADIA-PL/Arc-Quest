# 对话与 NPC 代码模式

## 动态对话选择

在 `getDialogueId(ServerPlayer)` 或扩展的 `getDialogueTreeId(...)` 中读取 ArcQ flag/Quest 状态，并返回不同的完整 Dialogue ID。只做低成本读取，复杂业务先在独立 Service 计算。

优先级建议：一次性关键剧情 > active Quest 阶段 > 已完成后闲聊 > 默认对话。这样条件不重叠时更容易诊断。

## 对话推进 Quest

- 内置 talk/interact Objective：优先 `notifyTalk` / `notifyInteract`。
- 外部业务动作：`addEvents` 中调用附属 Service，再由 Service 调 `QuestProgressHandler`。
- 完整完成 Quest/Phase：使用 `forceCompleteResult` / `forceCompletePhaseResult`。
- 不使用 `ChoiceBuilder.completeQuest` 结算有奖励或后续解锁的任务。

## 保留原版交互

`shouldCancelInteract` 返回 null 可保留原交互。若只在特定 Quest 阶段打开对话，未命中时返回 null，不要一律返回 `SUCCESS` 吞掉村民交易或其他模组交互。

## 结束回调幂等

对话可能因正常关闭、距离、实体移除、断线或恢复失败结束。`onDialogueEnd` 的奖励或进度推进应检查 ArcQ flag/当前任务状态，避免同一会话多路径清理造成重复副作用。

## 常见错误

- 客户端直接决定对话树或奖励。
- 在 `onTalkingTick` 执行大范围实体搜索。
- 依赖未实现的 `QUEUED`、`GROUP_SHARED` 或 `SPECTATE_SHARED`。
- 以为 `DialogueStartingEvent` 可替换树。
- 注册 extension 后又用 Mixin 注入同一实体交互，导致触发两次。
- `DialogueContext` 放入无法安全转成文本或跨网络表达的任意对象。

## 最低验证

- 原版实体、自有实体各验证一次交互。
- 距离超限、NPC 移除、玩家断线和 Screen 被替换。
- one-time/cooldown、恢复节点和条件变化。
- 专用服务器类加载。
- 与原实体交互（交易、骑乘等）的取消/保留行为。
