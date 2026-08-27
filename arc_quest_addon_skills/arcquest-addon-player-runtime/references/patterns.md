# 玩家状态安全修改模式

## 业务 flag

在服务端事件中获取 `ArcQuestPlayer`，只在值真正变化时设置 flag。监听 `PlayerProfileEvents.FlagChanged` 的代码按 namespace 快速过滤，避免事件重入再次设置同一 flag。

## 外部状态与 ArcQ 条件

将复杂外部状态映射成少量 ArcQ flag/variable：外部系统变化时更新一次，Quest/Guide/Trade 条件只读取 ArcQ 状态。这样避免条件热路径访问远程服务或扫描大对象图。

## 玩家克隆与重生

ArcQ 自己处理 `ArcQuestPlayerManager.clone(from, to)`。附属不要在 Forge clone 事件里再次复制 ArcQ 全量 NBT，否则可能覆盖已完成的 ArcQ 生命周期处理。附属只复制自己的状态。

## 异步结果

异步工作只携带 UUID 和不可变纯数据。结果返回时通过服务器线程重新查找在线玩家，再获取最新 `ArcQuestPlayer` 并校验业务 revision；不要在线程间持有 `ServerPlayer` 或 `ArcQuestPlayer`。

## 常见错误

- 客户端修改 flag/variable 并期望服务端接受。
- 直接修改 active Quest map。
- 手工调用 `clearDirty` 但没有持久化。
- 缓存玩家状态跨存档。
- 服务端监听客户端非权威 `TrackedQuestChangedEvent` 发奖励。
- 将大段 JSON/NBT 编码进 variable 或 flag 名称。

## 最低验证

- 玩家登录、退出、死亡重生、服务器重启、同客户端切换存档。
- flag/variable 事件只发布一次且重启仍存在。
- 追踪、取消追踪、任务终止自动切换和并行 Phase 聚焦。
- 异步回调期间玩家离线。
