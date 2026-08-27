# Quest API 深入参考

## 核心类型关系

```text
QuestBuilder
  -> QuestDefinition
      -> List<PhaseDefinition>
          -> List<ObjectiveEntry>
          -> List<PhaseTransition>
          -> List<IReward>
          -> List<MarkSpec>
      -> List<ICondition>
      -> List<IReward>
      -> QuestVisualConfig
```

Definition 在注册后视为不可变。玩家状态位于 `ArcQuestPlayer` 和 `QuestRuntimeData`，不要把玩家状态写进 Builder lambda 以外的共享字段。

## `QuestBuilder`

入口：

```text
QuestBuilder.create(ResourceLocation id)
QuestBuilder.create(String id)
```

附属始终传完整 namespace。`build()` 只构建并验证，`buildAndRegister()` 直接写 Registry；在 `ArcQuestRegistrationEvent.Quest` 中优先使用 `event.register(builder.build())`，使注册所有权和时机清晰。

### 身份与展示

| 方法族 | 含义 | 注意事项 |
| --- | --- | --- |
| `category(QuestCategory)` | 日志分类 | 分类必须已经可用，不要临时创建未注册类别 |
| `displayName(...)` | 名称 | 支持 literal、`Component`、`QuestText` |
| `description(...)` | 描述 | 动态文本要提供客户端 fallback |
| `icon(ResourceLocation/Item/ItemStack)` | 列表图标 | ItemStack 会保留展示数据，避免塞入超大 NBT |
| `sortOrder(int)` | 同组排序 | 只影响表现，不应作为业务 ID |
| `themeColor(...)` | 主题色 | int 使用项目约定颜色格式 |

### 行为

| 方法 | 语义 |
| --- | --- |
| `repeatable()` | 完成后允许再次接受；奖励实现仍应明确重复语义 |
| `abandonable(boolean)` | 是否允许玩家放弃 |
| `canBeAutoTrack(boolean)` | 是否可被自动追踪策略选择 |
| `mode(QuestMode)` | 普通/集合等任务模式 |
| `completionPolicy(QuestCompletionPolicy)` | 并行阶段如何判定 Quest 完成 |
| `completionRequiredCount(int)` | `COUNT` 类策略需要的阶段数 |
| `completionTargetPhase(String)` | 指定目标 Phase 完成策略 |

### 前置条件

- `unlockCondition(ICondition)`：添加任意条件；多个条件由 Definition 按项目规则组合。
- `requiresQuest(ResourceLocation/String)`：任务完成前置，并参与 Registry 交叉引用验证。
- `requiresFlag(String)`：要求玩家 flag。

优先使用 `requiresQuest` 或 `ICondition.questCompleted`，因为它们能暴露依赖 ID。纯 lambda 无法被 Registry 静态检查。

### 初始阶段

- `phase(PhaseBuilder/PhaseDefinition)`：按稳定 phaseId 添加。
- `startAt`、`initialPhase`、`setInitialPhase`：单初始 Phase 的兼容入口。
- `setInitialPhase(first, additional...)`、`setInitialPhases(Collection)`：并行初始 Phase。

并行任务不要依赖 Phase 的添加顺序隐式决定焦点；显式设置初始集合，并让追踪逻辑保存 `trackedPhaseId`。

### Quest 级副作用

- `reward(IReward)`：Quest 完成时发放。
- `setFlagOnAccept` / `setFlagOnComplete`：正常生命周期中设置 flag。
- `chapterTradeShop` / `chapterGachaShop`：配置章节商店。
- `chapterStartSound` / `chapterFailSound` / `chapterCompleteSound`：服务端触发并同步合适表现。
- `markRelatedObject` / `markOnAccept`：Quest 级 Marker。

### 时间限制

- `questTimeLimitSeconds(long)`：现实秒数型限制；负值/零值行为以 Builder 校验为准。
- `questTimeLimitDayTicks(long)`：游戏日 tick 语义。
- `clearQuestTimeLimit()`：显式取消。

不要在附属 tick handler 自己实现第二套计时器来直接失败 Quest；使用 Definition 的限制或调用公开失败入口。

## `PhaseBuilder`

每个 Phase 的 `phaseId` 只在 Quest 内唯一，但会进入持久化、事件、Marker 和补全项，发布后应稳定。

### 文本与视觉

