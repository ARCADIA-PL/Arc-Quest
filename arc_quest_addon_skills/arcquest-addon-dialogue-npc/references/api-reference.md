# Dialogue 与 NPC API 深入参考

## 模型边界

```text
DialogueTreeBuilder -> DialogueTree -> DialogueNode -> DialogueChoice
                                            |              |
                                      DialogueText    DialogueAction
                                            |
                                   DialogueCondition
```

`DialogueTree` 是注册期定义；`DialogueSession` 是某位服务端玩家的一次会话。不要把 Session、Player 或 Entity 保存进静态 Definition。

## `DialogueTreeBuilder`

### 创建和树级配置

- `create(ResourceLocation/String)`：附属传完整 namespace。
- `npc(DialogueText)`：默认说话者。
- `startNode(String)`：显式指定起点；省略时首个 `node` 成为起点。
- `visualConfig(QuestVisualConfig)`：复用 ArcQ 视觉配置。
- `oneTime()`：整棵树不可重复。
- `cooldown(seconds)`、`cooldownGameDay()`、`cooldownGameTick(resetTick)`：树级重复规则。
- `repeatable(boolean)`：显式控制。
- `markRelatedObject`：树级连续 Marker。

`build()` 会提交当前节点并验证至少存在一个节点、起点存在。`buildAndRegister()` 直接写 Dialogue Registry；在 `ArcQuestRegistrationEvent.Dialogue` 中优先 `event.register(builder.build())`。

### 节点构建状态

`node(nodeId)` 会提交上一个节点并重置当前节点临时状态。每个 nodeId 会进入恢复快照和事件，必须稳定且在树内唯一。

节点常用配置：

| 方法 | 语义 |
| --- | --- |
| `speaker(DialogueText/String)` | 覆盖当前节点说话者 |
| `say(DialogueText/String, sayId)` | 默认文本；sayId 必须稳定且完整 namespace |
| `sayIf(...)` | 条件文本；可配置 priority、sound、Marker |
| `enterSound(...)` | 节点进入音效 |
| `delay(ms)` | 自动推进前延迟 |
| `choice(...)` / `choiceIf(...)` | 添加选项 |
| `autoNext(nodeId)` | 无选项自动跳转 |
| `nodeOneTime()` | 当前节点一次性 |
| `nodeCooldown*` | 当前节点独立冷却 |
| `markOnNodeEnter` | 节点进入短时 Marker |

`sayId` 不是纯调试文字，它用于文本选择、记录和兼容逻辑。不要让多个文本共享同一 ID，也不要使用数组序号拼接不稳定 ID。

## `ChoiceBuilder`

`choice(choiceId, text, configurator)` 和 `choiceIf` 提供链式配置。choiceId 在节点内必须稳定。

### 跳转和关闭

- `goTo(nodeId)`：跳转；目标必须存在。
- `close()`：结束会话。
- `restoreToCurrentNode` 等恢复配置由 open shop/guide 入口内部使用，关闭次级 Screen 后恢复同一会话节点。

### 内置服务端动作

- Quest：`startQuest`、`completeQuest`、`advancePhase`；
- 玩家：`giveXp`、`giveItem`、`setFlag`、`setVariable`；
- Objective 通知：`notifyTalk`、`notifyInteract`；
- 外部执行：`runCommand`；
- Guide：`unlockGuide`、`openGuide`；
- Shop：`openTrade`、`openSimpleTrade`、`openGacha`；
- 任意动作：`action(DialogueAction)`。

注意：`ChoiceBuilder.completeQuest` 是兼容动作，若要求与手动完成严格相同的奖励、flag、事件和解锁语义，使用 `DialogueAction.LambdaAction` 调用 `QuestProgressHandler.forceCompleteResult`，并处理拒绝码。

## `DialogueText`

入口：

- `literal(String)`；
- `translatable(String, DialogueArg...)`；
- `of(Function<DialogueTextContext, Component>)`；
- `of(BiFunction<ServerPlayer, Entity, Component>)`；
- `of(TriFunction<ServerPlayer, Entity, IDialogueNpc, Component>)`。

动态 resolver 可能在不同上下文执行。不要访问客户端单例，不要修改玩家状态，不要执行昂贵查询。需要显示按键时使用翻译/按键 Component，在客户端没有服务端实体时提供可读退化。

## `DialogueCondition`

Dialogue 条件和 Quest `ICondition` 是不同接口。常见条件包括 flag、variable、Quest、节点进度、组合 `Not` 等；`DialogueCondition.CustomCondition` 适合代码定义的服务端业务判断。

