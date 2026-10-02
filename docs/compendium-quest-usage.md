# 图鉴模式 Quest：内容作者使用说明

本说明对应新的 `collectionConfig.entries` 与 `phase.collectionSheet` 数据格式。完整产品设计见 [compendium-quest-design.md](compendium-quest-design.md)。图鉴直接显示在任务面板中，无需另一个图鉴 Screen。

内置任务列表分类「图鉴」的 ID 是 `arc_quest:collection`；Java 使用 `QuestCategory.COLLECTION`，JSON 配置 `"category":"arc_quest:collection"`。三个现代 Demo 都归入此分类。QuestCategory 决定列表分组，`mode:"COLLECTION"` 决定图鉴面板与任务语义，作者仍可显式使用其他任务分类。

## 1. 先区分 Entry、Binding 和 Phase

| 对象 | 作者配置 | 意义 |
| --- | --- | --- |
| Entry | `collectionConfig.entries[]` | 一个长期知识对象：名称、主体、头像、资料、发现与研究规则 |
| Binding | `phase.collectionSheet.bindings[]` | 这份任务对这个条目的具体要求 |
| Phase | `phases[]` | 实际调查章节，拥有正常目标、流程、并行分支和奖励 |

同一个 `arc_quest:zombie` Entry 可以被多个任务引用：首次调查要求已发现，另一份任务要求研究完成，重复委托要求本轮击败 5 只。Entry 已发现并不意味着每一个 Binding 都完成。

不要为每个 Entry 添加一个 Phase。一章可以拥有多个 Binding；Phase 的进入条件、转移和并行关系继续按普通 Quest 配置。

`entryId`、`bindingId`、Objective `id`、图文 `blockId` 和条目奖励 `rewardId` 是持久化标识，发布后尽量保持不变。改显示名称、调整排序和新增资料不需要改变这些 ID。奖励的作用域与迁移规则见第 10.2 节。

## 2. 在编辑器里建立任务

1. 在任务基础设置中选择 `COLLECTION`。
2. 在分类配置中添加生物、材料等分类。
3. 在「图鉴条目定义」中添加 Entry，填写稳定 ID、名称、主体和所属分类。
4. 编辑发现规则与长期研究步骤，按实际行为选择 `KILL`、`COLLECT`、`CRAFT` 等 Objective。
5. 在「Guide 式图文资料」中添加说明和配图；可以配置何时公开。
   在条目的奖励配置中，可以分别添加首次发现、研究完成和本轮绑定完成奖励；默认手动领取。
6. 打开一个真实 Phase，在上方普通 Objective 区域添加本轮行动或提交要求。
7. 为该阶段添加「条目目标板」，添加 Binding，选择 Entry，再选择本轮 Objective 或长期记录要求。
8. 选择 `ALL` 或 `QUOTA`，检查诊断后导出。

新 Entry 的初始模板是「获得一枚铁锭」。改成生物条目时，应同时修改主体与发现规则，不能只改目录名称。默认使用本轮阶段 Objective 的原有检测方式，不根据图片、查看 JEI 或打开详情推进任务。

编辑器提供可以收起详情的结构预览。它验证目录与图文组织，不模拟服务端进度，也不访问游戏的物品注册表或资源包。图片资源存在性、物品有效性和实际头像适配由客户端判断。

## 3. 一个最小可用任务

下面配置一份单章「铁锭调查」。玩家获得一枚铁锭会登记长期发现；本任务还要求本轮获得 4 枚。已有图鉴记录只满足「发现」，不会替代本轮计数。

