# 对话树与动作 API

## 注册入口

在 MOD 总线监听 `ArcQuestRegistrationEvent.Dialogue`，构建 `DialogueTree` 后调用 `event.register(tree)`。

```text
@Mod.EventBusSubscriber(modid = ExampleArcQuestAddon.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ExampleDialogues {
    @SubscribeEvent
    public static void register(ArcQuestRegistrationEvent.Dialogue event) {
        DialogueTree tree = DialogueTreeBuilder.create(
                        ResourceLocation.fromNamespaceAndPath(ExampleArcQuestAddon.MOD_ID, "blacksmith_intro"))
                .npc(DialogueText.translatable("dialogue.example_addon.blacksmith"))
                .startNode("start")
                .node("start")
                .speaker(DialogueText.translatable("dialogue.example_addon.blacksmith"))
                .say(DialogueText.translatable("dialogue.example_addon.blacksmith.intro"), "intro")
                .choice("accept", DialogueText.translatable("dialogue.example_addon.accept"), choice -> choice
                        .startQuest("example_addon:first_steps")
                        .close())
                .choice("leave", DialogueText.translatable("dialogue.example_addon.leave"), choice -> choice.close())
                .build();
        event.register(tree);
    }
}
```

`create(String)` 对无 namespace 的 ID 回退到 `arc_quest`，附属使用 `ResourceLocation` 或完整字符串。

## 树级配置

- `npc(DialogueText)`：默认 NPC 名称。
- `startNode(String)`：入口节点；必须存在。
- `visualConfig(QuestVisualConfig)`：对话视觉配置。
- `oneTime()`、`repeatable(boolean)`：树级重复策略。
- `cooldown(long)`：真实秒冷却。
- `cooldownGameDay()`：按游戏日重置。
- `cooldownGameTick(int resetTick)`：按指定日内 tick 重置。
- `markRelatedObject(...)`、`markOnNodeEnter(...)`：关联和节点进入 Marker。

## 节点配置

调用 `node(nodeId)` 开始一个节点。后续 `speaker`、`say`、`sayIf`、`enterSound`、`delay`、`choice`、`choiceIf`、`autoNext`、`nodeOneTime` 和 `nodeCooldown...` 都作用于当前节点。

每条说话内容使用稳定 `sayId`。条件文本按 priority 选择时，不要复用同一 ID 表示不同业务语义。

`DialogueText` 支持：

- `literal(String)`；
- `translatable(String, DialogueArg...)`；
- `of(Function<DialogueTextContext, Component>)`；
- 兼容重载 `of(BiFunction<ServerPlayer, Entity, Component>)` 和三参数 resolver。

发布内容优先 `translatable`。动态 resolver 在服务端解析，禁止执行世界全量扫描或阻塞 I/O。

## Choice 动作

`ChoiceBuilder` 可串联多个 `DialogueAction`，按定义顺序执行。主要方法：

| 方法 | 行为 |
| --- | --- |
| `goTo(nodeId)` / `close()` | 跳转或结束会话 |
| `startQuest(questId)` | 走任务接受流程 |
| `completeQuest(questId)` | 当前实现直接写完成状态，不等同完整 Quest 结算，正常奖励场景不要使用 |
| `advancePhase(questId)` | 推进关联任务阶段，使用前核对并行 Phase 语义 |
| `giveXp`、`giveItem`、`runCommand` | 服务端副作用 |
| `notifyTalk`、`notifyInteract` | 通知内置 Objective 追踪 |
| `setFlag`、`setVariable` | 修改 ArcQ 玩家状态 |
| `unlockGuide`、`openGuide` | 发放或打开 Guide |
| `openTrade`、`openSimpleTrade`、`openGacha` | 打开已注册商店 |
| `action(DialogueAction)` | 添加正式动作对象 |
| `addEvents(BiConsumer<ServerPlayer, Entity>)` | 添加代码 lambda 动作 |
| `onlyIf(DialogueCondition)` / `conditionIf(...)` | 限制选择或动作 |
| `oneTime`、`cooldown...`、`priority` | 选择可用性和排序 |
| `restoreToNode` / `restoreToCurrentNode` | 会话恢复位置 |
| `selectSound` | 选择音效 |

需要从对话完整完成 Quest 时，使用代码动作：

```text
.choice("finish", DialogueText.translatable("dialogue.example_addon.finish"), choice -> choice
        .addEvents((player, npc) -> QuestProgressHandler.forceCompleteResult(
                player, "example_addon:first_steps"))
        .close())
```

`addEvents` 和动态 lambda 只适用于代码 Definition，不可序列化，也不能作为数据包契约。

## 条件与恢复

`DialogueCondition` 负责节点文本和 Choice 可用性。条件在服务端会话中判断，应只读取当前玩家、NPC 和已加载状态。树级、节点级和 Choice 级 one-time/cooldown 记录会参与恢复。

选择恢复目标时，确保目标节点存在且在当前条件下仍有可展示内容。`DialogueRestoreAttemptEvent` 和 `DialogueRestoreFailedEvent` 用于观察恢复，不要通过客户端缓存自行恢复服务端 Session。

## 构建校验

`build()` 会固化节点并返回 `DialogueTree`。至少检查：

- start node 存在；
- 所有 `goTo`、`autoNext` 和恢复节点存在；
- Choice ID、say ID 和 node ID 稳定；
- 对话商店、Guide、Quest ID 使用完整 namespace；
- 动作不会信任客户端输入或形成无限跳转。
