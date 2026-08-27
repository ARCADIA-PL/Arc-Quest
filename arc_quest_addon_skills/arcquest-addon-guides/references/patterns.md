# Guide 代码模式

## 首次进入发放教程

监听服务端玩家登录或附属自己的首次初始化事件，以附属 flag 做幂等保护，再调用 `GuideUnlockService.grant`。不要只依赖客户端本地状态，否则切换存档或重启会重复。

## 达到业务里程碑后静默发放

Definition 使用 `.unlockPopup(false)`，事件发生时调用 `grant`。静默只控制弹窗，Guide 仍进入列表并产生未读状态。

## 当前有 Screen 时延迟教程

Definition 使用 `.unlockPopup(true, false)`。客户端将请求保留到可以安全打开时；附属不需要每 tick 重发，也不应自行保存 `Screen` 强引用。

## 任务阶段教学

优先将 `grantGuideOnEnter` / `grantGuideOnComplete` 写进 Phase Definition，这样手动完成、调试强制完成和正常 Objective 推进都共享同一流程。

## 常见错误

- 解锁后手工再次发布 `GuideEvents.Unlocked`。
- 将 `unlockPopup(false)` 误解为“不解锁”。
- 把 `GuideCompletedEvent` 当成逐页阅读证明。
- 在客户端直接发奖励响应 Guide 阅读。
- 引用不存在的图片、Ponder scene 或其他模组资源。
- 在登录事件每次无条件 `clear` 再 `grant`，导致重复发放。

## 最低验证

- 首次解锁、重复 grant、重启客户端和重进同一存档。
- `unlockPopup(false)`、`(true, false)`、`(true, true)`。
- 当前存在 Dialogue/Shop/其他 Screen 时的延迟和返回行为。
- 页码边界、最后一页、关闭标记 seen、批量全部已读。
- 专用服务器环境无客户端类加载。
