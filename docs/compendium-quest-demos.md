# 图鉴模式 Quest：可游玩范例与 API 用法

本文对应现代 `CollectionEntryDefinition`、`EntryRequirementBinding` 与 `CollectionSheetDefinition`。三个内置范例使用实际的任务生命周期、目标事件、阶段和奖励，不依靠每个条目一个 Phase。

## 1. 三个内置任务

| 任务 ID | 面板标题 | 用来体验什么 |
| --- | --- | --- |
| `arc_quest:field_compendium_demo` | 荒野手册 | 八个条目的 ALL 调查、已有知识认可、本轮击败与制作、样本提交、次级详情图文、隐藏条目 |
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

随后使用当前绑定的「任务面板」按键打开任务日志，在「图鉴」分类选择对应任务。默认展示目录；点击条目名称或卡片文字区域打开面板内的次级详情，顶栏「返回目录」关闭详情，配图可点击放大。详情与目录分别可以滚动和拖动滑条，Esc 依次关闭配图、详情、任务面板。

追踪时默认选择一个已公开、未完成且可行动的 Binding，保留任务名并展示该条目的要求。详情顶栏的「追踪此条目」可以手动切换；浏览条目本身不切换追踪。达成后短暂反馈，再切换下一条或隐藏，不回到永续显示的任务总览。并行阶段可以选择不同章节中的条目，重接任务不复用上一轮焦点。

物品图标的左键查看 JEI 配方，右键查看用途；查询不打开详情、不改变任务计数、不提交样本或领取奖励。物品 Tooltip 复用任务奖励栏，不显示 JEI 快捷键说明。

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

### 3.1 三个任务共用的条目奖励

下表逐项对应 `CollectionFieldDemos.entries()` 及 JSON 生成器。只有僵尸和原木 Entry 配置了新条目奖励；其他条目没有默认发奖。

| Entry | 稳定 `rewardId` | `trigger` / 模式 | 实际奖励 | 计次规则 |
| --- | --- | --- | --- | --- |
| 僵尸 | `zombie_first_record` | `DISCOVERED` / `MANUAL` | 煤炭 ×1 | 每玩家、该僵尸 Entry 永久一次 |
| 僵尸 | `zombie_anatomy` | `RESEARCH_COMPLETE` / `MANUAL` | 铁粒 ×3 | 永久累计击败五次后一次 |
| 僵尸 | `zombie_investigation` | `BINDING_COMPLETE` / `MANUAL` | 绿宝石 ×1 | 每个任务、每个 Run、对应 Phase/Binding 各一次 |
| 原木 | `logs_investigation` | `BINDING_COMPLETE` / `AUTO` | 木棍 ×2 | 每个对应 Phase/Binding 的本轮完成各一次 |

三个内置 Java 任务共享 `arc_quest:codex/zombie` 和 `arc_quest:codex/logs`，所以切换任务不会重发永久首次发现/研究奖励。调查奖励跟各自 Binding 的完整要求走：荒野手册的僵尸需要「已发现 + 三次本轮击败 + 提交两份腐肉」，轮值委托和并行生物章需要各自的两次本轮击败。永久已发现不会替代这些行动。

内置并行任务的原木 Entry 被材料章与回报章分别绑定：材料章实际完成八份获得目标时发两个木棍，回报章提交四份原木时再发两个。若材料章通过煤炭和铁锭达成配额而没有完成原木 Binding，材料章的木棍奖励不会被回报操作补发。

这些条目奖励独立于任务最终奖励和荒野手册的两个里程碑。打开僵尸次级详情，在条目奖励区域手动领取；原木调查奖励直接发放。奖励物品支持 JEI 配方/用途查询。已解锁但未领取的旧调查奖励会在后续 Run 的对应详情显示为「往期调查奖励」，不是自动结算成当前轮奖励。

### 3.2 建议的验收路线

1. 用新测试玩家在接取前亲手击败一只僵尸，然后接取任一 Demo：应该已有永久发现资格，可以手动领一份煤炭，当前任务的击败/提交要求仍从本轮开始。
2. 接取轮值委托，完成僵尸两次击败、八份原木和四份煤炭这三个候选：原木自动给两个木棍，僵尸调查可手动领一枚绿宝石。整个任务确认后的最终奖励另为一枚绿宝石。
3. 暂时不领僵尸调查奖励，确认任务、重接并再完成两轮；退出再登录。对应条目应保留各轮欠奖，并能逐条领取往期奖励；领取一轮不改变另一轮的凭证。
4. 跨多个 Run 累计五次僵尸击败后，永久研究解锁三个铁粒。接取荒野手册或联合调查仍显示同一份首次发现/研究已领取状态，自己的本轮调查奖励继续独立获得。
5. 已结算的更早 Run 不再占用欠奖归档，最后一轮仍可查看。重复点击领取不能重复发奖；JEI 查询、详情关闭或图片放大不会触发领取。

