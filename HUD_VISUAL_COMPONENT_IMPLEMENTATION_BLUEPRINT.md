# HUD 视觉件组件实现蓝图

目标：把 Arc Quest HUD 从手动 renderer 体系升级为接近 tetra/mutil 的 retained-mode 高性能 UI。

统一链路：

```text
Data Source -> Presenter/ViewModel -> ArcGuiElement Tree -> ArcRenderQueue / Tooltip / Input
```

统一完成定义：

1. 每个视觉件有独立 `ArcGuiElement` 或 element tree。
2. render 阶段不排序、不组装大对象、不重建 child tree。
3. 文本 wrapping、tooltip、item name、visible rows 使用 cache/dirty。
4. 滚动区优先使用 `ArcScrollableX/Y`、`ArcVirtualList`、`ArcVirtualGrid`。
5. item/tooltip/overlay pass 统一接入 `ArcRenderQueue` 或等价机制。
6. 每个 HUD 有 debug fixture，可独立打开并与 legacy 对照。

---

## 1. HUD 总入口与 Overlay Host

目标结构：

```text
QuestHudOverlay
  -> QuestArcHudController
      -> ArcOverlayHost
          -> Tracker / Toast / CenterToast / Splash / Marker / GachaResult
```

实现要点：

- `QuestHudOverlay` 只保留 Minecraft/Forge event 入口。
- `QuestArcHudController` 负责 overlay 注册、tick、draw、block 策略。
- z-order：marker < tracker < toast < center toast < splash < modal。
- 非 active overlay 不递归 update/draw。

验收：所有 overlay 可并存，Splash/modal 屏蔽关系正确，profiler 可观测 draw/update 数。

---

## 2. Quest Tracker

目标结构：

```text
ArcQuestTrackerOverlayRoot
  -> ArcQuestTrackerPanelElement
      -> Header
      -> ArcQuestTrackerPhaseStrip
      -> ObjectiveList -> ArcQuestTrackerObjectiveRow[]
      -> ArcQuestTrackerCollectionSummary
      -> Footer
```

ViewModel：

```text
QuestTrackerViewModel
  questId, title, themeColor, mode
  phases[], objectives[], collectionSummary
  progressPercent, transitionState, version
```

实现步骤：Header、phase strip、objective list、collection summary child 化；objective row pool 复用；Presenter 负责 tracked quest、phase order、objective summary；高度由 layout group 计算；slide/fade/dismiss 用 `ArcPanelTransition`。

性能策略：quest/version 未变化不重建 rows；objective 文本按 `objectiveId + width + locale + progressVersion` 缓存。

---

## 3. Quest Toast / Center Toast

目标结构：

```text
ArcQuestToastOverlayRoot
  -> ToastSlotHost -> ToastSlot[] -> ArcQuestToastElement
ArcQuestCenterToastOverlayRoot
  -> CenterToastStage -> ArcQuestCenterToastElement
```

ViewModel：

```text
QuestToastViewModel
  id, type, title, subtitle, icon, themeColor
  durationMs, priority, dedupeKey, createdAt, version
```

实现步骤：收口 `QuestToastQueueService` 管理 queue/dedupe/freeze/priority；SlotHost 只做 placement 与 push-down；ToastElement 只画边框、icon、文本、进度条。

验收：连续 toast push-down 正确；dedupe 生效；Splash active 时 freeze 行为正确。

---

## 4. Quest Splash

目标结构：

```text
ArcQuestSplashOverlayRoot
  -> SplashBackdrop
  -> ArcQuestSplashElement
      -> Icon / Title / Subtitle / Progress
```

状态机：

```text
IDLE -> ENTER -> HOLD -> EXIT -> IDLE
```

实现要点：Splash 是 blocking overlay；时间线用 `ArcTimedStateMachine`；title/subtitle wrapping 只在数据变化时计算；背景、icon、水印、文字、进度条 child 化。

---

## 5. Quest Marker

目标结构：

```text
ArcQuestMarkerOverlayRoot
  -> MarkerLayer
      -> MarkerElement[]
          -> Icon / Distance / Pointer
```

ViewModel：

```text
QuestMarkerViewModel
  markerId, worldPos, screenPos, distance
  occluded, offscreen, direction, icon
  color, alpha, priority, version
```

实现步骤：`QuestMarkerPresenter` 在 update 中完成 projection；marker element 固定 pool；onscreen icon 与 offscreen pointer 分离；距离文本按 bucket 缓存；marker 多时按 priority/distance 裁剪。

---

## 6. Journal 总体

当前已迁移：

