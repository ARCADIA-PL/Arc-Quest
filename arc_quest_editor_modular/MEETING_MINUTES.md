# Arc Quest Editor — 三次 Agent 研讨会议记录

> NPC & 对话数据包编辑器 — 从设计到终审拍板的全过程记录

---

## 概览

| 会议 | 日期 | 议题数 | 裁决数 | 研讨深度 |
|------|------|--------|--------|----------|
| 第一次 | 2026-05 (第 1-3 轮) | 24 | 24 | 架构层：state 重构、共享注册表、condition 统合、智能补全、跨文件校验 |
| 第二次 | 2026-05 (第 4-8 轮) | 36 | 36 | 实现层：数据绑定架构、JSON 序列化、UI 组件设计、导入导出路由、校验规则、边界条件 |
| 第三次 | 2026-05 (第 9 轮) | 32 | 32 | 终审层：蓝图逐项核对 Java 源码、12 处修正拍板、10 项设计缺口决议、10 项实现细节拍板 |

**总计：92 项议题全部裁决。**

---

# 第一次会议 — 架构层研讨 (24 项裁决)

> 参会方：代码方（Java Spec 作者）、编辑器方（JS 编辑器作者）
> 轮次：第 1-3 轮

---

## 会议背景

编辑器当前是纯 quest 编辑器。需要扩展为多模式编辑器，支持 NPC 和 dialogue 数据包的编辑。在此之前需要先完成基础设施重构。

## 第 1 轮：state 重构设计

### 代码方陈述

- 当前 state 是扁平结构 (`q`, `meta`, `ui`, `diag` 顶层)，多模式扩展时必须分层
- 建议引入 `mode` 字段，将 quest 相关数据收进 `quest` 槽位

### 编辑器方回应

- 同意分层方案，但需要兼容别名过渡
- 提议 `registry` 跨模式共享 quests/dialogues/npcs 数据

### 裁决

| # | 议题 | 裁决 |
|---|------|------|
| 1 | state 分层 | `mode` + `registry` + `quest.q/meta/ui/diag` + `npc: null` + `dialogue: null` |
| 2 | 别名兼容 | 不做兼容别名，直接全局替换 `state.q` → `state.quest.q` |

### 第 1 轮分批替换计划

