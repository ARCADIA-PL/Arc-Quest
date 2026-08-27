# Trade 商店 API

## 注册

在 MOD 总线的 `ArcQuestRegistrationEvent.Trade` 中构建并注册 `TradeShopDefinition`：

```text
@SubscribeEvent
public static void registerTrade(ArcQuestRegistrationEvent.Trade event) {
    TradeShopDefinition shop = TradeShopBuilder.create(
                    ResourceLocation.fromNamespaceAndPath(ExampleArcQuestAddon.MOD_ID, "supply_shop"))
            .displayName(Component.translatable("trade.example_addon.supply_shop"))
            .description(Component.translatable("trade.example_addon.supply_shop.description"))
            .entry(TradeEntryBuilder.create("bread")
                    .displayName(Component.translatable("trade.example_addon.bread"))
                    .costItem(Items.EMERALD, 2)
                    .rewardItem(Items.BREAD, 4)
                    .maxPurchases(3))
            .build();
    event.register(shop);
}
```

附属优先 `build()` + `event.register(...)`。`buildAndRegister()` 只在明确处于注册窗口时使用。

## `TradeShopBuilder`

- `create(ResourceLocation)` / `create(String)`：商店 ID。无 namespace 字符串回退到 `arc_quest`。
- `displayName`、`description`：支持字面文本、`Component`、`TradeText`。
- `simpleMode()`：使用简易商店表现。
- `openCondition(ICondition)`：服务端开启资格。
- `themeColor(...)`、`openSound(...)`、`closeSound(...)`。
- `category(...)`：可传 `TradeCategory`，也可用 ID、名称、排序和颜色创建。
- `entry(TradeEntry/TradeEntryBuilder)`：添加稳定 `entryId` 条目。

## `TradeEntryBuilder`

内容与表现：`displayName`、`description`、`category`、`sortOrder`、成本/奖励图标和主题色。

成本：

- `cost(ITradeOffer)`；
- `costItem(Item, count)`；
- `costItemTag(TagKey<Item>, count)`；
- `costItemDynamic` / `costItemTagDynamic`；
- `costFlag(String)`。

奖励：

- `reward(ITradeOffer)`；
- `rewardItem`；
- `rewardEffect`；
- `rewardFlag`；
- `rewardCommand`。

条件与次数：

- `visibleCondition(ICondition)` 只影响是否展示；
- `canBuyCondition(ICondition)` 只影响购买资格；
- `condition(ICondition)` 同时设置前两者；
- `maxPurchases(int)` / `unlimited()`；
- `purchaseResetCondition(Predicate<ServerPlayer>)`；
- `purchaseResetByCooldown()`；
- `cooldown`、`cooldownGameDay`、`cooldownGameTick`。

声音：购买成功、普通失败、冷却、达到上限、条件失败均有独立方法。

## `ITradeOffer`

交易成本和奖励共用 `ITradeOffer`：

```text
boolean canAfford(ServerPlayer player);
void execute(ServerPlayer player);
TradeMutation prepareMutation(ServerPlayer player, TradeOfferRole role);
Component describe();
@Nullable ResourceLocation getIcon();
int getDisplayAmount();
List<CostShortfallLine> buildShortfallLines(ServerPlayer player);
String getType();
```

内置实现：`ItemTradeOffer`、`FlagTradeOffer`、`EffectTradeOffer`、`CommandTradeOffer`、`CompositeTradeOffer`。

自定义 Offer 应优先覆盖 `prepareMutation`，为扣除失败提供可控回滚。默认实现是 `nonReversible`，只适合不会在后续步骤失败或可以接受不可逆副作用的操作。`canAfford` 与 `execute` 必须在相同服务端事实基础上工作，不能信任客户端余额预览。

## 动态成本

动态 resolver 是服务端函数，`previewCount` 只是客户端展示基线。实际购买仍以服务端解析结果为准。resolver 必须快速、有界，并保证返回合法数量。
