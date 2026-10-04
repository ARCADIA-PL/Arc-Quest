# 统一图鉴调查：作者使用说明

2026-10-04。本文对应当前代码中的 `gameplayVersion: 2`；旧版 Research 配置按兼容执行器保留。实施与平台验证进度见 [验收记录](compendium-quest-verification.md)。

首次接入或交给外部 AI 编写任务，请使用 [外部 AI 接入手册](compendium-ai-authoring-guide.md)：含可直接复制的完整 Java／JSON、当前安装入口和验收步骤。

当前网络协议为 **21**：支持完整翻译组件，包括嵌套参数、追加内容和样式。成果、冻结候选和奖励来源数据在此前协议 20 已引入；客户端与服务器必须一起更新，旧协议客户端不会连接新版服务器。Forge 与 NeoForge 分别保持各自的网络平台实现。

## 1. 只有一份调查目标

| 对象 | 职责 |
| --- | --- |
| Entry | 名称、主体、发现方式、资料、可取得的成果 |
| Discover | 首次真实接触，个人永久事实 |
| Binding | 引用当前 Phase 的 Objectives，是唯一调查进度 |
| Outcome | 调查完成后记录的档案事实，没有自己的 Objective 计数 |
| Quest / Phase | 接取、阶段、配额、并行和回报 |

公开资料可以在尚未发现时浏览；“能打开档案”不等于已经发现。无 Outcome 的纯收录条目只表达“已收录”，不会空集合自动变成研究完毕。当前荒野手册中的牛与骨头均已配置实际调查与 Outcome：牛需要本轮接触与牛奶交付，骨头需要本轮合成骨粉；它们不再是仅发现候选。

普通 Binding 不自动授予永久成果。只有显式 `.recordOutcome("anatomy")` 的标准或等价调查才产出解剖记录；`.discovered()` 只验收发现。

## 2. Java 最小范例

以下示例使用 NeoForge 1.21.1 的 `ResourceLocation.parse`；Forge 1.20.1 将这一行替换为 `new ResourceLocation("my_pack:codex/zombie")`，其余 ArcQ 构建接口相同。

```java
var zombieId = ResourceLocation.parse("my_pack:codex/zombie");
var entry = CollectionEntryBuilder.create(zombieId)
        .category("living").displayName(QuestText.translatable("ai_codex.example.entry.zombie.name")).entity(EntityType.ZOMBIE)
        .discover(ObjectiveBuilder.kill(EntityType.ZOMBIE, 1).id("first_contact"))
        .outcome("anatomy", QuestText.translatable("ai_codex.example.outcome.anatomy"))
        .outcomeReward("anatomy", "anatomy_first", new ItemReward(Items.IRON_NUGGET, 3))
        .content(new CollectionContentBlock("anatomy_notes", QuestText.translatable("ai_codex.example.content.anatomy_notes"),
                null, QuestText.literal(""), CollectionMediaFit.CONTAIN, false,
                CollectionContentReveal.OUTCOME, "anatomy"))
        .build();

var quest = QuestBuilder.create("my_pack:zombie_survey")
        .category(QuestCategory.COLLECTION).mode(QuestMode.COLLECTION)
        .collectionConfig(CollectionQuestConfigBuilder.create()
                .category("living", QuestText.translatable("ai_codex.example.category.living")).entry(entry).build())
        .phase(PhaseBuilder.create("survey").displayName(QuestText.translatable("ai_codex.example.phase.survey.name"))
                .objective(ObjectiveBuilder.kill(EntityType.ZOMBIE, 3).id("defeats"))
                .objective(ObjectiveBuilder.offer(Items.ROTTEN_FLESH, 2).id("samples"))
                .collectionSheet(CollectionSheetBuilder.create()
                        .binding(EntryRequirementBuilder.create("zombie", zombieId)
                                .objectives("defeats", "samples")
                                .recordOutcome("anatomy")
                                .reward("payment", new ItemReward(Items.EMERALD, 1))))
                .autoAdvanceOnComplete(false))
        .build();
```

`recordOutcome` 自动增加已发现前置。首次击败可以同时建立发现并把当前击败目标推进一次；没有另一份永久研究 1/5。击败 3 次并交付 2 份腐肉后，成果只登记一次，首次奖和本次报酬分别可领。

