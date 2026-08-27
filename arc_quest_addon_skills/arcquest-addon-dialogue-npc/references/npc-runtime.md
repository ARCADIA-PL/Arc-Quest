# NPC 接入与会话生命周期

## 方式一：实体直接实现 `IDialogueNpc`

适用于附属拥有并能修改的实体类。唯一必须实现的方法是：

```text
@Nullable
String getDialogueId(ServerPlayer player);
```

返回已注册 Dialogue ID；返回 null 表示当前不打开对话。默认行为：非潜行、玩家存活、距离不超过 5 格，NPC 注视玩家并停止移动。

可重写：

- `asEntity()`：组件式宿主需要返回真实实体；默认强转 `this`。
- `canDialogueWith(Player)`、`getMaxDialogueDistance()`。
- `shouldLookAtPlayer()`、`shouldStopMoving()`。
- `buildDialogueContext(ServerPlayer)`：默认放入 `playerName`。
- `getDialogueDisplayName()`。

`startDialogueWith` 已完成可交互检查、对话 ID、上下文、NPC 占用状态和 `DialogueSessionManager` 委托。一般不需要覆盖。

## 方式二：`IEntityDialogueExtension<T>`

适用于原版或其他模组实体。附属应在 `ArcQuestRegistrationEvent.Npc` 中显式注册：

```text
@SubscribeEvent
public static void registerNpcExtensions(ArcQuestRegistrationEvent.Npc event) {
    event.registerExtension(new VillagerDialogueExtension());
}
```

尽管 `@EntityDialogueExtension` 文档提到自动扫描，当前最可靠的公开生命周期仍是 `event.registerExtension(...)`；不要让附属正确性依赖未核实的扫描环境。

必须实现：

- `EntityType<T> getEntityType()`。
- `boolean canInteractWith(Player, T)`。
- `@Nullable String getDialogueTreeId(ServerPlayer, T, InteractionHand, NpcBinding)`。

可选回调：

- `interactionPolicy(...)`。
- `onDialogueStart(..., DialogueSession)` / `onDialogueEnd(...)`。
- `shouldCancelInteract(...)`：非 null 时取消原交互并返回该 `InteractionResult`。
- `maxTalkDistance()`、注视/停移动控制、`onTalkingTick(...)`。
- `getProgressScope()` / `getProgressNamespace(...)`。
- `getClientData(...)`。

`onTalkingTick` 是高频服务端路径，只处理当前会话实体的有界状态，不扫描全世界。

## 方式三：`NpcSpec`

`NpcSpec` 是可直接注册的代码对象，适合按实体类型和多条 binding 配置对话：

- `entityType`：完整实体类型 ID 字符串。
- `bindings`：`NpcBindingSpec` 列表。
- `cancelVanillaInteract`。
- `dialogueDistance`。
- `shouldLookAtPlayer` / `shouldStopMoving`。
- `interactionPolicy`。
- `interactCondition`。
- `onDialogueStartCommands` / `onDialogueEndCommands`。

`NpcBindingSpec` 包含 `bindingId`、`dialogueId`、`dialogueIdFromNbt`、`condition` 和 `priority`。动态 NBT 绑定属于不可信输入，必须保证最终 ID 指向允许的已注册对话，不要让任意 NBT 打开任意服务端流程。

注册使用 `ArcQuestRegistrationEvent.Npc#register(NpcSpec)`。这是代码附属用法，不代表本 Skill 覆盖 NPC JSON 数据包。

## 交互策略

`NpcInteractionPolicy` 枚举包含：

- `PARALLEL_PRIVATE`
- `EXCLUSIVE`
- `QUEUED`
- `GROUP_SHARED`
- `SPECTATE_SHARED`

当前 `isImplemented()` 只对 `PARALLEL_PRIVATE` 和 `EXCLUSIVE` 返回 true。附属不要选择其余常量并假设队列、共享或旁观语义已经存在。

## 会话权威边界

`DialogueSessionManager` 在服务端管理开始、选择、节点推进、恢复和结束。客户端仅展示同步快照并发送选择请求。服务端必须重新验证：

- 玩家与 Session 对应；
- NPC 仍存在、维度一致且距离有效；
- node/choice 属于当前 Session；
- 条件、one-time 和 cooldown 仍成立；
- 原交互取消行为与对话是否成功打开一致。

附属不直接调用 C2S 包 handler，也不修改客户端 Session 缓存模拟选择。

## 开始事件

`DialogueStartingEvent` 暴露玩家、可空 NPC、当前 `DialogueTree` 和 `DialogueContext`，并提供独立的 `cancel(reason)` / `setCancelled(boolean)`。当前事件没有 `setDialogue(...)`，所以只能取消，不能通过事件替换树；动态选择应在 `getDialogueId` / `getDialogueTreeId` 阶段完成。

`NpcDialogueInteractionEvent` 同样使用自身的 `setCancelled(boolean)`，不是 Forge `@Cancelable` 注解语义。只调用事件类实际公开的方法。
