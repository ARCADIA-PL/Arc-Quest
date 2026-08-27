# Trade/Gacha 需求实现配方

## 需求：任务 flag 控制商店开放

### ArcQ 实现路径

在 `TradeShopBuilder#openCondition` 或 `GachaShopBuilder#openCondition` 使用 `ICondition.flagSet`。该条件控制能否打开整个商店，服务端会重新验证。

完整代码：[ScenarioAdvancedTradeGachaContent.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioAdvancedTradeGachaContent.java)。

## 需求：条目隐藏条件和购买条件不同

### 场景

高级工具只有取得会员 flag 后才显示；即使显示，仍要求基础商店权限。

### 实现

```text
visibleCondition(flagSet(premium))
canBuyCondition(flagSet(access).and(flagSet(premium)))
```

`condition()` 会同时设置二者，只在语义完全相同的简单条目使用。客户端可见状态只是表现，购买请求仍由服务端 `canBuyCondition` 和成本事务决定。

## 需求：每日限购商品

组合：

- `maxPurchases(1)`；
- `cooldownGameDay()` 或 `cooldownGameTick(resetTick)`；
- `purchaseResetByCooldown()`。

现实秒冷却适合短期补给，游戏日冷却适合按世界时间重置。多人服务器不要用客户端本地时间判断。

## 需求：对话打开商店并恢复

对话 Choice 使用 `openTrade/openSimpleTrade/openGacha`。关闭后恢复当前会话节点，不需要附属重建 Screen 或 Session。

完整调用：[ScenarioConditionalDialogueContent.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioConditionalDialogueContent.java)。

## 需求：打开特定商店后推进设施访问任务

### ArcQ 实现路径

```text
TradeOpenedEvent
  -> 匹配 shopId
  -> 验证 Quest/Phase active
  -> incrementObjective(对应分支)
```

完整代码：[ScenarioCommerceQuestBridge.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioCommerceQuestBridge.java)。

打开事件只证明服务端允许打开商店，不证明发生购买。若任务要求购买，改监听成功交易事件并匹配 entryId；不要用客户端 Screen open 作为依据。

## 需求：外部货币作为成本或奖励

实现 `ITradeOffer`，把查询、扣款或入账委托给外部经济模块。要求：

- `canExecute` 与执行时都在服务端重验余额；
- 扣款和奖励有明确顺序及失败补偿；
- 客户端只获得展示数据，不作为余额事实来源；
- 数值校验正数和上限，防止溢出；
- 外部 API 失败记录 player UUID、shopId、entryId、transaction ID；
- 重复包或重试使用幂等 transaction ID。

若外部经济模组是可选依赖，Offer 不应在缺失依赖时完成类加载；将实现放入独立兼容类，并在注册前检查依赖存在。

## 需求：抽奖池包含普通、稀有和保底

### ArcQ 实现路径

```text
drawCost(offer)
addItem(stableItemId, stack, positiveWeight, rarity)
pitySystem(threshold, targetRarity, enabled)
resetPityOnEarlyTrigger(boolean)
maxDraws(count)
cooldown/reset policy
```

完整定义：[ScenarioAdvancedTradeGachaContent.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioAdvancedTradeGachaContent.java)。

池项 ID 必须稳定，权重必须为正，ItemStack 数量和 NBT 有边界。`resetPityOnEarlyTrigger(true)` 表示保底前抽中目标品质也重置计数；false 则保留进度，必须按玩法明确选择。

## 需求：抽奖成功后设置剧情状态

监听 `GachaEvents.PostDrawEvent`，匹配 shopId 和 drawn item，再写 flag 或推进 custom Objective。不要在 `PreDrawEvent` 发奖励；Pre 可能被取消且尚未扣除成本。

[ScenarioCommerceQuestBridge.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioCommerceQuestBridge.java) 展示了成功后写 flag。

## 需求：在交易前阻止特定玩家

监听可取消的 `TradePurchaseAttemptEvent` 或 `GachaEvents.PreDrawEvent`：

- 只在服务端处理；
- 先匹配附属自己的 shop/entry；
- 检查权限、业务状态和冷却；
- 使用事件公开取消 API；
- 给客户端返回可翻译的简短拒绝原因；
- 不在取消事件内直接执行另一笔交易。

## 需求：遥测、成就和额外奖励

使用成功/Post 事件。为外部奖励建立 transaction/claim 去重，不依赖事件“通常只发一次”。事件监听器不要再次调用同一交易或抽取入口，避免递归。

## 验证清单

- 未满足 openCondition 无法通过对话或直接请求打开；
- visible 与 canBuy 分离正确；
- 成本不足、背包满、条目不存在均安全失败；
- 限购和冷却在重登后保持；
- 游戏日重置边界经过跨日测试；
- 抽奖权重、最大次数、保底及提前触发行为正确；
- 重复请求不重复扣款或发奖；
- 任务只由正确 shop/entry 事件推进；
- 客户端无法提交自选抽奖结果。
