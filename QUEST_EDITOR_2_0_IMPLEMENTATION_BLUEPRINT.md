# QuestEditorScreen 2.0 实施蓝图（详细版）

## 1. 范围与目标
在现有 1.x（已具备：导入、基础编辑、画布拖拽、choice 编辑、导出 guard）基础上，建设 2.0：

- 可撤销/重做（Undo/Redo）
- Dirty/Savepoint 状态治理
- 导出报告化与强制导出策略
- 连接编辑闭环（创建/选择/删除）
- 多选与对齐分布
- 画布体验增强（小地图/自动布局）

本轮先落地 **M1：编辑可靠性基础设施**。

---

## 2. 分阶段路线

### M1（本轮起步）：可靠性基础设施
1. 命令总线 `EditorCommandBus`
2. 命令接口 `EditorCommand`
3. 控制器 revision/dirty/savepoint
4. 将关键变更入口接入命令执行（先覆盖高频入口）
5. 工具栏增加 Undo/Redo 按钮与 dirty 展示

验收：
- 有改动时 dirty=true
- Undo/Redo 可回退/重放
- 导入后 savepoint 重置
- 导出成功后 savepoint 对齐

### M2：连接编辑闭环
1. 连接创建状态机（拖线）
2. 连接删除与选择联动
3. Connection Inspector（先只读后可编辑）

### M3：多选与排版
1. 框选、多选
2. 对齐/分布命令
3. 批量拖拽

### M4：可视增强
1. 小地图
2. 自动布局 2.0
3. 问题导航

---

## 3. 关键设计

### 3.1 命令模型
- `EditorCommand#apply()`：执行
- `EditorCommand#revert()`：撤销
- `EditorCommand#description()`：用于 UI 提示

`EditorCommandBus`：
- `execute(cmd)`：成功后 push undo，clear redo
- `undo()` / `redo()`
- `canUndo()` / `canRedo()`
- `lastUndoDescription()` / `lastRedoDescription()`

### 3.2 快照策略（M1）
M1 采用“编辑器快照法”保证正确性优先：
- 变更前保存 `(EditableQuest + EditorSelection)`
- 执行变更
- 变更后保存快照
- undo/redo 直接恢复快照

说明：后续可将高频输入改成细粒度增量命令以优化性能。

### 3.3 Revision/Dirty
控制器字段：
- `long revision`
- `long savepointRevision`

规则：
- 成功执行命令：`revision++`
- 导入成功：`revision=0; savepointRevision=0`
- 导出成功：`savepointRevision=revision`
- `isDirty = revision != savepointRevision`

---

## 4. 代码落地清单（M1）

### 新增
- `EditorCommand.java`
- `EditorCommandBus.java`

### 修改
- `QuestEditorController`
  - 接入命令总线
  - 接入 revision/dirty/savepoint
  - 高频编辑方法改为 `executeMutation(...)`
  - 提供 `undo()/redo()/canUndo()/canRedo()/isDirty()`
- `QuestEditorToolbar`
  - 增加 Undo/Redo 按钮
  - 状态栏展示 dirty

---

## 5. 风险与约束
1. 快照基于 mapper 往返，性能中等：M1 可接受。
2. 快照恢复会覆盖临时 UI 状态：通过保留 selection 缓解。
3. 旧方法可能漏接命令：M1 先覆盖高频入口，M1.1 做全量覆盖清点。

---

## 6. M1 完成定义（DoD）
- 至少这些操作可撤销：
  - Quest/Phase 基础编辑
  - Objective 增删改移
  - Choice 增删改
  - 节点移动
- 导入后 dirty=false
- 导出成功后 dirty=false
- 编译通过
