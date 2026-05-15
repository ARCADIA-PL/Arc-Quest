# Arc Quest Editor — NPC & 对话数据包编辑器 施工蓝图

> **研讨历程**
>
> 第一轮（3 轮 Agent 研讨 · 架构层）：24 项议题裁决 — state 重构、共享注册表、condition 全面统合、智能补全、跨文件校验。
>
> 第二轮（5 轮 Agent 研讨 · 实现层）：Java 代码方 ↔ 编辑器方深度对撞。核证 `DialogueSpec`/`NpcSpec` 全字段语义、Gson 序列化行为、编译器内部逻辑、运行时冷却系统。
> 新增 **36 项合意裁决**，覆盖数据绑定、JSON 序列化、UI 组件设计、导入导出路由、校验规则。
>
> 合计 **60 项议题全部裁决**。

---

## 目标

在现有 quest 编辑器基础上，正式落地 NPC 编辑器和对话编辑器，使编辑器成为**真正的三模式数据包编辑器**。

核心能力：
1. NPC 模式 — 完整的 `NpcSpec` 编辑、导入、导出
2. 对话模式 — 完整的 `DialogueSpec` 编辑（节点树 + conditionalTexts + choices + actions）、导入、导出
3. 跨模式智能导入 — 自动检测 JSON 类型，路由到对应编辑区或仅入库
4. 三层校验 — quest/NPC/dialogue 各自的结构校验 + 跨文件引用完整性检查
5. 组件复用 — condition 编辑器、visual 编辑器、chip 编辑器、冷却字段组全部零修改复用

---

## 跨层对齐总表

```text
ConditionSpec.java (单一真相源，已统合)
    ├── quest.spec.TransitionSpec.condition
    ├── quest.spec.QuestSpec.unlockConditions
    ├── quest.spec.PhaseSpec.enterCondition
    ├── quest.spec.ChoiceSpec.visibleCondition
    ├── dialogue.spec.ConditionalSaySpec.conditions
    ├── dialogue.spec.DialogueChoiceSpec.conditions
    └── npc.spec.NpcBindingSpec.condition
    └── npc.spec.NpcSpec.interactCondition

全部字段: questId, phaseId, targetPhaseId, fromPhaseId, toPhaseId,
          flag, key, op, value, inner, conditions, predicate,
          nodeId, choiceId, dialogueId, cooldownSeconds, startTick, endTick,
          name, nbtScope, nbtKey, nbtValue, namePattern
```

---

## 一、state 架构（第一阶段已完成 + 本次扩展）

```text
state
├── mode: 'quest' | 'npc' | 'dialogue'
├── registry
│   ├── quests: {}           { "arc_quest:epic_prologue": { id, phases[], flags[], ... } }
│   ├── dialogues: {}        { "arc_quest:epic_village_elder": { id, nodes[] } }
│   ├── npcs: {}             { "minecraft:villager": { entityType, bindings[] } }
│   └── npcBindings:
│       ├── dialogue: {}     dialogue.spec.NpcBindingSpec { npcId, dialogueId }
│       └── npc: {}          npc.spec.NpcBindingSpec { bindingId, dialogueId, ... }
├── quest
│   ├── q: {}
│   ├── meta: { file, dirty }
│   ├── ui: { sel, tab, graphView, paneSizes, ... }
│   ├── diag: []            quest 结构校验结果
│   └── crossResults: {}    跨文件校验结果
├── npc                      ← 本次新增
│   ├── q: {}               NpcSpec 完整对象
│   ├── meta: { file, dirty }
│   └── diag: []            NPC 结构校验结果
└── dialogue                 ← 本次新增
    ├── q: {}               DialogueSpec 完整对象
    ├── meta: { file, dirty }
    ├── ui: { selNodeId }   当前选中的节点 ID
    └── diag: []            对话结构校验结果
```

### NPC 骨架工厂