```json
{
  "id": "my_pack:iron_survey",
  "category": "arc_quest:collection",
  "displayName": {"mode": "literal", "value": "铁锭调查"},
  "description": {"mode": "literal", "value": "记录铁锭，并补充四份新的材料记录。"},
  "mode": "COLLECTION",
  "initialPhaseId": "survey",
  "collectionConfig": {
    "categories": [{
      "categoryId": "materials",
      "displayName": {"mode": "literal", "value": "材料"},
      "sortOrder": 0
    }],
    "entries": [{
      "entryId": "my_pack:iron",
      "categoryId": "materials",
      "displayName": {"mode": "literal", "value": "铁锭"},
      "description": {"mode": "literal", "value": "广泛用于制作工具和机械的金属材料。"},
      "subjectKind": "ITEM",
      "subjectId": "minecraft:iron_ingot",
      "icon": {"type": "arc_quest:auto"},
      "visibilityMode": "VISIBLE_BY_DEFAULT",
      "hiddenPresentationMode": "FULLY_HIDDEN",
      "discoveryObjectives": [{
        "id": "discover_iron",
        "type": "COLLECT",
        "targetId": "minecraft:iron_ingot",
        "requiredCount": 1,
        "displayText": {"mode": "literal", "value": "获得铁锭"}
      }],
      "researchObjectives": [],
      "relatedItems": ["minecraft:iron_ingot", "minecraft:iron_pickaxe"],
      "content": [{
        "blockId": "intro",
        "text": {"mode": "literal", "value": "铁锭可以制作工具，也可以用于建设机械。"},
        "reveal": "DISCOVERED",
        "fit": "CONTAIN",
        "zoomable": true
      }]
    }]
  },
  "phases": [{
    "phaseId": "survey",
    "displayName": {"mode": "literal", "value": "材料调查"},
    "objectives": [{
      "id": "collect_four",
      "type": "COLLECT",
      "targetId": "minecraft:iron_ingot",
      "requiredCount": 4,
      "displayText": {"mode": "literal", "value": "本轮获得铁锭"}
    }],
    "collectionSheet": {
      "completionPolicy": "ALL",
      "bindings": [{
        "bindingId": "iron_survey",
        "entryId": "my_pack:iron",
        "objectiveIds": ["collect_four"],
        "recordRequirements": [{"type": "DISCOVERED"}],
        "requirementMode": "ALL",
        "recordPolicy": "EXISTING_RECORDS",
        "optional": false
      }]
    },
    "transitions": []
  }],
  "completionRewards": [{"type": "item", "itemId": "minecraft:emerald", "count": 1}]
}
```

数据包仍使用项目现有 Quest 文件目录与注册流程，图片放入对应客户端资源包。JSON 文件中可以使用 `literal` 直写文本或 `translatable` 翻译键；图文中的换行写作 `\n`。

## 4. Entry 字段

| 字段 | 类型 | 配置说明 |
| --- | --- | --- |
| `entryId` | 资源 ID | 稳定条目标识，例如 `my_pack:zombie` |
| `categoryId` | 字符串 | 必填，引用本任务声明的分类 |
| `displayName` | QuestText | 条目名称 |
| `description` | QuestText | 简介 |
| `subjectKind` | `ENTITY / ITEM / CUSTOM` | 主体类型 |
| `subjectId` | 资源 ID | 生物或物品的真实注册 ID |
| `itemTag` | 资源 ID | 物品候选 Tag；应避免与具体物品目标混写导致含义不清 |
| `icon` | ObjectiveIconSpec | 使用已有 Objective 图标格式 |
| `discoveryObjectives` | ObjectiveSpec 数组 | 发现规则；任一合格规则满足即可发现 |
| `researchObjectives` | ObjectiveSpec 数组 | 长期研究步骤；必需步骤全部满足后研究完成 |
| `researchAfterDiscovery` | 布尔 | 是否必须先发现才开始累计研究 |
| `visibilityMode` | 现有 VisibilityMode | 公开目录、默认隐藏等表现策略 |
| `hiddenPresentationMode` | `FULLY_HIDDEN / PLACEHOLDER` | 隐藏整个条目，或显示神秘占位 |
| `relatedItems` | 物品 ID 数组 | 详情中的关联物品，可接 JEI |
| `content` | 图文块数组 | 正文、配图、公开时机 |
| `rewards` | CollectionEntryRewardSpecData 数组 | 条目奖励；配置稳定 ID、触发时机、领取方式及实际奖励，见第 10.2 节 |
| `sortOrder` | 整数 | 目录顺序，不能代替稳定 ID |

已公开但尚未发现的条目可以展示任务所需身份，但受 `reveal` 保护的资料仍不公开。神秘占位不能把真实名称、关联物品或图片藏在 Tooltip 中泄露。

共享条目可以通过 `collectionConfig.entryIds` 引用其他已注册任务声明的 Entry。引用端不用复制 Entry 定义，但发布的数据包必须同时提供该条目的注册来源。修改共享 Entry 的介绍不会修改各任务独立的 Binding 进度。

## 5. 记录要求与本轮行动

Binding 的 `objectiveIds` 指向**同一个 Phase** 的正常 Objective。数量、提交消耗、NPC、Tag 和图标仍由该 Objective 配置，图鉴系统不复制第二份行动计数规则。

长期记录写在 `recordRequirements`：

