# Quest Journal 任务变化历史记录完整实现蓝图

## 1. 目标

在 `QuestJournalScreen` 内新增 UI 模块：**任务变化历史记录**。该模块可展开面板查看任务变化历史，第一版即包含：完整分类、客户端持久化、过滤、滚动、去重与完整事件接入。

记录范围：任务接取/完成/失败/放弃，phase 新增/切换/推进/完成，objective 进度/完成，collection entry 发现/完成，collection reward 解锁/领取，collection category/quest 完成。

---

## 2. 新增文件

建议新增包：

```text
src/main/java/org/arcadia/arc_quest/client/hud/quest/journal/history/
```

文件清单：

```text
QuestChangeHistoryCategory.java
QuestChangeHistoryType.java
QuestChangeHistoryEntry.java
QuestChangeHistoryFilters.java
QuestChangeHistoryFormatter.java
QuestChangeHistoryPersistence.java
QuestChangeHistoryStore.java
QuestChangeHistoryPanel.java
```

修改文件：

```text
src/main/java/org/arcadia/arc_quest/client/hud/quest/journal/QuestJournalScreen.java
src/main/java/org/arcadia/arc_quest/quest/network/ClientQuestCache.java
```

---

## 3. 分类与类型

### 3.1 Category

```java
public enum QuestChangeHistoryCategory {
    QUEST,
    PHASE,
    OBJECTIVE,
    COLLECTION,
    REWARD,
    SYSTEM
}
```

### 3.2 Type

```java
public enum QuestChangeHistoryType {
    QUEST_ACCEPTED(QuestChangeHistoryCategory.QUEST, 0x4FC3F7),
    QUEST_COMPLETED(QuestChangeHistoryCategory.QUEST, 0x66FF66),
    QUEST_FAILED(QuestChangeHistoryCategory.QUEST, 0xFF6666),
    QUEST_ABANDONED(QuestChangeHistoryCategory.QUEST, 0xFFAA66),

    PHASE_ADDED(QuestChangeHistoryCategory.PHASE, 0x8CD8FF),
    PHASE_SWITCHED(QuestChangeHistoryCategory.PHASE, 0xA98BFF),
    PHASE_ADVANCED(QuestChangeHistoryCategory.PHASE, 0xFFD166),
    PHASE_COMPLETED(QuestChangeHistoryCategory.PHASE, 0x66FF66),

    OBJECTIVE_PROGRESS(QuestChangeHistoryCategory.OBJECTIVE, 0x88DDFF),
    OBJECTIVE_COMPLETED(QuestChangeHistoryCategory.OBJECTIVE, 0x66FF66),

    COLLECTION_ENTRY_DISCOVERED(QuestChangeHistoryCategory.COLLECTION, 0xA98BFF),
    COLLECTION_ENTRY_COMPLETED(QuestChangeHistoryCategory.COLLECTION, 0x7CFFB2),
    COLLECTION_CATEGORY_COMPLETED(QuestChangeHistoryCategory.COLLECTION, 0x66FF88),
    COLLECTION_QUEST_COMPLETED(QuestChangeHistoryCategory.COLLECTION, 0x66FF66),

    COLLECTION_REWARD_UNLOCKED(QuestChangeHistoryCategory.REWARD, 0xFFD166),
    COLLECTION_REWARD_CLAIMED(QuestChangeHistoryCategory.REWARD, 0xFFE6A3),

    SYSTEM_SYNC(QuestChangeHistoryCategory.SYSTEM, 0x90A4AE);
}
```

每个 type 暴露：`category()`、`accentColor()`、`displayName()`。

---

## 4. Entry 数据模型

`QuestChangeHistoryEntry` 使用普通 class，便于 Gson 序列化。

字段：

```java
public final class QuestChangeHistoryEntry {
    public String id;
    public long timeMs;
    public String worldKey;
    public String playerKey;

    public String questId;
    public String questName;
    public String phaseId;
    public String phaseName;
    public String objectiveId;
    public String rewardId;
    public String categoryId;

    public QuestChangeHistoryType type;
    public QuestChangeHistoryCategory category;

    public String title;
    public String detail;
    public String beforeValue;
    public String afterValue;

    public int themeColor;
    public int sortPriority;
}
```