```js
// factories.js 新增
export const createNpcSkeleton = () => ({
    entityType: '',
    bindings: [],
    cancelVanillaInteract: true,
    dialogueDistance: 8.0,
    shouldLookAtPlayer: true,
    shouldStopMoving: true,
    interactCondition: null,
    onDialogueStartCommands: [],
    onDialogueEndCommands: []
});

export const createNpcBinding = () => ({
    bindingId: '',
    dialogueId: '',
    dialogueIdFromNbt: '',
    condition: null,
    priority: 0
});
```

### 对话骨架工厂

```js
// factories.js 新增
export const createDialogueSkeleton = () => ({
    id: '',
    defaultNpc: { mode: 'literal', value: '', args: [] },
    startNodeId: '',
    nodes: [],
    visualConfig: null,
    repeatable: true,
    cooldownSeconds: 0,
    cooldownType: 'NONE',
    resetTimeTicks: 0,
    npcBindings: [],
    entityBindings: []
});

export const createDialogueNode = () => ({
    nodeId: '',
    speaker: { mode: 'literal', value: '', args: [] },
    text: { mode: 'literal', value: '', args: [] },
    conditionalTexts: {},
    choices: [],
    autoNextId: '',
    delayMs: 0,
    repeatable: true,
    cooldownSeconds: 0,
    cooldownType: 'NONE',
    resetTimeTicks: 0,
    nodeEnterSound: ''
});

export const createConditionalSay = () => ({
    sayId: '',
    text: { mode: 'literal', value: '', args: [] },
    soundEvent: '',
    conditions: [],
    priority: 0
});

export const createDialogueChoice = () => ({
    choiceId: '',
    text: { mode: 'literal', value: '', args: [] },
    nextNodeId: '',
    conditions: [],
    actions: [],
    repeatable: true,
    cooldownSeconds: 0,
    cooldownType: 'NONE',
    resetTimeTicks: 0,
    priority: 0,
    restoreNodeId: '',
    selectSound: ''
});

export const createDialogueAction = (type = 'no_op') => ({
    type,
    questId: '',
    amount: 0,
    itemId: '',
    count: 1,
    npcId: '',
    targetId: '',
    command: '',
    flagName: '',
    key: '',
    value: 0,
    shopId: '',
    restoreNodeId: '',
    customTypeId: '',
    customData: {}
});
```

---

## 二、数据绑定架构

### 绑定前缀体系

| 模式 | 前缀 | 路径示例 | 语义 |
|------|------|----------|------|
| quest | `q.*` | `q.id` | Quest 顶层字段 |
| quest | `ph.N.*` | `ph.0.title` | Phase[N] 字段 |
| quest | `ob.N.M.*` | `ob.0.1.targetId` | Phase[N].objectives[M] 字段 |
| quest | `rw.quest.N.*` | `rw.quest.0.itemId` | 全局 Rewards[N] 字段 |
| quest | `rw.phase.N.M.*` | `rw.phase.0.2.itemId` | Phase[N].rewards[M] 字段 |
| NPC | `npc.*` | `npc.entityType` | NpcSpec 顶层字段 |
| NPC | `npc.bind.N.*` | `npc.bind.0.dialogueId` | bindings[N] 字段 |
| NPC | `npc.interactCond` | — | interactCondition (走 renderConditionTree) |
| dialogue | `diag.*` | `diag.id` | DialogueSpec 顶层字段 |
| dialogue | `diag.node.N.*` | `diag.node.0.nodeId` | nodes[N] 字段基路径 |
| dialogue | `diag.node.N.ch.C.*` | `diag.node.0.ch.1.nextNodeId` | nodes[N].choices[C] 字段 |
| dialogue | `diag.node.N.ch.C.actions.A.*` | `diag.node.0.ch.1.actions.2.type` | actions[A] 字段 |
| dialogue | `diag.node.N.condText.K.*` | `diag.node.0.condText.newcomer.text.value` | conditionalTexts[K] 字段 |