使用正常确认和重复接取来验证欠奖保留。管理员 `quest reset` 会明确清除任务归档，永久知识虽保留，往期欠奖不会由 reset 恢复。

## 4. 重复委托与真实并行章节

轮值委托包含六个候选：击败两只僵尸、击败两只骷髅、击败一只蜘蛛、获得八份原木、获得四份煤炭、用铁粒合成两个铁锭。完成任意三个就可确认，剩余候选不会阻止完成。重新接取生成新的 `runId` 和零行动计数；永久发现、研究和已经读过的资料保留。

内置 Java 的营地联合调查先要求获得一份准备燃料，之后实际同时激活 `wildlife` 和 `materials`：

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

### 5.1 条目奖励 Java 与 JSON

以下 Java 配置与内置僵尸 Demo 的奖励一致：

```java
var zombie = CollectionEntryBuilder.create("my_pack:codex/zombie")
    .category("living")
    .entity(EntityType.ZOMBIE)
    .discover(ObjectiveBuilder.kill(EntityType.ZOMBIE, 1).id("first_defeat"))
    .research(ObjectiveBuilder.kill(EntityType.ZOMBIE, 5).id("anatomy"))
    .discoveryReward("zombie_first_record", new ItemReward(Items.COAL, 1))
    .researchReward("zombie_anatomy", new ItemReward(Items.IRON_NUGGET, 3))
    .bindingReward("zombie_investigation", new ItemReward(Items.EMERALD, 1))
    .build();
```

三个便捷方法均为 `MANUAL`。原木 Demo 的自动调查奖励使用通用重载：

```java
.reward("logs_investigation", CollectionEntryRewardTrigger.BINDING_COMPLETE,
    EntryRewardGrantMode.AUTO, new ItemReward(Items.STICK, 2))
```

JSON 在对应 Entry 内配置，例如原木的完整奖励字段：

```json
"rewards": [{
  "rewardId": "logs_investigation",
  "trigger": "BINDING_COMPLETE",
  "grantMode": "AUTO",
  "rewards": [{"type": "item", "itemId": "minecraft:stick", "count": 2}]
}]
```

`DISCOVERED` 和 `RESEARCH_COMPLETE` 依赖永久记录；没有非可选研究步骤时，发现即研究完成。`BINDING_COMPLETE` 使用当前 Binding 的 ALL/ANY 结果，不把全部候选都当作已完成。省略 `grantMode` 默认 MANUAL；永久 AUTO 只在首次发现/研究状态转换时发放，接取和恢复已有记录不补造事件。已有玩家需要补领新永久奖励时，使用 MANUAL 或明确迁移。

`rewardId` 为 1–128 字符，同一个 Entry 内跨三个 trigger 都必须唯一；不同 Entry 可以复用字符串。发布后保留 Entry、Phase、Binding 与 reward ID。永久与本轮作用域分别保存，修改 trigger 跨越作用域时要更换 ID 或明确迁移，不能依靠改名自动重新领奖。条目 `rewardId` 不与里程碑 `nodeId` 互转。

