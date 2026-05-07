# DATAPACK_2_0_PHASE_3_DEMO_PARITY.md

## Phase-3 目标

通过数据包完整复刻：
- `EpicMainlineDemo`
- `CollectionCodexDemo`

并建立可重复的“语义一致性”验证体系。

---

## 实现范围（全量）

### P3-1 EpicMainline 数据包复刻
要求：
1. Quest 基础元信息一致
2. Phase/Objective/Transition 语义一致
3. 条件、标记、奖励、分支行为一致

### P3-2 CollectionCodex 数据包复刻
要求：
1. collectionConfig/category/entry/rules/rewardNodes 一致
2. scope/grantMode/countingMode 等关键枚举一致
3. 可在运行态正确驱动收集逻辑

### P3-3 一致性比对器
新增：
- `QuestParityComparator`（建议）

职责：
1. 对比 code demo 与 datapack quest 的结构化语义
2. 输出差异报告（path + expected + actual）

---

## 单元测试（必须全部通过）

1. `DatapackEpicMainlineParityTest`
2. `DatapackCollectionCodexParityTest`
3. `QuestParityComparatorTest`

建议覆盖维度：
- phase ids
- objective types + params
- transitions + conditions
- flags + rewards
- collection categories/rules/reward nodes

---

## 阶段验收清单

- [ ] 两个 Demo 的数据包复刻文件存在且可加载
- [ ] 比对器报告无结构性差异（允许非语义字段白名单）
- [ ] 关键链路手工验证通过（进入/推进/完成/奖励）
- [ ] 单元测试全绿

---

## 退出条件（Gate）

满足以下全部才可进入 Phase-4：
1. P3-1/P3-2/P3-3 全完成
2. 本阶段测试 100% 通过
3. 差异白名单经过评审
4. 无 P0/P1 缺陷
