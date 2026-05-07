# Arc Quest HTML 数据包编辑器 v1 字段清单与交互规格文档

## 1. 文档用途

本文档是 `ARC_QUEST_HTML_DATAPACK_EDITOR_V1_PROTOTYPE.md` 的实现级补充，重点定义：

- 每个页面/模块需要编辑哪些字段
- 每个字段的输入控件、默认值、校验规则
- 跨模块联动行为
- 节点图预览如何随数据变化刷新

目标是让后续实现 `arc_quest_datapack_editor.html` 时，可以直接按本文档落地。

---

## 2. 编辑器内部状态模型

建议 v1 使用统一状态树：

```js
editorState = {
  meta: {
    fileName: "epic_prologue.json",
    dirty: false,
    schemaVersion: "arcquest-v1"
  },
  quest: {
    // 当前 quest JSON 对象
  },
  ui: {
    selectedPath: ["phases", 0],
    activeRightTab: "preview",
    expandedTree: {},
    graphFocusId: null
  },
  diagnostics: [],
  history: []
}
```

---

## 3. 字段规范说明

### 3.1 字段表说明

每个字段表使用以下列：

- `字段`：JSON key
- `说明`：业务含义
- `控件`：UI 输入控件类型
- `默认值`：新建时给出的值
- `必填`：是/否
- `校验`：输入与结构约束
- `联动`：对其他区域的影响

### 3.2 控件类型约定

- `text`：单行文本
- `textarea`：多行文本
- `number`：数字输入
- `switch`：布尔开关
- `select`：下拉选择
- `chips`：标签列表
- `list`：数组列表编辑器
- `json`：高级 JSON 文本框
- `color`：颜色选择器

---

## 4. Quest 基础信息字段清单

| 字段 | 说明 | 控件 | 默认值 | 必填 | 校验 | 联动 |
|---|---|---|---|---|---|---|
| `id` | quest 唯一 ID | text | `new_quest` | 是 | 非空；建议小写+下划线；不重复 | 影响文件名建议、translation key 推荐、节点图标题 |
| `title` | quest 标题 key | text | 自动推荐 | 否 | 建议非空 | 影响右侧预览标题 |
| `description` | quest 描述 key | textarea | 自动推荐 | 否 | 建议非空 | 影响右侧预览描述 |
| `sortOrder` | 排序值 | number | `0` | 否 | 整数 | 无 |
| `repeatable` | 是否可重复 | switch | `false` | 否 | 布尔 | 影响预览摘要 |
| `tags` | quest 标签 | chips | `[]` | 否 | 字符串数组 | 影响筛选与模板分类 |
| `startConditions` | 启动条件 | list/json | `[]` | 否 | 结构合法 | 影响校验 |
| `rewards` | quest 级奖励 | list | `[]` | 否 | reward 结构合法 | 影响 reward 计数与节点摘要 |

### 4.1 交互规格

1. 修改 `id` 时，若 `title/description` 仍为自动生成值，弹出提示：是否同步更新推荐 key。
2. `id` 为空时：
   - 顶层 quest 节点标红
   - 禁止导出
3. `tags` 使用 chips 输入：回车新增，点 `x` 删除。
4. `startConditions` v1 允许简化卡片编辑，也允许切换到高级 JSON 模式。

---

## 5. VisualConfig 字段清单

### 5.1 顶层字段

| 字段 | 说明 | 控件 | 默认值 | 必填 | 校验 | 联动 |
|---|---|---|---|---|---|---|
| `themeColor` | quest 主题色 | color/text | `#63c7ff` | 否 | 颜色值合法 | 影响节点图高亮色、右侧预览主题 |
| `icons` | 图标映射 | list | `{}` | 否 | key/value 合法 | 影响预览摘要 |
| `splashes` | splash 配置映射 | list | `{}` | 否 | 结构合法 | 影响校验与预览 |

### 5.2 Icon 项字段

| 字段 | 说明 | 控件 | 默认值 | 必填 | 校验 |
|---|---|---|---|---|---|
| `type` | 图标用途，如 `questIcon` | text/select | 空 | 是 | 不为空 |
| `texture` | 资源路径 | text | 空 | 是 | 形如 `namespace:path` |

### 5.3 Splash 项字段

