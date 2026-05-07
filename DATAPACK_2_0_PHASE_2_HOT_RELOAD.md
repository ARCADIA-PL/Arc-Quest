# DATAPACK_2_0_PHASE_2_HOT_RELOAD.md

## Phase-2 目标

建立 ArcQuest 专用热重载主链路：
- 仅重载 ArcQuest 任务数据
- 不触发/不依赖全局 datapack 重载
- 重载过程可观测、可回滚、可诊断

---

## 实现范围（全量）

### P2-1 HotReloadService
新增：
- `ArcQuestDatapackHotReloadService`

职责：
1. 扫描 `@datapack`
2. validate -> compile -> apply
3. 生成 reload 报告（新增/更新/删除/失败）

### P2-2 分区重载策略
改造：
- `QuestRegistry` 的 datapack 分区刷新逻辑

要求：
1. 仅清空并重建 DATAPACK 分区
2. CODE 分区保持不动
3. 合并后 registry 一致性检查

### P2-3 命令入口与权限
改造：
- `arcquest_reload` 命令

要求：
1. 默认走专用热重载服务
2. 回包包含统计：scanned/loaded/failed/active
3. 明确标识“未触发全局 reload”

---

## 单元测试（必须全部通过）

1. `ArcQuestDatapackHotReloadServiceTest`
   - 单次重载成功
   - 错误文件不阻断其它任务
2. `QuestRegistryPartitionReloadTest`
   - datapack 分区刷新不影响 code 分区
3. `ArcQuestReloadCommandTest`
   - 命令回执字段完整

---

## 阶段验收清单

- [ ] 可在不执行全局 reload 下完成任务热更新
- [ ] 重载后 QuestRegistry 立即生效
- [ ] 失败项不导致整体崩溃
- [ ] 单元测试全绿

---

## 退出条件（Gate）

满足以下全部才可进入 Phase-3：
1. P2-1/P2-2/P2-3 全完成
2. 本阶段测试 100% 通过
3. 运维日志字段固定并文档化
4. 无 P0/P1 缺陷
