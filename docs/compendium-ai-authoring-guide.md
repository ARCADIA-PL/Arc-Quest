# ArcQ 图鉴任务：外部 AI 接入手册

本文件面向没有本项目聊天上下文的 AI 与任务作者。只阅读本文件即可编写、安装和验收一个现代图鉴任务；完整 Java 与 JSON 示例已内嵌，附带同内容的独立文件。

核对日期：2026-10-03。适用 ArcQ 当前 Forge 1.20.1／NeoForge 1.21.1 分支。

## 1. 先确定接入方式与版本

| 项目 | 当前规则 |
| --- | --- |
| Entry 玩法结构 | `gameplayVersion: 2`；每个新 JSON Entry 都显式填写 |
| Minecraft／Java | Forge 1.20.1 使用 JDK 17；NeoForge 1.21.1 使用 JDK 21 |
| 网络协议 | 20；客户端与服务端使用相同平台、兼容的 ArcQ 版本 |
| Java 任务规则版本 | 作者定义的稳定字符串，例如 `zombie-survey-v1`；与 Entry 的数字版本无关 |
| 内置 Demo 规则版本 | `collection-v3`；这是内置 Demo 的版本名，不是新任务应照抄的 Entry 版本 |
| 默认归属 | 每名玩家独立的永久档案与任务运行；没有默认队伍共享／助攻计数 |

希望制作任务内容，优先使用第 3 节的外置 JSON。已有附属模组、需要 Java 条件或回调，使用第 4 节。两份完整示例是替代入口，使用相同任务 ID，**选择一种注册来源**，避免同时加载两份定义。

新内容不调用 `.research()`、`.researchStep()`、`.researchReward()` 或 Entry 级 `.bindingReward()`，也不把每个条目做成一个 Phase。旧 Research API 仅为已有存档保留。

## 2. 正确的玩法模型

| 对象 | 保存什么 | 作者在哪里配置 |
| --- | --- | --- |
| Quest | 接取、完成、失败、重复、最终奖励 | `QuestBuilder`／JSON 根对象 |
| Phase | 当前阶段的行动目标、阶段转换 | `PhaseBuilder`／`phases` |
| CollectionSheet | 本阶段需完成哪些调查，全部或任选配额 | Phase 的 `collectionSheet` |
| Entry | 共享档案身份、主体、发现规则、资料、永久成果、首次奖励 | `CollectionEntryBuilder`／`collectionConfig.entries` |
| Discover | 已经发现这个 Entry 的永久事实 | Entry 的 `discover`／`discoveryObjectives` |
| Binding | 一项实际调查：引用本 Phase 的 Objectives，并可读取永久事实 | `EntryRequirementBuilder`／Sheet 的 `bindings` |
| Outcome | 调查完成后记入档案的永久事实，只有 ID 与名称，没有计数目标 | Entry 声明；Binding 显式产出 |
| Content | 文字和图片；阅读不会推进目标 | Entry 的 `content` |

例如：首次击败僵尸产生 Discover；当前任务要求击败 3 只并交付腐肉 2 份；这个 Binding 完成后产生 `anatomy` Outcome。没有额外的“永久研究再击败 5 只”。

### 必须理解的边界

- `.entity()`、`.item()`、`.itemTag()` 定义主体与自动图标，**不自动生成发现规则**。未配置 Discover 不会凭主体自动发现；普通站在生物旁边也不会产生发现。
- 同 Entry 配置多个发现 Objective 时，**任意一条达到自身门槛即可发现**。没有 `discoverMode` 字段，也不能把多条 Discover 当成 ALL 调查。
- Binding 的 `objectiveIds` 只引用其所在 Phase 的 Objective ID，不能直接引用 Entry 的发现目标、另一个 Phase 的目标或列表下标。
- 默认 `requirementMode: ALL` 合并检查本轮行动与记录前置。`ANY` 对两类要求一并取任意，因此已发现事实可能绕过尚未完成的行动。产出 Outcome 或支付 Binding 奖励的调查必须使用 ALL。
- 已发现或已取得成果，不自动填充新 Run 的击杀／合成／交付进度。读取既有事实与做本轮行动是不同条件。
- 同一真实事件可能推进多项同时激活且匹配的调查；不是一份事件只属于一个 Quest。新激活 Phase 不再消费刚刚触发其激活的同次事件。

## 3. JSON：安装一个完整可玩的任务

### 3.1 当前支持的安装路径

将下面的完整 JSON，或附带的 [zombie_survey.json](examples/collection-ai/zombie_survey.json)，保存到：

```text
<游戏实例或服务端工作目录>/
└─ arc_quest/
   └─ datapack/
      └─ quests/
         └─ ai_codex/
            └─ zombie_survey.json
```

客户端通常是含有 `mods`、`config` 的实例目录；独立服务器使用 JVM 的工作目录。也可用启动参数 `-Darcquest.datapack.dir=<绝对目录>` 改变 `datapack` 根目录，任务仍放在该根目录的 `quests` 子目录。

这里是 ArcQ 的外置内容目录，**不需要 `pack.mcmeta`**。重载后检查诊断，再接取：

```mcfunction
/arcquest_reload
/arcquest admin registry
/arcquest quest give @s ai_codex:zombie_survey
```

`/reload` 也能触发内容重载。重载发现阻断错误时拒绝整批更新，旧内容继续生效；不能看到任务仍在注册表中就假定新文件已经加载。管理员命令需要相应权限；`@s` 的接取／重置命令由玩家执行，控制台使用玩家名。

**普通存档数据包的当前限制：**扫描器也识别 `data/<namespace>/arc_quest/quests/*.json`，但当前重载实现没有为 ResourceManager 来源保存原始作者快照。现代图鉴任务可能注册成功，却在接取时返回 `COLLECTION_DEFINITION_UNAVAILABLE`。本手册的可玩入口使用上述外置目录。不要向用户承诺把现代图鉴 JSON 放进普通世界数据包即可完整运行，也不要通过伪造内部快照字段绕过检查。

### 3.2 完整 JSON

这一份文件包含 Quest、Entry、Phase、Sheet、Binding、发现、成果、图片和三种奖励来源，不需要其他 Entry 定义。

