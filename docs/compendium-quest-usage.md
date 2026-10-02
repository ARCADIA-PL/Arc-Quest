# 图鉴模式 Quest：内容作者使用说明

本说明对应新的 `collectionConfig.entries` 与 `phase.collectionSheet` 数据格式。完整产品设计见 [compendium-quest-design.md](compendium-quest-design.md)。图鉴直接显示在任务面板中，无需另一个图鉴 Screen。

## 1. 先区分 Entry、Binding 和 Phase

| 对象 | 作者配置 | 意义 |
| --- | --- | --- |
| Entry | `collectionConfig.entries[]` | 一个长期知识对象：名称、主体、头像、资料、发现与研究规则 |
| Binding | `phase.collectionSheet.bindings[]` | 这份任务对这个条目的具体要求 |
| Phase | `phases[]` | 实际调查章节，拥有正常目标、流程、并行分支和奖励 |

同一个 `arc_quest:zombie` Entry 可以被多个任务引用：首次调查要求已发现，另一份任务要求研究完成，重复委托要求本轮击败 5 只。Entry 已发现并不意味着每一个 Binding 都完成。

不要为每个 Entry 添加一个 Phase。一章可以拥有多个 Binding；Phase 的进入条件、转移和并行关系继续按普通 Quest 配置。

`entryId`、`bindingId`、Objective `id`、图文 `blockId` 是持久化标识，发布后尽量保持不变。改显示名称、调整排序和新增资料不需要改变这些 ID。

## 2. 在编辑器里建立任务

1. 在任务基础设置中选择 `COLLECTION`。
2. 在分类配置中添加生物、材料等分类。
3. 在「图鉴条目定义」中添加 Entry，填写稳定 ID、名称、主体和所属分类。
4. 编辑发现规则与长期研究步骤，按实际行为选择 `KILL`、`COLLECT`、`CRAFT` 等 Objective。
5. 在「Guide 式图文资料」中添加说明和配图；可以配置何时公开。
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
| `categoryId` | 字符串 | 引用本任务声明的分类；空值为未分类 |
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

点击条目打开详情；详情可以收起，收起后陈列区扩大。分类、搜索、选择和滚动属于界面状态，不能改变任务计数。图片放大关闭、JEI 返回或界面退出时，应回到原有浏览位置。

「追踪任务总览」展示任务标题和总进度。「追踪此条目」保留任务身份，聚焦这个 Binding 的未完成要求。点击目录只查看资料，不自动改变追踪。

聚焦条目达成后短暂反馈并回到总览；任务待回报或领奖只短暂显示，待办留在任务面板，不永续遮挡视野。游戏内追踪器不抢占鼠标，JEI 查询在任务面板中完成。

## 11. 旧配置兼容与迁移

旧 `collectionConfig` 分类、规则、奖励节点及 Phase 的 `collectionEntryConfig` 可以继续导入编辑器。旧奖励节点使用 Java 的 `nodeId/scope/scopeRefId/grantMode/completionRules/rewards` 字段，编辑器导出时完整保留，不转换成无效的 `rewardId/completionMode`。

新表单和旧条目 Phase 表单分开显示；后者收纳在兼容区。原始数据导入时不自动改变 Phase ID 或生成新 Binding，因此不会仅因为打开编辑器就改变已发布任务语义。

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

编辑器的自动检查覆盖数据往返、稳定引用、配额、研究步骤、图片尺寸和旧奖励节点兼容；实际事件检测、多玩家存档、奖励及游戏内渲染仍需要游戏侧验收。

## 13. Demo 的编排建议

内置范例按不同使用场景分开，避免一个任务把所有能力混在一起：

| 范例场景 | 应展示的配置 |
| --- | --- |
| 荒野手册 | 生物头像、材料分类、长期发现、本轮调查与资料解锁 |
| 材料任选调查 | Tag 任一候选、不同物品 Entry、QUOTA 门槛 |
| 营地样本委托 | 重复任务、本轮 OFFER/DELIVER、奖励与重接 |
| 并行调查 | 两个真实调查 Phase 同时活跃，各自拥有多个条目 |

实际 Demo 的任务 ID、入口和启动步骤在游戏侧注册完成后补充。编辑器契约示例位于 `arc_quest_editor_modular/test/fixtures/collection-quest.json`；该文件包含两章及一个共享条目引用，是自动测试夹具，不作为独立数据包范例直接安装。
