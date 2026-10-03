# 图鉴模式 Quest：三条可玩 Demo 路线

2026-10-03。当前 Java Demo 的冻结规则版本为 **`collection-v3`**，Entry 仍使用 **`gameplayVersion: 2`**。前者区分 Demo 的数值、流程、资料和奖励版本，后者区分统一调查与旧 Research 执行语义；不能把 Entry 改为 `gameplayVersion: 3`。

Java 与可安装 JSON 使用相同玩法、标题、说明、资料和奖励，只重映射任务与 Entry 命名空间。本文描述新接取的 v3 路线；已有 `collection-v1`、`collection-v2` 运行继续使用原冻结规则。通用配置见 [统一调查使用说明](compendium-unified-quest-usage.md)，历史验证见 [验收记录](compendium-quest-verification.md)。

## 1. 选择路线与接取

| 任务 ID | 标题 | 玩法侧重 | 当前目录 |
| --- | --- | --- | --- |
| `arc_quest:field_compendium_demo` | 荒野手册 · 从样本到用途 | 八项完整调查，行动与样本共同产出永久成果 | 8 个条目：4 生物＋4 材料 |
| `arc_quest:renewable_survey_demo` | 营地补给 · 轮值委托 | 六选三消耗交付，可重复，接取冷却 1200 tick | 6 个材料条目 |
| `arc_quest:parallel_expedition_demo` | 营地踏勘 · 建站计划 | 持有检查→两线并行各选二→建材与照明交付 | 7 个条目：3 生物＋2 材料＋2 器材 |

每个任务只列出自身实际 Binding 引用的 Entry，并只保留相关分类。目录不会把其他 Demo 的无调查条目或空分类一起显示。同一任务中同 Entry 出现在多个阶段时仍合并为一张卡片，详情里选择对应调查。

```mcfunction
/arcquest quest give @s arc_quest:field_compendium_demo
/arcquest quest give @s arc_quest:renewable_survey_demo
/arcquest quest give @s arc_quest:parallel_expedition_demo
```

在任务面板「图鉴」分类打开任务。卡片名称或空白打开半透明详情；调查页显示本轮要求，档案页显示发现、资料与永久成果。点击追踪立即生效，0.2 秒悬停控制追踪文案与高亮的视觉切换。物品图标左键查询 JEI 配方、右键用途，浏览、收藏、图片放大和 JEI 查询不推进目标。

OFFER 样本要在提交入口实际交付，背包持有不会自动扣除或完成提交。CRAFT 只统计相应阶段激活后的真实合成事件；指令给物品、捡回旧成品和提前制备不能替代它。POSSESSION 检查当前库存，可以使用已有装备，不消耗。

## 2. 荒野手册：八项完整调查

这是一次性手册任务，`survey` 阶段要求八项全部达成，再手动确认最终回报。各 Binding 是唯一行动进度，完成后记录对应 Outcome；没有另一份永久 Research 计数。

| 条目 | 首次发现 | 本次调查要求 | 永久成果 ID／名称 |
| --- | --- | --- | --- |
| 僵尸 | 亲手击败 1 只 | 亲手击败 3 只＋交付腐肉 2 份 | `anatomy`／腐肉与行为对照 |
| 骷髅 | 亲手击败 1 只 | 亲手击败 2 只＋交付箭 2 支 | `combat`／弓箭威胁记录 |
| 蜘蛛 | 亲手击败 1 只 | 亲手击败 1 只＋交付线 2 份 | `samples`／蛛丝用途记录 |
| 牛 | 与牛互动 | 本次与牛互动＋交付牛奶桶 1 个 | `dairy`／牧场补给记录 |
| 铁锭 | 首次获得铁锭 | 本次合成铁镐 1 把 | `preparation`／铁工具制备记录 |
| 煤炭 | 首次获得煤炭 | 交付煤炭 5 份＋本次合成火把 4 根 | `fuel_samples`／燃料与照明记录 |
| 原木 | 首次获得任意 `minecraft:logs` 成员 | 交付任意原木共 8 份＋本次合成工作台 1 张 | `wood_samples`／木材加工记录 |
| 骨头 | 首次获得骨头 | 本次合成骨粉 3 份 | `cultivation`／骨粉用途记录 |

首次击败可同时建立发现并推进本次战斗目标一次。已发现不免除本次行动；牛与骨头现在都有实际行动目标，可以追踪。铁镐合成品、工作台和火把制备品保留在背包，只有列为 OFFER 的样本被消耗。骨粉按合成输出数量计算，一根骨头合成的三份骨粉可满足该要求。

