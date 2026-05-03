# Arc Quest Objective Framework 2.0 实施蓝图

## 1. 文档目标

本蓝图是《Objective Framework 2.0 设计稿》的工程落地版本，目标是：

- 基于当前 `Arc Quest` 代码库实施新的 `COLLECTION` 型任务系统
- 保持现有 `PROGRESSION` 任务完全兼容
- 给出明确的类改造方案、字段草案、序列化方案、同步方案、UI 接入点和分阶段开发顺序

本文默认当前代码基线如下：

- 顶层定义：`quest/api/QuestDefinition.java`
- 阶段定义：`quest/api/PhaseDefinition.java`
- 运行时：`quest/capability/QuestRuntimeData.java`
- 服务端推进：`quest/logic/QuestProgressHandler.java`
- 客户端镜像：`quest/network/ClientQuestCache.java`
- Journal 详情：`client/hud/quest/journal/detail/JournalDetailPanel.java`
- Tracker：`client/hud/quest/tracker/QuestTrackerPanel.java`

---

## 2. 实施总路线

采用“定义层扩展 -> Runtime 扩展 -> Handler 分流 -> UI 分流”的四阶段实施路径。

### 阶段 A：定义层扩展
目标：让 Registry / Builder / QuestDefinition 能表达 collection quest。

### 阶段 B：Runtime 与同步扩展
目标：让服务端 capability 和客户端 cache 能存取 collection 状态。

### 阶段 C：服务端求值分流
目标：让 `QuestProgressHandler` 在 `QuestMode.COLLECTION` 下走新引擎。

### 阶段 D：客户端 UI 分流
目标：让 Journal / Tracker / Toast / History 能按 mode 展示不同内容。

---

## 3. 包与文件实施方案

### 3.1 新增文件清单

#### 定义层
- `src/main/java/org/arcadia/arc_quest/quest/api/QuestMode.java`
- `src/main/java/org/arcadia/arc_quest/quest/api/CollectionQuestConfig.java`
- `src/main/java/org/arcadia/arc_quest/quest/api/CollectionCategoryDefinition.java`
- `src/main/java/org/arcadia/arc_quest/quest/api/CollectionEntryConfig.java`
- `src/main/java/org/arcadia/arc_quest/quest/api/VisibilityMode.java`
- `src/main/java/org/arcadia/arc_quest/quest/api/HiddenPresentationMode.java`
- `src/main/java/org/arcadia/arc_quest/quest/api/CountingMode.java`
- `src/main/java/org/arcadia/arc_quest/quest/api/EntryRewardGrantMode.java`
- `src/main/java/org/arcadia/arc_quest/quest/api/RewardScope.java`
- `src/main/java/org/arcadia/arc_quest/quest/api/CollectionCompletionRule.java`
- `src/main/java/org/arcadia/arc_quest/quest/api/CollectionRuleContext.java`
- `src/main/java/org/arcadia/arc_quest/quest/api/CollectionRewardNode.java`
- `src/main/java/org/arcadia/arc_quest/quest/api/TrackerPresentationMode.java`
- `src/main/java/org/arcadia/arc_quest/quest/api/CollectionPresentationMode.java`

#### Runtime / capability 层
- `src/main/java/org/arcadia/arc_quest/quest/capability/CollectionRuntimeData.java`

#### 逻辑层
- `src/main/java/org/arcadia/arc_quest/quest/logic/profile/CollectionQuestEngine.java`
- `src/main/java/org/arcadia/arc_quest/quest/logic/profile/ProgressionQuestEngine.java`
- `src/main/java/org/arcadia/arc_quest/quest/logic/profile/collection/CollectionRuleEvaluator.java`
- `src/main/java/org/arcadia/arc_quest/quest/logic/profile/collection/CollectionVisibilityResolver.java`
- `src/main/java/org/arcadia/arc_quest/quest/logic/profile/collection/CollectionRewardResolver.java`
- `src/main/java/org/arcadia/arc_quest/quest/logic/profile/collection/CollectionCategorySnapshot.java`

#### 客户端 UI 层
- `src/main/java/org/arcadia/arc_quest/client/hud/quest/journal/detail/JournalDetailCollection.java`
- `src/main/java/org/arcadia/arc_quest/client/hud/quest/tracker/TrackerCollectionWidget.java`
- `src/main/java/org/arcadia/arc_quest/client/hud/quest/history/CollectionHistoryPanel.java`
- `src/main/java/org/arcadia/arc_quest/client/hud/quest/toast/CollectionEntryToast.java`
- `src/main/java/org/arcadia/arc_quest/client/hud/quest/toast/CollectionRewardToast.java`