```json
{
  "id": "ai_codex:zombie_survey",
  "category": "arc_quest:collection",
  "mode": "COLLECTION",
  "displayName": {"mode": "literal", "value": "僵尸调查 · AI 接入范例"},
  "description": {"mode": "literal", "value": "亲手击败 3 只僵尸，再交付 2 份腐肉；完成后手动确认。"},
  "repeatable": false,
  "initialPhaseId": "survey",
  "completionPolicy": "ALL",
  "visualConfig": {"themeColor": 8767150},
  "collectionConfig": {
    "categories": [
      {"categoryId": "living", "displayName": {"mode": "literal", "value": "野外生物"}}
    ],
    "entries": [
      {
        "entryId": "ai_codex:codex/zombie",
        "categoryId": "living",
        "gameplayVersion": 2,
        "displayName": {"mode": "literal", "value": "僵尸"},
        "description": {"mode": "literal", "value": "记录夜间威胁与腐肉样本的用途。"},
        "subjectKind": "ENTITY",
        "subjectId": "minecraft:zombie",
        "visibilityMode": "VISIBLE_BY_DEFAULT",
        "hiddenPresentationMode": "FULLY_HIDDEN",
        "discoveryObjectives": [
          {
            "id": "first_defeat", "type": "arc_quest:kill",
            "targetId": "minecraft:zombie", "requiredCount": 1,
            "displayText": {"mode": "literal", "value": "首次亲手击败 1 只僵尸"}
          }
        ],
        "outcomes": [
          {"outcomeId": "anatomy", "displayName": {"mode": "literal", "value": "腐肉与行为记录"}}
        ],
        "relatedItems": ["minecraft:rotten_flesh", "minecraft:shield"],
        "content": [
          {
            "blockId": "overview",
            "text": {"mode": "literal", "value": "僵尸常在夜间或阴暗处出现；准备盾牌并保持退路。"},
            "reveal": "ALWAYS", "zoomable": false
          },
          {
            "blockId": "habitat",
            "media": {
              "type": "image", "texture": "arc_quest:textures/gui/collection/field_notes.png",
              "width": 240, "height": 120, "autoplay": false, "loop": false
            },
            "caption": {"mode": "literal", "value": "发现后公开的野外观察图。"},
            "fit": "CONTAIN", "zoomable": true, "reveal": "DISCOVERED"
          },
          {
            "blockId": "anatomy_notes",
            "text": {"mode": "literal", "value": "腐肉样本已归档。腐肉可以饲喂狼，也能与牧师村民交易。"},
            "reveal": "OUTCOME", "revealStepId": "anatomy", "zoomable": false
          }
        ],
        "rewards": [
          {
            "rewardId": "first_record", "trigger": "DISCOVERED",
            "grantMode": "MANUAL", "previewVisibility": "PUBLIC",
            "rewards": [{"type": "item", "itemId": "minecraft:coal", "count": 1}]
          },
          {
            "rewardId": "first_anatomy", "trigger": "OUTCOME", "outcomeId": "anatomy",
            "grantMode": "MANUAL", "previewVisibility": "PUBLIC",
            "rewards": [{"type": "item", "itemId": "minecraft:iron_nugget", "count": 3}]
          }
        ]
      }
    ]
  },
  "phases": [
    {
      "phaseId": "survey",
      "displayName": {"mode": "literal", "value": "夜间调查"},
      "description": {"mode": "literal", "value": "只统计当前阶段的击败，样本交付实际消耗库存。"},
      "autoAdvanceOnComplete": false,
      "objectives": [
        {
          "id": "defeats", "type": "arc_quest:kill",
          "targetId": "minecraft:zombie", "requiredCount": 3,
          "displayText": {"mode": "literal", "value": "亲手击败 3 只僵尸"}
        },
        {
          "id": "samples", "type": "arc_quest:offer",
          "targetId": "minecraft:rotten_flesh", "requiredCount": 2,
          "displayText": {"mode": "literal", "value": "交付 2 份腐肉样本（消耗）"}
        }
      ],
      "collectionSheet": {
        "completionPolicy": "ALL",
        "bindings": [
          {
            "bindingId": "zombie", "entryId": "ai_codex:codex/zombie",
            "objectiveIds": ["defeats", "samples"],
            "requirementMode": "ALL", "recordPolicy": "EXISTING_RECORDS",
            "recordRequirements": [{"type": "DISCOVERED"}],
            "outcomeIds": ["anatomy"],
            "rewards": [
              {
                "rewardId": "survey_payment", "trigger": "BINDING_COMPLETE",
                "grantMode": "MANUAL", "previewVisibility": "PUBLIC",
                "rewards": [{"type": "item", "itemId": "minecraft:emerald", "count": 1}]
              }
            ]
          }
        ]
      }
    }
  ],
  "completionRewards": [{"type": "item", "itemId": "minecraft:emerald", "count": 2}]
}
```

### 3.3 预期行为

1. 打开任务面板的「图鉴」分类，看到公开的僵尸身份与基础文字。此时仍可处于“尚未发现”；能读公开资料不代表目标完成。
2. 接取后亲手击败第一只僵尸：发现成立，击败目标推进一次，可手动领取首次煤炭 ×1，发现图片公开。
3. 完成 3 次击败与实际交付腐肉 ×2，顺序不限：Binding 达成，记录 `anatomy`，公开成果文字，可领首次铁粒 ×3 和本次调查绿宝石 ×1。
4. 手动确认最终阶段，任务完成并获得最终绿宝石 ×2。确认不替代前三项独立手动奖励的领取；未领合法奖励可按原来源保留。

主体、发现目标、本轮 Objective 的 ID 可以指向不同内容，但必须符合玩法意图。比如“铁锭档案”可用“制作铁镐”作为本轮调查；给 Entry 指定铁锭图标不会改变铁镐的真实制作目标。

## 4. Java：完整类与注册时机