`displayName`、`description`、`story` 均支持 `String`、`Component`、`QuestText`。视觉入口包括 start/complete splash、history image、label icon、theme color 和 `useQuestSplashPresentation`。

### Objective

`objective(ObjectiveBuilder)` 或 `objective(ObjectiveEntry)` 按添加顺序形成 Objective 列表。运行时旧 API/事件仍可能暴露 index，因此不要无迁移地在已发布 Phase 中插入或重排 Objective。对外系统应使用稳定 objectiveId 和 target key，而不是只保存 index。

### 转移

| 方法 | 用途 |
| --- | --- |
| `thenGoTo(target)` | 无条件激活一个后继 |
| `thenGoTo(first, additional...)` | fan-out 激活多个后继 |
| `thenGoTo(Collection)` | 动态组织多个目标 ID |
| `thenGoToIf(..., ICondition)` | 条件分支 |
| `choice(...)` | 玩家选择并设置 flag/跳转 |

`thenGoTo` 多目标不是“任选其一”，而是激活多个 Phase。Quest 是否在一部分完成后结束由 `QuestCompletionPolicy` 决定。

### 进入和完成

- `enterWhen(condition, autoEnterByCondition)`：控制 Phase 是否可进入，以及是否由条件自动激活。
- `autoAdvanceOnComplete(boolean)`：完成后是否自动执行 transition。
- `setFlagOnEnter` / `setFlagOnComplete`：由正常流程设置。
- `grantGuideOnEnter` / `grantGuideOnComplete`：使用 Guide 服务端解锁链。
- `reward(IReward)`：Phase 完成奖励。

### Marker

- `markRelatedObject`：Phase 活跃期间按 activation 计算的一般 Marker。
- `markOnEnter` / `markOnComplete` / `markOnAdvance`：短时事件 Marker。
- `trackingMarker`：仅当前被玩家聚焦追踪的 Phase 显示；切换焦点时自动删除旧 Marker、创建新 Marker。

`trackingMarker` 只能接受连续 Marker，不能传带事件 trigger 的 `MarkSpec`。

## `ObjectiveBuilder`

### 内置 Objective

| 入口 | target 语义 | 计数 |
| --- | --- | --- |
| `kill(EntityType, count)` | 实体类型 | 是 |
| `collect(Item, count)` | 物品 | 是 |
| `collectTag(ResourceLocation, count)` | item tag ID | 是 |
| `talk(ResourceLocation npcId)` | NPC 稳定 ID | 通常一次 |
| `deliver(Item, count, npcId)` | 物品和交付 NPC | 是 |
| `reachLocation(locationId, x,y,z,radius)` | 逻辑位置 | 通常一次 |
| `interact(targetId)` | 方块/实体业务目标 | 通常一次 |
| `offer(Item/count)`、`offerTag(...)` | 提交并消耗物品 | 是 |
| `custom(customId, count)` | 附属定义的业务 target key | 是 |
| `nullObjective()` | bootstrap/叙事占位 | 否 |

`custom(customId, count)` 仍使用内置 `arc_quest:custom` ObjectiveType。附属负责监听自己的 gameplay event，再调用 `incrementObjective`；注册 `ObjectiveTypeDefinition` 本身不会自动监听事件。

### 修饰器

- `id(String)`：稳定 objectiveId，强烈建议显式设置。
- `display(...)`：显示文本；动态文本同样需要 fallback。
- `count(int)`：覆盖目标数量。
- `countModifier(ToIntFunction<ServerPlayer>)`：按玩家计算需求量；必须快速、确定、无副作用。
- `hidden()`：不在普通 UI 展示。
- `optional()`：不阻止 Phase 完成，但仍可记录进度。
- `extra(key, value)`：类型特定的有界字符串元数据。
- Objective 级 Marker：`markRelatedObject`、`markOnComplete`。

保留拼写错误的 `countModifyer` 仅用于兼容旧附属，新代码使用 `countModifier`。

## 自定义 `ObjectiveTypeDefinition`

`ObjectiveTypeRegistry.register(ResourceLocation, ObjectiveTypeDefinition)` 注册类型元数据，包括：

- 是否 builtin；
- 是否 counting；
- display translation key；
- 是否要求 target ID；
- 默认 target kind。

