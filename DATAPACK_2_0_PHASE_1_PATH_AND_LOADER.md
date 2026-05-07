# DATAPACK_2_0_PHASE_1_PATH_AND_LOADER.md

## Phase-1 目标

完成运行时任务数据路径与加载器重构：
- 运行态主路径切换到 `@datapack`
- 建立 ArcQuest 专用路径解析与加载入口
- 不改变 QuestSpec 语义，仅改变来源与读写策略

---

## 实现范围（全量）

### P1-1 路径解析层
新增：
- `DatapackPathResolver`（建议）

职责：
1. 解析 `@datapack` 根目录
2. 提供 quests 子目录定位（如 `@datapack/quests`）
3. 提供路径合法性校验（禁止越界）

### P1-2 读取链路重构
新增/改造：
- `QuestSpecResourceLoader2`

要求：
1. 支持从 `@datapack` 读取 quest json
2. 输出资源标识、来源路径、解析结果
3. 读取错误必须结构化上报（不能 silent ignore）

### P1-3 写入链路重构
新增/改造：
- `QuestDatapackWriter`（建议）

要求：
1. 所有运行态导出写入 `@datapack`
2. 写入采用原子策略（临时文件 + replace）
3. 失败不污染目标文件

---

## 单元测试（必须全部通过）

1. `DatapackPathResolverTest`
   - 根路径解析正确
   - 非法路径被拒绝
2. `QuestSpecResourceLoader2Test`
   - 可读取有效 json
   - 异常文件有错误报告
3. `QuestDatapackWriterTest`
   - 正常写入
   - 写入失败回滚

---

## 阶段验收清单

- [ ] 运行态主路径已切换到 `@datapack`
- [ ] 读取链路不再依赖模组 resources 目录
- [ ] 错误可诊断（文件 + 字段 + 原因）
- [ ] 单元测试全绿

---

## 退出条件（Gate）

满足以下全部才可进入 Phase-2：
1. P1-1/P1-2/P1-3 全完成
2. 本阶段测试 100% 通过
3. 文档更新（类图/时序图/异常码）
4. 无 P0/P1 缺陷