### conditionalTexts Map key 约束

- 最大长度：64 字符
- 允许字符：`[a-zA-Z0-9_-]`
- **禁止 `.`**（会破坏 `split('.')` 路径解析）
- 编辑器在 key 输入框上直接限制 + oninput 实时过滤非法字符

### setByPath 拆分

| 文件 | 导出函数 | 负责模式 |
|------|----------|----------|
| `quest-shape.js` | `setByPath(target, bind, value, inputType)` | quest（不变） |
| **新建** `npc-shape.js` | `setNpcByPath(target, bind, value, inputType)` | NPC |
| **新建** `dialogue-shape.js` | `setDialogueByPath(target, bind, value, inputType)` | dialogue |

`event-bindings.js` 按 `state.mode` 路由调用不同的 setByPath。NPC/对话模式各自有独立的中栏事件绑定。

---

## 三、UI 组件设计

### 复用组件（零修改）

| 组件 | 文件 | 复用方式 |
|------|------|----------|
| condition 编辑器 | `condition-editor.js` | `renderConditionTree(bindBase, condition, registry)` — 直接调用 |
| visual 编辑器 | `visual-editor.js` | 参数化 source 对象，quest/dialogue 共用 |
| chip 编辑器 | `chip-editor.js` | chip 列表（命令列表 / args） |
| 冷却字段组 | **新建** `cooldown-editor.js` | `renderCooldownGroup(bindBase, obj)` — 3 处复用 |
| TextSpec 编辑器 | **新建** `textspec-editor.js` | `renderTextSpec(bindBase, spec, label)` — ≈8 处复用 |

### 新建通用组件

#### `cooldown-editor.js` — 冷却字段组

```js
// 在 DialogueSpec / DialogueNodeSpec / DialogueChoiceSpec 三层复用
export function renderCooldownGroup(bindBase, obj) {
    return `
    <div class="row">
      <div class="f"><label>Repeatable</label>
        <select data-b="${bindBase}.repeatable">
          <option value="true" ${obj.repeatable !== false ? 'selected' : ''}>是</option>
          <option value="false" ${obj.repeatable === false ? 'selected' : ''}>否（仅一次）</option>
        </select>
      </div>
      <div class="f"><label>Cooldown Type</label>
        <select data-b="${bindBase}.cooldownType">
          <option value="NONE" ${obj.cooldownType === 'NONE' ? 'selected' : ''}>无冷却</option>
          <option value="SECONDS" ${obj.cooldownType === 'SECONDS' ? 'selected' : ''}>秒</option>
          <option value="GAME_DAY" ${obj.cooldownType === 'GAME_DAY' ? 'selected' : ''}>游戏日</option>
          <option value="GAME_TICK" ${obj.cooldownType === 'GAME_TICK' ? 'selected' : ''}>游戏 Tick</option>
        </select>
      </div>
      <div class="f"><label>Cooldown Seconds</label>
        <input type="number" data-b="${bindBase}.cooldownSeconds" value="${obj.cooldownSeconds ?? 0}" min="0" max="86400">
      </div>
      <div class="f"><label>Reset Ticks</label>
        <input type="number" data-b="${bindBase}.resetTimeTicks" value="${obj.resetTimeTicks ?? 0}" min="0" max="24000">
      </div>
    </div>`;
}
```

#### `textspec-editor.js` — 通用 TextSpec

