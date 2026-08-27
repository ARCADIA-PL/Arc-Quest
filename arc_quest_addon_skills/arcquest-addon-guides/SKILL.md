---
name: arcquest-addon-guides
description: Implement Arc Quest code-defined guides for Forge 1.20.1 using GuideBuilder, pages, media, categories, groups, unlock conditions, GuideUnlockService, progress state, and guide events. Excludes guide datapack JSON.
---

# Arc Quest Guide 附属

用于注册 Guide、分类、分组、页面媒体，按任务或业务事件解锁，并监听阅读进度。

## 必读

- [Guide 定义 API](references/api.md)
- [Guide API 深入参考](references/api-reference.md)
- [解锁、进度与事件](references/runtime.md)
- [代码模式](references/patterns.md)
- [Guide 需求实现配方](references/scenario-recipes.md)

完整源码见 [ExampleGuideContent.java](../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ExampleGuideContent.java) 和 [ScenarioGuideRewardHandler.java](../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioGuideRewardHandler.java)。

## 工作流

1. 在 `ArcQuestRegistrationEvent.Guide` 中注册 Group 和 Guide，再分配 Guide 到 Group。
2. 使用附属翻译键构造 `GuideText`；动态文本必须提供客户端可用的静态表现。
3. 页面使用 `GuidePageBuilder`，媒体仅选择实际存在的图片或 Ponder scene。
4. 自动条件可写入 Definition；业务事件即时发放使用 `GuideUnlockService`。
5. 阅读完成奖励监听 `GuideCompletedEvent` 或 `GuideEvents.Seen`，不要在客户端直接发奖励。

## 约束

- `unlockPopup(false)` 表示静默解锁；`unlockPopup(true, false)` 在已有 Screen 时延迟打开；第二参数为 true 时允许强制叠加。
- `GuideUnlockService#grant` 只在首次变化时持久化、同步并发布 `GuideEvents.Unlocked`。
- `GuideCompletedEvent` 当前在 Guide 被标记 seen 时发布，不等同于“浏览过每一页”的通用证明；结合 `GuideEvents.ProgressChanged` 判断复杂阅读要求。
