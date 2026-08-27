# Gacha 商店 API

## Builder 的注册特性

`GachaShopBuilder` 当前只有 `buildAndRegister()`，没有独立 `build()`。该方法会同时把底层 `TradeShopDefinition` 注册到 `TradeRegistry`，并把 `GachaShopDefinition` 注册到 `GachaRegistry`。

因此应在 `ArcQuestRegistrationEvent.Gacha` 的注册窗口内调用一次 `buildAndRegister()`，不要再把返回值传给 `event.register(...)`，否则会重复注册。

```text
@SubscribeEvent
public static void registerGacha(ArcQuestRegistrationEvent.Gacha event) {
    GachaShopBuilder.create(
                    ResourceLocation.fromNamespaceAndPath(ExampleArcQuestAddon.MOD_ID, "relic_draw"),
                    Component.translatable("gacha.example_addon.relic_draw"))
            .drawCost(new ItemTradeOffer(Items.EMERALD, 3, true))
            .addItem("iron", new ItemStack(Items.IRON_INGOT, 2),
                    80, GachaItem.Rarity.COMMON)
            .addItem("diamond", new ItemStack(Items.DIAMOND),
                    20, GachaItem.Rarity.RARE)
            .pitySystem(10, GachaItem.Rarity.RARE, true)
            .maxDraws(-1)
            .buildAndRegister();
}
```

`ItemTradeOffer` 的第三参数表示该 Offer 是否作为成本扣除；使用前以当前构造器源码为准。

## 商店配置

- `description`、`category`、`simpleMode(boolean)`、`themeColor`。
- `openCondition(ICondition)`：是否允许开启。
- `drawCondition(ICondition)`：是否允许本次抽取。
- `openSound`、`closeSound` 和各类抽取失败音效。
- `drawCost(ITradeOffer)`：可多次调用，Definition 保存成本列表。
- `drawCostIcon(ResourceLocation)`。
- `cooldown`、`cooldownGameDay`、`cooldownGameTick`。
- `maxDraws(int)`：`-1` 表示无限；其他值应为有效上限。
- `resetCondition`、`drawResetByCooldown`、`resetOnLimitReached`。
- `resetPityOnEarlyTrigger(boolean)`。

## 奖池项

`addItem` 的核心参数：

- `itemId`：商店内稳定唯一 ID；
- `rewardStack`：客户端预览与物品奖励信息；
- `reward`：实际 `ITradeOffer`；
- `weight`：必须为正；
- `rarity`：`GachaItem.Rarity`；
- `countsTowardsPity`；
- `minCount` / `maxCount`：范围必须合法；
- 可见条件、图标、主题色、成功音效和排序。

基础重载会自动创建物品 Reward Offer。自定义非物品奖励时仍要提供合理 `rewardStack` 作为展示锚点，并传入实际 `ITradeOffer`。

## 保底

简化入口：

- `pitySystem(threshold, guaranteedRarity, resetOnTrigger)`；
- `pitySystem(threshold, guaranteedItemId, resetOnTrigger)`。

完整入口还允许指定重置冷却类型、值、条件和触发后是否重置。指定 item ID 时必须引用同一奖池中存在的稳定 ID；按 rarity 时必须保证该稀有度至少有可抽项。

`rarityConfig` 配置稀有度默认主题色与抽中音效，单个奖池项的配置可覆盖默认表现。

## 随机与权威性

抽取、成本校验、次数、冷却、保底计数和奖励执行全部由服务端决定。客户端只展示同步状态。不要在客户端预抽结果后提交 item ID，也不要直接调用 `GachaPool#draw` 绕过完整购买事务和事件链。
