---
name: arcquest-addon-dialogue-npc
description: Build Arc Quest code-defined dialogue trees and NPC integrations for Forge 1.20.1 using DialogueTreeBuilder, DialogueText, IDialogueNpc, IEntityDialogueExtension, NpcSpec, sessions, actions, conditions, and dialogue events. Excludes dialogue datapacks.
---

# Arc Quest 对话与 NPC 附属

用于代码对话树、实体直接实现对话、已有实体类型扩展、NPC 绑定和会话联动。

## 必读

- [对话树与动作 API](references/dialogue-api.md)
- [Dialogue 与 NPC API 深入参考](references/api-reference.md)
- [NPC 接入与会话生命周期](references/npc-runtime.md)
- [代码模式与风险](references/patterns.md)
- [Dialogue/NPC 需求实现配方](references/scenario-recipes.md)

完整源码见 [ExampleDialogueContent.java](../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ExampleDialogueContent.java)、[ScenarioConditionalDialogueContent.java](../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioConditionalDialogueContent.java) 和 [ExampleVillagerDialogueExtension.java](../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ExampleVillagerDialogueExtension.java)。

## 选择接入方式

- 自有实体类可修改：实现 `IDialogueNpc`。
- 不能修改的实体类型：实现 `IEntityDialogueExtension<T>`，并在 `ArcQuestRegistrationEvent.Npc` 中 `registerExtension`。
- 需要按实体类型配置多条绑定：构造 `NpcSpec` + `NpcBindingSpec` 并在 Npc 注册事件中注册。
- 仅需观察或阻止流程：监听 Dialogue/NPC Forge 事件。

## 约束

- 对话 Definition 在 `ArcQuestRegistrationEvent.Dialogue` 注册；NPC 扩展在 Npc 事件注册。
- `DialogueSessionManager` 的开始、选择和结束均由服务端权威处理；不要从客户端直接改会话。
- `DialogueAction.LambdaAction` 和 lambda 条件只适合代码定义，不能视为可序列化数据。
- `ChoiceBuilder.completeQuest` 当前不是完整任务结算流程。需要正常奖励和事件时用自定义 action 调用 `QuestProgressHandler.forceCompleteResult`。
- `NpcInteractionPolicy` 当前只有 `PARALLEL_PRIVATE` 和 `EXCLUSIVE` 标记为已实现，不要假设其余枚举已有完整行为。