### Java 规则版本必须显式保留

服务器不能从展示导出器复原任意 Java 条件和回调。使用现代图鉴任务的代码作者，需注册不可变版本工厂，再注册任务：

```java
CollectionRunDefinitionStore.registerCodeDefinitionFactory(
        quest.getId(), "zombie-survey-v2", () -> buildZombieSurveyV2(), true);
ArcQuestAPI.registerQuest(quest);
```

保存后的世界只记录工厂的稳定版本引用。以后改变数值或回调时注册新版本，并继续提供旧版工厂；不要让旧版供应器调用已经改成新版的构建方法。没有完整原始 JSON 或显式工厂的代码任务会被拒绝接取，已有不可恢复的 run 保留存档、暂停推进，不套用当前规则。

数据包任务使用原始作者 JSON 冻结，包含条件、奖励载荷与共享 Entry；当前 run 的 Tag 成员另外保存。展示导出、匿名线索和 JEI 数据都不是可执行的服务端快照。

## 3. JSON 配置

新 Entry 必须明确写 `"gameplayVersion": 2`。缺失版本按旧版解析，不能把旧文件无声解释成新玩法。

```json
{
  "entryId": "my_pack:codex/zombie",
  "categoryId": "living",
  "gameplayVersion": 2,
  "displayName": {"mode":"translatable", "value":"ai_codex.example.entry.zombie.name"},
  "subjectKind": "ENTITY",
  "subjectId": "minecraft:zombie",
  "discoveryObjectives": [{
    "id":"first_contact", "type":"arc_quest:kill",
    "targetId":"minecraft:zombie", "requiredCount":1
  }],
  "outcomes": [{"outcomeId":"anatomy", "displayName":{"mode":"translatable", "value":"ai_codex.example.outcome.anatomy"}}],
  "rewards": [{
    "rewardId":"anatomy_first", "trigger":"OUTCOME", "outcomeId":"anatomy",
    "grantMode":"MANUAL", "previewVisibility":"PUBLIC",
    "rewards":[{"type":"item", "itemId":"minecraft:iron_nugget", "count":3}]
  }]
}
```

实际 Phase 定义 `defeats` 和 `samples` 两个目标，Binding 引用它们：

```json
{
  "bindingId":"zombie", "entryId":"my_pack:codex/zombie",
  "objectiveIds":["defeats", "samples"],
  "requirementMode":"ALL", "recordRequirements":[{"type":"DISCOVERED"}],
  "outcomeIds":["anatomy"],
  "rewards":[{
    "rewardId":"payment", "trigger":"BINDING_COMPLETE", "grantMode":"MANUAL",
    "previewVisibility":"PUBLIC",
    "rewards":[{"type":"item", "itemId":"minecraft:emerald", "count":1}]
  }]
}
```

完整可安装例子在 [范例数据包](examples/collection/collection-demo-pack)，三条路线见 [可玩 Demo 说明](compendium-quest-demos.md)。其生成器 [generate-demo-pack.py](examples/collection/generate-demo-pack.py) 通过 [demo-blueprints.py](examples/collection/demo-blueprints.py) 读取 Java Demo 的受限构建 DSL，根据 Minecraft 版本设置 pack_format，并检查中英文翻译键完整性。Java 与 JSON 的玩法、翻译键、资料和奖励一致，只重映射任务／Entry 命名空间；各任务只配置实际 Binding 引用的条目及分类。当前内置 Demo 工厂为 `collection-v4`，Entry 仍为 `gameplayVersion: 2`，两种版本不混用。`collection-v1`、`collection-v2` 和 `collection-v3` 工厂继续服务已有冻结运行；v4 沿用 v3 的玩法与奖励，当前作者文案全部使用模组自带中英翻译键。

## 4. 资料与公开线索

`content` 是文字、图片等资料块，不计数、不自动完成调查。新版公开条件为 `ALWAYS / DISCOVERED / OUTCOME`；`OUTCOME` 的 `revealStepId` 指成果 ID。

默认公开条目展示基本身份，个人状态仍显示尚未发现。占位条目使用：

```java
.visibility(VisibilityMode.HIDDEN_BY_DEFAULT, HiddenPresentationMode.PLACEHOLDER)
.publicClue(QuestText.translatable("ai_codex.example.clue.spider"))
```