```js
// mode select + value input + args chip 列表
export function renderTextSpec(bindBase, spec, label) {
    const modeBind = `${bindBase}.mode`;
    const valueBind = `${bindBase}.value`;
    const argsBind = `${bindBase}.args`;
    return `
    <div class="card">
      <div class="small"><b>${label}</b></div>
      <div class="row">
        <div class="f">
          <label>Mode</label>
          <select data-b="${modeBind}">
            <option value="literal" ${spec.mode === 'literal' ? 'selected' : ''}>字面</option>
            <option value="translatable" ${spec.mode === 'translatable' ? 'selected' : ''}>可翻译</option>
          </select>
        </div>
        <div class="f"><label>Value</label>
          <input data-b="${valueBind}" value="${esc(spec.value || '')}">
        </div>
      </div>
      ${spec.mode === 'translatable' ? renderArgsEditor(argsBind, spec.args || []) : ''}
    </div>`;
}

function renderArgsEditor(bindBase, args) {
    const chips = (args || []).map((v, i) =>
        `<span class="chip-item">${esc(v)}<button type="button" class="chip-remove" data-chip-remove="${bindBase}:${i}">×</button></span>`
    ).join('');
    return `
    <div class="f" style="margin-top:6px">
      <label>Args</label>
      <div class="chip-editor">
        <div class="chip-list">${chips || '<span class="tiny">内置: player_name, npc_name, npc_pos, npc_display_name</span>'}</div>
        <div class="chip-input-row">
          <input type="text" data-chip-add-input="${bindBase}" placeholder="player_name">
          <button type="button" data-chip-add="${bindBase}">添加</button>
        </div>
      </div>
    </div>`;
}
```

### NPC 模式布局

```
┌─────────┐  ┌──────────────────────────────────┐  ┌─────────────┐
│  左栏   │  │           中栏                   │  │   右栏      │
│  (空)   │  │  ┌─ NPC 属性 ──────────────────┐ │  │  拓扑(禁用) │
│         │  │  │ entityType  [input+datalist] │ │  │  引用       │
│         │  │  │ cancelVanillaInteract [✓]    │ │  │  跨文件     │
│         │  │  │ dialogueDistance  [8.0]      │ │  │  大纲       │
│         │  │  │ shouldLookAtPlayer [✓]       │ │  │  诊断       │
│         │  │  │ shouldStopMoving   [✓]       │ │  │  JSON       │
│         │  │  └──────────────────────────────┘ │  │  指南       │
│         │  │  ┌─ 交互条件 ───────────────────┐ │  │             │
│         │  │  │ renderConditionTree()         │ │  │             │
│         │  │  └──────────────────────────────┘ │  │             │
│         │  │  ┌─ 绑定列表 ───────────────────┐ │  │             │
│         │  │  │ [Binding 0] bindingId        │ │  │             │
│         │  │  │ [Binding 1] dialogueId       │ │  │             │
│         │  │  │ [+ 添加绑定]                  │ │  │             │
│         │  │  └──────────────────────────────┘ │  │             │
│         │  │  ┌─ 命令列表 ───────────────────┐ │  │             │
│         │  │  │ chip-editor (start)           │ │  │             │
│         │  │  │ chip-editor (end)             │ │  │             │
│         │  │  └──────────────────────────────┘ │  │             │
└─────────┘  └──────────────────────────────────┘  └─────────────┘
```

### 对话模式布局