```text
ArcQuestJournalRoot / Element / Tab / List / Detail / Body
Header / HistoryButton / Scrollbar / Watermark
Rewards / Collection / SinglePhase / ParallelPhase
```

目标结构：

```text
QuestJournalScreen
  -> ArcScreenRoot
      -> ArcQuestJournalRoot
          -> ArcQuestJournalElement
              -> Tabs
              -> ListPane
              -> Detail
                  -> DetailControls
                  -> Body
                      -> Watermark
                      -> ScrollBody
                          -> Header
                          -> StateMessage
                          -> ActiveContent
                          -> Rewards
                      -> ArcScrollbarY
```

下一步：把 `headerElement.render(...)`、`watermarkElement.render(...)`、`scrollbarElement.render(...)` 改为真正 child tree；新增 `ArcQuestJournalScrollBodyElement`、`ArcQuestJournalStateMessageElement`、`ArcQuestJournalActiveContentElement`。

---

## 7. Journal Tab / List

Tab：

```text
ArcQuestJournalTabElement
  -> TabButton[ACTIVE, COMPLETED, FAILED]
```

List：

```text
ArcQuestJournalListPaneElement
  -> ArcClipRect
      -> ArcVirtualList<QuestListEntry>
          -> ArcQuestJournalListRowElement
  -> ArcScrollbarY
```

List VM：

```text
JournalListViewModel
  tab, rows[], selectedQuestId, scrollOffset, version
```

验收：100+ quest 时只绘制可见行；row title/subtitle trim 缓存；hover/selected 动画只存在于可见 row。

---

## 8. Journal Detail / Body / Header

Detail：

```text
ArcQuestJournalDetailElement
  -> ArcPanelChrome
  -> DetailControls
  -> ArcQuestJournalBodyElement
```

Body：

```text
ArcQuestJournalBodyElement
  -> Watermark
  -> ArcQuestJournalScrollBodyElement
      -> ArcVerticalLayoutGroup
          -> Header
          -> StateMessage
          -> ActiveContent
          -> Rewards
  -> ArcScrollbarY
```

Header：

```text
ArcQuestJournalHeaderElement
  -> TitleRow: Icon / TitleText / TimerText / HistoryButton
  -> ArcGuiWrappedText Description
```

性能策略：timer 每秒更新一次；description wrapping 用 `ArcTextLayoutCache`；Body 不再直接判断 collection/single/parallel。

---

## 9. Journal Active Content

目标：

```text
ArcQuestJournalActiveContentElement
  -> ArcQuestJournalCollectionElement
  -> ArcQuestJournalSinglePhaseElement
  -> ArcQuestJournalParallelPhaseElement
```

职责：根据 quest 类型和 active phase 数量切换 child 显隐；输出 selected phase id 给 rewards；content type 切换时执行 transition。

验收：`ArcQuestJournalBodyElement` 不再直接判断 collection/single/parallel。

---

## 10. Journal Collection / Single / Parallel

Collection：

```text
ArcQuestJournalCollectionElement
  -> ProgressSummary
  -> ArcVirtualList<CollectionEntry> -> CollectionEntryRow
  -> ManualRewardList
```

Single：

```text
ArcQuestJournalSinglePhaseElement
  -> PhaseTitle / PhaseDescription / StoryButton
  -> ObjectiveList
  -> BranchChoiceList
  -> IntelButton
```

Parallel：

```text
ArcQuestJournalParallelPhaseElement
  -> ParallelHeader
  -> PhaseLaneStrip -> ArcScrollableX -> PhaseCard[]
  -> SelectedPhaseDetail
  -> HorizontalScrollbar
```

关键点：collection rows virtualized；single objective row 复用 tracker row 能力；parallel phase card 从大类中拆出；lane strip 管理横向滚动和 drag reorder；phase order cache 只在 drag end 持久化。

---

## 11. Journal Rewards

目标结构：

```text
ArcQuestJournalRewardsElement
  -> RewardTabStrip
  -> ArcClipRect
      -> ArcScrollableX -> RewardCard[]
  -> RewardScrollbar
```

ViewModel：

```text
JournalRewardsViewModel
  selectedTab, phaseRewards[], chapterRewards[]
  visibleRewards[], tooltipData, version
```

关键改造：移除每帧 `List<Runnable>` item pass；item 渲染提交给 `ArcRenderQueue.item(...)`；tooltip 统一走 `ArcTooltipProvider`；reward card 长期复用。

---

## 12. Journal 辅助面板

对象：History、Story、Offer、Intel、CollectionHistory。

目标结构：