JSON 对应 `publicClue` QuestText。未发现时服务器只发送匿名身份和作者公开线索，实体、贴图、真实目标和隐藏奖励不下发；玩家可以追踪匿名线索。完全隐藏条目不提供匿名追踪。

所有原版头像复用已有二维适配；模组生物需要注册纹理与 UV 规则或自定义图标，无规则留空。物品及有效 iconItem 可左键查询 JEI 配方、右键用途，头像与纯纹理不接 JEI。Tooltip 复用任务奖励栏，不追加快捷键说明。

## 5. 奖励次数和待交付

| 奖励 | 配置位置 | 身份 |
| --- | --- | --- |
| 首次发现 | Entry.discoveryReward | 玩家＋Entry＋rewardId |
| 首次档案成果 | Entry.outcomeReward | 玩家＋Entry＋rewardId；跨成果触发器保持 rewardId 唯一 |
| 本轮报酬 | Binding.reward | 玩家＋Quest＋run＋phase＋binding＋rewardId |

Java Builder 的便捷奖励方法默认 PUBLIC 预告；JSON 的 `previewVisibility` 缺省为 UNLOCKED_ONLY，公开预告须显式填写 PUBLIC。UNLOCKED_ONLY 保留神秘奖励。可见性不是领取资格，资格由服务器判断。旧轮欠奖保留原来源和原金额，即使新轮接取、节点改名也不能用当前轮代领。

合法资格产生时保存奖励载荷；标准 Item/Flag/Variable/Command 保存实际参数，自定义回调引用保留的规则版本。删除 provider 后无法兑现的回调暂停处理并记录错误，绝不执行新版替代回调。

物品奖励先完整验证并写持久交付授权，再登记领取和执行交付；首次准备失败保留未领取资格。背包没有足够空位的部分保留在待交付箱，不丢到地上；每秒及登录时重试。目录显示“待交付”，腾出空位后自动交付。每个物品块有交付 token，原版玩家文件同时保存库存与 token，日志确认该文件后才完成；重复恢复不会重放已确认物品。

新日志记录已经通过服务端资格检查的领取决定。首次领取检查点丢失时，恢复会核对原 Entry 世代、Run、Binding 和定义哈希，重建原奖励凭证并先保存检查点，再交付；旧无授权标记的日志不推认，reset 的旧授权失效。日志损坏、写入或库存保存核查失败时暂停该玩家的交付并记录错误，修复存储后重新登录／重启恢复，不在每秒循环里反复执行失败副作用。提供者失联的回调进入 Review，不阻塞已准备好的物品与其他授权奖励。

命令、外部经济和任意回调不能承诺通用严格一次副作用：执行前记录已尝试，失败及不明确中断保留日志，禁止自动重放。需由作者提供具体补偿策略。领取不弹 Toast，详情状态、滚动及动画保持。

## 6. 收集、Tag 和重复委托

| 目标 | Java | 语义 |
| --- | --- | --- |
| 当前持有 | `ObjectiveBuilder.possess(item,n)` / `possessTag(tag,n)` | 接取和每秒读取库存，可以下降，未消耗 |
| 实际制作来源 | `CRAFT` 或 COLLECT＋`collectMode(CRAFTED_ONLY)` | 只认服务端支持的制作事件 |
| 消耗样本 | `offer` / `offerTag`、现有 DELIVER | 实际扣除库存 |
| 兼容获得累计 | 旧 COLLECT、`LEGACY_ACQUISITION` | 保留旧语义，不能证明样本来源 |

JSON 的 `collectMode` 为 `POSSESSION / CRAFTED_ONLY / LEGACY_ACQUISITION`。持有不是反复获得；丢弃重捡不会把持有 1 个积成 5 个。重置不撤走背包物品，所以显式持有准备条件可以立刻满足；新版 Demo 的奖励调查使用真实新行为或样本提交。

Tag 轮换表示任选候选，不表示每个成员都要交一遍。当前 run 冻结 Tag 成员与门槛；旧成员仍可提交，新成员留给新 run。客户端只接收已获准目标的候选清单，空集合不能退回当前 Tag。