它不注册事件监听器、网络包或客户端 renderer。最稳妥的附属目标通常是 `ObjectiveBuilder.custom` + 自己的 Forge 事件适配器；只有 UI/schema 需要独立类型身份时才新增 ObjectiveType。

## `ICondition`

签名：

```text
boolean test(@Nullable ServerPlayer player,
             Set<ResourceLocation> completedQuests,
             Set<String> flags,
             Map<String, Integer> variables)
```

内置工厂包括 `always`、`flagSet`、`flagNotSet`、`questCompleted`、`variable`，组合器包括 `and`、`or`、`negate`。

客户端预览通过 `testClient` 传入 `player == null`。自定义条件必须：

- 明确 null 玩家时返回什么；
- 不修改任何状态；
- 不做磁盘 I/O、网络请求或全世界扫描；
- 对同一快照给出确定结果；
- `describe()` 返回可诊断文本。

Quest 条件可用 `asDialogueCondition()` 桥接到 Dialogue；反向并不自动成立。

## `IReward`

接口只有：

```text
void grant(ServerPlayer player)
String describe()
```

内置实现包括 `ItemReward`、`CommandReward`、`FlagReward`、`VariableReward`。自定义奖励必须考虑：

- 服务端主线程执行；
- 背包满时的插入失败；
- 外部经济/权限 API 抛异常；
- 重复调用语义和附属幂等字段；
- `describe()` 不泄露敏感信息且能定位奖励类型。

`QuestRewardPolicyRegistry` 可在发放前返回 `GRANT`、`SKIP`、`REPLACE`。当前 `REPLACE` 没有替代执行器契约，不能依赖它完成替换；新附属只把 `SKIP` 用作明确的正式拦截。

## 注册 Quest 和 Group

`ArcQuestRegistrationEvent.Quest` 提供：

```text
register(QuestDefinition)
registerGroup(QuestGroupDefinition)
assignQuestToGroup(ResourceLocation questId, ResourceLocation groupId)
assignQuestToGroup(String questId, String groupId)
```

推荐顺序：先 Group，再 Quest，再 assignment。Group assignment 引用不存在 ID 会在冻结或运行时暴露错误，不要依赖静默忽略。

## 运行时入口

`QuestProgressHandler` 的高层入口：

| 入口 | 结果 | 用途 |
| --- | --- | --- |
| `acceptQuestWithCode` | 拒绝码 | 新代码接受任务 |
| `incrementObjective` | void | 外部计数事件；内部会校验 active Phase/Objective |
| `handlePlayerChoiceWithCode` | 拒绝码 | 服务端处理选择 |
| `confirmManualPhaseAdvance` | 拒绝码 | UI 手动推进 |
| `abandonQuestWithCode` | 拒绝码 | 放弃 Quest |
| `abandonPhase` | 拒绝码 | 并行 Phase 单独放弃 |
| `forceCompleteResult` | 拒绝码 | 完整走 Quest 完成链 |
| `forceCompletePhaseResult` | 拒绝码 | 完整走 Phase 完成链 |
| `failQuest` | void | 正常失败链 |

`forceComplete*Result` 不是直接写 completed flag；它应结算奖励、flag、事件、Marker、transition 和后续解锁。不要直接改 `QuestRuntimeData` 替代。

`syncToClient` 是低层补救入口。通过 Handler 正常推进时通常已经同步；只有附属明确直接修改了公开低层状态且理解同步协议时才考虑调用。

## 事件顺序的使用原则

附属不应把具体内部发布顺序当作事务 API，但可依赖事件含义：

- `QuestAcceptedEvent` / `QuestStartedEvent`：接受已成功；
- `QuestProgressChangedEvent`：Objective 值已变化；
- `QuestPhaseCompletedEvent`：Phase 完成链通知；
- `QuestPhaseActivatedEvent`：一个新 Phase 被激活，适合并行任务；
- `QuestPhaseChangedEvent`：兼容线性焦点变化，不足以描述所有并行激活；
- `QuestCompletedEvent`：Quest 已完成；
- `QuestFailedEvent` / `QuestAbandonedEvent`：终止结果；
- `TrackedQuestChangedEvent`：追踪目标变化，可能双端发布，要检查 side/authoritative。

事件监听器不要再次写回同一状态而形成递归，也不要在结果事件中重复发放 ArcQ 已经发过的奖励。