前提是附属模组已正确依赖当前平台的 ArcQ。复制以下类，或 [ExternalAiCodexExample.java](examples/collection-ai/ExternalAiCodexExample.java)，放入源目录的 `ai_codex/integration/ExternalAiCodexExample.java`。示例通过 `ResourceLocation.tryParse` 同时兼容两个目标 Minecraft 版本，不依赖平台专属构造器。

```java
package ai_codex.integration;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.api.ArcQuestAPI;
import org.arcadia.arc_quest.quest.api.CollectionContentBlock;
import org.arcadia.arc_quest.quest.api.CollectionContentReveal;
import org.arcadia.arc_quest.quest.api.CollectionMediaFit;
import org.arcadia.arc_quest.quest.api.QuestCategory;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestMode;
import org.arcadia.arc_quest.quest.api.QuestText;
import org.arcadia.arc_quest.quest.builder.CollectionEntryBuilder;
import org.arcadia.arc_quest.quest.builder.CollectionQuestConfigBuilder;
import org.arcadia.arc_quest.quest.builder.CollectionSheetBuilder;
import org.arcadia.arc_quest.quest.builder.EntryRequirementBuilder;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.builder.PhaseBuilder;
import org.arcadia.arc_quest.quest.builder.QuestBuilder;
import org.arcadia.arc_quest.quest.data.CollectionRunDefinitionStore;
import org.arcadia.arc_quest.quest.reward.ItemReward;

/** Call register from ArcQuestRegistrationEvent.Quest on the mod event bus. */
public final class ExternalAiCodexExample {
    public static final ResourceLocation QUEST_ID = id("ai_codex:zombie_survey");
    public static final ResourceLocation ENTRY_ID = id("ai_codex:codex/zombie");
    public static final String RULE_VERSION = "zombie-survey-v1";
    private static boolean registered;

    private ExternalAiCodexExample() {}

    public static synchronized void register() {
        if (registered) return;
        CollectionRunDefinitionStore.registerCodeDefinitionFactory(
                QUEST_ID, RULE_VERSION, ExternalAiCodexExample::buildV1, true);
        ArcQuestAPI.registerQuest(buildV1());
        registered = true;
    }

    /** Preserve this method when introducing a later rule version. No registration side effects. */
    public static QuestDefinition buildV1() {
        var zombie = CollectionEntryBuilder.create(ENTRY_ID)
                .category("living").displayName("僵尸")
                .description("记录夜间威胁与腐肉样本的用途。")
                .entity(EntityType.ZOMBIE)
                .discover(ObjectiveBuilder.kill(EntityType.ZOMBIE, 1)
                        .id("first_defeat").display("首次亲手击败 1 只僵尸"))
                .outcome("anatomy", "腐肉与行为记录")
                .discoveryReward("first_record", new ItemReward(Items.COAL, 1))
                .outcomeReward("anatomy", "first_anatomy", new ItemReward(Items.IRON_NUGGET, 3))
                .relatedItem(Items.ROTTEN_FLESH).relatedItem(Items.SHIELD)
                .content(textBlock("overview", "僵尸常在夜间或阴暗处出现；准备盾牌并保持退路。",
                        CollectionContentReveal.ALWAYS, ""))
                .image("habitat", id("arc_quest:textures/gui/collection/field_notes.png"),
                        240, 120, "发现后公开的野外观察图。")
                .content(textBlock("anatomy_notes", "腐肉样本已归档。腐肉可以饲喂狼，也能与牧师村民交易。",
                        CollectionContentReveal.OUTCOME, "anatomy"))
                .build();

        return QuestBuilder.create(QUEST_ID)
                .category(QuestCategory.COLLECTION).mode(QuestMode.COLLECTION)
                .displayName("僵尸调查 · AI 接入范例")
                .description("亲手击败 3 只僵尸，再交付 2 份腐肉；完成后手动确认。")
                .themeColor(0x85C6AE)
                .collectionConfig(CollectionQuestConfigBuilder.create()
                        .category("living", "野外生物").entry(zombie).build())
                .phase(PhaseBuilder.create("survey").displayName("夜间调查")
                        .description("只统计当前阶段的击败，样本交付实际消耗库存。")
                        .objective(ObjectiveBuilder.kill(EntityType.ZOMBIE, 3)
                                .id("defeats").display("亲手击败 3 只僵尸"))
                        .objective(ObjectiveBuilder.offer(Items.ROTTEN_FLESH, 2)
                                .id("samples").display("交付 2 份腐肉样本（消耗）"))
                        .collectionSheet(CollectionSheetBuilder.create().all()
                                .binding(EntryRequirementBuilder.create("zombie", ENTRY_ID)
                                        .objectives("defeats", "samples")
                                        .recordOutcome("anatomy")
                                        .reward("survey_payment", new ItemReward(Items.EMERALD, 1))))
                        .autoAdvanceOnComplete(false))
                .reward(new ItemReward(Items.EMERALD, 2))
                .build();
    }

    private static CollectionContentBlock textBlock(String blockId, String text,
                                                    CollectionContentReveal reveal, String outcomeId) {
        return new CollectionContentBlock(blockId, QuestText.literal(text), null, QuestText.literal(""),
                CollectionMediaFit.CONTAIN, false, reveal, outcomeId);
    }

    // tryParse exists in both Minecraft 1.20.1 and 1.21.1.
    private static ResourceLocation id(String value) {
        return Objects.requireNonNull(ResourceLocation.tryParse(value), "Invalid resource ID: " + value);
    }
}
```

在附属模组构造阶段，把监听器接到**模组事件总线**，使用 ArcQ 的注册窗口：

```java
import org.arcadia.arc_quest.api.event.registry.ArcQuestRegistrationEvent;
import ai_codex.integration.ExternalAiCodexExample;

// modEventBus 是当前加载器提供给你这个模组的 MOD 事件总线。
modEventBus.addListener((ArcQuestRegistrationEvent.Quest event) ->
        ExternalAiCodexExample.register());
```

Forge 的事件总线包名为 `net.minecraftforge.eventbus.api`，NeoForge 为 `net.neoforged.bus.api`；不要给普通游戏事件总线注册这个内容监听器。ArcQ 在此事件后冻结 Quest 注册表，不能拖到玩家登录或每个世界加载后再注册。

