# Guide API 深入参考

## 定义模型

```text
GuideBuilder -> GuideDefinition
                 -> GuideCategory
                 -> List<GuidePageDefinition>
                       -> GuideText
                       -> GuideMediaDefinition
                 -> GuideVisualConfig
```

Guide 定义在注册期建立；解锁、seen 和当前页进度属于 `ArcQuestPlayer` 的服务端持久状态。

## `GuideBuilder`

### 基础字段

- `create(ResourceLocation/String)`：使用附属 namespace。
- `category(GuideCategory)`：决定面板分类。
- `title(...)`、`summary(...)`：支持 literal、`Component`、`GuideText`。
- `icon(Item/ItemStack)`：列表和弹窗图标。
- `sortOrder(int)`：分类内排序。
- `hidden()`：定义存在但不按普通列表规则展示。

动态 `GuideText` 和其他动态文本一样，必须为无服务端玩家的客户端环境提供 fallback。

### 弹窗语义

| 调用 | 行为 |
| --- | --- |
| `unlockPopup()` | 开启默认解锁弹窗 |
| `unlockPopup(false)` | 静默解锁，不打开 Guide Screen |
| `unlockPopup(true)` | 弹窗，使用默认屏幕叠加策略 |
| `unlockPopup(true, true)` | 当前已有 Screen 时允许强制叠加 |
| `unlockPopup(true, false)` | 当前已有 Screen 时延迟，等无 Screen 再打开 |
| `repeatablePopup()` | 已解锁后再次触发时仍允许弹窗，需谨慎防刷屏 |

`forceOpenWithScreen` 只影响客户端何时打开，不改变服务端解锁状态。静默发放问题应检查调用的是不是 `GuideUnlockService#grant`、Definition 是否真为 `unlockPopup(false)`，以及是否有另一个监听器主动打开 Screen。

### 弹窗视觉

- `renderLargeIconOnIntro(boolean)`；
- `popupBackground(ResourceLocation)`；
- `renderPopupBackground(boolean)`。

纹理只影响表现。缺失资源不应阻止服务端 Guide 解锁，但会造成客户端日志或空白背景。

### 解锁条件

- `unlockCondition(ICondition)`；
- `requiresQuest(ResourceLocation)`；
- `requiresFlag(String)`。

Definition 条件用于 eligibility。条件成立不一定自动立即发放，具体取决于调用 `ensureUnlockedIfEligible` 的触发点。需要在明确业务事件立即发放时直接使用 `GuideUnlockService#grant`。

### 页面

- `page(GuidePageBuilder/GuidePageDefinition)`；
- `imagePage(texture, description)`；
- `ponderPage(sceneId, description)`；
- `ponderQuestPhasePage(questId, phaseId, description)`。

`build()` 返回 Definition；`buildAndRegister()` 直接写 Registry。注册事件中优先 `event.register(builder.build())`。

## `GuidePageBuilder`

- `description(String/Component/GuideText)`：页面正文；
- `media(GuideMediaDefinition/GuideMediaBuilder)`：显式媒体；
- `image(texture, width, height)`：图片及设计尺寸；
- `ponder(sceneId)`：Ponder scene；
- `ponderScene(questId, phaseId)`：Quest Phase 对应场景；
- `none()`：纯文本页；
- `build()`：不可变页面。

图片尺寸是 UI 布局提示，不应使用原始超大纹理像素值。验证 GUI scale 2/3/4、小窗口和长翻译文本。

## `GuideMediaBuilder`

- `none()`、`image(ResourceLocation)`、`ponder(ResourceLocation)`；
- `size(width, height)`：正尺寸；
- `autoplay(boolean)`、`loop(boolean)`：媒体播放策略；
- `build()`。

Ponder scene 必须真实注册，且客户端具备依赖。不要从服务端 common 类直接引用客户端 Ponder renderer。

## Category 与 Group

`GuideCategory` 决定顶层分类、主题色和排序。可以复用 `GuideCategories`，也可以创建附属类别。

`GuideGroupDefinition` 提供：

```text
id
displayName
sortOrder
themeColor
```

`ArcQuestRegistrationEvent.Guide` 的顺序建议：

```text
registerGroup(group)
register(guide)
assignGuideToGroup(guideId, groupId)
```

Group 和 Category 不是同一概念：Category 是面板大类，Group 是类内组织和折叠单元。

## `GuideUnlockService`

### `grant` / `unlock`

`unlock` 委托到 `grant`。返回 `true` 表示本次首次加入 unlocked 集合；返回 `false` 表示无变化或失败。

成功链包括：

```text
检查 Guide Definition
  -> 检查/写入玩家 unlocked state
  -> 标记 dirty
  -> 持久化/同步
  -> 发布 GuideEvents.Unlocked
  -> 根据 Definition 决定静默、立即或延迟弹窗
```

不要直接 `ArcQuestPlayer#unlockGuide` 替代 Service，否则会绕过同步、事件和弹窗策略。

### `grantAll`

接受 `Collection<ResourceLocation>`，返回成功变化数量。调用前仍应去除 null 和非法业务输入；Service 会处理已解锁项。

### `ensureUnlockedIfEligible`

先读取 Definition 和玩家快照，只有条件满足时 grant。适合登录、flag/variable 变化或阶段事件后的条件型检查。不要每 tick 遍历全部 Guide。

## 玩家 Guide 状态

`ArcQuestPlayer` 公开读取：

- `isGuideUnlocked` / `getUnlockedGuides`；
- `isGuideSeen` / `getSeenGuides`；
- `getGuideProgress` / `getAllGuideProgress`。

低层写入包括 unlock/revoke/seen/progress，但业务代码优先使用 Service 和正常客户端请求链。直接低层写入可能漏事件或同步。

## seen、completed 与逐页进度

ArcQ 当前存在两类语义：

- `GuideEvents.Seen` 和兼容 `GuideCompletedEvent`：首次标为已读；
- `GuideEvents.ProgressChanged`：页面 index 变化，可用 `hasReachedFinalPage()` 判断到达末页。

不要把 `GuideCompletedEvent` 解读为“玩家认真读完每一页”。奖励条件若要求末页，应监听 ProgressChanged；若要求首次关闭/seen，则监听 Seen/Completed。无论哪种都要持久化附属自己的奖励去重 ID。

## 事件

- `GuideEvents.Unlocked`：Definition、player、来源；
- `GuideEvents.Seen`：首次 seen；
- `GuideEvents.ProgressChanged`：旧页、新页、末页判断；
- `GuideEvents.MarkedAllSeen`：批量一键已读；
- `GuideCompletedEvent`：兼容事件，随首次 seen 发布。

事件监听器运行在服务端业务链时可以发奖励，但不要反向重复标记同一 Guide，避免事件重入。

## 性能和幂等

- 不要登录时无条件重新 grant 数百 Guide；按条件索引或批量筛选。
- `repeatablePopup` 不是奖励重复许可。
- 外部奖励先检查幂等标记，再写 pending/claimed 状态。
- 批量已读事件可能携带多个 ID，处理时设置数量上限并聚合日志。
- 不在 Guide UI 关闭事件直接信任客户端发来的奖励金额或 guideId。
