# 事件目录与事件总线

## 总线选择

`ArcQuestRegistrationEvent.*` 实现 `IModBusEvent`，只在 MOD 总线监听。其余 `org.arcadia.arc_quest.api.event` 运行时事件发布到 `MinecraftForge.EVENT_BUS`。

```text
@Mod.EventBusSubscriber(modid = ExampleArcQuestAddon.MOD_ID)
public final class ExampleRuntimeEvents {
    @SubscribeEvent
    public static void onQuestCompleted(QuestCompletedEvent event) {
        // Forge 运行时总线
    }
}
```

不要把运行时事件监听类声明为 `bus = Bus.MOD`，也不要把注册事件放到默认 Forge 总线。

## 注册事件

`ArcQuestRegistrationEvent` 子类及入口：

| 事件 | 注册内容 |
| --- | --- |
| `Quest` | Quest、Quest Group、Quest 到 Group 分配 |
| `Dialogue` | `DialogueTree` |
| `Npc` | `NpcSpec`、`IEntityDialogueExtension<?>` |
| `Trade` | `TradeShopDefinition` |
| `Gacha` | `GachaShopDefinition`；注意 `GachaShopBuilder` 当前自行注册 |
| `Guide` | Guide、Guide Group、Guide 到 Group 分配 |

## Quest 事件

| 事件 | 关键数据与用途 |
| --- | --- |
| `QuestAcceptedEvent` | 玩家、Quest ID；接受已成功 |
| `QuestStartedEvent` | 玩家、Quest ID；任务运行开始 |
| `QuestProgressChangedEvent` | Quest/Phase、objective index、旧/新/需要数量、是否刚完成 |
| `QuestPhaseCompletedEvent` | Quest ID、Phase ID；阶段完成结算链中的通知 |
| `QuestPhaseChangedEvent` | 旧/新 Phase；兼容线性阶段观察 |
| `QuestPhaseActivatedEvent` | from/to Phase、是否自动激活；适合并行阶段 |
| `QuestChoiceResolvedEvent` | Phase、Choice index/ID、目标 Phase |
| `QuestCompletedEvent` | Quest 正常完成后 |
| `QuestFailedEvent` | Quest 失败后 |
| `QuestAbandonedEvent` | Quest 放弃后 |
| `QuestTrackerRebuiltEvent` | 玩家、active Quest 数；目标追踪索引重建 |
| `TrackedQuestChangedEvent` | 新旧追踪 Quest、reason、revision、authoritative、side |
| `QuestMarkerChangedEvent` | upsert/remove Marker 差量 |
| `QuestMarkersRefreshedEvent` | Quest、active Phase 数、Marker 数 |

并行任务优先监听 `QuestPhaseActivatedEvent` 与明确 Phase ID，不要只依赖单值 PhaseChanged 假设。

## Dialogue 与 NPC 事件

| 事件 | 关键语义 |
| --- | --- |
| `DialogueStartingEvent` | 开始前，可通过自身 `cancel(reason)` 取消；不能替换 DialogueTree |
| `DialogueStartedEvent` | Session 已开始 |
| `DialogueNodeStartedEvent` | node/say ID、展示文本和可选声音 |
| `DialogueChoiceSelectedEvent` | node、choice index/ID/文本 |
| `DialogueNodeAutoAdvancedEvent` | from/to node |
| `DialogueRestoreAttemptEvent` | 尝试恢复到 node |
| `DialogueRestoreFailedEvent` | 恢复 node 与 FailureReason |
| `DialogueEndedEvent` | 会话结束 |
| `NpcDialogueInteractionEvent` | 交互前，含 binding source 和自身取消状态 |

Starting 和 NPC Interaction 使用事件类自己的 boolean 取消字段，而不是假设存在 Forge `@Cancelable`。

## Guide 事件

- `GuideEvents.Unlocked`：玩家、Guide ID/Definition、UnlockSource。
- `GuideEvents.Seen`：首次标记已读。
- `GuideEvents.ProgressChanged`：旧/新页码，`hasReachedFinalPage()`。
- `GuideEvents.MarkedAllSeen`：批量已读 ID 集合与变化数量。
- `GuideCompletedEvent`：兼容事件，在 Guide 首次 seen 时发布，不表示逐页读完。

## Trade 事件

- `TradeOpenedEvent` / `TradeOpenRejectedEvent`。
- `TradePurchaseAttemptEvent`：购买事务前，提供自身 `cancel(reason)`。
- `TradePurchasedSuccessEvent`。
- `TradePurchaseFailedEvent`：条件、冷却、上限、余额等结构化失败原因。
- `TradePurchaseRejectedEvent`：请求级 shop/entry/error key 拒绝。
- `TradeStateSyncedEvent`。
- `TradeClosedEvent`：含 `clientSide`，player 可空。

`ChapterShopOpenEvent` 位于 `api.event` 根包，包含 player、questId、shopId、拒绝码和 `isSuccess()`。

## Gacha 事件

`GachaEvents` 使用嵌套类：

- `PreDrawEvent`：抽取前，提供自身 `setCancelled`；含 pity counter 和玩家状态。
- `PostDrawEvent`：抽中项、是否保底、新 pity counter。
- `PoolRefreshEvent`：新旧权重 Map。
- `OpenedEvent`、`DrawingEvent`。
- `DrawFailedEvent`：`FailReason`，并提供条件/冷却/上限便捷判断。
- `PityEarlyTriggerEvent`：抽中项、当前计数、阈值和进度百分比。
- `DrawLimitResetEvent`：重置原因和之前抽取数。
- `StateSyncedEvent`：reason 与 `SyncResult.SENT/DROPPED`。
- `ClosedEvent`。

只在 `PreDrawEvent` 当前公开的取消字段上阻止抽取；不要假设 Post/Failed 等结果事件可取消。

## 玩家与重载事件

`PlayerProfileEvents.FlagChanged` 和 `VariableChanged` 携带 player UUID、`ArcQuestPlayer` 与新旧值。它们反映 ArcQ 状态变化，监听者应避免写回同一值形成事件重入。

`ArcQuestReloadEvents.Prepared` 可能运行在资源重载准备线程，只能处理 diagnostics 和纯数据，不能访问世界。`Completed` 在提交或回滚完成后提供 `ReloadSummary` 和 `isApplied()`。

本 Skills 不覆盖数据包开发；重载事件仅用于代码附属观察 ArcQ 内容快照生命周期。
