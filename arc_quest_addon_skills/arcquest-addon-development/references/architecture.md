# Arc Quest 代码附属开发架构

## 适用基线

本 Skill 集以当前 Arc Quest `master` 源码为唯一 API 依据：

| 项目 | 基线 |
| --- | --- |
| Minecraft | `1.20.1` |
| Loader | Forge `47.4.20` |
| Mappings | Mojang Official `1.20.1` |
| Java 源码级别 | Java 17 |
| Gradle Wrapper | Gradle 8.8 |
| Arc Quest | `1.0.6` |
| 模组 ID | `arc_quest` |
| 基础包 | `org.arcadia.arc_quest` |

不要把 1.21.1 NeoForge、Fabric、Architectury 或其他映射版本的示例直接写进 1.20.1 附属。大型附属的业务组织可以借鉴，但具体符号必须以本分支源码为准，详见 [大型附属集成模式](integration-patterns.md)。

## 附属能扩展什么

代码附属通过 Java API 注册定义、监听运行时事件或挂接正式客户端扩展点。它适合：

- 构建 `QuestDefinition`、`PhaseDefinition`、`ObjectiveEntry` 和 `IReward`；
- 构建 `DialogueTree`，为自有或第三方实体接入对话；
- 构建 Guide、Trade、Gacha 定义；
- 注册 Marker 目标解析器和客户端 Marker 表现解析器；
- 读取服务端玩家 ArcQ 状态，并通过公开 Handler/Service 推进业务；
- 监听任务、对话、Guide、交易、抽奖、玩家状态和重载事件；
- 通过公开客户端 Registry 调整对话叠层、日志页签和 Marker 表现。

本 Skill 集不覆盖 JSON 数据包、Web 编辑器、ArcQ 自身内部重构或跨版本移植。

## ArcQ 的核心分层

| 层 | 代表类型 | 附属职责 |
| --- | --- | --- |
| 稳定门面 | `ArcQuestAPI` | 注册和查询公开定义，避免依赖 Registry 内部细节 |
| Builder/Definition | `QuestBuilder`、`DialogueTreeBuilder`、`GuideBuilder` | 在注册窗口构建不可变业务定义 |
| Registry | `QuestRegistry`、`DialogueRegistry`、`GuideRegistry` | 只在公开门面缺少能力时直接查询，不管理冻结 |
| 运行时 Service | `QuestProgressHandler`、`GuideUnlockService`、`QuestTrackingFacade` | 在服务端主线程执行权威状态变更 |
| 玩家状态 | `ArcQuestPlayerManager`、`ArcQuestPlayer`、`QuestRuntimeData` | 读取状态；只有公开的 flag/variable 等低层字段适合直接修改 |
| 事件/客户端扩展 | `api.event`、客户端 Registry | 观察或拦截生命周期，扩展表现，不复制核心流程 |

### 定义与运行时必须分开

`QuestDefinition` 描述“这个任务是什么”，`QuestRuntimeData` 描述“某位玩家进行到哪里”。不要在 Definition 中保存玩家、世界、实体或可变集合，也不要通过直接改 RuntimeData 来模拟完成。

正确调用链是：

```text
附属业务事件
  -> QuestProgressHandler / GuideUnlockService / Trade/Gacha 服务端入口
  -> ArcQ 校验当前定义和玩家状态
  -> 修改权威运行时数据
  -> 结算奖励、flag、Marker、后续阶段
  -> 发布 Forge 运行时事件
  -> 持久化并同步客户端
```

## 注册生命周期

ArcQ 在自己的 `FMLCommonSetupEvent#enqueueWork` 中按下面顺序工作：

```text
注册 ArcQ 内置内容
  -> 发布 ArcQuestRegistrationEvent.Quest
  -> 发布 ArcQuestRegistrationEvent.Dialogue
  -> 发布 ArcQuestRegistrationEvent.Npc
  -> 发布 ArcQuestRegistrationEvent.Trade
  -> 发布 ArcQuestRegistrationEvent.Gacha
  -> 发布 ArcQuestRegistrationEvent.Guide
  -> 注册网络包
  -> 冻结各模块 Registry
```

这些事件实现 `IModBusEvent`，只能在附属 MOD 总线监听。所有 `api.event` 运行时事件在 `MinecraftForge.EVENT_BUS` 上发布。

附属构造器只完成监听器注册，不要在构造器或静态初始化块直接调用 `ArcQuestAPI.register*`。定义注册应位于对应 `ArcQuestRegistrationEvent` 回调内；客户端扩展应位于 `Dist.CLIENT` 隔离入口。

## 模块选择

| 需求 | 子 Skill | 首要参考 |
| --- | --- | --- |
| Gradle、`mods.toml`、注册时机 | `$arcquest-addon-lifecycle` | `setup.md`、`registration.md` |
| Quest/Phase/Objective/Reward | `$arcquest-addon-quests` | `builders.md`、`runtime.md`、`scenario-recipes.md` |
| Dialogue/NPC | `$arcquest-addon-dialogue-npc` | `dialogue-api.md`、`npc-runtime.md`、`scenario-recipes.md` |
| Guide | `$arcquest-addon-guides` | `api.md`、`runtime.md`、`scenario-recipes.md` |
| Trade/Gacha | `$arcquest-addon-trade-gacha` | `trade.md`、`gacha.md`、`scenario-recipes.md` |
| Marker | `$arcquest-addon-markers` | `api.md`、`runtime.md`、`scenario-recipes.md` |
| 玩家状态和外部系统推进 | `$arcquest-addon-player-runtime` | `player-state.md`、`conditions-tracking.md`、`scenario-recipes.md` |
| 运行时事件和客户端扩展 | `$arcquest-addon-events-client` | `events.md`、`client.md`、`scenario-recipes.md` |