```
┌──────────────┐  ┌──────────────────────────────────┐  ┌─────────────┐
│    左栏      │  │           中栏                   │  │   右栏      │
│  ┌─节点树──┐ │  │  ┌─ 对话顶层 ─────────────────┐ │  │  拓扑       │
│  │ ★ start │ │  │  │ id / defaultNpc / startNode│ │  │  引用       │
│  │   intro  │ │  │  │ visualConfig / 冷却         │ │  │  跨文件     │
│  │   outro  │ │  │  └────────────────────────────┘ │  │  大纲       │
│  └─────────┘ │  │  ┌─ 节点: start ───────────────┐│  │  诊断       │
│  [+添加节点] │  │  │ nodeId / speaker / text      ││  │  JSON       │
│              │  │  │ delayMs / autoNextId / 冷却  ││  │  指南       │
│              │  │  │ ┌─ ConditionalTexts ────────┐││  │             │
│              │  │  │ │ [newcomer] sayId          │││  │             │
│              │  │  │ │ conditions[]              │││  │             │
│              │  │  │ └──────────────────────────┘││  │             │
│              │  │  │ ┌─ Choices ────────────────┐││  │             │
│              │  │  │ │ [choice_intro] text       │││  │             │
│              │  │  │ │ nextNodeId / conditions[] │││  │             │
│              │  │  │ │ ┌─ Actions ──────────────┐│││  │             │
│              │  │  │ │ │ [0] start_quest        ││││  │             │
│              │  │  │ │ └───────────────────────┘│││  │             │
│              │  │  │ └──────────────────────────┘││  │             │
│              │  │  └──────────────────────────────┘│  │             │
│              │  │  ┌─ 绑定 ──────────────────────┐│  │             │
│              │  │  │ npcBindings[]               ││  │             │
│              │  │  │ entityBindings[]            ││  │             │
│              │  │  └──────────────────────────────┘│  │             │
└──────────────┘  └──────────────────────────────────┘  └─────────────┘
```

---

## 四、导入/导出路由

### 类型检测

```js
function detectJsonType(json) {
    if (json && json.nodes && Array.isArray(json.nodes)) return 'dialogue';
    if (json && json.entityType && Array.isArray(json.bindings)) return 'npc';
    if (json && (Array.isArray(json.phases) || json.id)) return 'quest';
    return 'unknown';
}
```

### 场景矩阵

| 当前 mode | 操作 | JSON 类型 | 行为 |
|-----------|------|-----------|------|
| quest | 导入 JSON | quest | 替换 quest 编辑区 + 写入 registry + 跨文件校验 |
| quest | 导入 JSON | npc/dialogue | 仅写入 registry + Toast "已导入到 {type} 注册表" |
| npc | 导入 JSON | npc | 替换 NPC 编辑区 + 写入 registry |
| npc | 导入 JSON | quest/dialogue | 仅写入 registry |
| dialogue | 导入 JSON | dialogue | 替换 dialogue 编辑区 + 写入 registry |
| dialogue | 导入 JSON | quest/npc | 仅写入 registry |
| 任意 | 导入到库 | 任意 | 仅写入 registry，不替换编辑区 |
| 任意 | 导出序列 | — | 按当前 mode 导出对应 JSON |
| unknown | 任意 | unknown | Toast "无法识别 JSON 类型" |

### 导出文件名

- quest → `{quest_id}_quest.json`
- npc → `{entityType}_npc.json`
- dialogue → `{dialogue_id}_dialogue.json`

### 拖拽导入

拖拽 overlay 文案按 mode 动态切换：
- quest → "拖入 Quest JSON"
- npc → "拖入 NPC JSON 或任意 JSON 入库"
- dialogue → "拖入 Dialogue JSON 或任意 JSON 入库"

---

## 五、校验体系（三层）

### NPC 校验规则 (`npc-validators.js`)

| # | 规则 | 级别 |
|---|------|------|
| 1 | `entityType` 非空 | ERROR |
| 2 | `dialogueDistance >= 1.0` | WARN |
| 3 | `bindings[].dialogueId` 和 `bindings[].dialogueIdFromNbt` 至少一个非空 | ERROR |
| 4 | `bindings[].condition` condition 类型有效（走 `validateConditionNode`） | WARN |
| 5 | `interactCondition` condition 类型有效 | WARN |

### Dialogue 校验规则 (`dialogue-validators.js`)

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

### 跨文件校验增强 (`cross-validator.js`)

原有规则全部保留，对话编辑器落地后增强：
- dialogue 内部节点引用的 `nextNodeId`/`restoreNodeId`/`autoNextId` 存在性检查
- dialogue action `start_quest` 的 `questId` 在 `registry.quests` 中存在

---

## 六、Action 类型白名单

