# Quest HUD ArcMutil 直接迭代迁移蓝图

## 1. 新路线

本蓝图替代旧的“复刻 - 比较 - 替换”路线。Quest HUD 迁移改为：

```text
tetra 原则作为验收标准
ArcMutil 作为自研 HUD 运行时
Quest HUD 直接生产级迭代迁移
```

不再逐类复刻旧 HUD 或 tetra/mutil，而是按真实业务模块逐步接管：Tracker -> Toast -> Journal -> History/Story/Offer/Intel -> Marker。

核心验收原则：

1. 数据在 tick / packet / dirty cache 更新。
2. render 只读 ViewModel 并绘制。
3. 元素树长期复用，不每帧重建。
4. 文本、layout、item、tooltip 均走 ArcMutil 性能层。
5. 旧 HUD 保留 fallback，按 feature flag 逐模块切换。
6. 用 `ArcGuiProfiler` 验证，而不是凭感觉优化。

---

## 2. 当前 Quest HUD 基线

主要入口：

- `client/hud/QuestHudOverlay.java`
- `client/hud/quest/tracker/QuestTrackerPanel.java`
- `client/hud/quest/toast/QuestToastManager.java`
- `client/hud/quest/journal/QuestJournalScreen.java`
- `quest/network/ClientQuestCache.java`

当前问题集中在：

- `QuestHudOverlay.render` 同时做 tracked quest resolve、phase popup 检测、toast 排布、tracker render。
- `QuestTrackerPanel.render` 内读取 `ClientQuestCache`、resolve registry、推进动画、计算 layout/scissor、分流 progression/collection。
- `QuestToastManager` 已有 tick，但 render 仍为旧 immediate path，且 push-down 与 tracker 耦合。
- `ClientQuestCache` 同时负责 mirror、toast trigger、journal refresh，后续需要提供 dirty/version 入口。

迁移后要求：render 阶段不得再访问 quest cache / registry 做业务计算。

---

## 3. 推荐包结构

新增 Quest 专用 ArcMutil 迁移包：

```text
client/hud/quest/arcmutil/
  QuestArcHudController.java
  QuestHudMigrationFlags.java
  QuestHudBlockState.java
  QuestHudSelectors.java

  tracker/
    ArcQuestTrackerOverlayRoot.java
    QuestTrackerPresenter.java
    QuestTrackerViewModel.java
    QuestTrackerPhaseViewModel.java
    QuestTrackerObjectiveViewModel.java
    QuestTrackerCollectionViewModel.java

  toast/
    ArcQuestToastOverlayRoot.java
    QuestToastPresenter.java
    QuestToastViewModel.java
    QuestToastSlotViewModel.java

  journal/
    ArcQuestJournalScreen.java
    QuestJournalPresenter.java
    QuestJournalViewModel.java
    QuestJournalListViewModel.java
    QuestJournalDetailViewModel.java

  history/
    ArcQuestHistoryPanel.java
    QuestHistoryPresenter.java
    QuestHistoryViewModel.java

  marker/
    ArcQuestMarkerOverlayRoot.java
    QuestMarkerPresenter.java
    QuestMarkerViewModel.java

  common/
    QuestHudTheme.java
    QuestHudFormatters.java
    QuestHudKeys.java
```

原则：迁移代码先与旧 HUD 并存；功能稳定后再退役旧类。

---

## 4. 运行模型

目标链路：

```text
S2C packet / cache update
  -> ClientQuestCache 更新镜像
  -> Quest presenter markDirty

ClientTickEvent.END
  -> QuestArcHudController.tick
  -> Tracker/Toast/Marker presenter.tick
  -> ViewModel 更新
  -> ArcMutil root tickAndUpdateRoot

RenderGuiOverlayEvent.Post
  -> QuestArcHudController.render
  -> ArcOverlayHost.renderAll
  -> drawRootOnly
  -> flush render queue
```

render 阶段禁止：

- resolve tracked quest
- 遍历 active quest 做业务判断
- 查 `QuestRegistry`
- 生成 objective / collection 列表
- 推进 phase transition 状态机
- 创建 toast 对象
- wrap 文本
- 重建 child tree

