# Arc Quest ArcMutil HUD 迁移实施蓝图

## 1. 目标

本蓝图用于指导 `Arc Quest` 在项目内实现一套受 `tetra + mutil` 启发的自有 HUD/UI 基础设施：`ArcMutil`，并基于它全量复刻现有 `client/hud/` 体系，待复刻验证完成后整体替换旧 HUD。

目标不是局部优化，而是完成一次 HUD 架构升级：

1. 建立 retained-mode 的统一 HUD/UI 基座
2. 将 overlay / tracker / toast / marker / journal / dialogue / shop / gacha 等统一到同一套基座
3. 把状态更新与渲染彻底分离
4. 让现有 HUD 在保真前提下获得 `tetra` 级别的性能路线

---

## 2. 当前基线

当前 Arc Quest HUD 主要分布在：

- 总入口：`client/hud/QuestHudOverlay.java`
- Quest Tracker：`client/hud/quest/tracker/*`
- Quest Toast：`client/hud/quest/toast/*`
- Quest Journal：`client/hud/quest/journal/*`
- Quest History / Story / Offer / Intel：`client/hud/quest/*`
- Dialogue：`client/hud/dialogue/*`
- Shop：`client/hud/shop/*`
- Gacha：`client/hud/gacha/*`
- Marker：`client/hud/questmarker/*`
- Splash：`client/hud/quest/splash/*`

当前主要问题：

1. render 中混入大量状态整理、布局计算、动画推进
2. 多个系统各自维护 uiScale/scissor/tooltip/slide 动画
3. 缺少统一元素树抽象，控件复用与性能治理困难
4. 复杂 HUD 越做越难统一优化

---

## 3. 向 tetra + mutil 学什么

需要学习的不是 API 表面，而是这套架构原则：

1. **长期存活的 GUI 元素树**，而不是每帧重建 UI
2. **tick/update 更新状态，render 只消费状态**
3. **动画只驱动少量字段**：`x/y/opacity/width/height`
4. **布局 lazy/dirtied**，不是每帧重排
5. **复杂 overlay 只在激活时刷新内容**
6. **HUD 由廉价 primitives 组成**：rect/texture/text/item
7. **大型 Screen 与 HUD 共用同一套基座**

ArcMutil 的目标是：在 Arc Quest 中重建这套思路，而不是直接拷贝 `mutil`。

---

## 4. 总体路线

### 核心迁移约束

本项目的 HUD 迁移必须严格遵守以下约束：

1. **旧 HUD 在迁移完成前不得破坏现有行为**
2. **ArcMutil 版 HUD 必须以“复刻”而不是“重设计”为目标**
3. **ArcMutil 版 HUD 的外观、功能、交互、状态切换、动画节奏、屏蔽关系、tooltip 表现必须尽可能与现有 HUD 完全一致**
4. **必须提供专用调试指令集/调试入口，用于快捷打开 ArcMutil 复刻版 HUD 进行对照测试**
5. **在全量复刻验证完成前，新旧 HUD 必须允许并行存在与切换**
6. **最终替换旧 HUD 的前提是：ArcMutil 版 HUD 已完成逐项对照验证**

### 四层路线
1. **ArcMutil 基础层**：做 GUI/HUD 核心基座
2. **ViewModel 适配层**：把现有业务状态整理成 HUD 可消费模型
3. **复刻层**：用 ArcMutil 完整复刻现有 HUD
4. **替换层**：完成总入口切换，退役旧 HUD

### 三阶段切换
1. **并存阶段**：旧 HUD 正常运行，ArcMutil 版并行验证
2. **镜像阶段**：同一功能新旧对照，做视觉/行为回归
3. **接管阶段**：新 HUD 全量接管，旧 HUD 下线

---

## 5. ArcMutil 包结构蓝图

建议新增包：

- `src/main/java/org/arcadia/arc_quest/client/arcmutil/`

建议子包：

### core
- `ArcGuiElement`
- `ArcGuiRoot`
- `ArcHudRoot`
- `ArcGuiContext`
- `ArcGuiAttachment`
- `ArcGuiAlignment`

### primitive
- `ArcGuiRect`
- `ArcGuiTexture`
- `ArcGuiText`
- `ArcGuiTextSmall`
- `ArcGuiItem`

### layout
- `ArcClipRect`
- `ArcVerticalLayoutGroup`
- `ArcHorizontalLayoutGroup`
- `ArcScrollableX`
- `ArcScrollableY`

### input
- `ArcGuiClickable`
- `ArcTooltipProvider`
- `ArcInputRouter`