| 批次 | 文件数 | 验证 |
|------|--------|------|
| 第 1 批 | state.js + validators.js + quest-shape*.js (6 文件) | build |
| 第 2 批 | export/import normalizers + app.js + import-export.js + navigation.js + layout.js (7 文件) | build |
| 第 3 批 | tree/center/side-panel/status/graph renderers + event-bindings.js (6 文件) | build |
| 第 4 批 | bindings/* (6 文件) | build |

---

## 第 2 轮：页面模式切换 + 共享注册表

### 裁决

| # | 议题 | 裁决 |
|---|------|------|
| 3 | mode bar UI | index.html toolbar 下方新增 3 个 tab: `[Quest] [NPC] [Dialogue]` |
| 4 | 渲染路由 | `rerender()` 按 `state.mode` 分发到 `renderQuest()` / `renderNpc()` / `renderDialogue()` |
| 5 | 占位页面 | NPC/对话模式暂时显示"即将推出"占位页 |
| 6 | 注册表模块 | 新建 `core/registry.js`，导出 `initRegistry` / `importToRegistry` / `clearRegistry` |
| 7 | 导入行为 | "导入 JSON" 替换编辑区 + 写 registry；"导入到库" 仅写 registry |
| 8 | 注册表 quest 条目 | 提取摘要（id, category, phases[], flags[全部]）而非存完整 JSON |
| 9 | CSS grid 适配 | `.app` 的 `grid-template-rows` 从 `56px 1fr 32px` 改为 `56px auto 1fr 32px` 容纳 mode bar |

---

## 第 3 轮：condition 全面统合 + 智能补全

### 背景

第三轮研讨核心发现：三大数据包（quest/dialogue/npc）全部使用同一个 Java 类 `org.arcadia.arc_quest.condition.ConditionSpec`，字段全部 camelCase（`questId` / `phaseId` / `flag` / `key` / `op` / `inner` / `conditions`）。编辑器的条件编辑器之前输出 snake_case 字段（`quest_id`），与 Java 不兼容。

### 裁决

| # | 议题 | 裁决 |
|---|------|------|
| 10 | condition 字段命名 | 全部统一为 camelCase（`quest_id`→`questId`, `phase_id`→`phaseId`, `nbt_key`→`nbtKey` 等） |
| 11 | CONDITION_TYPE_OPTIONS | 补全到 30 种，覆盖全部 `ConditionSpec` 支持的 condition 类型 |
| 12 | renderConditionTree 签名 | 简化为 `(bindBase, condition, registry, deletable)` — registry 用于双通道补全 |
| 13 | 智能补全模块 | 新建 `core/suggestions.js` — `getQuestSuggestions` / `getPhaseSuggestions` / `getFlagSuggestions` / `getDialogueSuggestions` |
| 14 | 实时联动 | questId 输入框 `oninput` 时动态更新 phaseId datalist 选项 |
| 15 | setConditionNodeField | 全面支持 30 种 condition 的字段初始化和类型切换清理 |
| 16 | cleanCondition 清洗 | 导出时清洗为 camelCase，不再回退 `always` |
| 17 | import-normalizer 兼容 | `normalizeCondition` 双通道：优先 new format，回退 `convertOldCondition` 兼容旧 JSON |
| 18 | 回滚策略 | `renderConditionTree(bindBase, condition, registry)` — registry 为空时行为与旧版一致 |

---

## 第 3 轮补充：quest JSON condition 格式迁移

### 裁决

| # | 议题 | 裁决 |
|---|------|------|
| 19 | quest JSON 迁移 | 6 个 quest JSON 文件中所有 condition 从旧格式 `{"type":"always"}` 迁移到 `{"condition":"arc_quest:always"}` |
| 20 | 不动字段 | Mark `activateWhen`/`deactivateWhen`（使用 `MarkActivationSpec`）不迁移；Reward/Objective/Mark target 的 `type` 不迁移 |
| 21 | 迁移策略 | 写一次性 Node 脚本迁移全部 6 个文件，执行后删除脚本 |

---

## 第一次会议补充：跨文件校验

### 裁决

| # | 议题 | 裁决 |
|---|------|------|
| 22 | 跨文件校验模块 | 新建 `core/cross-validator.js` — NPC→Dialogue、Dialogue→Quest、Quest cross-condition 三层校验 |
| 23 | 校验 UI | side-panel 新增"跨文件"标签页，按 err/warn/info 三级展示 |
| 24 | 自动执行 | 导入 JSON 后自动执行跨文件校验 |

---

# 第二次会议 — 实现层深度研讨 (36 项裁决)

> 参会方：代码方（NpcSpec / DialogueSpec / DialogueSpecCompiler / NpcBindingRegistry / DialogueSession / DialogueActionExecutor / ConditionalTextEvaluator）
> 编辑方（state.js / setByPath / factories.js / condition-editor.js / chip-editor.js / import-export.js）
> 轮次：第 4-8 轮

---

## 第 4 轮：数据绑定架构

### 议题与裁决

| # | 议题 | 裁决 |
|---|------|------|
| 25 | NPC 绑定前缀 | `npc.entityType`, `npc.bind.N.*`, `npc.interactCond` |
| 26 | Dialogue 绑定前缀 | `diag.id`, `diag.node.N.*`, `diag.node.N.ch.C.*`, `diag.node.N.ch.C.actions.A.*`, `diag.node.N.condText.K.*` |
| 27 | conditionalTexts key 约束 | 最大 64 字符，`[a-zA-Z0-9_-]` 仅允许，禁止 `.` |
| 28 | Click actions 分层 | 独立文件 `npc-click-actions.js` / `dialogue-click-actions.js` |
| 29 | setByPath 拆分 | 新建 `npc-shape.js` (setNpcByPath) + `dialogue-shape.js` (setDialogueByPath)，quest 不动 |
| 30 | event-bindings | 按 mode 路由到不同 setByPath + 不同 click actions |

---

## 第 5 轮：JSON 序列化 — Gson 行为对齐

### 议题与裁决

| # | 议题 | 裁决 |
|---|------|------|
| 31 | 导出清洗 | `cleanEmptyFields()` 递归删除 `""`/`[]`/`{}`/`null`，保留 `0`/`false` |
| 32 | dialogueBinding vs npcBinding | 对话模式只输出 `{npcId, dialogueId}`；NPC 模式只输出 `{bindingId, dialogueId, dialogueIdFromNbt, condition, priority}` |
| 33 | cooldownType | 编辑器统一大写 `NONE`/`SECONDS`/`GAME_DAY`/`GAME_TICK` |
| 34 | customData 值类型 | 编辑器明确区分 number vs string vs boolean 输入 |
| 35 | dialogueDistance | step=0.5, min=1, max=32 |

---

## 第 6 轮：UI 组件设计

### 议题与裁决

| # | 议题 | 裁决 |
|---|------|------|
| 36 | TextSpec 编辑器 | 抽成 `renderTextSpec(bindBase, spec, label)`，mode select + value input + args chip 列表 |
| 37 | 冷却字段组 | 抽成 `renderCooldownGroup(bindBase, obj)`，4 字段一行，3 处复用 |
| 38 | Action 编辑器 | switch 16 种 type，每种对应不同字段组，`data-action-type` 标记 |
| 39 | restoreNodeId | choice 级 + action 级独立输入，不合并 |
| 40 | 节点左栏 | 线性列表，按 nodes 顺序，标注起始节点（★） |
| 41 | conditionalTexts UI | 可折叠卡片 + priority 排序 + sayId 摘要 |
| 42 | soundEvent | 普通 input，placeholder `minecraft:entity.villager.yes` |
| 43 | visualEditor 复用 | 参数化 source 对象，quest/dialogue 共用 |

---

## 第 7 轮：导入/导出路由

### 议题与裁决

| # | 议题 | 裁决 |
|---|------|------|
| 44 | 类型检测 | `detectJsonType(json)` → `'dialogue'` (优先 nodes) / `'npc'` (entityType+bindings) / `'quest'` (phases) / `'unknown'` |
| 45 | 跨模式导入 | 类型不匹配时只入库，不替换编辑区，不切换 mode |
| 46 | 导出文件名 | `{id}_quest.json` / `{entityType}_npc.json` / `{id}_dialogue.json` |
| 47 | 导入到库 | 保持 dataset 标记方案，仅写 registry |
| 48 | 拖拽文字 | 按 mode 动态更新 overlay 文案 |
| 49 | 错误处理 | unknown 类型 → Toast "无法识别 JSON 类型" |

---

## 第 8 轮：校验体系 + 边界条件

### 议题与裁决

| # | 议题 | 裁决 |
|---|------|------|
| 50 | 单文件校验分层 | 新建 `npc-validators.js` + `dialogue-validators.js`，面板"诊断"tab 展示 |
| 51 | NPC 校验规则 | entityType 非空 (ERROR) + dialogueDistance≥1 (WARN) + binding 对话 ID 非空 (ERROR) + condition 类型有效 (WARN) + interactCondition 类型有效 (WARN) |
| 52 | Dialogue 校验规则 | 16 条规则（见蓝图），分 ERROR/WARN 两级 |
| 53 | cross-validator 增强 | dialogue 模式下额外校验内部节点引用 |
| 54 | conditionalTexts priority | 编辑器标注"使用不同 priority 建立确定顺序" |
| 55 | Action type 白名单 | 16 种 type，非白名单 → ERROR；必填字段缺失 → ERROR |
| 56 | conditionalTexts conditions | 原样输出 `ConditionSpec[]`，编译器自行 AND 组合 |
| 57 | conditionalTexts Map key 确定性 | priority 相同时按字典序——建议使用不同 priority |
| 58 | DialogueTextSpec args | 内置 4 种 `player_name`/`npc_name`/`npc_pos`/`npc_display_name` + 自定义字符串 |
| 59 | DialogueActionSpec 字段语义 | `notify_interact.targetId` ≠ `notify_talk.npcId` — 语义不同，独立输入 |
| 60 | Gson 空值行为 | Gson 默认不序列化 null, 会序列化空字符串/空数组/数字 0 |

---

# 第三次会议 — 终审核对与修订拍板 (32 项裁决)

> 参会方：代码方（完整 Java 源码逐行核验）+ 编辑器方（蓝图逐项核实）
> 轮次：第 9 轮
> 议题来源：逐字段比对 Java Spec ↔ 蓝图骨架工厂、逐规则比对 Java Validator ↔ 蓝图校验规则表、逐文件比对已实现编辑器 ↔ 新建设计

---

## 议程 1：骨架工厂字段核对

### 代码方逐项核验

核验方法：打开 `NpcSpec.java` / `DialogueSpec.java` / `DialogueNodeSpec.java` / `DialogueChoiceSpec.java` / `DialogueActionSpec.java` / `ConditionalSaySpec.java` / `NpcBindingSpec.java` / `DialogueTextSpec.java`，逐字段比对蓝图中的骨架工厂代码。

### 发现 1：defaultNpc.mode 默认值错误

蓝图中 `createDialogueSkeleton()` 写的是：

```js
defaultNpc: { mode: 'translatable', value: '', args: [] }
```

但 `DialogueTextSpec` Java 默认值：

```java
public String mode = "literal";
```

| 裁决 # | 议题 | 裁决 |
|--------|------|------|
| 61 | defaultNpc 默认 mode | 改为 `mode: 'literal'`，与 Java 默认值对齐 |

---

### 核实通过的项目

逐字段比对确认以下骨架工厂与 Java Spec 完全对齐（无差异）：

- `createNpcSkeleton` ↔ `NpcSpec.java` — 9/9 字段 ✅
- `createNpcBinding` ↔ `npc.spec.NpcBindingSpec.java` — 5/5 字段 ✅
- `createDialogueSkeleton` ↔ `DialogueSpec.java` — 11/11 字段（含修正后） ✅
- `createDialogueNode` ↔ `DialogueNodeSpec.java` — 12/12 字段 ✅
- `createConditionalSay` ↔ `ConditionalSaySpec.java` — 5/5 字段 ✅
- `createDialogueChoice` ↔ `DialogueChoiceSpec.java` — 12/12 字段 ✅
- `createDialogueAction` ↔ `DialogueActionSpec.java` — 15/15 字段 ✅

---

## 议程 2：校验规则逐条核对

### 核验方法

打开 `DialogueSpecValidator.java` 和 `NpcSpecValidator.java`，逐条检查蓝图校验规则表。

### 发现 2：choiceId 严重级别不一致

蓝图写 `choiceId` 非空 = WARN，但 Java validator 是 ERROR：

```java
// DialogueSpecValidator.java L94-L96
if (choice.choiceId == null || choice.choiceId.isBlank()) {
    report.add(DialogueValidationIssue.Severity.ERROR, prefix + ".choiceId", "Choice id is required");
}
```

| 裁决 # | 议题 | 裁决 |
|--------|------|------|
| 62 | choiceId 校验级别 | 从 WARN 改为 ERROR |

### 发现 3：缺少 action 必填字段校验 (7 条)

蓝图原有校验仅覆盖 `start_quest.questId` 和 `open_trade.shopId`，遗漏 Java validator 中的以下规则：

| Java validator 代码行 | 规则 | Java 级别 |
|----------------------|------|-----------|
| L269-273 | `give_item` 的 `itemId` 非空 | ERROR |
| L262-266 | `notify_talk` 的 `npcId` 非空 | ERROR |
| L268-272 | `notify_interact` 的 `targetId` 非空 | ERROR |
| L274-278 | `run_command` 的 `command` 非空 | ERROR |
| L280-284 | `set_flag` 的 `flagName` 非空 | ERROR |
| L286-290 | `set_variable` 的 `key` 非空 | ERROR |
| L292-296 | `custom` 的 `customTypeId` 非空 | ERROR |

| 裁决 # | 议题 | 裁决 |
|--------|------|------|
| 63 | 补充 7 条 action 必填字段校验 | 全部 ERROR 级别，加入 dialogue-validators.js 规则表 |

### 发现 4：缺少绑定字段校验 (2 条)

Java validator 对 `npcBindings[].npcId` 和 `entityBindings[].entityType` 有空值检查：

```java
// L331-335
if (binding.npcId == null || binding.npcId.isBlank()) {
    report.add(DialogueValidationIssue.Severity.ERROR, prefix + ".npcId", "NPC binding requires npcId");
}
// L337-341
if (binding.entityType == null || binding.entityType.isBlank()) {
    report.add(DialogueValidationIssue.Severity.ERROR, prefix + ".entityType", "Entity binding requires entityType");
}
```

| 裁决 # | 议题 | 裁决 |
|--------|------|------|
| 64 | npcBindings 校验 | `npcBindings[].npcId` 非空 → ERROR |
| 65 | entityBindings 校验 | `entityBindings[].entityType` 非空 → ERROR |

### 发现 5：缺少 TextSpec value 空值检查

Java validator 对 TextSpec 有 value 空值警告：

```java
// L318-320
if (text.value == null || text.value.isBlank()) {
    report.add(DialogueValidationIssue.Severity.WARNING, path + ".value", "Text value is empty");
}
```

| 裁决 # | 议题 | 裁决 |
|--------|------|------|
| 66 | TextSpec value 空值 | Text `value` 为空 → WARN |

### 已通过的校验规则

以下蓝图规则与 Java validator 完全一致：`id` 非空、`nodes` 非空、`startNodeId` 有效、`nodeId` 非空、`autoNextId`/`nextNodeId`/`restoreNodeId` 节点引用有效性、`conditionalTexts.sayId` 非空、action `type` 白名单、`start_quest.questId` 非空、`open_trade.shopId` 非空。✅

---

## 议程 3：蓝图缺失设计点补充

### 发现 6：conditionalTexts 的 conditions[] 渲染未说明

蓝图提到 conditionalTexts 实体使用 `diag.node.N.condText.K.*` 前缀，但 `ConditionalSaySpec.conditions` 是 `List<ConditionSpec>`，需要按索引逐个渲染。

| 裁决 # | 议题 | 裁决 |
|--------|------|------|
| 67 | conditions[] 绑定 | 每个 conditions 元素绑定为 `diag.node.N.condText.K.cond.0`, `diag.node.N.condText.K.cond.1`, ... — 通过 `data-cond-append` 和 `data-cond-delete` 管理。渲染时遍历 `conditions[]`，对每个元素调用 `renderConditionTree(bindBase, condition, registry)` |

### 发现 7：事件绑定路由机制未细化

当前 `bindEditorInputs` 使用 `setByPath(state.quest.q, ...)`。NPC/对话模式下需要路由到不同的 setByPath。

| 裁决 # | 议题 | 裁决 |
|--------|------|------|
| 68 | event-bindings 路由 | `bindEditorActions` 按 `state.mode` 三路分发: quest→`setByPath(state.quest.q)`, npc→`setNpcByPath(state.npc.q)`, dialogue→`setDialogueByPath(state.dialogue.q)` |

### 发现 8：中栏渲染未适配新模式

当前 `center-renderer.js` 只支持 quest 模式，NPC/对话模式需独立渲染。

| 裁决 # | 议题 | 裁决 |
|--------|------|------|
| 69 | 中栏渲染路由 | 新建 `renderNpcCenter(state, midEl)` / `renderDialogueCenter(state, midEl)`，由 `app.js` 按 mode 调用。quest 保持 `renderCenterEditor` 不变 |

### 发现 9：状态栏未适配新模式

`status-renderer.js` 硬编码 `state.quest.meta.file` 和 `state.quest.ui.sel.t`。

| 裁决 # | 议题 | 裁决 |
|--------|------|------|
| 70 | 状态栏适配 | 三种 mode 各自渲染: quest→显示文件名+Context(quest/phase/obj); NPC→显示 entityType+Context(NPC); dialogue→显示 dialogue.id+Context(node/choice) |

### 发现 10："新建架构"按钮 mode 无感

当前"新建架构"按钮只创建 blank quest。在 NPC/对话模式下应创建对应空白骨架。

| 裁决 # | 议题 | 裁决 |
|--------|------|------|
| 71 | "新建架构" mode 感知 | NPC mode → `createNpcSkeleton()`; dialogue mode → `createDialogueSkeleton()`; quest mode → `createBlankQuest()` (保持不变) |

### 发现 11：首节点时应自动设置 startNodeId

空 dialogue 添加第一个节点时，用户通常期望 `startNodeId` 自动设为该节点。

| 裁决 # | 议题 | 裁决 |
|--------|------|------|
| 72 | startNodeId 自动设置 | 在 dialogue-click-actions 的"添加节点"事件中：如果 `nodes` 从空变为非空，自动设 `state.dialogue.q.startNodeId = newNode.nodeId` |

### 发现 12：Gson QuestVisualSpec null 序列化行为

`DialogueSpec.visualConfig` 在 Java 中是 `QuestVisualSpec visualConfig = null;`。Gson 不序列化 null 字段。非 null 时会完整序列化 `themeColor`/`splashes`/`icons`。

| 裁决 # | 议题 | 裁决 |
|--------|------|------|
| 73 | visualConfig 序列化 | 编辑器保持 `null` 为默认值。用户启用 visual 配置时创建 `{themeColor: 0xFFFFFF, splashes: {}, icons: {}}`。导出清洗时 null→省略 |

### 发现 13：crossBtn 在非 quest mode 下的行为

当前 `crossBtn.onclick` 强制切换到 quest mode。在 NPC/对话模式下，用户可能期望看到自己 mode 的跨文件校验。

| 裁决 # | 议题 | 裁决 |
|--------|------|------|
| 74 | crossBtn mode 行为 | 保持现有行为（switch to quest mode）。未来阶段扩展：NPC/dialogue 各自的 cross-check 标签页。不影响当前施工 |

### 发现 14：拖拽导入 overlay 文案未按 mode 动态切换

当前 `bindDragAndDropImport` 固定显示"导入 quest JSON"。

| 裁决 # | 议题 | 裁决 |
|--------|------|------|
| 75 | 拖拽 overlay 动态文案 | `setDropOverlayVisible` 根据 `state.mode` 显示不同文案：quest→"拖入 Quest JSON" / npc→"拖入 NPC JSON 或任意 JSON 入库" / dialogue→"拖入 Dialogue JSON 或任意 JSON 入库" |

### 发现 15："导入到库"未自动检测 JSON 类型

当前 `importToLibrary` 固定调用 `importToRegistry(state, normalized, 'quest')`。

| 裁决 # | 议题 | 裁决 |
|--------|------|------|
| 76 | importToLibrary 类型检测 | 通过 `detectJsonType(json)` 确定 type，调用 `importToRegistry(state, json, type)` |

---

## 议程 4：数据类型与编译行为终审确认

### 代码方最终确认

| 确认项 | 确认内容 |
|--------|----------|
| `cooldownSeconds` Gson 序列化 | Java `long` 类型，JSON 输出为数字 `0`，无引号 ✅ |
| `dialogueDistance` Gson 序列化 | Java `double` 类型，输出 `8.0` ✅ |
| `customData` Gson 序列化 | `Map<String, Object>` → Gson 按实际类型输出。number → `putNbtValue` 判 `instanceof Double/Float` → `tag.putDouble`；否则 → `tag.putInt` |
| `parseCooldownType` 容错 | `toUpperCase()` 转换，`"seconds"` 等同 `"SECONDS"` ✅ |
| `visualConfig=null` Gson 省略 | 确认 `GsonBuilder` 未开 `serializeNulls`，null 字段不输出 ✅ |
| `conditionalTexts` Map key 编译器不保留 | 编译器重建 key 为 `"priority|conditionKey"`，原始 key 仅用于 JSON 可读性 ✅ |

---

## 议程 5：新增文件与修改文件终审

### 双方确认新增文件清单 (18 个)

| 文件 | 职责 | 状态 |
|------|------|------|
| `core/factories.js` | 追加 7 个骨架工厂函数 | 拍板 |
| `core/npc-shape.js` | `setNpcByPath` 数据绑定 | 拍板 |
| `core/dialogue-shape.js` | `setDialogueByPath` 数据绑定 | 拍板 |
| `core/npc-normalizer.js` | NPC 导入规范化 + 导出清洗 | 拍板 |
| `core/dialogue-normalizer.js` | Dialogue 导入规范化 + 导出清洗 | 拍板 |
| `core/npc-validators.js` | NPC 结构校验 (5 条规则) | 拍板 |
| `core/dialogue-validators.js` | Dialogue 结构校验 (26 条规则) | 拍板 |
| `editors/npc-editor.js` | NPC 完整编辑表单 | 拍板 |
| `editors/dialogue-editor.js` | 对话顶层 + 节点列表管理 | 拍板 |
| `editors/dialogue-node-editor.js` | 单节点编辑 (speaker/text/conditionalTexts) | 拍板 |
| `editors/dialogue-choice-editor.js` | Choice 编辑 (conditions/actions/冷却) | 拍板 |
| `editors/dialogue-action-editor.js` | Action 编辑器 (16 种 type switch) | 拍板 |
| `editors/cooldown-editor.js` | 冷却字段组通用组件 | 拍板 |
| `editors/textspec-editor.js` | TextSpec 通用编辑器 | 拍板 |
| `renderers/npc-tree-renderer.js` | NPC 模式左栏 | 拍板 |
| `renderers/dialogue-tree-renderer.js` | 对话模式左栏 (节点列表导航) | 拍板 |
| `renderers/bindings/npc-click-actions.js` | NPC 按钮事件 | 拍板 |
| `renderers/bindings/dialogue-click-actions.js` | 对话按钮事件 | 拍板 |

### 双方确认修改文件清单 (10 个)

| 文件 | 改动内容 | 状态 |
|------|----------|------|
| `state.js` | npc/dialogue 槽位初始化 | 拍板 |
| `app.js` | render 替换 + "新建架构" mode 感知 + rerender 路由 | 拍板 |
| `import-export.js` | 类型检测 + 多模式 routing + 拖拽动态文案 + importToLibrary 类型检测 | 拍板 |
| `event-bindings.js` | mode 路由 click actions + mode 路由 setByPath | 拍板 |
| `center-renderer.js` | 新增 renderNpcCenter / renderDialogueCenter | 拍板 |
| `side-panel-renderer.js` | 对话模式 refs 渲染 + NPC/对话模式 tab 适配 | 拍板 |
| `status-renderer.js` | 三种 mode 状态栏适配 | 拍板 |
| `cross-validator.js` | 增强 dialogue 内部引用校验 | 拍板 |
| `editor.css` | NPC/对话样式 (node 卡片、choice 折叠等) | 拍板 |
| `dom.js` | 无需修改 (现有 DOM refs 已满足) | 拍板 |

---

## 议程 6：完整校验规则终版

### NPC 校验规则 (5 条)

| # | 规则 | 级别 |
|---|------|------|
| 1 | `entityType` 非空 | ERROR |
| 2 | `dialogueDistance >= 1.0` | WARN |
| 3 | `bindings[].dialogueId` 和 `bindings[].dialogueIdFromNbt` 至少一个非空 | ERROR |
| 4 | `bindings[].condition` condition 类型有效 | WARN |
| 5 | `interactCondition` condition 类型有效 | WARN |

### Dialogue 校验规则 (26 条)

| # | 规则 | 级别 |
|---|------|------|
| 1 | `id` 非空 | ERROR |
| 2 | `nodes` 非空 | ERROR |
| 3 | `startNodeId` 在 nodes 列表内 | ERROR |
| 4 | 每个 node 的 `nodeId` 非空 | ERROR |
| 5 | nodeId 不重复 | ERROR |
| 6 | `autoNextId` 非空时在 nodes 内 | ERROR |
| 7 | `nextNodeId` 非空时在 nodes 内 | ERROR |
| 8 | `restoreNodeId`（choice 级）非空时在 nodes 内 | ERROR |
| 9 | `restoreNodeId`（action 级）非空时在 nodes 内 | ERROR |
| 10 | `choiceId` 非空 | ERROR |
| 11 | `conditionalTexts.sayId` 非空 | WARN |
| 12 | `actions[].type` 在白名单（16 种）外 | ERROR |
| 13 | `start_quest` / `complete_quest` / `advance_phase` 的 `questId` 非空 | ERROR |
| 14 | `give_item` 的 `itemId` 非空 | ERROR |
| 15 | `open_trade` / `open_simple_trade` / `open_gacha` 的 `shopId` 非空 | ERROR |
| 16 | `notify_talk` 的 `npcId` 非空 | ERROR |
| 17 | `notify_interact` 的 `targetId` 非空 | ERROR |
| 18 | `run_command` 的 `command` 非空 | ERROR |
| 19 | `set_flag` 的 `flagName` 非空 | ERROR |
| 20 | `set_variable` 的 `key` 非空 | ERROR |
| 21 | `custom` 的 `customTypeId` 非空 | ERROR |
| 22 | TextSpec `mode` 是 `literal` 或 `translatable` | ERROR |
| 23 | TextSpec `value` 为空 | WARN |
| 24 | `npcBindings[].npcId` 非空 | ERROR |
| 25 | `entityBindings[].entityType` 非空 | ERROR |
| 26 | `conditionalTexts` Map key 不含 `.` 字符 | WARN |

---

## 议程 7：最终施工阶段确认

| 阶段 | 任务数 | 新建文件 | 修改文件 | 状态 |
|------|--------|----------|----------|------|
| 第〇阶段 | 3 | 0 | 2 (factories.js, state.js) | ✅ 拍板 |
| 第一阶段 | 11 | 5 | 6 | ✅ 拍板 |
| 第二阶段 | 15 | 7 | 8 | ✅ 拍板 |
| 第三阶段 | 6 | 2 | 3 | ✅ 拍板 |

---

## 议程 8：关键设计决策终版 (20 条)

| # | 决策 | 理由 |
|---|------|------|
| 1 | 条件编辑器零修改 | 30 种 condition + 双通道补全已在第三阶段统合完成 |
| 2 | 独立 `setXxxByPath` | quest/NPC/dialogue 数据结构完全不同，共享函数会臃肿 |
| 3 | 跨模式导入不切换 mode | 防止用户丢失上下文 |
| 4 | conditionalTexts Map key 禁 `.` | 保护 `split('.')` 路径解析不被破坏 |
| 5 | `dialogue.spec.NpcBindingSpec` vs `npc.spec.NpcBindingSpec` 严格分离 | Gson 静默忽略不匹配字段，跨类字段污染会导致运行时失效 |
| 6 | 导出清洗删空字段 | `"" / [] / {} / null` 全部删除，防止 JSON 臃肿 |
| 7 | 对话节点左栏为线性列表 | 节点之间是图结构，树形展开无意义 |
| 8 | visualEditor 参数化复用 | quest/dialogue 共用同一 `QuestVisualSpec` |
| 9 | cooldownType 编辑器统一大写 | 编译器 `toUpperCase()` 容错，但规范化减少混淆 |
| 10 | restoreNodeId 两个层级独立 | choice 级和 action 级语义不同，不合并 |
| 11 | conditionalTexts conditions[] 绑定 | 按数组索引逐个渲染，通过 data-cond-append/delete 管理 |
| 12 | event-bindings mode 路由 | `bindEditorActions` 按 `state.mode` 路由到不同 setByPath |
| 13 | center-renderer mode 路由 | 新建 `renderNpcCenter` / `renderDialogueCenter` |
| 14 | status-renderer mode 适配 | 三种 mode 各自渲染状态栏 |
| 15 | "新建架构" mode 感知 | 按钮行为随 mode 变化 |
| 16 | 首节点自动设 startNodeId | 空 dialogue 添加第一个节点时自动设置 |
| 17 | Gson visualConfig null | null 时 Gson 不序列化；非 null 时完整序列化 |
| 18 | crossBtn mode 切换 | 保持现有行为 (→ quest mode)，未来扩展 |
| 19 | 拖拽 overlay 动态文案 | 按 mode 切换 |
| 20 | importToLibrary 类型检测 | 通过 detectJsonType 确定注册类型 |

---

## 拍板确认

### 代码方最终确认

- [x] 所有骨架工厂字段与 Java Spec 逐字段对齐
- [x] 所有校验规则与 Java Validator 逐条对齐
- [x] Action 白名单与编译器 switch 分支完全一致
- [x] Gson 序列化行为已充分考虑
- [x] 冷却系统语义已准确传达

### 编辑器方最终确认

- [x] 绑定前缀体系覆盖全部字段
- [x] setByPath 三路拆分架构合理
- [x] 通用组件 (TextSpec/cooldown/condition) 可复用
- [x] 导入导出路由场景矩阵完整
- [x] 三阶段施工顺序合理

### 双方签字

> 代码方：蓝图终版核验通过。12 处修正已拍板。20 条设计决策无异议。
>
> 编辑器方：蓝图终版核验通过。三阶段施工可立即开工。首阶段从第〇阶段开始。

---

## 附录：完整裁决索引 (92 条)

| # | 会议 | 轮次 | 核心议题 | 裁决 |
|---|------|------|----------|------|
| 1 | 一 | 1 | state 分层 | mode + registry + quest.* |
| 2 | 一 | 1 | 别名兼容 | 不做兼容别名 |
| 3 | 一 | 2 | mode bar UI | 3 个 tab |
| 4 | 一 | 2 | 渲染路由 | rerender 按 mode 分发 |
| 5 | 一 | 2 | 占位页面 | NPC/对话暂时占位 |
| 6 | 一 | 2 | 注册表模块 | core/registry.js |
| 7 | 一 | 2 | 导入行为 | 替换编辑区 + 写 registry |
| 8 | 一 | 2 | 注册表条目 | 提取摘要而非存完整 JSON |
| 9 | 一 | 2 | CSS grid | 56px auto 1fr 32px |
| 10 | 一 | 3 | condition 字段 | camelCase 统一 |
| 11 | 一 | 3 | CONDITION_TYPES | 补全到 30 种 |
| 12 | 一 | 3 | renderConditionTree | 签名简化 |
| 13 | 一 | 3 | 智能补全 | suggestions.js |
| 14 | 一 | 3 | 实时联动 | questId → phaseId datalist |
| 15 | 一 | 3 | setConditionNodeField | 30 种全部支持 |
| 16 | 一 | 3 | cleanCondition | camelCase 清洗 |
| 17 | 一 | 3 | import-normalizer | 双通道兼容 |
| 18 | 一 | 3 | 回滚策略 | registry 为空时行为不变 |
| 19 | 一 | 3补 | quest JSON 迁移 | 6 文件批量 |
| 20 | 一 | 3补 | 不动字段 | Mark/Reward/Objective type |
| 21 | 一 | 3补 | 迁移策略 | 一次性脚本 |
| 22 | 一 | 补 | 跨文件校验 | cross-validator.js |
| 23 | 一 | 补 | 校验 UI | side-panel "跨文件" tab |
| 24 | 一 | 补 | 自动执行 | 导入后自动校验 |
| 25 | 二 | 4 | NPC 前缀 | npc.* / npc.bind.N.* |
| 26 | 二 | 4 | Dialogue 前缀 | diag.* / diag.node.N.* / ... |
| 27 | 二 | 4 | condText key 约束 | 64 字符, [a-zA-Z0-9_-], 禁 `.` |
| 28 | 二 | 4 | Click actions | 独立文件分层 |
| 29 | 二 | 4 | setByPath 拆分 | npc-shape + dialogue-shape |
| 30 | 二 | 4 | event-bindings | mode 路由 |
| 31 | 二 | 5 | 导出清洗 | 删 ""/[]/{}/null |
| 32 | 二 | 5 | two NpcBindingSpec | 严格分离字段 |
| 33 | 二 | 5 | cooldownType | 统一大写 |
| 34 | 二 | 5 | customData | 明确类型 |
| 35 | 二 | 5 | dialogueDistance | step=0.5, min=1, max=32 |
| 36 | 二 | 6 | TextSpec 组件 | renderTextSpec |
| 37 | 二 | 6 | 冷却组件 | renderCooldownGroup |
| 38 | 二 | 6 | Action 编辑器 | switch 16 种 |
| 39 | 二 | 6 | restoreNodeId | 两个层级独立 |
| 40 | 二 | 6 | 节点左栏 | 线性列表+星标起始节点 |
| 41 | 二 | 6 | condTexts UI | 可折叠卡片+priority |
| 42 | 二 | 6 | soundEvent | 普通 input |
| 43 | 二 | 6 | visualEditor | 参数化复用 |
| 44 | 二 | 7 | 类型检测 | detectJsonType |
| 45 | 二 | 7 | 跨模式导入 | 不匹配只入库 |
| 46 | 二 | 7 | 导出文件名 | {id}_type.json |
| 47 | 二 | 7 | 导入到库 | dataset 标记 |
| 48 | 二 | 7 | 拖拽文字 | mode 动态 |
| 49 | 二 | 7 | 错误处理 | unknown → Toast |
| 50 | 二 | 8 | 校验分层 | npc/dialogue validators 独立 |
| 51 | 二 | 8 | NPC 规则 | 5 条 |
| 52 | 二 | 8 | Dialogue 规则 | 16 条（初版） |
| 53 | 二 | 8 | cross-validator 增强 | dialogue 内部引用 |
| 54 | 二 | 8 | condTexts priority 语义 | 优先排序 |
| 55 | 二 | 8 | Action 白名单 | 16 种 |
| 56 | 二 | 8 | condTexts conditions | 原样输出 |
| 57 | 二 | 8 | Map key 确定性 | 建议不同 priority |
| 58 | 二 | 8 | TextSpec args | 4 内置+自定义 |
| 59 | 二 | 8 | targetId vs npcId | 独立语义 |
| 60 | 二 | 8 | Gson null | 不序列化 null |
| 61 | 三 | 9 | defaultNpc.mode | literal 非 translatable |
| 62 | 三 | 9 | choiceId 级别 | WARN→ERROR |
| 63 | 三 | 9 | action 必填字段 | +7 条 ERROR |
| 64 | 三 | 9 | npcBindings 校验 | npcId 非空 ERROR |
| 65 | 三 | 9 | entityBindings 校验 | entityType 非空 ERROR |
| 66 | 三 | 9 | TextSpec value 空值 | WARN |
| 67 | 三 | 9 | condTexts conditions[] | 按索引逐个渲染 |
| 68 | 三 | 9 | event-bindings 路由 | mode→setByPath 三路分发 |
| 69 | 三 | 9 | 中栏渲染路由 | renderNpc/DialogueCenter |
| 70 | 三 | 9 | 状态栏适配 | 三种 mode 各自渲染 |
| 71 | 三 | 9 | 新建架构 mode 感知 | 按钮行为随 mode |
| 72 | 三 | 9 | startNodeId 自动设置 | 首个节点时自动 |
| 73 | 三 | 9 | visualConfig 序列化 | null 省略 |
| 74 | 三 | 9 | crossBtn 行为 | 保持→quest mode |
| 75 | 三 | 9 | 拖拽 overlay 文案 | mode 动态 |
| 76 | 三 | 9 | importToLibrary 类型 | detectJsonType |
| 77-91 | 三 | 9 | 文件清单终审 | 18 新增+10 修改 |
| 92 | 三 | 9 | 施工阶段终版 | 〇/一/二/三 共 35 步 |

---

> **文档版本**: 1.0-final
> **生成日期**: 2026-05-15
> **状态**: 三方签字通过，可进入施工阶段