牛奶交付**消耗整只牛奶桶**，不是喝牛奶，也不会由提交动作自动返空桶。首次取得 `dairy` 后可手动领取空桶 ×1；永久首次奖只领取一次，不能把它当作每次交付的容器返还机制。手持空桶对牛使用可同时完成接触并取得待交付牛奶。

原木可混用当前 run 冻结的 Tag 成员，合计八份即可，不要求每个木种交一遍。蜘蛛首次发现前显示匿名线索；真实击败后公开身份、二维头像和对应调查。资料区的 `content` 解释用途、展示图片，部分内容在对应 Outcome 取得后公开，浏览内容不会自动授予成果。

### 奖励及领取来源

| 来源 | 奖励 ID | 奖励 | 触发与计次 |
| --- | --- | --- | --- |
| 僵尸首次发现 | `zombie_first_record` | 煤炭 ×1，手动领取 | 每名玩家／Entry 首次一次 |
| 僵尸 `anatomy` 成果 | `zombie_anatomy` | 铁粒 ×3，手动领取 | 永久成果首次一次 |
| 牛 `dairy` 成果 | `cow_dairy_bucket` | 空桶 ×1，手动领取 | 永久成果首次一次 |
| 本次僵尸调查 | `zombie_investigation` | 绿宝石 ×1，手动领取 | 当前 Quest／run／phase／binding 一次 |
| 本次原木调查 | `logs_investigation` | 木棍 ×2，自动领取 | 当前实际调查一次 |
| 完成本轮三项调查 | `field_three_samples` | 煤炭 ×1，自动领取 | 当前任务里程碑 |
| 完成四项生物调查 | `field_living_complete` | 绿宝石 ×1，手动领取 | 当前生物分类里程碑 |
| 八项调查全部完成并确认 | 任务最终奖励 | 绿宝石 ×2 | 任务完成回报 |

首次奖和本次报酬在详情中分开切换，调查里程碑与最终任务奖励也有各自来源。可见预告不等于可领取；领取以服务端资格为准。领取后原位更新，不弹领取 Toast。

## 3. 营地补给：六选三，全部消耗

这是可重复的经济委托。`round` 阶段六个候选任选三个，配额达标即停止剩余候选，手动确认获得**绿宝石 ×2**。

| 候选条目 | 本轮 OFFER 交付 |
| --- | --- |
| 原木 | 任意 `minecraft:logs` 成员共 8 份，可混用 |
| 煤炭 | 4 份 |
| 铁锭 | 1 份 |
| 骨头 | 3 根 |
| 线 | 3 份 |
| 小麦 | 8 份 |

六项全部真实消耗物品，没有击杀候选、免费发现候选或普通互动报酬。已有库存可以交，但每轮都必须重新消耗；永久知识或上一轮完成状态不能抵扣。Tag 的轮换图标表示可选成员。

接取冷却为 **1200 服务器游戏 tick**，从接取时开始计时；正常 20 TPS 下为 60 秒，停服或未推进游戏 tick 的时间不计。冷却期间即使放弃或已经完成，也不能提前重接；放弃和重登不清冷却。正常重接保留知识，新 Run 的交付从零开始。

这些 Binding 没有 `.recordOutcome(...)`，所以交铁锭不会取得铁工具制备记录，交骨头也不会代替实际骨粉制作。完成补给单与深入手册调查承担不同用途。

## 4. 营地踏勘：准备、并行、建站

这是一项一次性任务，使用四个 Phase。实际目录为僵尸、骷髅、牛、铁锭、原木、火把、工作台七项；同 Entry 的准备、制作和交付在详情中按调查来源区分。

| 阶段 | 目标与完成方式 | 物品是否消耗 |
| --- | --- | --- |
| `preparation`／出发前检查 | 背包当前持有工作台 ×1、火把 ×8，两项都满足 | 否，POSSESSION |
| `wildlife`／周边生物踏勘 | 本阶段击败僵尸 ×1、击败骷髅 ×1、与牛互动 ×1，三选二 | 否，行动计数 |
| `materials`／营地器材制备 | 本阶段合成工作台 ×1、火把 ×4、铁镐 ×1，三选二 | 配方正常消耗材料，成品留在背包 |
| `report`／照明与建材交付 | 交付任意 `minecraft:planks` 木板共 ×16＋火把 ×8，两项都完成，顺序不限 | 是，OFFER |

准备要求在背包里，已经放在地上的工作台不算持有。已有装备可立即通过准备，但不算后续材料阶段的新制作；准备阶段提前制作的成品不会被再次计为刚激活阶段的事件。

准备完成后同时激活 `wildlife` 和 `materials`。两线各自三选二，任一线达标就停止其余候选；必须两线都完成，才开放 `report`。汇合读取 `phaseCompleteCurrentRun`，不会借永久 flag 或上一轮历史完成进入回报。