重复经济委托仅依赖旧知识、原地位置、普通互动、实时持有或容易循环的制作时，必须配置接取冷却或明确消耗履约。已知重复任务的里程碑规则检查是否仅凭免费初始候选就能达标，无法证明的自定义规则给作者诊断。Java 使用 `CollectionQuestConfigBuilder.repeatCooldownTicks(n)`，JSON 为 `collectionConfig.repeatCooldownTicks`，单位是服务器游戏 tick。

沿用 ArcQ 的既有生命周期：非重复任务放弃后进入失败终态，不能再次接取，直到管理员显式 reset；系统不因此对合法的一次性提前报酬强加重复成本限制。重复任务可放弃重接，冷却保留。真实服务端回归分别验证两种拒绝码和没有遗留的活跃 run。

冷却从接受时开始，放弃和失败不清除，单任务 reset / resetall 清除。共享同一个消耗 Objective 的多个新版 Binding 会被拒绝，避免一份样本包装成多份报酬；需要共同证据时应配置一个共同调查及清晰奖励归属。

## 7. 阶段、配额与事件

ALL 完成全部必需 Binding；QUOTA 按配置的调查/不同 Entry 数量判断，达标固定本轮结算结果并停止剩余候选计数。可选项不计必需分母。

一个来源事件先冻结接收者，再一次性更新相关目标，最后统一结算 Binding、成果、报酬及阶段。新激活阶段不会再次消费同次合成或位置采样。奖励交付与调查事件隔离。

并行汇合使用 `ICondition.phaseCompleteCurrentRun(questId,phaseId)`；JSON 条件为 `arc_quest:quest_phase_completed_current_run`，带 `questId / phaseId`。不要用长期全局 flag 代表本轮合流。

## 8. 重置、迁移与编辑

普通重复接取保留发现与成果，本轮行动从零开始。reset 清目标任务运行/完成/失败/归档和关联 Entry 的知识、权限及首次奖；关联共享 Entry 也清零，其他任务独立行动和本轮报酬保留。resetall 额外禁止旧库存导入，真实新事件仍有效。

成果登记需要实际完成边沿与当前 Entry 世代；旧 run 的完成锁存、登录、刷新和旧迁移不能在 reset 后重新授予。默认个人归属，不增加隐式队伍共享或助攻。

旧 `.research()` / `.researchStep()` / `.researchReward()` / Entry.bindingReward 按 v1 执行；新旧 API 混用给出诊断。已完成旧研究需作者显式 `.migrateResearchStep(oldObjective, outcomeId)` 或完整集合映射，未完成 2/5 不转换为新轮行动。稳定首奖 ID 可保留已领凭证，旧欠奖按原载荷兑现。正在运行的旧任务保留原版执行器，结束后新轮采用 v2。

编辑器默认新建 v2 Entry，成果只有 ID/名称，调查目标在 Phase 定义并由 Binding 引用。编辑器检查未知成果、循环来源、缺失引用、免费永久事实报酬和重复消耗；旧研究字段仅留在旧定义兼容层。

## 9. 玩家界面

同 Entry 的多调查合并为卡片，详情选择具体调查；主行动进度只读 Binding。次级档案保留半透明主题、模态阻断及开关动画；调查/档案切换和限高奖励条带分别管理本次与首次奖励。

追踪点击立即有效；悬停 0.2 秒只控制文案切换与高亮动画。收藏立即更新书签，顺序下次自然刷新再调整。关闭期间固定 Entry、Binding、主题、成果和追踪快照，避免数据同步引起文本或图标跳变。

目录、搜索、详情和追踪使用同一公开线索投影。真正关闭再打开重置搜索，JEI 返回保持搜索与阅读位置。通知沿用左侧，领取无 Toast。

### 分类、卡片与详情排序

图鉴卡片依次比较：**收藏优先 → Category.sortOrder → Category 声明顺序 → Entry.sortOrder → 当前 Phase 中该 Entry 首次 Binding 的声明顺序**。收藏与未收藏各自按这些分类／条目规则排；各组中未声明的分类位于已声明分类之后。收藏立即更新书签，排序等下次自然刷新再应用，不在点击时让卡片跳位。