### 3.2 修改文件清单

#### 必改定义文件
- `QuestDefinition.java`
- `PhaseDefinition.java`
- `QuestBuilder.java`
- `PhaseBuilder.java`

#### 必改运行时文件
- `QuestRuntimeData.java`
- `QuestCapabilityImpl.java`
- `IQuestCapability.java`

#### 必改逻辑文件
- `QuestProgressHandler.java`
- `ObjectiveTracker.java`
- `QuestEventManager.java`

#### 必改同步文件
- `ClientQuestCache.java`
- `QuestSyncCoordinator.java`
- `S2CSyncFullDataPacket.java`
- `S2CSyncQuestStatePacket.java`

#### 必改 HUD 文件
- `JournalDetailPanel.java`
- `JournalListPanel.java`
- `QuestTrackerPanel.java`
- `QuestHistoryPanel.java`
- `QuestToastManager.java`

---

## 4. 定义层字段蓝图

### 4.1 QuestMode

```java
public enum QuestMode {
    PROGRESSION,
    COLLECTION
}
```

默认值：`PROGRESSION`

### 4.2 QuestDefinition 改造

#### 新增字段

```java
private final QuestMode mode;
@Nullable
private final CollectionQuestConfig collectionConfig;
```

#### 新增 getter

```java
public QuestMode getMode();
@Nullable public CollectionQuestConfig getCollectionConfig();
public boolean isCollectionQuest();
```

#### 构造器兼容策略
所有旧构造器自动注入：

- `mode = QuestMode.PROGRESSION`
- `collectionConfig = null`

新增 collection 构造器/Builder 路径时显式传入。

### 4.3 PhaseDefinition 改造

#### 新增字段

```java
@Nullable
private final CollectionEntryConfig collectionEntryConfig;
```

#### 新增 getter

```java
@Nullable public CollectionEntryConfig getCollectionEntryConfig();
public boolean hasCollectionEntryConfig();
```

#### 语义
- progression quest：该字段通常为 `null`
- collection quest：每个 phase 都应具备 `collectionEntryConfig`

### 4.4 CollectionQuestConfig 蓝图

```java
public final class CollectionQuestConfig {
    private final List<CollectionCategoryDefinition> categories;
    private final List<CollectionCompletionRule> questCompletionRules;
    private final List<CollectionRewardNode> questRewardNodes;
    private final TrackerPresentationMode trackerMode;
    private final CollectionPresentationMode journalMode;
    private final boolean revealAllEntriesByDefault;
    private final boolean allowManualRewardClaim;
    private final boolean showCategories;
}
```

### 4.5 CollectionCategoryDefinition 蓝图

```java
public final class CollectionCategoryDefinition {
    private final String categoryId;
    private final QuestText displayName;
    @Nullable
    private final ResourceLocation iconTexture;
    private final int sortOrder;
    private final List<CollectionCompletionRule> completionRules;
    private final List<CollectionRewardNode> rewardNodes;
    private final List<ICondition> visibilityConditions;
}
```

### 4.6 CollectionEntryConfig 蓝图

```java
public final class CollectionEntryConfig {
    private final String categoryId;
    private final VisibilityMode visibilityMode;
    private final HiddenPresentationMode hiddenPresentationMode;
    private final List<ICondition> visibilityConditions;
    private final CountingMode countingMode;
    private final int completionTarget;
    private final boolean repeatableProgress;
    private final boolean repeatableCompletion;
    private final int maxCount;
    private final EntryRewardGrantMode rewardGrantMode;
    private final List<CollectionRewardNode> rewardNodes;
    private final int sortOrder;
    private final boolean showInTrackerByDefault;
}
```

### 4.7 新枚举蓝图

#### VisibilityMode
```java
VISIBLE_BY_DEFAULT,
HIDDEN_BY_DEFAULT,
DISCOVER_ONLY,
CONDITIONAL
```

#### HiddenPresentationMode
```java
FULLY_HIDDEN,
PLACEHOLDER,
SILHOUETTE,
NAME_MASKED
```

