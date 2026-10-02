# 图鉴模式 Quest：可游玩范例与 API 用法

本文对应现代 `CollectionEntryDefinition`、`EntryRequirementBinding` 与 `CollectionSheetDefinition`。三个内置范例使用实际的任务生命周期、目标事件、阶段和奖励，不依靠每个条目一个 Phase。

## 1. 三个内置任务

| 任务 ID | 面板标题 | 用来体验什么 |
| --- | --- | --- |
| `arc_quest:field_compendium_demo` | 荒野手册 | 八个条目的 ALL 调查、已有知识认可、本轮击败与制作、样本提交、可收纳图文、隐藏条目 |
| `arc_quest:renewable_survey_demo` | 轮值调查委托 | 六个候选任意完成三个，显示 `0/3` 与候选数量；重复接取重新计算行动，保留长期图鉴 |
| `arc_quest:parallel_expedition_demo` | 营地联合调查 | 真正的准备、并行生物/材料调查、汇合回报；每个调查章节内部拥有多个条目 |

旧 `collection_codex_demo` 仍保留定义，供旧存档和历史读取；它的接取条件恒为 false，不再作为新任务接取。已接取的旧任务及奖励凭证不会被删除。

三个现代任务共用 `arc_quest:codex/zombie`、`skeleton`、`spider`、`cow`、`iron_ingot`、`coal`、`logs`、`bone`、`rotten_flesh`。同一次真实事件可推进永久记录与当前激活任务，但不会给尚未激活的后续阶段预先计数。

## 2. 在游戏里打开范例

使用有管理权限的玩家执行以下命令。`@s` 指执行命令的玩家；服务器控制台请改成玩家名字。

```mcfunction
/arcquest quest give @s arc_quest:field_compendium_demo
/arcquest quest give @s arc_quest:renewable_survey_demo
/arcquest quest give @s arc_quest:parallel_expedition_demo
```

随后使用当前绑定的「任务面板」按键打开任务日志，在「图鉴」分类选择对应任务。点击条目浏览，点击「追踪此条目」明确聚焦；收起详情可以扩展标本目录，点击配图可放大。查询物品的 JEI 配方或用途不会改变选中条目、任务计数或追踪焦点。

为了直接体验调查，可以使用原版指令准备环境和材料，但目标动作仍由实际事件检测：

```mcfunction
/time set night
/summon minecraft:zombie
/summon minecraft:skeleton
/summon minecraft:spider
/summon minecraft:cow
/give @s minecraft:iron_nugget 18
/give @s minecraft:crafting_table 1
/give @s minecraft:oak_log 16
/give @s minecraft:coal 8
```

玩家亲手击败生物才触发击败规则；牛使用右键互动发现。铁锭的 CRAFT 范例要求在工作台中用铁粒合成，熔炉冶炼不会被当成合成。通过指令取得物品会由现有背包增量检测识别为获得，一般不超过一秒。提交目标在详情中的实际提交入口完成，并且消耗背包物品。

## 3. 荒野手册的要求

| 条目 | 永久记录 | 本任务要求 |
| --- | --- | --- |
| 僵尸 | 首次击败发现；累计五次击败解锁研究资料 | 已发现、本轮击败三只、提交两份腐肉 |
| 骷髅 | 首次击败发现；累计三次击败完成研究 | 已发现、本轮击败两只 |
| 蜘蛛 | 首次击败后公开资料 | 已发现 |
| 牛 | 右键互动发现，无额外长期研究要求 | 已发现 |
| 铁锭 | 首次获得发现；两次铁粒合成作为长期研究 | 已发现、本轮用铁粒合成一个铁锭 |
| 煤炭 | 首次获得发现；累计五份样本研究 | 长期 `fuel_samples` 研究步骤完成 |
| 原木 | 接受 `minecraft:logs` 的所有成员 | 本轮获得八份原木 |
| 骨头 | 首次获得发现 | 已发现 |

已经发现僵尸只会满足「已发现」，不会替代当前任务的 `0/3` 击败和 `0/2` 提交。已有煤炭研究可以立即满足记录型要求。原木 Tag 的候选图标轮换，点击时查询当前显示的真实物品。

