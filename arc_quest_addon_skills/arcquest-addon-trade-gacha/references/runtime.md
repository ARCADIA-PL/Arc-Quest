# Trade/Gacha 运行时与安全模式

## 打开方式

对话场景使用正式 Builder 动作：

- `ChoiceBuilder.openTrade(shopId)`；
- `ChoiceBuilder.openSimpleTrade(shopId)`；
- `ChoiceBuilder.openGacha(shopId)`。

ArcQ 当前没有为任意服务端业务公开统一的 Trade/Gacha opener facade。不要把 `C2SRequestTradePacket`、`C2SGachaControlPacket` 的 handler 当通用 API；它们是网络边界。需要非对话打开时，优先在附属中封装受控入口并基于当前公开 runtime 评估，或向 ArcQ 增加正式 facade，而不是调用 handler。

## 购买事务

一次 Trade 应依次完成：

1. 服务端定位 shop 和 entry；
2. 校验 Screen 类型、open condition、visible/canBuy condition；
3. 校验 cooldown 和购买次数；
4. 校验所有成本；
5. 准备 `TradeMutation`；
6. 扣除成本并执行奖励；
7. 更新次数/冷却、持久化、同步；
8. 发布结果事件。

自定义 Offer 不应在 `canAfford` 修改状态。多个 Offer 的事务需要可逆 mutation；命令或外部系统副作用往往不可逆，应放在不会被后续失败回滚的位置。

## 事件

Trade 常用事件：Opened、OpenRejected、PurchaseAttempt、PurchasedSuccess、PurchaseFailed、PurchaseRejected、StateSynced、Closed。

`TradePurchaseAttemptEvent` 提供自身 `cancel(reason)` / `setCancelled`，用于服务端购买前拒绝。失败事件用于观察原因，不要在失败后重复扣款或发奖励。

Gacha 常用事件：PreDraw、PostDraw、PoolRefresh、Opened、Drawing、DrawFailed、PityEarlyTrigger、DrawLimitReset、StateSynced、Closed。只在事件类明确提供取消/修改 API 时使用；不能假设所有 Forge `Event` 都可取消。

## 状态与持久化

Trade 次数/冷却和 Gacha 次数/保底/历史位于 `ArcQuestPlayer` 的专用 store。正常购买/抽取流程负责更新、持久化和同步。直接改 store 只适合维护工具，且必须补齐一致性，不应作为日常业务入口。

## 安全检查

- shopId/entryId 必须存在且长度有界；
- 玩家必须仍在线且位于正确会话；
- 所有数量、权重、次数和字符串设上限；
- 成本与奖励在执行前后均记录可诊断上下文；
- 命令 Offer 不拼接未经转义的客户端文本；
- 断线和 Screen 关闭不能留下未提交事务。

## 最低验证

- 足够/不足成本、背包已满、条件隐藏与仅不可购买。
- 达到上限、冷却重置、游戏日重置和断线重连。
- Gacha 权重边界、保底指定项、保底稀有度、提前触发与无限次数。
- 同一请求重复到达时不重复奖励。
- 专用服务器环境和客户端显示状态一致。
