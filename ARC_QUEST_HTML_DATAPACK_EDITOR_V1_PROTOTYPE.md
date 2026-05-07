# Arc Quest HTML 数据包编辑器 v1 产品原型文档

## 1. 目标

这是一个面向 `Arc Quest` quest/datapack JSON 的可视化 HTML 编辑器原型，目标是：

- 降低手写 JSON 门槛
- 支持导入、编辑、校验、预览、导出
- 支持简单节点预览图
- 为后续 collection quest、dialogue、marker 编辑打基础

---

## 2. 产品定位

- 形态：单文件 HTML 工具
- 使用方式：浏览器直接打开
- 输入：导入 quest JSON
- 输出：导出规范化 JSON
- 用户：内容作者、调试者、系统维护者

---

## 3. v1 范围

### 包含
- 导入 JSON
- Quest 基础信息编辑
- VisualConfig 编辑
- Phase 编辑
- Objective 编辑
- Reward 编辑
- JSON 实时预览
- 结构校验
- 导出 JSON
- 简单节点预览图

### 不包含
- 完整对话树图编辑
- marker 地图可视化
- 多 quest 工程工作区
- 自动扫描本地资源目录

---

## 4. 整体布局

采用顶部工具栏 + 左树 + 中间编辑区 + 右侧辅助面板。

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│ Arc Quest Datapack Visual Editor                                           │
│ [新建] [导入JSON] [导出JSON] [格式化] [校验] [模板] [撤销] [重做] [帮助]     │
├───────────────┬───────────────────────────────────────┬─────────────────────┤
│ 结构导航树    │ 编辑区                                │ 右侧面板            │
│ Quest         │ 当前节点表单                          │ [预览][JSON][校验]  │
│ ├─ 基础信息   │ id / title / description / ...        │ [帮助][节点图]      │
│ ├─ Visual     │                                       │                     │
│ ├─ Rewards    │                                       │                     │
│ ├─ Phases     │                                       │                     │
│ │  ├─ phase1  │                                       │                     │
│ │  │  ├─ obj1 │                                       │                     │
│ │  │  └─ obj2 │                                       │                     │
│ │  └─ phase2  │                                       │                     │
│ └─ Raw JSON   │                                       │                     │
├───────────────┴───────────────────────────────────────┴─────────────────────┤
│ 状态栏：文件名 / dirty / 校验结果 / schema版本 / 当前选中路径               │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. 顶部工具栏

- 新建
- 导入 JSON
- 导出 JSON
- 格式化
- 校验
- 模板
- 撤销
- 重做
- 帮助

### 新建模板建议
- 空白 Quest
- Epic Prologue 模板
- Collection Codex 模板
- 对话任务模板
- 收集任务模板

---

## 6. 左侧结构导航树

```text
Quest
├─ 基础信息
├─ VisualConfig
├─ Rewards
├─ Phases
│  ├─ Phase: intro
│  │  ├─ Objectives
│  │  │  ├─ Objective 1
│  │  │  └─ Objective 2
│  │  └─ Rewards
│  └─ Phase: ending
└─ Raw JSON
```

### 节点交互
- 点击：切换中间表单
- 操作：新增、复制、删除、上移、下移、克隆
- error 节点显示红点
- warning 节点显示黄点

---

## 7. 页面一：Quest 基础信息

```text
┌─────────────────────────────────────────────────────────────┐
│ Quest 基础信息                                              │
├─────────────────────────────────────────────────────────────┤
│ Quest ID            [ epic_prologue                      ]  │
│ 标题 Key            [ arc_quest.quest.epic_prologue...   ]  │
│ 描述 Key            [ arc_quest.quest.epic_prologue...   ]  │
│ 排序                [ 0 ]                                  │
│ 可重复              [开关]                                  │
│ 标签                [ main ] [ prologue ] [+添加]          │
│ 启动条件            [ 简化卡片 / 高级JSON编辑 ]             │
│ Quest级奖励         [进入 Reward 编辑]                      │
└─────────────────────────────────────────────────────────────┘
```

字段：`id`、`title`、`description`、`sortOrder`、`repeatable`、`tags`、`startConditions`、`rewards`。

---

## 8. 页面二：VisualConfig

```text
┌─────────────────────────────────────────────────────────────┐
│ VisualConfig                                                │
├─────────────────────────────────────────────────────────────┤
│ ThemeColor           [ #FFAA33 ] [颜色选择器]               │
│ Icons               [列表 + 新增/编辑/删除]                 │
│ Splashes            [ QUEST_ACQUIRED / QUEST_COMPLETED ... ]│
│                    [ texture / scale / tint 摘要 ]          │
└─────────────────────────────────────────────────────────────┘
```