扩展 UI 读取 `CollectionBindingProgress.entryRewards()` 的 `definition/unlocked/claimed/canClaim/sourceRunId`。发送领取时使用该行 `sourceRunId()`，以 `questId, runId, phaseId, bindingId, rewardId` 定位本轮或往期凭证；永久奖励 Run 字符串为空。完整 Builder 重载、JSON 字段、已有记录行为和迁移说明见 [使用说明第 10.2 节](compendium-quest-usage.md#102-条目奖励永久知识与本轮调查分别计次)。

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

三个 JSON 内部共用 `arc_quest_examples:codex/*`，条目奖励与第 3.1 节相同。Java 的 `arc_quest:codex/*` 和 JSON 的 `arc_quest_examples:codex/*` 是不同 Entry，所以两套永久知识与奖励互相独立；同一套范例内部的三个任务共享永久凭证。

JSON 范例刻意保留一些更精简的调查内容；以下差异与生成器及实际 JSON 一致：

| 配置 | 内置 Java | 可安装 JSON |
| --- | --- | --- |
| 荒野手册最终奖励 | 绿宝石 ×2 | 绿宝石 ×1 |
| 轮值委托最终奖励 | 绿宝石 ×1 | 绿宝石 ×1 |
| 联合调查生物候选 | 僵尸两只、骷髅两只、牛互动一次，任选二 | 僵尸两只、骷髅两只、蜘蛛一只，任选二 |
| 联合调查材料合成候选 | 铁锭一个 | 铁锭两个；原木八份和煤炭四份与 Java 相同 |
| 联合调查回报 | 提交 Tag 原木四份与铁锭一个 | 只提交 Tag 原木四份 |
| 联合调查最终奖励 | 钻石 ×1 | 绿宝石 ×1 |
| 长期研究步骤 ID | 僵尸 `anatomy`、煤炭 `fuel_samples` 等各自命名 | 有研究步骤的 Entry 使用 `study` |

两套范例中的三个 trigger、永久/本轮计次及往期欠奖保留语义一致。JSON 原木调查仍在材料章及回报章分别绑定，每个实际完成的绑定自动发两个木棍。

当前分支的范例数据包针对 Minecraft 1.21.1，`pack_format=48`。生成器读取所在仓库的 `gradle.properties`，在 1.20.1 分支生成格式 15，在 1.21.1 分支生成格式 48；三个任务的 JSON 内容保持一致。图片由模组资源提供，不包含外部网络地址。

## 8. 配置边界与奖励规则

- Entry 的发现/研究 Objective、Phase 中被绑定的 Objective、Binding 和图文块都需要显式稳定 ID。
- 可选 Objective 可展示，但不阻止绑定达成；一个绑定不能只有可选要求。
- `NEW_DISCOVERIES` 只用于 DISCOVERED 记录要求；接取时保存已有发现基线，无法完成的配额需在接取诊断中报告。
- 现代任务最终完成使用普通 Quest Phase completionPolicy 与 Sheet 的 ALL/QUOTA。旧 `collectionConfig.completionRules` 不允许覆盖现代真实阶段生命周期。
- 里程碑 `rewardNodes` 的规则仍可使用完成条目数、条目完成比例、完成分类数等，并由统一的绑定投影计算。
- 奖励规则中的 ALL 表示所有必需候选绑定，不等于任意达成配额。配额任务的正常最终奖励写在 `completionRewards`。
- 自动和手动里程碑保存同一轮次的 unlocked/claimed 凭证；手动奖励在任务归档后仍可领取，重复请求不会再次给物品。
- 条目奖励使用自己的 `rewardId`，与里程碑和最终任务奖励分开；永久发现/研究按玩家与 Entry 领取一次，本轮调查按 Quest/Run/Phase/Binding 分别计次。
- 有条目调查欠奖的更早 Run 会在后续归档和玩家存档中保留，客户端以 `sourceRunId` 精确领取，结算后释放旧 Run。此保留规则不把旧里程碑扩展成完整多轮历史。
- 旧任务中已经领取的同 ID 奖励凭证会迁移，现代节点不会再次发放。

## 9. 验证与维护

`CollectionSheetSemanticsTest`、`CollectionRecordServiceTest`、`CollectionSheetCompatibilityTest` 覆盖定义、JSON、长期/本轮区分、ALL/ANY/QUOTA、可选要求、唯一种类计数、稳定 ID、热重载缺失绑定及客户端/服务端隔离。

`CollectionQuestGameTests` 使用真实加载的 Tag 和服务端事件，覆盖未接任务的永久发现、登录背包只建立发现、事件不回填新阶段、放弃保留记录、手动确认、重复新轮次、归档领奖防重及实际 Tag 提交消耗。

`CollectionEntryRewardsTest` 的七项回归覆盖永久与本轮凭证、Java/JSON 奖励往返、纯投影、隐藏内容、旧欠奖多轮归档/复制/恢复和来源 Run。`CollectionEntryRewardGameTests` 的四项真实服务端回归覆盖未接任务的永久资格、共享引用防重、准确 Run 领取、重复调查、三轮欠奖与玩家数据恢复、AUTO 状态转换及异常/回调防重。文件中包含预期异常的测试奖励，不应把它产生的日志当作普通 Demo 奖励行为。

条目奖励领取使用 ArcQ 网络协议 **19**。各平台客户端与服务器需使用同一协议版本；Forge 1.20.1 与 NeoForge 1.21.1 的数据语义相同，网络平台实现分别保留。未授权的隐藏条目、锁定奖励资料及失效凭证不提前广播；获得新奖励权限时更新对应玩家的内容投影。

`docs/examples/collection/generate-demo-pack.py` 仅使用 Python 标准库，可重新生成三个 JSON 数据包范例与两张原始像素示意配图，并根据所在分支的 Minecraft 版本生成 `pack.mcmeta`。未配置或不支持的版本会在写入前报错。图鉴目录和资料渲染本身不读取或执行此脚本。
