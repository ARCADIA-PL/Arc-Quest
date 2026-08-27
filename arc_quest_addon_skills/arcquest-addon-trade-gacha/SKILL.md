---
name: arcquest-addon-trade-gacha
description: Implement Arc Quest code-defined trade shops and gacha shops for Forge 1.20.1 using builders, offers, conditions, limits, cooldowns, pity, runtime opening, and trade/gacha events. Excludes trade and gacha datapacks.
---

# Arc Quest 商店与抽奖附属

用于代码注册完整/简易商店、交易条目、抽奖池、保底、限购和相关事件。

## 必读

- [Trade API](references/trade.md)
- [Gacha API](references/gacha.md)
- [Trade 与 Gacha API 深入参考](references/api-reference.md)
- [运行时与安全模式](references/runtime.md)
- [Trade/Gacha 需求实现配方](references/scenario-recipes.md)

完整源码见 [ExampleTradeGachaContent.java](../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ExampleTradeGachaContent.java)、[ScenarioAdvancedTradeGachaContent.java](../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioAdvancedTradeGachaContent.java) 和 [ScenarioCommerceQuestBridge.java](../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioCommerceQuestBridge.java)。

## 工作流

1. 在 `ArcQuestRegistrationEvent.Trade` / `.Gacha` 中注册 Definition。
2. Trade 使用 `TradeShopBuilder` + `TradeEntryBuilder` + `ITradeOffer`。
3. Gacha 使用 `GachaShopBuilder`，明确成本、池项权重、数量范围、保底和抽取限制。
4. 从对话打开时优先使用 `ChoiceBuilder.openTrade/openSimpleTrade/openGacha`。
5. 修改购买/抽取决策时使用可取消的 Pre/Attempt 事件；奖励和经济状态只在服务端处理。

## 约束

- 客户端只显示服务端同步状态，不能信任客户端提交的 shopId、entryId、成本或抽取结果。
- Trade `condition()` 同时设置可见性和购买资格；需要分离时用 `visibleCondition()` 与 `canBuyCondition()`。
- 权重必须为正，数量范围必须有效；`maxDraws(-1)` 和 `unlimited()` 表示无限。
- 不直接调用 C2S packet handler 作为通用 API；对话 Builder 已提供正式打开入口，其他服务端打开场景按 runtime reference 中的现有入口处理。