推荐 splash 类型：
- `QUEST_ACQUIRED`
- `QUEST_COMPLETED`
- `PHASE_START`
- `PHASE_COMPLETE`

---

## 9. 页面三：Phase 列表

```text
┌─────────────────────────────────────────────────────────────┐
│ Phases                                                      │
├─────────────────────────────────────────────────────────────┤
│ [ + 新增 Phase ]                                            │
│ [ intro ]        objectives: 2 rewards: 1  [编辑][复制][删] │
│ [ combat_test ]  objectives: 3 rewards: 0  [编辑][复制][删] │
└─────────────────────────────────────────────────────────────┘
```

支持：新增、复制、删除、上移、下移。

---

## 10. 页面四：单个 Phase 编辑

```text
┌─────────────────────────────────────────────────────────────┐
│ Phase 编辑：intro                                           │
├─────────────────────────────────────────────────────────────┤
│ Phase ID             [ intro ]                              │
│ 标题 Key             [ arc_quest.phase.epic_prologue... ]   │
│ 描述 Key             [ arc_quest.phase.epic_prologue... ]   │
│ 自动开始             [开关]                                  │
│ Objectives           [列表 + 新增]                           │
│ Phase Rewards        [进入 Reward 编辑]                      │
└─────────────────────────────────────────────────────────────┘
```

---

## 11. 页面五：Objective 编辑

```text
┌─────────────────────────────────────────────────────────────┐
│ Objective 编辑                                              │
├─────────────────────────────────────────────────────────────┤
│ 类型 Type            [ kill ▼ ]                             │
│ ID / 别名            [ kill_zombie ]                        │
│ 文案 Key             [ arc_quest.objective.... ]            │
│ 目标数量             [ 1 ]                                  │
│ 完成后隐藏           [开关]                                  │
│                                                               │
│ 类型专属字段                                                 │
│ entityType          [ minecraft:zombie ]                    │
│ count               [ 1 ]                                   │
└─────────────────────────────────────────────────────────────┘
```

v1 类型建议：`kill`、`collect`、`talk`、`reach`、`interact`、`submit`、`custom_counter`。

---

## 12. 页面六：Reward 编辑

```text
┌─────────────────────────────────────────────────────────────┐
│ Rewards                                                     │
├─────────────────────────────────────────────────────────────┤
│ [ + 新增 Reward ]                                           │
│ [ item ]    minecraft:iron_ingot x3       [编辑][复制][删]  │
│ [ command ] give @s minecraft:diamond 1   [编辑][复制][删]  │
└─────────────────────────────────────────────────────────────┘
```

reward 类型建议：`item`、`command`、`xp`、`message`、`unlock`、`quest_chain`。

---

## 13. 页面七：Raw JSON

```text
┌─────────────────────────────────────────────────────────────┐
│ Raw JSON                                                    │
├─────────────────────────────────────────────────────────────┤
│ {                                                           │
│   "id": "epic_prologue",                                  │
│   ...                                                       │
│ }                                                           │
│ [复制JSON] [重新格式化] [从JSON覆盖当前状态]                 │
└─────────────────────────────────────────────────────────────┘
```

默认只读，高级模式可编辑。

---

## 14. 右侧辅助面板

右侧 Tabs：
- 预览
- JSON
- 校验
- 帮助
- 节点图

---

## 15. 节点预览图设计

这个模块参考：
- `src/main/java/org/arcadia/arc_quest/client/hud/quest/history/QuestHistoryPanel.java`
- `src/main/java/org/arcadia/arc_quest/client/hud/quest/history/CollectionHistoryPanel.java`

### 15.1 参考抽象

从 `QuestHistoryPanel` 提炼：
- phase 节点图结构
- 当前节点高亮
- 简洁连接线
- focus / 结构感

从 `CollectionHistoryPanel` 提炼：
- 深色 HUD 面板风格
- 顶部状态信息条
- 卡片式信息组织
- hover 高亮与状态文案

### 15.2 v1 目标

v1 不做复杂可编辑流程图，只做“结构预览图”：
- 左到右展示 phase 主流程
- 每个 phase 一个节点卡片
- 节点下显示 objective / reward 摘要
- 点击节点切换编辑对象
- 错误节点高亮

