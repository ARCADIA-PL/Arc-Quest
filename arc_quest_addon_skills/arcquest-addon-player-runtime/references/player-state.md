# ArcQ 玩家状态与持久化

## 获取状态

服务端主线程：

```text
ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
```

- `get(ServerPlayer)`：返回当前已加载状态，可能为 null；不会为了读取自动创建。
- `getOrCreate(ServerPlayer)`：加载或创建玩家状态，是普通业务读取入口。
- `persistSnapshot(player, data)`：持久化当前快照。
- `persistAndUnload(player)`：保存并卸载，通常由生命周期处理。
- `deleteSnapshot`、`unload`、`clone`、`clearRuntimeState`：维护/生命周期操作，不用于日常玩法。

不要缓存 `ArcQuestPlayer` 跨玩家退出、服务器停止或存档切换；每次业务事件按 `ServerPlayer` 获取。

## 可读取状态

`ArcQuestPlayer` 包含：

- 当前追踪 Quest/Phase、revision 和 change reason；
- Guide 解锁、seen 和页码进度；
- Dialogue 进度；
- Trade/Gacha 次数、冷却、保底与历史；
- active/completed/failed Quest；
- flag 与 int variable；
- Marker；
- dirty kind 与 NBT 序列化。

集合 getter 应视为快照/只读视图，不通过返回集合修改内部状态。

## Flag 与 Variable

```text
data.setFlag("example_addon:met_blacksmith");
data.removeFlag("example_addon:met_blacksmith");
data.setVariable("example_addon:reputation", 10);
data.incrementVariable("example_addon:reputation", 1);
```

这些方法会标记 dirty 并发布 `PlayerProfileEvents.FlagChanged` / `VariableChanged`。跨系统立即可见还需要高层业务流程负责持久化和同步；独立附属事件修改后，调用现有协调持久化入口，不要只清 dirty。

键同样使用附属 namespace 风格，避免与 ArcQ 或其他附属冲突。

## Quest 状态

读取可使用 `isQuestActive`、`isQuestCompleted`、`isQuestFailed`、`getActiveQuest`。修改 Quest 生命周期必须通过 `QuestProgressHandler`。

`addActiveQuest`、`removeActiveQuest`、`markCompleted`、`markFailed` 和 `QuestRuntimeData` setter 是低层能力，不会自动补齐奖励、Marker、事件、追踪重算和网络同步。

## Guide/Gacha/Marker 低层状态

同理，`unlockGuide`、Gacha counter、`upsertMarker` 等公开方法主要供 ArcQ Service 使用。附属优先：

- Guide：`GuideUnlockService`；
- Gacha/Trade：正常抽取/购买流程；
- Marker：Definition + runtime reconciliation；
- Quest：`QuestProgressHandler`。

只有迁移、修复命令等维护场景才直接操作低层数据，并显式补齐持久化、同步和事件语义。

## NBT

`serializeNBT` / `deserializeNBT` 是完整 ArcQ 状态格式，不是附属私有存储容器。不要向未知键随意写入或替换整个 root。附属自己的大型/复杂状态应使用自己的 SavedData/Capability，并只用 ArcQ flag/variable 保存小型联动状态。
