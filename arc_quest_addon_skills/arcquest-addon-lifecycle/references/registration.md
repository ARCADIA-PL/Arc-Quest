# 注册生命周期

## ArcQ 的实际顺序

ArcQ 主类在 `FMLCommonSetupEvent` 中调用 `event.enqueueWork`，按以下顺序执行：

```text
注册 ArcQ 内置 Marker resolver 与内置内容
发布 ArcQuestRegistrationEvent.Quest
发布 ArcQuestRegistrationEvent.Dialogue
发布 ArcQuestRegistrationEvent.Npc
发布 ArcQuestRegistrationEvent.Trade
发布 ArcQuestRegistrationEvent.Gacha
发布 ArcQuestRegistrationEvent.Guide
注册 ArcQ 网络通道
冻结 Quest/Group/Dialogue/Npc/Extension/Trade/Gacha/Guide/Group Registry
```

因此附属必须在自己的模组构造阶段把监听器挂入 MOD 总线。

## 推荐监听方式

```text
@Mod(ExampleArcQuestAddon.MOD_ID)
public final class ExampleArcQuestAddon {
    public static final String MOD_ID = "example_addon";

    public ExampleArcQuestAddon() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(AddonContent::registerQuests);
        modBus.addListener(AddonContent::registerDialogues);
        modBus.addListener(AddonContent::registerNpcExtensions);
        modBus.addListener(AddonContent::registerGuides);
    }
}
```

```text
public final class AddonContent {
    public static void registerQuests(ArcQuestRegistrationEvent.Quest event) {
        event.register(buildQuest());
    }

    public static void registerDialogues(ArcQuestRegistrationEvent.Dialogue event) {
        event.register(buildDialogue());
    }

    public static void registerNpcExtensions(ArcQuestRegistrationEvent.Npc event) {
        event.registerExtension(new VillagerDialogueExtension());
    }

    public static void registerGuides(ArcQuestRegistrationEvent.Guide event) {
        event.register(buildGuide());
    }
}
```

也可使用 `@Mod.EventBusSubscriber(bus = Bus.MOD)` 静态监听器。不要将这些方法订阅到 `MinecraftForge.EVENT_BUS`。

## 事件提供的方法

- `Quest`: `register(QuestDefinition)`、`registerGroup(QuestGroupDefinition)`、`assignQuestToGroup(ResourceLocation, ResourceLocation)`，另有字符串重载。
- `Dialogue`: `register(DialogueTree)`。
- `Npc`: `register(NpcSpec)`、`registerExtension(IEntityDialogueExtension<?>)`。
- `Trade`: `register(TradeShopDefinition)`。
- `Gacha`: `register(GachaShopDefinition)`。
- `Guide`: `register(GuideDefinition)`、`registerGroup(GuideGroupDefinition)`、`assignGuideToGroup(...)`。

`GachaShopBuilder` 当前没有独立 `build()`，其 `buildAndRegister()` 会同时写入 Trade 与 Gacha Registry。在 Gacha 注册事件内使用该 Builder 时只调用一次 `buildAndRegister()`，不要再把返回值传给 `event.register(...)`。

## `ArcQuestAPI` 门面

事件方法内部委托 `ArcQuestAPI`。门面还提供查询：

- Quest：`getQuest`、`getQuestOrThrow`、`hasQuest`、Quest Group 与来源查询。
- Dialogue：`getDialogueTree`、`hasDialogueTree`、来源查询。
- Guide：`getGuide`、`hasGuide`、Guide Group 与来源查询。
- Trade/Gacha：注册、查询或来源查询。
- Marker：注册、注销、查询自定义 `MarkTargetResolver`。

附属内容注册优先使用 event 参数；跨模块查询可使用 `ArcQuestAPI`。

## 冻结和重复 ID

- Registry 冻结后代码注册抛 `IllegalStateException`。
- 同一个代码 ID 重复注册通常抛异常，而不是覆盖。
- 代码定义与数据包同 ID 时，代码定义优先，并在来源信息中记录数据包被忽略。
- 不调用 Registry 的 `clearAll/clear/freeze/replaceDatapack...`；这些是 ArcQ 生命周期和测试入口。

## 客户端初始化

客户端 Registry 应在附属客户端 setup 的 `enqueueWork` 中注册一次：

```text
@Mod.EventBusSubscriber(modid = ExampleArcQuestAddon.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AddonClientSetup {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(AddonArcQuestClientExtensions::register);
    }
}
```

通用类不得静态引用该客户端类。