```json
[
  {"type": "DISCOVERED"},
  {"type": "RESEARCH_COMPLETE"},
  {"type": "RESEARCH_STEP", "stepId": "defeat_five"}
]
```

`RESEARCH_STEP` 的 `stepId` 必须指向该 Entry 的 `researchObjectives[].id`。`requirementMode` 为 `ALL` 表示所有配置的要求必须完成，`ANY` 表示任一要求即可完成。`optional` 条目可以展示，但不计入必需完成门槛。

记录策略：

| 策略 | 行为 |
| --- | --- |
| `EXISTING_RECORDS` | 认可任务接取前已有的长期发现和研究事实 |
| `NEW_DISCOVERIES` | 只认可本轮接取后首次发现；只允许 `DISCOVERED` 要求 |

本轮行动不是第三个记录策略，而是通过 `objectiveIds` 引用原有阶段目标。行动默认在该 Phase 激活后开始计数，不提前累积尚未进入的章节。

重复任务推荐只绑定本轮行动，或同时附加长期发现条件。若只引用长期记录，重复接取会再次满足这些事实；编辑器会报告风险。放弃、失败或重复接取不能清空玩家的长期图鉴。

## 6. ALL、配额与不同对象

全部必需条目完成：

```json
{"completionPolicy": "ALL", "countDistinctEntries": false, "bindings": []}
```

候选中任意完成 5 个不同 Entry：

```json
{"completionPolicy": "QUOTA", "requiredCount": 5, "countDistinctEntries": true, "bindings": []}
```

以上片段只展示门槛字段；实际文件必须填写有效、非空的 `bindings`。

有 12 个候选且目标为 5 时，玩家看到 `3/5`，候选总数 12 另列。`countDistinctEntries:true` 按唯一 `entryId` 计数，重复绑定同一物种不能凑多个不同物种。设为 `false` 时计数单位是不同 Binding 要求，不应把文案写成不同物种。

可选条目不计入门槛，配额不能超过有效候选数。只配置可选条目或空目标板会报配置错误。

## 7. Tag 的两种常见需求

**任选一种获得即可记录**：一个 Entry 配置 `itemTag`，发现 Objective 也配置该 Tag。目录图标自动轮换候选物品，获得任一符合该目标语义的物品即可推进。

**每一种都要单独收录**：为各个具体物品建立独立 Entry、独立 Binding；需要 N 种时使用 `QUOTA` 和 `countDistinctEntries:true`。不要用一个 Tag Objective 的数量 N 冒充 N 种物品，它表示数量而非不同种类。

Tag 候选随整合包改变时，应保留已发布 Entry ID，复核配额是否仍可完成。已经领取的任务奖励不能因 Tag 变化撤销。

## 8. 图标与 JEI

沿用 Objective 图标格式：

```json
{"type": "arc_quest:item", "item": "minecraft:diamond"}
```

```json
{
  "type": "arc_quest:texture",
  "texture": "my_pack:textures/gui/heads/cow.png",
  "region": {"x": 0, "y": 0, "width": 32, "height": 32}
}
```

支持 `arc_quest:auto`、`arc_quest:item`、`arc_quest:texture`、`arc_quest:none` 和 `arc_quest:provider`。纹理裁切使用原始图片像素坐标。生物仅展示二维头像；没有有效头像时保留文字，不渲染模型或强行改成刷怪蛋。

显式 `arc_quest:item` 可以用与主体不同的物品，且只要该物品有效就能接入 JEI。关联物品同样支持左键配方、右键用途；查询图标不会误执行条目选择、追踪或领奖。Tooltip 复用任务奖励栏，不添加研究规则和 JEI 快捷键说明。

## 9. Guide 式图文与资料解锁

```json
{
  "blockId": "weakness",
  "text": {"mode": "literal", "value": "完成研究后公开的观察结论。"},
  "media": {
    "type": "image",
    "texture": "my_pack:textures/collection/zombie.png",
    "width": 180,
    "height": 90
  },
  "caption": {"mode": "literal", "value": "野外观察记录"},
  "fit": "CONTAIN",
  "zoomable": true,
  "reveal": "RESEARCH_STEP",
  "revealStepId": "defeat_five"
}
```

图片资源路径对应 `assets/my_pack/textures/collection/zombie.png`。`width`、`height` 是图片显示区域的逻辑尺寸。`CONTAIN` 完整显示并保持比例，`COVER` 填满区域并允许裁切。`zoomable:false` 禁止点击放大。

