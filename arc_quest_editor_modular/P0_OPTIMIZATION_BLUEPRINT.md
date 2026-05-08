# P0 优化蓝图：消灭结构性手写输入

## 目标
P0 的唯一目标是：把当前编辑器里最容易出错、最不该由用户手写维护的“结构性字段”改造成自动化或半自动化控件。

完成 P0 之后，用户应当可以主要专注于任务内容设计，而不是手工维护 ID、引用链和翻译键。

---

## 进入条件
开始 P0 前必须满足：

- 当前模块化编辑器可正常打开
- 基础导入 / 导出 / 校验功能可用
- 当前 `phase` / `objective` / `graph` 编辑流程没有阻断性崩溃

---

## P0 范围定义
P0 只处理以下三类问题：

1. ID 自动生成与唯一性维护
2. Phase 之间引用关系的可视化选择
3. Translatable key 的自动生成与维护

P0 明确不处理：

- 复杂 objective 资源选择器
- reward / collection 高阶结构编辑器
- 资源浏览器 / autocomplete 数据源接入

---

## P0 交付结果
P0 完成后，必须全部达到以下结果：

### A. ID 不再默认靠手打维护
以下字段必须支持自动生成：

- Quest ID
- Phase ID
- Objective ID
- Collection Category ID
- Reward Node ID

要求：

- 新建时自动生成默认值
- 同层级内自动保证唯一性
- 用户允许手动改名，但改名后立即校验唯一性
- 改名冲突时给出明确错误提示

### B. Phase 引用关系不再靠逗号字符串手填
以下字段必须替换成结构化输入：

- `initialPhaseId`
- `parallelPhaseIds`
- `choicePhaseIds`
- `transitions[].targetPhaseId`

要求：

- 单引用字段使用单选控件
- 多引用字段使用多选 / chips 控件
- 候选项来自当前 quest 的 phase 列表
- 删除 phase 时自动清理失效引用
- 重命名 phase id 时自动联动所有引用

### C. 文本 key 默认自动维护
以下字段必须支持 key 自动生成机制：

- quest `title`
- quest `description`
- phase `title`
- phase `description`
- objective `text`

要求：

- 当模式为 `translatable` 时，系统默认生成 key
- key 生成规则可预测、稳定、可重复
- 修改 quest id / phase id / objective id 后，可选择同步更新 key
- 支持显式切换 `literal / translatable`
- 对用户暴露“自动生成 / 手动覆盖”两种模式

---

## 详细实施拆解

### 1. ID 体系改造
涉及文件优先级：

- `scripts/core/factories.js`
- `scripts/core/validators.js`
- `scripts/core/quest-shape.js`
- `scripts/renderers/event-bindings.js`
- `scripts/editors/quest-editor.js`
- `scripts/editors/phase-editor.js`
- `scripts/editors/objective-editor.js`

实施内容：

- 抽出统一 ID 生成函数
- 抽出统一 slug / sanitize 规则
- 为 quest / phase / objective / category / node 提供唯一性检查
- 新建元素时不再散落拼接字符串，而是统一走 factory
- 表单中增加唯一性状态提示

完成标准：

- 任意新增 20 个 phase / objective，不会出现重复 ID
- 手工改名冲突时，右侧校验与字段态提示一致

### 2. Phase 引用编辑器改造
涉及文件优先级：

- `scripts/editors/quest-editor.js`
- `scripts/editors/phase-editor.js`
- `scripts/renderers/event-bindings.js`
- `scripts/renderers/graph-renderer.js`
- `scripts/core/state.js`

实施内容：

- 增加 phase 候选项构建器
- 将字符串输入替换为结构化 selector UI
- 为 transition editor 增加 target phase dropdown
- phase 删除时做引用清理
- phase id 变更时更新所有相关引用

完成标准：

- 用户不再需要输入 `phase_xxx, phase_yyy` 这类字符串
- 任意删除被引用 phase 后，编辑器不会保留脏引用
- graph 与表单数据保持一致

### 3. 文本 key 自动化
涉及文件优先级：

- `scripts/core/utils.js`
- `scripts/core/factories.js`
- `scripts/renderers/event-bindings.js`
- `scripts/editors/quest-editor.js`
- `scripts/editors/phase-editor.js`
- `scripts/editors/objective-editor.js`

实施内容：

- 定义 quest / phase / objective key 模板
- 增加“自动 key”元状态
- 在重命名关键 ID 时触发 key 重算
- 用户手动覆盖后，停止自动覆盖

建议模板：

- `arc_quest.quest.{questId}.title`
- `arc_quest.quest.{questId}.description`
- `arc_quest.phase.{questId}.{phaseId}.title`
- `arc_quest.phase.{questId}.{phaseId}.description`
- `arc_quest.objective.{questId}.{phaseId}.{objectiveId}`

完成标准：

- 新建内容时自动得到合法 key
- 大多数用户无需手写 key 即可完成配置

---

## P0 验收清单
以下全部完成，P0 才算完成：

- [x] Quest / Phase / Objective / Category / Reward Node 新建默认 ID 全自动
- [x] 重名校验全部生效
- [x] `initialPhaseId` 改为单选
- [x] `parallelPhaseIds` 改为多选
- [x] `choicePhaseIds` 改为多选
- [x] `targetPhaseId` 改为单选
- [x] 删除 / 改名 phase 后自动维护引用
- [x] title / description / objective text key 自动生成
- [x] literal / translatable 切换行为正确
- [x] 导出 JSON 结构兼容现有 datapack 规范
- [x] 导入旧 JSON 后可正常回显与继续编辑

### P0 验证记录

- 导入/导出链路验证样例：
  - `run/arc_quest/datapack/quests/epic_prologue.json`
  - `run/arc_quest/datapack/quests/collection_codex_demo.json`
- 验证方式：`normalizeImportedQuest` -> `exportQuestToDatapack`
- 验证结果：2/2 样例通过，导出结果包含合法 `id`、`phases`、`initialPhaseId`

---

## P0 禁止进入 P1 的条件
只要出现以下任意一项，就禁止进入 P1：

- 仍有核心 phase 引用字段依赖手填字符串
- 仍有核心文本 key 依赖默认手写
- ID 唯一性校验未闭环
- phase 改名 / 删除后引用不会自动维护
- 导入导出兼容性未验证

---

## P0 完成定义
P0 完成不是“功能能用”，而是：

- 结构性字段的手写负担显著下降
- 编辑器不再要求用户手工维护 phase 引用关系
- 编辑器不再要求用户默认手写 translatable key

只有以上目标全量实现，才允许进入 P1。
