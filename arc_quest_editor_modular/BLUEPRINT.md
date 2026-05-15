# Arc Quest Editor — 共享注册表 + 智能补全 施工蓝图

> 三轮多 Agent 研讨修订。
>
> 第一轮（设计层面）：架构师 + 后端/数据包 + UX 交互 + 工程管理。10 项议题裁决。
>
> 第二轮（结合代码）：`setConditionNodeField()`/`cleanCondition()` 与新类型同步、`oninput` 可行性、分批计划。4 项新增议题。
>
> 第三轮（代码侧 vs 编辑器侧）：Java Spec 作者 + 编辑器作者 对撞。核证三大数据包全部使用同一套 `ConditionSpec`（camelCase）、quest JSON 旧格式现状、`cleanCondition()` 输出格式错误。**发现前版蓝图的字段命名策略从根本上错误**。
>
> 24 项议题全部裁决。

---

## 目标

将任务编辑器升级为**多模式编辑器**，核心能力：
1. 多次导入不同类型 JSON 后自动存入共享注册表
2. 条件编辑器的 quest/phase/flag 从注册表自动补全
3. 跨文件引用完整性校验
4. 为后续 NPC/对话编辑器提供基础设施
5. 统一三大数据包的 condition 格式为 camelCase（与 `org.arcadia.arc_quest.condition.ConditionSpec` 对齐）

---

## 跨层对齐：Java Spec ↔ JSON ↔ 编辑器

> 第三轮研讨核心发现：三大数据包（quest/dialogue/npc）全部使用同一个 Java 类 `org.arcadia.arc_quest.condition.ConditionSpec`，字段命名全部是 camelCase。编辑器必须与之对齐。

```text
ConditionSpec.java (单一真相源)
    ├── quest.spec.TransitionSpec.condition
    ├── quest.spec.QuestSpec.unlockConditions
    ├── dialogue.spec.ConditionalSaySpec.conditions
    ├── dialogue.spec.DialogueChoiceSpec.conditions
    └── npc.spec.NpcBindingSpec.condition

全部字段：questId, phaseId, targetPhaseId, fromPhaseId, toPhaseId,
          flag, key, op, value, inner, conditions, predicate,
          nodeId, choiceId, dialogueId, cooldownSeconds, startTick, endTick,
          name, nbtScope, nbtKey, nbtValue, namePattern
```

### 数据包 condition 格式现状

| 数据包 | 当前 JSON 格式 | 示例 | 与 ConditionSpec 对齐？ |
|---|---|---|---|
| quest | 旧格式 `{"type": "always"}` | `"condition": {"type": "always"}` | ❌ 仅 always 可用，非 always 条件因字段不匹配被静默忽略 |
| dialogue | 新格式 camelCase | `{"condition": "arc_quest:quest_phase", "questId": "..."}` | ✅ |
| NPC | 新格式 camelCase | `{"condition": "arc_quest:entity_nbt", "nbtKey": "..."}` | ✅ |

**编辑器 `cleanCondition()` 当前输出的是错误的 hybrid 格式**——新格式 condition 键名 + snake_case 字段名（`quest_id`）。这导致：
- dialogue/NPC 导出：`quest_id` 不匹配 Java `questId` → 静默字段丢失
- quest 导出：旧格式 `type` 被写为 `condition` 但字段仍是 snake_case → 两不兼容

**结论**：第三阶段必须把 condition 字段统一为 camelCase（`quest_id` → `questId`）。原蓝图第五阶段废弃，合并到第三阶段。

---

## 架构变更

### state 结构