`fit`、`zoomable`、`caption` 和公开规则位于图文块层级，不能误写进旧 Guide 的 `media` 对象。没有图片的文字块可以省略 `media`。新内容首先支持 `none/image`，不因为复用了 Guide 数据结构就承诺所有第三方媒体自动支持。

| `reveal` | 资料公开时机 |
| --- | --- |
| `ALWAYS` | 条目身份已公开时即可展示 |
| `DISCOVERED` | 已发现后展示，默认值 |
| `RESEARCH_COMPLETE` | 全部必需研究完成后展示 |
| `RESEARCH_STEP` | 指定研究步骤完成后展示 |

多块图文按配置顺序排列。不要通过图片名称、关联物品或隐藏正文的 Tooltip 泄露尚未公开的资料。

## 10. 玩家浏览与追踪

任务面板保留左侧任务列表，右侧展示调查简介、真实 Phase 选择、完成门槛、分类搜索、标本陈列和奖励。

默认展示标本目录。点击条目名称或卡片文字区域，在任务面板内打开次级详情；详情覆盖目录并阻止背景操作，不单独打开新的 Screen。顶栏的「返回目录」关闭详情，详情内的「追踪此条目」固定在顶栏，长资料不需要滚到底才能追踪。目录和详情各自支持滚轮、滑条点击及拖动，返回时保留分类、搜索和浏览位置。

追踪图鉴任务以 **Binding** 为单位：首次追踪、自动追踪或登录恢复时，优先保留当前 Run 内仍有效的条目选择，否则选择当前激活章节中已公开且未完成的可行动条目；并行章节可以手动切换。HUD 保留任务名称和必要的章节名称，显示选中条目的头像、名称、要求完成数及少量未完成要求，而不是只展示任务总进度。点击目录名称只查看资料，「追踪此条目」才明确切换追踪目标。

聚焦条目达成后短暂反馈，再切换下一个可行动 Binding；没有可追踪条目时隐藏，不退回持续占位的总览。待回报提示短暂显示，实际回报和领奖留在任务面板。重新接取的 Run 不继承上一轮的追踪焦点。

物品图标单独承担 JEI 的左键配方、右键用途入口，点击图标不打开详情、不追踪、不提交或领奖。图片放大在详情上再增加一层：Esc 先关闭图片，再返回目录，最后关闭任务面板。游戏内追踪器不抢占鼠标，JEI 查询在任务面板中完成。

### 10.1 里程碑奖励

任务级节点写在 `collectionConfig.rewardNodes`，分类节点写在对应 `categories[].rewardNodes`。例如，在至少有三个必需绑定的任务中，完成三个绑定后自动发一份煤炭：

```json
{
  "nodeId": "first_three",
  "scope": "QUEST",
  "scopeRefId": "my_pack:field_survey",
  "grantMode": "AUTO",
  "completionRules": [{"type": "completed_entry_count", "value": 3}],
  "rewards": [{"type": "item", "itemId": "minecraft:coal", "count": 1}]
}
```

`nodeId` 必须非空，并且在同一任务的任务级和所有分类节点之间唯一。任务节点使用 `scope:"QUEST"`，显式 `scopeRefId` 必须匹配任务 ID；分类节点使用 `scope:"CATEGORY"`，归属必须匹配所在 `categoryId`。省略或留空 `scopeRefId` 时使用所在配置的归属；Java 节点也可用 `null` owner。旧条目节点仍按兼容格式读取。

`completed_entry_ratio`、`category_completed_ratio` 可以使用 `ratio:0.5` 表示一半，数值必须有限且在 `[0,1]`。省略 `ratio` 时保留旧 `value:50` 百分比格式；同时填写时优先使用 `ratio`。支持 `and/or/not` 组合。现代任务的条目完成依据本次 Binding 的完整要求，不以永久发现替代本轮行动；`all_entries_complete` 也不等于配额已达标。

`AUTO` 自动发放，`MANUAL` 解锁后在面板中领取；归档后的手动奖励仍可领取。面板可以预览未解锁节点的奖励，JEI 图鉴目录只登记已解锁节点。客户端的领取资格读取服务端同步凭证，Java 自定义规则不会在导出或客户端执行。

发奖前先登记领取凭证，防止奖励回调重新进入同一节点。单项奖励异常会写入日志并继续余下奖励及任务收尾，整包不会自动重发，失败项需要按日志处理。这保证回调重入及已保存凭证的防重，未提供任意外部副作用与磁盘保存之间的跨崩溃原子事务。

### 10.2 条目奖励：永久知识与本轮调查分别计次

