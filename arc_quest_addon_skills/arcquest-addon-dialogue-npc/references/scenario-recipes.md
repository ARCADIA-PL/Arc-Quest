# Dialogue/NPC 需求实现配方

## 需求：NPC 根据任务状态提供不同操作

### 场景

尚未接任务时显示“接受巡览”；进行市场分支时显示“打开市场”；任务完成后显示感谢选项。

### ArcQ 实现路径

使用同一 `DialogueTree` 的条件 choice：

- `DialogueCondition.Not(HasQuest)`：显示接受；
- `QuestPhaseActive(questId, phaseId)`：显示当前分支操作；
- `QuestCompleted(questId)`：显示完成后文本；
- 默认离开选项始终存在。

完整代码：[ScenarioConditionalDialogueContent.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioConditionalDialogueContent.java)。

### 关键判断

- 条件只读，不在 `test` 中接受任务或发奖励；
- 状态变更放进 `DialogueAction`；
- 每个 nodeId、choiceId 和 sayId 稳定；
- 并行任务使用 `QuestPhaseActive`，不用阶段声明顺序推断；
- 完成动作需要完整任务语义时调用 `QuestProgressHandler` 结果型入口。

## 需求：对话接受任务并立即进入后续节点

```text
choice action
  -> QuestProgressAdapter.ensureAccepted
  -> 返回 OK 才认为接受成功
  -> goTo(accepted)
```

简单 Builder 链会无条件执行后续跳转；若业务必须根据拒绝码显示不同节点，使用自定义 `DialogueAction` 记录结果 flag/variable，或在进入目标节点时再次用条件文本分流。不要在客户端预判前置条件并直接打开成功节点。

## 需求：对话确认一个 Phase 已整体完成

叙事确认、提交报告或外部战斗胜利可调用 `forceCompletePhaseResult(player, questId, phaseId)`。这比 `incrementObjective(..., 1)` 更明确，前提是整个 Phase 的所有业务条件确实已经由外部系统验证。

## 需求：从对话打开商店或抽奖，关闭后回到原节点

使用 `openTrade`、`openSimpleTrade`、`openGacha`，必要时显式 `restoreToCurrentNode()`。服务端会保存恢复上下文；附属不要：

- 客户端直接 `new TradeScreen`；
- 关闭商店后重新开始整棵 Dialogue；
- 手工构造 `DialogueSession`；
- 绕过 Shop 的 `openCondition`。

完整调用见 [ScenarioConditionalDialogueContent.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioConditionalDialogueContent.java)。

## 需求：只接管具有特定业务身份的村民

### 设计判断

按 `EntityType.VILLAGER` 全量接管会破坏原版交易和其他模组交互。扩展必须二次识别实体身份，例如职业、附属持久字段、tag、capability 或稳定 NPC ID。

### ArcQ 实现路径

实现 `IEntityDialogueExtension<Villager>`：

- `getEntityType()` 返回 `EntityType.VILLAGER`；
- `canInteractWith()` 同时检查存活、年龄和业务身份；
- `getDialogueTreeId()` 返回已注册 Dialogue ID；
- `shouldCancelInteract()` 命中时返回 `SUCCESS`，未命中返回 `PASS`；
- `maxTalkDistance()` 给出合理上限。

完整代码：[ExampleVillagerDialogueExtension.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ExampleVillagerDialogueExtension.java)。

## 需求：同一种实体按玩家状态选择不同对话树

在 `getDialogueTreeId(ServerPlayer, entity, hand, binding)` 中读取服务端 `ArcQuestPlayer`：

```text
quest completed -> post_story_dialogue
specific phase active -> submission_dialogue
quest active -> reminder_dialogue
otherwise -> introduction_dialogue
```

不要把选择结果缓存在实体静态字段，因为不同玩家状态不同。方法在交互时执行，应保持快速且无磁盘/网络 I/O。

## 需求：多个 NPC 共享或隔离对话历史

- 每个实体独立：使用实体级 progress namespace；
- 同一角色的替身共享：返回稳定角色 namespace；
- 全局教程 NPC 共享：明确使用全局业务 namespace；
- 不用显示名作为 namespace，改名或翻译会破坏进度。

通过 `IEntityDialogueExtension#getProgressScope` 与 `getProgressNamespace` 表达。发布后不要改变已有 namespace 语义。

## 需求：长文本和动态文本

长段落拆成多个稳定 node/sayId，通过 `autoNext` 或选择分页。动态 `DialogueText` 必须在缺少服务端玩家的展示环境提供可读 fallback。

不要 Mixin `DialogueText` 或 `DialogueTreeBuilder#say` 改全局分行语义；这会影响所有附属和已有对话恢复 ID。

## 需求：记录对话审计和调试信息

监听正式事件：

- `DialogueStartedEvent`：会话已开始；
- `DialogueNodeStartedEvent`：进入节点；
- `DialogueChoiceSelectedEvent`：选择已提交；
- `DialogueNodeAutoAdvancedEvent`：自动推进；
- `DialogueEndedEvent`：结束原因与清理。

保存 player UUID、dialogueId、nodeId、choiceId 等纯标识，不长期持有 Session 或 Entity。无需注入 `DialogueSessionManager`。

## 需求：对话画像或剧情叠层

客户端使用 `DialogueOverlayRegistry`，按 dialogueId/node snapshot 快速过滤。Overlay 每帧执行，只渲染同步数据；Screen 关闭后立即停止，不访问服务端对象或磁盘。

参考 [ExampleClientExtensions.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ExampleClientExtensions.java)。

## 验证清单

- 条件选项在 active/completed/flag 各状态下正确；
- 拒绝接受时不会显示伪成功文本；
- 商店/Guide 关闭后恢复原会话节点；
- 未命中业务身份的村民仍正常交易；
- 两名玩家与同一 NPC 的进度 scope 符合设计；
- 距离超限、NPC 移除、玩家断线能清理会话；
- 客户端 Overlay 在 GUI scale 2/3/4 不遮挡选项；
- 专服不加载 `DialogueScreen` 或其他客户端类。