```
state
├── mode: 'quest'           # 当前编辑模式
├── registry                 # 跨模式共享
│   ├── quests: {}           # { "arc_quest:epic_prologue": { id, category, tags, phases[], flags[], repeatable, ... } }
│   ├── dialogues: {}        # (未来) { "arc_quest:epic_village_elder": { id, nodes[] } }
│   ├── npcs: {}             # (未来) { "minecraft:villager": { entityType, bindings[] } }
│   └── npcBindings: {}      # 区分两类同名 NpcBindingSpec
│       ├── dialogue: {}     # dialogue.spec.NpcBindingSpec (npcId + dialogueId)
│       └── npc: {}          # npc.spec.NpcBindingSpec (bindingId + dialogueId + condition + priority)
├── quest                    # 任务模式工作区
│   ├── q: {}
│   ├── meta: { file, dirty }
│   ├── ui: { sel, tab, graphView, ... }
│   └── diag: []
├── npc                      # (未来) NPC 模式工作区
└── dialogue                 # (未来) 对话模式工作区
```

### 补全优先级

```
suggestions.js
├── getQuestSuggestions(registry)        → quest 列表
├── getPhaseSuggestions(registry, questId) → questId 所在 quest 的 phase 列表
├── getFlagSuggestions(registry)         → 所有已导入 quest 的全部 flag
└── getDialogueSuggestions(registry)     → 对话 ID 列表
```

### 导入行为定义

| 按钮 | 行为 |
|---|---|
| "打开"（现有 `importJson`） | 替换当前编辑区 + 写入 registry |
| "导入到库"（新增） | 只写入 registry，不替换当前编辑区。多次导入同名 ID 时覆盖旧条目 |

### 同名类区分

Java 中存在两个 `NpcBindingSpec` 类：

| 包 | 字段 |
|---|---|
| `dialogue.spec.NpcBindingSpec` | `npcId`, `dialogueId` |
| `npc.spec.NpcBindingSpec` | `bindingId`, `dialogueId`, `dialogueIdFromNbt`, `condition`, `priority` |

注册表键名区分为 `registry.npcBindings.dialogue` 和 `registry.npcBindings.npc`。

---

## 新增 condition 类型涉及文件对照

新增一种 condition 类型需要**同步修改 5 个位置**（第三轮新增 `cleanCondition()` 字段名修正）：

| 文件 | 位置 | 职责 | 需新增 |
|---|---|---|---|
| `condition-editor.js` | `CONDITION_TYPE_OPTIONS` | 下拉列表 + 摘要 + 徽标 | 12 种 |
| `condition-editor.js` | `renderConditionTree()` | 渲染输入控件 | 12 种 |
| `quest-shape-core.js` | `setConditionNodeField()` | 字段初始化 + 切换清理 | 12 种 |
| `export-normalizer-phase.js` | `cleanCondition()` | 导出清洗 + **字段名统一为 camelCase** | 12 种 |

单改一处会导致：无控件 / 垃圾字段残留 / 导出静默丢数据。

---

## 施工阶段

### 第一阶段：state 重构

| # | 文件 | 操作 | 说明 |
|---|---|---|---|
| 1a | `core/state.js` | 重构 | 引入 `mode` + `registry`，`q`/`meta`/`ui`/`diag` 收进 `quest` 槽位。不做兼容别名 |
| 1b | 全局 (20 个文件) | 分批替换 | `state.q` → `state.quest.q` 等。每约 5 个文件 `npm run build` 一次 |

#### 1b 分批计划

| 批次 | 文件 | 验证 |
|---|---|---|
| 第 1 批 | `state.js` + `validators.js` + `quest-shape.js` + `quest-shape-core.js` + `quest-shape-phase.js` + `quest-shape-collection.js` | build |
| 第 2 批 | `export-normalizer.js` + `export-normalizer-phase.js` + `import-normalizer.js` + `app.js` + `import-export.js` + `navigation.js` + `layout.js` | build |
| 第 3 批 | `tree-renderer.js` + `center-renderer.js` + `side-panel-renderer.js` + `status-renderer.js` + `graph-renderer.js` + `event-bindings.js` | build |
| 第 4 批 | `bindings/editor-click-actions.js` + `editor-delete-actions.js` + `editor-helpers.js` + `editor-helpers-identity.js` + `editor-helpers-collection.js` + `editor-inputs.js` | build |

