# P2 优化蓝图：复杂结构可视化与系统化编辑

## 前置门禁
只有当 `P0` 和 `P1` 都已经全量完成并通过验收后，才允许开始 P2。

任何一个前置阶段未闭环，P2 都不得启动。

---

## 目标
P2 的目标是：把 collection / reward node / completion rules / 高阶流程关系从“表单堆叠编辑”升级为“系统化、可视化、低出错率”的高级编辑体验。

P2 完成后，编辑器应从“可用表单工具”跃升为“可进行复杂 datapack 设计的专用工作台”。

---

## P2 范围定义
P2 聚焦以下内容：

1. Collection / Category / Reward Node 的可视化结构编辑
2. Completion Rule 的组合式编辑
3. 跨对象引用的统一引用系统
4. 高级校验、预览、批量修复与辅助生成

---

## P2 交付结果

### A. Collection 结构不再是深层表单嵌套
以下结构必须升级：

- `collectionConfig`
- `categories`
- `rewardNodes`
- `completionRules`

要求：

- 支持分组视图
- 支持卡片式 / 树式 / 图式切换
- 支持拖拽排序
- 支持快速新增模板

### B. Rule Editor 组合化
Completion rule 不应只是字段集合。

要求：

- 针对 rule type 提供专属编辑器
- 支持 AND / OR / 嵌套条件模型（若 schema 支持）
- 支持引用目标选择，而不是手写 `refId`
- 支持规则摘要预览

### C. 统一引用系统
到 P2 时，所有跨对象引用必须统一纳入引用系统：

- phase 引用
- category 引用
- reward node 引用
- completion rule ref 引用
- scopeRefId / refId 等内部指针

要求：

- 所有引用通过选择器建立
- 改名可联动
- 删除有影响分析
- 支持“查看谁引用了我”

### D. 高级辅助能力
P2 至少应包含以下高级能力中的大部分：

- 引用反查
- 未使用节点检测
- 无效规则检测
- 一键修复常见问题
- 批量重命名 / 批量生成 key
- 导出前结构审查面板

---

## 详细实施拆解

### 1. Collection Workspace 重构
优先文件：

- `scripts/editors/quest-editor.js`
- `scripts/renderers/side-panel-renderer.js`
- `scripts/renderers/tree-renderer.js`
- `styles/editor.css`

实施内容：

- 为 collection 单独提供 workspace
- 以 category / reward node 卡片方式呈现
- 支持层级折叠、排序、拖拽

完成标准：

- 用户不需要在超长表单中滚动寻找结构节点

### 2. Rule Builder
优先文件：

- `scripts/editors/shared.js`
- `scripts/renderers/event-bindings.js`
- `scripts/core/factories.js`
- `scripts/core/validators.js`

实施内容：

- 为 completion rule 建立 schema-aware builder
- 支持 rule summary
- 支持 rule type 迁移和 normalize

完成标准：

- 复杂规则可读、可改、可校验

### 3. Reference Graph / Impact Analysis
优先文件：

- `scripts/core/state.js`
- `scripts/core/utils.js`
- `scripts/renderers/graph-renderer.js`
- `scripts/renderers/side-panel-renderer.js`

实施内容：

- 抽出统一引用索引
- 增加被引用关系面板
- 删除对象前显示影响面
- 支持跳转到引用源

完成标准：

- 用户修改复杂结构时不再靠记忆判断副作用

### 4. Validation & Fix Tools
优先文件：

- `scripts/core/validators.js`
- `scripts/renderers/side-panel-renderer.js`
- `scripts/renderers/status-renderer.js`

实施内容：

- 增加分级错误类型
- 提供一键修复入口
- 增加导出阻断校验
- 提供 warnings / suggestions 区分

完成标准：

- 用户能在导出前明确知道结构风险与可修复项

---

## P2 验收清单
P2 必须全部完成以下内容：

- [x] Collection 拥有独立结构化工作区
- [x] Category / Reward Node 支持清晰层级编辑
- [x] Completion Rule 支持类型化 builder
- [x] `refId` / `scopeRefId` 等引用不再手打
- [x] 支持引用反查
- [x] 支持删除影响分析
- [x] 支持高级校验与建议
- [x] 支持常见问题一键修复
- [ ] 复杂 datapack 示例可在编辑器内稳定维护

### P2 阶段进展说明

- 已完成：
  - 引用系统（索引、反查、失效引用可见）
  - 删除影响分析（phase）
  - Collection 工作区（Card / Tree 视图）
  - Rule Builder（含 AND/OR 基础可视化子条件）
  - 一键修复（引用清理 + rule 默认值补齐）
- 待收口：
  - 复杂 datapack 示例的持续维护稳定性验证（需连续编辑场景回归）

---

## P2 完成定义
P2 完成意味着：

- 编辑器已不只是“表单壳子”
- 对复杂 datapack 结构具备系统化编辑能力
- 用户可以维护大型 quest / collection / reward network，而不必手动追踪内部引用

当 P2 全量完成后，编辑器才算真正跨过“可用”阶段，进入“高生产力专业工具”阶段。