render 阶段允许：

- 读取 ViewModel 字段
- draw primitives/text/item/tooltip
- focus/hover 计算
- render queue flush

---

## 5. 核心类职责

### 5.1 `QuestHudMigrationFlags`

短期 static flag，长期可接 client config / debug command。

建议字段：

```java
public static boolean ARC_TRACKER_ENABLED = false;
public static boolean ARC_TOAST_ENABLED = false;
public static boolean ARC_JOURNAL_ENABLED = false;
public static boolean ARC_HISTORY_ENABLED = false;
public static boolean ARC_MARKER_ENABLED = false;
public static boolean ARC_DEBUG_OVERLAY = false;
```

### 5.2 `QuestArcHudController`

Quest ArcMutil HUD 总入口。

职责：

- 持有 tracker/toast/marker roots 与 presenters。
- 对外暴露 `setTrackedQuest` / `setTrackedFocus`。
- 根据 feature flags 控制新旧 HUD 切换。
- 在 tick 中更新 presenters。
- 在 render 中只调用 ArcMutil render-only。

建议 API：

```java
public void tick();
public void render(GuiGraphics graphics, float partialTick);
public void setTrackedQuest(String questId);
public void setTrackedFocus(String questId, String phaseId);
public String getTrackedQuestId();
public String getTrackedPhaseId();
public void setEnabled(boolean enabled);
public boolean isEnabled();
```

### 5.3 `QuestHudBlockState`

统一屏蔽/冻结判断，替代散落逻辑。

输入来源：

- `Minecraft.options.hideGui`
- `Minecraft.screen`
- `QuestSplashRenderer.isActive()`
- `GachaResultRenderer.INSTANCE.isActive()`
- `QuestJournalScreen`
- `DialogueScreen`
- `AbstractTradeScreen`

建议结构：

```java
public record QuestHudBlockState(
    boolean hideAll,
    boolean freezeToasts,
    boolean hideTracker,
    boolean hideNonBlockingToasts,
    boolean blockInput
) {}
```

### 5.4 `QuestHudSelectors`

纯选择工具，集中处理 tracked quest resolve。

应从旧代码抽离：

- `QuestHudOverlay.resolveTrackedQuest`
- `QuestTrackerPanel.resolveTrackedQuest`

输出建议：

```java
record ResolvedTrackedQuest(
    String requestedQuestId,
    QuestRuntimeData data,
    boolean requestedInvalidated
) {}
```

---

## 6. A13：Quest Tracker 首个生产级迁移

### 6.1 为什么先迁移 Tracker

Tracker 最适合验证 ArcMutil 生产链路：

- 是 overlay，天然适配 tick/render 分离。
- 长期显示，FPS 敏感。
- 覆盖 progression 与 collection。
- 能验证 phase transition、toast push-down、blocking screen、text cache、scissor stack。

### 6.2 目标文件

新增：

```text
client/hud/quest/arcmutil/tracker/QuestTrackerPresenter.java
client/hud/quest/arcmutil/tracker/QuestTrackerViewModel.java
client/hud/quest/arcmutil/tracker/QuestTrackerPhaseViewModel.java
client/hud/quest/arcmutil/tracker/QuestTrackerObjectiveViewModel.java
client/hud/quest/arcmutil/tracker/QuestTrackerCollectionViewModel.java
client/hud/quest/arcmutil/tracker/ArcQuestTrackerOverlayRoot.java
```

保留：

- `QuestTrackerPanel` 作为 fallback。

### 6.3 Presenter 职责

`QuestTrackerPresenter` 在 tick 中完成：

- 读取 `ClientQuestCache`。
- resolve tracked quest。
- 维护 tracked quest / phase focus。
- 维护 active phase order。
- 维护 displayed/target phase。
- 推进 phase wipe 状态机。
- 推进 completion dismiss。
- 计算 panel reveal/slide/height/pushDownOffset。
- 根据 quest mode 生成 progression 或 collection view model。

禁止：

- 持有 `GuiGraphics`。
- 在 render 中被调用做业务计算。
- 每 tick 重建所有列表，除非 dirty。