条目奖励写在 `collectionConfig.entries[].rewards`。它与上节的任务/分类 `rewardNodes`、Phase 奖励、任务最终奖励分别配置和记录，不借用旧里程碑 `nodeId`。

| `trigger` | 解锁条件 | 发放计次范围 |
| --- | --- | --- |
| `DISCOVERED` | Entry 的永久发现成立 | 每名玩家、每个 `entryId`、每个 `rewardId` 最多领取一次 |
| `RESEARCH_COMPLETE` | 已发现，且全部非可选的长期研究步骤达标 | 与首次发现同属永久记录，不随任务重新接取清空 |
| `BINDING_COMPLETE` | 某个 Binding 按自己的 `ALL/ANY` 完成要求并锁定本轮结果 | 每个 Quest 的 `runId + phaseId + bindingId + rewardId` 分别计次 |

没有必需长期研究步骤时，**已发现即研究完成**。只有可选研究步骤也按此规则处理；如需真正的研究门槛，应配置至少一个非可选步骤。`BINDING_COMPLETE` 不要求该 Phase 或整份 Quest 已结束；一个候选绑定达成即可获得它的奖励。配额已经满足却没有完成的其他候选不会因任务回报补发绑定奖励。

例如，同一个僵尸 Entry 被两份任务引用：永久首次发现奖励只能领一次；第一份任务要求「击败三只并提交腐肉」，第二份任务要求「本轮击败两只」，两个 Binding 的调查奖励各自成立。重复接取第二份任务会生成新 Run，必须重新完成本轮行动才能再次领取调查奖励。永久记录成立不会自动完成另一份任务的行动。

#### JSON 字段与配置位置

以下内容放入一个 Entry 的 `rewards` 字段；外层仍需配置该 Entry 的名称、分类、发现/研究规则，并由 Phase 的 Binding 引用它。

```json
"rewards": [
  {
    "rewardId": "first_record",
    "trigger": "DISCOVERED",
    "grantMode": "MANUAL",
    "rewards": [{"type": "item", "itemId": "minecraft:coal", "count": 1}]
  },
  {
    "rewardId": "completed_research",
    "trigger": "RESEARCH_COMPLETE",
    "grantMode": "MANUAL",
    "rewards": [{"type": "item", "itemId": "minecraft:iron_nugget", "count": 3}]
  },
  {
    "rewardId": "completed_investigation",
    "trigger": "BINDING_COMPLETE",
    "grantMode": "AUTO",
    "rewards": [{"type": "item", "itemId": "minecraft:stick", "count": 2}]
  }
]
```

| 字段 | 默认值与约束 |
| --- | --- |
| `rewardId` | 必填；1–128 字符，不允许空白值或首尾空白；同一个 Entry 的所有 trigger 之间也必须唯一 |
| `trigger` | 仅支持上表三个值；JSON 缺省为 `DISCOVERED`，建议显式写出 |
| `grantMode` | `MANUAL` 或 `AUTO`，默认 `MANUAL` |
| `rewards` | 使用现有 RewardSpec；可以是零个、一个或多个实际奖励，不限于物品 |

Entry 可以完全不配置奖励。JSON 校验最多允许一个 Entry 声明 128 个条目奖励定义。这里的外层 `rewards` 是条目奖励定义列表，每项的内层 `rewards` 才是要执行的奖励列表。

#### Java Builder API

```java
var zombie = CollectionEntryBuilder.create("my_pack:codex/zombie")
    .category("living")
    .entity(EntityType.ZOMBIE)
    .discover(ObjectiveBuilder.kill(EntityType.ZOMBIE, 1).id("first_defeat"))
    .research(ObjectiveBuilder.kill(EntityType.ZOMBIE, 5).id("anatomy"))
    .discoveryReward("first_record", new ItemReward(Items.COAL, 1))
    .researchReward("completed_research", new ItemReward(Items.IRON_NUGGET, 3))
    .bindingReward("completed_investigation", new ItemReward(Items.EMERALD, 1))
    .build();

// 显式指定 AUTO；其他两种 trigger 也使用相同重载。
var logs = CollectionEntryBuilder.create("my_pack:codex/logs")
    .category("materials")
    .itemTag(ResourceLocation.parse("minecraft:logs"))
    .discover(ObjectiveBuilder.collectTag(ResourceLocation.parse("minecraft:logs"), 1)
        .id("first_sample"))
    .reward("completed_investigation", CollectionEntryRewardTrigger.BINDING_COMPLETE,
        EntryRewardGrantMode.AUTO, new ItemReward(Items.STICK, 2))
    .build();
```