### 4.1 Java 版本工厂是必要步骤

现代图鉴 Run 必须保留原规则，任意 Java 条件／回调无法由展示 JSON 复原。先调用 `CollectionRunDefinitionStore.registerCodeDefinitionFactory`，再注册 Quest。仅 `ArcQuestAPI.registerQuest(...)` 不足以保证可接取。

示例的 `buildV1()` 只构建定义，没有注册、授奖或修改玩家的副作用。规则升级时：

```java
CollectionRunDefinitionStore.registerCodeDefinitionFactory(
        QUEST_ID, "zombie-survey-v1", ExternalAiCodexExample::buildV1, false);
CollectionRunDefinitionStore.registerCodeDefinitionFactory(
        QUEST_ID, "zombie-survey-v2", ExternalAiCodexExample::buildV2, true);
ArcQuestAPI.registerQuest(ExternalAiCodexExample.buildV2());
```

这段是**替换原注册实现的升级示意**；`buildV2()` 要由作者实现，不能在原 `register()` 之后再重复注册同一个 Quest／版本。保留 V1 函数及其依赖的旧定义，不要让旧工厂转调已改成新版的方法。这里的字符串 `v2` 仍不意味着 JSON Entry 应改为 `gameplayVersion: 3`。

## 5. 作者字段与 API 速查

### 5.1 ID、分类与共享条目

| ID | 例子 | 唯一范围 |
| --- | --- | --- |
| Quest ID | `ai_codex:zombie_survey` | 全局，包含 namespace |
| Entry ID | `ai_codex:codex/zombie` | 全局永久档案身份 |
| Phase ID | `survey` | 当前 Quest |
| Objective ID | `defeats` | 当前 Phase；Binding 精确引用 |
| Binding ID | `zombie` | 当前 Phase；用于追踪、进度和本轮奖励身份 |
| Outcome ID | `anatomy` | 当前 Entry |
| Entry rewardId | `first_anatomy` | 当前 Entry 的全部永久奖励，跨触发器唯一 |
| Binding rewardId | `survey_payment` | 当前 Binding |
| categoryId | `living` | 当前图鉴分类定义 |
| blockId | `anatomy_notes` | 当前 Entry 的资料块 |

资源 ID 使用小写、有效 namespace/path；稳定业务 ID 发布后不要随意改名。`category: arc_quest:collection` 是任务列表分类；`mode: COLLECTION` 决定图鉴玩法／面板，二者分别填写。Entry 的 `categoryId` 属于图鉴内部分类，不是 QuestCategory。

跨 Quest 复用相同 Entry ID 时，完整定义必须一致，不要为了当前任务更改其资料、发现规则或奖励。Quest 内联 `.entry(entry)` 会随任务注册进入共享索引，通常无需单独注册。显式共享 API 是 `CollectionEntryRegistry.register(entry)`，没有 `ArcQuestAPI.registerCollectionEntry`。

JSON 的 `collectionConfig.entryIds` 只引用已注册的共享 Entry，不是另一个 JSON 文件的路径。独立交付内容优先内联 `entries`；同批重载不要依赖未经确认的文件注册先后顺序。每个任务只配置实际需要的 Entry 和非空分类。

目录使用的 Entry 中，每个声明的 Outcome 都必须有已注册调查中的 Binding 显式产出。Java 每注册一条 Quest 就重建共享索引，因此首次注册时来源必须完整；不要先注册只有成果声明的 Quest，再期望后续尚未注册的 Quest 补齐来源。入门任务像完整示例一样在同一个 Quest 中声明并产出成果。

### 5.2 Entry 的现代字段

| JSON 字段 | Java 对应／含义 |
| --- | --- |
| `gameplayVersion: 2` | 新 `CollectionEntryBuilder` 默认 v2；JSON 缺省为旧版 1 |
| `entryId`、`categoryId` | `create(id)`、`category(id)` |
| `displayName`、`description` | `displayName(...)`、`description(...)` |
| `subjectKind: ENTITY`、`subjectId` | `.entity(EntityType...)` |
| `subjectKind: ITEM`、`subjectId` | `.item(Items...)` |
| `subjectKind: ITEM`、`itemTag` | `.itemTag(tagId)`；不同时指定普通 `subjectId` |
| `subjectKind: CUSTOM` | `.subject(...)`；主体本身不生成自定义事件检测 |
| `discoveryObjectives` | `.discover(ObjectiveBuilder...)`；每条仍须稳定 `id` |
| `outcomes` | `.outcome(id, name)`；没有 requiredCount／研究目标 |
| `relatedItems` | `.relatedItem(...)`；供资料与 JEI 查询，不计算进度 |
| `recordConditions` | `.recordWhen(ICondition...)`；所有配置条件须满足，发现规则才计数／产生发现 |
| `visibilityMode`、`hiddenPresentationMode` | `.visibility(...)`，见第 7 节 |
| `publicClue` | `.publicClue(...)`；匿名占位时的公开线索 |
| `icon` | `.icon(...)`／`.iconItem(...)`／`.iconTexture(...)` |
| `content`、`rewards` | 资料和永久奖励；本轮报酬放在 Binding |
| `sortOrder` | `.sortOrder(n)` |

新 Entry 不填 `researchObjectives`、`researchAfterDiscovery` 或旧奖励触发器 `RESEARCH_COMPLETE`。`legacyResearchObjectives`、`legacyResearchOutcomeMappings` 是迁移证据，只在明确处理旧存档时使用，不是新任务的隐藏第二套进度。

`recordWhen(...)` 控制发现规则，成果结算不会再次检查这些条件。限制调查或成果的取得时，应配置 Quest／Phase 门槛或实际 Binding 行动要求，不能只依赖 Entry 的 `recordConditions`。

### 5.3 本轮 Objective 选型