## ID 与命名空间

附属创建的所有持久或跨系统 ID 必须使用附属自己的 namespace：

```text
example_addon:intro_quest
example_addon:professor_intro
example_addon:getting_started
example_addon:relic_shop
```

不要依赖某些 String 重载在缺少 namespace 时回退到 `arc_quest`。该回退是 ArcQ 内置内容的便利行为，会让附属 ID 落入错误命名空间。

稳定 ID 包括：Quest、Phase、Objective、Choice、Guide、Group、Shop、Gacha item、Marker、custom resolver 和附属持久化字段。发布后不要仅为了排版调整而重命名这些 ID。

## 服务端权威与线程边界

### 只在服务端主线程修改

下列操作必须在 `ServerPlayer` 所在线程执行：

- 接受、推进、完成、失败或放弃任务；
- 修改 Objective 进度、flag、variable、Guide 解锁；
- 发放物品、经验、命令、经济奖励；
- 搜索世界实体或结构，解析 Marker；
- 打开服务端控制的 Trade/Gacha/Dialogue 会话。

Forge 世界事件通常已经在主线程。网络回调必须 `context.enqueueWork`；外部 WebSocket、数据库或异步计算完成后必须调度到 `MinecraftServer#execute`。

### 客户端只处理表现

客户端可以：

- 根据同步快照渲染 HUD/Screen；
- 注册 Marker 标签、颜色、可见性和相机 basis；
- 注册 Dialogue overlay 或 Journal tab visibility；
- 发送受限的用户意图包。

客户端不能决定任务完成、奖励结果、交易成功或抽奖结果。

## 公共 API 使用优先级

1. `ArcQuestAPI`、公开 Builder、Service、Facade；
2. `ArcQuestRegistrationEvent` 和 `api.event`；
3. 明确标为扩展点的 Registry；
4. 直接查询公开 Registry；
5. 最后才评估 Mixin。

Mixin ArcQ 内部 Screen、HUD、网络包、RuntimeData 或 Manager 会绑定私有字段和方法签名。这类实现属于兼容债务，不是推荐范式。

## 动态文本

`QuestText`、`DialogueText`、`GuideText` 和 `TradeText` 支持运行时解析。动态文本要考虑两个环境：

- 服务端有 `ServerPlayer`，可以解析玩家名、flag、variable 或上下文；
- 客户端定义预览和同步展示可能没有服务端玩家，必须有可读 fallback。

大型附属可以通过薄 Builder 门面为所有动态任务文本提供 fallback。门面仍应返回 ArcQ 原始 Builder；不要让 fallback 返回空白、异常或依赖客户端不存在的服务端类。

## 错误处理和返回值

不要忽略结果型 API：

- Quest 接受、放弃、强制完成优先使用返回拒绝码的 `*WithCode` / `*Result` 入口；
- `GuideUnlockService#grant` 的 boolean 表示是否真的首次解锁；
- Trade/Gacha 通过结果事件区分条件、冷却、上限、资金和请求错误；
- Marker resolver 返回 `null` 表示本轮找不到目标，不能伪造原点坐标。

只有“明确允许幂等且无需区分原因”的调用才适合使用 void 兼容入口。

## 附属开发流程

1. 锁定 Minecraft、Forge、ArcQ 和 Java 版本。
2. 搜索 ArcQ 当前源码中的目标类和调用点，不根据旧文档猜签名。
3. 选择对应注册事件或运行时 Service。
4. 定义稳定 ID、翻译键、服务端权威端和持久化所有权。
5. 用 Builder 构建定义，并在对应注册事件中注册。
6. 用 Forge 运行时事件将外部玩法事件转为 ArcQ 高层调用。
7. 对并行 Phase、重登、维度切换、奖励重复和客户端缺少上下文做边界检查。
8. 编译 ArcQ 和附属；涉及客户端时再验证 GUI scale、窗口大小和专用服务器类加载。

## 完整示例资产

所有可复制 Java 示例位于：

```text
arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/
```

每个文件都是完整 compilation unit，包含 `package`、`import`、顶层类和必要上下文。Markdown 中的 `text` 代码块只表示签名、流程或局部调用，不应保存为独立 `.java` 文件。

在 ArcQ 仓库根目录验证这些示例：

```powershell
.\gradlew.bat -I arc_quest_addon_skills/arcquest-addon-development/scripts/validate-example-sources.gradle compileArcQuestAddonSkillExamples
```

## 最低验证矩阵

| 改动 | 最低验证 |
| --- | --- |
| Definition/Builder | 附属 `compileJava`，启动服务端确认 Registry 未重复/过晚注册 |
| Quest 运行时 | 接受、正常推进、强制完成、重登、并行阶段 |
| Dialogue/NPC | 开始、选择、关闭、距离终止、原交互是否保留 |
| Guide | 首次解锁、静默/弹窗、已读、重登、奖励幂等 |
| Trade/Gacha | 条件、成本、背包满、冷却、次数、保底、断线 |
| Marker | 目标存在/不存在、维度不同、实体移除、远距离、重登 |
| 客户端扩展 | GUI scale 2/3/4、小窗口、专用服务器无客户端类加载 |