### 6.4 ViewModel 建议

`QuestTrackerViewModel`：

```java
public final class QuestTrackerViewModel {
    public final ArcViewModelVersion version = new ArcViewModelVersion();
    public boolean visible;
    public boolean collectionQuest;
    public String questId;
    public String questTitle;
    public String phaseId;
    public String phaseName;
    public String phaseDescription;
    public int themeColor;
    public float panelReveal;
    public float panelSlide;
    public float wipeReveal;
    public float wipeAlpha;
    public float wipeDrift;
    public int targetHeight;
    public int pushDownOffset;
    public List<QuestTrackerPhaseViewModel> activePhases;
    public List<QuestTrackerObjectiveViewModel> objectives;
    public QuestTrackerCollectionViewModel collection;
}
```

`QuestTrackerObjectiveViewModel`：

```java
String key;
String text;
int progress;
int required;
boolean complete;
float reveal;
float progressVisual;
```

`QuestTrackerCollectionViewModel`：

```java
int discoveredCount;
int completedCount;
int totalCount;
List<String> visibleEntryLines;
List<String> rewardLines;
```

### 6.5 ArcMutil Root 结构

`ArcQuestTrackerOverlayRoot` 长期持有元素：

```text
root
  clip/wipe container
    panel background
    accent bar
    title text
    phase area
    description wrapped text
    objective vertical group OR collection group
```

优先使用：

- `ArcGuiRect`
- `ArcGuiText`
- `ArcGuiWrappedText`
- `ArcVerticalLayoutGroup`
- `ArcClipRect`
- `ArcScissorStack`

Root 只做：

- 读取 `QuestTrackerViewModel`
- dirty 时更新 text/width/visible
- draw-only 阶段绘制

### 6.6 接入策略

短期在 `QuestHudOverlay.render` 中：

```java
if (QuestHudMigrationFlags.ARC_TRACKER_ENABLED) {
    QuestArcHudController.INSTANCE.renderTracker(g, partialTick);
} else {
    trackerPanel.render(g, screenWidth, screenHeight, partialTick);
}
```

tick 中：

```java
QuestArcHudController.INSTANCE.tick();
```

长期：Forge overlay 直接由 controller 接管，旧 `QuestHudOverlay` 退役。

### 6.7 Tracker 验收

功能验收：

- 无 active quest 时不显示。
- 自动追踪第一个 active quest。
- `setTrackedQuest` 生效。
- `setTrackedFocus` 生效。
- quest completed/failed 后正确滑出。
- phase 切换 wipe 正确。
- parallel phase 显示正确。
- progression objectives 正确。
- collection tracker 正确。
- blocking screen 隐藏/冻结符合旧行为。
- toast push-down 正确。

性能验收：

- render 不访问 `ClientQuestCache`。
- render 不访问 `QuestRegistry`。
- render 不创建 objective/collection 列表。
- render 不推进 phase state machine。
- `ArcGuiProfiler.textLayouts` 只在 dirty 时增长。
- `ArcGuiProfiler.layoutRebuilds` 只在 dirty 时增长。

---

## 7. A14：Quest Toast 迁移

现有 `QuestToastManager.tick()` 可保留，第一步只做 snapshot/viewmodel 适配。

新增：

```text
QuestToastPresenter
QuestToastViewModel
QuestToastSlotViewModel
ArcQuestToastOverlayRoot
ArcQuestToastElement
```

迁移路线：

1. `QuestToastManager` 继续作为 `show(...)` 入口。
2. manager 暴露 active slot snapshot。
3. `QuestToastPresenter.tick` 读取 snapshot 并生成 VM。
4. ArcMutil root 绘制 slots。
5. 后续再把 manager 内部 queue 迁进 presenter。

普通 toast 先迁移；`PhaseUpdateToast` / `BranchChoiceToast` 第二轮迁移。

---

## 8. A15：Quest Journal 迁移

Journal 是复杂 Screen，放在 Tracker/Toast 后。

目标：