同时开启使用两条独立转换：`.thenGoTo("wildlife").thenGoTo("materials")`。一条转换中的多个 `targetPhaseIds` 表示随机选路；JSON 同样配置两条各含一个目标的转换。

木板可以混用冻结的 Tag 成员，合计十六份；它与原木是不同目标，界面应显示实际木板候选。建站交付完成后手动确认，最终奖励**钻石 ×1**。短途踏勘的 Binding 不授予荒野手册的深入成果，普通接触牛也不取得 `dairy`；需要奶样本的调查仍要单独完成。

## 5. 安装 JSON 数据包

把 [collection-demo-pack](examples/collection/collection-demo-pack) 整个目录复制到存档 `datapacks`，执行 `/reload`。JSON 使用独立命名空间：

```mcfunction
/arcquest quest give @s arc_quest_examples:field_compendium_demo
/arcquest quest give @s arc_quest_examples:renewable_survey_demo
/arcquest quest give @s arc_quest_examples:parallel_expedition_demo
```

两个命名空间的玩法、文字、资料公开规则、样本数量、分类／任务里程碑、最终奖励和冷却保持一致，不再存在 Java 牛／JSON 蜘蛛、不同最终金额或不同回报材料的变体。同一命名空间内的共享 Entry 保持一致定义；跨命名空间的发现、成果、收藏身份和奖励记录独立，完成 Java 任务不会把其保存进度或成果导入 JSON 任务。同一真实事件仍可能同时推进两套已激活且匹配的行动目标。

各 JSON 只内联自身实际使用的 Entry，分别为 8／6／7 项；Entry 相同的交集沿用同一完整定义。并行条件里的 `questId` 同步改为 JSON 命名空间。原版物品、实体、Tag 和模组图片资源仍使用各自真实资源 ID，不重映射为示例命名空间。

[generate-demo-pack.py](examples/collection/generate-demo-pack.py) 使用 Python 标准库，通过 [demo-blueprints.py](examples/collection/demo-blueprints.py) 读取当前 `CollectionFieldDemos.java` 的受限构建 DSL，再生成三个 JSON。遇到未知构建方法即报错，避免静默漏掉规则。它读取分支 `gradle.properties`：Forge 1.20.1 使用 pack_format **15**，NeoForge 1.21.1 使用 **48**。两份资料图由模组资源提供。

## 6. 版本、旧存档与重置

| Demo 规则版本 | 来源 | 既有运行行为 |
| --- | --- | --- |
| `collection-v1` | `LegacyCollectionFieldDemos` | 保留原 Discover／Research 执行与原奖励 |
| `collection-v2` | `FrozenCollectionFieldDemosV2` | 保留上一版统一调查的原目标、阶段、资料、奖励与主题 |
| `collection-v3` | 当前 `CollectionFieldDemos` | 新接取采用本文三条路线 |

`CollectionDemoDefinitionFactories` 注册三个版本，旧供应器不调用新版可变构建方法。没有版本戳的历史 Demo 明确映射 v1；不能猜成 v3。现代 Entry 的结构版本始终为 2。JSON 的运行保存原始作者文档，Java 运行保存不可变工厂引用，Tag 候选另随 Run 冻结；展示导出不是服务端可执行快照。

因此升级后还看见旧标题、旧目标或旧数值，先核对是否正在进行或查看历史 v1／v2 Run；它应继续原规则。不要用新文本替换旧运行，也不要把旧行动数直接转成新调查进度。要从头体验 v3，可在测试存档先对目标任务执行管理员 reset，再重新 give。

普通重复接取保留发现与成果，reset 则清目标任务的运行、终态、归档与关联 Entry 的永久记录及奖励资格。共享 Entry 的其他目录知识会一并受影响，其他任务自己的行动和本轮报酬不会被当成当前任务替代。reset 不删除背包物品，所以新的 POSSESSION 准备仍可能立即满足；新的 CRAFT 与 OFFER 调查必须按本轮规则完成。reset 使旧交付授权失效，不应靠旧完成锁存或迁移重新恢复进度。

已取得永久成果、已领取凭证和旧欠奖按兼容规则保留；旧研究仅按显式映射迁移，未完成的 2/5 不转换成新行动 2/3。新版奖励资格保存原载荷及原来源，历史欠奖不能用当前 Run 代领。背包不足时物品保留待交付，腾出空间后恢复，不丢到地上；任意自定义命令／外部回调的跨崩溃语义见使用说明。

## 7. 验收路线与本轮记录

以下是 v3 的体验验收路线；本轮自动测试的执行范围与结果见下表。上一轮记录仍保留在 [历史验收记录](compendium-quest-verification.md)。