说明：

- `questName`、`phaseName` 是显示名快照，避免后续 registry 改名导致历史显示变化。
- `id` 用于去重，建议由 `timeMs + type + questId + phaseId + objectiveId + rewardId + afterValue` 生成。
- `beforeValue` / `afterValue` 用于 objective progress，例如 `1/5 -> 2/5`。

---

## 5. 持久化设计

### 5.1 文件位置

第一版使用单文件：

```text
config/arc_quest_change_history.json
```

entry 内保留 `worldKey` 和 `playerKey`，后续可平滑升级到多存档/多玩家文件：

```text
config/arc_quest/history/<worldHash>/<playerUuid>.json
```

### 5.2 JSON 根结构

```java
public final class HistoryFile {
    public int schemaVersion = 1;
    public int maxEntries = 500;
    public List<QuestChangeHistoryEntry> entries = new ArrayList<>();
}
```

### 5.3 保存策略

- `QuestChangeHistoryStore.add(...)` 后标记 dirty。
- 使用 500ms debounce 异步保存。
- 保存前复制 snapshot，异步线程不直接访问 mutable list。
- `QuestJournalScreen.removed()` 或 `onClose()` 主动 flush。
- JSON 损坏时不崩溃，备份为 `.broken` 后重建空历史。

---

## 6. Store 设计

`QuestChangeHistoryStore` 是单例。

核心职责：

- 懒加载持久化数据。
- 添加记录。
- 去重。
- 查询过滤。
- 异步保存。
- 提供事件快捷方法。

API：

```java
public final class QuestChangeHistoryStore {
    public static final QuestChangeHistoryStore INSTANCE = new QuestChangeHistoryStore();

    public void ensureLoaded();
    public void flush();
    public void clear();
    public void add(QuestChangeHistoryEntry entry);
    public List<QuestChangeHistoryEntry> query(QuestChangeHistoryFilters filters);

    public void recordQuestAccepted(String questId);
    public void recordQuestCompleted(String questId);
    public void recordQuestFailed(String questId);
    public void recordQuestAbandoned(String questId);

    public void recordPhaseAdded(String questId, String phaseId);
    public void recordPhaseSwitched(String questId, String oldPhaseId, String newPhaseId);
    public void recordPhaseAdvanced(String questId, String oldPhaseId, String newPhaseId);
    public void recordPhaseCompleted(String questId, String phaseId);

    public void recordObjectiveProgress(String questId, String phaseId, int index, int oldValue, int newValue, int required);
    public void recordObjectiveCompleted(String questId, String phaseId, int index, int required);

    public void recordCollectionEntryDiscovered(String questId, String phaseId);
    public void recordCollectionEntryCompleted(String questId, String phaseId);
    public void recordCollectionCategoryCompleted(String questId, String categoryId);
    public void recordCollectionQuestCompleted(String questId);
    public void recordCollectionRewardUnlocked(String questId, String rewardId);
    public void recordCollectionRewardClaimed(String questId, String rewardId);
}
```

### 6.1 去重规则

使用 key：

```text
type | questId | phaseId | objectiveId | rewardId | categoryId | afterValue
```

时间窗口：

```java
DEDUP_WINDOW_MS = 1200L
```

1.2 秒内相同 key 忽略。

objective progress 不合并，保留每次增长；但只记录 `newProgress > oldProgress`。

### 6.2 容量

默认保留最近 500 条，最新在前。

```java
while (entries.size() > maxEntries) entries.remove(entries.size() - 1);
```

---

## 7. Formatter 设计

`QuestChangeHistoryFormatter` 负责：

- 格式化时间：`HH:mm:ss`
- 生成 title/detail
- 解析 quest / phase / objective 显示名

解析来源：

- Quest：`ClientQuestCache.INSTANCE.getQuestDisplayName(questId)`
- Phase：`ClientQuestCache.INSTANCE.getPhaseDisplayName(questId, phaseId)`
- Objective：`QuestRegistry -> QuestDefinition -> PhaseDefinition -> objectives[index]`
- Reward：第一版显示 reward node id

