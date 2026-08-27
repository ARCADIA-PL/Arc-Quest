# Quest 代码模式

## 模式一：由外部模组事件推进

适用于技能释放、宝可梦战斗结果、机器操作等 ArcQ 不直接认识的行为。

1. Quest 使用 `ObjectiveBuilder.custom(example_addon:action_id, count)`。
2. 附属监听真实服务端成功事件。
3. 检查玩家、维度、目标和操作结果。
4. 调用 `QuestProgressHandler.incrementObjective(...)`。

这比注册全新 `ObjectiveType` 更稳定，因为客户端仍可显示通用 Objective，服务端扩展只负责产生进度。

## 模式二：任务完成后开启附属内容

优先监听 `QuestCompletedEvent`，按完整 `ResourceLocation` 过滤，再调用附属 Service。不要轮询 `ArcQuestPlayer#getCompletedQuests()`。

```text
@Mod.EventBusSubscriber(modid = ExampleArcQuestAddon.MOD_ID)
public final class QuestCompletionHooks {
    @SubscribeEvent
    public static void onCompleted(QuestCompletedEvent event) {
        if (!event.getQuestId().equals(ExampleQuestIds.FIRST_STEPS)) return;
        ExampleFeatureService.unlock(event.getPlayer());
    }
}
```

事件位于 Forge 总线，默认订阅声明不要指定 `Bus.MOD`。

## 模式三：并行阶段汇合

用 `setInitialPhase("branch_a", "branch_b")` 启动两个 Phase，两个 Phase 均 `thenGoTo("join")` 时，需要结合 Definition 的转移与激活条件设计汇合语义。不要假设第一个完成的分支会自动代表另一个已完成。

若 Quest 完成要求两个分支均完成，使用 `QuestCompletionPolicy.ALL`；若只需完成其一，使用 `ANY`；需要任意 N 个分支时使用 `N_OF_M` 并设置 `completionRequiredCount`。

## 模式四：动态目标数量

`countModifier(ToIntFunction<ServerPlayer>)` 在服务端解析玩家所需数量。函数必须：

- 纯读取或低成本；
- 返回有界正数；
- 不执行网络、磁盘或世界扫描；
- 在同一任务生命周期中保持可理解的稳定性。

如果目标数量会随装备实时下降，已有进度与完成判断可能产生突变。更稳妥的是接受 Quest 时把业务值固化到 ArcQ variable，再由 resolver 读取。

## 常见错误

- 使用无 namespace 的 Quest 字符串，意外注册到 `arc_quest`。
- Objective 未设置稳定 ID，后续重排导致外部事件推进错目标。
- 在客户端事件中调用 `QuestProgressHandler`。
- 直接改 `QuestRuntimeData` 并手动发包。
- 把 `ChoiceBuilder.completeQuest` 当成完整 Quest 奖励流程；当前对话动作不具备该语义。
- 在每 tick 扫描全部玩家、全部 active Quest 和全部 Objective。
- 在 Registry freeze 后注册 Definition 或 Objective 类型。

## 最低验证

- 附属 `compileJava`。
- 专用服务器启动，确认公共代码没有客户端类加载。
- 接受、普通推进、Phase 完成、Quest 完成、放弃和重进存档。
- 并行 Quest 检查 active Phase、聚焦 Phase 持久化和单 Phase 强制完成。
- 背包已满、奖励策略 `SKIP/REPLACE`、重复事件和断线重连。