#### CountingMode
```java
BINARY,
ACCUMULATE,
UNIQUE_SET
```

#### EntryRewardGrantMode
```java
AUTO,
MANUAL
```

#### RewardScope
```java
ENTRY,
CATEGORY,
QUEST,
MILESTONE
```

#### TrackerPresentationMode
```java
SUMMARY,
SUMMARY_WITH_FEED,
PINNED_ENTRIES
```

#### CollectionPresentationMode
```java
GRID,
LIST,
GRID_WITH_DETAIL
```

---

## 5. 规则系统实施蓝图

### 5.1 CollectionCompletionRule 接口

```java
public interface CollectionCompletionRule {
    boolean test(CollectionRuleContext context);
    String getDebugLabel();
}
```

### 5.2 首批规则实现文件建议

- `AllEntriesCompleteRule.java`
- `CompletedEntryCountRule.java`
- `CompletedEntryRatioRule.java`
- `CategoryCompletedCountRule.java`
- `CategoryCompletedRatioRule.java`
- `AndCollectionRule.java`
- `OrCollectionRule.java`
- `NotCollectionRule.java`

放置建议：`quest/api/rule/collection/`

### 5.3 CollectionRuleContext

```java
public final class CollectionRuleContext {
    private final ServerPlayer player;
    private final QuestDefinition questDef;
    private final QuestRuntimeData runtime;
    private final CollectionRuntimeData collectionData;
    private final IQuestCapability capability;
    @Nullable
    private final String categoryId;
}
```

---

## 6. Runtime 层实施蓝图

### 6.1 CollectionRuntimeData 蓝图

```java
public final class CollectionRuntimeData {
    private final LinkedHashSet<String> visiblePhaseIds;
    private final LinkedHashSet<String> discoveredPhaseIds;
    private final LinkedHashMap<String, Integer> entryCounts;
    private final LinkedHashMap<String, LinkedHashSet<String>> entryUniqueKeys;
    private final LinkedHashSet<String> claimedRewardIds;
    private String lastUpdatedPhaseId;
    private String lastUpdatedCategoryId;
    private long lastUpdatedAtMs;
    private boolean dirty;
}
```

### 方法建议
- `isVisible(String phaseId)`
- `isDiscovered(String phaseId)`
- `getEntryCount(String phaseId)`
- `incrementEntryCount(String phaseId, int amount, int max)`
- `addUniqueKey(String phaseId, String key)`
- `markVisible(String phaseId)`
- `markDiscovered(String phaseId)`
- `isRewardClaimed(String rewardId)`
- `markRewardClaimed(String rewardId)`
- `copy()`
- `serializeNBT()` / `deserializeNBT()`
- `writeToNetwork()` / `readFromNetwork()`

### 6.2 QuestRuntimeData 改造蓝图

#### 新增字段

```java
@Nullable
private CollectionRuntimeData collectionData;
```

#### 新增方法

```java
@Nullable public CollectionRuntimeData getCollectionData();
public boolean hasCollectionData();
public void setCollectionData(@Nullable CollectionRuntimeData data);
```

#### SchemaVersion
从 `2` 升级到 `3`

#### NBT 新结构

```nbt
CollectionData: {
  VisiblePhases: [...],
  DiscoveredPhases: [...],
  EntryCounts: {...},
  EntryUniqueKeys: {...},
  ClaimedRewards: [...],
  LastUpdatedPhaseId: "...",
  LastUpdatedCategoryId: "...",
  LastUpdatedAtMs: 0L
}
```

---

## 7. Capability / Sync 实施蓝图

### 7.1 IQuestCapability 改造

新增只读辅助接口：

- `boolean isCollectionQuestActive(String questId)`
- `@Nullable CollectionRuntimeData getCollectionData(String questId)`

### 7.2 QuestCapabilityImpl 改造

需要保证：
- collection data 跟随 `QuestRuntimeData` 一起存档
- dirty 判定能包含 collectionData.dirty
- full sync 时带出 collectionData

### 7.3 QuestSyncCoordinator 改造

当前按 `QuestRuntimeData` 同步。首版不拆 collection 专属 packet。

#### 首版策略
- full sync：直接携带包含 `collectionData` 的 `QuestRuntimeData`
- quest state sync：同上
- objective delta：progression 继续用；collection 暂不新增 delta packet