Category.sortOrder 决定当前 Quest 中分类的先后，同分保持 Java `.category(...)` 调用或 JSON `categories` 数组的声明顺序；Entry.sortOrder 决定同分类内卡片先后，同分保持当前 Phase 原始 `bindings` 中 Entry 首次出现的顺序。Entry 注册／数组顺序、名称及 ID 字母序不作为卡片同分规则。同 Entry 多 Binding 仍是一张卡片。

Binding.sortOrder **只控制同 Entry 详情中的调查顺序**，同分按 Phase 中 Binding 声明顺序；它不影响卡片排序。三个 sortOrder 都是整数，小值靠前，支持负值。Entry、Binding 和 JSON Category 缺省为 0；既有 Java 两参 `category(id, name)` 保留加入时 `categories.size()` 的默认值，需明确分类优先级时使用新增三参重载。

```java
var config = CollectionQuestConfigBuilder.create()
        .category("materials", QuestText.translatable("ai_codex.example.category.materials"), 10)
        .category("living", QuestText.translatable("ai_codex.example.category.living"), -10)
        .entry(CollectionEntryBuilder.create("my_pack:codex/zombie")
                .category("living").displayName(QuestText.translatable("ai_codex.example.entry.zombie.name")).sortOrder(-5))
        .build();
var binding = EntryRequirementBuilder.create("zombie", "my_pack:codex/zombie")
        .objective("defeats").sortOrder(-10).build();
```

JSON 的字段位置对应如下。此片段用于合并到完整任务，实际 Phase 仍须定义 `defeats`，其余发现、主体、奖励等配置按上文示例保留：

```json
{
  "collectionConfig": {
    "categories": [
      {"categoryId": "materials", "displayName": {"mode": "translatable", "value": "ai_codex.example.category.materials"}, "sortOrder": 10},
      {"categoryId": "living", "displayName": {"mode": "translatable", "value": "ai_codex.example.category.living"}, "sortOrder": -10}
    ],
    "entries": [
      {"entryId": "my_pack:codex/zombie", "categoryId": "living", "gameplayVersion": 2, "sortOrder": -5}
    ]
  },
  "phases": [{
    "phaseId": "survey",
    "collectionSheet": {"bindings": [
      {"bindingId": "zombie", "entryId": "my_pack:codex/zombie", "objectiveIds": ["defeats"], "sortOrder": -10}
    ]}
  }]
}
```

排序只影响展示，不修改服务端定义顺序、调查要求、配额、进度、奖励或 reset 语义。排序沿用既有字段；当前协议为 21，另含完整翻译组件同步。更多同 Entry 多调查的示例见 [外部 AI 接入手册 §7.4](compendium-ai-authoring-guide.md#74-分类条目卡片与调查详情排序)。

### 所有作者文本都可翻译

图鉴的目录／详情／追踪／拓扑等系统文案使用 ArcQ 中英翻译。作者配置的任务、阶段、分类、条目、描述、公开线索、成果、目标、正文与图片说明均可传 `QuestText.translatable(key)`；分类、`.text()` 和 `.image()` 的便捷 API 也接受 `QuestText`。String 重载仍按字面显示，不自动识别键。

```java
var config = CollectionQuestConfigBuilder.create()
        .category("living", QuestText.translatable("ai_codex.example.category.living"), -10);
var entry = CollectionEntryBuilder.create("ai_codex:codex/zombie")
        .category("living")
        .text("overview", QuestText.translatable("ai_codex.example.content.overview"))
        .image("habitat", texture, 240, 120,
                QuestText.translatable("ai_codex.example.content.habitat.caption"));
```

这里的 `texture` 是作者准备好的纹理 ResourceLocation。完整 Component 的嵌套翻译参数、追加文字和样式会保留到客户端，不要在服务端提前 `getString()`。JSON 普通键使用 `mode: translatable`；复杂内容使用 `mode: component`，`value` 是序列化 Component JSON 字符串，写法见 [作者手册 §7.5](compendium-ai-authoring-guide.md#75-全部-hud-文本与翻译组件)。

本文 `ai_codex.example.*` 示例键全部包含在 [客户端范例资源包](examples/collection-ai/resource-pack) 的中英语言文件中，按 [安装说明](examples/collection-ai/README.md) 启用。自定义键同样要放入客户端 `assets/<namespace>/lang/zh_cn.json` 和 `en_us.json`；服务端外置任务目录不会自动分发资源。
