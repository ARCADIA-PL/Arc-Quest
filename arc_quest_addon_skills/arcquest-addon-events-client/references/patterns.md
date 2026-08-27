# 事件订阅与副作用模式

## 观察与修改分离

结果事件用于观察和触发附属后续流程；只有事件明确公开取消/修改方法时才改变 ArcQ 决策。不要通过反射修改 private 字段，也不要把所有 `Event` 当成可取消。

## 服务端奖励监听

Quest/Guide/Trade/Gacha 奖励只在含 `ServerPlayer` 的服务端成功事件中执行，并用附属 flag 或自身持久状态保证幂等。客户端 Closed/Tracked 事件不能发权威奖励。

## Side 检查

`TrackedQuestChangedEvent` 明确可能两侧发布：

```text
if (!event.isServerSide() || !event.isAuthoritative()) return;
```

`TradeClosedEvent` 通过 `isClientSide()` 区分。其他事件若字段就是 `ServerPlayer`，通常已经是服务端事件，但仍不要从 client-only 订阅类处理。

## 取消原因

`DialogueStartingEvent` 和 `TradePurchaseAttemptEvent` 支持 reason。使用稳定、可诊断的短字符串或现有错误语义，不把玩家输入、隐私信息或大段堆栈放入 reason。

## 高频事件

Objective Progress、Dialogue node、Marker 刷新和客户端 render 都可能频繁触发。监听器按 namespace/ID 先过滤，禁止磁盘 I/O、网络等待、全世界扫描和无界日志。

## Registry 一次性注册

客户端扩展 Registry 没有通用 unregister。推荐在 client setup 的 `enqueueWork` 或附属一次性初始化执行。开发环境重复初始化时用附属 `AtomicBoolean` 或生命周期结构避免重复，但不要把它扩展成跨服务器业务状态。

## 常见错误

- MOD 总线和 Forge 总线订阅反了。
- 在 `Prepared` 重载线程访问 Level/Entity。
- 在结果事件里尝试取消已完成事务。
- 未检查 `TrackedQuestChangedEvent` side，奖励触发两次。
- Overlay 修改深度/混合状态后不恢复。
- Screen 每次打开重复注册 resolver。

## 最低验证

- 每个监听器确认实际总线与侧。
- 成功、拒绝、取消和异常路径各触发一次。
- 同一事件不因客户端同步重复产生服务端副作用。
- 资源重载期间无跨线程世界访问。
- 客户端扩展在专用服务器不参与类加载。