#### 后续优化
再增量化：
- collection visibility delta
- collection count delta
- reward claim delta

---

## 8. 服务端推进层实施蓝图

### 8.1 QuestProgressHandler 职责重构

当前仍保留为统一入口，但内部 profile-aware。

#### 建议新增私有分发

```java
private static boolean isCollectionQuest(QuestDefinition def)
```

统一入口分流：
- `acceptQuest(...)`
- `incrementObjective(...)`
- `handlePlayerChoice(...)`
- quest completion check

对 collection quest 走 `CollectionQuestEngine`

### 8.2 新增 ProgressionQuestEngine

目标：把现有 `QuestProgressHandler` 里 progression 专属逻辑逐步迁过去。

首版可做法：先不完全迁文件，只抽私有静态 helper，等 collection 跑稳后再完全拆类。

### 8.3 CollectionQuestEngine 蓝图

#### 核心职责
- 初始化 collection runtime
- reveal / discover entry
- update count / unique set
- evaluate entry completion
- evaluate category completion
- evaluate quest completion
- unlock rewards
- 驱动 sync / toast hooks

#### 核心方法

```java
initializeQuest(ServerPlayer, IQuestCapability, QuestDefinition, QuestRuntimeData)
revealEntry(...)
discoverEntry(...)
incrementEntry(...)
addUniqueProgress(...)
evaluateEntryCompletion(...)
evaluateCategoryStates(...)
evaluateQuestState(...)
evaluateRewardUnlocks(...)
claimReward(...)
```

### 8.4 接任务初始化（COLLECTION）

流程：
1. 创建 `QuestRuntimeData`
2. 创建 `CollectionRuntimeData`
3. 遍历 quest 全部 phase
4. 根据 `CollectionEntryConfig.visibilityMode` 决定：初始 visible / hidden / discover only / conditional
5. 对初始 visible 的 phase：加入 `activePhaseIds`
6. 将 `collectionData` 附加到 runtime
7. add active quest
8. 发 full quest sync

兼容策略：保留 `initialPhaseId`，但 collection mode 下仅作为“默认聚焦 phase”而不是推进起点。

### 8.5 条目更新模型

progression 保持 `incrementObjective(...)`。

collection 新增：

```java
public static void incrementCollectionEntry(ServerPlayer player, String questId, String phaseId, int amount)
public static void discoverCollectionEntry(ServerPlayer player, String questId, String phaseId)
public static void addCollectionUniqueKey(ServerPlayer player, String questId, String phaseId, String key)
```

后续再做统一 event binding 自动分发。

### 8.6 完成判定顺序

每次 collection entry 变化后：
1. 判 entry 是否 completed
2. 若完成，发 entry reward unlock
3. 重算 category 统计
4. 跑 category completion rules
5. 跑 quest completion rules
6. 若 quest complete，则沿用现有 `QuestState.COMPLETED`

注意：collection quest 的 quest complete 不意味着所有 reward 自动领取，仍需看 reward node grant mode。

---

## 9. ClientQuestCache 实施蓝图

### 9.1 新增读取接口

```java
public boolean isCollectionQuest(String questId)
@Nullable public CollectionRuntimeData getCollectionData(String questId)
public boolean isCollectionEntryVisible(String questId, String phaseId)
public boolean isCollectionEntryDiscovered(String questId, String phaseId)
public int getCollectionEntryCount(String questId, String phaseId)
public boolean isCollectionRewardClaimed(String questId, String rewardId)
public List<CollectionCategorySnapshot> getCollectionCategorySnapshots(String questId)
```

### 9.2 更新缓存逻辑

`updateQuest(QuestRuntimeData data)` 不需要特殊分包逻辑，只要 collectionData 被正确反序列化即可。

额外建议：在这里构造客户端 feed 事件：
- 条目新发现
- 条目新完成
- 分类奖励解锁
- 总奖励解锁

供 Toast / Tracker 使用。

---

## 10. Journal UI 实施蓝图

### 10.1 JournalDetailPanel 分流点

在 `render(...)` 中，在拿到 `QuestDefinition def` 后立即判断：

```java
if (def.isCollectionQuest()) {
    collectionRenderer.render(...);
    return;
}
```

新增字段：

```java
private final JournalDetailCollection collectionRenderer;
```

在构造器初始化。

### 10.2 JournalDetailCollection 职责