---

## 8. Filters 设计

```java
public final class QuestChangeHistoryFilters {
    public String questId;
    public QuestChangeHistoryCategory category;
    public QuestChangeHistoryType type;
    public int limit = 200;
}
```

UI 层负责根据按钮状态填充 filters。

---

## 9. ClientQuestCache 接入

修改：

```text
src/main/java/org/arcadia/arc_quest/quest/network/ClientQuestCache.java
```

### 9.1 Quest 状态

在 `updateQuest(QuestRuntimeData data)` 中，基于 old/new 差异记录。

- `oldState == null`：`recordQuestAccepted`
- `oldState != COMPLETED && data.state == COMPLETED`：`recordQuestCompleted`
- `oldState != FAILED && data.state == FAILED`：`recordQuestFailed`

不要在首次 `applyFullSync` 中批量记录。

### 9.2 Phase 差异

在 `updateQuest` 中比较：

```java
beforeActive = previousData.getActivePhaseIds()
afterActive = data.getActivePhaseIds()
beforeCompleted = previousData.getCompletedPhaseIds()
afterCompleted = data.getCompletedPhaseIds()
oldCurrent = previousData.getCurrentPhaseId()
newCurrent = data.getCurrentPhaseId()
```

规则：

- `afterActive - beforeActive`：`PHASE_ADDED`
- `afterCompleted - beforeCompleted`：`PHASE_COMPLETED`
- `oldCurrent != newCurrent`：`PHASE_SWITCHED`
- 如果 oldCurrent 完成且 newCurrent 新增，可额外记录 `PHASE_ADVANCED`

### 9.3 Objective 进度

两个 overload 都接入：

```java
updateObjectiveProgress(String questId, int objIndex, int newProgress)
updateObjectiveProgress(String questId, String phaseId, int objIndex, int newProgress)
```

当 `newProgress > oldProgress`：

- 记录 `OBJECTIVE_PROGRESS`
- 若 `oldProgress < required && newProgress >= required`，记录 `OBJECTIVE_COMPLETED`

`required` 通过 `QuestRegistry` 解析；解析失败则只记录 progress，不记录 completed。

### 9.4 Collection 事件

在 `maybeShowCollectionToasts(...)` 中，已有 before/after set 对比，可直接接入：

- `after.discoveredPhaseIds - before.discoveredPhaseIds`：`COLLECTION_ENTRY_DISCOVERED`
- `newData.completedPhaseIds - previousData.completedPhaseIds`：`COLLECTION_ENTRY_COMPLETED`
- `after.unlockedRewardIds - before.unlockedRewardIds`：`COLLECTION_REWARD_UNLOCKED`
- `after.claimedRewardIds - before.claimedRewardIds`：`COLLECTION_REWARD_CLAIMED`

category completed：entry completed 后根据 `CollectionCategoryStateResolver.snapshots(...)` 判断分类是否 newly completed。

quest completed：当 collection completed count 从 `< total` 到 `>= total`，记录 `COLLECTION_QUEST_COMPLETED`。

---

## 10. UI Panel 设计

新增：

```text
QuestChangeHistoryPanel.java
```

### 10.1 状态

```java
private boolean expanded;
private float reveal;
private float scroll;
private float targetScroll;
private boolean currentQuestOnly;
private QuestChangeHistoryCategory selectedCategory;
```

### 10.2 折叠入口

Journal 右下角或右侧显示：

```text
// CHANGE LOG
```

点击展开/收起。

### 10.3 展开布局

推荐浮动 panel：

```text
// QUEST CHANGE LOG
[ALL] [CURRENT]
[QUEST] [PHASE] [OBJ] [COLL] [REWARD]

18:42:10 QUEST ACCEPTED
         Ancient Archive
18:43:02 PHASE COMPLETED
         Entry: Ancient Shard
```

建议尺寸：

```java
PANEL_W = 270;
PANEL_H = 280;
```

放置：优先右下角，避免遮挡主 detail 内容。

