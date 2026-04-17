# 对话系统详解

**模块**: dialogue/  
**最后更新**: 2026-04-17  
**版本**: v2.0（新增生命周期管理、冷却状态检查）

---

## 📋 目录

1. [对话树设计](#对话树设计)
2. [生命周期管理](#生命周期管理) ⭐ **新增**
3. [NPC交互流程](#npc交互流程)
4. [动作执行机制](#动作执行机制)
5. [上下文变量替换](#上下文变量替换)
6. [条件系统](#条件系统) ⭐ **增强**
7. [动态文本与预设动作](#动态文本与预设动作) ⭐ **新增**
8. [实战示例](#实战示例)

---

## 对话树设计

### 基本结构

```
DialogueTree (对话树)
├── dialogueId: "villager_greeting"
├── defaultNpc: "村民"
├── startNodeId: "start"
├── nodes: Map<String, DialogueNode>
│   ├── "start" → DialogueNode
│   │   ├── speaker: "村民"
│   │   ├── text: "你好，冒险者！"
│   │   ├── choices: List<DialogueChoice>
│   │   │   ├── choice[0]: "接受任务" → goTo("accept")
│   │   │   └── choice[1]: "离开" → close()
│   │   └── autoNextId: null
│   └── "accept" → DialogueNode
│       ├── speaker: "村民"
│       ├── text: "谢谢你！"
│       ├── choices: []
│       └── autoNextId: null
└── visualConfig: QuestVisualConfig
```

---

### 节点类型

#### 1. 选择节点（有选项）

**特征**: `choices.size() > 0`

**示例**:
```java
.node("start")
    .say("你需要帮助吗？")
    .choice("是的", c -> c.goTo("help"))
    .choice("不需要", c -> c.close())
```

**行为**:
- 客户端显示选项列表
- 等待玩家选择
- 根据选择跳转到不同节点或关闭对话

---

#### 2. 自动推进节点（无选项）

**特征**: `choices.isEmpty() && autoNextId != null`

**示例**:
```java
.node("intro")
    .say("让我给你讲个故事...")
    .autoNext("story_part2")
```

**行为**:
- 显示文本后自动跳转
- 可配置延迟时间（默认1.5秒）
- 适合连续叙述

---

#### 3. 终止节点

**特征**: `choices.isEmpty() && autoNextId == null`

**示例**:
```java
.node("farewell")
    .say("再见！")
```

**行为**:
- 显示文本后自动关闭对话
- 无需额外配置

---

## 生命周期管理

### 三层生命周期配置

对话系统支持在三个层级配置可重复性和冷却时间：

| 层级 | 配置项 | 作用范围 | 数据存储 |
|------|--------|----------|----------|
| **对话树** | `repeatable`, `cooldownSeconds` | 整个对话的启动 | `dialogueHistory` |
| **节点** | `repeatable`, `cooldownSeconds` | 节点的访问 | `nodeVisitHistory` |
| **选项** | `repeatable`, `cooldownSeconds` | 选项的选择 | `choiceSelectionHistory` |

---

### 1. 对话树级别

#### 一次性对话（默认）

```java
DialogueTreeBuilder.create("one_time_intro")
    .npc("神秘老人")
    .repeatable(false)  // 玩家只能进行一次此对话
    
    .node("start")
        .say("我是时间的守护者...")
        .choice("聆听", c -> c.goTo("story"))
    
    .buildAndRegister();
```

**行为**:
- ✅ 玩家首次对话正常进行
- ❌ 第二次尝试对话时被阻止
- 💾 记录到 `dialogueHistory`

---

#### 带冷却的可重复对话

```java
DialogueTreeBuilder.create("daily_greeting")
    .npc("村民")
    .repeatable(true)
    .cooldown(86400)  // 24小时冷却
    
    .node("start")
        .say("早上好！这是今天的奖励。")
        .choice("谢谢", c -> c.giveItem("minecraft:bread", 5).close())
    
    .buildAndRegister();
```

**行为**:
- ✅ 玩家每天可与 NPC 对话一次
- ⏰ 冷却期间尝试对话被阻止
- 💾 记录最后对话时间戳

---

### 2. 节点级别

#### 一次性节点

```java
.node("backstory")
    .say("让我告诉你我的过去...")
    .repeatable(false)  // 此节点只能访问一次
    .choice("明白了", c -> c.goTo("next"))
```

**行为**:
- ✅ 首次访问正常显示
- ❌ 再次到达此节点时被跳过或报错
- 💾 记录到 `nodeVisitHistory`

---

#### 带冷却的节点

```java
.node("daily_hint")
    .say("今天的提示是：去北边的山洞看看。")
    .repeatable(true)
    .cooldown(3600)  // 1小时冷却
    .choice("好的", c -> c.close())
```

**行为**:
- ✅ 每小时可提供一次提示
- ⏰ 冷却期间无法再次访问
- 💾 记录最后访问时间戳

---

### 3. 选项级别

#### 一次性选项

``java
.node("reward_choice")
    .say("选择一个奖励：")
    .choice("金币 x100（仅限一次）", c -> c
        .giveItem("minecraft:gold_ingot", 100)
        .repeatable(false)  // 此选项只能选择一次
        .close())
    .choice("经验瓶 x10（仅限一次）\", c -> c
        .giveXp(100)
        .repeatable(false)
        .close())
```

**行为**:
- ✅ 每个选项只能选择一次
- 👁️ 已选择的选项自动隐藏（需配合条件）
- 💾 记录到 `choiceSelectionHistory`

---

#### 带冷却的选项

```java
.node("shop")
    .say("今日特惠：")
    .choice("购买药水（1小时冷却）", c -> c
        .giveItem("minecraft:potion\", 1)
        .repeatable(true)
        .cooldown(3600)
        .close())
```

**行为**:
- ✅ 每小时可购买一次
- ⏰ 冷却期间选项不可见或禁用
- 💾 记录最后选择时间戳

---

### 配置冲突检测

系统会在注册时自动检测不合理的配置组合：

#### ❌ 冲突1：一次性对话 + 节点冷却

``java
DialogueTreeBuilder.create("invalid_dialogue")
    .repeatable(false)  // 一次性对话
    .node("start")
        .cooldown(3600)  // ❌ 报错：Cooldown is meaningless for one-time dialogue tree
```

**错误信息**:
```
WARN [DialogueRegistry] Dialogue 'invalid_dialogue' has validation errors:
WARN   - Node 'start' has cooldownSeconds=3600 but dialogue tree is one-time (repeatable=false). Cooldown is meaningless.
```

---

#### ❌ 冲突2：一次性节点 + 节点冷却

``java
.node("intro")
    .repeatable(false)  // 一次性节点
    .cooldown(3600)     // ❌ 报错：Cooldown is meaningless for one-time nodes
```

---

#### ❌ 冲突3：一次性选项 + 选项冷却

``java
.choice("Accept Reward", c -> c.giveItem("...").close())
    .repeatable(false)  // 一次性选项
    .cooldown(3600)     // ❌ 报错：Cooldown is meaningless for one-time choices
```

---

### 数据持久化

所有历史记录存储在玩家的 `IQuestCapability` 中，完全独立：

```java
// 每个玩家独立的 HashMap
private final Map<String, Long> dialogueHistory = new HashMap<>();      // dialogueId -> timestamp
private final Map<String, Long> nodeVisitHistory = new HashMap<>();     // nodeId -> timestamp
private final Map<String, Long> choiceSelectionHistory = new HashMap<>(); // choiceKey -> timestamp
```

**特性**:
- ✅ 玩家退出游戏后数据保留
- ✅ 服务器重启后数据保留
- ✅ 跨维度有效
- ✅ 玩家之间互不影响

**NBT 存储**:
```
playerdata/<UUID>.dat
└── ArcQuest
    ├── DialogueHistory
    │   ├── "epic_village_elder": 1712345678901
    │   └── "daily_greeting": 1712432078901
    ├── NodeVisitHistory
    │   ├── "backstory": 1712345678901
    │   └── "daily_hint": 1712432078901
    └── ChoiceSelectionHistory
        ├── "reward_choice:0": 1712345678901
        └── "shop:buy_potion": 1712432078901
```

---

## NPC交互流程

### 完整交互流程

```
graph TD
    A[玩家右键点击实体] --> B{实体是否有对话?}
    B -->|否| C[原版交互]
    B -->|是| D[NpcDialogueHandler拦截]
    D --> E[查询DialogueRegistry]
    E --> F{对话存在?}
    F -->|否| G[记录警告]
    F -->|是| H[创建DialogueSession]
    H --> I[发送S2COpenDialoguePacket]
    I --> J[客户端打开DialogueScreen]
    J --> K[显示起始节点]
    K --> L{节点类型?}
    L -->|选择节点| M[显示选项列表]
    L -->|自动推进| N[延迟后跳转]
    M --> O[玩家选择选项]
    O --> P[发送C2SDialogueChoicePacket]
    P --> Q[服务端执行动作]
    Q --> R[跳转到下一节点]
    R --> K
    N --> K
    K --> S{是否关闭?}
    S -->|是| T[结束会话]
    S -->|否| K
```

---

### NPC绑定方式

#### 方式1：通过Capability绑定

**适用**: 自定义NPC实体

```java
// 在实体初始化时
entity.getCapability(DialogueNpcPatchProvider.DIALOGUE_NPC_CAP)
    .ifPresent(patch -> {
        patch.setDialogueId("villager_greeting");
        patch.setNpcName("老村长");
    });
```

**优点**:
- 灵活，每个实体可独立配置
- 支持运行时动态修改

---

#### 方式2：实现IDialogueNpc接口

**适用**: 自定义NPC类

```java
public class MyNpcEntity extends Villager implements IDialogueNpc {
    @Override
    public String getDialogueId() {
        return "elder_dialogue";
    }
    
    @Override
    public String getNpcName() {
        return "智者";
    }
}
```

**优点**:
- 编译时类型检查
- 代码更清晰

---

#### 方式3：通过注册表绑定

**适用**: 原版实体或无法修改的实体

```java
// 在初始化时
DialogueRegistry.INSTANCE.bindNpc("villager", "villager_greeting");
```

**工作原理**:
```java
@SubscribeEvent
public static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
    Entity target = event.getTarget();
    
    // 1. 尝试获取Capability
    target.getCapability(DialogueNpcPatchProvider.DIALOGUE_NPC_CAP)
        .ifPresentOrElse(
            patch -> openDialogue(player, patch.getDialogueId()),
            () -> {
                // 2. 尝试IDialogueNpc接口
                if (target instanceof IDialogueNpc npc) {
                    openDialogue(player, npc.getDialogueId());
                } else {
                    // 3. 尝试注册表绑定
                    String entityType = ForgeRegistries.ENTITY_TYPES.getKey(target.getType()).toString();
                    String dialogueId = DialogueRegistry.INSTANCE.getDialogueForNpc(entityType);
                    if (dialogueId != null) {
                        openDialogue(player, dialogueId);
                    }
                }
            }
        );
}
```

---

## 动作执行机制

### DialogueAction 类型

#### 1. StartQuest - 开始任务

**用途**: 从对话中给予任务

**示例**:
```java
.choice("接受任务", c -> c.startQuest("arc_quest:epic_prologue").close())
```

**执行逻辑**:
```java
public record StartQuest(String questId) implements DialogueAction {
    @Override
    public void execute(ServerPlayer player, DialogueSession session) {
        QuestProgressHandler.acceptQuest(player, questId);
        
        // 触发立绘
        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def != null) {
            ClientQuestEvents.handleVisualTrigger(def, SplashType.QUEST_ACQUIRED, null);
        }
    }
}
```

---

#### 2. SetFlag - 设置标志位

**用途**: 记录对话状态，用于条件判断

**示例**:
```java
.choice("询问身世", c -> c.setFlag("knows_backstory").goTo("next"))
```

**执行逻辑**:
```java
public record SetFlag(String flagName) implements DialogueAction {
    @Override
    public void execute(ServerPlayer player, DialogueSession session) {
        IQuestCapability cap = getCapability(player);
        cap.setFlag(flagName);
    }
}
```

---

#### 3. NotifyInteract - 通知NPC交互

**用途**: 完成任务的交互目标

**示例**:
```java
.choice("打招呼", c -> c.notifyInteract("villager_npc").close())
```

**执行逻辑**:
```java
public record NotifyInteract(String npcId) implements DialogueAction {
    @Override
    public void execute(ServerPlayer player, DialogueSession session) {
        // 查找匹配的交互目标
        ObjectiveTracker.INSTANCE.notifyInteraction(
            player.getUUID(),
            npcId
        );
    }
}
```

---

#### 4. Close - 关闭对话

**用途**: 结束对话会话

**示例**:
```java
.choice("再见", c -> c.close())
```

**执行逻辑**:
```java
public record Close() implements DialogueAction {
    @Override
    public void execute(ServerPlayer player, DialogueSession session) {
        session.close();
        DialogueSessionManager.INSTANCE.endDialogue(player);
    }
}
```

---

#### 5. Custom - 自定义动作

**用途**: 扩展模组功能

**注册**:
```java
DialogueActionTypes.register(
    new ResourceLocation("mymod", "give_coins"),
    (player, data) -> {
        int amount = data.getInt("amount");
        EconomyAPI.addCoins(player, amount);
    }
);
```

**使用**:
```java
CompoundTag actionData = new CompoundTag();
actionData.putInt("amount", 500);

.choice("购买物品", c -> c.customAction("mymod:give_coins", actionData).close())
```

---

### 动作执行顺序

**规则**: 按列表中顺序依次执行

**示例**:
```java
.choice("接受任务", c -> c
    .setFlag("accepted_quest")      // 1. 设置flag
    .startQuest("my_quest")          // 2. 开始任务
    .notifyInteract("quest_giver")   // 3. 通知交互
    .close()                         // 4. 关闭对话
)
```

**执行流程**:
```java
for (DialogueAction action : choice.getActions()) {
    try {
        action.execute(player, session);
    } catch (Exception e) {
        LOGGER.error("Error executing dialogue action: {}", e.getMessage());
        // 继续执行下一个动作，不中断
    }
}
```

---

## 上下文变量替换

### DialogueContext 工作原理

**用途**: 在对话文本中动态插入变量

**示例**:
```
// 设置上下文变量
DialogueContext context = new DialogueContext();
context.put("player_name", player.getName().getString());
context.put("quest_name", "史诗序章");
context.put("villager_name", "老约翰");

// 创建对话时使用变量
.node("greeting")
    .say("你好，${player_name}！我是${villager_name}。")
    .build()

// 渲染时替换
String rendered = context.replaceVariables(node.getText());
// 输出：你好，Steve！我是老约翰。
```

---

### 内置变量

| 变量名 | 来源 | 示例值 |
|--------|------|--------|
| `${player_name}` | 玩家名称 | Steve |
| `${quest_name}` | 当前任务名 | 史诗序章 |
| `${phase_name}` | 当前阶段名 | 收集木材 |
| `${npc_name}` | NPC名称 | 老约翰 |

---

### 自定义变量

**在服务端设置**:
```java
// 在 DialogueSession 创建时
DialogueSession session = new DialogueSession(player, tree);
session.getContext().put("reputation", cap.getVariable("reputation"));
session.getContext().put("completed_quests", cap.getCompletedQuests().size());
```

**在对话中使用**:
```java
.node("status_check")
    .say("你的声望值是：${reputation}，已完成${completed_quests}个任务。")
```

---

## 条件系统

### DialogueCondition 类型总览

对话条件用于控制选项的可见性，支持多种判断逻辑：

| 条件类型 | 用途 | 示例 |
|---------|------|------|
| **任务相关** | 检查任务状态 | `HasQuest`, `QuestActive`, `QuestCompleted`, `QuestPhase` |
| **等级相关** | 检查玩家等级 | `MinLevel` |
| **历史状态** ⭐ **新增** | 检查对话/节点/选项历史 | `NodeVisited`, `ChoiceSelected`, `DialogueCompleted` |
| **冷却状态** ⭐ **新增** | 精确检查冷却时间 | `NodeOnCooldown`, `ChoiceOnCooldown`, `DialogueOnCooldown` |
| **逻辑组合** | AND/OR/NOT | `All`, `Any`, `Not` |

---

### 1. 任务相关条件

#### HasQuest - 拥有任务（任何状态）

```java
.choice("询问任务进度", c -> c.goTo("quest_status"))
    .visibleIf(new DialogueCondition.HasQuest("arc_quest:epic_prologue"))
```

---

#### QuestActive - 任务进行中

```java
.choice("继续任务", c -> c.goTo("continue"))
    .visibleIf(new DialogueCondition.QuestActive("arc_quest:epic_prologue"))
```

---

#### QuestCompleted - 任务已完成

```java
.choice("领取奖励", c -> c.giveItem("...").close())
    .visibleIf(new DialogueCondition.QuestCompleted("arc_quest:epic_prologue"))
```

---

#### QuestPhase - 任务处于特定阶段

```java
.choice("汇报进度", c -> c.goTo("report"))
    .visibleIf(new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "gather_wood"))
```

---

### 2. 历史状态条件 ⭐ **新增**

#### NodeVisited - 节点是否被访问过

**用途**: 实现一次性剧情节点

```java
// 仅当玩家未访问过 intro_story 节点时显示选项
.choiceIf(
    new DialogueCondition.Not(new DialogueCondition.NodeVisited("intro_story")),
    "介绍故事背景",
    c -> c.goTo("intro_story")
)
```

**应用场景**:
- 新手引导（只显示一次）
- 剧情揭示（避免剧透重复）
- 一次性提示

---

#### ChoiceSelected - 选项是否被选择过

**用途**: 实现一次性奖励选项

```java
// 仅当玩家未选择过该选项时显示
.choiceIf(
    new DialogueCondition.Not(new DialogueCondition.ChoiceSelected("start:reward")),
    "领取新手礼包（仅限一次）",
    c -> c.giveItem("minecraft:diamond", 5).close()
)
```

**选项键格式**:
- 推荐：`nodeId:choiceIndex` （如 `"start:0"`）
- 或自定义唯一键（如 `"daily_reward_2024"`）

---

#### DialogueCompleted - 对话树是否已完成

**用途**: 实现首次见面礼

```java
// 仅当玩家未完成此对话树时显示
.choiceIf(
    new DialogueCondition.Not(new DialogueCondition.DialogueCompleted("epic_village_elder")),
    "首次见面礼",
    c -> c.giveItem("minecraft:bread", 10).close()
)
```

---

### 3. 冷却状态条件 ⭐ **新增**

> ⚠️ **重要**：冷却条件需要手动指定冷却时间，必须与定义中的 `cooldownSeconds` 一致。

#### NodeOnCooldown - 节点是否在冷却中

```java
// 每日任务节点（24小时冷却）
.choiceIf(
    new DialogueCondition.Not(new DialogueCondition.NodeOnCooldown("daily_quest", 86400)),
    "领取每日任务",
    c -> c.goTo("daily_quest_node")
)
```

**工作原理**:
```java
long lastTime = cap.getLastNodeVisit(nodeId);
if (lastTime == 0) return false; // 从未访问过，不在冷却中

long currentTime = System.currentTimeMillis();
long cooldownMs = cooldownSeconds * 1000;
return (currentTime - lastTime) < cooldownMs; // 精确判断
```

---

#### ChoiceOnCooldown - 选项是否在冷却中

```java
// 提示选项（1小时冷却）
.choiceIf(
    new DialogueCondition.Not(new DialogueCondition.ChoiceOnCooldown("start:hint", 3600)),
    "获取提示（1小时冷却）",
    c -> c.goTo("hint_node")
)
```

---

#### DialogueOnCooldown - 对话树是否在冷却中

```java
// NPC 对话冷却（2小时）
.choiceIf(
    new DialogueCondition.Not(new DialogueCondition.DialogueOnCooldown("epic_village_elder", 7200)),
    "再次交谈（2小时后可用）",
    c -> c.goTo("repeat_chat")
)
```

---

### 4. 逻辑组合条件

#### Not - 逻辑取反

```java
// 仅当玩家没有某个任务时显示
.choiceIf(
    new DialogueCondition.Not(new DialogueCondition.HasQuest("arc_quest:epic_prologue")),
    "接受任务",
    c -> c.startQuest("arc_quest:epic_prologue").close()
)
```

---

#### All - 所有条件均满足（AND）

```java
// 需要同时满足多个条件
.choiceIf(
    new DialogueCondition.All(List.of(
        new DialogueCondition.QuestCompleted("arc_quest:epic_prologue"),
        new DialogueCondition.MinLevel(10),
        new DialogueCondition.Not(new DialogueCondition.HasQuest("arc_quest:epic_chapter1"))
    )),
    "开始第一章",
    c -> c.startQuest("arc_quest:epic_chapter1").close()
)
```

---

#### Any - 任一条件满足（OR）

```java
// 满足任一条件即可
.choiceIf(
    new DialogueCondition.Any(List.of(
        new DialogueCondition.QuestCompleted("arc_quest:side_quest_a"),
        new DialogueCondition.QuestCompleted("arc_quest:side_quest_b")
    )),
    "解锁隐藏剧情",
    c -> c.goTo("secret_story")
)
```

---

### 5. 实战：复杂条件组合

#### 示例：分支选择

```java
.node("branch_choice")
    .say("你选择了哪条道路？")
    
    // 战斗路线：需要完成序章且等级>=15
    .choiceIf(
        new DialogueCondition.All(List.of(
            new DialogueCondition.QuestCompleted("arc_quest:epic_prologue"),
            new DialogueCondition.MinLevel(15)
        )),
        "战斗之路（需等级15）",
        c -> c.setFlag("chose_combat_path").goTo("combat_intro")
    )
    
    // 探索路线：需要完成序章且有特定道具
    .choiceIf(
        new DialogueCondition.All(List.of(
            new DialogueCondition.QuestCompleted("arc_quest:epic_prologue"),
            new DialogueCondition.HasQuest("arc_quest:has_compass")
        )),
        "探索之路（需指南针）",
        c -> c.setFlag("chose_exploration_path").goTo("exploration_intro")
    )
    
    // 默认选项：未达到条件
    .choice("我还没准备好", c -> c.close())
```

---

#### 示例：周期性内容

```java
.node("daily_rewards")
    .say("今日奖励已刷新！")
    
    // 每日签到（24小时冷却）
    .choiceIf(
        new DialogueCondition.Not(new DialogueCondition.NodeOnCooldown("daily_signin", 86400)),
        "签到领取奖励",
        c -> c
            .giveItem("minecraft:gold_ingot", 10)
            .close()
    )
    
    // 每小时提示
    .choiceIf(
        new DialogueCondition.Not(new DialogueCondition.NodeOnCooldown("daily_hint", 3600)),
        "获取今日提示",
        c -> c.goTo("hint_node")
    )
    
    // 总是可见的退出选项
    .choice("离开", c -> c.close())
```

---

## 动态文本与预设动作

### sayIf() - 条件文本

**用途**: 根据条件动态显示不同的对话文本

**API**:
```java
public DialogueTreeBuilder sayIf(DialogueCondition condition, String text)
```

**工作原理**:
1. 在服务端评估所有 `sayIf()` 条件
2. 第一个满足条件的文本被选中
3. 如果都不满足，使用最后的 `say()` 作为兜底

---

#### 示例1：根据任务状态显示不同文本

```java
.node("start")
    // 情况1：完全新手
    .sayIf(
        new DialogueCondition.Not(new DialogueCondition.HasQuest("arc_quest:epic_prologue")),
        "你好，冒险者！我是村庄长老。"
    )
    
    // 情况2：序章进行中
    .sayIf(
        new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "gather_wood"),
        "木材收集得怎么样了？"
    )
    
    // 情况3：序章已完成
    .sayIf(
        new DialogueCondition.QuestCompleted("arc_quest:epic_prologue"),
        "感谢你保护了村庄！"
    )
    
    // 默认文本（兜底）
    .say("欢迎回到村庄。")
```

**执行流程**:
```
玩家打开对话
  ↓
服务端评估条件（按顺序）
  ↓
找到第一个满足的条件 → 返回对应文本
  ↓
发送到客户端显示
```

---

#### 示例2：复杂条件组合

```java
.node("greeting")
    // 传奇英雄
    .sayIf(
        new DialogueCondition.QuestCompleted("arc_quest:epic_finale"),
        "向您致敬，传奇英雄！"
    )
    
    // 第一章完成，等待分支选择
    .sayIf(
        new DialogueCondition.All(List.of(
            new DialogueCondition.QuestCompleted("arc_quest:epic_chapter1"),
            new DialogueCondition.Not(new DialogueCondition.HasQuest("arc_quest:epic_branch_choice"))
        )),
        "你已准备好做出选择了。"
    )
    
    // 默认
    .say("你好，旅行者。")
```

---

### choiceIf() - 条件选项

**用途**: 根据条件动态显示/隐藏选项

**API**:
```java
public DialogueTreeBuilder choiceIf(
    DialogueCondition condition, 
    String text,
    Consumer<ChoiceBuilder> configurator
)
```

**示例**:
```java
.node("main_menu")
    .say("你想做什么？")
    
    // 仅当未接受任务时显示
    .choiceIf(
        new DialogueCondition.Not(new DialogueCondition.HasQuest("quest_id")),
        "接受任务",
        c -> c.startQuest("quest_id").close()
    )
    
    // 仅当任务进行中时显示
    .choiceIf(
        new DialogueCondition.QuestActive("quest_id"),
        "汇报进度",
        c -> c.goTo("report")
    )
    
    // 仅当任务完成后显示
    .choiceIf(
        new DialogueCondition.QuestCompleted("quest_id"),
        "领取奖励",
        c -> c.giveItem("...").close()
    )
    
    // 总是显示
    .choice("离开", c -> c.close())
```

---

### PresetActions - 预设动作

**位置**: `org.com.arc_quest.dialogue.action.PresetActions`

**用途**: 提供常用的物品给予、效果施加等快捷方法

---

#### 内置预设动作（ChoiceBuilder 方法）

| 方法 | 说明 | 等效操作 |
|------|------|----------|
| `presetStoneSword()` | 给予石剑 x1 | `giveItem("minecraft:stone_sword", 1)` |
| `presetIronArmorSet()` | 给予铁甲全套 | 4个 giveItem 调用 |
| `presetDiamondArmorSet()` | 给予钻石甲全套 | 4个 giveItem 调用 |
| `presetNetheriteIngot()` | 给予下界合金锭 x1 | `giveItem("minecraft:netherite_ingot", 1)` |
| `presetTorches()` | 给予火把 x32 | `giveItem("minecraft:torch", 32)` |
| `presetBread()` | 给予面包 x4 | `giveItem("minecraft:bread", 4)` |
| `presetStrength()` | 给予力量效果60秒 | `runCommand("effect give @p minecraft:strength 60 0")` |
| `presetHeroOfVillage()` | 给予村庄英雄30分钟 | `runCommand("effect give @p minecraft:hero_of_the_village 1800 0")` |

---

#### 使用示例

```java
.choice("领取新手礼包", c -> c
    .presetStoneSword()           // 石剑
    .presetBread()                // 面包
    .presetTorches()              // 火把
    .close()
)

.choice("领取高级装备", c -> c
    .presetIronArmorSet()         // 铁甲全套
    .presetStrength()             // 力量效果
    .close()
)

.choice("传奇奖励", c -> c
    .presetDiamondArmorSet()      // 钻石甲全套
    .presetNetheriteIngot()       // 下界合金锭
    .presetHeroOfVillage()        // 村庄英雄
    .close()
)
```

---

#### 自定义预设动作

你可以在自己的代码中创建类似的预设方法：

```java
public class MyPresetActions {
    
    /** 给予玩家探险家套装 */
    public static void applyExplorerKit(ChoiceBuilder builder) {
        builder
            .giveItem("minecraft:leather_helmet", 1)
            .giveItem("minecraft:compass", 1)
            .giveItem("minecraft:map", 1)
            .giveItem("minecraft:torch", 16);
    }
}

// 使用
.choice("领取探险家套装", c -> {
    MyPresetActions.applyExplorerKit(c);
    c.close();
})
```

---

#### PresetActions 工具类

**位置**: `org.com.arc_quest.dialogue.action.PresetActions`

**提供的静态方法**:

| 方法 | 参数 | 说明 |
|------|------|------|
| `awardItem()` | ServerPlayer, Item, int | 玩家获得物品 |
| `giveItem()` | ServerPlayer, Item | 玩家失去物品 |
| `exChangeItem()` | ServerPlayer, Item, Item | 玩家交换物品 |
| `addEffects()` | ServerPlayer, MobEffect, int, int | 玩家获得效果 |
| `triggerInteraction()` | ServerPlayer, String | 触发任务交互标识 |
| `addEvents()` | ServerPlayer, Entity, BiConsumer | 添加自定义事件处理器 |

**使用场景**:
- 在自定义 DialogueAction 中调用
- 在 Lambda 回调中使用
- 扩展模组功能时复用

---

#### 示例：使用 addEvents Lambda 回调

```java
.choice("特殊互动", c -> c
    .addEvents((player, target) -> {
        // 自定义逻辑
        PresetActions.addEffects(player, MobEffects.SPEED, 1200, 1);
        player.sendMessage(
            Component.literal("你获得了速度提升！"), 
            Util.NIL_UUID
        );
    })
    .close()
)
```

---

## 实战示例

### 示例1：简单问候对话

``java
DialogueTreeBuilder.create("simple_greeting")
    .npc("村民")

    .node("start")
        .say("你好，旅行者！欢迎来到我们的村庄。")
        .choice("谢谢！", c -> c.goTo("thanks"))
        .choice("再见", c -> c.close())

    .node("thanks")
        .say("不客气！如果需要帮助，随时找我。")
        .choice("好的", c -> c.close())

    .buildAndRegister();
```

**流程**:
```
玩家右键村民
  ↓
显示："你好，旅行者！..."
  ↓
选项：["谢谢！", "再见"]
  ↓
选择"谢谢！" → 显示："不客气！..." → 关闭
选择"再见" → 直接关闭
```

---

### 示例2：任务给予对话

``java
DialogueTreeBuilder.create("quest_giver")
    .npc("村长")

    .node("start")
        .say("冒险者，村庄正面临危机！你能帮助我们吗？")
        .choice("发生了什么事？", c -> c.goTo("explain"))
        .choice("我没兴趣", c -> c.close())

    .node("explain")
        .say("僵尸每晚袭击村庄，我们需要有人清理它们。")
        .choice("我来帮忙！", c -> c
            .startQuest("arc_quest:zombie_clearance")
            .setFlag("accepted_zombie_quest")
            .close())
        .choice("让我考虑一下", c -> c.goTo("consider"))

    .node("consider")
        .say("好吧，如果你改变主意，随时回来找我。")
        .choice("好的", c -> c.close())

    .visualConfig(QuestVisualConfig.builder()
        .splash(SplashType.DIALOGUE_START, 
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/splash/village_elder.png"),
            1.2f)
        .themeColor(ChatFormatting.GOLD)
        .build())

    .buildAndRegister();
```

**流程**:
```
开始对话 → 显示立绘
  ↓
"发生了什么事？" / "我没兴趣"
  ↓
选择"发生了什么事？"
  ↓
"僵尸每晚袭击..."
  ↓
"我来帮忙！" / "让我考虑一下"
  ↓
选择"我来帮忙！"
  ↓
执行动作：
  1. startQuest("zombie_clearance")
  2. setFlag("accepted_zombie_quest")
  3. close()
  ↓
任务已接受，对话关闭
```

---

### 示例3：条件分支对话

``java
DialogueTreeBuilder.create("shopkeeper")
    .npc("商人")

    .node("start")
        .say("欢迎光临！${player_name}，今天想看点什么？")
        .choice("查看商品", c -> c.goToIf("show_goods", "has_met_before"))
        .choice("首次见面", c -> c.goTo("first_meeting"))

    .node("first_meeting")
        .say("哦，你是新面孔！我叫老杰克，是这个村的商人。")
        .setFlagOnEnter("has_met_before")
        .choice("很高兴认识你", c -> c.goTo("show_goods"))

    .node("show_goods")
        .say("这是我的商品清单...")
        .choice("购买药水", c -> c
            .customAction("mymod:buy_potion", buyData)
            .close())
        .choice("只是看看", c -> c.close())

    .buildAndRegister();
```

**条件判断逻辑**:
``java
// goToIf 内部实现
public NodeBuilder goToIf(String nodeId, String requiredFlag) {
    this.choices.add(new DialogueChoice(
        text,
        nodeId,
        List.of(new FlagSetCondition(requiredFlag)),  // 可见性条件
        List.of()
    ));
    return this;
}

// 客户端过滤可见选项
List<DialogueChoice> visibleChoices = node.getChoices().stream()
    .filter(choice -> choice.isVisible(player))
    .toList();
```

---

### 示例4：多语言对话

**定义翻译键**:
```
# assets/arc_quest/lang/en_us.json
{
  "dialogue.shopkeeper.start.text": "Welcome, ${player_name}!",
  "dialogue.shopkeeper.start.choice1": "Show me your wares",
  "dialogue.shopkeeper.start.choice2": "Goodbye"
}

# assets/arc_quest/lang/zh_cn.json
{
  "dialogue.shopkeeper.start.text": "欢迎，${player_name}！",
  "dialogue.shopkeeper.start.choice1": "看看你的商品",
  "dialogue.shopkeeper.start.choice2": "再见"
}
```

**使用翻译**:
``java
DialogueTreeBuilder.create("shopkeeper")
    .npc(Component.translatable("npc.shopkeeper.name").getString())

    .node("start")
        .say(Component.translatable("dialogue.shopkeeper.start.text").getString())
        .choice(Component.translatable("dialogue.shopkeeper.start.choice1").getString(),
                c -> c.goTo("goods"))
        .choice(Component.translatable("dialogue.shopkeeper.start.choice2").getString(),
                c -> c.close())

    .buildAndRegister();
```

---

## 附录

### 调试技巧

#### 1. 强制打开对话

```
/quest dialogue @p villager_greeting
```

#### 2. 查看对话注册表

```
/quest registry
# 输出所有注册的对话ID
```

#### 3. 日志监控

```
// 在 log4j2.xml 中添加
<Logger name="org.com.arc_quest.dialogue" level="DEBUG"/>
```

**关键日志**:
```
[ArcQuest] Player Steve started dialogue: villager_greeting
[ArcQuest] Executing action: StartQuest{questId=arc_quest:test}
[ArcQuest] Player Steve ended dialogue
```

---

### 常见问题

#### Q1: 对话为什么不显示？

**A**: 检查以下几点：
1. 对话是否正确注册到 DialogueRegistry
2. NPC 是否正确绑定了 dialogueId
3. 网络包是否正常发送（查看日志）
4. 客户端是否正确接收并打开 DialogueScreen

---

#### Q2: 选项为什么不可见？

**A**: 检查可见性条件：
```java
// 确保条件正确
.choice("隐藏选项", c -> c.goTo("secret"))
    .visibleIf(new FlagSetCondition("unlocked_secret"))  // 需要此flag

// 或者无条件显示
.choice("始终可见", c -> c.goTo("normal"))
```

---

#### Q3: 如何重置对话进度？

**A**: 清除相关flag
```bash
# 手动清除flag
/quest flag @p remove has_met_before

# 或重置所有数据
/quest resetall @p
```

---

#### Q4: 如何实现打字机效果？

**A**: DialogueScreen 已内置打字机效果

**配置**:
```java
// 在 DialogueScreen 中
private static final int TYPEWRITER_SPEED = 50;  // 每字符延迟(ms)
private static final boolean ENABLE_TYPEWRITER = true;
```

**禁用**:
```java
// 对于重要信息，可跳过动画
session.skipTypewriter();
```

---

**文档结束**

*本文档详细讲解了Arc Quest对话系统的设计原理、交互流程和实战示例。*