| 玩家行为 | Java | JSON 关键字段 | 实际语义 |
| --- | --- | --- | --- |
| 亲手击败 | `kill(EntityType.ZOMBIE,n)` | `type: arc_quest:kill`、实体 `targetId` | 服务端支持的玩家击杀事件 |
| 与实体互动 | `interact(entityId)` | `type: arc_quest:interact`、实体 `targetId` | 右键互动；不等价于靠近或观察 |
| 新合成 | `craft(Items.BONE_MEAL,n)` | `type: arc_quest:craft`、产出物品 `targetId` | 计产出物品数量，不是点击次数 |
| 当前持有 | `possess(item,n)` | `type: arc_quest:collect`、`collectMode: POSSESSION` | 读取主背包及鼠标携带；不消耗，不读取箱子或地上物品 |
| 当前持有任意 Tag 成员 | `possessTag(tagId,n)` | 同上，另填 `itemTag` | 任意候选数量合计 |
| 消耗交付 | `offer(item,n)` | `type: arc_quest:offer`、物品 `targetId` | 提交入口真实扣库存，不因持有自动完成 |
| 消耗 Tag 样本 | `offerTag(tagId,n)` | `type: arc_quest:offer`、`targetId` 与 `itemTag` 都是 Tag ID | 可混用成员，合计到门槛 |
| 仅制作来源的 COLLECT | `collect(item,n).collectMode(CollectMode.CRAFTED_ONLY)` | `type: arc_quest:collect`、`collectMode: CRAFTED_ONLY` | 当前支持的合成事件；不要当作所有机器／冶炼来源 |
| 兼容累计获得 | `collect(item,n)` | `type: arc_quest:collect`、`collectMode: LEGACY_ACQUISITION` | 包括库存增加等旧来源，不能证明物品确实新生产 |

Java `craft(item,n)` 是具体产出 ID 的便捷方法，没有 `craftTag(...)` 便捷方法。JSON 的 CRAFT 也可通过 `itemTag` 使用现有 Tag 匹配；`CRAFTED_ONLY` 的 COLLECT 同样支持 Tag。两者仍只读当前支持的合成事件，不是任意机器产出 API。熔炉／模组机器不应在文案中承诺会自动完成 CRAFT，除非已接入并验证对应事件。

POSSESSION 在未完成时可下降，调查达成后会锁存，不因为随后放下装备撤销已完成 Binding。周期检查不是逐帧渲染扫描。COLLECT 的丢弃重捡不能用于证明新的生产成果；有奖励的收集调查优先选择 CRAFT 或实际消耗的 OFFER。

Tag ID 写成 `minecraft:logs`，不带 `#`；示例：

```json
{
  "id": "wood_samples", "type": "arc_quest:offer",
  "targetId": "minecraft:logs", "itemTag": "minecraft:logs", "requiredCount": 8,
  "displayText": {"mode": "literal", "value": "交付任意原木共 8 份（可混用，消耗）"}
}
```

Tag 图标轮换表示可选成员，不表示每种成员分别集齐。接取时冻结当前 Run 的候选集合，重载加入的新成员供新 Run 使用；空冻结集合不会退回实时 Tag。不要写作者字段 `frozenTagMembers`、定义哈希或服务端内部授权数据。

### 5.4 Binding 与记录前置

| 字段／方法 | 用法 |
| --- | --- |
| `objectiveIds`／`.objective()`、`.objectives()` | 引用所在 Phase 的稳定 Objective ID |
| `recordRequirements: [{"type":"DISCOVERED"}]`／`.discovered()` | 检查该 Entry 已发现 |
| `{"type":"OUTCOME","stepId":"anatomy"}`／`.requiresOutcome("anatomy")` | 读取已取得的档案成果；不会产出新成果 |
| `outcomeIds: ["anatomy"]`／`.recordOutcome("anatomy")` | 当前 Binding 完成后登记成果 |
| `requirementMode: ALL`／`.requirementMode(...)` | 合并全部非 optional 要求；一般保持 ALL |
| `recordPolicy: EXISTING_RECORDS` | 默认接受既有永久事实，本轮行动仍需重新完成 |
| `recordPolicy: NEW_DISCOVERIES` | 仅接受整个 Quest 接取时尚未发现的 Entry；只允许 DISCOVERED 记录前置 |
| `optional: true`／`.optional()` | 不计 Sheet 必需分母，不保证达标后还能补做 |

没有 `NEW_THIS_RUN` 策略。`NEW_DISCOVERIES` 的基线在**接取整个 Quest**时冻结，不是阶段开始时重采样；已发现条目不会因再次击败变为新发现。数量不足时任务可能被拒绝接取。重复经济委托不宜依赖不断寻找新物种。

Java `.recordOutcome(...)` 自动增加 `.discovered()`；JSON 必须像完整示例那样自己填写 DISCOVERED 前置。产出 Outcome 必须有至少一个非 optional 本轮 Objective，使用 ALL，且成果已在 Entry 声明。不能要求同一个 Binding 自己将要产出的成果，也不能配置成果来源依赖环。

有 Binding 奖励也必须具备非 optional 本轮 Objective 与 ALL；纯 `.discovered().reward(...)` 不能作为合法本轮报酬。v2 不使用记录类型 `RESEARCH_COMPLETE`／`RESEARCH_STEP`。

### 5.5 Sheet 的 ALL 与 QUOTA

```java
CollectionSheetBuilder.create().all();
CollectionSheetBuilder.create().quota(3);
```

对应 JSON 片段分别为：

```json
{"completionPolicy": "ALL", "bindings": []}
```

```json
{"completionPolicy": "QUOTA", "requiredCount": 3, "countDistinctEntries": false, "bindings": []}
```

上面仅示意字段，**实际文件必须填入非空 bindings**，且至少有一个必需 Binding。ALL 不填写 `requiredCount` 或填 0；QUOTA 的门槛在 1 与必需候选数之间。不要把 Sheet 的 QUOTA 写到 Quest 根级 `completionPolicy`，两者判断不同层级。

默认按 Binding 数计配额。`countDistinctEntries: true`／`.countDistinctEntries(true)` 按 Entry 分组：同 Entry 有多个必需 Binding 时，该组都完成才计该 Entry 一次。达标后固定本轮结果并停止余项计数／交付。

现代任务使用真实 Phase 生命周期；不配置旧 `collectionConfig.completionRules` 来代替它。分类／任务里程碑奖励不是另一套主线行动进度，进阶范例见内置手册。