```text
ArcQuestLegacyPanelOverlayRoot
  -> ArcModalPanelRoot
      -> HistoryPanel / StoryPanel / OfferPanel / IntelPanel / CollectionHistoryPanel
```

重点：History timeline 用 virtual list；Story text cache；Offer item pass queue；Intel topology 使用 pan/zoom culling；CollectionHistory 使用 virtual grid。

---

## 13. Dialogue Screen

目标结构：

```text
ArcDialogueScreenRoot
  -> Backdrop
  -> SpeakerPanel
  -> DialogueTextPanel -> ArcGuiTypewriterText
  -> ChoiceList -> ArcOptionListElement
  -> HistoryButton
  -> HistoryPanel
```

ViewModel：`sessionId, speakerName, portrait, currentLine, typewriterProgress, choices[], history[], canContinue, version`。

性能策略：当前句 wrapping 只在线变化时计算；choices 使用 button pool；history 使用 `ArcVirtualList`。

---

## 14. Shop / Trade Screen

目标结构：

```text
ArcTradeScreenRoot
  -> Header
  -> CategoryStrip
  -> OfferPane -> ArcVirtualList/Grid -> TradeOfferCard
  -> DetailPanel -> RequiredItems / ResultItems / ActionButton
  -> TooltipLayer
```

ViewModel：`shopId, categories[], selectedCategory, offers[], selectedOffer, playerAffordability, tooltip, version`。

实现要点：category 切换刷新 offers VM；offer card pool 复用；affordability 在 Presenter 计算；item/tooltip 走 `ArcRenderQueue`。

---

## 15. Gacha Screen / Result Overlay

目标结构：

```text
ArcGachaScreenRoot
  -> MachineFrame
  -> PreviewPanel -> ArcVirtualGrid<ResultPreviewCard>
  -> RollerPanel -> RollerReel[]
  -> ControlPanel
ArcGachaResultOverlayRoot
  -> ResultStage -> ResultCard[]
```

ViewModel：`shopId, previewPool[], rollerState, currentRolls[], result[], canRoll, cost, version`。

性能策略：reel item pool 固定；roller 动画只改 y/opacity/scale；result item/tooltip 走 render queue。

---

## 16. Intel / Ponder Topology

目标结构：

```text
ArcIntelTopologyElement
  -> ArcPanZoomCanvas
      -> IntelEdgeLayer
      -> IntelNode[]
      -> IntelSelectionDetail
```

ViewModel：`nodes[], edges[], selectedNodeId, unlockedNodeIds[], camera, version`。

实现要点：pan/zoom 交给 `ArcPanZoomCanvas`；nodes 做 viewport culling；edge path cache；node tooltip cache。

---

## 17. Debug / Compare

目标结构：

```text
ArcHudDebugOverlayRoot
  -> DebugHudSelectorPanel
  -> DebugScenarioPanel
  -> DebugPerfPanel
```

指令建议：

```text
/aqhud arc tracker demo
/aqhud arc toast quest
/aqhud arc splash complete
/aqhud arc marker demo
/aqhud arc journal parallel
/aqhud arc dialogue demo
/aqhud arc shop demo
/aqhud arc gacha demo
/aqhud compare journal
```

Fixture：trackerNormal、trackerCollection、toastStack、markerDenseField、journalParallelQuest、journalCollectionQuest、dialogueBranching、tradeLargeShop、gachaTenRoll。

---

## 18. 分批实现优先级

P0：收尾 Journal retained tree 化。

```text
refactor: 将 Journal 详情体改为滚动容器组件
refactor: 拆分 Journal 状态消息组件
refactor: 拆分 Journal active content 分发组件
perf: Journal rewards 接入 ArcRenderQueue
refactor: 拆分 Journal 并行阶段卡片组件
```

P1：Overlay 高性能闭环。

```text
refactor: 统一 HUD overlay host 驱动
refactor: 完善 tracker presenter 与 row pool
refactor: 重构 toast queue 与 slot host
refactor: 收口 splash blocking overlay
perf: marker 接入 presenter 与 element pool
```

P2：Screen 体系统一。

```text
refactor: Dialogue 接入 ArcScreenRoot
refactor: Trade screen 接入 ArcMutil virtual grid
refactor: Gacha screen 接入 ArcMutil result cards
refactor: Intel topology 接入 pan zoom canvas
```

P3：旧 HUD 退役。

```text
chore: 默认启用 ArcMutil HUD 实现
chore: 保留 legacy compare debug 入口
refactor: 删除已迁移 legacy renderer
refactor: 清理旧 HUD util 依赖
```