```js
const DIALOGUE_ACTION_TYPES = [
    'start_quest',      // → questId
    'complete_quest',   // → questId
    'advance_phase',    // → questId
    'give_xp',          // → amount
    'give_item',        // → itemId + count
    'notify_talk',      // → npcId
    'notify_interact',  // → targetId
    'no_op',            // (无)
    'close',            // (无)
    'run_command',      // → command
    'set_flag',         // → flagName
    'set_variable',     // → key + value
    'open_trade',       // → shopId + restoreNodeId
    'open_simple_trade', // → shopId + restoreNodeId
    'open_gacha',       // → shopId + restoreNodeId
    'custom',           // → customTypeId + customData (JSON area)
];
```

### Action 字段映射表

| type | 显示字段 |
|------|----------|
| `start_quest` / `complete_quest` / `advance_phase` | `questId` (input + registry datalist) |
| `give_xp` | `amount` (number) |
| `give_item` | `itemId` (input) + `count` (number) |
| `notify_talk` | `npcId` (input, placeholder: `namespace:npc_id`) |
| `notify_interact` | `targetId` (input, placeholder: `namespace:npc_id`) |
| `no_op` / `close` | 无额外字段，仅显示描述 |
| `run_command` | `command` (textarea) |
| `set_flag` | `flagName` (input, registry flag 补全) |
| `set_variable` | `key` (input) + `value` (number) |
| `open_trade` / `open_simple_trade` / `open_gacha` | `shopId` (input) + `restoreNodeId` (select, 从节点列表补全) |
| `custom` | `customTypeId` (input) + `customData` (JSON textarea) |

---

## 七、新增文件清单

| 文件 | 职责 | 预估行数 |
|------|------|----------|
| `core/factories.js` | 追加 7 个骨架工厂函数 | +55 |
| `core/npc-shape.js` | `setNpcByPath` 数据绑定 | 60 |
| `core/dialogue-shape.js` | `setDialogueByPath` 数据绑定 | 80 |
| `core/npc-normalizer.js` | NPC 导入规范化 + 导出清洗 | 80 |
| `core/dialogue-normalizer.js` | Dialogue 导入规范化 + 导出清洗 | 120 |
| `core/npc-validators.js` | NPC 结构校验 | 40 |
| `core/dialogue-validators.js` | Dialogue 结构校验 | 80 |
| `editors/npc-editor.js` | NPC 完整编辑器 | 150 |
| `editors/dialogue-editor.js` | 对话顶层 + 节点列表管理 | 200 |
| `editors/dialogue-node-editor.js` | 单节点编辑（speaker/text/conditionalTexts） | 150 |
| `editors/dialogue-choice-editor.js` | Choice 编辑（conditions/actions/冷却） | 120 |
| `editors/dialogue-action-editor.js` | Action 编辑器（16 种 type switch） | 100 |
| `editors/cooldown-editor.js` | 冷却字段组通用组件 | 30 |
| `editors/textspec-editor.js` | TextSpec 通用编辑器 | 50 |
| `renderers/npc-tree-renderer.js` | NPC 模式左栏（空占位或节点摘要） | 20 |
| `renderers/dialogue-tree-renderer.js` | 对话模式左栏（节点列表导航） | 60 |
| `renderers/bindings/npc-click-actions.js` | NPC 按钮事件（添加/删除 binding 等） | 60 |
| `renderers/bindings/dialogue-click-actions.js` | 对话按钮事件（添加/删除 node/choice/action 等） | 120 |

### 修改文件清单

