# Quest 定义与 Builder API

## 入口与注册期

Quest 代码定义应在 MOD 总线的 `ArcQuestRegistrationEvent.Quest` 中创建并注册。事件发生在 ArcQ 内置内容注册之后、`QuestRegistry.freeze()` 之前。

```text
@Mod.EventBusSubscriber(modid = ExampleArcQuestAddon.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ExampleQuestRegistration {
    private static final ResourceLocation QUEST_ID =
            ResourceLocation.fromNamespaceAndPath(ExampleArcQuestAddon.MOD_ID, "first_steps");

    @SubscribeEvent
    public static void registerQuests(ArcQuestRegistrationEvent.Quest event) {
        QuestDefinition quest = QuestBuilder.create(QUEST_ID)
                .displayName(Component.translatable("quest.example_addon.first_steps"))
                .description(Component.translatable("quest.example_addon.first_steps.description"))
                .phase(PhaseBuilder.create("collect_apples")
                        .displayName(Component.translatable("phase.example_addon.collect_apples"))
                        .objective(ObjectiveBuilder.collect(Items.APPLE, 4)
                                .id("apples")
                                .display(Component.translatable("objective.example_addon.apples"))))
                .startAt("collect_apples")
                .build();
        event.register(quest);
    }
}
```

不要在静态初始化器中调用 `buildAndRegister()`。它直接写入公开 Registry，仅适合已经处于正确注册窗口的代码；事件参数的 `register(...)` 更清楚，也更容易检查重复 ID。

## `QuestBuilder`

`QuestBuilder.create(ResourceLocation)` 是附属首选。`create(String)` 对无 namespace 的字符串回退到 `arc_quest`，附属不要依赖该回退。

主要配置：

| 领域 | 方法 | 语义 |
| --- | --- | --- |
| 文本 | `displayName(...)`、`description(...)` | 接受字面文本、`Component` 或 `QuestText`；附属发布内容优先翻译键 |
| 分类与排序 | `category(QuestCategory)`、`sortOrder(int)` | 控制日志分类和稳定排序 |
| 图标与主题 | `icon(Item/ItemStack/ResourceLocation)`、`themeColor(...)` | Item 图标走原版物品模型；纹理必须来自可用资源 |
| Splash | `acquisitionSplash`、`detailSplash`、`completionSplash` | 分别用于获得、详情和完成展示 |
| 生命周期 | `repeatable()`、`abandonable(boolean)`、`canBeAutoTrack(boolean)` | 控制重复接受、放弃和自动追踪资格 |
| 解锁 | `unlockCondition(ICondition)`、`requiresQuest(...)`、`requiresFlag(...)` | 所有解锁条件共同参与接受校验 |
| Phase | `phase(...)`、`startAt(...)`、`setInitialPhase(s)` | Phase ID 在单个 Quest 内唯一；多初始 Phase 支持并行开始 |
| 完成策略 | `completionPolicy(...)`、`completionRequiredCount(...)`、`completionTargetPhase(...)` | `ALL`、`ANY`、`N_OF_M`、`SPECIFIC_PHASE` 的参数组合会在 `build()` 校验 |
| 时间限制 | `questTimeLimitSeconds(...)`、`questTimeLimitDayTicks(...)` | 值必须大于 0；分别使用真实秒和世界日时间 |
| 奖励与 Flag | `reward(IReward)`、`setFlagOnAccept(...)`、`setFlagOnComplete(...)` | 由正常接受/完成流程统一执行 |
| 商店 | `chapterTradeShop(...)`、`chapterGachaShop(...)` | 将章节商店与任务关联；ID 必须对应已注册 Definition |
| Marker | `markRelatedObject(...)`、`markOnAccept(...)` | 连续标点与事件触发标点语义不同，详见 Marker Skill |

`build()` 会拒绝空 Phase、未知初始 Phase、未知转移目标、重复 Phase ID，以及不合法的完成策略组合。保留这些失败，不要捕获后继续注册残缺 Definition。

## `PhaseBuilder`

`PhaseBuilder.create(String phaseId)` 创建 Quest 内稳定阶段。常用方法：

- 内容：`displayName`、`description`、`story`。
- 目标：`objective(ObjectiveBuilder)` 或 `objective(ObjectiveEntry)`。
- 转移：`thenGoTo(...)`、`thenGoToIf(...)`、`transition(PhaseTransition)`。
- 玩家选择：`choice(...)` 或完整 `ChoiceOption`。
- 阶段奖励：`reward(IReward)`。
- 状态联动：`setFlagOnEnter`、`setFlagOnComplete`、`grantGuideOnEnter`、`grantGuideOnComplete`。
- 条件激活：`enterWhen(ICondition)`；第二重载可控制是否由条件自动进入。
- 推进：`autoAdvanceOnComplete(boolean)`。关闭后阶段完成会等待玩家确认。
- 视觉：`historyImage(...)`、`themeColor(...)`、阶段声音与 `intelScene(...)`。
- Marker：`markOnEnter`、`markOnComplete`、`markOnAdvance`、`trackingMarker(...)`。