## 6. 奖励、重复任务与存档

### 6.1 三种奖励来源

| 需求 | Java 配置位置 | JSON 位置与 trigger | 次数 |
| --- | --- | --- | --- |
| 首次发现奖励 | Entry `.discoveryReward(id,reward)` | Entry `rewards`，`DISCOVERED` | 每玩家／Entry／rewardId 一次，reset 可清资格 |
| 首次档案成果奖励 | Entry `.outcomeReward(outcomeId,id,reward)` | Entry `rewards`，`OUTCOME`，另填 `outcomeId` | 同样是永久 rewardId 身份，不随普通重接重复 |
| 本轮调查报酬 | Binding `.reward(id,reward)` | Binding `rewards`，`BINDING_COMPLETE` | 玩家／Quest／Run／Phase／Binding／rewardId 一次 |
| 最终任务回报 | Quest `.reward(reward)` | 根 `completionRewards` | 正常任务完成时执行 |

不要把 Binding 报酬放入 Entry 奖励列表。Outcome 本身是永久布尔事实；永久奖励实际领取身份不额外用 Outcome ID 分隔，因此同 Entry 的 rewardId 必须跨触发器保持唯一。

便捷 Java Builder 方法默认 `MANUAL` 与 `PUBLIC`。**JSON 的 `previewVisibility` 缺省是 `UNLOCKED_ONLY`**，希望公开预告就显式填 `PUBLIC`；直接构造部分 `CollectionEntryRewardDefinition` 重载也可能使用 UNLOCKED_ONLY，不可把所有入口都当作同一默认值。

`grantMode: MANUAL` 表示出现合法领取资格后由玩家领取；`AUTO` 表示达成时尝试自动交付。永久 AUTO 并非登录时为既有事实补发新加奖励。预告 PUBLIC 不代表有领取资格。满背包时可能已领取但仍显示待交付，腾出空位后恢复；不要为此再次 `grant` 或向地面补丢同一份物品。

物品奖励载荷使用 `{"type":"item","itemId":"minecraft:emerald","count":1}`。其他命令／变量／自定义回调须使用已存在的 Reward 类型并验证存档恢复；任意外部副作用不能通用承诺跨崩溃严格一次，不自动重放不明确失败的回调。

### 6.2 可重复委托

Java 在 Quest 上调用 `.repeatable()`；JSON 根写 `"repeatable": true`。冷却位于 `collectionConfig.repeatCooldownTicks`，Java 为 `CollectionQuestConfigBuilder.repeatCooldownTicks(n)`。

冷却从接取时开始按服务器游戏 tick 计，1200 tick 在 20 TPS 下约 60 秒；放弃、完成、重登不清除，停服不推进。管理员 reset 可清冷却。普通重接保留永久发现与成果，只重新开始 Run 的行动及交付。

经济委托可采用实际消耗的 OFFER／DELIVER，或明确冷却与已验证履约条件。反复读取旧知识、普通互动、持有或易逆转合成不能成为无成本无限奖励来源；系统会检查已知免费达标路径和重复成本配置。

### 6.3 升级、热重载与 reset

当前 Run 保存原定义及 Tag。修改 JSON 并重载不把正在进行的任务改成新目标；新接取使用新定义。Java 使用稳定不可变工厂执行旧 Run，不用新的展示导出替代规则。

在**测试存档**从零体验新定义：

```mcfunction
/arcquest quest reset @s ai_codex:zombie_survey
/arcquest quest give @s ai_codex:zombie_survey
```

reset 清当前任务活跃／完成／失败／归档数据、关联共享 Entry 的永久知识、永久奖励资格和旧授权。其他任务引用同 Entry 时也会看见知识被清除，但其独立本轮行动／报酬不会自动改成当前任务。reset 不删除玩家物品，POSSESSION 可以立即满足；旧库存导入、旧完成锁存和旧迁移不会悄悄回填已重置事实，真实新行为仍有效。

非重复任务放弃进入失败终态，不能把放弃当作普通重新接取入口；需要管理员显式 reset。普通重接、迁移和管理员重置不能混为一谈。

## 7. 资料、隐藏线索、图标与玩家交互

### 7.1 资料公开和图片

`content.reveal` 的现代值是 `ALWAYS`、`DISCOVERED`、`OUTCOME`。ALWAYS 仍受 Entry 整体公开权限约束，隐藏且未发现条目不能借它泄露档案。OUTCOME 必须填 `revealStepId`，值是已声明成果 ID；这个字段沿用旧名字，不能改为 `revealOutcomeId`。

Java `.text()`、`.image()` 默认在发现后公开。需要 ALWAYS 或 OUTCOME 时用完整 `CollectionContentBlock` 构造器，参照 Java 示例。图片媒体目前支持 Guide 的 IMAGE／NONE，不提供视频或 Ponder 模型预览。

图片文件例如 `assets/my_pack/textures/codex/zombie.png`，资源 ID 为 `my_pack:textures/codex/zombie.png`。资源必须由模组或客户端资源包提供，放在服务器的 `data` 或外置 `quests` 目录不会自动成为客户端贴图。

`media.width/height` 是正整数；`fit` 仅 `CONTAIN`／`COVER`；`zoomable` 控制放大。完整示例复用 ArcQ 已有 240×120 图，不要求额外贴图。

文字统一使用 QuestText 对象，例如 `{"mode":"literal","value":"僵尸"}` 或 `{"mode":"translatable","value":"my_pack.codex.zombie"}`；翻译键需在客户端语言资源中存在。别把裸字符串或未知翻译键当作已验证显示结果。

### 7.2 匿名线索

```java
.visibility(VisibilityMode.HIDDEN_BY_DEFAULT, HiddenPresentationMode.PLACEHOLDER)
.publicClue("夜间寻找会攀爬墙面的八足生物，击败一只并带回线样本。")
```

JSON 写 `visibilityMode: HIDDEN_BY_DEFAULT`、`hiddenPresentationMode: PLACEHOLDER` 和 QuestText 格式 `publicClue`。发现前可追踪匿名线索，服务器不下发真实实体、头像、秘密目标与奖励载荷。`FULLY_HIDDEN` 不提供匿名追踪入口。此示意需导入 `org.arcadia.arc_quest.quest.api.VisibilityMode`、`HiddenPresentationMode`。

