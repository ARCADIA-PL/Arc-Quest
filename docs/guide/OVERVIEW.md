# Arc Quest 项目概述

**版本**: 1.0.0  
**Minecraft**: 1.20.1 Forge  
**Java**: 17  
**最后更新**: 2026-04-20

---

## 📋 快速开始

### 项目简介

Arc Quest 是一个纯代码驱动的 Minecraft 任务、对话与交易系统模组。它彻底摒弃了繁琐的 JSON 配置文件，完全采用 Java Builder API 来定义游戏内的任务流、NPC 对话树以及自定义商店系统。同时，模组自带强大的视觉渲染（立绘、图标）、多语言支持和网络同步机制，非常适合用于大型 RPG 整合包的底层框架开发。

### 核心特性

- ✅ **纯代码驱动** - 零 JSON 配置，任务、对话、交易全部通过链式调用的 Java Builder API 定义，支持强类型检查与重构。
- ✅ **三大核心系统**：
  - **任务系统 (Quest System)**：支持多阶段、多目标、条件解锁、变量追踪与奖励发放。
  - **对话系统 (Dialogue System)**：基于节点的树状结构，支持 NPC 绑定、选项分支、冷却时间、动作回调及条件分支。
  - **交易系统 (Trade System)**：高度自定义的商店与交易项，支持多货币、最大购买次数限制、分类管理与动态解锁。
- ✅ **视觉与 UI 渲染** - 内置动态立绘动画（基于配置的淡入淡出、平移）、HUD 任务追踪面板、以及现代化的屏幕中央横幅提示（Toast & Splash）。
- ✅ **状态存储与网络同步** - 基于 Forge Capability 和 Network 包，实现客户端与服务端的任务进度、标志位（Flags）及变量的增量/全量实时同步。
- ✅ **数据生成 (DataGen)** - 自动生成中英文翻译文件，无需手动编写 lang JSON。

---

## 🚀 5分钟快速上手

下面展示如何使用代码创建一个简单的流程：**与 NPC 对话 -> 接受任务 -> 在商店购买所需物品**。

### 1. 创建任务 (Quest)

```java
import org.com.arc_quest.quest.builder.*;

QuestBuilder.create("my_first_quest")
    .category(QuestCategory.ADVENTURE)
    .displayName(Component.translatable("quest.my_first.title"))
    .description(Component.translatable("quest.my_first.desc"))
    // 添加第一阶段
    .phase(PhaseBuilder.create("step1")
        .displayName(Component.literal("准备物资"))
        .objective(ObjectiveBuilder.collect(Items.OAK_LOG, 5)
            .display(Component.literal("收集5个橡木原木")))
        .thenGoTo("step2") // 完成后跳转到第二阶段
        .build())
    // 添加第二阶段
    .phase(PhaseBuilder.create("step2")
        .displayName(Component.literal("实战演练"))
        .objective(ObjectiveBuilder.kill(EntityType.ZOMBIE, 3)
            .display(Component.literal("击杀3只僵尸")))
        .build())
    // 任务完成奖励
    .reward(new ItemReward(Items.DIAMOND, 1))
    .buildAndRegister();
```

### 2. 创建对话树 (Dialogue)

```java
import org.com.arc_quest.dialogue.builder.*;

DialogueTreeBuilder.create("villager_greeting")
    .defaultNpc("新手向导")
    .startNode("start")
        .say("你好，冒险者！准备好开始你的旅程了吗？")
        .choice("接受任务", context -> context.startQuest("arc_quest:my_first_quest").close())
        .choice("离开", context -> context.close())
    .buildAndRegister();
```

### 3. 创建交易商店 (Trade)

```java
import org.com.arc_quest.trade.builder.*;

TradeShopBuilder.create("beginner_shop")
    .displayName("新手商店")
    .description("出售基础物资")
    .category("materials", "基础材料")
    .entry(TradeEntryBuilder.create("buy_oak_log")
        .displayName("购买橡木原木")
        .costItem(Items.EMERALD, 1)        // 花费 1 个绿宝石
        .rewardItem(Items.OAK_LOG, 5)      // 获得 5 个橡木原木
        .maxPurchases(10)                  // 限制购买 10 次
        .build())
    .buildAndRegister();
```

### 4. 运行游戏测试

运行客户端：
```bash
./gradlew runClient
```

游戏内可用管理员命令快速测试：
```bash
# 给予任务
/arcquest quest give @p arc_quest:my_first_quest
# 强制完成任务
/arcquest quest complete @p arc_quest:my_first_quest
# 唤出对话界面（需绑定实体或通过命令测试）
/arcquest dialogue open @p arc_quest:villager_greeting
```

---

## � 模块架构总览

整个项目按照高内聚、低耦合的设计原则划分：

```text
org.com.arc_quest/
├── client/          # 客户端专属：事件监听、GUI渲染（屏幕、Toast、HUD、立绘）
├── command/         # 调试与管理：管理员命令注册
├── data/            # 数据生成：Language Providers (中/英)
├── dialogue/        # 对话系统：树结构定义、运行时会话、NPC扩展接口
├── npc/             # 实体交互：NPC右键事件拦截分发
├── quest/           # 任务系统（核心）：生命周期逻辑、条件/奖励、玩家数据存储(Capability)
├── trade/           # 交易系统：商店与商品定义、多货币兑换逻辑、交易界面同步
└── Arc_quest.java   # 主类：模组入口与注册事件总线
```

欲深入了解系统底层原理，请参阅 [核心架构设计](ARCHITECTURE.md)。
