# 游戏时间刻冷却系统

**模块**: dialogue/ + quest/capability/  
**最后更新**: 2026-04-17  
**版本**: v3.2（基于 Minecraft 世界总运行时间的精确冷却）

---

## 📋 目录

1. [系统概述](#系统概述)
2. [为什么使用游戏时间刻](#为什么使用游戏时间刻)
3. [核心概念](#核心概念)
4. [API 使用指南](#api-使用指南)
5. [时间段条件](#时间段条件)
6. [数据存储与序列化](#数据存储与序列化)
7. [实现细节](#实现细节)
8. [最佳实践](#最佳实践)
9. [常见问题](#常见问题)

---

## 系统概述

### 什么是游戏时间刻冷却？

游戏时间刻冷却系统允许对话节点、选项和对话树在**每天固定的游戏时间刻**重置，而不是基于现实时间或简单的天数计算。

**关键特性**:
- ✅ 使用 `Level.getGameTime()` 获取世界总运行时间
- ✅ 精确到 tick 级别（1 tick = 1/20 秒）
- ✅ 不受 TPS 波动、玩家离线、世界暂停影响
- ✅ 支持跨天区间（如 12000-0，晚上6点到早上6点）
- ✅ 与任务系统共享 Capability 存储

---

## 为什么使用游戏时间刻

### 对比三种冷却方式

| 特性 | 现实时间冷却 | 游戏日冷却 | **游戏时间刻冷却** ⭐ |
|------|-------------|-----------|---------------------|
| **精度** | 毫秒级 | 天级 | **Tick 级** |
| **固定时间点** | ❌ 不支持 | ❌ 仅整天 | ✅ **任意时刻** |
| **受 TPS 影响** | ❌ 是 | ❌ 是 | ✅ **否** |
| **玩家离线影响** | ❌ 继续计时 | ❌ 跳过时间 | ✅ **准确累计** |
| **世界暂停影响** | ❌ 继续计时 | ❌ 跳过时间 | ✅ **暂停计时** |
| **适用场景** | 限时活动 | 每日任务 | **定时刷新/NPC作息** |

---

### 实际应用场景

#### 场景1：NPC 作息时间

```java
// 铁匠只在白天工作（6:00-18:00）
.node("blacksmith_work")
    .sayIf(
        Map.of(
            // 早上6点到晚上6点
            "0", "早上好！需要打造武器吗？",
            "6000", "中午好！今天生意不错。",
            "12000", "抱歉，我已经下班了。明天再来吧！"
        ),
        new DialogueCondition.GameTimeInRange(0, 12000) // 6:00-18:00
    )
    .cooldownType(CooldownType.GAME_TICK)
    .cooldownResetTick(0) // 每天早上6点重置
```

**行为**:
- 🌅 6:00-18:00：铁匠正常工作
- 🌙 18:00-次日6:00：铁匠休息
- ⏰ 每天6:00自动重置冷却

---

#### 场景2：每日任务刷新

```java
// 每日悬赏任务，每天早上7点刷新
.node("daily_bounty")
    .say("今天的悬赏任务是...")
    .choice("接受任务", c -> c.startQuest("daily_hunt").close())
    .repeatable(true)
    .cooldownType(CooldownType.GAME_TICK)
    .cooldownResetTick(1000) // 每天早上7点（1000 tick）重置
```

**优势**:
- ✅ 所有玩家在同一游戏时刻看到新任务
- ✅ 不受服务器 TPS 波动影响
- ✅ 玩家离线不会错过刷新

---

#### 场景3：商店补货

```java
// 商人每3天补货一次
.node("merchant_shop")
    .say("欢迎光临！今日商品：")
    .choice("购买商品", c -> c.goTo("shop_menu"))
    .repeatable(true)
    .cooldownType(CooldownType.GAME_TICK)
    .cooldownResetTick(0) // 每天6:00检查
    // 配合自定义条件判断是否满3天
```

---

## 核心概念

### Minecraft 时间系统

Minecraft 使用 **tick** 作为时间单位：

```
1 tick = 1/20 秒（理想情况下）
1 游戏日 = 24000 ticks = 20 分钟（现实时间）
```

**时间刻对应关系**:

| Tick 值 | 游戏时间 | 说明 |
|---------|---------|------|
| **0** | 早上 6:00 | 日出，一天开始 |
| **1000** | 早上 7:00 | `/time set day` 默认值 |
| **6000** | 中午 12:00 | 正午 |
| **12000** | 晚上 6:00 | 日落开始 |
| **13000** | 晚上 7:00 | `/time set night` 默认值 |
| **18000** | 午夜 12:00 | 深夜 |
| **23999** | 早上 5:59 | 一天结束 |

---

### CooldownType 枚举

```java
public enum CooldownType {
    NONE,           // 无冷却
    SECONDS,        // 现实时间秒（System.currentTimeMillis）
    GAME_DAY,       // 游戏日（按天计算）
    GAME_TICK       // ⭐ 游戏时间刻（Level.getGameTime）
}
```

**GAME_TICK 工作原理**:

```java
// 记录访问时的世界总运行时间
long worldGameTime = player.level().getGameTime();
cap.recordNodeVisitGameTime(nodeId, worldGameTime);

// 检查是否经过重置点
long lastGameTime = cap.getLastNodeVisitGameTime(nodeId);
long currentTotalGameTime = player.level().getGameTime();

// 计算经过的天数
long daysElapsed = (currentTotalGameTime - lastGameTime) / 24000;

if (daysElapsed > 0) {
    return true; // 已过至少一天，冷却重置
}

// 同一天内，检查是否跨过重置点
long currentDayTime = currentTotalGameTime % 24000;
long lastDayTime = lastGameTime % 24000;

return (lastDayTime <= resetTick && currentDayTime >= resetTick);
```

---

## API 使用指南

### 1. 配置对话节点的 GAME_TICK 冷却

```java
DialogueTreeBuilder.create("daily_npc")
    .npc("村民")
    
    .node("greeting")
        .say("早上好！这是今天的奖励。")
        .choice("领取", c -> c.giveItem("minecraft:bread", 5).close())
        .repeatable(true)
        .cooldownType(CooldownType.GAME_TICK)  // ⭐ 使用游戏时间刻
        .cooldownResetTick(0)                   // ⭐ 每天早上6点重置
    
    .buildAndRegister();
```

---

### 2. 配置选项的 GAME_TICK 冷却

```java
.node("shop")
    .say("今日特惠：")
    .choice("购买药水（每天限购）", c -> c
        .giveItem("minecraft:potion", 1)
        .repeatable(true)
        .cooldownType(CooldownType.GAME_TICK)
        .cooldownResetTick(1000)  // 每天早上7点重置
        .close())
```

---

### 3. 配置对话树的 GAME_TICK 冷却

```java
DialogueTreeBuilder.create("elder_advice")
    .npc("智者")
    .repeatable(true)
    .cooldownType(CooldownType.GAME_TICK)
    .cooldownResetTick(6000)  // 每天中午12点重置
    
    .node("start")
        .say("让我给你一些建议...")
        .choice("聆听", c -> c.goTo("advice"))
    
    .buildAndRegister();
```

---

### 4. 使用时间段条件

```java
import org.com.arc_quest.dialogue.api.DialogueCondition;

.node("time_based_greeting")
    .sayIf(
        Map.of(
            "morning", "早上好！新的一天开始了。",
            "afternoon", "下午好！工作顺利吗？",
            "night", "晚上好！注意安全。"
        ),
        // 根据时间段显示不同文本
        new DialogueCondition.IsMorning()   // 6:00-12:00
    )
    .sayIf(
        Map.of(
            "afternoon", "下午好！工作顺利吗？",
            "night", "晚上好！注意安全。"
        ),
        new DialogueCondition.IsAfternoon() // 12:00-18:00
    )
    .sayIf(
        Map.of(
            "night", "晚上好！注意安全。"
        ),
        new DialogueCondition.IsNight()     // 18:00-次日6:00（跨天）
    )
```

---

### 5. 自定义时间区间

```java
// 黄昏时段（17:00-19:00）
new DialogueCondition.GameTimeInRange(11000, 13000)

// 深夜时段（23:00-凌晨4:00，跨天）
new DialogueCondition.GameTimeInRange(20000, 4000)

// 工作时间（9:00-17:00）
new DialogueCondition.GameTimeInRange(3000, 11000)
```

---

## 时间段条件

### 内置时间段条件

| 条件类 | 时间范围 | Tick 范围 | 说明 |
|--------|---------|----------|------|
| `IsMorning()` | 6:00-12:00 | 0-6000 | 早晨 |
| `IsAfternoon()` | 12:00-18:00 | 6000-12000 | 下午 |
| `IsNight()` | 18:00-次日6:00 | 12000-0 | 夜晚（跨天） |

---

### GameTimeInRange 详解

```java
/**
 * 检查当前 Minecraft 游戏时间是否在指定区间内。
 * 
 * @param startTick 起始时间刻（包含）
 * @param endTick   结束时间刻（不包含）
 */
record GameTimeInRange(int startTick, int endTick) implements DialogueCondition {
    @Override
    public boolean test(ServerPlayer player, Entity npc) {
        if (npc == null || npc.level() == null) return false;
        
        long worldTime = npc.level().getDayTime() % 24000;
        
        if (startTick < endTick) {
            // 正常区间：startTick <= worldTime < endTick
            return worldTime >= startTick && worldTime < endTick;
        } else {
            // 跨天区间：worldTime >= startTick OR worldTime < endTick
            return worldTime >= startTick || worldTime < endTick;
        }
    }
}
```

---

### 跨天区间示例

```java
// 夜班守卫（22:00-凌晨6:00）
new DialogueCondition.GameTimeInRange(16000, 0)
// 等价于：worldTime >= 16000 OR worldTime < 0
// 即：22:00-24:00 或 0:00-6:00

// 午休时间（12:00-14:00）
new DialogueCondition.GameTimeInRange(6000, 8000)
// 正常区间，无需特殊处理
```

---

## 数据存储与序列化

### IQuestCapability 扩展方法

```java
/**
 * 记录节点访问时的游戏时间刻，用于 GAME_TICK 冷却。
 */
void recordNodeVisitGameTime(String nodeId, long worldGameTime);

/**
 * 获取上次节点访问时的游戏时间刻，-1 = 从未访问。
 */
long getLastNodeVisitGameTime(String nodeId);

/**
 * 记录选项选择时的游戏时间刻。
 */
void recordChoiceSelectionGameTime(String choiceKey, long worldGameTime);

/**
 * 获取上次选项选择时的游戏时间刻，-1 = 从未选择。
 */
long getLastChoiceSelectionGameTime(String choiceKey);

/**
 * 记录对话树开启时的游戏时间刻。
 */
void recordDialogueTimeGameTime(String dialogueId, long worldGameTime);

/**
 * 获取上次对话树开启时的游戏时间刻，-1 = 从未对话。
 */
long getLastDialogueTimeGameTime(String dialogueId);
```

---

### NBT 存储结构

```
playerdata/<UUID>.dat
└── ArcQuest
    ├── NodeVisitGameTime
    │   ├── "daily_hint": 1234567890      // worldGameTime
    │   └── "blacksmith_work": 1234591890
    ├── ChoiceSelectionGameTime
    │   ├── "shop:buy_potion": 1234567890
    │   └── "reward:daily": 1234591890
    └── DialogueGameTime
        ├── "elder_advice": 1234567890
        └── "daily_npc": 1234591890
```

---

### 序列化代码

```java
@Override
public CompoundTag serializeNBT() {
    CompoundTag root = new CompoundTag();
    
    // ... 其他数据 ...
    
    // 序列化游戏时间刻记录
    CompoundTag nodeVisitGameTimeTag = new CompoundTag();
    for (Map.Entry<String, Long> e : nodeVisitGameTime.entrySet()) {
        nodeVisitGameTimeTag.putLong(e.getKey(), e.getValue());
    }
    root.put("NodeVisitGameTime", nodeVisitGameTimeTag);
    
    CompoundTag choiceSelectionGameTimeTag = new CompoundTag();
    for (Map.Entry<String, Long> e : choiceSelectionGameTime.entrySet()) {
        choiceSelectionGameTimeTag.putLong(e.getKey(), e.getValue());
    }
    root.put("ChoiceSelectionGameTime", choiceSelectionGameTimeTag);
    
    CompoundTag dialogueGameTimeTag = new CompoundTag();
    for (Map.Entry<String, Long> e : dialogueGameTime.entrySet()) {
        dialogueGameTimeTag.putLong(e.getKey(), e.getValue());
    }
    root.put("DialogueGameTime", dialogueGameTimeTag);
    
    return root;
}
```

---

## 实现细节

### DialogueSession 中的冷却检查

```java
/**
 * 检查自上次访问后是否经过了指定的重置时间刻，用于 GAME_TICK 冷却。
 */
private boolean hasPassedResetTick(ServerPlayer player, String nodeId, int resetTick) {
    if (player.level() == null) return true;
    
    var cap = player.getCapability(QuestCapabilityProvider.QUEST_CAP).orElse(null);
    if (cap == null) return true;
    
    long lastGameTime = cap.getLastNodeVisitGameTime(nodeId);
    if (lastGameTime < 0) return true;  // 从未访问过
    
    long currentTotalGameTime = player.level().getGameTime();
    long currentDayTime = currentTotalGameTime % 24000;
    long lastDayTime = lastGameTime % 24000;
    
    // 计算经过了多少天
    long daysElapsed = (currentTotalGameTime - lastGameTime) / 24000;
    
    if (daysElapsed > 0) {
        return true;  // 已过至少一天
    }
    
    // 同一天内，检查是否跨过了重置点
    if (lastDayTime <= resetTick && currentDayTime >= resetTick) {
        return true;
    }
    
    return false;
}
```

---

### 记录游戏时间刻

```java
// 在节点访问时记录
public void advanceTo(String nodeId, ServerPlayer player) {
    // ... 其他逻辑 ...
    
    // 记录访问时间（现实时间）
    long currentTime = System.currentTimeMillis();
    cap.recordNodeVisit(nodeId, currentTime);
    
    // 记录游戏时间刻 ⭐
    if (player.level() != null) {
        cap.recordNodeVisitGameTime(nodeId, player.level().getGameTime());
    }
    
    // ... 其他逻辑 ...
}
```

---

## 最佳实践

### 1. 选择合适的重置时间点

```java
// ✅ 推荐：整点重置，便于记忆
.cooldownResetTick(0)      // 6:00
.cooldownResetTick(1000)   // 7:00
.cooldownResetTick(6000)   // 12:00

// ❌ 避免：奇怪的时间点
.cooldownResetTick(1237)   // 难以记忆
```

---

### 2. 配合时间段条件使用

```java
// ✅ 推荐：冷却 + 时间段双重控制
.node("night_guard")
    .sayIf(
        Map.of("night", "夜晚危险，小心行事。"),
        new DialogueCondition.IsNight()
    )
    .repeatable(true)
    .cooldownType(CooldownType.GAME_TICK)
    .cooldownResetTick(12000)  // 每天18:00重置
```

---

### 3. 考虑玩家体验

```java
// ✅ 友好：给予足够的时间窗口
.cooldownResetTick(0)  // 早上6点，玩家刚上线

// ❌ 不友好：重置时间在深夜
.cooldownResetTick(18000)  // 午夜12点，大多数玩家在睡觉
```

---

### 4. 文档化时间设置

```java
// ✅ 清晰注释
.cooldownResetTick(1000)  // 早上7:00重置（/time set day 默认值+1小时）

// ❌ 缺少说明
.cooldownResetTick(1000)
```

---

## 常见问题

### Q1: GAME_TICK 和 GAME_DAY 有什么区别？

**A**: 
- `GAME_DAY`: 按完整的天数计算，不考虑具体时间点
- `GAME_TICK`: 精确到 tick，支持在每天的特定时刻重置

**示例**:
```java
// GAME_DAY: 只要过了一天就重置，不管几点
.cooldownType(CooldownType.GAME_DAY)
// 昨天6:00访问 → 今天6:01访问 ✅ 已重置
// 昨天23:00访问 → 今天0:01访问 ✅ 已重置

// GAME_TICK: 必须跨过重置点才重置
.cooldownType(CooldownType.GAME_TICK)
.cooldownResetTick(0)  // 6:00重置
// 昨天6:00访问 → 今天5:59访问 ❌ 未重置
// 昨天6:00访问 → 今天6:01访问 ✅ 已重置
```

---

### Q2: 如果服务器 TPS 不稳定怎么办？

**A**: `Level.getGameTime()` 返回的是世界总运行 tick 数，不受 TPS 影响。即使服务器卡顿，游戏时间刻仍然准确累计。

---

### Q3: 玩家离线期间时间如何计算？

**A**: 游戏时间刻是世界级别的，玩家离线期间世界仍在运行（除非服务器关闭）。重新登录时，`getGameTime()` 会返回当前的世界总 tick 数，自动计算经过的时间。

---

### Q4: 如何处理跨天区间？

**A**: 当 `startTick > endTick` 时，系统自动识别为跨天区间：

```java
// 夜晚：18:00-次日6:00
new DialogueCondition.GameTimeInRange(12000, 0)
// 内部逻辑：worldTime >= 12000 OR worldTime < 0
```

---

### Q5: 能否同时使用多种冷却类型？

**A**: 可以！不同的节点/选项可以使用不同的冷却类型：

```java
.node("daily_task")
    .cooldownType(CooldownType.GAME_TICK)
    .cooldownResetTick(0)

.node("hourly_hint")
    .cooldownType(CooldownType.SECONDS)
    .cooldownSeconds(3600)
```

---

### Q6: 如何调试冷却状态？

**A**: 使用管理员命令查看玩家的冷却数据：

```bash
# 查看玩家的任务数据（包含游戏时间刻）
/arcquest data <player>

# 强制重置所有冷却
/arcquest resetall <player>
```

---

## 附录：时间刻速查表

| Tick | 游戏时间 | 常见用途 |
|------|---------|---------|
| 0 | 6:00 | 日出、一天开始 |
| 1000 | 7:00 | `/time set day` + 1小时 |
| 3000 | 9:00 | 工作时间开始 |
| 6000 | 12:00 | 正午 |
| 8000 | 14:00 | 午休结束 |
| 11000 | 17:00 | 黄昏开始 |
| 12000 | 18:00 | 日落、夜晚开始 |
| 13000 | 19:00 | `/time set night` |
| 16000 | 22:00 | 深夜 |
| 18000 | 24:00 | 午夜 |
| 23999 | 5:59 | 一天结束 |

---

**文档结束**

*本指南详细介绍了游戏时间刻冷却系统的原理、使用方法和最佳实践。*