### animation
- `ArcAnimation`
- `ArcKeyframeAnimation`
- `ArcAnimationChain`
- `ArcApplier`
- `ArcVisibilityFilter`
- `ArcAnimClock`

### overlay
- `ArcOverlayRoot`
- `ArcOverlayHost`
- `ArcOverlayRegistry`

### screen
- `ArcScreenRoot`
- `ArcPanelRoot`
- `ArcScissorUtil`
- `ArcScaleResolver`

### theme
- `ArcTheme`
- `ArcColorPalette`
- `ArcTextureRefs`

---

## 6. ArcMutil 核心类职责

## 6.1 ArcGuiElement

作为全部 HUD/Screen 元素共同父类，负责：

- 坐标与尺寸
- anchor/attachment
- opacity / visible / focus
- child 树管理
- draw/update/input 递归
- tooltip 聚合
- animation 挂载

关键原则：
- 元素长期存在
- 字段可变，结构尽量少变
- draw 只画，不做重业务逻辑

## 6.2 ArcGuiRoot

服务 2D HUD / Screen 根节点，负责：

- 获取窗口尺寸
- 计算 GUI 缩放后的鼠标坐标
- 创建顶层 `ArcGuiContext`
- 驱动整棵树 update/draw

## 6.3 ArcHudRoot

服务 overlay HUD，负责：

- 统一顶层锚点与定位
- 支持屏幕 HUD 与可选世界 HUD
- 作为所有 overlay 的标准根节点

## 6.4 ArcVisibilityFilter

服务短时 HUD 的显隐稳定，职责：

- 区间显隐过滤
- 延迟衰减
- 平滑 opacity 输出
- 抑制 HUD 抖动与高频显隐切换

---

## 7. Arc Quest HUD 重构地图

## 7.1 Overlay HUD

迁移对象：
- `QuestHudOverlay`
- `QuestSplashOverlay`
- `GachaResultOverlay`
- `MarkerHudRenderer`

目标：统一到 `ArcOverlayHost + ArcHudRoot`。

## 7.2 Quest Tracker / Toast / Marker

迁移对象：
- `quest/tracker/*`
- `quest/toast/*`
- `questmarker/*`

目标：
- tracker 变成 retained element tree
- toast 变成 slot host
- marker 变成统一 view-model + render layer

## 7.3 Screen 体系

迁移对象：
- `quest/journal/*`
- `dialogue/*`
- `shop/*`
- `gacha/*`
- `quest/history/*`
- `quest/story/*`
- `quest/offer/*`
- `quest/ponder/*`

目标：统一到 `ArcScreenRoot + ArcPanelRoot + ArcClipRect + ArcScrollable*`。

## 7.4 共享工具

旧工具：
- `HudAnimUtil`
- `HudRenderUtil`
- `HudMarkerUtil`
- questmarker 下各 util

目标：拆到 ArcMutil 下：
- `ArcAnimClock`
- `ArcDrawUtil`
- `ArcProjectionUtil`
- `ArcScaleResolver`
- `ArcScissorUtil`

---

## 8. 关键分层：ViewModel / Presenter

若只换 UI API，不改变分层，性能收益会有限。

每个 HUD 模块建议拆成三层：

1. **Data Source**
   - `ClientQuestCache`
   - 其它 client cache / runtime service

2. **ViewModel / Presenter**
   - 负责把复杂状态整理成 UI 可消费模型
   - 只在 tick 或 invalidation 时更新

3. **ArcMutil Elements**
   - 只消费 view model
   - 不直接承担复杂业务推理

### 重点对象

#### Tracker
当前 `QuestTrackerPanel` 同时承担：
- tracked quest resolve
- active phase ordering
- dismiss/transition 状态机
- panel height calculation
- render

目标拆分：
- `TrackerStateResolver`
- `TrackerViewModel`
- `ArcTrackerRoot`
- `ArcTrackerTitleElement`
- `ArcTrackerObjectiveList`
- `ArcTrackerCollectionView`

#### Toast
当前 `QuestToastManager` 同时承担：
- queue
- dedupe
- freeze
- slot placement
- render

目标拆分：
- `ToastQueueService`
- `ToastViewModel`
- `ArcToastHost`
- `ArcToastSlot`
- `ArcQuestToastElement`

---

## 9. 迁移阶段计划

## 阶段 A：ArcMutil 最小基座