并行阶段示例：

```text
QuestBuilder.create(QUEST_ID)
        .displayName(Component.translatable("quest.example_addon.scouting"))
        .phase(PhaseBuilder.create("forest")
                .displayName(Component.translatable("phase.example_addon.forest"))
                .objective(ObjectiveBuilder.custom(
                        ResourceLocation.fromNamespaceAndPath(ExampleArcQuestAddon.MOD_ID, "scan_forest"), 1)
                        .id("scan_forest")))
        .phase(PhaseBuilder.create("village")
                .displayName(Component.translatable("phase.example_addon.village"))
                .objective(ObjectiveBuilder.talk(
                        ResourceLocation.fromNamespaceAndPath(ExampleArcQuestAddon.MOD_ID, "village_chief"))
                        .id("talk_chief")))
        .setInitialPhase("forest", "village")
        .completionPolicy(QuestCompletionPolicy.ALL)
        .build();
```

在并行 Quest 中，运行时可能同时存在多个 active Phase。不要使用“当前 Phase”单值假设；查询 `QuestRuntimeData#getActivePhaseIds()`，推进时显式携带 `phaseId`。

## `ObjectiveBuilder`

内置构造入口：

- `kill(EntityType<?>, int)`：击杀实体。
- `collect(Item, int)` / `collectTag(ResourceLocation, int)`：持有物品或物品标签。
- `talk(ResourceLocation)`：与 NPC ID 对话。
- `deliver(Item, int, ResourceLocation)`：向 NPC 交付。
- `reachLocation(ResourceLocation, x, y, z, radius)`：到达位置。
- `interact(ResourceLocation)`：交互目标。
- `offer(Item, int)` / `offerTag(...)`：提交物品。
- `custom(ResourceLocation customId, int)`：使用内置 `arc_quest:custom` Objective 类型和附属目标键。
- `nullObjective()`：无需普通计数追踪的占位目标。

配置方法：

- `id(String)`：稳定 Objective ID。强烈建议每个 Objective 都设置。
- `display(...)`：客户端展示文本。
- `count(int)`：覆盖目标数量。
- `countModifier(ToIntFunction<ServerPlayer>)`：服务端按玩家动态计算数量。
- `hidden()` / `optional()`：控制展示和 Phase 完成要求。
- `extra(String, String)`：给扩展逻辑提供有界字符串元数据。
- `markRelatedObject(...)` / `markOnComplete(...)`：目标关联标点。

`countModifyer(...)` 是为旧拼写保留的兼容方法，新代码使用 `countModifier(...)`。

## 自定义 Objective 类型

`ObjectiveTypeRegistry.register(...)` 注册 `ObjectiveTypeDefinition` 元数据，但不会自动创建：

- Forge 事件监听器；
- `ObjectiveTracker` 条目或推进逻辑；
- 网络协议字段；
- 客户端专用 renderer。

只需要“附属监听事件并按键推进”时，优先使用 `ObjectiveBuilder.custom(customId, count)`。完全自定义 `ObjectiveType` 时，可直接构造 `ObjectiveEntry`，但附属必须同时承担服务端推进、展示回退和版本兼容。

## 奖励

`IReward` 只有两个方法：

```text
void grant(ServerPlayer player);
String describe();
```

内置代码奖励包括 `ItemReward`、`CommandReward`、`FlagReward`、`VariableReward`。自定义奖励在服务端执行，必须：

- 对数量和输入设边界；
- 处理背包已满或输出失败；
- 不访问客户端类；
- 给 `describe()` 返回可诊断但不包含敏感数据的描述；
- 明确重复调用行为，避免断线重试或业务重入造成重复外部副作用。

## Quest Group

使用 `QuestGroupDefinition` 创建分组，然后通过注册事件分配：

```text
QuestGroupDefinition group = QuestGroupDefinition.translated(
        ResourceLocation.fromNamespaceAndPath(ExampleArcQuestAddon.MOD_ID, "mainline"),
        "quest_group.example_addon.mainline");
event.registerGroup(group);
event.register(quest);
event.assignQuestToGroup(quest.getId(), group.getId());
```

分组构造器还支持 `sortOrder` 与 `themeColor`。先注册 Group 和 Quest，再分配关系，最容易获得明确的缺失 ID 诊断。