渲染结构：
1. 顶部摘要
2. 分类页签
3. 条目网格/列表
4. 条目详情
5. 奖励区

需要维护的局部状态：
- `selectedCategoryId`
- `selectedEntryPhaseId`
- `categoryScroll / gridScroll`
- `hover tooltip state`
- `reward claim interaction state`

### 10.3 条目数据来源

collection 模式下：
- 每个 `PhaseDefinition` 视为一个 entry
- 从 `CollectionEntryConfig` 获取 category / visibility / count mode
- 从 `CollectionRuntimeData` 获取 visible/discovered/count/claimed state

### 10.4 JournalListPanel 适配

在 quest 列表卡片上增加 collection 摘要渲染分支：
- 完成率
- 已发现/总数
- 是否有可领取奖励
- category 数量

旧 progression quest 维持原状。

---

## 11. Tracker 实施蓝图

### 11.1 QuestTrackerPanel 分流点

在 `render(...)` 中，拿到 `QuestDefinition def` 后：

```java
if (def.isCollectionQuest()) {
    trackerCollectionWidget.render(...);
    return;
}
```

### 11.2 TrackerCollectionWidget 蓝图

首版显示内容：
- quest 名称
- 总完成数 / 总数
- 当前分类进度
- 最近更新 feed

依赖状态：
- `lastUpdatedPhaseId`
- `lastUpdatedCategoryId`
- 客户端 feed 队列

首版不做：
- pin entries
- 条目展开分页

---

## 12. Toast / History 实施蓝图

### 12.1 ToastManager 分流

新增 toast type：
- `COLLECTION_ENTRY_DISCOVERED`
- `COLLECTION_ENTRY_COMPLETED`
- `COLLECTION_REWARD_UNLOCKED`

### 12.2 CollectionHistoryPanel

首版可以先只做：
- 分类视图
- 条目完成网格
- 不做复杂时间轴

在 `QuestHistoryPanel.trigger(...)` 前按 mode 分流到 collection history。

---

## 13. Builder 与内容编写实施蓝图

### 13.1 QuestBuilder 改造

新增：

```java
QuestBuilder.mode(QuestMode mode)
QuestBuilder.collectionConfig(CollectionQuestConfig config)
```

默认 `PROGRESSION`

### 13.2 PhaseBuilder 改造

新增：

```java
PhaseBuilder.collectionEntryConfig(CollectionEntryConfig config)
```

这样内容作者可以在现有 phase builder 基础上定义 collection entry。

### 13.3 内容注册建议

collection quest 内容仍走 `QuestRegistry`。

首个示例建议新增：
- 一个最小 monster codex demo
- 2~3 个 category
- 6~10 个 entries
- 至少一条 hidden reveal 规则
- 至少一条 repeatable count entry
- 至少一个 category reward
- 至少一个 quest reward

这样能全链路验证系统。

---

## 14. 开发任务顺序建议

### Sprint 1：定义层与 runtime
- QuestMode
- CollectionQuestConfig
- CollectionEntryConfig
- CollectionCategoryDefinition
- CollectionRuntimeData
- QuestRuntimeData 序列化扩展

### Sprint 2：服务端核心引擎
- CollectionQuestEngine
- entry visible/discovered/count/completion
- reward unlock state
- quest completion rules

### Sprint 3：客户端缓存与 Journal
- ClientQuestCache 扩展
- JournalDetailCollection
- JournalListPanel collection summary

### Sprint 4：Tracker / Toast / History
- TrackerCollectionWidget
- Collection toasts
- CollectionHistoryPanel

### Sprint 5：优化与增量同步
- collection delta sync
- feed 优化
- UI 交互完善
- 示例内容完善

---

## 15. 阶段性落地状态（2026-05）

### 15.1 已完成的核心主链

#### 定义层
- `QuestMode`
- `CollectionQuestConfig`
- `CollectionCategoryDefinition`
- `CollectionEntryConfig`
- `CollectionCompletionRule`
- `CollectionRuleContext`
- `CollectionRewardNode`
- `QuestDefinition` / `PhaseDefinition` / `QuestBuilder` / `PhaseBuilder` 的 collection 接线

#### Runtime / Capability / Sync
- `CollectionRuntimeData` 已接入 `QuestRuntimeData`
- `CollectionRuntimeData` 已支持：
  - 可见条目
  - 已发现条目
  - 计数 / unique key
  - `unlockedRewardIds`
  - `claimedRewardIds`