八个绑定达成后等待玩家确认任务，正常任务奖励为两枚绿宝石。等待提示短暂显示，实际领取和回报状态留在任务面板。

荒野手册另有两个调查里程碑，可在目录的奖励区域查看：任意完成三个绑定后自动获得一份煤炭；完成「生物」分类的僵尸、骷髅、蜘蛛和牛四个绑定后解锁一枚绿宝石，玩家手动领取。前者只触发一次，后者在任务回报并归档后仍可领取，重复点击不会重复发奖。判断依据是这次任务的完整绑定要求，只有旧发现不能替代僵尸或骷髅的本轮行动。

可安装 JSON 范例的荒野手册配置同样的两个里程碑；其最终任务奖励为一枚绿宝石。轮值委托和联合调查没有这些额外节点，便于单独体验重复与并行生命周期。

## 4. 重复委托与真实并行章节

轮值委托包含六个候选：击败两只僵尸、击败两只骷髅、击败一只蜘蛛、获得八份原木、获得四份煤炭、用铁粒合成两个铁锭。完成任意三个就可确认，剩余候选不会阻止完成。重新接取生成新的 `runId` 和零行动计数；永久发现、研究和已经读过的资料保留。

营地联合调查先要求获得一份准备燃料，之后实际同时激活 `wildlife` 和 `materials`：

- 生物调查：僵尸、骷髅、牛三个候选中完成两个。
- 材料调查：原木、煤炭、铁锭三个候选中完成两个。
- 两个调查章节都完成之后进入 `report`，提交四份任意 Tag 原木和一个铁锭，确认后领取一颗钻石。

准备阶段的那一次煤炭获得事件不会回填刚激活的材料阶段。详情顶部选择真实 Phase，分类按钮只过滤同一 Phase 的标本目录。

## 5. 最小 Java 配置

```java
var entry = CollectionEntryBuilder.create("my_pack:codex/iron")
    .category("materials")
    .item(Items.IRON_INGOT) // 默认取得物品名称与 AUTO 物品图标
    .discover(ObjectiveBuilder.collect(Items.IRON_INGOT, 1).id("first_sample"))
    .text("notes", "常用金属材料。")
    .image("diagram", ResourceLocation.parse("my_pack:textures/codex/iron.png"),
           240, 120, "加工示意")
    .build();

// 共享定义可只注册一次，多份任务使用同一个 entry。
CollectionEntryRegistry.register(entry);

var config = CollectionQuestConfigBuilder.create()
    .category("materials", "材料")
    .entry(entry)
    .build();

var quest = QuestBuilder.create("my_pack:iron_survey")
    .category(QuestCategory.COLLECTION)
    .mode(QuestMode.COLLECTION)
    .collectionConfig(config)
    .phase(PhaseBuilder.create("survey")
        .objective(ObjectiveBuilder.craft(Items.IRON_INGOT, 2).id("craft_iron"))
        .collectionSheet(CollectionSheetBuilder.create()
            .binding(EntryRequirementBuilder.create("iron", entry.getEntryId())
                .discovered()
                .objective("craft_iron")))
        .autoAdvanceOnComplete(false))
    .reward(new ItemReward(Items.EMERALD, 1))
    .build();

ArcQuestAPI.registerQuest(quest);
```

记录型要求可以使用 `.discovered()`、`.researched()` 或 `.researchStep("stable_step_id")`；本轮要求引用真实 Phase Objective ID。`.requirementMode(ANY)` 表示绑定内部任一必需要求达成；默认 ALL。配额使用 `CollectionSheetBuilder.quota(n)`，分类和候选数量不会被误当作门槛。

每个条目的 `recordWhen(ICondition)` 是永久记录资格，不是 UI 可见性。`researchAfterDiscovery(true)` 表示发现之前不推进研究，默认 false。一个真实事件可以先发现再研究各一次，不重复消费。

## 6. 内容解锁、头像和图片

图文块复用 `GuideMediaDefinition`。默认图片按 CONTAIN 保持比例，并可点击放大。要配置更细的资料解锁，可以传入完整块：

```java
new CollectionContentBlock(
    "anatomy_notes", QuestText.literal("研究资料"), imageMedia,
    QuestText.literal("图片说明"), CollectionMediaFit.CONTAIN, true,
    CollectionContentReveal.RESEARCH_STEP, "anatomy"
);
```