#### 关键映射表

| 旧引用 | 新引用 |
|---|---|
| `state.q` | `state.quest.q` |
| `state.meta` | `state.quest.meta` |
| `state.ui` | `state.quest.ui` |
| `state.diag` | `state.quest.diag` |
| `state.mode` | 保持不变 |

---

### 第二阶段：页面模式切换 + 共享注册表引擎

| # | 文件 | 操作 | 说明 |
|---|---|---|---|
| 2.1 | `index.html` | 修改 | toolbar 加 mode bar — 3 个 tab `[Quest] [NPC] [Dialogue]` |
| 2.2 | `app.js` | 修改 | `rerender()` 按 `state.mode` 路由；新增"导入到库"按钮；占位页面 |
| 2.3 | **新建** `core/registry.js` | 创建 | `initRegistry(state)`、`importToRegistry(state, json, type)`、`clearRegistry(state, type)` |
| 2.4 | `app/import-export.js` | 修改 | `importJson()` 替换编辑区 + 写 registry；新增 `importToLibrary()` |

验证：三个 tab 可切换；quest 编辑功能不受影响

---

### 第三阶段：condition 全面统合 + 智能补全（6 维同步）

> 此阶段合并原蓝图第三、第五阶段。核心目标：condition 编辑器输出与 Java `ConditionSpec` 完全对齐。

| # | 文件 | 操作 | 说明 |
|---|---|---|---|
| 3.1 | **新建** `core/suggestions.js` | 创建 | `getQuestSuggestions(registry)`, `getPhaseSuggestions(registry, questId)`, `getFlagSuggestions(registry)`, `getDialogueSuggestions(registry)` |
| 3.2 | `editors/condition-editor.js` | 修改 | **四合一改造**：① 所有读取字段 `quest_id`→`questId`、`phase_id`→`phaseId`、`nbt_key`→`nbtKey`、`nbt_value`→`nbtValue`、`nbt_scope`→`nbtScope`；② `CONDITION_TYPE_OPTIONS` 补全 12 种；③ `renderConditionTree()` 新增对应输入控件；④ 接受 `registry` 做双通道补全 |
| 3.3 | `core/quest-shape-core.js` | 修改 | `setConditionNodeField()` 全部字段 camelCase + 12 种新类型初始化和清理 |
| 3.4 | `core/export-normalizer-phase.js` | 修改 | `cleanCondition()` 全部字段 camelCase + 12 种新类型白名单（不再回退为 `always`） |
| 3.5 | `core/import-normalizer.js` | 修改 | `normalizeCondition()` 确保导入数据字段为 camelCase；移除 snake_case 残留 |
| 3.6 | `editors/phase-editor.js` | 修改 | 删除手动 `flagSuggestions`/`questSuggestions` (~12行)，传 registry |
| 3.7 | `editors/quest/quest-top-level-section.js` | 修改 | 同上 (~10行)，修复 questSuggestions 混淆 phase ID 的 bug |
| 3.8 | `editors/phase-flow-section.js` / `phase-transition-section.js` / `phase-choice-section.js` | 修改 | 签名加 `registry`，透传 |

#### 回滚策略

`renderConditionTree(bindBase, condition, options = {}, registry = null)` — registry 为空时行为与旧版一致。若引入 bug，删除 `suggestions.js` 并去掉 `registry` 参数即可回退。

#### 新 condition 类型输入控件对应关系