### 7.3 图标与 JEI

默认 AUTO：物品显示物品栏图标，Tag 轮换候选，原版生物使用已适配的二维头像。没有规则的模组实体可能留空；不能保证任意生物展开贴图都能自动定位脸部，也不改用 3D 模型预览。作者可直接指定准备好的头像纹理或裁切区。

| 需求 | Java 片段 | JSON `icon` |
| --- | --- | --- |
| 自动 | `.icon(ObjectiveIcons.auto())` 或省略 | 省略／`null` |
| 无图标 | `.icon(ObjectiveIcons.none())` | `{"type":"arc_quest:none"}` |
| 指定物品 | `.iconItem(Items.IRON_INGOT)` | `{"type":"arc_quest:item","item":"minecraft:iron_ingot"}` |
| 纹理 | `.iconTexture(textureId)` | `{"type":"arc_quest:texture","texture":"my_pack:textures/codex/face.png"}` |
| 纹理裁切 | `.icon(ObjectiveIcons.texture(textureId).region(8,8,8,8))` | 同 TEXTURE，另加 `"region":{"x":8,"y":8,"width":8,"height":8}` |

`ObjectiveIcons` 包名为 `org.arcadia.arc_quest.quest.api.icon`。坐标是实际图片像素，须在图内。Entry 与 ObjectiveBuilder 均可配置图标；`.iconTexture(Items.IRON_INGOT)` 的 ItemLike 重载就是指定物品栏图标，并不查找贴图文件。JSON 字段是 `type`，不是 `mode`；ITEM 的资源字段是 `item`，不是 `itemId`。

有效的显式 iconItem 可查询对应物品 JEI，即使 Objective 本身不涉及物品。图标只改变展示／查询对象，不改变真实目标匹配。头像与纯纹理没有物品配方查询。

已有交互由系统提供：物品左键配方、右键用途；物品 Tooltip 复用任务奖励栏，不添加 JEI 快捷键说明；同 Entry 多调查合并卡片，详情选择 Binding；追踪以条目／具体调查为单位；收藏和搜索、半透明次级档案与图片放大都不写进度。任务作者不需要新增 Screen、自行渲染 Tooltip 或在 hover 时授奖。

## 8. 多阶段与真正的并行汇合

顺序阶段可调用 `.thenGoTo("next")`。同时开启两条路线要配置**两条独立转换**：

```java
// preparation 阶段尾部：
.thenGoTo("wildlife").thenGoTo("materials")

// report 阶段配置；questIdText 是当前任务 ID 的字符串：
.enterWhen(ICondition.phaseCompleteCurrentRun(questIdText, "wildlife")
        .and(ICondition.phaseCompleteCurrentRun(questIdText, "materials")))
```

JSON 在 preparation 的 `transitions` 中写：

```json
[
  {"targetPhaseId": "wildlife"},
  {"targetPhaseId": "materials"}
]
```

在 report 的 `enterCondition` 中写：

```json
{
  "condition": "arc_quest:and",
  "conditions": [
    {"condition": "arc_quest:quest_phase_completed_current_run", "questId": "ai_codex:camp", "phaseId": "wildlife"},
    {"condition": "arc_quest:quest_phase_completed_current_run", "questId": "ai_codex:camp", "phaseId": "materials"}
  ]
}
```

这是需要补齐相应 Phase 与 Binding 的片段，`ai_codex:camp` 必须替换成实际 Quest ID。单条转换的多个 `targetPhaseIds`，包括 Java `.thenGoTo("wildlife", "materials")`，表示随机选一条路线，不是同时激活。根级 `initialPhaseIds` 同样是随机初始候选；入门保持单个 `initialPhaseId`，通过独立转换实现并行。

汇合用 `phaseCompleteCurrentRun`，不使用永久全局 flag 代替本轮状态。最终阶段需要手动确认时写 `.autoAdvanceOnComplete(false)`／`autoAdvanceOnComplete: false`。普通阶段默认自动推进；阶段完成与手动推进是不同状态。

完整进阶路线可阅读 [三套内置 Demo](compendium-quest-demos.md)：八项手册、六选三补给与双线营地。它们的 JSON 也可复制到本手册的外置 `quests` 目录；不要据普通数据包目录形状假定现代原稿快照问题已经修复。

## 9. 验证与故障定位

### 9.1 有源码时的真实验证入口

没有独立的 `validate quest` 命令，也不假设存在某个 JSON Schema CLI。解析、校验、编译入口为：

```java
import java.nio.file.Files;
import java.nio.file.Path;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonReader;
import org.arcadia.arc_quest.quest.spec.validate.QuestSpecValidator;
import org.arcadia.arc_quest.quest.spec.compile.QuestSpecCompiler;

var spec = QuestSpecJsonReader.read(Files.readString(Path.of("zombie_survey.json")));
var report = new QuestSpecValidator().validate(spec);
report.getIssues().forEach(issue -> System.err.println(
        issue.severity + " " + issue.path + ": " + issue.message));
if (report.hasErrors()) throw new IllegalArgumentException("Quest definition validation failed");
var definition = new QuestSpecCompiler().compile(spec);
```

这些调用需要 ArcQ／Minecraft 的已初始化运行环境；不能把它们当作没有模组类路径的普通 `java` 文件脚本。字段校验通过后还要编译、确认引用／注册规则，以及验证接取和实际事件。

本仓库的常用回归命令：

```text
gradlew.bat test --tests "*UnifiedCollectionDemoPackTest" --tests "*CollectionDemoGameplayTest" -PjeiServerAudit --offline --console=plain
gradlew.bat test runGameTestServer build -PjeiServerAudit --offline --console=plain
```

`--offline` 只适用于依赖已缓存环境，首次配置需按项目网络与依赖设置下载。`-PjeiServerAudit` 是本仓库的专用服务端验证配置，不是附属模组必须提供的参数。不要因为现有 Demo 测试通过就声称外部新任务已经验收。