`reveal` 支持 ALWAYS、DISCOVERED、RESEARCH_COMPLETE、RESEARCH_STEP。未知条目和尚未获准资料不会通过图标、图片预加载或 JEI 目录公开。

生物保持二维头像，不渲染实体模型。AUTO 在已有头像适配中解析；不存在头像的生物保留文字，不额外渲染刷怪蛋。可以配置裁切纹理、图标 provider 或显式 `iconItem`；只要存在有效物品绑定，就有该物品的 JEI 交互。

## 7. 可直接安装的 JSON 数据包

目录 `docs/examples/collection/collection-demo-pack` 是完整数据包。复制整个目录到存档的 `datapacks` 中，执行 `/reload` 后使用以下 ID：

```mcfunction
/arcquest quest give @s arc_quest_examples:field_compendium_demo
/arcquest quest give @s arc_quest_examples:renewable_survey_demo
/arcquest quest give @s arc_quest_examples:parallel_expedition_demo
```

目录结构为：

```text
collection-demo-pack/
  pack.mcmeta
  data/arc_quest_examples/arc_quest/quests/
    field_compendium_demo.json
    renewable_survey_demo.json
    parallel_expedition_demo.json
```

三个 JSON 都内联了相同的共享 Entry 定义，不依赖文件加载顺序。它们使用独立命名空间，可与内置 Java 范例同时加载。`collectionConfig.entryIds` 仅用于引用已经通过 Java/API 注册的 Entry；跨 JSON 文件共享通过相同的 inline `entryId` 和一致定义完成，冲突定义会被拒绝。

当前分支的范例数据包针对 Minecraft 1.21.1，`pack_format=48`。生成器读取所在仓库的 `gradle.properties`，在 1.20.1 分支生成格式 15，在 1.21.1 分支生成格式 48；三个任务的 JSON 内容保持一致。图片由模组资源提供，不包含外部网络地址。

## 8. 配置边界与奖励规则

- Entry 的发现/研究 Objective、Phase 中被绑定的 Objective、Binding 和图文块都需要显式稳定 ID。
- 可选 Objective 可展示，但不阻止绑定达成；一个绑定不能只有可选要求。
- `NEW_DISCOVERIES` 只用于 DISCOVERED 记录要求；接取时保存已有发现基线，无法完成的配额需在接取诊断中报告。
- 现代任务最终完成使用普通 Quest Phase completionPolicy 与 Sheet 的 ALL/QUOTA。旧 `collectionConfig.completionRules` 不允许覆盖现代真实阶段生命周期。
- 里程碑 `rewardNodes` 的规则仍可使用完成条目数、条目完成比例、完成分类数等，并由统一的绑定投影计算。
- 奖励规则中的 ALL 表示所有必需候选绑定，不等于任意达成配额。配额任务的正常最终奖励写在 `completionRewards`。
- 自动和手动里程碑保存同一轮次的 unlocked/claimed 凭证；手动奖励在任务归档后仍可领取，重复请求不会再次给物品。
- 旧任务中已经领取的同 ID 奖励凭证会迁移，现代节点不会再次发放。

## 9. 验证与维护

`CollectionSheetSemanticsTest`、`CollectionRecordServiceTest`、`CollectionSheetCompatibilityTest` 覆盖定义、JSON、长期/本轮区分、ALL/ANY/QUOTA、可选要求、唯一种类计数、稳定 ID、热重载缺失绑定及客户端/服务端隔离。

`CollectionQuestGameTests` 使用真实加载的 Tag 和服务端事件，覆盖未接任务的永久发现、登录背包只建立发现、事件不回填新阶段、放弃保留记录、手动确认、重复新轮次、归档领奖防重及实际 Tag 提交消耗。

`docs/examples/collection/generate-demo-pack.py` 仅使用 Python 标准库，可重新生成三个 JSON 数据包范例与两张原始像素示意配图，并根据所在分支的 Minecraft 版本生成 `pack.mcmeta`。未配置或不支持的版本会在写入前报错。图鉴目录和资料渲染本身不读取或执行此脚本。