实现：
- `ArcGuiElement`
- `ArcGuiRoot`
- `ArcHudRoot`
- `ArcGuiRect`
- `ArcGuiTexture`
- `ArcGuiText`
- `ArcGuiItem`
- `ArcClipRect`
- `ArcVerticalLayoutGroup`
- `ArcHorizontalLayoutGroup`
- `ArcKeyframeAnimation`
- `ArcAnimationChain`
- `ArcApplier`
- `ArcVisibilityFilter`

验收：
- 能独立跑一个 ArcMutil overlay demo
- 能做淡入/滑入/进度条变化
- 能做 scissor / tooltip / scroll 最小链路

## 阶段 B：Overlay Host 与 Toast Host

实现：
- `ArcOverlayRegistry`
- `ArcOverlayHost`
- `ArcToastHost`

首批迁移：
- `QuestToastManager`
- `QuestHudOverlay` 外层 host
- `QuestSplashOverlay`
- `GachaResultOverlay`

验收：
- 新旧 overlay 可并存
- 叠层关系不破坏

## 阶段 C：Tracker 全量复刻

迁移对象：
- `QuestTrackerPanel`
- `TrackerTitleWidget`
- `TrackerObjectiveWidget`
- `TrackerParallelWidget`
- `TrackerCollectionWidget`

重点：
- 把 render 内的 phase/order/height/transition 逐步迁到 update/view-model
- 用 retained 元素实现同样视觉

验收：
- progression / collection 模式完整一致
- 可替换旧 tracker

## 阶段 D：Toast / Splash / Marker 迁移

迁移对象：
- `QuestNotificationToast`
- `PhaseUpdateToast`
- `BranchChoiceToast`
- `QuestSplashRenderer`
- `MarkerHudRenderer`
- `MarkerPointerRenderer`
- `MarkerProjection`

验收：
- toast push-down 与 tracker 联动正确
- marker 表现与旧版一致
- splash 仍可最高优先级屏蔽其它 overlay

## 阶段 E：Journal Screen 基座迁移

迁移对象：
- `QuestJournalScreen`
- `JournalTabPanel`
- `JournalListPanel`
- `JournalDetailPanel`
- 所有 `JournalDetail*`
- `QuestHistoryPanel`
- `CollectionHistoryPanel`
- `QuestOfferPanel`
- `QuestIntelPanel`
- `QuestStoryPanel`

重点：
- 统一 uiScale / scissor / mouse transform
- 用 `ArcClipRect + ArcScrollable*` 重建列表与详情区
- tooltip 聚合改为统一 provider

验收：
- active/completed/failed/history/story/offer/intel 全部对齐

## 阶段 F：Dialogue / Shop / Gacha Screen 迁移

目标：让全部 Screen 统一到 `ArcScreenRoot`。

## 阶段 G：旧 HUD 退役

步骤：
1. 保留旧实现一段时间作为回滚
2. 提供配置或 debug 开关切换
3. 新实现稳定后删除旧 HUD
4. 下线旧工具类

---

## 9.5 调试指令集与复刻对照测试要求

为了保证 ArcMutil 版 HUD 不是“新做一个差不多的版本”，而是真正可对照验证的复刻实现，必须建设一套专用调试指令集与调试模式。

### 调试目标

1. 快捷打开 ArcMutil 版 HUD，而不依赖复杂业务触发条件
2. 能独立打开旧 HUD 与 ArcMutil 版 HUD 进行人工对比
3. 能在统一输入数据下观察两套 HUD 的外观、行为、动画、tooltip、滚动、按钮交互是否一致
4. 能强制注入测试数据/测试场景，降低回归测试成本

### 指令集建设原则

建议新增独立调试指令根，例如：

- `/aqhud debug ...`
- `/aqhud legacy ...`
- `/aqhud arc ...`
- `/aqhud compare ...`

也可以拆为更清晰的命令族，例如：

- `/aqhud_open_legacy_tracker`
- `/aqhud_open_arc_tracker`
- `/aqhud_open_legacy_journal`
- `/aqhud_open_arc_journal`

但更推荐统一根命令，便于维护。

### 首批必须支持的调试能力

#### Overlay 类
- 打开/关闭旧版 tracker
- 打开/关闭 ArcMutil 版 tracker
- 触发旧版 toast
- 触发 ArcMutil 版 toast
- 触发旧版 splash
- 触发 ArcMutil 版 splash
- 打开/关闭旧版 marker 调试层
- 打开/关闭 ArcMutil 版 marker 调试层

#### Screen 类
- 打开旧版 journal
- 打开 ArcMutil 版 journal
- 打开旧版 history/story/offer/intel 面板
- 打开 ArcMutil 版对应面板
- 打开旧版 dialogue/shop/gacha screen
- 打开 ArcMutil 版对应 screen