### 9.2 玩家验收顺序

1. 在独立测试存档安装／注册，完成重载并查看错误，确认任务 ID 真正在注册表中且可成功接取。
2. 打开目录，检查条目数、非空分类、名称、目标文案、默认头像／Tag、未发现状态与公开／隐藏资料。
3. 按真实事件执行最小范例：第一只击杀只推进一次；只击杀或只交付不能提前完成 ALL 调查；交付确实少两份腐肉。
4. 检查首次发现、成果首次奖、本次调查奖与最终回报分别有正确资格、数量和领取来源；重复点击不重复发放。
5. 如果使用制作目标，确认“接取前已有成品”“指令给成品”“丢弃重捡”不替代新制作；按合成输出数量检测。
6. 如果使用 QUOTA，达到配额后停止余项，额外交付不再扣物品；如果使用并行，只完成一线不能提前进入 report。
7. 保存重登验证原 Run；正常重接保留知识但行动归零；管理员 reset 后知识和资格按关联范围清除。
8. 验证详情、追踪、JEI、匿名线索和图片资源；满背包奖励应显示待交付，腾出空位后恢复。

### 9.3 常见错误

| 现象／诊断 | 核对与处理 |
| --- | --- |
| `COLLECTION_DEFINITION_UNAVAILABLE` | Java 是否先注册版本工厂；JSON 是否来自保存原稿的外置路径；旧 factory 是否保留 |
| `UNLOCK_CONDITION_NOT_MET` | 真正的 Quest 解锁条件与玩家事实，不能仅看目录可见 |
| `COLLECTION_NEW_DISCOVERIES_UNAVAILABLE` | NEW_DISCOVERIES 初始候选是否足够；已发现对象不能重新变“新发现” |
| `COLLECTION_REPEAT_COOLDOWN` | 接取后经过的游戏 tick；放弃和重登不清冷却 |
| `ALREADY_COMPLETED_NOT_REPEATABLE` | 一次性任务已完成；测试时显式 reset，勿伪造重新接取 |
| 只打开一条“并行”路线 | 是否误用了单条多 target 随机转换 |
| 已发现却没有成果 | 实际 Binding 的本轮要求是否全部达成；是否显式产出声明过的 outcomeIds |
| 源码 `.recordOutcome` 可用，JSON 编译失败 | JSON 是否自己写 DISCOVERED 前置、ALL 与非 optional Objective |
| 一发现就完成本轮行动 | 是否使用 ANY 令已发现事实满足整项；是否把发现目标直接当成本轮目标 |
| 无法追踪 | 所选 Binding 是否仍有未完成行动／公开线索；不是只有已满足永久事实 |
| 奖励预告消失 | JSON 是否缺少 `previewVisibility: PUBLIC`；隐藏 Entry 的服务器披露规则是否生效 |
| 材料不匹配 | targetId 是否为真实产物／物品；Tag 同时填写 itemTag、无 `#`，当前 Run 候选已冻结 |
| 模组生物没有头像 | 没有通用脸部 UV 推断，配置作者纹理／裁切或已有客户端适配，不改成模型预览 |
| 图片／翻译键没有显示 | 客户端资源是否存在，ResourceLocation 与尺寸是否正确，隐藏资料是否已获公开资格 |
| 重载后仍是旧数值 | 当前 Run 保留原规则，或重载整批被错误拒绝；检查诊断，新定义用新的测试 Run 验证 |

## 10. 给外部 AI 的交付模板

可把本文件连同以下任务要求一起交给另一个 AI：

```text
请依据《ArcQ 图鉴任务：外部 AI 接入手册》实现我的任务需求。
先给出 Entry／发现／本轮 Objectives／Binding／Outcome／奖励来源的映射。
新内容使用 gameplayVersion 2，把行动目标放在 Phase，Binding 引用稳定 Objective ID。
JSON 采用文档中已支持的外置目录并内联所需 Entry；Java 保留不可变版本工厂，监听正确的注册事件。
明确物品是否消耗、是否允许旧库存、合成产出数量、ALL／QUOTA、重复冷却与并行汇合。
为资料定义公开条件，为模组头像和图片提供实际客户端资源或清楚标注资源待提供。
只使用已存在的 API、字段、枚举与条件 ID；不要新增旧 Research 计数，不伪造任务完成或奖励资格。
交付完整文件与路径、注册／安装步骤、接取命令、预期行为和实际执行的验证结果。
示意片段必须标明不是完整文件；未执行的验证只能列为待验，不能宣称已经通过。
我的目标平台、任务内容、实体／物品／Tag、奖励和资源如下：……
```

有仓库访问时，可继续查阅 `CollectionEntryBuilder`、`EntryRequirementBuilder`、`CollectionSheetBuilder`、`CollectionQuestConfigBuilder`、`QuestSpecValidator` 与 `CollectionRunDefinitionStore` 的当前实现。历史设计文档不是新玩法 API 的优先依据；具体目标平台升级后，应重新核对版本与接口。

## 11. 本手册示例的验证范围

2026-10-03，完整 Java 类与独立 JSON 在 Forge 1.20.1／JDK 17、NeoForge 1.21.1／JDK 21 两端各通过 3 项临时检查：

1. 编译 Java 类；JSON 解析、字段校验、编译；两份定义的行动、资料和奖励规则完整比对，仅统一未指定的阶段主题色表示。
2. Java 版本工厂冻结定义并保存／恢复，保持目标数量及成果来源。
3. JSON 按外置加载器保留作者原稿的方式注册，不依赖 Java 版本工厂，能够冻结并保存／恢复。

Markdown 中完整 Java／JSON 与独立文件逐字一致，6 个 JSON 代码块均能解析，代码围栏与本地链接检查通过。验证使用当前仓库初始化后的测试环境，临时检查文件不属于交付 API。

这轮没有启动原生客户端或实际服务器进行玩家游玩验收，也没有通过真实 `/reload` 安装任务；第 3 项覆盖原稿注册与存档领域流程，不能当作完整加载器验收。接入方应继续执行第 9.2 节的玩家验收步骤。普通世界数据包的限制仍按第 3.1 节处理。
