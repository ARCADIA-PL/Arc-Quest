# Guide 解锁、进度与事件

## 解锁服务

服务端使用 `GuideUnlockService`：

```text
GuideUnlockService service = new GuideUnlockService();
boolean newlyUnlocked = service.grant(player, guideId);
```

公开方法：

- `unlock(player, guideId)`：与直接 `grant` 同义。
- `grant(player, guideId)`：仅在首次写入时返回 true。
- `grantAll(player, guideIds)`：批量解锁，返回变化数量。
- `ensureUnlockedIfEligible(player, guideId)`：已解锁返回 true；否则先检查 Definition 条件，再发放。

成功首次变化时，服务会：

1. 更新 `ArcQuestPlayer` Guide 状态；
2. 发布 `GuideEvents.Unlocked`；
3. 持久化玩家快照；
4. 同步 Guide 玩家状态；
5. 清理对应 dirty 标记。

因此不要在调用 `grant` 后再手工重复持久化、发同类网络包或自行发布 `Unlocked`。

## 从 Quest 发放

`PhaseBuilder.grantGuideOnEnter(...)` 和 `grantGuideOnComplete(...)` 是最直接的 Quest 联动方式。ArcQ 正常 Phase 流程负责发放；绕过 Phase 完成流程会漏发。

对话中：

- `unlockGuide(guideId)` 只解锁；
- `openGuide(guideId)` 打开；
- `openGuide(guideId, initialPage, markSeenOnClose)` 控制初始页与关闭时已读行为。

## 主动打开

服务端正式入口是 `GuideTriggerService#open(ServerPlayer, ResourceLocation, int initialPage, boolean markSeenOnClose)`。它校验 Guide、限制页码并发送打开请求。不要从公共业务代码直接构造和处理 C2S/S2C packet。

## 已读与进度语义

客户端浏览页码会通过网络由服务端验证并发布 `GuideEvents.ProgressChanged`。标记 seen 时发布 `GuideEvents.Seen`，现有兼容事件 `GuideCompletedEvent` 也在 Guide 首次标记 seen 时发布。

`GuideCompletedEvent` 不证明玩家逐页阅读了全部内容。需要“到达末页”时，监听 `GuideEvents.ProgressChanged` 并检查 `hasReachedFinalPage()`；需要“已关闭且标记已读”时监听 Seen/Completed 语义。

`GuideEvents.MarkedAllSeen` 表示批量清除未读红点，包含本次变化的 Guide ID 集合，不代表内容被逐页阅读。

## 解锁来源

`GuideEvents.Unlocked` 携带 `UnlockSource`。直接 `grant` 使用 `DIRECT`，批量使用 `BULK`，资格检查发放使用 `ELIGIBILITY`。监听者可按来源区分分析或表现，但不能据此绕过服务端实际解锁状态。

## 线程和重复触发

所有服务调用在服务端主线程。首次变化后 `grant` 返回 true，再次发放返回 false，不再重复发布解锁事件。若 Definition 使用 `repeatablePopup()`，重复弹窗属于表现策略，不改变“解锁集合只新增一次”的事实。