#### 数据注入类
- 注入假 quest tracker 数据
- 注入假 collection tracker 数据
- 注入假 toast 队列
- 注入假 marker 数据
- 注入假 journal list/detail 数据
- 切换 active/completed/failed/collection 等典型测试态

### 推荐调试模式

#### 模式 A：独立打开
只打开 ArcMutil 版 HUD，验证其单独表现。

#### 模式 B：旧版独立打开
只打开旧 HUD，作为参考基线。

#### 模式 C：对照模式
通过偏移、分屏、轮换、开关热切换等方式，在统一数据下对比新旧 HUD。

建议至少支持：
- 同类 HUD 的旧/新二选一切换
- 单键在旧/新实现间切换
- 输出当前实现来源（legacy / arc）用于截图和录屏比对

### 验收要求

任何一个 HUD 子系统在宣布“复刻完成”前，必须满足：

1. 存在对应的 ArcMutil 调试打开方式
2. 能在统一测试数据下与旧 HUD 做人工对照
3. 外观、功能、交互、行为、逻辑与旧 HUD 无明显差异
4. 回归测试至少覆盖：
   - 初始打开
   - 状态切换
   - 动画进出
   - tooltip/hover
   - 特殊模式（如 collection / parallel / failed / completed）

---

## 10. 迁移阶段计划

ArcMutil 迁移过程中必须遵守以下规则：

### render 阶段禁止事项
1. 不在 render 中组装大列表对象
2. 不在 render 中做复杂状态排序/统计
3. 不在 render 中重建 child tree
4. 不在 render 中重复计算布局
5. 不在 render 中创建大量临时集合

### 推荐做法
1. tick/update 阶段更新 ViewModel
2. layout / summary / visible rows / tooltip 采用 dirty flag
3. element 长期持有，只更新字段
4. 复杂 overlay 只在激活时刷新内容
5. 动画只改变少量属性

---

## 11. 风险与规避

### 风险 1：直接原地改旧 HUD，回滚困难
规避：
- 新建 `client/arcmutil/`
- 新 HUD 先平行实现，不直接替换旧目录

### 风险 2：ArcMutil API 设计过早僵化
规避：
- 先覆盖最小 primitive
- 先用 Tracker 验证 API
- 再推进大 Screen

### 风险 3：迁移后短期性能不升反降
规避：
- 强制 update/render 分离
- 强制 dirty flag 机制
- 每阶段只迁一个大系统并验证

### 风险 4：视觉保真下降
规避：
- 先复刻，不改美术风格
- 动画参数、颜色参数尽量保留原值

---

## 12. 推荐开发顺序

### Sprint 1
ArcMutil 最小基座

### Sprint 2
Overlay Host + Toast Host

### Sprint 3
Tracker 全量复刻

### Sprint 4
Marker / Splash / Gacha Overlay 迁移

### Sprint 5
Journal Screen 基座迁移

### Sprint 6
Journal 全量复刻

### Sprint 7
Dialogue / Shop / Gacha Screen 统一迁移

### Sprint 8
旧 HUD 退役与清理

---

## 13. 首版验收标准

### 架构层
- ArcMutil 可同时支撑 overlay 与 screen
- retained-mode 元素树稳定运行
- animation / scissor / tooltip / focus / scroll 全可用

### HUD 层
- Tracker / Toast / Marker / Splash / Gacha Overlay 已迁移
- Journal 已迁移
- 行为与旧版一致

### 性能层
- 常驻 HUD 不再在 render 阶段做大规模状态整理
- 主要 HUD 面板改为长期持有 element 树
- layout / visible rows / summary / tooltip 存在 dirty 缓存策略

### 兼容层
- 原功能不丢
- 原快捷键、交互、叠层关系不变
- 在最终退役前可回退旧实现

---

## 14. 最终原则

1. 先造基座，再迁功能
2. 先迁最能体现 retained-mode 收益的 HUD：Tracker / Toast / Overlay
3. Screen 迁移优先统一 scissor / uiScale / tooltip / scroll
4. 把业务状态整理从 render 中剥离出去
5. 最终让 `Arc Quest` 获得一套可长期演进的自有 HUD 框架：`ArcMutil`

最终目标不是“代码像 tetra”，而是：

- 继承 `tetra + mutil` 的高性能原则
- 保留 `Arc Quest` 自己的功能与风格
- 完成一次可持续维护的 HUD 架构升级
