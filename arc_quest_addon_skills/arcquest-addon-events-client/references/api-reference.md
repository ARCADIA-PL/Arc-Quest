# 事件与客户端扩展深入参考

## 总线与线程

| 类型 | 总线 | 典型线程 |
| --- | --- | --- |
| `ArcQuestRegistrationEvent.*` | MOD bus | common setup enqueueWork |
| Quest/Dialogue/Guide/Trade/Gacha/Player runtime event | Forge bus | 服务端主线程或明确客户端线程 |
| `ArcQuestReloadEvents.Prepared` | Forge bus | 可能是资源准备线程 |
| `ArcQuestReloadEvents.Completed` | Forge bus | reload 提交/回滚后 |
| 客户端 Registry 注册 | 附属 client bootstrap | 客户端初始化线程 |

事件类继承 Forge `Event` 不代表都可取消。只使用事件自身公开的 `setCancelled/cancel` 语义。

## Quest 事件选择

### 生命周期

- `QuestAcceptedEvent`：接受成功；
- `QuestStartedEvent`：运行时启动；
- `QuestCompletedEvent`：正常完成；
- `QuestFailedEvent`；
- `QuestAbandonedEvent`。

不要在 completed 事件重复发 Definition 自带奖励。附属额外奖励要有自己的幂等 claim ID。

### 进度与并行 Phase

- `QuestProgressChangedEvent`：phaseId、objectiveIndex、old/new/required、刚完成标记；
- `QuestPhaseCompletedEvent`：明确 phaseId；
- `QuestPhaseActivatedEvent`：from/to 和 autoActivated，描述并行激活；
- `QuestPhaseChangedEvent`：线性兼容 old/new，不足以覆盖全部并行状态；
- `QuestChoiceResolvedEvent`：choice index/ID 和 target phase。

并行任务监听 Activated/Completed 和明确 phaseId，不要只监听 Changed。

### Tracking 与 Marker

- `TrackedQuestChangedEvent`：level/player、old/new Quest、reason、revision、authoritative、side；
- `QuestTrackerRebuiltEvent`：active Quest 数量；
- `QuestMarkerChangedEvent`：upsert/remove 差量；
- `QuestMarkersRefreshedEvent`：active phase/marker 数量。

只在 server authoritative tracking event 做持久业务。客户端事件适合动画和提示。

## Dialogue/NPC 事件

- `DialogueStartingEvent`：开始前，可按事件 API 取消；
- `DialogueStartedEvent`；
- `DialogueNodeStartedEvent`；
- `DialogueChoiceSelectedEvent`；
- `DialogueNodeAutoAdvancedEvent`；
- `DialogueRestoreAttemptEvent` / `DialogueRestoreFailedEvent`；
- `DialogueEndedEvent`；
- `NpcDialogueInteractionEvent`：绑定解析后的交互拦截点。

事件回调不要长期保存 Session 或 Entity。需要稍后处理时保存 player UUID、treeId、nodeId 等纯标识，并在主线程重新查询。

## Guide 事件

- `GuideEvents.Unlocked`：服务端首次解锁；
- `GuideEvents.Seen`：首次 seen；
- `GuideEvents.ProgressChanged`：页面变化和末页判断；
- `GuideEvents.MarkedAllSeen`：批量已读；
- `GuideCompletedEvent`：Seen 的兼容事件。

奖励必须区分“解锁”“seen”“到达末页”。

## Trade/Gacha 事件

Trade 事务前使用 `TradePurchaseAttemptEvent`；Gacha 抽取前使用 `GachaEvents.PreDrawEvent`。它们暴露自己的取消状态。成功、失败和 Post 事件是观察点。

结果事件中不要反向执行同一交易或抽取。遥测、成就或附属奖励应以服务端事件为准，并使用 transaction/entry/item ID 去重。

## Player 事件

`PlayerProfileEvents.FlagChanged` 和 `VariableChanged` 携带 UUID、`ArcQuestPlayer`、旧值和新值。监听器不要无条件写回同一字段。

变化量事件适合触发条件型 Guide/Quest 检查，但应按受影响 ID 建索引，不要每次扫描全部 Definition。

## Reload 事件

`ArcQuestReloadEvents.Prepared` 可能不在世界主线程，只能观察纯 diagnostics/snapshot，不访问 Level、Entity 或玩家。

`Completed` 提供 reload summary 和 `isApplied()`。附属若缓存 ArcQ Definition 查询结果，应只在 applied 成功后原子替换缓存，失败时保留旧快照。

## `DialogueOverlayRegistry`

正式用途：在 ArcQ Dialogue Screen 渲染自定义叠层。注册内容每帧调用，必须：

- 只引用客户端类；
- 根据 treeId/node/session snapshot 快速过滤；
- 正确 push/pop pose 和恢复 render state；
- 不遮挡 choices、history、字号按钮；
- Screen 关闭立即停止；
- GUI scale 2/3/4 和小窗口布局安全。

不要 Mixin `DialogueScreen#render` 只为画像、徽标或额外文字。

## `JournalTabVisibilityRegistry`

用于按客户端上下文隐藏/显示 ArcQ Journal tab。注册一次，predicate 高频执行。

可见性只影响 UI，不应作为服务端权限。服务端仍要验证 Quest 数据请求和操作。

## `QuestMarkerPresentationRegistry`

- Label resolver：第一个非 null Component 生效；
- Color resolver：第一个非 null Integer 生效；
- Visibility filter：任一 false 即隐藏。

附属 resolver 应只匹配自己的 marker ID 前缀或 style hint，未命中返回 null/true，让其他扩展继续处理。

## `MarkerCameraBasisRegistry`

Provider 根据客户端 `Camera` 和 fallback basis 返回新 basis。用于骑乘、特殊相机或自定义视角下修正 Marker 投影。

要求：

- 向量有限、非零、近似正交；
- 无法处理时返回 null；
- 不修改 Camera；
- 不依赖服务端实体对象；
- 每帧不分配大型对象。

## 注册生命周期

这些客户端 Registry 当前通常没有通用 unregister。只在 client bootstrap 注册一次，不在每次 Screen init、资源重载或世界加入时重复注册。

Common 主类不要静态引用注册类。使用 `Dist.CLIENT` subscriber 或 `DistExecutor` 隔离。

## 事件监听器设计

优先保持监听器短小：

```text
过滤 side/player/id
  -> 提取不可变参数
  -> 委托附属 service
  -> 记录结构化结果
```

避免：

- 在高频进度/Marker 事件同步 I/O；
- 在事件中遍历所有在线玩家和全部 Quest；
- 事件 A 写状态触发 A 自身无限重入；
- 客户端和服务端各发一次奖励；
- 保存 event 对象供异步使用。
