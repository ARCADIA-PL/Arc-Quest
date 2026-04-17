# 对话系统与任务联动完整指南

## 📋 目录

1. [核心概念](#核心概念)
2. [内置条件类型](#内置条件类型)
3. [TestDialogues 示例解析](#testdialogues-示例解析)
4. [高级用法](#高级用法)
5. [最佳实践](#最佳实践)

---

## 核心概念

### 架构设计原则

Arc Quest 的对话系统通过 **`DialogueCondition`** 实现与任务系统的深度集成：

```
┌─────────────────────────────────────────────┐
│         DialogueTree (对话树)                │
│                                             │
│  Node (节点)                                │
│   ├─ say() - NPC说的话                      │
│   └─ choice() - 玩家选项                    │
│        ├─ onlyIf(Condition) - 显示条件      │
│        └─ action() - 执行动作               │
│             ├─ startQuest()                 │
│             ├─ notifyInteract()             │
│             └─ ...                          │
└─────────────────────────────────────────────┘
           ↓ 条件检测
┌─────────────────────────────────────────────┐
│      QuestCapability (玩家任务数据)          │
│                                             │
│  ├─ activeQuests - 活跃任务                  │
│  ├─ completedQuests - 已完成任务             │
│  └─ flags/variables - 标记和变量             │
└─────────────────────────────────────────────┘
```

### 关键API

| API | 作用 | 使用场景 |
|-----|------|---------|
| `choiceIf(condition, text, callback)` | 添加带条件的选项 | 根据任务状态显示不同选项 |
| `onlyIf(condition)` | 为选项添加前置条件 | ChoiceBuilder内部调用 |
| `.startQuest(questId)` | 给予任务 | 首次接受任务 |
| `.notifyInteract(targetId)` | 标记交互完成 | 推进INTERACT目标进度 |

---

## 内置条件类型

`DialogueCondition` 是 **sealed interface**，提供以下内置实现：

### 1. 任务状态条件

```java
// 玩家拥有指定任务（任何状态）
new DialogueCondition.HasQuest("arc_quest:epic_prologue")

// 玩家的指定任务处于活跃状态
new DialogueCondition.QuestActive("arc_quest:epic_prologue")

// 玩家已完成指定任务
new DialogueCondition.QuestCompleted("arc_quest:epic_prologue")
```

### 2. 任务阶段条件

```java
// 玩家的指定任务处于特定阶段
new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "gather_wood")
```

### 3. 等级条件

```java
// 玩家等级 >= 10
new DialogueCondition.MinLevel(10)
```

### 4. 逻辑组合条件

```java
// 逻辑取反
new DialogueCondition.Not(innerCondition)

// AND - 所有条件均满足
new DialogueCondition.All(List.of(cond1, cond2, cond3))

// OR - 任一条件满足
new DialogueCondition.Any(List.of(cond1, cond2))

// 无条件通过
new DialogueCondition.Always()
```

---

## TestDialogues 示例解析

### 完整代码

参见 [TestDialogues.java](file:///D:/Arc%20Quest/src/main/java/org/com/arc_quest/dialogue/registry/TestDialogues.java)

### 功能演示

#### 1️⃣ 根据任务状态显示不同选项

```java
.node("start")
.say("村里不太平...")

// 情况1：任务未开始 → 显示"接受任务"选项
.choiceIf(
    new DialogueCondition.Not(new DialogueCondition.HasQuest("arc_quest:epic_prologue")),
    "你有什么事吗？",
    c -> c.goTo("accept_quest")
)

// 情况2：任务进行中（gather_wood阶段）→ 显示"收集木材进度"选项
.choiceIf(
    new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "gather_wood"),
    "木材收集得怎么样了？",
    c -> c.goTo("ask_problem")
)

// 情况3：任务进行中（talk_villager阶段）→ 显示"与村民交互"选项
.choiceIf(
    new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "talk_villager"),
    "我来帮你解决怪物！",
    c -> c.goTo("interact_complete")
)

// 情况4：任务已完成 → 显示"感谢"选项
.choiceIf(
    new DialogueCondition.QuestCompleted("arc_quest:epic_prologue"),
    "谢谢你帮助村庄！",
    DialogueTreeBuilder.ChoiceBuilder::close
)
```

**效果**:
- ✅ 玩家第一次对话：看到"你有什么事吗？" → 进入接受任务流程
- ✅ 玩家在收集木材阶段：看到"木材收集得怎么样了？" → 闲聊
- ✅ 玩家在交互阶段：看到"我来帮你解决怪物！" → 完成任务目标
- ✅ 玩家完成任务后：看到"谢谢你帮助村庄！" → 关闭对话

---

#### 2️⃣ 根据任务阶段执行不同动作

```java
.node("ask_problem")
.say("村外有僵尸出没...")

// 选项1：如果处于 gather_wood 阶段，仅提示去收集木材
.choiceIf(
    new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "gather_wood"),
    "我先去准备武器",
    c -> c.close()  // 仅关闭，不推进任务
)

// 选项2：如果处于 talk_villager 阶段，可以完成任务目标
.choiceIf(
    new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "talk_villager"),
    "我来杀！",
    c -> c
        .notifyInteract("arc_quest:villager_npc")  // 标记交互完成
        .close()
)

// 选项3：通用选项 - 害怕溜了
.choice("害怕，溜了",
    c -> c.goTo("decline"))
```

**效果**:
- ✅ gather_wood 阶段：选择后仅关闭对话，任务不推进
- ✅ talk_villager 阶段：选择后标记交互完成，任务自动推进到下一阶段
- ✅ 任何时候：都可以选择"害怕溜了"

---

#### 3️⃣ 接受任务流程

```java
.node("accept_quest")
.say("勇士，你能帮帮我们吗？")

.choice("好的，我来帮忙",
    c -> c
        .startQuest("arc_quest:epic_prologue")  // 给予任务
        .goTo("ask_problem"))  // 跳转到下一阶段

.choice("不了，我还有事",
    c -> c.goTo("decline"))
```

**执行流程**:
```
1. 玩家选择"好的，我来帮忙"
2. 执行 .startQuest("arc_quest:epic_prologue")
   ├─ 检查任务是否已激活 → 否
   ├─ 创建 QuestRuntimeData
   ├─ 加入 activeQuests Map
   ├─ 注册第一阶段的目标追踪器
   └─ 同步到客户端
3. 执行 .goTo("ask_problem")
   └─ 跳转到 ask_problem 节点继续对话
```

---

## 高级用法

### 组合条件示例

#### AND 条件：任务激活中且处于指定阶段

```java
.choiceIf(
    new DialogueCondition.All(List.of(
        new DialogueCondition.QuestActive("arc_quest:epic_prologue"),
        new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "defend_village")
    )),
    "僵尸防御战准备好了吗？",
    c -> c.startQuest("arc_quest:defense_prep")
)
```

#### OR 条件：任务完成或处于后期阶段

```java
.choiceIf(
    new DialogueCondition.Any(List.of(
        new DialogueCondition.QuestCompleted("arc_quest:epic_prologue"),
        new DialogueCondition.QuestPhase("arc_quest:epic_chapter1", "boss_fight")
    )),
    "你已经足够强大了，可以挑战最终Boss",
    c -> c.goTo("final_challenge")
)
```

#### NOT + HasQuest：排除已有任务的玩家

```java
.choiceIf(
    new DialogueCondition.Not(new DialogueCondition.HasQuest("arc_quest:epic_chapter1")),
    "你想开始新的冒险吗？",
    c -> c.startQuest("arc_quest:epic_chapter1")
)
```

---

### 多任务联动示例

假设有一个NPC，根据多个任务的状态说不同的话：

```java
.node("blacksmith")
.say("欢迎来到铁匠铺！")

// 情况1：未完成序章
.choiceIf(
    new DialogueCondition.Not(new DialogueCondition.HasQuest("arc_quest:epic_prologue")),
    "你是新来的吧？先去见村长吧。",
    DialogueTreeBuilder.ChoiceBuilder::close
)

// 情况2：序章进行中，但未防御村庄
.choiceIf(
    new DialogueCondition.All(List.of(
        new DialogueCondition.QuestActive("arc_quest:epic_prologue"),
        new DialogueCondition.Not(new DialogueCondition.QuestPhase(
            "arc_quest:epic_prologue", "defend_village"))
    )),
    "你需要一把好剑，去收集铁矿石吧。",
    c -> c.giveItem("minecraft:iron_sword", 1)
)

// 情况3：已完成序章，但第一章未完成
.choiceIf(
    new DialogueCondition.All(List.of(
        new DialogueCondition.QuestCompleted("arc_quest:epic_prologue"),
        new DialogueCondition.Not(new DialogueCondition.QuestCompleted("arc_quest:epic_chapter1"))
    )),
    "矿洞很危险，带上这些火把。",
    c -> c.giveItem("minecraft:torch", 32)
)

// 情况4：已完成第一章
.choiceIf(
    new DialogueCondition.QuestCompleted("arc_quest:epic_chapter1"),
    "你已经是经验丰富的冒险者了，需要升级装备吗？",
    c -> c.goTo("upgrade_menu")
)
```

---

### 动态奖励示例

根据任务进度给予不同奖励：

```java
.node("reward_claim")
.say("这是你的奖励！")

// 早期完成：基础奖励
.choiceIf(
    new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "gather_food"),
    "谢谢！（获得铁剑 x1）",
    c -> c
        .giveItem("minecraft:iron_sword", 1)
        .close()
)

// 中期完成：进阶奖励
.choiceIf(
    new DialogueCondition.QuestCompleted("arc_quest:epic_chapter1"),
    "太棒了！（获得钻石镐 x1）",
    c -> c
        .giveItem("minecraft:diamond_pickaxe", 1)
        .giveXp(100)
        .close()
)

// 后期完成：终极奖励
.choiceIf(
    new DialogueCondition.QuestCompleted("arc_quest:epic_finale"),
    "你是传奇英雄！（获得下界合金锭 x8）",
    c -> c
        .giveItem("minecraft:netherite_ingot", 8)
        .giveXp(500)
        .runCommand("effect give @p minecraft:strength 300 1")
        .close()
)
```

---

## 最佳实践

### ✅ 推荐做法

1. **始终提供默认选项**
   ```java
   // ✅ 好：有无条件兜底选项
   .choiceIf(condition, "特殊选项", c -> c.action())
   .choice("普通选项", c -> c.close())
   
   // ❌ 坏：所有选项都有条件，可能导致无选项可选
   .choiceIf(cond1, "选项1", c -> c.action())
   .choiceIf(cond2, "选项2", c -> c.action())
   ```

2. **使用语义化的节点ID**
   ```java
   // ✅ 好：清晰表达意图
   .node("accept_quest")
   .node("interact_complete")
   .node("decline")
   
   // ❌ 坏：含义不明
   .node("node1")
   .node("node2")
   ```

3. **条件组合不超过3层嵌套**
   ```java
   // ✅ 好：简洁明了
   new DialogueCondition.QuestPhase("quest_id", "phase_id")
   
   // ✅ 好：适度组合
   new DialogueCondition.All(List.of(cond1, cond2))
   
   // ❌ 坏：过度复杂，难以维护
   new DialogueCondition.Not(
       new DialogueCondition.Any(List.of(
           new DialogueCondition.All(List.of(...)),
           new DialogueCondition.Not(...)
       ))
   )
   ```

4. **利用 Flags 简化条件**
   ```java
   // 在任务完成时设置标记
   .setFlagOnComplete("prologue_completed")
   
   // 对话中直接检查标记（需自定义条件）
   // 比检查任务状态更高效
   ```

---

### ❌ 常见错误

1. **忘记检查任务是否存在**
   ```java
   // ❌ 可能崩溃：任务不存在时 getActiveQuest() 返回 null
   new DialogueCondition.QuestPhase("non_existent_quest", "phase")
   
   // ✅ 安全：内置条件已处理 null 检查
   new DialogueCondition.QuestPhase("quest_id", "phase_id")
   ```

2. **条件过于复杂导致性能问题**
   ```java
   // ❌ 每次打开对话都遍历大量条件
   new DialogueCondition.Any(List.of(
       cond1, cond2, cond3, ..., cond50
   ))
   
   // ✅ 优化：拆分为多个节点，按需加载
   .node("check_condition_group1")
   .node("check_condition_group2")
   ```

3. **重复调用 startQuest**
   ```java
   // ❌ 任务已激活时再次调用会返回 false
   .choice("接受任务", c -> c.startQuest("quest_id"))
   
   // ✅ 先检查任务状态
   .choiceIf(
       new DialogueCondition.Not(new DialogueCondition.HasQuest("quest_id")),
       "接受任务",
       c -> c.startQuest("quest_id")
   )
   ```

---

## 调试技巧

### 1. 查看当前任务状态

```bash
# 查看活跃任务
/quest list @p

# 输出示例：
# Active Quests:
#   - arc_quest:epic_prologue
#     State: ACTIVE
#     Phase: talk_villager
#     Progress: [5/5, 0/1]
```

### 2. 强制设置任务阶段（测试用）

```bash
# 手动推进任务阶段
/quest phase @p arc_quest:epic_prologue defend_village
```

### 3. 日志输出

在 `NpcDialogueHandler` 中添加调试日志：

```java
LOGGER.debug("[Dialogue] Evaluating condition for choice '{}': {}", 
    choice.text(), 
    condition.test(player));
```

---

## 总结

通过 `DialogueCondition` 和 `DialogueTreeBuilder`，你可以轻松实现：

- ✅ **动态对话内容**：根据任务状态显示不同文本
- ✅ **条件选项**：玩家只能看到符合条件的选项
- ✅ **任务联动**：对话中直接给予/推进任务
- ✅ **复杂逻辑**：支持 AND/OR/NOT 组合条件

**核心优势**：
- 🚀 无需修改核心代码
- 🔧 完全可扩展（添加新的 Condition 类型）
- 📦 符合现有架构设计
- 💡 简洁的流式API

现在你可以在 TestDialogues 中看到完整的实战示例，尝试修改条件观察效果！