| 文件 | 改动 | 说明 |
|------|------|------|
| `state.js` | `npc`/`dialogue` 槽位初始化 | `npc: { q: createNpcSkeleton(), ... }` |
| `app.js` | `renderNpc()`/`renderDialogue()` 替换占位 + "新建架构" mode 感知 + rerender 模式路由 | ~50 行 |
| `import-export.js` | 类型检测 + 多模式 routing + 拖拽 overlay 动态文案 | ~60 行 |
| `dom.js` | 无新增 DOM 元素（已足够） | 0 |
| `event-bindings.js` | 按 mode 路由到 NpcClickActions/DialogueClickActions + setByPath 路由 | ~40 行 |
| `center-renderer.js` | 新增 `renderNpcCenter` / `renderDialogueCenter` 两个导出 | ~20 行 |
| `side-panel-renderer.js` | 对话模式的 refs 渲染 + NPC/对话模式 tab 适配 | ~20 行 |
| `status-renderer.js` | 适配 NPC/对话模式的文件名/entityType/id 显示 | ~25 行 |
| `cross-validator.js` | 增强 dialogue 内部引用校验 | ~40 行 |
| `editor.css` | NPC/对话相关样式（node 卡片、choice 折叠等） | ~60 行 |

---

## 八、施工阶段

### 第〇阶段：骨架 + 基础设施 (`state.js` / `factories.js`)

| # | 文件 | 操作 |
|---|------|------|
| 0.1 | `factories.js` | 追加 7 个骨架函数 |
| 0.2 | `state.js` | `state.npc` / `state.dialogue` 槽位初始化 |
| 0.3 | 构建验证 | — |

### 第一阶段：NPC 编辑器

| # | 文件 | 操作 |
|---|------|------|
| 1.1 | `npc-shape.js` | 创建 `setNpcByPath` |
| 1.2 | `npc-normalizer.js` | 导入/导出规范化 |
| 1.3 | `npc-validators.js` | 5 条校验规则 |
| 1.4 | `npc-editor.js` | 完整编辑表单 |
| 1.5 | `npc-click-actions.js` | 添加/删除 binding、chip 事件 |
| 1.6 | `app.js` | `renderNpc()` 替换占位 + "新建架构" mode 感知 |
| 1.7 | `import-export.js` | NPC JSON 导入/导出分支 + 类型检测 + 拖拽 overlay 动态文案 |
| 1.8 | `event-bindings.js` | NPC click actions 绑定 + mode 路由 setByPath |
| 1.9 | `center-renderer.js` | 新增 `renderNpcCenter` 函数 |
| 1.10 | `status-renderer.js` | NPC mode 状态栏适配 |
| 1.11 | 构建验证 + 提交 | — |

### 第二阶段：对话编辑器 — 基础

| # | 文件 | 操作 |
|---|------|------|
| 2.1 | `dialogue-shape.js` | 创建 `setDialogueByPath` |
| 2.2 | `dialogue-normalizer.js` | 导入/导出规范化 |
| 2.3 | `dialogue-validators.js` | 16 条校验规则 |
| 2.4 | `cooldown-editor.js` | 冷却字段组通用组件 |
| 2.5 | `textspec-editor.js` | TextSpec 通用组件 |
| 2.6 | `dialogue-editor.js` | 对话顶层 + 节点列表管理 |
| 2.7 | `dialogue-node-editor.js` | 单节点编辑（speaker/text/conditionalTexts/autoNextId/delayMs/首节点 startNodeId 自动设置） |
| 2.8 | `dialogue-tree-renderer.js` | 左栏节点列表导航 |
| 2.9 | `dialogue-click-actions.js` | 添加/删除 node/choice/conditionalText + 首节点 startNodeId 自动设置 |
| 2.10 | `app.js` | `renderDialogue()` 替换占位 + "新建架构" mode 感知 |
| 2.11 | `import-export.js` | dialogue JSON 导入/导出分支 |
| 2.12 | `event-bindings.js` | dialogue click actions 绑定 + mode 路由 setByPath |
| 2.13 | `center-renderer.js` | 新增 `renderDialogueCenter` 函数 |
| 2.14 | `status-renderer.js` | dialogue mode 状态栏适配 |
| 2.15 | 构建验证 + 提交 | — |

### 第三阶段：对话编辑器 — 高级

