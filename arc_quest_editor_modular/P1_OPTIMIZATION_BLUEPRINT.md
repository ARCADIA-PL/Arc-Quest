# P1 优化蓝图：消灭类型相关资源手写输入

## 前置门禁
只有当 `P0` 已全量完成并验收通过后，才能开始 P1。

若 P0 任一验收项未完成，P1 禁止启动。

---

## 目标
P1 的目标是：把 Objective、Reward、Visual 等类型相关字段从“裸文本录入”升级为“类型感知编辑器”。

P1 完成后，用户不再需要频繁手写资源 ID、对象引用和类型细节，而是通过上下文感知的表单完成配置。

---

## P1 范围定义
P1 只处理以下问题：

1. Objective 按类型切换专属编辑器
2. Reward 按类型切换专属编辑器
3. Tags / Flags / 路径字段改为更友好的结构化输入
4. 常见资源标识接入候选 / 自动补全能力

P1 不处理：

- collection / reward node 的复杂图形化块编辑
- 全局规则编排器
- 大规模 schema 驱动渲染重构

---

## P1 交付结果

### A. Objective 不再像写 JSON
Objective 编辑必须按 `type` 提供不同表单：

- `kill` -> `entityType`, `count`
- `collect` -> `itemId`, `count`
- `submit` -> `itemId`, `count`, `consumeOnSubmit`
- `talk` -> `npcId`, `dialogueId`
- `interact` -> `targetType`, `targetId`
- `custom_counter` -> `counterId`, `count`
- 其他类型 -> 合理 fallback editor

要求：

- 切换 type 后自动补齐默认字段
- 不展示与当前 type 无关的字段
- 所有资源字段具备候选或补全能力

### B. Reward 编辑器类型化
Reward 编辑不能只靠通用字段列表。

至少支持：

- item reward
- command reward
- flag reward
- currency / token 类 reward（若 schema 存在）

要求：

- 按 type 显示专属字段
- 隐藏无关字段
- 新建 reward 默认给出合法模板

### C. 常见数组字段不再手写逗号字符串
以下字段必须改造成结构化编辑：

- `tags`
- `flagsToSetOnComplete`
- 各类 flag / ref 列表

要求：

- 可添加 / 删除 tag chip
- 可回车创建
- 空值行为稳定

### D. 路径和资源标识输入改造
至少以下字段应支持建议 / 补全：

- `iconTexture`
- `itemId`
- `entityType`
- `npcId`
- `dialogueId`
- 其他常用 resource id

要求：

- 支持本地候选列表
- 无候选数据源时支持静态模板建议
- 无效值有即时提示

---

## 详细实施拆解

### 1. Objective Editor 重构
优先文件：

- `scripts/editors/objective-editor.js`
- `scripts/renderers/event-bindings.js`
- `scripts/core/factories.js`
- `scripts/core/validators.js`

实施内容：

- 以 objective `type` 为核心分发 UI
- 为每种类型定义字段 schema
- 将资源字段替换为 selector / autocomplete input
- 切换 type 时执行字段 normalize

完成标准：

- 用户编辑 objective 时不会看到大量不相关字段
- 常见 objective 配置可在 3 次点击内完成主要字段设置

### 2. Reward Editor 重构
优先文件：

- `scripts/editors/reward-editor.js`
- `scripts/core/factories.js`
- `scripts/renderers/event-bindings.js`

实施内容：

- 定义 reward type -> editor 映射
- 为常见 reward 提供模板和默认值
- 抽离 reward normalization 逻辑

完成标准：

- 新增 reward 后立即是可导出的合法结构
- 不需要用户先理解内部 schema 才能填写

### 3. Tags / Flags 结构化输入
优先文件：

- `scripts/editors/quest-editor.js`
- `scripts/editors/phase-editor.js`
- `styles/editor.css`
- `scripts/renderers/event-bindings.js`

实施内容：

- 抽出通用 chip editor
- 支持增删改、去重、空字符串过滤
- 替换逗号分隔字符串输入

完成标准：

- 用户不再需要维护 `a, b, c` 这类格式

### 4. 自动补全与候选源
优先文件：

- `scripts/core/paths.js`
- `scripts/core/utils.js`
- `scripts/core/state.js`
- 各 editor 文件

实施内容：

- 设计统一候选提供接口
- 先支持静态内建候选
- 预留未来接入 datapack 扫描的接口

完成标准：

- 至少 item / entity / dialogue / npc / texture 五类字段能获得建议能力

---

## P1 验收清单
全部完成才允许进入 P2：

- [x] Objective 按类型渲染专属编辑器
- [x] type 切换后字段自动 normalize
- [x] Reward 按类型渲染专属编辑器
- [x] Tags 改为 chip editor
- [x] Flags 改为结构化输入
- [x] 常见资源字段具备候选 / 自动补全
- [x] 无效资源值具备即时提示
- [x] 导入旧 JSON 后仍可正确回显
- [x] 导出结构与 datapack 兼容

### P1 验证记录

- 导入/导出链路样例：
  - `run/arc_quest/datapack/quests/epic_prologue.json`
  - `run/arc_quest/datapack/quests/collection_codex_demo.json`
- 回归方式：`normalizeImportedQuest` ->（模拟 P1 编辑：objective/reward/tags/flags/iconTexture）-> `exportQuestToDatapack`
- 回归结果：2/2 样例通过
  - objective `talk` 映射为导出 `TALK` 正常
  - phase reward `currency`、quest reward `flag` 导出结构正常
  - `flagsToSetOnComplete` 保留正常

---

## P1 禁止进入 P2 的条件
只要出现以下任意一项，就不能进入 P2：

- Objective 编辑仍然主要依赖通用文本输入
- Reward 编辑仍然主要依赖手写 schema 字段
- Tags / Flags 仍靠逗号字符串维护
- 常见资源字段无候选支持
- 类型切换后字段结构容易脏化

---

## P1 完成定义
P1 完成意味着：

- 用户编辑 objective / reward 时感知到的是“任务设计”，不是“JSON 结构”
- 常见资源字段不再需要频繁手敲完整 ID
- 文本与结构化输入已经分层清晰

只有这些目标全部达成，才允许进入 P2。
