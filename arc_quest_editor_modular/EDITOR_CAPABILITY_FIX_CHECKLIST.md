# Arc Quest 数据包编辑器修正清单（对齐 DATAPACK_FULL_CAPABILITIES_LIST）

> 范围：`arc_quest_editor_modular`
>
> 目标：将编辑器能力与 `DATAPACK_FULL_CAPABILITIES_LIST.md` 严格对齐，消除“漏项 / 多项 / 命名偏差”。

---

## P0（必须先修，影响语义正确性）

## P0-1 ObjectiveType 标准化

### 问题
当前编辑器内部使用 `submit/custom_counter/reach` 等旧命名，且导出映射与后端标准不完全一致。

### 修正目标
统一到标准枚举：
- `KILL/COLLECT/TALK/INTERACT/REACH_LOCATION/DELIVER/CRAFT/OFFER/CUSTOM`

### 涉及文件
- `scripts/editors/objective-editor.js`
- `scripts/editors/shared.js`
- `scripts/core/import-normalizer.js`
- `scripts/core/export-normalizer.js`
- `scripts/core/factories.js`
- `scripts/renderers/event-bindings.js`

### 验收
- UI 下拉仅显示标准枚举。
- 导入导出无别名绕行（不再依赖 submit/reach/custom_counter 作为主模型）。

---

## P0-2 RewardType 标准化

### 问题
编辑器 reward 类型与后端标准不一致（`flag/currency` 等偏差）。

### 修正目标
仅保留并完整支持：
- `item`
- `flag_set`
- `flag_clear`
- `command`
- `var_set`
- `var_add`
- `var_subtract`
- `var_multiply`

### 涉及文件
- `scripts/editors/reward-editor.js`
- `scripts/core/import-normalizer.js`
- `scripts/core/export-normalizer.js`
- `scripts/core/factories.js`
- `scripts/renderers/event-bindings.js`

### 验收
- reward 类型与标准清单完全一致。
- 导出 JSON 不再出现非标准 reward type。

---

## P0-3 Condition 标准化（至少核心全覆盖）

### 问题
Transition/Condition UI 仅覆盖 `always/flag_set`，与后端支持集不一致。

### 修正目标
支持并可编辑：
- `always`
- `flag_set`
- `flag_not_set`
- `quest_completed`
- `variable`
- `and`
- `or`
- `not`

### 涉及文件
- `scripts/editors/phase-editor.js`
- `scripts/core/quest-shape.js`
- `scripts/core/import-normalizer.js`
- `scripts/core/export-normalizer.js`
- `scripts/renderers/event-bindings.js`

### 验收
- transition、enterCondition、unlockConditions 可视化编辑并正确导出。

---

## P0-4 Collection Rule 与后端规则集对齐

### 问题
当前 collection rule UI 混入非标准字段与类型。

### 修正目标
仅支持：
- `all_entries_complete`
- `completed_entry_count`
- `category_completed_count`
- `completed_entry_ratio`
- `category_completed_ratio`
- `and`
- `or`
- `not`

并移除/隐藏：
- `node_completed`
- `flag_set`（作为 collection rule 类型）
- `leftType/rightType/refId/expr` 这类非标准结构

### 涉及文件
- `scripts/editors/quest-editor.js`
- `scripts/core/quest-shape.js`
- `scripts/core/import-normalizer.js`
- `scripts/core/export-normalizer.js`
- `scripts/renderers/event-bindings.js`

### 验收
- collection rules 导出结构与后端 compiler 预期一致。

---

## P1（完整能力补齐，提升可用性）

## P1-1 Quest 顶层漏项补齐

### 补齐项
- `unlockConditions`
- `flagsToSetOnAccept`
- `completionPolicy`
- `completionRequiredCount`
- `completionTargetPhaseId`
- `timeLimitType`
- `timeLimitValue`
- `chapterStartSound/chapterFailSound/chapterCompleteSound`
- `relatedMarks`