| # | 文件 | 操作 |
|---|------|------|
| 3.1 | `dialogue-choice-editor.js` | Choice 编辑（conditions/actions/冷却/restoreNode） |
| 3.2 | `dialogue-action-editor.js` | Action 编辑器（16 种 type switch） |
| 3.3 | `dialogue-click-actions.js` | 补充 choice/action 的添加/删除事件 |
| 3.4 | `cross-validator.js` | 增强对话内部引用校验 |
| 3.5 | `editor.css` | NPC/对话样式补充 |
| 3.6 | 构建验证 + 提交 | — |

---

## 九、关键设计决策速查

| # | 决策 | 理由 |
|---|------|------|
| 1 | 条件编辑器零修改 | 30 种 condition + 双通道补全已在第三阶段统合完成 |
| 2 | 独立 `setXxxByPath` | quest/NPC/dialogue 数据结构完全不同，共享函数会臃肿 |
| 3 | 跨模式导入不切换 mode | 防止用户丢失上下文（正在编辑 quest 时导入 dialogue 只入库） |
| 4 | conditionalTexts Map key 禁 `.` | 保护 `split('.')` 路径解析不被破坏 |
| 5 | `dialogue.spec.NpcBindingSpec` vs `npc.spec.NpcBindingSpec` 严格分离 | Gson 静默忽略不匹配字段，跨类字段污染会导致运行时失效 |
| 6 | 导出清洗删空字段 | `"" / [] / {} / null` 全部删除，防止 JSON 臃肿 |
| 7 | 对话节点左栏为线性列表 | 节点之间是图结构，树形展开无意义 |
| 8 | visualEditor 参数化复用 | quest/dialogue 共用同一 `QuestVisualSpec` |
| 9 | cooldownType 编辑器统一大写 | 编译器 `toUpperCase()` 容错，但规范化减少混淆 |
| 10 | restoreNodeId 两个层级独立 | choice 级和 action 级语义不同，不合并 |
| 11 | conditionalTexts conditions[] 绑定 | 按数组索引逐个渲染: `diag.node.N.condText.K.cond.0`, `diag.node.N.condText.K.cond.1`, ... — 通过 `data-cond-append` 和 `data-cond-delete` 管理 |
| 12 | event-bindings mode 路由 | `bindEditorActions` 按 `state.mode` 路由: quest→`setByPath`, NPC→`setNpcByPath`, dialogue→`setDialogueByPath` |
| 13 | center-renderer mode 路由 | 新建 `renderNpcCenter` / `renderDialogueCenter`, 取代 quest 的 `renderCenterEditor`, 由 `app.js` 按 mode 调用 |
| 14 | status-renderer mode 适配 | 三种 mode 各自渲染: quest 显示 `state.quest.meta.file`, NPC 显示 `state.npc.q.entityType`, dialogue 显示 `state.dialogue.q.id` |
| 15 | "新建架构" mode 感知 | NPC mode → 创建 `createNpcSkeleton()`; dialogue mode → 创建 `createDialogueSkeleton()` |
| 16 | 首节点自动设 startNodeId | 在空 dialogue 中添加第一个节点时, `startNodeId` 自动设为该节点的 `nodeId` |
| 17 | Gson visualConfig null | `QuestVisualSpec visualConfig = null` 时 Gson 不序列化; 非 null 时完整序列化; 编辑器导出清洗时保留 null→省略 |
| 18 | crossBtn mode 切换扩展 | 跨文件校验按钮在 non-quest mode 下也强制切换到 quest mode (保持现有行为), 未来扩展 NPC/dialogue 独立校验 |
| 19 | 拖拽 overlay 动态文案 | `bindDragAndDropImport` 按 mode 切换 overlay 文字 |
| 20 | 导入到库的 type 自动检测 | `importToLibrary` 通过 `detectJsonType` 确定 registry 类型, 自动调用 `importToRegistry(state, json, type)` |
