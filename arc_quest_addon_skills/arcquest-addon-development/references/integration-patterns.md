# 大型附属集成模式

## 适用目标

本页用于把复杂剧情、任务、NPC、商店、Guide 和客户端表现拆成可维护的 ArcQ 附属。重点不是复制某个项目的目录，而是识别状态所有权、注册边界和跨模块契约。

## 从需求到模块

| 业务需求 | ArcQ 所有者 | 附属职责 |
| --- | --- | --- |
| 任务定义和阶段拓扑 | Quest Definition | 提供稳定 ID、Builder 配置和翻译键 |
| 玩家任务进度 | `ArcQuestPlayer` / `QuestRuntimeData` | 通过 Handler 推进，不复制完整进度 |
| 剧情 NPC 对话 | Dialogue/NPC | 选择树、实体绑定和服务端动作 |
| 教程内容 | Guide | 定义页面、发放时机和额外奖励幂等 |
| 商店与抽奖 | Trade/Gacha | 定义成本、奖励、限制和外部经济适配 |
| 导航目标 | Marker | 提供目标身份、解析器和表现提示 |
| 画像、页签、相机投影 | 客户端 Registry | 只处理同步后的表现，不决定业务结果 |
| 附属独有剧情事实 | 附属持久层 | 保存 ArcQ 不负责的 actor、经济或系统状态 |

## 推荐目录组织

```text
addon/
  ArcQuestAddonBootstrap
  quest/
    ChapterQuestContent
    QuestProgressAdapter
  dialogue/
    ChapterDialogueContent
    NpcDialogueExtensions
  guide/
    GuideCatalog
    GuideRewardHandler
  commerce/
    TradeContent
    GachaContent
  marker/
    MarkerResolvers
  runtime/
    ArcQuestStateFacade
    VisibilitySnapshotService
  client/
    ArcQuestClientExtensions
```

每个 `Content` 类只构建定义；`Adapter` 把外部事件转成 ArcQ 高层调用；`Facade` 统一只读查询；客户端扩展完全隔离。不要让一个章节 Setup 同时负责注册、玩家状态、异步回调和渲染。

## 薄 Builder 门面

当几十个任务共享分类、主题色、音效、可放弃策略和动态文本 fallback 时，可以建立薄门面：

```text
AddonQuestBuilders.create(id)
  -> QuestBuilder.create(id)
  -> 注入附属通用默认值
  -> 返回 QuestBuilder
```

门面不得注册 Definition、读取玩家、保存“当前 Builder”静态变量或屏蔽 ArcQ 原始方法。返回 ArcQ Builder 可让业务模块继续使用新 API，也降低附属门面跟随 ArcQ 演进的成本。

## 两套状态的事实来源

大型附属常同时拥有自身剧情状态和 ArcQ Quest 状态。每个字段必须只有一个事实来源：

- Quest/Phase/Objectives 是否完成，以 ArcQ 为准；
- 外部战斗结果、剧情 actor 生命周期、货币事务，以附属系统为准；
- 恢复逻辑只把外部系统已经确认的事实补偿到 ArcQ；
- 不把完整 `QuestRuntimeData` 复制进附属 NBT；
- 补偿必须逐项检查、可重复执行且不会重复奖励。

## 跨模块流程

典型“NPC 接任务，访问多个设施，阅读 Guide，领取奖励”流程：

```text
Dialogue choice
  -> QuestProgressAdapter.ensureAccepted
  -> bootstrap Phase 正常完成
  -> fan-out 激活多个访问 Phase
  -> 每个 Phase 产生独立 tracking Marker
  -> TradeOpenedEvent 推进对应 custom Objective
  -> Phase enter 自动 grant Guide
  -> Guide 最终页事件执行附属幂等奖励
  -> 全部分支完成后 ArcQ 完成 Quest 并设置 flag
```

完整代码由以下资产协作展示：

- [ScenarioParallelQuestContent.java](../assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioParallelQuestContent.java)
- [ScenarioConditionalDialogueContent.java](../assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioConditionalDialogueContent.java)
- [ScenarioCommerceQuestBridge.java](../assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioCommerceQuestBridge.java)
- [ScenarioGuideRewardHandler.java](../assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioGuideRewardHandler.java)

## 兼容性边界

公共依赖优先级：

1. `ArcQuestAPI`、Builder、Service、Facade；
2. `ArcQuestRegistrationEvent` 和 `api.event`；
3. 明确标为扩展点的 Registry；
4. 只读查询公开 Registry；
5. 最后才评估 Mixin。

不要把“当前是 public”自动理解为稳定扩展点。HUD renderer、Screen 内部布局、网络 handler、RuntimeData 私有布局和 Mixin descriptor 都会随内部重构变化。

## 验证矩阵

- 注册：重复 ID、错误总线、freeze 后注册、缺失依赖；
- Quest：线性、并行、重登、强制完成、奖励和 flag；
- Dialogue：条件分支、关闭恢复、未命中扩展时保留原交互；
- Guide：静默、延迟弹窗、seen、末页、奖励去重；
- Trade/Gacha：条件、冷却、限购、成本失败、保底；
- Marker：目标消失、缓存回退、跨维度、玩家离线清理；
- 客户端：GUI scale 2/3/4、小窗口、专用服务器类加载。
