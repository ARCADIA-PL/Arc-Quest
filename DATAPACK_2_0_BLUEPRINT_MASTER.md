# DATAPACK_2_0_BLUEPRINT_MASTER.md

## 文档定位

这是 Arc Quest 数据包 2.0 的**施工主文档**。目标是将任务数据包体系升级为：

1. 可完整复刻 `EpicMainlineDemo` 与 `CollectionCodexDemo`
2. 具备独立的数据包加载/读取系统
3. 仅重载 Arc Quest 自有数据包（不重置全局 datapack）
4. 数据包读写路径固定在 `@datapack`（而非模组 `data/` 目录）
5. 采用“分阶段闸门制”：**每阶段必须全量实现 + 单元测试全绿，方可进入下一阶段**

---

## 核心约束（必须遵守）

### C1. 重载范围约束
- 禁止使用全局 `reload` 作为主路径。
- 允许存在运维兜底命令，但主链路必须是 Arc Quest 自有重载：
  - 仅刷新 Arc Quest 任务数据
  - 不影响其它模组数据包状态

### C2. 路径约束
- 任务数据包读写主路径：`@datapack`
- 明确禁止将编辑产物落在模组 `src/main/resources/data/...` 作为运行时主路径。
- 开发态样例可保留在 resources，但运行态加载来源必须可切换到 `@datapack`。

### C3. 阶段闸门约束
- 每阶段必须满足：
  1) 功能全量完成
  2) 阶段单元测试全绿
  3) 阶段文档更新
  4) 阶段验收记录完成
- 未过闸门不得进入下一阶段。

---

## 目标架构（Datapack 2.0）

```text
@datapack (Arc Quest runtime root)
   └─ quests/*.json
        ↓
DatapackPathResolver
        ↓
QuestSpecResourceLoader2 (ArcQuest only)
        ↓
QuestSpecJsonReader + Validator
        ↓
QuestSpecCompiler
        ↓
QuestRegistry (datapack partition only refresh)
        ↓
HotReloadService (delta/apply/report)
```

---

## 阶段总览

- **Phase-1**：路径与加载源重构（切到 `@datapack`）
- **Phase-2**：ArcQuest 局部热重载链路（不触发全局重载）
- **Phase-3**：Demo 复刻一致性（Epic/Collection 完整对齐）
- **Phase-4**：稳定性与运维化（可诊断、可回滚、可观测）

> 详细施工见各阶段文档：
> - `DATAPACK_2_0_PHASE_1_PATH_AND_LOADER.md`
> - `DATAPACK_2_0_PHASE_2_HOT_RELOAD.md`
> - `DATAPACK_2_0_PHASE_3_DEMO_PARITY.md`
> - `DATAPACK_2_0_PHASE_4_STABILITY_OPS.md`

---

## 交付定义（Definition of Done）

满足以下全部条件才算 Datapack 2.0 完工：

1. `@datapack` 成为唯一运行时任务数据主路径
2. ArcQuest 热重载不依赖全局 `reload`
3. `EpicMainlineDemo` 与 `CollectionCodexDemo` 可由数据包完整复刻
4. 复刻一致性测试与热重载测试全绿
5. 出错可定位（资源路径、字段路径、错误码）
6. 提供运行手册与回滚策略

---

## 执行顺序（强制）

1. 先完成 Phase-1 并过闸门
2. 再完成 Phase-2 并过闸门
3. 再完成 Phase-3 并过闸门
4. 最后完成 Phase-4 并过闸门

禁止跨阶段并行开发。