| condition 类型 | 输入控件 |
|---|---|
| `arc_quest:entity_nbt` | `nbtScope` (select), `nbtKey`, `nbtValue` |
| `arc_quest:entity_name` | `namePattern` |
| `arc_quest:phase_before`, `arc_quest:phase_after` | `questId`, `targetPhaseId` |
| `arc_quest:phase_between`, `arc_quest:any_active_in_range`, `arc_quest:all_completed_in_range` | `questId`, `fromPhaseId`, `toPhaseId` |
| `arc_quest:phase_enterable` | `questId`, `phaseId` |
| `arc_quest:has_quest` | `questId` |
| `arc_quest:dialogue_completed`, `arc_quest:dialogue_on_cooldown` | `dialogueId` |
| `arc_quest:node_visited`, `arc_quest:node_on_cooldown` | `nodeId` |
| `arc_quest:choice_selected`, `arc_quest:choice_on_cooldown` | `choiceId` |
| `arc_quest:game_time_in_range` | `startTick`, `endTick` |

验证：导出 JSON 字段名与 `ConditionSpec.java` 一致；12 种类型正常渲染；旧 quest JSON 导入兼容

---

### 第四阶段：实时联动 + 移除 conditionOptions

| # | 文件 | 操作 | 说明 |
|---|---|---|---|
| 4.1 | `editors/condition-editor.js` | 修改 | `renderConditionTree()` 移除 `options` 参数，只接受 `registry` |
| 4.2 | `renderers/event-bindings.js` | 修改 | `bindEditorInputs()` 追加 phase 实时联动 DOM 事件 |
| 4.3 | 透传链 (6个) | 修改 | 删除 `conditionOptions`，只传 `registry` |

#### post-render DOM 绑定方案

