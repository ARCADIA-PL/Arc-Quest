# Guide 需求实现配方

## 需求：注册有目录结构的教程集

### ArcQ 实现路径

```text
ArcQuestRegistrationEvent.Guide
  -> registerGroup(group)
  -> register(guide definitions)
  -> assignGuideToGroup(guideId, groupId)
```

Category 表示一级用途，Group 用于附属内部章节或系统归类。ID 和排序值是结构契约，显示文本使用翻译键。

完整代码：[ExampleGuideContent.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ExampleGuideContent.java)。

## 需求：任务进入阶段时自动发放 Guide

优先把关系写进 Phase Definition：

- `grantGuideOnEnter(guideId)`：阶段首次激活时发放；
- `grantGuideOnComplete(guideId)`：阶段完成时发放。

这样正常推进、管理员强制完成和补偿流程共用同一生命周期。只有解锁条件跨多个模块、无法归属于单个 Phase 时，才监听事件并调用 `GuideUnlockService`。

并行任务示例：[ScenarioParallelQuestContent.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioParallelQuestContent.java)。

## 需求：静默解锁、立即弹窗和延迟弹窗

| 配置 | 行为 |
| --- | --- |
| `unlockPopup(false)` | 只写入 Guide 列表，不自动打开 |
| `unlockPopup(true)` | 使用默认弹窗策略 |
| `unlockPopup(true, true)` | 当前有 Screen 时也允许强制叠加 |
| `unlockPopup(true, false)` | 当前有 Screen 时延迟，待空闲再打开 |

剧情对话或商店期间通常选择 `true, false`，避免覆盖当前会话。任务快捷操作提示但不希望打断玩家时选择 `false`。

## 需求：批量发放并自动去重

集中服务先规范化 ID，再调用 `GuideUnlockService#grantAll`。`grantAll` 返回实际首次解锁数量，可用于日志或成就；不要把输入数量当成新增数量。

完整代码：[ScenarioGuideRewardHandler.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioGuideRewardHandler.java) 中的 `grantDistinct`。

## 需求：玩家首次看到 Guide 时奖励

监听 `GuideEvents.Seen` 或兼容 `GuideCompletedEvent`。该语义是首次标记已读，不证明玩家读到末页。

适合：低价值“发现知识”奖励、红点消除成就。必须在服务端执行，并用附属 flag 或 claimed set 去重，不能只依赖客户端弹窗。

## 需求：玩家到达最终页才奖励

### ArcQ 实现路径

```text
GuideEvents.ProgressChanged
  -> 匹配 guideId
  -> hasReachedFinalPage()
  -> 检查附属 claimed flag
  -> 先写 claimed
  -> 发放奖励
```

完整实现：[ScenarioGuideRewardHandler.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioGuideRewardHandler.java)。

### 为什么先写 claimed

在同一服务端主线程上先写去重标记，再调用外部经济或经验奖励，可缩小重复事件窗口。若外部事务失败后需要重试，使用独立 transaction 状态和有限重试，不要简单删除 flag 导致无限重复发放。

## 需求：首次登录或条件满足时补发 Guide

```text
PlayerLoggedInEvent
  -> ServerPlayer
  -> ArcQuestPlayer 已加载
  -> 检查 flag/quest/variable 条件
  -> GuideUnlockService.grant
```

`grant` 本身幂等，只在首次解锁时同步和发布 `Unlocked`。条件检查仍应有明确业务边界，不要每次登录扫描所有 Guide Definition；按受影响 Guide 建索引或显式列表。

## 需求：Guide 页面显示按键、图片和 Ponder

- 按键名称用 `Component`/翻译键和当前 key mapping 的显示文本，不硬编码默认键；
- 图片资源必须存在于客户端资源包，定义只保存资源 ID；
- Ponder scene 必须已注册且客户端依赖存在；
- 页面文本拆成适合当前 UI 的自然段，不依赖 Mixin 改全局分行；
- 动态 `GuideText` 提供客户端 fallback。

## 需求：Guide 解锁后联动任务

“解锁”不等于“阅读”。若任务目标是获得指南，监听 `GuideEvents.Unlocked`；若目标是查看内容，监听 `Seen`；若目标是读到末页，监听 `ProgressChanged`。把对应事件转换成 custom Objective，不要混用。

## 验证清单

- Group 先注册，assignment ID 全部存在；
- 静默、强制弹窗和延迟弹窗符合当前 Screen 状态；
- 重复 grant 不重复事件和奖励；
- seen 与最终页语义分别验证；
- 重登后 Guide 和 claimed flag 均持久；
- 批量发放返回实际新增数；
- 图片/Ponder 缺失时有可诊断日志；
- 服务端奖励不在客户端事件重复执行。