### 10.4 渲染风格

复用：

- `HudAnimUtil`
- `HudRenderUtil`
- cyber edge
- 半透明黑底
- theme/accent 色条
- 小字号机能风标题

### 10.5 交互

- 折叠状态只消费按钮点击。
- 展开状态消费 panel 内点击和滚轮。
- ALL/CURRENT 切换当前任务过滤。
- category tab 切换分类过滤；再次点击取消分类。
- 滚轮只在鼠标位于 panel 内时生效。

---

## 11. QuestJournalScreen 接入

字段：

```java
private final QuestChangeHistoryPanel changeHistoryPanel = new QuestChangeHistoryPanel(this);
```

render 末尾：

```java
changeHistoryPanel.render(g, mouseX, mouseY, partialTick);
```

mouseClicked 开头或主 UI 后：

```java
if (changeHistoryPanel.mouseClicked(mouseX, mouseY, button)) return true;
```

mouseScrolled：

```java
if (changeHistoryPanel.mouseScrolled(mouseX, mouseY, delta)) return true;
```

关闭时：

```java
QuestChangeHistoryStore.INSTANCE.flush();
```

Panel 获取当前任务：

```java
screen.getSelectedIndex()
screen.getCurrentEntries()
```

---

## 12. Full Sync 与时序规则

- 首次 `applyFullSync` 不记录历史，避免进世界瞬间刷屏。
- 后续事件优先由 `updateQuest`、`updateObjectiveProgress`、`maybeShowCollectionToasts` 记录。
- 所有记录必须基于 old/new 差异。
- Toast 和 History 并列触发，但互不依赖。
- UI panel 只读 Store，不反向修改任务状态。

---

## 13. 持久化容错

`QuestChangeHistoryPersistence` 需要处理：

- 文件不存在：返回空历史。
- JSON 损坏：重命名为 `arc_quest_change_history.json.broken`，返回空历史。
- 保存失败：吞掉异常并 log warn，不崩溃。
- schemaVersion 不匹配：保守迁移或清空。

---

## 14. 实施步骤

### 阶段 A：数据与持久化

实现：

- category/type/entry/filters/formatter
- persistence load/save
- store add/query/dedup/flush

验证：启动游戏后能生成/读取 json。

### 阶段 B：事件接入

修改 `ClientQuestCache`：

- quest 状态
- phase 差异
- objective 进度
- collection 差异

验证：日志或 debug 能看到记录增长。

### 阶段 C：Journal UI

实现 `QuestChangeHistoryPanel` 并接入 `QuestJournalScreen`。

验证：

- 展开/收起
- ALL/CURRENT
- 分类过滤
- 滚动
- 空状态

### 阶段 D：完整验证

运行：

```bash
.\gradlew.bat compileJava --console=plain
```

手测：

1. 接任务，历史出现 `QUEST_ACCEPTED`。
2. 推进 phase，历史出现 `PHASE_*`。
3. 推进 objective，历史出现 progress/completed。
4. 完成 collection entry，历史出现 collection entry completed。
5. 解锁/领取 reward，历史出现 reward unlocked/claimed。
6. 关闭并重新打开客户端，历史仍存在。
7. 当前任务过滤只显示当前选中任务。
8. 分类过滤正确。

---

## 15. 验收标准

- `QuestJournalScreen` 内存在可展开的 `CHANGE LOG` panel。
- 历史记录持久化到 `config/arc_quest_change_history.json`。
- 重启后历史可恢复。
- Quest/Phase/Objective/Collection/Reward 分类齐全。
- 支持全部/当前任务过滤。
- 支持分类过滤与滚动。
- 首次 full sync 不刷历史。
- 重复同步不会产生大量重复记录。
- 编译通过。
- 不影响 tracker、toast、reward claim、journal 原有交互。

---

## 16. 后续增强

- 搜索框。
- 点击历史跳转到对应 quest/phase/card。
- 按日期分组。
- 多存档/多服务器/多玩家分文件。
- 服务端权威历史同步。
- 历史导出。
- 明确接入任务放弃网络事件。