1. 新存档分别打开三项任务，检查目录恰为 8／6／7 项，分类没有空壳；Java 与 JSON 逐项比较标题、文案、目标、资料、奖励、冷却和汇合。
2. 荒野手册首次击败僵尸只推进一次，领取发现煤炭后不能提前领取铁粒；完成三次击败与两份腐肉交付，分别检查成果铁粒与本次绿宝石。
3. 与牛互动并取得牛奶，交付前后检查库存确实少一只牛奶桶；首次 `dairy` 空桶奖领取一次，刷新、重登不能重复领取。
4. 接取前已有铁镐、骨粉和工作台不计 CRAFT；接取后真实制作铁镐、三份骨粉、工作台和火把，核对输出数量与成果，不把资料打开当作达成。
5. 补给仅交三种材料便达标，库存扣除正确，剩余候选停止；未选补给不用补齐。完成、放弃、重登不绕过 1200 tick 冷却，下一轮仍须新交付。
6. 营地准备可以使用库存装备且不消耗；准备完成后两个分支同时激活。本阶段前的制作不计入材料分支，一线完成不能提前进入交付；两线各二项后交木板十六份和火把八根，最终奖励钻石一颗。
7. 简短补给／踏勘不授予深入 Outcome。未知蜘蛛先显示线索，发现后头像及资料公开；物品与 Tag 的实际目标、JEI 和追踪对应所选 Binding。
8. 满背包领取、欠奖归档、重载、reset 再 give 按使用说明验证；v1／v2 旧运行仍读原工厂，v3 新运行不复用旧目标。

| 本轮检查 | Forge 1.20.1 | NeoForge 1.21.1 |
| --- | --- | --- |
| JSON 编译及 Java／JSON 等价测试 | 通过：三份真实 JSON 验证、编译与完整规则／资料／奖励比对 | 同样通过三份真实 JSON 与 Java 比对 |
| 完整构建与 JUnit | 通过：811 项，零失败／错误／跳过 | 通过：819 项，零失败／错误／跳过 |
| 服务端 GameTest | 通过：44 项，含三条 v3 Demo 完整路线 | 通过：45 项，含三条 v3 Demo 完整路线 |
| 原生任务面板／详情／追踪／JEI | 本轮未重复原生 GUI 审计，界面源码未改 | 本轮未重复原生 GUI 审计，界面源码未改 |

[CollectionDemoGameplayTest](../src/test/java/org/arcadia/arc_quest/quest/logic/CollectionDemoGameplayTest.java) 检查已发现后仍需真实行动、牛／骨成果与追踪结束、无多余条目／空分类，以及保存重载后的精确 v2 工厂。[UnifiedCollectionDemoPackTest](../src/test/java/org/arcadia/arc_quest/quest/spec/io/UnifiedCollectionDemoPackTest.java) 比对 Java 与 JSON 的编译结果，保留所有当前目标、数量、资料、奖励和阶段规则；只规范主题默认值、命名空间与不参与新玩法的旧迁移目标默认文案。

[UnifiedCollectionGameTests](../src/gameTest/java/org/arcadia/arc_quest/collection/UnifiedCollectionGameTests.java) 使用真实服务端能力与模拟玩家，覆盖手册八项调查及八个成果、原版挤奶与首次返桶防重复、六选三实际扣料与 1199／1200 tick 冷却边界、营地双线汇合及最终交付。OFFER 实际扣库存，奖励实际发放并检查重载；击杀与合成通过生产事件入口注入，不包含原版配方操作界面的手动点击。

可复现命令：`gradlew.bat test runGameTestServer build -PjeiServerAudit --offline --console=plain`，Forge 使用 JDK 17，NeoForge 使用 JDK 21。Forge 完整构建日志为 `build/collection-demo-redesign-final.log`，扩展为八项完整手册后的服务端日志为 `build/collection-demo-redesign-complete-handbook.log`；NeoForge 对应 `build/collection-demo-redesign-final.log`。生成器再次运行产物不变，Java／JSON 在两平台一致，pack_format 分别为 15／48。

测试材料可在专用验收存档准备；真实合成与互动仍需实际执行。确认阶段已经激活，再制造对应制作事件。

```mcfunction
/time set night
/summon minecraft:zombie
/summon minecraft:skeleton
/summon minecraft:spider
/summon minecraft:cow
/give @s minecraft:bucket 1
/give @s minecraft:iron_ingot 6
/give @s minecraft:stick 8
/give @s minecraft:bone 4
/give @s minecraft:oak_log 24
/give @s minecraft:coal 16
/give @s minecraft:arrow 2
/give @s minecraft:string 5
/give @s minecraft:wheat 8
```