- NBT / network / copy 已打通
- `IQuestCapability` / `QuestCapabilityImpl` / `ClientQuestCache` 已具备 collection 读取能力
- 首版同步策略已落地：通过现有 `QuestRuntimeData` 镜像携带 collection 状态

#### 服务端运行时
- `QuestProgressHandler` 已按 `QuestMode` 分流
- `CollectionQuestEngine` 已具备首版核心职责：
  - collection 接任务初始化
  - entry discover / increment / unique progress
  - entry completion
  - category rule 求值
  - quest completion
  - reward unlock / auto grant / manual claim
- reward 状态已从“仅 claimed”推进到“unlocked / claimed 分离”

#### 客户端 UI / 交互
- `JournalDetailCollection` 已接入 `JournalDetailPanel`
- `TrackerCollectionWidget` 已接入 `QuestTrackerPanel`
- `JournalListPanel` 已有 collection summary 分支
- `CollectionHistoryPanel` 已接入 `QuestHistoryPanel.trigger(...)` 分流
- `QuestToastManager` 已支持：
  - `COLLECTION_ENTRY_DISCOVERED`
  - `COLLECTION_ENTRY_COMPLETED`
  - `COLLECTION_REWARD_UNLOCKED`
- `JournalDetailCollection` 已支持 manual reward claim 最小交互链路
- reward UI 已支持：`Locked / Claim / Claimed`

#### 网络交互
- 已新增 `C2SClaimCollectionRewardPacket`
- 服务端 reward claim 已可回写 collection runtime 并同步到客户端

### 15.2 当前状态判断

本蓝图已经达到“首版可用、主链闭环”的阶段：
- collection quest 可接受
- collection 状态可存档/同步
- 条目可发现、计数、完成
- reward 可解锁、可自动发放、可手动领取
- Journal / Tracker / History / Toast 已完成首版分流接入
- 旧 progression quest 主链仍保持兼容

也就是说，当前不是“缺主干”，而是“还差满配与收尾”。

### 15.3 仍待完成的非 HUD 项

#### 规则层
- `CompletedEntryRatioRule`
- `CategoryCompletedCountRule`
- `CategoryCompletedRatioRule`
- `AndCollectionRule`
- `OrCollectionRule`
- `NotCollectionRule`
- 规则层的统一组织、复用与 debug 能力

#### 服务端结构整理
- `ProgressionQuestEngine` 仍未正式落地为完整迁移目标
- `QuestProgressHandler` 与 `CollectionQuestEngine` 的职责边界仍可继续收紧
- `revealEntry(...)` 等更完整的 collection API 仍可继续整理
- 自动事件分发 / event binding 体系尚未完整框架化

#### Reward / Runtime 能力
- milestone / repeat claim 仍为预留能力，未完整实现
- category 运行时状态仍偏“逻辑求值”，尚未提升为更完整的独立 runtime snapshot
- 更细粒度的 reward reject code / observability 仍可继续细化

#### Sync / 协议层
- collection 仍主要依赖 quest state 镜像同步
- collection delta sync 尚未拆分：
  - visibility delta
  - count delta
  - reward delta
- 更细颗粒度的 collection feed / packet 仍为后续优化项

### 15.4 仍待完成的 HUD / 表达层项

#### Journal / History / Tracker 完整度
- `CollectionHistoryPanel` 目前是首版最小实现，仍可继续增强为分类/条目/奖励轨迹视图
- `JournalDetailCollection` 目前已可展示 reward node 与三态按钮，但 reward 具体内容摘要、图标与更强分组表现仍可继续完善
- Tracker 目前为摘要级 collection widget，尚未扩展到更丰富的 category / feed 表达

#### Category 表达
- category 维度目前已参与规则与 reward 逻辑，但在客户端展示层仍未完全强化为独立的一等视图

### 15.5 建议的下一阶段工作顺序

1. 补齐剩余 collection rule 实现
2. 继续整理 `CollectionQuestEngine` / `QuestProgressHandler` 分层
3. 建立 collection 自动事件绑定与统一分发入口
4. 视需要补 collection delta sync
5. 最后再继续增强 History / Tracker / Journal 的表现完整度

---

## 16. 风险点与规避策略