```text
ArcQuestJournalScreen
  QuestJournalPresenter
  QuestJournalViewModel
  ArcVirtualList questList
  detailRoot
  rewardStrip
```

迁移顺序：

1. list 使用 `ArcVirtualList`。
2. detail panel retained element tree。
3. rewards 接 render queue item pass。
4. collection detail 使用 snapshot。
5. tooltip 接 render queue tooltip pass。

`ClientQuestCache.refreshJournalIfOpen()` 短期保留；长期改为 presenter dirty。

---

## 9. A16：History / Story / Offer / Intel

### History

迁移重点：

- timeline ViewModel
- progression/collection 统一展示
- 长列表 virtual list
- text cache

### Story / Offer

迁移重点：

- retained panel
- choice/button input
- reward preview item pass

### Intel

最后迁移。

重点：

- blocking input
- pause/scroll/key handling
- ponder scene bridge

---

## 10. A17：Quest Marker 迁移

目标：

```text
QuestMarkerPresenter
  -> projected marker snapshots
ArcQuestMarkerOverlayRoot
  -> draw marker snapshot
```

注意：world projection 可以在 tick 或 dedicated update 中完成，render 只画 snapshot。

---

## 11. Collection Quest 策略

Collection 不做独立 HUD 体系，而是 Quest presentation mode。

原则：

- progression / collection 在 presenter 中分流。
- 外层 panel/root 复用。
- 内容区域替换为 objective group 或 collection group。
- collection detail/list/toast 都通过 ViewModel 表达。

已有相关类：

- `QuestMode.COLLECTION`
- `CollectionQuestConfig`
- `CollectionRuntimeData`
- `TrackerCollectionWidget`
- `JournalDetailCollection`
- `CollectionHistoryPanel`

迁移时优先复用业务计算，逐步替换绘制。

---

## 12. 提交切分建议

### A13 Tracker

1. `feat: 添加 Quest HUD 迁移开关与控制器`
2. `feat: 添加 Quest Tracker ViewModel 与 Presenter`
3. `feat: 添加 ArcMutil Quest Tracker overlay root`
4. `feat: 接入 ArcMutil Quest Tracker fallback 切换`
5. `perf: 将 Quest Tracker 数据更新迁移到 tick`

### A14 Toast

1. `feat: 添加 Quest Toast ViewModel 快照`
2. `feat: 添加 ArcMutil Quest Toast overlay root`
3. `feat: 接入 Quest Toast ArcMutil 渲染开关`

### A15 Journal

1. `feat: 添加 ArcMutil Quest Journal screen skeleton`
2. `feat: 使用 ArcVirtualList 重建 Quest Journal 列表`
3. `feat: 迁移 Quest Journal detail retained layout`
4. `perf: 将 Journal reward item 渲染接入 render queue`

---

## 13. 总验收清单

通用：

- 编译通过。
- 旧 HUD fallback 可用。
- 新 HUD 可通过 flag 启停。
- render 阶段无业务 cache 查询。
- 无每帧重建元素树。
- 无每帧重复 wrap text。
- profiler 指标符合预期。
- hideGui / blocking screen 行为正确。

Tracker：

- 自动追踪 active quest。
- 手动追踪生效。
- phase 切换动画正确。
- progression / collection 都正确。
- toast push-down 正确。

Toast：

- duplicate suppression 正确。
- frozen screen 不出队。
- active slot 生命周期正确。

Journal：

- list/detail/collection/reward/tooltip 正确。
- cache 更新后 dirty refresh 正确。

---

## 14. 下一步

直接进入：

```text
A13：Quest Tracker ArcMutil 生产级迁移
```

推荐 TODO：

1. 新增 `QuestHudMigrationFlags`。
2. 新增 `QuestArcHudController`。
3. 新增 `QuestHudBlockState`。
4. 新增 `QuestHudSelectors`。
5. 新增 tracker ViewModel 系列。
6. 新增 `QuestTrackerPresenter`。
7. 新增 `ArcQuestTrackerOverlayRoot`。
8. 在旧 `QuestHudOverlay` 中接入 fallback 切换。
9. 编译验证。
10. 分批中文提交。