三个便捷方法 `discoveryReward(String, IReward...)`、`researchReward(String, IReward...)`、`bindingReward(String, IReward...)` 都默认 `MANUAL`。通用方法为：

```java
reward(String rewardId, CollectionEntryRewardTrigger trigger, IReward... rewards)
reward(String rewardId, CollectionEntryRewardTrigger trigger,
       EntryRewardGrantMode grantMode, IReward... rewards)
reward(CollectionEntryRewardDefinition definition)
```

也可以直接创建 `new CollectionEntryRewardDefinition(rewardId, trigger, rewards)`，或使用带 `grantMode` 的四参数构造器。省略领取模式、或四参数构造器传入 `null` 模式，均使用 `MANUAL`。`entry.getRewards()` 读取定义；每个定义提供 `rewardId()`、`trigger()`、`grantMode()`、`rewards()`。

#### MANUAL、AUTO 与已有记录

`MANUAL` 在资格成立后等待玩家从条目次级详情领取。发现和研究即使在未接任务时也能积累永久资格；领取仍要求玩家已知一份合法引用该 Entry 的图鉴 Quest，并且条目已经公开。已知范围包括接取中、完成过或失败过的任务；随意发送未知任务、隐藏条目或未满足条件的请求不能发奖。

永久 `AUTO` 在实际首次发现或研究完成的状态转换时执行，不要求事先接任务。接取、重接、打开资料和登录恢复已有记录不会伪造这种事件。为已经完成的永久记录新增 `AUTO` 奖励，也不会仅因恢复记录补发；需要给已有玩家补领时，应配置 `MANUAL` 或编写明确的数据迁移。

本轮 `AUTO` 在 Binding 结果成立后登记并发放，不需要等待整份任务完成；每个新 Run 可以独立获得。`AUTO` 不接受客户端手动领取请求。两种模式都先登记已领取凭证再执行实际奖励；重复请求和奖励回调重入不会重发。一个实际奖励异常时记录日志并继续后续奖励，保留已领取凭证，整组不会自动重试。

#### 往期调查欠奖与客户端 API

普通归档保留最后结束的 Run；更早的 Run 只要还有已解锁未领取的条目调查奖励，也会继续保留。新任务的接取、再次完成、失败、退出与存档恢复不会用最新归档覆盖这些欠奖。条目详情中会追加「往期调查奖励」，玩家可以领取原来那轮的奖励；领取旧奖励不会消费当前轮的同名奖励。更早的 Run 全部结算后才释放，最后一轮归档仍保留用于查看。

这些归档与永久图鉴记录都随玩家数据保存、复制和同步，不需要另一个图鉴 Screen。管理员明确执行任务 `reset` 会清该任务归档，因此不能用 reset 来验证正常重复任务的欠奖保留；玩家永久发现与研究仍然保留。

扩展客户端 UI 时，使用服务端授权投影 `CollectionBindingProgress.entryRewards()`；每行提供 `definition()`、`unlocked()`、`claimed()`、`canClaim()` 和 `sourceRunId()`。本轮及往期奖励都携带其准确来源 Run，永久奖励的 `sourceRunId()` 为空。领取调用：

```java
ArcQuestNetwork.sendClaimCollectionEntryReward(
    C2SClaimCollectionEntryRewardPacket.of(
        questId, projected.sourceRunId(), phaseId, bindingId,
        projected.definition().rewardId()
    )
);
```

不要用界面当前 Run 替换 `sourceRunId()`。服务端按 `questId, runId, phaseId, bindingId, rewardId` 定位凭证；永久奖励不依赖 Run，但仍验证任务、阶段与绑定引用。本轮请求必须匹配有效的确切 Run，旧模态窗口不能误领取刚接取的新轮奖励。锁定奖励内容及隐藏条目的奖励凭证不会通过网络提前下发；已获授权的奖励物品复用任务奖励 Tooltip，支持 JEI 左键配方、右键用途。

#### 稳定 ID 与迁移

| ID | 唯一范围与用途 |
| --- | --- |
| Quest 资源 ID | 任务注册标识与本轮奖励归属 |
| `entryId` | 共享注册的条目标识；永久奖励不额外以 Quest 隔离 |
| `phaseId` | 同一 Quest 内唯一 |
| `bindingId` | 同一 Phase 内唯一；同 Entry 在不同 Phase/Binding 的调查奖励各自计次 |
| Objective `id` | 同一 Phase，或 Entry 内各自的发现/研究列表内唯一；长期记录分别使用发现/研究键 |
| `blockId` | 同 Entry 内唯一，供资料和已读状态引用 |
| `rewardId` | 同 Entry 内跨 trigger 唯一；不同 Entry 可以复用这个字符串 |
| `runId` | 运行时生成，作者不手写；每次重新接取生成新的调查身份 |