### 15.1 风险：QuestRuntimeData 改造影响广
规避：
- 追加字段，不破坏旧字段
- 保持旧 getter/setter 行为不变
- 全部走默认 null-safe 路径

### 15.2 风险：QuestProgressHandler 过于庞大
规避：
- 先引入 engine 分流，不一次性大拆
- progression 逻辑先保持原位
- collection 新逻辑尽量放新类

### 15.3 风险：UI 侵入太大
规避：
- 通过 `JournalDetailPanel` 和 `QuestTrackerPanel` 的 mode 分流接入
- 不重写整个 Screen / Panel 基座

### 15.4 风险：reward 规则过早复杂化
规避：
- 首版 reward node 支持 unlock + claim 即可
- milestone / repeat claim 先只预留字段，不全实现

---

## 16. 首版验收标准

### 服务端
- collection quest 可接受
- 条目可见性可正确初始化
- 条目可发现/计数/完成
- quest 完成规则生效
- 条目/分类/quest 奖励状态可记录

### 客户端
- full sync 后 collection 状态正确显示
- Journal 可正常展示分类与条目
- Tracker 能展示摘要
- 奖励状态可展示
- collection quest 不破坏旧 progression quest UI

### 兼容
- 现有 quest registry 内容无须修改即可继续运行
- 老存档可读取
- 旧 tracker/journal/history 不报错

---

## 17. 最终实施原则

本蓝图的核心原则是：

1. 继续以 `QuestDefinition` 作为顶层容器
2. 继续以 `PhaseDefinition` 作为底层节点
3. 在 `COLLECTION` 模式下将 phase 重新解释为 entry
4. 通过 `CollectionEntryConfig + CollectionRuntimeData` 提供新能力
5. 通过 `QuestProgressHandler + CollectionQuestEngine` 分流运行逻辑
6. 通过 `JournalDetailCollection + TrackerCollectionWidget` 分流展示逻辑

这样可以在当前代码库上实现“往大了做”的 Objective Framework，而不是推翻重建。


目标：把现有 `QuestProgressHandler` 里 progression 专属逻辑逐步迁过去。

首版可做法：先不完全迁文件，只抽私有静态 helper，等 collection 跑稳后再完全拆类。

### 8.3 CollectionQuestEngine 蓝图

#### 核心职责
- 初始化 collection runtime
- reveal / discover entry
- update count / unique set
- evaluate entry completion
- evaluate category completion
- evaluate quest completion
- unlock rewards
- 驱动 sync / toast hooks

#### 核心方法

```java
initializeQuest(ServerPlayer, IQuestCapability, QuestDefinition, QuestRuntimeData)
revealEntry(...)
discoverEntry(...)
incrementEntry(...)
addUniqueProgress(...)
evaluateEntryCompletion(...)
evaluateCategoryStates(...)
evaluateQuestState(...)
evaluateRewardUnlocks(...)
claimReward(...)
```

### 8.4 接任务初始化（COLLECTION）

流程：
1. 创建 `QuestRuntimeData`
2. 创建 `CollectionRuntimeData`
3. 遍历 quest 全部 phase
4. 根据 `CollectionEntryConfig.visibilityMode` 决定：初始 visible / hidden / discover only / conditional
5. 对初始 visible 的 phase：加入 `activePhaseIds`
6. 将 `collectionData` 附加到 runtime
7. add active quest
8. 发 full quest sync

兼容策略：保留 `initialPhaseId`，但 collection mode 下仅作为“默认聚焦 phase”而不是推进起点。

### 8.5 条目更新模型

progression 保持 `incrementObjective(...)`。

collection 新增：

```java
public static void incrementCollectionEntry(ServerPlayer player, String questId, String phaseId, int amount)
public static void discoverCollectionEntry(ServerPlayer player, String questId, String phaseId)
public static void addCollectionUniqueKey(ServerPlayer player, String questId, String phaseId, String key)
```

后续再做统一 event binding 自动分发。

### 8.6 完成判定顺序

每次 collection entry 变化后：
1. 判 entry 是否 completed
2. 若完成，发 entry reward unlock
3. 重算 category 统计
4. 跑 category completion rules
5. 跑 quest completion rules
6. 若 quest complete，则沿用现有 `QuestState.COMPLETED`

注意：collection quest 的 quest complete 不意味着所有 reward 自动领取，仍需看 reward node grant mode。