| 字段 | 说明 | 控件 | 默认值 | 必填 | 校验 | 联动 |
|---|---|---|---|---|---|---|
| `eventType` | 触发事件 | select | `QUEST_ACQUIRED` | 是 | 必须在支持枚举中 | 影响预览卡片标题 |
| `texture` | 贴图路径 | text | 空 | 是 | `namespace:path` | 影响预览摘要 |
| `scale` | 缩放 | number | `1.0` | 否 | 大于 0 | 影响预览显示 |
| `tint` | 色调 | color/text | `#FFFFFF` | 否 | 颜色值合法 | 影响预览显示 |
| `duration` | 展示时长 | number | 空 | 否 | 大于 0 | 预留 |
| `subtitle` | 副标题 key | text | 空 | 否 | 任意字符串 | 预留 |

### 5.4 VisualConfig 交互规格

1. v1 的 splash 类型下拉预置：
   - `QUEST_ACQUIRED`
   - `QUEST_COMPLETED`
   - `PHASE_START`
   - `PHASE_COMPLETE`
2. 若缺少 `QUEST_ACQUIRED` 或 `QUEST_COMPLETED`：给 warning，不禁止导出。
3. `themeColor` 修改后：
   - 节点图选中边框即时更新
   - 右侧预览头条颜色即时更新
4. `texture` 为空：该 splash 项标红。

---

## 6. Phase 列表与 Phase 字段清单

### 6.1 Phase 列表项摘要字段

| 字段 | 说明 | 来源 |
|---|---|---|
| `id` | phase ID | phase 对象 |
| `objectiveCount` | objective 数量 | `objectives.length` |
| `rewardCount` | reward 数量 | `rewards.length` |
| `warningCount` | warning 数量 | 诊断系统 |
| `errorCount` | error 数量 | 诊断系统 |

### 6.2 单个 Phase 字段

| 字段 | 说明 | 控件 | 默认值 | 必填 | 校验 | 联动 |
|---|---|---|---|---|---|---|
| `id` | phase 唯一 ID | text | `phase_1` | 是 | 非空；同 quest 内不重复 | 影响左树显示、节点图节点名、translation key 推荐 |
| `title` | phase 标题 key | text | 自动推荐 | 否 | 建议非空 | 影响节点 tooltip |
| `description` | phase 描述 key | textarea | 自动推荐 | 否 | 无 | 影响详情预览 |
| `autoStart` | 是否自动开始 | switch | `false` | 否 | 布尔 | 影响 phase 摘要 |
| `objectives` | objective 列表 | list | `[]` | 建议是 | 至少建议 1 个 | 影响节点图 obj 数量 |
| `rewards` | phase 奖励列表 | list | `[]` | 否 | reward 结构合法 | 影响节点图 rw 数量 |
| `startConditions` | phase 启动条件 | list/json | `[]` | 否 | 合法结构 | 影响校验 |
| `completeConditions` | phase 完成条件 | list/json | `[]` | 否 | 合法结构 | 影响校验 |

### 6.3 Phase 交互规格

1. 新增 phase：
   - 自动分配 `phase_1`, `phase_2`...
   - 自动添加空 `objectives` 数组
2. 删除 phase：
   - 二次确认
   - 删除后左树焦点回到 `Phases`
3. phase 排序变化：
   - 左树顺序更新
   - 节点图连线顺序实时更新
4. 修改 `phase.id`：
   - 若 objective 文案 key 使用自动生成值，可提示同步更新推荐 key。

---

## 7. Objective 字段清单

## 7.1 通用字段

| 字段 | 说明 | 控件 | 默认值 | 必填 | 校验 | 联动 |
|---|---|---|---|---|---|---|
| `type` | objective 类型 | select | `kill` | 是 | 必须为支持类型之一 | 决定动态表单内容 |
| `id` | objective 标识 | text | 自动生成 | 否 | 建议唯一 | 影响左树显示 |
| `text` | objective 文案 key | text | 自动推荐 | 否 | 建议非空 | 影响预览与节点摘要 |
| `count` | 目标数量 | number | `1` | 否 | 大于 0 | 影响摘要 |
| `hideWhenCompleted` | 完成后隐藏 | switch | `false` | 否 | 布尔 | 影响预览 |

## 7.2 kill 类型

| 字段 | 说明 | 控件 | 默认值 | 必填 | 校验 |
|---|---|---|---|---|---|
| `entityType` | 击杀实体类型 | text | 空 | 是 | `namespace:path` |
| `count` | 击杀数量 | number | `1` | 是 | > 0 |
| `filter` | 高级过滤 | json | 空 | 否 | JSON 合法 |

## 7.3 collect 类型

