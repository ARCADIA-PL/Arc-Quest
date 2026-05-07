# DATAPACK_2_0_PHASE_4_STABILITY_OPS.md

## Phase-4 目标

将 Datapack 2.0 升级为可长期运维能力：
- 可观测
- 可回滚
- 可排障
- 可发布

---

## 实现范围（全量）

### P4-1 可观测性
新增/改造：
- reload 指标输出（日志/调试接口）

至少包含：
- scanned
- parsed
- validated
- compiled
- registered
- failed
- durationMs

### P4-2 回滚机制
新增：
- `DatapackSnapshotManager`（建议）

要求：
1. reload 前保留上一次有效快照
2. reload 失败可回滚到最近可用版本
3. 回滚结果有明确回执

### P4-3 运维工具化
新增：
- `arcquest_datapack_status`
- `arcquest_datapack_diff`

用于快速查看当前有效版本与差异。

### P4-4 文档与手册
输出：
1. 运行手册（路径、命令、故障排查）
2. 发布清单（上线前检查项）
3. 回滚手册（事故场景）

---

## 单元测试（必须全部通过）

1. `DatapackReloadMetricsTest`
2. `DatapackRollbackTest`
3. `DatapackStatusCommandTest`

---

## 阶段验收清单

- [ ] 重载过程具备完整指标
- [ ] 失败可一键回滚到有效版本
- [ ] 运维命令输出稳定可读
- [ ] 手册齐全
- [ ] 单元测试全绿

---

## 完工门槛（Final Gate）

仅当以下全部满足，Datapack 2.0 才可宣告完成：
1. Phase-1~4 全部过闸门
2. `@datapack` 作为唯一运行态任务路径
3. ArcQuest 热重载不依赖全局 reload
4. 两个 Demo 可由数据包复刻并通过 parity 测试
5. 关键运维场景有自动化测试与手册