### 涉及文件
- `scripts/editors/quest-editor.js`
- `scripts/core/quest-shape.js`
- `scripts/core/import-normalizer.js`
- `scripts/core/export-normalizer.js`

---

## P1-2 Phase 漏项补齐

### 补齐项
- 标准 `choices[]`（text/flagToSet/targetPhaseId/visibleCondition）
- `tradeShopId`
- `phaseStartSound/phaseCompleteSound`
- `relatedMarks`
- `visualConfig`（phase 级）

### 涉及文件
- `scripts/editors/phase-editor.js`
- `scripts/core/quest-shape.js`
- `scripts/core/import-normalizer.js`
- `scripts/core/export-normalizer.js`

---

## P1-3 Objective 扩展字段完整可视化

### 补齐项
- `npcId`
- `itemTag`
- `x/y/z/radius`
- `countMode/countBase/countPerLevel/countMin/countMax`
- `extraData`（键值编辑）
- `relatedMarks`

### 涉及文件
- `scripts/editors/objective-editor.js`
- `scripts/editors/shared.js`
- `scripts/core/import-normalizer.js`
- `scripts/core/export-normalizer.js`

---

## P1-4 Collection 配置漏项补齐

### 补齐项
- `trackerPresentationMode`
- `collectionPresentationMode`
- `visibilityConditions`
- `repeatableProgress`
- `repeatableCompletion`
- entry 级 `rewardNodes`

### 涉及文件
- `scripts/editors/quest-editor.js`
- `scripts/core/quest-shape.js`
- `scripts/core/import-normalizer.js`
- `scripts/core/export-normalizer.js`

---

## P1-5 Visual 能力补齐

### 补齐项
- `icons` 编辑（texture/scale，按位置键）
- quest/phase 视觉结构完整导入导出

### 涉及文件
- `scripts/editors/visual-editor.js`
- `scripts/core/import-normalizer.js`
- `scripts/core/export-normalizer.js`

---

## P1-6 Mark 能力补齐

### 补齐项
- `relatedMarks` 完整结构编辑：
  - `target`
  - `activateWhen`
  - `deactivateWhen`
  - `markerType`
  - `priority`
  - `maxDistance`
  - `refreshTicks`
  - `trackMovingEntity`
  - `oneShot`
  - `styleHints`

### 涉及文件
- `scripts/editors/quest-editor.js`
- `scripts/editors/phase-editor.js`
- `scripts/editors/objective-editor.js`
- `scripts/core/import-normalizer.js`
- `scripts/core/export-normalizer.js`

---

## P2（收敛历史模型，降低维护成本）

## P2-1 删除旧模型字段

### 清理项
- phase: `mode/parallelPhaseIds/choicePhaseIds/autoStart`（如与标准模型重复）
- objective: `submit/reach/custom_counter` 旧别名
- reward: `currency/flag(非 flag_set/flag_clear)`
- collection rule 非标准辅助字段

### 涉及文件
- `scripts/core/quest-shape.js`
- `scripts/core/import-normalizer.js`
- `scripts/core/export-normalizer.js`
- `scripts/renderers/event-bindings.js`

---

## P2-2 增加一致性校验与提示

### 目标
- 在编辑态即提示“非标准字段/枚举”。
- 导出前执行 schema-level 校验并给出阻断错误。

### 涉及文件
- `scripts/core/validators.js`
- `scripts/renderers/status-renderer.js`
- `scripts/renderers/event-bindings.js`

---

## 交付与验收清单

1. 字段能力对齐检查表（勾选制）
2. 导入导出回归：标准样本 round-trip
3. 类型覆盖回归：Objective/Reward/Condition/CollectionRule 全枚举
4. 兼容策略说明（旧字段迁移、自动转换、弃用时间）

---

## 推荐实施顺序（强制）

1. 先做 P0（语义正确性）
2. 再做 P1（能力完整性）
3. 最后做 P2（历史收敛）
