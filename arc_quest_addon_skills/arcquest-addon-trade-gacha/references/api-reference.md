# Trade 与 Gacha API 深入参考

## 共同边界

Trade 和 Gacha 都以服务端为权威：客户端只显示服务端同步的 Definition/状态并提交 shopId、entryId 或 draw 意图。服务端重新查询 Registry、玩家、条件、成本、次数和冷却，不能信任客户端价格或结果。

两者共享：

- `ICondition`；
- `ITradeOffer` 成本/奖励抽象；
- 玩家 `TradeDataStore` / `GachaDataStore`；
- 服务端同步和结构化失败事件；
- Dialogue Choice 打开入口。

## Trade Definition

`TradeShopBuilder` 负责 Shop，`TradeEntryBuilder` 负责条目。

### `TradeShopBuilder`

| 方法族 | 语义 |
| --- | --- |
| `create(ResourceLocation/String)` | 稳定 Shop ID |
| `displayName` / `description` | `String`、`Component`、`TradeText` |
| `simpleMode()` | 使用简易交易 UI |
| `openCondition(ICondition)` | 服务端能否打开 |
| `themeColor*` | Shop 视觉 |
| `openSound/closeSound` | 生命周期音效 |
| `category(...)` | 注册/添加条目分类 |
| `entry(TradeEntry/Builder)` | 添加条目 |
| `build()` | 构建 Definition |
| `buildAndRegister()` | 直接写 Registry |

在 `ArcQuestRegistrationEvent.Trade` 中优先 `event.register(builder.build())`。

### `TradeEntryBuilder`

身份与展示：

- `create(entryId)`：Shop 内稳定唯一 ID；
- `displayName` / `description`；
- `category`、`sortOrder`；
- `rewardIcon`、`costIcon`、`themeColor`。

成本/奖励：

- `cost(ITradeOffer)` / `reward(ITradeOffer)`；
- `costItem` / `costItemTag`；
- dynamic item/tag cost resolver；
- `costFlag`、`rewardFlag`；
- `rewardItem`、`rewardEffect`、`rewardCommand`。

条件：

- `visibleCondition`：是否显示；
- `canBuyCondition`：是否可购买；
- `condition`：同时设置可见和可购买。

限制：

- `maxPurchases(max)` / `unlimited()`；
- `purchaseResetCondition`；
- `purchaseResetByCooldown`；
- `cooldown(seconds)`、`cooldownGameDay()`、`cooldownGameTick(resetTick)`。

音效可以分别设置成功、一般失败、冷却、上限和条件不满足。不要用客户端音效结果判定事务成功。

## `ITradeOffer`

Offer 既可作为成本，也可作为奖励。内置 `ItemTradeOffer` 构造参数中的 `isCost` 必须与用途一致；成本执行扣除，奖励执行给予。

自定义 Offer 应提供：

- 服务端资格检查；
- 原子或可补偿执行；
- 显示描述/图标；
- 数量边界；
- 失败原因；
- 外部经济异常处理；
- 重复请求防护。

多成本事务不能先扣一部分再因后续成本失败而不回滚。若 Offer API 不支持事务，应先完整验证，再按可补偿顺序执行。

## Trade 运行时事件

- `TradeOpenedEvent`：成功打开，含可空 NPC；
- `TradeOpenRejectedEvent`：请求级拒绝和 error key；
- `TradePurchaseAttemptEvent`：事务前，可通过事件自己的 `cancel(reason)` 拦截；
- `TradePurchasedSuccessEvent`：成功；
- `TradePurchaseFailedEvent`：条件、冷却、上限、资金等结构化原因；
- `TradePurchaseRejectedEvent`：非法 shop/entry/request；
- `TradeStateSyncedEvent`：状态包 sent/dropped；
- `TradeClosedEvent`：可能客户端发布，player 可空，检查 `clientSide`。

取消 Attempt 只阻止本次事务，不应直接修改客户端缓存伪造失败。

## Gacha Definition

`GachaShopBuilder` 当前只有 `buildAndRegister()`，它会注册底层 Trade Shop 和 Gacha Definition。应在 `ArcQuestRegistrationEvent.Gacha` 窗口调用一次，不要把返回值再次 `event.register`，否则重复注册。

### Shop 设置

- `create(id, displayName/TradeText)`；
- `description`、`category`、`simpleMode`、`themeColor`；
- `openCondition`、`drawCondition`；
- `openSound/closeSound`；
- `drawCost(ITradeOffer)`，可添加多个成本；
- `drawCostIcon`；
- 抽取失败、冷却、上限、条件音效。

### 次数和重置

- `maxDraws(-1)` 表示无限；
- `cooldown` / `cooldownGameDay` / `cooldownGameTick`；
- `resetCondition`；
- `drawResetByCooldown`；
- `resetOnLimitReached`；
- `resetPityOnEarlyTrigger`。

重置抽取次数和重置保底不是同一件事，必须显式选择。

## Gacha pool item

`addItem` 的核心字段：

- `itemId`：池内稳定唯一 ID；
- `rewardStack`：客户端预览锚点；
- `ITradeOffer reward`：实际服务端奖励；
- `baseWeight`：正权重；
- `GachaItem.Rarity`；
- `countsTowardsPity`；
- `minCount/maxCount`；
- 可见条件、图标、主题色、音效、排序。

简化 overload 会自动创建 item reward Offer。非物品奖励仍要提供合理 `rewardStack` 作为 UI 展示，不代表真实奖励只是该 ItemStack。

动态 `WeightModifier` 用 `ICondition + delta`。有效权重会 clamp 到非负。所有可见项有效权重为 0 时必须走明确失败，不可除零或无限重抽。

## Pity

简化入口：

- `pitySystem(threshold, guaranteedRarity, resetOnTrigger)`；
- `pitySystem(threshold, guaranteedItemId, resetOnTrigger)`。

按 itemId 保底时 ID 必须存在于同一池；按 rarity 时至少有一个当前可见、有效权重的候选。阈值、计数和重置必须服务端持久化。

提前抽中保底稀有度是否重置由 `resetPityOnEarlyTrigger` 决定。文案要与真实配置一致。

## Gacha 事件

- `GachaEvents.PreDrawEvent`：抽取前，可取消；
- `PostDrawEvent`：结果、是否保底、新 pity；
- `PoolRefreshEvent`：动态权重快照变化；
- `OpenedEvent`、`DrawingEvent`、`ClosedEvent`；
- `DrawFailedEvent`：条件/冷却/次数/成本等；
- `PityEarlyTriggerEvent`；
- `DrawLimitResetEvent`；
- `StateSyncedEvent`。

只有明确暴露取消字段的 Pre 事件能阻止抽取。Post/Failed 是结果通知。

## Dialogue 集成

`ChoiceBuilder#openTrade`、`openSimpleTrade`、`openGacha` 会配置恢复到当前 Dialogue node。关闭 Shop 后会话继续，不需要附属重建 DialogueScreen。

直接从 gameplay event 打开 Shop 时应调用服务端 opener/service，而不是客户端 `setScreen`。这样 openCondition、距离/NPC、Registry 和同步仍会校验。

## 安全与事务

- 重新验证 player、shopId、entryId、NPC 和维度；
- 限制请求频率和 pending draw 数量；
- 成本和奖励只执行一次；
- 断线时清理 pending session；
- 外部经济 Offer 使用事务 ID 或附属幂等记录；
- 不把完整 NBT 或无限历史写进网络包；
- Gacha 历史和池项数量有上限；
- 失败日志限频，保留 player UUID 和 shop/entry/item ID。