| 字段 | 说明 | 控件 | 默认值 | 必填 | 校验 |
|---|---|---|---|---|---|
| `itemId` | 物品 ID | text | 空 | 是 | `namespace:path` |
| `count` | 数量 | number | `1` | 是 | > 0 |
| `consumeOnSubmit` | 提交时是否消耗 | switch | `false` | 否 | 布尔 |

## 7.4 talk 类型

| 字段 | 说明 | 控件 | 默认值 | 必填 | 校验 |
|---|---|---|---|---|---|
| `dialogueId` | 关联对话 ID | text | 空 | 否 | 字符串 |
| `npcId` | 关联 NPC ID | text | 空 | 否 | 字符串 |
| `count` | 触发次数 | number | `1` | 否 | > 0 |

规则：`dialogueId` 与 `npcId` 至少建议填一个。

## 7.5 reach 类型

| 字段 | 说明 | 控件 | 默认值 | 必填 | 校验 |
|---|---|---|---|---|---|
| `x` | 目标 X | number | `0` | 是 | 数字 |
| `y` | 目标 Y | number | `64` | 是 | 数字 |
| `z` | 目标 Z | number | `0` | 是 | 数字 |
| `dimension` | 维度 ID | text | `minecraft:overworld` | 否 | `namespace:path` |
| `radius` | 判定半径 | number | `3` | 否 | > 0 |

## 7.6 interact / submit / custom_counter

v1 可先做简化版本：

| 类型 | 最小字段 |
|---|---|
| `interact` | `targetType`, `targetId`, `count` |
| `submit` | `itemId`, `count`, `consumeOnSubmit` |
| `custom_counter` | `counterId`, `count` |

## 7.7 Objective 交互规格

1. 修改 `type` 时：
   - 若已有专属字段不兼容，提示“切换类型将清理部分字段”。
2. Objective 列表支持：
   - 新增
   - 复制
   - 删除
   - 上移
   - 下移
3. 新增 objective：
   - 先弹出类型选择框
   - 自动生成 `id`
4. 修改 objective 后：
   - phase 节点图摘要即时刷新
   - 右侧预览中的 objective 数量与类型摘要即时刷新

---

## 8. Reward 字段清单

## 8.1 通用字段

| 字段 | 说明 | 控件 | 默认值 | 必填 | 校验 |
|---|---|---|---|---|---|
| `type` | reward 类型 | select | `item` | 是 | 必须为支持类型 |
| `label` | 摘要标签 | text | 空 | 否 | 字符串 |

## 8.2 item reward

| 字段 | 说明 | 控件 | 默认值 | 必填 | 校验 |
|---|---|---|---|---|---|
| `itemId` | 物品 ID | text | 空 | 是 | `namespace:path` |
| `count` | 数量 | number | `1` | 是 | > 0 |

## 8.3 command reward

| 字段 | 说明 | 控件 | 默认值 | 必填 | 校验 |
|---|---|---|---|---|---|
| `command` | 执行命令 | textarea | 空 | 是 | 非空 |

## 8.4 xp / message / unlock / quest_chain

| 类型 | 最小字段 |
|---|---|
| `xp` | `amount` |
| `message` | `text` |
| `unlock` | `unlockId` |
| `quest_chain` | `targetQuestId` |

## 8.5 Reward 交互规格

1. reward 列表支持新增、复制、删除、排序。
2. reward 数量变化后：
   - phase 节点摘要 `rw:x` 即时刷新
   - 右侧预览总奖励数刷新
3. item reward 可显示简短摘要：`minecraft:iron_ingot x3`。

---

## 9. Raw JSON 区域交互规格

| 功能 | 说明 |
|---|---|
| 只读模式 | 默认展示格式化后的 JSON |
| 高级编辑模式 | 允许直接修改 JSON |
| 重新格式化 | 重新输出规范缩进 |
| 从 JSON 覆盖当前状态 | 重新解析并替换 `editorState.quest` |
| 复制 JSON | 复制当前 JSON 到剪贴板 |

### 9.1 约束

- 高级模式解析失败时，不覆盖当前状态。
- 错误提示至少显示：错误原因、行号、列号。

---

## 10. 右侧面板规格

## 10.1 预览 Tab

显示：
- quest 标题
- quest 描述
- themeColor
- splash 配置情况
- phase 数量
- objective 总数
- reward 总数

## 10.2 JSON Tab

显示当前序列化后的 JSON 文本。

## 10.3 校验 Tab

按级别分组：
- Error
- Warning
- Info

每条诊断至少包含：
- 级别
- 文本说明
- 定位路径

## 10.4 帮助 Tab

显示当前字段说明：
- 字段用途
- 是否必填
- 示例值
- 推荐 key 形式