稳定 ID 的改名是数据迁移，不是界面改名。调整文案、排序或头像保留原 ID；修改奖励内容也不会让同 ID 的已领取凭证重新变成可领取。共享条目在不同任务中的定义必须一致，奖励定义也参与一致性检查。

将同 `rewardId` 从 `DISCOVERED` 改成 `RESEARCH_COMPLETE` 仍复用永久领取凭证，不自动再发。将永久 trigger 改成 `BINDING_COMPLETE`，或反过来，会跨越两个独立生命周期，不能悄悄复用 ID 并期待系统自动映射；应发布新的 `rewardId` 并明确新奖励资格，或在迁移中把旧凭证映射到新的作用域。永久凭证与旧 `rewardNodes.nodeId` 不自动互转。

迁移应先备份与导出玩家数据，保留 `entryId/phaseId/bindingId/rewardId` 对照，再处理记录、归档和奖励凭证。欠奖按当前有效配置及真实引用领取；删除 Entry、Binding 或奖励定义之前应完成结算或迁移，恢复原定义才会重新开放合法旧凭证。网页编辑器只编辑内容定义，不读取玩家存档，也不自动替作者完成凭证迁移。

## 11. 旧配置兼容与迁移

旧 `collectionConfig` 分类、规则、奖励节点及 Phase 的 `collectionEntryConfig` 可以继续导入编辑器。旧奖励节点使用 Java 的 `nodeId/scope/scopeRefId/grantMode/completionRules/rewards` 字段，编辑器导出时完整保留，不转换成无效的 `rewardId/completionMode`。

新表单和旧条目 Phase 表单分开显示；后者收纳在兼容区。同一个 Phase 不能混用新 `collectionSheet` 与旧 `collectionEntryConfig`，主动迁移后可以在兼容区点击「移除旧条目配置」。新任务使用正常 Quest 完成策略与目标板门槛，不使用旧顶层 `collectionConfig.completionRules`。原始数据导入时不自动改变 Phase ID 或生成新 Binding，因此不会仅因为打开编辑器就改变已发布任务语义。

作者主动迁移时，应先确定真实调查章节，再建立 Entry 和 Binding，并保存旧 ID 到新稳定 ID 的对应关系。旧存档转换由运行时迁移负责，网页编辑器不读取或修改玩家存档。

## 12. 提交与验证内容

作者发布前至少核对：

- Entry、Binding、Objective、内容块 ID 不重复；所有引用存在。
- 长期发现与任务行动实际对应目标说明，重复委托不会仅靠旧记录直接领奖。
- 配额不大于候选，Tag 数量与不同种类要求没有混淆。
- 神秘条目及研究资料没有在目录、Tooltip 或 JEI 关联入口提前泄露。
- 配图资源存在，保持比例，大字号下可以滚动阅读。
- 图标、详情、图片放大与 Tooltip 随任务面板淡出，并受裁切和模态遮挡约束。
- JEI 返回、面板重开和重新登录后，任务状态与浏览焦点正确。
- 首次发现/研究只领取一次，本轮绑定奖励在新的 Run 内独立计次；重复完成多轮后仍能领取往期欠奖。

编辑器的自动检查覆盖数据往返、稳定引用、配额、研究步骤、图片尺寸和旧奖励节点兼容；实际事件检测、多玩家存档、奖励及游戏内渲染仍需要游戏侧验收。

## 13. 内置可玩 Demo

三份范例通过 `CollectionFieldDemos.registerAll()` 注册，共享九个 `arc_quest:codex/*` 长期条目。正常注册模组内容后即可用管理员命令接取，默认按 **J** 打开任务面板。

| 任务 ID | 标题 | 可以检查的行为 |
| --- | --- | --- |
| `arc_quest:field_compendium_demo` | 荒野手册 | 单章多条目、分类、生物二维头像、蜘蛛神秘占位、Tag 图标轮换、长期发现与本轮击败/制作/提交、研究解锁文字和 Guide 配图 |
| `arc_quest:renewable_survey_demo` | 轮值调查委托 | 六个候选中任意完成三个，本轮行动重新计数，永久发现与研究记录保持，奖励后可以重新接取 |
| `arc_quest:parallel_expedition_demo` | 营地联合调查 | 准备章节完成后生物/材料两章并行，两章都完成后合流到样本提交，含普通与 Tag 的 OFFER 目标 |