```js
// event-bindings.js → bindEditorInputs() 追加：
midEl.addEventListener('input', e => {
    const target = e.target;
    const bind = target.dataset.b;
    if (!bind || !bind.endsWith('.questId')) return;

    const bindBase = bind.replace(/\.questId$/, '');
    const phaseList = midEl.querySelector(`#${CSS.escape(bindBase)}-phase-list`);
    if (!phaseList) return;

    const phaseIds = getPhaseSuggestions(state.registry, target.value);
    phaseList.innerHTML = phaseIds
        .map(id => `<option value="${id}"></option>`)
        .join('');
});
```

验证：输入 questId 后 phaseId datalist 即时更新

---

### 第五阶段：quest JSON condition 格式迁移

| # | 文件 | 操作 | 说明 |
|---|---|---|---|
| 5.1 | `run/arc_quest/datapack/quests/*.json` | 数据迁移 | 全部 quest JSON 的 condition 从旧格式 `{"type": "always"}` 迁移到 `{"condition": "arc_quest:always"}` |
| 5.2 | `import-normalizer.js` | 修改 | 保留 `convertOldCondition()` 兼容旧 quest JSON；新导入的 quest 使用新格式 |
| 5.3 | `epic_prologue.json` 等 | 重写 | 逐字段改为 camelCase，transitions condition 改为新格式 |

验证：quest JSON 重新加载到 Minecraft 后 quest 逻辑正常

---

## 修复的 bug

| 位置 | 问题 | 严重程度 |
|---|---|---|
| `quest/quest-top-level-section.js:L36` | `questSuggestions` 混入了 phase ID | 中 |
| `condition-editor.js` | `phaseSuggestions` 始终为空数组 | 高 |
| `condition-editor.js` | `CONDITION_TYPE_OPTIONS` 缺少 ~12 种 condition 类型 | 高 |
| `quest-shape-core.js` | `setConditionNodeField()` 不支持 12 种新类型 | 中 |
| `export-normalizer-phase.js` | `cleanCondition()` 不支持 12 种新类型 | 高 |
| `export-normalizer-phase.js` | `cleanCondition()` 输出 snake_case（`quest_id`）而非 camelCase（`questId`）→ Java 侧静默字段丢失 | **极严重** |
| `condition-editor.js` | 内部存取字段为 snake_case（`quest_id`）→ 导入 camelCase JSON 时出现双字段并存 | 高 |

---

## 删除的冗余代码

| 位置 | 删除内容 | 行数 |
|---|---|---|
| `phase-editor.js:L15-L27` | 手动 `flagSuggestions`/`questSuggestions` 构建 | ~12 |
| `quest/quest-top-level-section.js:L27-L36` | 同上 | ~10 |
| 第三阶段 | import/export normalizer 中 snake_case ↔ camelCase 转换 | ~25 |

---

## 改动量

| 阶段 | 新增文件 | 修改独立文件 | 删除行 |
|---|---|---|---|
| 一 | 0 | ~20 | 0 |
| 二 | 1 | 3 | 0 |
| 三 | 1 | 8 | ~35 |
| 四 | 0 | ~8 | ~15 |
| 五 | 0 | ~8 | 0 |
| **合计** | **2** | **~18-20** (有重叠) | **~50** |

---

## 注册表 quest 条目的最小结构

```js
{
  id: "arc_quest:epic_prologue",
  category: "main",
  mode: "PROGRESSION",
  repeatable: false,
  initialPhaseId: "arc_quest:gather_wood",
  completionPolicy: "ALL",
  phases: [
    {
      id: "arc_quest:gather_wood",
      mode: "normal",
      flagsToSetOnEnter: [],
      flagsToSetOnComplete: [],
    },
  ],
  flagsToSetOnAccept: [],
  flagsToSetOnComplete: ["prologue_done"],
}
```

---

## NPC 编辑器预设计

> 对照 [`npc.spec.NpcSpec.java`](file:///d:/Arc%20Quest/src/main/java/org/arcadia/arc_quest/npc/spec/NpcSpec.java) + [`NpcBindingSpec.java`](file:///d:/Arc%20Quest/src/main/java/org/arcadia/arc_quest/npc/spec/NpcBindingSpec.java)

| 编辑器部件 | 对应 Java 字段 | UI 类型 |
|---|---|---|
| 实体类型 | `NpcSpec.entityType` | input + datalist（从 `registry.npcs` 补全） |
| 绑定列表 | `NpcSpec.bindings` (List\<NpcBindingSpec\>) | 列表 + 添加/删除/排序 |
| → 绑定 ID | `NpcBindingSpec.bindingId` | input |
| → 对话 ID | `NpcBindingSpec.dialogueId` | input + datalist（从 `registry.dialogues` 补全） |
| → NBT 对话 ID | `NpcBindingSpec.dialogueIdFromNbt` | input |
| → 条件 | `NpcBindingSpec.condition` | `renderConditionTree(registry)` |
| → 优先级 | `NpcBindingSpec.priority` | number input |
| 取消原版交互 | `NpcSpec.cancelVanillaInteract` | checkbox |
| 对话距离 | `NpcSpec.dialogueDistance` | number input |
| 注视玩家 | `NpcSpec.shouldLookAtPlayer` | checkbox |
| 停止移动 | `NpcSpec.shouldStopMoving` | checkbox |
| 交互条件 | `NpcSpec.interactCondition` | `renderConditionTree(registry)` |
| 对话开始命令 | `NpcSpec.onDialogueStartCommands` | chip 列表 |
| 对话结束命令 | `NpcSpec.onDialogueEndCommands` | chip 列表 |

---

## 对话编辑器预设计

> 对照 [`dialogue.spec.DialogueSpec.java`](file:///d:/Arc%20Quest/src/main/java/org/arcadia/arc_quest/dialogue/spec/DialogueSpec.java) + [`DialogueNodeSpec.java`](file:///d:/Arc%20Quest/src/main/java/org/arcadia/arc_quest/dialogue/spec/DialogueNodeSpec.java) + [`DialogueChoiceSpec.java`](file:///d:/Arc%20Quest/src/main/java/org/arcadia/arc_quest/dialogue/spec/DialogueChoiceSpec.java) + [`DialogueActionSpec.java`](file:///d:/Arc%20Quest/src/main/java/org/arcadia/arc_quest/dialogue/spec/DialogueActionSpec.java)

### 对话树顶层

| 编辑器部件 | 对应 Java 字段 | UI 类型 |
|---|---|---|
| 对话 ID | `DialogueSpec.id` | input |
| 默认 NPC 名 | `DialogueSpec.defaultNpc` | text（mode select + value input） |
| 起始节点 | `DialogueSpec.startNodeId` | select（从节点列表） |
| 可重复 | `DialogueSpec.repeatable` | checkbox |
| 冷却 | `DialogueSpec.cooldownSeconds` + `cooldownType` | number + select |
| 视觉配置 | `DialogueSpec.visualConfig` | 复用 visual-editor |

### 节点列表编辑器（树状结构）

| 编辑器部件 | 对应 Java 字段 | UI 类型 |
|---|---|---|
| 节点 ID | `DialogueNodeSpec.nodeId` | input |
| Speaker | `DialogueNodeSpec.speaker` | text（mode + value） |
| 文本 | `DialogueNodeSpec.text` | textarea（mode + value） |
| 条件文本 | `DialogueNodeSpec.conditionalTexts` (Map\<String, ConditionalSaySpec\>) | 条目列表 + 条件编辑器 |
| → sayId | `ConditionalSaySpec.sayId` | input |
| → text | `ConditionalSaySpec.text` | text（mode + value） |
| → conditions | `ConditionalSaySpec.conditions` | `renderConditionTree(registry)` |
| → 优先级 | `ConditionalSaySpec.priority` | number |
| 选项列表 | `DialogueNodeSpec.choices` | 列表 + 添加/删除 |
| → 选项 ID | `DialogueChoiceSpec.choiceId` | input |
| → 文本 | `DialogueChoiceSpec.text` | text（mode + value） |
| → 目标节点 | `DialogueChoiceSpec.nextNodeId` | select（节点列表） |
| → 条件 | `DialogueChoiceSpec.conditions` | `renderConditionTree(registry)` |
| → 动作 | `DialogueChoiceSpec.actions` | 动作列表编辑器 |
| →→ start_quest | `DialogueActionSpec.questId` | input + datalist（registry.quests） |
| →→ open_trade/open_gacha | `DialogueActionSpec.shopId` | input |
| →→ notify_talk/npc_id | `DialogueActionSpec.npcId` | input |
| →→ give_item | `DialogueActionSpec.itemId` + `count` | input + number |
| →→ run_command | `DialogueActionSpec.command` | input |
| →→ set_flag/set_variable | `DialogueActionSpec.flagName`/`key` + `value` | input + number |
| →→ custom | `DialogueActionSpec.customTypeId` + `customData` | input + JSON area |
| 自动下一节点 | `DialogueNodeSpec.autoNextId` | input |
| 延迟 | `DialogueNodeSpec.delayMs` | number |
| 进入音效 | `DialogueNodeSpec.nodeEnterSound` | input |

### NPB/Entity 绑定

| 编辑器部件 | 对应 Java 字段 | UI 类型 |
|---|---|---|
| NPC 绑定 | `dialogue.spec.NpcBindingSpec` (npcId + dialogueId) | 简单键值对列表 |
| 实体绑定 | `dialogue.spec.EntityBindingSpec` (entityType + dialogueId) | 简单键值对列表 |

---

## 后续扩展（完成五阶段后）

NPC/对话编辑器的代码增量：
- `factories.js` 追加 `createNpcSkeleton`, `createNpcBinding`, `createDialogueSkeleton`, `createDialogueNode`, `createDialogueChoice` (~60行)
- `editors/npc-editor.js` (~150行)
- `editors/dialogue-editor.js` + `node-editor.js` + `action-editor.js` (~400行)
- `core/npc-normalizer.js` (~80行)
- `core/dialogue-normalizer.js` (~120行)
- `core/cross-validator.js` — 跨文件引用完整性校验 (~60行)