已有 `ICondition` 可通过 `asDialogueCondition()` 桥接。这样 Quest/Trade 条件和 Dialogue 选项共享同一 flag/variable 规则。

自定义条件必须：

- 对缺失 player/entity/session 状态定义行为；
- 只读、快速、确定；
- 不把服务端 lambda 当成可序列化数据；
- 用 `describe` 或稳定类型名提供诊断。

## `DialogueAction`

动作在服务端执行。`LambdaAction(BiConsumer<ServerPlayer, Entity>)` 可接入外部系统，但应把复杂业务委托到附属 Service，而不是在 Builder 中堆积长 lambda。

动作实现要验证：

- 玩家仍在线且 Session 有效；
- Entity 仍存在、维度一致、距离合法；
- Quest/Shop/Guide ID 已注册；
- 数量和命令输入有边界；
- 外部调用失败不会留下半完成事务。

## 三种 NPC 接入方式

### 1. 自有实体实现 `IDialogueNpc`

适合附属能修改实体类。实体直接提供对话 ID、显示名、互动策略和会话回调。common 实体类仍不得引用客户端 Screen。

### 2. `IEntityDialogueExtension<T>`

适合不能修改的实体类型。必须实现：

```text
EntityType<T> getEntityType()
boolean canInteractWith(Player player, T entity)
@Nullable String getDialogueTreeId(ServerPlayer player, T entity,
                                   InteractionHand hand, NpcBinding binding)
```

可覆盖：

- `interactionPolicy`：默认 `PARALLEL_PRIVATE`；
- `onDialogueStart` / `onDialogueEnd` / `onTalkingTick`；
- `shouldCancelInteract`：是否替代原版/其他模组交互；
- `maxTalkDistance`；
- `shouldLookAtPlayer` / `shouldStopMoving`；
- `getProgressScope` / `getProgressNamespace`；
- `getClientData`。

`getDialogueTreeId` 返回 `null` 表示该次不打开对话。动态选择树时必须只返回已注册 ID。

`shouldCancelInteract` 返回非 null `InteractionResult` 会影响原交互。村民、商人等实体应明确：ArcQ 对话是否替代交易，还是只在特定条件命中时替代。

### 3. `NpcSpec` / `NpcBindingSpec`

适合按 EntityType、标识和条件维护多条绑定。通过 `ArcQuestRegistrationEvent.Npc#register` 注册，Registry 会在运行期解析最合适 binding。

代码绑定和数据包绑定存在分层快照；附属不要调用 `replaceDatapackSnapshot`、`clear` 或 `freeze`。

## 进度作用域

`ProgressScope` 控制节点历史/冷却如何区分 NPC：

- `DIALOGUE_TREE`：同一树共享进度；
- `INSTANCE`：按实体实例；
- `CUSTOM`：通过 `getProgressNamespace(entity)` 返回附属稳定 namespace。

不要把瞬时 entityId 用作跨重启 CUSTOM namespace。使用 UUID、剧情 actor ID 或附属持久标识，并设置长度边界。

## 会话权威与恢复

`DialogueSessionManager` 在服务端处理开始、选项、自动推进和结束。客户端只提交选项意图。

打开 Trade/Gacha/Guide 的 Choice 动作会保存恢复节点。附属自定义 Screen 若要不中止对话，应使用正式会话恢复入口或事件，不要直接重建 `DialogueScreen` 和伪造 Session。

## 事件

- `DialogueStartingEvent`：开始前，可通过事件自身取消并提供原因；
- `DialogueStartedEvent`：Session 已建立；
- `DialogueNodeStartedEvent`：节点、sayId、显示文本和可选声音已确定；
- `DialogueChoiceSelectedEvent`：服务端接受了选择；
- `DialogueNodeAutoAdvancedEvent`：自动跳转；
- `DialogueRestoreAttemptEvent` / `DialogueRestoreFailedEvent`：次级 UI 后恢复；
- `DialogueEndedEvent`：会话终止；
- `NpcDialogueInteractionEvent`：实体交互接入前，可按事件自己的 API 取消。

监听结果事件时不要重复执行 Builder 已配置的 Action。需要审计时记录 treeId/nodeId/choiceId/player UUID，而不是保存 Session 强引用。

## 专用服务器安全

Dialogue Definition、Action、Condition、NPC extension 都属于 common/服务端路径，不能 import：

```text
net.minecraft.client.Minecraft
net.minecraft.client.gui.screens.Screen
org.arcadia.arc_quest.client.*
```

画像、叠层和 Screen 表现通过客户端 Skill 的正式 Registry 扩展。