分别启动：

```mcfunction
/arcquest quest give @s arc_quest:field_compendium_demo
/arcquest quest give @s arc_quest:renewable_survey_demo
/arcquest quest give @s arc_quest:parallel_expedition_demo
```

为了看到从零开始的图鉴状态，建议使用新的测试玩家；`quest reset` 只重置任务，不清空长期知识。首次获得煤炭、原木或铁锭会留下发现记录；击败一次蜘蛛后，其匿名条目才公开真实身份。与牛互动也可建立发现记录。

铁锭制作目标检测的是**合成结果为铁锭**：用九个铁粒合成铁锭即可测试，熔炉冶炼不算 CRAFT。收集与提交是不同动作，OFFER 必须在条目详情的目标提交入口实际消耗背包中的样本；查看图片或 JEI 不会推进。

「荒野手册」完成全部八个必需 Binding 后手动回报，奖励两枚绿宝石。僵尸累计击败五次后解锁 `anatomy_notes` 长期资料。轮值任务完成任意三个本轮候选后手动回报，奖励一枚绿宝石；重接后行动归零，图鉴依然保留。并行任务在准备阶段获得煤炭后进入两条调查，每条完成三个候选中的两个，随后提交四个原木和一个铁锭，回报奖励一枚钻石。

荒野手册还包含两个独立里程碑：完成任意三个绑定自动获得一份煤炭；完成全部「生物」绑定后可手动领取一枚绿宝石。它们与最终任务奖励分别记录，手动里程碑在任务归档后仍可领取。

三个 Demo 的共享僵尸条目还配置了 `zombie_first_record`（首次发现，手动一份煤炭）、`zombie_anatomy`（永久五次击败研究，手动三个铁粒）和 `zombie_investigation`（完成各任务的僵尸 Binding，手动一枚绿宝石）。原木条目的 `logs_investigation` 在每次对应 Binding 完成时自动发两个木棍。这些条目奖励独立于前述里程碑和最终任务奖励；详细体验路线见 [compendium-quest-demos.md](compendium-quest-demos.md)。

共享条目涵盖僵尸、骷髅、蜘蛛、牛、铁锭、煤炭、原木、骨头和腐肉。原木是一个 Tag 条目，木种之间轮换图标且共用数量；这个范例不把它写成不同物种收集。内置配图位于 `assets/arc_quest/textures/gui/collection/field_notes.png` 与 `mineral_notes.png`。

编辑器契约示例位于 `arc_quest_editor_modular/test/fixtures/collection-quest.json`；该文件包含两章及一个外部共享条目引用，是自动测试夹具，不作为独立数据包范例直接安装。

## 14. 同步与权限边界

未接取、未完成或失败过的图鉴任务仅下发匿名条目定义；即使玩家在别处已有发现，也不会通过另一份尚未知晓的任务提前取得身份信息。已接取任务按该玩家的发现与研究状态公开名称、主体、图标、关联物品和图文。锁定图片和隐藏物品从内容数据包中移除，不只是在界面中遮住。

记录同步仅包含该玩家已知任务中可公开的 Entry。接取任务时会补发此前已积累的长期记录，因此旧发现仍可以满足 `EXISTING_RECORDS`。任务本轮的发现基线只包含这个 Phase 绑定的条目，不发送其他图鉴的发现列表。未公开内容块的已读标记和旧迁移内部字段不进入客户端同步。

普通计数增加或已读状态改变不会重新编码、压缩所有任务内容。只有发现、研究门槛达成、任务可知范围改变或数据包更新使资料权限改变时，才重建该玩家的图鉴内容。客户端收到新定义会清理投影缓存，即使数据包 epoch 没有变化，也能立即看到刚解锁的资料。

条目奖励新获授权也会更新该玩家的内容权限，即使本轮 Binding 的变化没有增加永久记录 revision。全量及增量本轮快照都按接收玩家的可见条目过滤奖励凭证，不将隐藏 Entry 的奖励 ID 发到客户端。当前 ArcQ 任务网络协议版本为 **19**；Forge 1.20.1 和 NeoForge 1.21.1 各自保留平台网络实现，客户端与服务器需要使用相同平台和支持协议 19 的版本。JSON 数据格式不填写协议号。