## 10.5 节点图 Tab

显示：
- 主流程 phase 节点图
- 当前选中 phase 的高亮
- objective / reward 摘要
- 错误节点状态

---

## 11. 节点图字段与交互规格

### 11.1 phase 节点渲染字段

| 字段 | 来源 | 用途 |
|---|---|---|
| `phase.id` | phase | 节点标题 |
| `phase.title` | phase | tooltip/详情 |
| `objectiveCount` | `objectives.length` | 节点摘要 |
| `rewardCount` | `rewards.length` | 节点摘要 |
| `diagnosticLevel` | 校验结果 | 边框颜色 |
| `isSelected` | `ui.selectedPath` | 当前高亮 |

### 11.2 collection 预览字段

| 字段 | 说明 |
|---|---|
| `categoryName` | 分类名称 |
| `entryCount` | 条目数量 |
| `completedCount` | 完成数量 |
| `claimableCount` | 可领取奖励数 |

### 11.3 节点图交互

| 操作 | 行为 |
|---|---|
| 点击 phase 节点 | 左树定位到该 phase，中间切换到该 phase 编辑页 |
| hover phase 节点 | 显示 title/objective/reward 摘要 |
| 点击错误节点 | 同步切换到校验 tab 并聚焦该节点 |
| phase 排序变化 | 连线顺序重算 |
| objective/reward 修改 | 节点摘要即时更新 |

### 11.4 节点图颜色规则

| 状态 | 样式 |
|---|---|
| 普通 | 灰蓝描边 |
| 当前选中 | 使用 `themeColor` 强高亮 |
| warning | 橙色描边 |
| error | 红色描边 |
| 含奖励 | 可在角标上显示 `◆` 或 badge |

---

## 12. 自动生成与推荐规则

### 12.1 translation key 推荐

| 场景 | 推荐格式 |
|---|---|
| quest title | `arc_quest.quest.{questId}.title` |
| quest description | `arc_quest.quest.{questId}.description` |
| phase title | `arc_quest.phase.{questId}.{phaseId}` |
| objective text | `arc_quest.objective.{questId}.{phaseId}.{index}` |

### 12.2 默认 ID 规则

| 对象 | 规则 |
|---|---|
| phase | `phase_1`, `phase_2` |
| kill objective | `kill_target_1` |
| collect objective | `collect_item_1` |
| talk objective | `talk_npc_1` |

---

## 13. 校验规则清单

### Error
- quest `id` 为空
- phase 数组为空
- phase `id` 重复
- objective `type` 为空
- objective 缺少类型关键字段
- reward 缺关键字段
- JSON 解析失败

### Warning
- quest `title` / `description` 缺失
- 未配置 `QUEST_ACQUIRED` 或 `QUEST_COMPLETED`
- objective 文案 key 缺失
- phase 无 objective
- reward 为空

### Info
- `themeColor` 未设置
- `tags` 为空
- 建议配置 icon
- 某 phase 无奖励

---

## 14. 全局联动规格

1. **左树 -> 编辑区**
   - 点击任意节点，中间表单切换到对应编辑页。

2. **编辑区 -> 右侧预览**
   - 任意字段修改后，预览、JSON、校验实时刷新。

3. **编辑区 -> 节点图**
   - phase / objective / reward 变化后，节点图摘要即时刷新。

4. **节点图 -> 左树/编辑区**
   - 点击节点，左树同步选中，中间表单同步切换。

5. **校验 -> 定位**
   - 点击校验项，左树、中间、节点图同时聚焦到目标。

6. **Dirty 状态**
   - 任意数据修改后，将 `meta.dirty = true`。
   - 导出成功后可选择清除 dirty。

---

## 15. 实现优先级建议

### P0
- Quest 基础字段
- Phase 列表与 phase 编辑
- Objective 通用字段与 3 个基础类型
- Reward 基础类型
- VisualConfig 的 themeColor + splash
- JSON 导入导出
- 校验
- 基础节点图

### P1
- 更多 objective/reward 类型
- collection quest 纵向预览
- 高级 JSON 编辑
- 节点图 tooltip

### P2
- 拖拽排序
- 缩放/平移节点图
- marker 与 dialogue 扩展

---

## 16. 下一步建议

基于本文档，下一步可以直接开始：

1. 新建 `arc_quest_datapack_editor.html`
2. 先实现：
   - 三栏布局
   - quest/phase/objective/visualConfig 基础表单
   - 右侧 JSON / 校验
   - 节点图 tab