### 15.3 节点图线框图

```text
┌─────────────────────────────────────────────────────────────┐
│ 节点图预览                                                   │
├─────────────────────────────────────────────────────────────┤
│ 状态栏：QUEST // FLOW PREVIEW                               │
│                                                             │
│   [Start]───[Phase:intro]───[Phase:combat]───[Phase:ending] │
│                │                  │                         │
│                └ obj:2 rw:1       └ obj:3 rw:0             │
│                                                             │
│ 当前选中：Phase:intro                                       │
│ - titleKey: ...                                             │
│ - objectives: 2                                             │
│ - rewards: 1                                                │
│                                                             │
│ 图例： ○ 普通   ● 当前编辑   ◎ 有错误   ◆ 有奖励             │
└─────────────────────────────────────────────────────────────┘
```

### 15.4 phase 节点样式

- 标题：phase id
- 副信息：`obj: x`、`rw: y`
- 普通：灰蓝边框
- 当前选中：主题色高亮
- error：红色边框
- warning：橙色边框

### 15.5 objective 预览策略

v1 不把所有 objective 单独画成复杂子节点，采用聚合摘要：
- `obj: 3`
- `types: talk / kill / collect`

选中 phase 后，在节点图下方或右侧详情区显示 objective 简版列表。

### 15.6 collection quest 预览策略

collection quest 不使用横向 phase 流程图，而采用纵向分类卡片流：

```text
┌─────────────────────────────────────────────────────────────┐
│ COLLECTION PREVIEW                                          │
├─────────────────────────────────────────────────────────────┤
│ [Category: Undead]     progress 3/8                         │
│   - Zombie entry                                            │
│   - Skeleton entry                                          │
│                                                             │
│ [Category: Nether]     progress 1/5                         │
│   - Blaze entry                                             │
│                                                             │
│ Claimable Rewards: 2                                        │
└─────────────────────────────────────────────────────────────┘
```

### 15.7 节点图交互

- 点击节点：切换左树与中间表单
- hover 节点：显示 tooltip
- 点击错误节点：同步跳到校验项
- phase 顺序变化后，连接线实时更新

### 15.8 实现建议

v1 节点图建议使用 HTML + SVG：
- SVG 画连接线
- HTML 卡片画节点
- CSS class 控制 hover / 选中 / 错误状态

---

## 16. 校验系统

### Error
- quest id 为空
- phase 数组为空
- phase id 重复
- objective 类型为空
- objective 缺关键字段
- 无法导出合法 JSON

### Warning
- quest title / description 缺失
- 未配置关键 splash
- reward 为空
- objective 文案 key 缺失
- translation key 命名不规范

### Info
- themeColor 未配置
- 建议配置 icon
- 某 phase 无奖励

点击校验项后，需要同时联动：
- 左树定位
- 中间表单切换
- 节点图高亮对应节点

---

## 17. 自动建议规则

### translation key
- quest title：`arc_quest.quest.{questId}.title`
- quest description：`arc_quest.quest.{questId}.description`
- phase title：`arc_quest.phase.{questId}.{phaseId}`
- objective text：`arc_quest.objective.{questId}.{phaseId}.{index}`

### 默认 id
- phase：`phase_1`, `phase_2`
- kill objective：`kill_target_1`
- collect objective：`collect_item_1`
- talk objective：`talk_npc_1`

---

## 18. 视觉风格

建议延续 Arc Quest HUD 的轻科技风：
- 深色背景
- 清晰卡片分区
- 轻微荧光描边
- 节制的科幻感

建议配色：
- 背景：`#0f1115`
- 面板：`#171a21`
- 边框：`#2a3140`
- 主强调色：`#63c7ff`
- warning：`#ffb347`
- error：`#ff6b6b`
- success：`#57d38c`

---

## 19. 技术实现建议

v1 采用单文件 HTML，但代码内部按模块组织：
- state
- renderer
- form builders
- validators
- serializer
- preview renderer
- graph renderer
- history manager

节点图先做静态自动布局：
- phase 顺序横向排列
- reward / objective 只做摘要
- collection quest 使用纵向卡片流

---

## 20. 下一步建议

基于本文档，后续最合适的两条路径：

1. 继续输出“字段清单与交互规格文档”
2. 直接开始做 `arc_quest_datapack_editor.html` 第一版原型

如果马上实现，建议优先做：
- 三栏布局
- 导入/导出
- Quest / Phase / Objective / VisualConfig 基础表单
- 节点图 tab
