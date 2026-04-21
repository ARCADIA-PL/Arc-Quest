# Arc Quest 完整技术文档

> **版本**: 1.0.0  
> **平台**: Minecraft Forge 1.20.1  
> **Java**: 17+  
> **最后更新**: 2026-04-22  
> **文档类型**: 完整 API 参考与开发指南
>
> **最近更新**：
> - ✅ Say/Choice 强制 ID 绑定（支持智能命名空间解析）
> - ✅ 对话事件系统完善（DialogueNodeStartedEvent 添加 sayId）
> - ✅ ClientDialogueCache/ClientTradeCache 统一管理
> - ✅ 个体化音效配置（每个 SayIf/Choice 独立音效）

---

## 📑 目录

### Part 1: 快速开始
- [1. 项目概述](#1-项目概述)
- [2. 环境搭建](#2-环境搭建)
- [3. Lib 模组集成](#3-lib-模组集成)

### Part 2: 任务系统
- [4. 任务定义与注册](#4-任务定义与注册)
- [5. 阶段与目标系统](#5-阶段与目标系统)
- [6. 条件判断系统](#6-条件判断系统)
- [7. 奖励发放机制](#7-奖励发放机制)
- [8. 任务事件监听](#8-任务事件监听)

### Part 3: 对话系统
- [9. 对话树构建](#9-对话树构建)
- [10. 节点与选项](#10-节点与选项)
- [10.5 Say/Choice 强制 ID 规范](#105-saychoice-强制-id-规范)
- [11. 动作系统](#11-动作系统)
- [11.5 个体化音效配置](#115-个体化音效配置)
- [12. NPC 扩展机制](#12-npc-扩展机制)
- [13. 对话事件监听](#13-对话事件监听)

### Part 4: 交易系统
- [14. 商店定义](#14-商店定义)
- [15. 商品条目配置](#15-商品条目配置)
- [16. 冷却与限购系统](#16-冷却与限购系统)
- [17. 交易事件监听](#17-交易事件监听)

### Part 5: 统一 API
- [18. ArcQuestAPI 入口](#18-arcquestapi-入口)
- [19. 事件系统总览](#19-事件系统总览)

### Part 6: 管理员工具
- [20. 命令系统](#20-命令系统)

### Part 7: 高级主题
- [21. 网络通信](#21-网络通信)
- [22. 数据持久化](#22-数据持久化)
- [23. 性能优化](#23-性能优化)
- [24. 最佳实践](#24-最佳实践)

### Part 7: 附录
- [A. API 速查表](#a-api-速查表)
- [B. 常见问题](#b-常见问题)
- [C. 完整示例项目](#c-完整示例项目)
- [D. 智能命名空间详解](#d-智能命名空间详解)

---

## 1. 项目概述

### 1.1 核心特性

Arc Quest 是一个**纯代码驱动**的 Minecraft RPG 模组，提供完整的任务、对话和交易系统。

**关键优势**：
- ✅ **O(1) 目标追踪**: 哈希索引替代遍历，性能提升 10-250 倍
- ✅ **增量网络同步**: 仅发送变化数据，减少 90% 带宽占用
- ✅ **统一冷却管理**: 支持 GAME_TICK/GAME_DAY/REAL_TIME 三种模式
- ✅ **服务端权威**: 所有业务逻辑二次验证，防止作弊
- ✅ **Lib 模组架构**: 完善的 API 和事件系统，易于扩展

### 1.2 模块结构

```
org.com.arc_quest/
├── api/                    # 公共 API（附属模组使用）
│   ├── ArcQuestAPI.java   # 统一入口
│   └── event/             # 事件类
├── quest/                  # 任务系统 (40%)
│   ├── api/               # 任务 API
│   ├── builder/           # Builder 模式
│   ├── registry/          # 注册表
│   ├── logic/             # 业务逻辑
│   ├── network/           # 网络包
│   └── capability/        # 数据存储
├── dialogue/               # 对话系统 (30%)
│   ├── api/               # 对话 API
│   ├── builder/           # 对话树构建器
│   ├── runtime/           # 运行时管理
│   └── extension/         # NPC 扩展
├── trade/                  # 交易系统 (20%)
│   ├── api/               # 交易 API
│   ├── builder/           # 商店构建器
│   └── runtime/           # 会话管理
└── client/                 # 客户端 UI (10%)
    ├── gui/               # 界面类
    └── render/            # 渲染工具
```

### 1.3 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Minecraft | 1.20.1 | 游戏版本 |
| Forge | 47.4.20 | 模组加载器 |
| Java | 17+ | 编程语言 |
| Mixin | 0.8.5 | 字节码注入 |
| Gradle | 8.8 | 构建工具 |

---

## 2. 环境搭建

### 2.1 前置要求

```bash
# 检查 Java 版本（需要 17+）
java -version

# 检查 Gradle（需要 8.8+）
gradle --version
```

### 2.2 导入项目

```bash
# 克隆仓库
git clone <repository-url>
cd "Arc Quest"

# 生成 IDE 配置
./gradlew genIntellijRuns  # IntelliJ IDEA
./gradlew eclipse          # Eclipse

# 编译项目
./gradlew build

# 运行客户端
./gradlew runClient

# 运行服务端
./gradlew runServer
```

### 2.3 调试配置

**启用详细日志**（`log4j2.xml`）：
```xml
<Logger name="org.com.arc_quest" level="DEBUG"/>
```

**常用调试命令**：
```bash
# 查看任务状态
/arcquest quest debug @p arc_quest:test

# 查看任务列表
/arcquest quest list @p

# 手动推进进度
/arcquest quest progress @p arc_quest:test 0 5

# 给予任务
/arcquest quest give @p arc_quest:test

# 完成任务
/arcquest quest complete @p arc_quest:test

# 重置所有数据
/arcquest admin registry  # 查看所有注册内容
```

---

## 3. Lib 模组集成

### 3.1 依赖配置

在附属模组的 `mods.toml` 中添加：

```toml
[[dependencies."my_addon"]]
modId = "arc_quest"
mandatory = false  # 可选依赖
versionRange = "[1.0,)"
ordering = "AFTER"  # 在 Arc Quest 之后加载
side = "BOTH"
```

### 3.2 条件加载

```java
@Mod("my_addon")
public class MyAddon {
    
    public MyAddon() {
        if (ModList.get().isLoaded("arc_quest")) {
            // 安全地使用 Arc Quest API
            initializeArcQuestIntegration();
        } else {
            LOGGER.warn("Arc Quest not found, skipping integration");
        }
    }
    
    private void initializeArcQuestIntegration() {
        FMLJavaModLoadingContext.get().getModEventBus()
            .addListener(this::onCommonSetup);
    }
}
```

### 3.3 快速示例

```java
private void onCommonSetup(FMLCommonSetupEvent event) {
    event.enqueueWork(() -> {
        // 1. 注册任务（使用智能命名空间）
        ArcQuestAPI.registerQuest(
            QuestBuilder.create("dragon_slayer")  // 自动补全为 "arc_quest:dragon_slayer"
                .displayName(Component.literal("屠龙勇士"))
                .phase(PhaseBuilder.create("hunt")
                    .objective(ObjectiveBuilder.kill(EntityType.ENDER_DRAGON, 1)))
                .build()
        );
        
        // 2. 注册对话（显式指定命名空间）
        ArcQuestAPI.registerDialogueTree(
            DialogueTreeBuilder.create("my_addon:dragon_npc")  // 使用附属模组命名空间
                .npc("龙猎人")
                .node("start", NodeBuilder.create()
                    .text("准备好屠龙了吗？")
                    .choice("接受任务", c -> c.startQuest("my_addon:dragon_slayer")))
                .build()
        );
        
        // 3. 注册商店（混合使用）
        ArcQuestAPI.registerTradeShop(
            TradeShopBuilder.create("dragon_shop")  // 自动补全为 "arc_quest:dragon_shop"
                .displayName(Component.literal("龙族宝库"))
                .entry(TradeEntryBuilder.create("dragon_scale")
                    .costItem(Items.DIAMOND, 10)
                    .rewardItem(Items.DRAGON_BREATH, 1))
                .build()
        );
    });
}
```

**智能命名空间解析规则**：
- ✅ `create("id")` → 自动补全为 `"arc_quest:id"`
- ✅ `create("mod:id")` → 直接使用 `"mod:id"`
- ✅ 适用于所有三个系统：任务、对话、商店
- ⚠️ **局部 ID**（节点、条目、分类）**不需要**命名空间

---

## 4. 任务定义与注册

### 4.1 基础任务

```java
QuestDefinition quest = QuestBuilder.create("fetch_quest")
    .displayName(Component.translatable("quest.fetch_quest.name"))
    .description(Component.translatable("quest.fetch_quest.desc"))
    .category(QuestCategory.ADVENTURE)
    .icon(new ResourceLocation("my_mod:textures/gui/quest_icon.png"))
    .sortOrder(100)
    .repeatable(false)
    .buildAndRegister();
```

**参数说明**：
- `create(String id)`: **智能命名空间解析**
  - 如果包含 `:`（如 `"my_mod:quest"`），直接使用
  - 如果不包含 `:`（如 `"quest"`），自动添加 `arc_quest:` 前缀 → `"arc_quest:quest"`
- `create(ResourceLocation id)`: 使用完整资源位置（推荐）
- `displayName`: 任务显示名称（支持翻译键）
- `description`: 任务描述
- `category`: 任务分类（ARCHON/COMPANION/DAILY/ADVENTURE/EVENT）
- `icon`: 图标纹理路径（可选）
- `sortOrder`: 排序权重（越小越靠前）
- `repeatable`: 是否可重复完成
- `buildAndRegister()`: 构建并注册到系统

### 4.2 带前置条件的任务

```java
QuestDefinition quest = QuestBuilder.create("advanced_quest")
    .displayName(Component.literal("高级任务"))
    .unlockCondition(Conditions.questCompleted("basic_quest"))
    .phase(PhaseBuilder.create("start")
        .objective(ObjectiveBuilder.collect(Items.DIAMOND, 10)
            .display("收集 10 个钻石")))
    .buildAndRegister();
```

**注意**：
- `.unlockCondition()`: 设置解锁条件
- `ObjectiveBuilder.collect(Item, count)`: 创建收集目标
- `.display(String)`: 自定义显示文本

### 4.3 多阶段任务

```java
QuestDefinition quest = QuestBuilder.create("epic_quest")
    .displayName(Component.literal("史诗任务"))
    
    // 第一阶段：收集材料
    .phase(PhaseBuilder.create("gather")
        .displayName(Component.literal("收集材料"))
        .objective(ObjectiveBuilder.collect(Items.OAK_LOG, 20)
            .display("收集 20 个橡木原木"))
        .objective(ObjectiveBuilder.collect(Items.COBBLESTONE, 10)
            .display("收集 10 个圆石"))
        .thenGoToIf("craft", Conditions.allOf(
            Conditions.flagSet("wood_collected"),
            Conditions.flagSet("stone_collected"))))
    
    // 第二阶段：制作物品
    .phase(PhaseBuilder.create("craft")
        .displayName(Component.literal("制作工具"))
        .objective(ObjectiveBuilder.custom(
            new ResourceLocation("my_mod:craft_sword"), 1)
            .display("制作一把钻石剑"))
        .thenGoTo("complete"))
    
    .startAt("gather")  // 显式指定起始阶段
    .buildAndRegister();
```

**关键 API**：
- `.phase(PhaseBuilder)`: 添加阶段（自动 build）
- `.thenGoTo(String)`: 无条件跳转
- `.thenGoToIf(String, ICondition)`: 条件跳转
- `.startAt(String)`: 显式指定起始阶段

---

## 5. 阶段与目标系统

### 5.1 目标类型

| 类型 | 说明 | 示例 |
|------|------|------|
| `KILL` | 击杀实体 | 击杀 5 只僵尸 |
| `COLLECT` | 收集物品 | 收集 10 个钻石 |
| `TALK` | 与 NPC 对话 | 与村民交谈 |
| `INTERACT` | 右键交互 | 与方块/实体交互 |
| `REACH_LOCATION` | 到达位置 | 到达村庄 |
| `DELIVER` | 提交物品 | 上交物品给 NPC |
| `CRAFT` | 制作物品 | 制作铁剑 |
| `CUSTOM` | 自定义条件 | 编程实现 |

### 5.2 目标配置

```java
PhaseDefinition phase = PhaseBuilder.create("combat_training")
    .displayName(Component.literal("战斗训练"))
    
    // 目标 1：击杀怪物
    .objective(ObjectiveBuilder.kill(EntityType.ZOMBIE, 10)
        .display("击杀 10 只僵尸"))
    
    // 目标 2：收集战利品
    .objective(ObjectiveBuilder.collect(Items.ROTTEN_FLESH, 5)
        .display("收集 5 个腐肉"))
    
    // 阶段奖励
    .reward(reward -> reward
        .item(Items.IRON_SWORD, 1)
        .xp(50)
        .flag("completed_training"))
    
    .build();
```

**ObjectiveBuilder 静态工厂方法**：
- `kill(EntityType, count)`: 击杀目标
- `collect(Item, count)`: 收集物品
- `talk(ResourceLocation npcId)`: 与 NPC 对话
- `deliver(Item, count, npcId)`: 提交物品
- `reachLocation(id, x, y, z, radius)`: 抵达位置
- `interact(ResourceLocation targetId)`: 右键交互
- `custom(ResourceLocation id, count)`: 自定义目标

### 5.3 阶段转换

```java
// 自动转换（无条件）
.transition("next_phase")

// 条件转换
.transition("next_phase", Conditions.flagSet("unlocked"))

// 多分支转换
.transition("branch_a", Conditions.variableInRange("choice", 1, 1))
.transition("branch_b", Conditions.variableInRange("choice", 2, 2))
```

---

## 6. 条件判断系统

### 6.1 内置条件

```java
// 任务完成
Conditions.questCompleted("my_mod:prev_quest")

// Flag 检查
Conditions.flagSet("unlocked_area")
Conditions.flagNotSet("failed_mission")

// 变量范围
Conditions.variableInRange("reputation", 10, 100)
Conditions.variableEquals("level", 5)

// 组合条件
Conditions.allOf(
    Conditions.questCompleted("quest_a"),
    Conditions.flagSet("flag_b")
)

Conditions.anyOf(
    Conditions.variableGTE("strength", 10),
    Conditions.variableGTE("intelligence", 10)
)

Conditions.not(Conditions.flagSet("blocked"))
```

### 6.2 自定义条件

```java
.visibleCondition((player, completedQuests, flags, variables) -> {
    if (player == null) return false;  // 客户端保护
    
    // 检查玩家等级
    if (player.experienceLevel < 10) return false;
    
    // 检查是否在夜晚
    if (!player.level().isNight()) return false;
    
    // 检查自定义变量
    int reputation = variables.getOrDefault("reputation", 0);
    return reputation >= 50;
})
```

**参数说明**：
- `player`: 服务端玩家对象（客户端为 null）
- `completedQuests`: 已完成任务集合
- `flags`: 标记集合
- `variables`: 变量映射

---

## 7. 奖励发放机制

### 7.1 内置奖励

```java
QuestBuilder.create("quest_id")
    // ...
    .reward(reward -> reward
        // 物品奖励
        .item(Items.DIAMOND, 5)
        .itemStack(ItemStack.of(Items.NETHERITE_INGOT))
        
        // 经验值
        .xp(100)
        .levels(2)
        
        // 命令执行
        .command("/give {player} emerald 10")
        .command("/effect give {player} strength 60 1")
        
        // Flag 设置
        .flag("quest_completed")
        .flag("unlocked_next_area")
        
        // 变量修改
        .variable("reputation", 10)
        .variableAdd("kills", 5))
    .buildAndRegister();
```

**注意**：使用 `.reward(IReward)` 而非 `.onComplete()`

### 7.2 自定义奖励

```java
QuestBuilder.create("quest_id")
    // ...
    .reward(reward -> reward.custom(player -> {
        // 播放音效
        player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
        
        // 给予特殊效果
        player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 3600));
        
        // 发送消息
        player.sendSystemMessage(Component.literal("§6恭喜你完成了史诗任务！"));
    }))
    .buildAndRegister();
```


### 7.3 容错处理

奖励系统会自动捕获异常，确保部分失败不影响其他奖励：

```java
try {
    reward.grant(player);
} catch (Exception e) {
    LOGGER.error("Reward failed: {}", e.getMessage(), e);
    // 继续发放其他奖励
}
```

---

## 8. 任务事件监听

### 8.1 可用事件

| 事件类 | 触发时机 | 用途 |
|--------|---------|------|
| `QuestAcceptedEvent` | 玩家接受任务 | 监听任务开始 |
| `QuestCompletedEvent` | 玩家完成任务 | 监听任务完成 |
| `QuestPhaseChangedEvent` | 任务阶段变更 | 监听进度变化 |

### 8.2 事件监听示例

```java
@Mod.EventBusSubscriber(modid = "my_addon", bus = Bus.FORGE)
public class QuestEventHandler {
    
    @SubscribeEvent
    public static void onQuestAccepted(QuestAcceptedEvent event) {
        ServerPlayer player = event.getPlayer();
        ResourceLocation questId = event.getQuestId();
        
        LOGGER.info("Player {} accepted quest: {}", 
            player.getName().getString(), questId);
        
        // 播放接受任务的音效
        player.playSound(SoundEvents.ANVIL_USE, 1.0f, 1.0f);
    }
    
    @SubscribeEvent
    public static void onQuestCompleted(QuestCompletedEvent event) {
        ServerPlayer player = event.getPlayer();
        ResourceLocation questId = event.getQuestId();
        
        // 检查是否是特定任务
        if (questId.equals(new ResourceLocation("my_mod:epic_finale"))) {
            // 给予特殊奖励
            player.giveItemStack(new ItemStack(Items.NETHER_STAR));
            
            // 全服公告
            player.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("§6玩家 " + player.getName().getString() + 
                                " §6完成了史诗终章！"), false);
        }
    }
    
    @SubscribeEvent
    public static void onPhaseChanged(QuestPhaseChangedEvent event) {
        ServerPlayer player = event.getPlayer();
        String oldPhase = event.getOldPhaseId();
        String newPhase = event.getNewPhaseId();
        
        LOGGER.info("Quest {} phase changed: {} → {}", 
            event.getQuestId(), oldPhase, newPhase);
        
        // 显示 Toast 通知
        player.sendSystemMessage(Component.literal(
            "§a任务进展：进入阶段 " + newPhase));
    }
}
```

---

## 9. 对话树构建

### 9.1 基础对话树

```java
DialogueTree tree = DialogueTreeBuilder.create("villager_greeting")
    .npc("村民")
    .repeatable(true)
    .cooldown(3600)  // 1 小时冷却（SECONDS 类型）
    
    .node("start")
        .say("你好，旅行者！", "greeting_default")  // ✅ 强制要求 ID
        .choice("再见", "choice_bye", c -> c.close())  // ✅ 强制要求 ID
        .choice("有任务吗？", "choice_quest", c -> c.goTo("quest_offer"))
    
    .node("quest_offer")
        .say("最近村庄附近有怪物出没...", "quest_offer_text")
        .choice("我来帮忙！", "choice_accept", c -> c
            .startQuest("monster_hunt")
            .close())
        .choice("下次吧", "choice_decline", c -> c.close())
    
    .buildAndRegister();
```

**重要变化（2026-04-22）**：
- ⚠️ **Say/Choice 必须带 ID** - 所有 `say()` 和 `choice()` 方法强制要求 ID 参数
- 🎯 **智能命名空间解析** - ID 会自动补全 `arc_quest:` 前缀（如果未包含 `:`）
- 🔒 **编译期保证** - 通过紧凑构造函数验证 ID 非空
- 🌐 **附属模组支持** - 可使用自定义命名空间（如 `my_mod:custom_id`）

**注意**：
- `create(String dialogueId)`: **智能命名空间解析**
  - 如果包含 `:`（如 `"my_mod:dialogue"`），直接使用
  - 如果不包含 `:`（如 `"dialogue"`），自动添加 `arc_quest:` 前缀 → `"arc_quest:dialogue"`
- `.node(String nodeId)`: 进入节点配置模式（节点 ID 是局部的，无需命名空间）
- `.say(String text, String sayId)`: **必须提供 ID**（旧版无参 `text()` 已移除）
- `.choice(String label, String choiceId, Consumer<ChoiceBuilder> action)`: **必须提供 ID**
- `.buildAndRegister()`: 构建并自动注册

### 9.2 条件文本（SayIf）

```java
.node("greeting")
    // 首次见面
    .sayIf(Conditions.not(Conditions.flagSet("met_before")),
        "你好，我是新来的村民！",
        "greeting_first_time")  // ✅ SayIf 分支 ID
    
    // 再次见面
    .sayIf(Conditions.flagSet("met_before"),
        "又见面了！",
        "greeting_return")
    
    // 完成任务后
    .sayIf(Conditions.questCompleted("first_quest"),
        "感谢你的帮助！",
        "greeting_completed")
    
    // 默认文本
    .say("今天天气不错。", "greeting_default")
```

**SayIf 工作原理**：
1. **服务端权威评估** - 所有条件在服务端判断
2. **第一个匹配生效** - 按顺序检查，第一个满足条件的分支被选中
3. **必须提供默认 say()** - 作为回退方案
4. **每个分支独立 ID** - 用于事件监听和网络同步

### 9.3 动态文本

```java
.node("status")
    .text((ctx) -> {
        int reputation = ctx.getVariable("reputation", 0);
        if (reputation >= 100) {
            return "你是我们的英雄！";
        } else if (reputation >= 50) {
            return "你是个值得信赖的朋友。";
        } else {
            return "我们还不算太熟。";
        }
    })
```

---

## 10. 节点与选项

### 10.1 选项配置

```java
.choice("接受任务", c -> c
    .startQuest("quest_id")
    .goTo("accepted_node"))

.choice("拒绝", c -> c
    .goTo("declined_node"))

.choice("打开商店", c -> c
    .openTrade("my_shop")
    .restoreToCurrentNode())  // 商店关闭后恢复对话

.choice("给予物品", c -> c
    .consumeItem(Items.DIAMOND, 1)
    .giveItem(Items.EMERALD, 5)
    .close())
```

**注意**：`.choice(String label, Consumer<ChoiceBuilder> action)` 是标准用法

### 10.2 条件选项

```java
.choiceIf(
    Conditions.flagSet("unlocked_premium"),
    "购买VIP服务",
    c -> c.openTrade("vip_shop"))

.choiceIf(
    Conditions.variableGTE("reputation", 50),
    "请求帮助（需要声望≥50）",
    c -> c.goTo("help_request"))
```

### 10.3 选项冷却

```java
.choice("获取提示", c -> {
    c.cooldown(3600);  // 1 小时冷却
    c.goTo("hint_node");
})

.choice("每日奖励", c -> {
    c.cooldownGameDay();  // 每天重置
    c.giveItem(Items.BREAD, 5);
    c.close();
})
```

---

## 10.5 Say/Choice 强制 ID 规范

### 10.5.1 设计理念

**为什么需要强制 ID？**
1. **事件监听** - 附属模组可以精确监听特定分支
2. **网络同步** - 客户端缓存 Say/Choice ID，用于 UI 渲染和调试
3. **语义化标识** - 比索引更稳定，支持重构和国际化
4. **编译期安全** - 避免遗漏 ID 导致的运行时错误

### 10.5.2 API 签名变化

**旧版（已移除）**：
```java
// ❌ 不再支持
.text("Hello!")
.choice("Bye", c -> c.close())
```

**新版（强制 ID）**：
```java
// ✅ 必须提供 ID
.say("Hello!", "greeting_hello")
.choice("Bye", "choice_bye", c -> c.close())
```

### 10.5.3 智能命名空间解析

```java
// 场景1：主模组内部（自动补全）
.say("Hello!", "greeting")  
// → 解析为 "arc_quest:greeting"

// 场景2：显式指定（直接使用）
.say("Hello!", "arc_quest:greeting")  
// → 解析为 "arc_quest:greeting"

// 场景3：附属模组（自定义命名空间）
.say("Hello!", "my_mod:custom_greeting")  
// → 解析为 "my_mod:custom_greeting"
```

**解析规则**：
- 如果 ID 包含 `:` → 直接使用
- 如果 ID 不包含 `:` → 自动添加 `arc_quest:` 前缀

### 10.5.4 完整示例

```java
DialogueTreeBuilder.create("village_elder")
    .npc("村长")
    
    .node("start")
        // SayIf 条件分支（每个分支独立 ID）
        .sayIf(Conditions.not(Conditions.flagSet("met_before")),
            "你好，我是新来的村长！",
            "greeting_first_time")  // ✅ 首次见面
        
        .sayIf(Conditions.flagSet("met_before"),
            "又见面了，冒险者！",
            "greeting_return")  // ✅ 再次见面
        
        // 默认文本
        .say("今天天气不错。", "greeting_default")  // ✅ 回退方案
        
        // Choice 选项（每个选项独立 ID）
        .choice("接受任务", "choice_accept_quest",
            c -> c.startQuest("village_defense").goTo("accepted"))
        
        .choiceIf(
            Conditions.variableGTE("reputation", 50),
            "请求帮助（声望≥50）",
            "choice_request_help",  // ✅ 条件选项 ID
            c -> c.goTo("help_menu"))
        
        .choice("再见", "choice_bye", c -> c.close())
    
    .buildAndRegister();
```

### 10.5.5 事件监听示例

**监听 SayIf 分支选择**：
```java
@SubscribeEvent
public static void onNodeStarted(DialogueNodeStartedEvent event) {
    ServerPlayer player = event.getPlayer();
    String sayId = event.getSayId();  // ✅ 保证非空
    
    // 根据 Say ID 执行自定义逻辑
    if (sayId.equals("arc_quest:greeting_first_time")) {
        player.sendSystemMessage(Component.literal("§6首次见面触发特殊对话！"));
    }
}
```

**监听 Choice 选择**：
```java
@SubscribeEvent
public static void onChoiceSelected(DialogueChoiceSelectedEvent event) {
    ServerPlayer player = event.getPlayer();
    String choiceId = event.getChoiceId();  // ✅ 保证非空
    
    if (choiceId.equals("arc_quest:choice_accept_quest")) {
        // 玩家接受了任务
        player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
    }
}
```

### 10.5.6 最佳实践

#### ✅ 推荐做法
```java
// 1. 使用语义化 ID
.say("欢迎来到村庄！", "welcome_message")
.choice("浏览商店", "choice_open_shop", c -> c.openTrade("shop"))

// 2. SayIf 分支 ID 保持一致性
.sayIf(condition1, "Text A", "branch_option_a")
.sayIf(condition2, "Text B", "branch_option_b")
.say("Default", "branch_default")

// 3. 附属模组使用自己的命名空间
.say("Custom text", "my_mod:custom_say")
.choice("Custom choice", "my_mod:custom_choice", c -> c.close())
```

#### ❌ 避免做法
```java
// 1. 不要使用无意义的 ID
.say("Hello", "id1")  // ❌ 不清晰

// 2. 不要重复 ID（在同一个节点内）
.say("Text A", "same_id")
.say("Text B", "same_id")  // ❌ 冲突！

// 3. 不要给局部资源添加命名空间
.node("arc_quest:start")  // ❌ 节点 ID 是局部的，无需命名空间
```

### 10.5.7 迁移指南

**如果你正在从旧版本迁移**：

1. **查找所有 `.text()` 调用**：
   ```bash
   # 搜索旧 API
   grep -r "\.text(" src/
   ```

2. **替换为 `.say(text, id)`**：
   ```java
   // 旧代码
   .text("Hello!")
   
   // 新代码
   .say("Hello!", "greeting_hello")
   ```

3. **更新所有 `.choice()` 调用**：
   ```java
   // 旧代码
   .choice("Bye", c -> c.close())
   
   // 新代码
   .choice("Bye", "choice_bye", c -> c.close())
   ```

4. **测试编译**：
   ```bash
   ./gradlew compileJava
   ```

---

## 11. 动作系统

### 11.1 内置动作

```java
// 任务相关
.startQuest("quest_id")
.completeQuest("quest_id")
.failQuest("quest_id")

// 物品操作
.giveItem(Items.DIAMOND, 5)
.consumeItem(Items.GOLD_INGOT, 2)
.giveItemStack(customStack)

// 属性修改
.giveXp(100)
.giveLevels(2)
.setHealth(20.0f)

// Flag/变量
.setFlag("quest_started")
.clearFlag("temporary_flag")
.setVariable("reputation", 10)
.addVariable("kills", 1)

// 对话控制
.goTo("next_node")
.close()
.endDialogue()

// 商店交互
.openTrade("shop_id")
.openSimpleTrade("quick_shop")

// 预设动作
.presetStoneSword()      // 给予石剑
.presetIronArmorSet()    // 给予铁甲套装
.presetDiamondArmorSet() // 给予钻石甲套装
.presetBread()           // 给予面包
.presetTorches()         // 给予火把
.presetNetheriteIngot()  // 给予下界合金锭
```

### 11.2 自定义动作

```java
.action((player, session) -> {
    // 播放音效
    player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
    
    // 发送消息
    player.sendSystemMessage(Component.literal("§6特殊奖励已发放！"));
    
    // 修改进度
    session.setVariable("special_unlocked", 1);
})
```

### 11.3 动作链

```java
.choice("接受挑战", c -> c
    .setFlag("challenge_accepted")
    .startQuest("my_mod:challenge")
    .giveItem(Items.POTION, 2)
    .goTo("challenge_start"))
```

---

## 12. NPC 扩展机制

### 12.1 创建扩展

```java
public class BlacksmithExtension implements IEntityDialogueExtension<Villager> {
    
    @Override
    public EntityType<Villager> getEntityType() {
        return EntityType.VILLAGER;
    }
    
    @Override
    public boolean canInteractWith(Player player, Villager villager) {
        // 只允许非潜行玩家对话
        return !player.isCrouching();
    }
    
    @Override
    @Nullable
    public String getDialogueTreeId(ServerPlayer player, Villager villager, InteractionHand hand) {
        // 根据职业返回不同对话树
        return switch (villager.getVillagerData().getProfession()) {
            case WEAPONSMITH -> "blacksmith_weapons";
            case ARMORER -> "blacksmith_armor";
            case TOOLSMITH -> "blacksmith_tools";
            default -> "blacksmith_generic";
        };
    }
    
    @Override
    public ProgressScope getProgressScope() {
        return ProgressScope.INSTANCE;  // 每个 NPC 独立进度
    }
    
    @Override
    public void onDialogueStart(ServerPlayer player, Villager villager, DialogueSession session) {
        // 对话开始时触发
        player.sendSystemMessage(Component.literal("铁匠向你点头致意"));
    }
    
    @Override
    public void onDialogueEnd(ServerPlayer player, Villager villager) {
        // 对话结束时触发
    }
}
```

**注意**：接口方法签名与文档之前描述的不同，请以实际代码为准

### 12.2 注册扩展

```java
ArcQuestAPI.registerDialogueExtension(new BlacksmithExtension());
```

### 12.3 进度作用域

| 作用域 | 说明 | 适用场景 |
|--------|------|---------|
| `DIALOGUE_TREE` | 对话树共享 | 所有同类 NPC 共用进度（默认） |
| `INSTANCE` | 实例独立 | 每个 NPC 独立进度 |
| `CUSTOM` | 自定义逻辑 | 编程实现复杂规则 |

---

## 13. 对话事件监听

### 13.1 可用事件（2026-04-22 更新）

| 事件类 | 触发时机 | 关键字段 | 用途 |
|--------|---------|----------|------|
| `DialogueStartedEvent` | 对话开始 | player, npc, dialogueId | 监听 NPC 交互 |
| `DialogueNodeStartedEvent` | 节点显示 | player, npc, dialogueId, nodeId, **sayId**, text, sound | 监听 Say/SayIf 分支，获取语义化 ID |
| `DialogueChoiceSelectedEvent` | 选项选择 | player, npc, dialogueId, nodeId, choiceIndex, **choiceId**, text | 监听选项选择，获取语义化 ID |
| `DialogueEndedEvent` | 对话结束 | player, npc, dialogueId | 监听对话关闭 |

**重要变化**：
- ✅ `DialogueNodeStartedEvent` 新增 `sayId` 字段（保证非空）
- ✅ `DialogueChoiceSelectedEvent` 新增 `choiceId` 字段（保证非空）
- ❌ 删除 `DialogueSayIfEvaluatedEvent`（功能已被 `DialogueNodeStartedEvent` 覆盖）

### 13.2 事件监听示例

#### 监听节点开始（含 Say ID）

```java
@Mod.EventBusSubscriber(modid = "my_addon", bus = Bus.FORGE)
public class DialogueEventHandler {
    
    @SubscribeEvent
    public static void onNodeStarted(DialogueNodeStartedEvent event) {
        ServerPlayer player = event.getPlayer();
        Entity npc = event.getNpc();
        String dialogueId = event.getDialogueId();
        String nodeId = event.getNodeId();
        String sayId = event.getSayId();  // ✅ 保证非空
        String displayText = event.getDisplayText();
        
        LOGGER.info("Node started: dialogue={}, node={}, sayId={}", 
            dialogueId, nodeId, sayId);
        
        // 根据 Say ID 执行自定义逻辑
        if (sayId.equals("arc_quest:greeting_first_time")) {
            player.sendSystemMessage(Component.literal("§6首次见面触发特殊对话！"));
        }
        
        // 检查是否有音效
        if (event.hasSaySound()) {
            SoundEvent sound = event.getSaySound();
            LOGGER.info("Playing sound: {}", sound.getLocation());
        }
    }
    
    @SubscribeEvent
    public static void onChoiceSelected(DialogueChoiceSelectedEvent event) {
        ServerPlayer player = event.getPlayer();
        Entity npc = event.getNpc();
        String dialogueId = event.getDialogueId();
        String nodeId = event.getNodeId();
        int choiceIndex = event.getChoiceIndex();
        String choiceId = event.getChoiceId();  // ✅ 保证非空
        String choiceText = event.getChoiceText();
        
        LOGGER.info("Choice selected: dialogue={}, node={}, choiceId={}", 
            dialogueId, nodeId, choiceId);
        
        // 根据 Choice ID 执行自定义逻辑
        if (choiceId.equals("arc_quest:choice_accept_quest")) {
            // 玩家接受了任务
            player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
            player.sendSystemMessage(Component.literal("§a任务已接受！"));
        }
    }
    
    @SubscribeEvent
    public static void onDialogueStarted(DialogueStartedEvent event) {
        ServerPlayer player = event.getPlayer();
        Entity npc = event.getNpc();
        String dialogueId = event.getDialogueId();
        
        LOGGER.info("Player {} started dialogue '{}' with {}", 
            player.getName().getString(),
            dialogueId,
            npc.getType().getDescriptionId());
    }
    
    @SubscribeEvent
    public static void onDialogueEnded(DialogueEndedEvent event) {
        ServerPlayer player = event.getPlayer();
        Entity npc = event.getNpc();
        String dialogueId = event.getDialogueId();
        
        // 检查是否完成了关键对话
        if (dialogueId.equals("my_mod:story_reveal")) {
            player.sendSystemMessage(Component.literal(
                "§6你揭开了一个重要的秘密..."));
        }
    }
}
```

### 13.3 ClientDialogueCache（客户端缓存）

**新增功能（2026-04-22）**：

```java
// 在客户端访问当前对话状态
ClientDialogueCache cache = ClientDialogueCache.getInstance();

// 获取当前会话数据
Optional<ClientDialogueCache.DialogueSessionData> session = cache.getCurrentSession();
if (session.isPresent()) {
    ClientDialogueCache.DialogueSessionData data = session.get();
    
    // 获取 Say ID
    String sayId = data.getMatchedSayId();  // ✅ 可能为 null
    
    // 获取 Choice IDs
    for (int i = 0; i < data.getChoicesCount(); i++) {
        String choiceId = data.getChoiceId(i);  // ✅ 可能为 null
        LOGGER.info("Choice {}: {}", i, choiceId);
    }
}
```

**用途**：
- GUI 渲染时显示 Say/Choice ID（调试模式）
- 附属模组 UI 增强（根据 ID 显示不同样式）
- 日志记录和统计分析

---

## 14. 商店定义

### 14.1 基础商店

```java
TradeShopDefinition shop = TradeShopBuilder.create("general_store")
    .displayName(Component.translatable("shop.general_store.name"))
    .description(Component.translatable("shop.general_store.desc"))
    .themeColor(0xFF00FFFF)  // 青色主题
    
    .entry(TradeEntryBuilder.create("bread")
        .displayName(Component.literal("面包"))
        .costItem(Items.EMERALD, 1)
        .rewardItem(Items.BREAD, 4))
    
    .entry(TradeEntryBuilder.create("torch_bundle")
        .displayName(Component.literal("火把 bundle"))
        .costItem(Items.EMERALD, 2)
        .rewardItem(Items.TORCH, 16))
    
    .buildAndRegister();
```

**注意**：
- `create(String shopId)`: **智能命名空间解析**
  - 如果包含 `:`（如 `"my_mod:shop"`），直接使用
  - 如果不包含 `:`（如 `"shop"`），自动添加 `arc_quest:` 前缀 → `"arc_quest:shop"`
- `.entry(TradeEntryBuilder)`: 直接传入构建器，会自动调用 `.build()`
- 条目 ID（如 `"bread"`）是局部的，在商店内唯一，无需命名空间

### 14.2 分类商店

```java
TradeCategory weapons = TradeCategory.ofTranslatedColor(
    "weapons", 
    "shop.category.weapons", 
    0, 
    ChatFormatting.RED);

TradeCategory armor = TradeCategory.ofTranslatedColor(
    "armor", 
    "shop.category.armor", 
    1, 
    ChatFormatting.AQUA);

TradeShopBuilder.create("blacksmith_shop")
    .displayName(Component.literal("铁匠铺"))
    .category(weapons)
    .category(armor)
    
    .entry(TradeEntryBuilder.create("iron_sword")
        .displayName(Component.literal("铁剑"))
        .costItem(Items.EMERALD, 5)
        .rewardItem(Items.IRON_SWORD, 1)
        .category(weapons))
    
    .entry(TradeEntryBuilder.create("iron_chestplate")
        .displayName(Component.literal("铁胸甲"))
        .costItem(Items.EMERALD, 12)
        .rewardItem(Items.IRON_CHESTPLATE, 1)
        .category(armor))
    
    .buildAndRegister();
```

### 14.3 简易商店

```java
TradeShopBuilder.create("quick_supplies")
    .displayName(Component.literal("快速补给"))
    .simpleMode()  // 启用简易模式（弹窗式）
    
    .entry(TradeEntryBuilder.create("qs_bread")
        .costItem(Items.EMERALD, 1)
        .rewardItem(Items.BREAD, 4))
    
    .entry(TradeEntryBuilder.create("qs_potion")
        .costItem(Items.EMERALD, 3)
        .rewardEffect(MobEffects.HEAL, 1))
    
    .buildAndRegister();
```

---

## 15. 商品条目配置

### 15.1 基础配置

```java
TradeEntryBuilder.create("diamond_sword")
    .displayName(Component.literal("钻石剑"))
    .description(Component.literal("锋利的钻石剑"))
    .iconOverride(new ResourceLocation("my_mod:textures/items/custom_sword.png"))
    
    // 价格
    .costItem(Items.EMERALD, 10)
    .costItem(Items.DIAMOND, 2)
    
    // 奖励
    .rewardItem(Items.DIAMOND_SWORD, 1)
    
    // 分类
    .category("weapons")
    
    // 排序
    .sortOrder(1)
    
    .build();
```

### 15.2 可见性与购买资格分离

```java
TradeShopBuilder.create("legendary_shop")
    .displayName(Component.literal("传说商店"))
    .entry(TradeEntryBuilder.create("legendary_sword")
        .displayName(Component.literal("传说之剑"))
        
        // 可见性：完成前置任务后显示
        .visibleCondition((player, completed, flags, vars) -> 
            completed.contains(new ResourceLocation("my_mod:defeat_boss")))
        
        // 购买资格：需要等级 20 + 足够声望
        .canBuyCondition((player, completed, flags, vars) ->
            player != null && 
            player.experienceLevel >= 20 &&
            vars.getOrDefault("reputation", 0) >= 50)
        
        .costItem(Items.EMERALD, 50)
        .rewardItem(Items.NETHERITE_SWORD, 1))
    .buildAndRegister();
```

**优势**：
- ✅ 玩家可以提前看到目标商品
- ✅ 明确区分"看不到"和"买不起"
- ✅ 更好的用户体验

### 15.3 多物品奖励

```java
.entry(TradeEntryBuilder.create("adventurer_kit")
    .displayName(Component.literal("冒险者套装"))
    .costItem(Items.EMERALD, 20)
    
    // 多个奖励物品
    .rewardItem(Items.IRON_SWORD, 1)
    .rewardItem(Items.IRON_PICKAXE, 1)
    .rewardItem(Items.BREAD, 10)
    .rewardItem(Items.TORCH, 32))
```

---

## 16. 冷却与限购系统

### 16.1 限购配置

```java
TradeShopBuilder.create("limited_shop")
    .displayName(Component.literal("限购商店"))
    .entry(TradeEntryBuilder.create("rare_item")
        .maxPurchases(3)  // 最多购买 3 次
        .purchaseResetByCooldown())  // 冷却重置时清零计数
    .buildAndRegister();
```

### 16.2 冷却类型

| 方法 | 说明 | 示例 |
|------|------|------|
| `.cooldown(seconds)` | 现实时间秒 | `.cooldown(3600)` - 1 小时 |
| `.cooldownGameDay()` | 游戏日重置 | 每天一次 |
| `.cooldownGameTick(tick)` | 游戏刻重置 | `.cooldownGameTick(0)` - 每天早上 6 点 |
| 无调用 | 无冷却 | 默认值 |

### 16.3 冷却配置示例

```java
TradeShopBuilder.create("cooldown_shop")
    .displayName(Component.literal("冷却商店"))
    
    // 1 小时冷却
    .entry(TradeEntryBuilder.create("hourly_item")
        .cooldown(3600))
    
    // 每天重置
    .entry(TradeEntryBuilder.create("daily_item")
        .cooldownGameDay())
    
    // 每天早上 6 点重置（GAME_TICK = 0）
    .entry(TradeEntryBuilder.create("morning_item")
        .cooldownGameTick(0))
    
    // 7 天冷却
    .entry(TradeEntryBuilder.create("weekly_item")
        .cooldown(7 * 24 * 3600))
    
    .buildAndRegister();
```

### 16.4 自动刷新

当玩家完成任务或改变状态时，商店界面会自动刷新（防抖 1 秒）：

```java
// 无需手动配置，系统自动处理
// 触发事件：QUEST_COMPLETED, QUEST_ACCEPTED, OBJECTIVE_COMPLETED
```

---

## 17. 交易事件监听

### 17.1 可用事件（2026-04-22 更新）

| 事件类 | 触发时机 | 关键字段 | 用途 |
|--------|---------|----------|------|
| `TradeOpenedEvent` | 商店打开 | player, shopId, **npc(可空)** | 监听交易开始 |
| `TradePurchasedSuccessEvent` | 商品购买成功 | player, shopId, entryId | 监听交易完成 |
| `TradePurchaseFailedEvent` | 商品购买失败 | player, shopId, entryId, reason | 监听失败原因 |

**重要变化**：
- ✅ `TradeOpenedEvent` 新增 `npc` 字段（@Nullable，当前为 null，预留接口）
- ✅ `TradeItemPurchasedEvent` 重命名为 `TradePurchasedSuccessEvent`
- ✅ 新增 `TradePurchaseFailedEvent`（细分失败原因）

### 17.2 事件监听示例

```java
@Mod.EventBusSubscriber(modid = "my_addon", bus = Bus.FORGE)
public class TradeEventHandler {
    
    @SubscribeEvent
    public static void onTradeOpened(TradeOpenedEvent event) {
        ServerPlayer player = event.getPlayer();
        String shopId = event.getShopId();
        Entity npc = event.getNpc();  // ✅ 可能为 null
        
        LOGGER.info("Player {} opened shop: {}, npc: {}", 
            player.getName().getString(), 
            shopId,
            npc != null ? npc.getType().getDescriptionId() : "null");
        
        // 检查是否有 NPC 上下文
        if (event.hasNpc()) {
            // 从对话中打开的商店
            player.playSound(SoundEvents.VILLAGER_TRADE, 1.0f, 1.0f);
        } else {
            // 直接打开的商店
            player.playSound(SoundEvents.ANVIL_USE, 1.0f, 1.0f);
        }
    }
    
    @SubscribeEvent
    public static void onTradePurchased(TradePurchasedSuccessEvent event) {
        ServerPlayer player = event.getPlayer();
        String shopId = event.getShopId();
        String entryId = event.getEntryId();
        
        LOGGER.info("Player {} purchased {} from shop {}", 
            player.getName().getString(), entryId, shopId);
        
        // 统计购买次数
        player.getDataStorage().increment("total_purchases");
        
        // 达到特定购买次数给予成就
        int totalPurchases = player.getDataStorage().getInt("total_purchases");
        if (totalPurchases == 100) {
            player.awardAdvancement(
                player.getServer().getAdvancements()
                    .getAdvancement(new ResourceLocation("my_mod:shopaholic")));
        }
    }
    
    @SubscribeEvent
    public static void onTradeFailed(TradePurchaseFailedEvent event) {
        ServerPlayer player = event.getPlayer();
        String shopId = event.getShopId();
        String entryId = event.getEntryId();
        S2COpenTradePacket.FailReason reason = event.getReason();
        
        LOGGER.warn("Trade failed: player={}, shop={}, entry={}, reason={}",
            player.getName().getString(), shopId, entryId, reason);
        
        // 根据失败原因播放不同音效
        switch (reason) {
            case INSUFFICIENT_FUNDS ->
                player.playSound(SoundEvents.VILLAGER_NO, 1.0f, 0.8f);
            case COOLDOWN_ACTIVE ->
                player.playSound(SoundEvents.NOTE_BLOCK_BASS, 1.0f, 0.5f);
            case PURCHASE_LIMIT_REACHED ->
                player.playSound(SoundEvents.NOTE_BLOCK_HAT, 1.0f, 1.2f);
            default -> {}
        }
    }
}
```

### 17.3 ClientTradeCache（客户端缓存）

**新增功能（2026-04-22）**：

```java
// 在客户端访问当前交易状态
ClientTradeCache cache = ClientTradeCache.getInstance();

// 获取当前会话数据
Optional<ClientTradeCache.TradeSessionData> session = cache.getCurrentSession();
if (session.isPresent()) {
    ClientTradeCache.TradeSessionData data = session.get();
    
    // 获取商店 ID
    String shopId = data.getShopId();
    
    // 获取条目信息
    for (int i = 0; i < data.getEntriesCount(); i++) {
        String entryId = data.getEntryId(i);
        int purchaseCount = data.getPurchaseCount(entryId);
        boolean canBuy = data.canBuy(entryId);
        
        LOGGER.info("Entry {}: id={}, purchases={}, canBuy={}", 
            i, entryId, purchaseCount, canBuy);
    }
}
```

**用途**：
- GUI 渲染时显示购买计数和冷却时间
- 附属模组 UI 增强（根据状态显示不同样式）
- 避免重复查询服务端数据

---

## 18. ArcQuestAPI 入口

### 18.1 任务系统 API

```java
// 注册任务
ArcQuestAPI.registerQuest(QuestDefinition definition);

// 查询任务
QuestDefinition quest = ArcQuestAPI.getQuest(ResourceLocation id);
QuestDefinition quest = ArcQuestAPI.getQuestOrThrow(ResourceLocation id);
boolean exists = ArcQuestAPI.hasQuest(ResourceLocation id);
```

### 18.2 对话系统 API

```java
// 注册对话树
ArcQuestAPI.registerDialogueTree(DialogueTree tree);

// 查询对话树
DialogueTree tree = ArcQuestAPI.getDialogueTree(String dialogueId);
boolean exists = ArcQuestAPI.hasDialogueTree(String dialogueId);

// 注册 NPC 扩展
ArcQuestAPI.registerDialogueExtension(IEntityDialogueExtension<?> extension);
```

### 18.3 交易系统 API

```java
// 注册商店
ArcQuestAPI.registerTradeShop(TradeShopDefinition shop);

// 查询商店
TradeShopDefinition shop = ArcQuestAPI.getTradeShop(String shopId);
boolean exists = ArcQuestAPI.hasTradeShop(String shopId);
```

---

## 19. 事件系统总览

### 19.1 事件列表（2026-04-22 更新）

| 事件类 | 总线 | 触发端 | 说明 |
|--------|------|--------|------|
| `QuestAcceptedEvent` | FORGE | 服务端 | 任务接受 |
| `QuestStartedEvent` | FORGE | 服务端 | 任务开始 |
| `QuestProgressChangedEvent` | FORGE | 服务端 | 进度变化 |
| `QuestPhaseChangedEvent` | FORGE | 服务端 | 阶段变更 |
| `QuestPhaseCompletedEvent` | FORGE | 服务端 | 阶段完成 |
| `QuestCompletedEvent` | FORGE | 服务端 | 任务完成 |
| `QuestFailedEvent` | FORGE | 服务端 | 任务失败 |
| `DialogueStartedEvent` | FORGE | 服务端 | 对话开始 |
| `DialogueNodeStartedEvent` | FORGE | 服务端 | 节点显示（含 sayId） |
| `DialogueChoiceSelectedEvent` | FORGE | 服务端 | 选项选择（含 choiceId） |
| `DialogueEndedEvent` | FORGE | 服务端 | 对话结束 |
| `TradeOpenedEvent` | FORGE | 服务端 | 商店打开（含 npc） |
| `TradePurchasedSuccessEvent` | FORGE | 服务端 | 商品购买成功 |
| `TradePurchaseFailedEvent` | FORGE | 服务端 | 商品购买失败 |

**新增事件**：
- ✅ `QuestStartedEvent` - 任务开始
- ✅ `QuestProgressChangedEvent` - 进度变化
- ✅ `QuestPhaseCompletedEvent` - 阶段完成
- ✅ `QuestFailedEvent` - 任务失败
- ✅ `DialogueNodeStartedEvent` - 节点显示（替代 DialogueSayIfEvaluatedEvent）
- ✅ `DialogueChoiceSelectedEvent` - 选项选择
- ✅ `TradePurchaseFailedEvent` - 交易失败

**删除事件**：
- ❌ `DialogueSayIfEvaluatedEvent` - 功能已被 `DialogueNodeStartedEvent` 覆盖

**重命名事件**：
- 🔄 `TradeItemPurchasedEvent` → `TradePurchasedSuccessEvent`

### 19.2 事件订阅

```java
@Mod.EventBusSubscriber(modid = "my_addon", bus = Bus.FORGE)
public class MyEventHandler {
    
    @SubscribeEvent
    public static void onEvent(QuestCompletedEvent event) {
        // 处理事件
    }
}
```

---

## 20. 命令系统

Arc Quest 提供了一套完整的模块化命令系统，用于管理员调试、测试和数据管理。

**权限要求**: 所有命令需要权限等级 ≥ 2（管理员）

### 20.1 命令层级结构

```
/arcquest
├── quest          # 任务管理子系统
│   ├── give       # 给予任务
│   ├── complete   # 完成任务
│   ├── fail       # 失败任务
│   ├── reset      # 重置任务
│   ├── phase      # 切换阶段
│   ├── progress   # 修改进度
│   ├── list       # 列出任务
│   └── debug      # 调试信息
├── dialogue       # 对话管理子系统
│   ├── start      # 开始对话
│   ├── reset      # 重置进度
│   └── status     # 查看状态
├── trade          # 交易管理子系统
│   ├── open       # 打开商店
│   ├── simple     # 打开简易商店
│   ├── list       # 列出商店
│   ├── debug      # 调试商店
│   └── reset      # 重置交易
│       ├── shop   # 重置整个商店
│       ├── entry  # 重置单个条目
│       └── all    # 重置所有交易
└── admin          # 管理员功能
    ├── registry   # 查看注册表
    └── reload     # 重载配置
```

### 20.2 任务管理命令

#### `/arcquest quest give <player> <quest_id>`
给予玩家指定任务。

**示例**：
```bash
/arcquest quest give @p arc_quest:epic_prologue
```

**错误处理**：
- 任务已激活 → 提示 `already_active`
- 任务已完成且不可重复 → 提示 `already_completed`

---

#### `/arcquest quest complete <player> <quest_id>`
强制完成指定任务（自动填满当前阶段所有目标）。

**示例**：
```bash
/arcquest quest complete @p arc_quest:epic_prologue
```

**注意**：只会完成当前激活的阶段，不会跳过后续阶段。

---

#### `/arcquest quest fail <player> <quest_id>`
使任务失败。

**示例**：
```bash
/arcquest quest fail @p arc_quest:epic_prologue
```

---

#### `/arcquest quest reset <player> <quest_id>`
放弃任务并清除进度。

**示例**：
```bash
/arcquest quest reset @p arc_quest:epic_prologue
```

---

#### `/arcquest quest phase <player> <quest_id> <phase_id>`
切换到指定阶段（如果任务未激活则自动激活）。

**示例**：
```bash
/arcquest quest phase @p arc_quest:epic_prologue gather_wood
```

**Tab 补全**：支持自动补全可用阶段 ID。

---

#### `/arcquest quest progress <player> <quest_id> <obj_index> <amount>`
手动设置目标进度。

**示例**：
```bash
# 将第 0 个目标的进度设为 5
/arcquest quest progress @p arc_quest:epic_prologue 0 5
```

**参数说明**：
- `obj_index`: 目标索引（从 0 开始）
- `amount`: 进度值

---

#### `/arcquest quest list [player]`
列出玩家的所有活动任务。

**示例**：
```bash
# 查看自己的任务
/arcquest quest list

# 查看指定玩家的任务
/arcquest quest list Steve
```

**输出格式**：
```
=== Player Steve's Quests ===
  arc_quest:epic_prologue [ACTIVE] phase=gather
  arc_quest:daily_hunt [COMPLETED] phase=complete
Completed History: arc_quest:tutorial
```

---

#### `/arcquest quest debug <player> <quest_id>`
显示任务的详细调试信息（定义 + 运行时状态）。

**示例**：
```bash
/arcquest quest debug @p arc_quest:epic_prologue
```

**输出内容**：
- 任务定义（显示名称、阶段数、可重复性）
- 所有阶段的目标列表
- 运行时状态（当前阶段、各目标进度）

---

### 20.3 对话管理命令

#### `/arcquest dialogue start <player> <dialogue_id>`
强制玩家与指定对话树开始对话。

**示例**：
```bash
/arcquest dialogue start @p village_elder
```

**智能命名空间**：
- 输入 `village_elder` → 自动解析为 `arc_quest:village_elder`
- 输入 `my_mod:custom_dialogue` → 直接使用

---

#### `/arcquest dialogue reset <player> [dialogue_id]`
重置对话进度。

**示例**：
```bash
# 重置所有对话进度
/arcquest dialogue reset @p

# 重置指定对话树
/arcquest dialogue reset @p village_elder
```

---

#### `/arcquest dialogue status <player>`
查看玩家的对话状态（开发中）。

**示例**：
```bash
/arcquest dialogue status @p
```

---

### 20.4 交易管理命令

#### `/arcquest trade open <player> <shop_id>`
为玩家打开完整模式商店界面。

**示例**：
```bash
/arcquest trade open @p blacksmith_shop
```

---

#### `/arcquest trade simple <player> <shop_id>`
为玩家打开简易模式商店界面（弹窗式）。

**示例**：
```bash
/arcquest trade simple @p quick_supplies
```

---

#### `/arcquest trade list`
列出所有注册的商店。

**示例**：
```bash
/arcquest trade list
```

**输出格式**：
```
=== Trade Shops (3) ===
blacksmith_shop - 铁匠铺 [12 entries]
general_store - 杂货店 [8 entries], simple
vip_shop - VIP 商店 [5 entries]
```

---

#### `/arcquest trade debug <shop_id>`
显示商店的详细调试信息。

**示例**：
```bash
/arcquest trade debug blacksmith_shop
```

**输出内容**：
- 商店基本信息（名称、描述、模式）
- 分类列表
- 所有条目详情（成本、奖励、限购、冷却）

---

#### `/arcquest trade reset shop <player> <shop_id>`
重置玩家在指定商店的所有购买计数。

**示例**：
```bash
/arcquest trade reset shop @p blacksmith_shop
```

**用途**：刷新所有限购商品的购买资格。

---

#### `/arcquest trade reset entry <player> <shop_id> <entry_id>`
重置单个商品条目的购买计数。

**示例**：
```bash
/arcquest trade reset entry @p blacksmith_shop diamond_sword
```

**Tab 补全**：支持自动补全商店内的条目 ID。

---

#### `/arcquest trade reset all <player>`
重置玩家在所有商店的所有购买计数。

**示例**：
```bash
/arcquest trade reset all @p
```

**输出**：显示重置的条目总数。

---

### 20.5 管理员功能

#### `/arcquest admin registry`
查看所有已注册的任务、对话树和商店。

**示例**：
```bash
/arcquest admin registry
```

**输出格式**：
```
=== Arc Quest Registry ===
--- Quests (5) ---
    arc_quest:epic_prologue - 史诗序章
    arc_quest:daily_hunt - 每日狩猎
--- Dialogues (3) ---
    arc_quest:village_elder - Village Elder
    arc_quest:blacksmith - Blacksmith
--- Trade Shops (2) ---
    blacksmith_shop - 铁匠铺 [12 entries]
    general_store - 杂货店 [8 entries]
```

---

#### `/arcquest admin reload`
重载游戏配置（调用原生的 `/reload` 命令）。

**示例**：
```bash
/arcquest admin reload
```

---

### 20.6 翻译键规范

所有命令反馈均使用翻译键，支持多语言本地化。

**翻译键前缀**：
- `arc_quest.command.give.*` - 给予任务
- `arc_quest.command.complete.*` - 完成任务
- `arc_quest.command.fail.*` - 失败任务
- `arc_quest.command.reset.*` - 重置任务
- `arc_quest.command.phase.*` - 阶段切换
- `arc_quest.command.progress.*` - 进度修改
- `arc_quest.command.list.*` - 任务列表
- `arc_quest.command.debug.*` - 调试信息
- `arc_quest.command.dialogue.*` - 对话管理
- `arc_quest.command.trade.*` - 交易管理
- `arc_quest.command.registry.*` - 注册表查看
- `arc_quest.command.reload.*` - 重载配置

**自定义消息颜色**：
- 成功消息：`§a[ArcQuest] §f<message>`
- 错误消息：`§c[ArcQuest] §f<message>`

---

### 20.7 扩展命令（附属模组）

附属模组可以通过 Forge 事件总线注册自己的命令：

```java
@Mod.EventBusSubscriber(modid = "my_addon", bus = Bus.FORGE)
public class MyAddonCommands {
    
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        
        dispatcher.register(
            Commands.literal("myaddon")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("custom")
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(
                            () -> Component.literal("Custom command!"), false);
                        return 1;
                    }))
        );
    }
}
```

---

## 21. 网络通信

### 20.1 网络架构

**C2S 数据包**（客户端 → 服务端）：
- `C2SRequestQuestActionPacket`: 任务操作请求
- `C2SDialogueChoicePacket`: 对话选择
- `C2SRequestTradePacket`: 商店交互

**S2C 数据包**（服务端 → 客户端）：
- `S2CSyncFullDataPacket`: 全量数据同步
- `S2CDeltaProgressPacket`: 增量进度同步
- `S2CSyncQuestStatePacket`: 任务状态同步
- `S2COpenDialoguePacket`: 打开对话
- `S2COpenTradePacket`: 打开商店

### 20.2 同步策略

**全量同步**（登录/维度切换）：
```java
ArcQuestNetwork.syncFullData(player, capability);
```

**增量同步**（状态变化）：
```java
// 任务状态变化
ArcQuestNetwork.syncQuestState(player, runtimeData);

// 目标进度更新
ArcQuestNetwork.syncDeltaProgress(player, questId, objIndex, newProgress);

// Flags/Vars 变化
ArcQuestNetwork.syncFlagsAndVars(player, capability);
```

### 20.3 客户端缓存

```java
// 访问客户端缓存（无需网络请求）
ClientQuestCache cache = ClientQuestCache.getInstance();
QuestRuntimeData quest = cache.getActiveQuest("my_mod:quest_id");
Set<String> completed = cache.getCompletedQuests();
```

---

## 22. 数据持久化

### 21.1 Capability 系统

Arc Quest 使用 Forge Capability 系统存储玩家数据：

```java
// 获取玩家能力
IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);

// 查询任务状态
QuestRuntimeData data = cap.getActiveQuest("my_mod:quest_id");
boolean completed = cap.isQuestCompleted("my_mod:quest_id");

// 查询 Flags/Variables
boolean hasFlag = cap.hasFlag("unlocked_area");
int reputation = cap.getVariable("reputation");
```

### 21.2 NBT 结构

```
QuestCapability (CompoundTag)
├── Version (Int) = 2
├── ActiveQuests (List)
│   └── [0] (Compound)
│       ├── QuestId (String)
│       ├── State (Int)
│       ├── CurrentPhase (String)
│       └── Progress (IntArray)
├── CompletedQuests (List<String>)
├── Flags (List<String>)
├── Variables (Compound)
└── DialogueProgress (Compound)
```

### 21.3 版本迁移

```java
// 自动处理旧版本数据迁移
if (version < NbtVersionManager.CURRENT_VERSION) {
    nbt = NbtVersionManager.migrate(nbt, version);
}
```

---

## 23. 性能优化

### 22.1 O(1) 目标追踪

**传统方案**：O(n×m) 遍历
```java
for (QuestRuntimeData quest : allActiveQuests) {
    for (ObjectiveEntry obj : quest.getObjectives()) {
        if (matches(event, obj)) {
            incrementProgress(quest, obj);
        }
    }
}
```

**Arc Quest 方案**：O(1) 哈希查找
```java
Map<UUID, Map<String, Map<Integer, TrackedObjective>>> index;

// 直接定位相关目标
TrackedObjective tracked = index.get(playerId)
    .get(questId)
    .get(objIndex);
```

**性能对比**：

| 场景 | 传统方案 | Arc Quest | 提升 |
|------|---------|-----------|------|
| 10 任务 × 3 目标 | 30 次检查 | 1-3 次 | **10-30x** |
| 50 任务 × 5 目标 | 250 次检查 | 1-5 次 | **50-250x** |

### 22.2 增量网络同步

**全量同步**：~5KB（登录时）
**增量同步**：~50B（进度更新）

**带宽节省**：90%+

### 22.3 UI 渲染优化

**顶点缓冲批量绘制**：
- 合并相同材质的绘制调用
- 100 个商品：600 次 OpenGL 调用 → 10 次
- FPS 提升：15-20%

**动画插值**：
```java
// 帧率无关插值
float dt = Math.min((now - lastRenderTime) / 1000f, 0.1f);
progress = lerp(progress, targetProgress, dt * animationSpeed);
```

### 22.4 缓存机制

**条件评估缓存**：
```java
// 缓存 1 tick，避免重复计算
private long lastEvalTick = -1;
private boolean cachedResult;

public boolean evaluate(DialogueContext ctx) {
    long currentTick = ctx.getLevel().getGameTime();
    if (currentTick != lastEvalTick) {
        cachedResult = condition.test(ctx);
        lastEvalTick = currentTick;
    }
    return cachedResult;
}
```

---

## 24. 最佳实践

### 23.1 使用 Builder 模式

```java
// ✅ 推荐
QuestBuilder.create("quest_id")
    .category(QuestCategory.MAIN)
    .phase(phase -> phase.objective(obj -> obj.count(5)))
    .buildAndRegister();

// ❌ 避免
new QuestDefinition(...);  // 参数过多
```

### 23.2 条件判断做空值检查

```java
// ✅ 安全
.visibleCondition((player, ...) -> {
    if (player == null) return false;
    return player.getHealth() > 10.0f;
})

// ❌ 危险（NPE）
.visibleCondition((player, ...) -> player.getHealth() > 10.0f)
```

### 23.3 服务端验证

```java
// ✅ 正确：服务端二次验证
if (TradeEntryStateResolver.canPurchase(player, cap, shopId, entry)) {
    executeTrade();
}

// ❌ 错误：信任客户端
if (clientSaysCanBuy) {
    executeTrade();
}
```

### 23.4 国际化支持

```java
// ✅ 使用翻译键
Component.translatable("arc_quest.quest.my_quest.name")

// ❌ 硬编码
Component.literal("My Quest")
```

### 23.5 错误处理

```java
// 奖励发放容错
try {
    reward.grant(player);
} catch (Exception e) {
    LOGGER.error("Reward failed: {}", e.getMessage(), e);
    // 继续发放其他奖励
}
```

---

## A. API 速查表

### 任务系统

```java
// 创建任务
QuestBuilder.create("id")
    .displayName(Component)
    .category(QuestCategory)
    .phase(PhaseBuilder.create("phase_id")
        .objective(ObjectiveBuilder.kill(EntityType.ZOMBIE, 5))
        .thenGoTo("next_phase"))
    .reward(reward -> reward.item(Items.DIAMOND, 1))
    .buildAndRegister();

// 监听事件
@SubscribeEvent
public void onQuestCompleted(QuestCompletedEvent event) { ... }
```

### 对话系统

```java
// 创建对话树
DialogueTreeBuilder.create("id")
    .npc("NPC Name")
    .node("start", NodeBuilder.create()
        .text("Hello!")
        .choice("Bye", c -> c.close()))
    .build();

// 注册对话树
ArcQuestAPI.registerDialogueTree(tree);

// 注册 NPC 扩展
ArcQuestAPI.registerDialogueExtension(extension);
```

### 交易系统

```java
// 创建商店
TradeShopBuilder.create("id")
    .displayName(Component)
    .entry(TradeEntryBuilder.create("entry_id")
        .costItem(Item, int)
        .rewardItem(Item, int)
        .cooldown(seconds))
    .buildAndRegister();
```

---

## B. 常见问题

### Q1: 任务完成后没有触发奖励？

**A**: 检查：
1. 奖励是否正确添加到 `onComplete()`
2. 日志是否有异常信息
3. 玩家背包是否有空间

### Q2: 对话冷却不生效？

**A**: 确保：
1. 使用了正确的 `CooldownType`
2. 时间在 `TimeSanitizer` 范围内
3. 冷却数据正确序列化到 NBT

### Q3: 交易界面显示 "Locked" 但条件已满足？

**A**: 可能原因：
1. 客户端缓存未更新 → 重开界面
2. 自动刷新防抖 → 等待 1 秒
3. 条件判断逻辑错误 → 检查日志

### Q4: 如何重置测试数据？

**A**: 
```bash
/quest resetall @p
```

或在代码中：
```java
cap.clearAllData();
ArcQuestNetwork.syncFullData(player, cap);
```

### Q5: O(1) 追踪索引何时重建？

**A**: 
- 玩家登录时
- 接受新任务时
- 任务完成/失败时
- 手动调用 `ObjectiveTracker.rebuildIndex()`

---

## C. 完整示例项目

### C.1 示例：冒险者公会任务线

```java
@Mod("adventure_guild")
public class AdventureGuildMod {
    
    public AdventureGuildMod() {
        FMLJavaModLoadingContext.get().getModEventBus()
            .addListener(this::onCommonSetup);
    }
    
    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            registerQuests();
            registerDialogues();
            registerShops();
        });
    }
    
    private void registerQuests() {
        // 任务 1：新手训练
        ArcQuestAPI.registerQuest(
            QuestBuilder.create("adventure_guild:novice_training")
                .displayName(Component.translatable("quest.novice_training.name"))
                .description(Component.translatable("quest.novice_training.desc"))
                .category(QuestCategory.ARCHON)
                
                .phase(PhaseBuilder.create("combat")
                    .displayName(Component.literal("战斗训练"))
                    .objective(ObjectiveBuilder.kill(EntityType.SKELETON, 5)
                        .display("击杀 5 只骷髅"))
                    .objective(ObjectiveBuilder.kill(EntityType.ZOMBIE, 5)
                        .display("击杀 5 只僵尸"))
                    .thenGoTo("gather"))
                
                .phase(PhaseBuilder.create("gather")
                    .displayName(Component.literal("资源收集"))
                    .objective(ObjectiveBuilder.collect(Items.OAK_LOG, 20)
                        .display("收集 20 个橡木原木"))
                    .objective(ObjectiveBuilder.collect(Items.COBBLESTONE, 10)
                        .display("收集 10 个圆石"))
                    .thenGoTo("complete"))
                
                .reward(reward -> reward
                    .item(Items.IRON_SWORD, 1)
                    .item(Items.IRON_PICKAXE, 1)
                    .xp(100)
                    .flag("novice_trained"))
                
                .buildAndRegister()
        );
        
        // 任务 2：探索洞穴（需要完成任务 1）
        ArcQuestAPI.registerQuest(
            QuestBuilder.create("adventure_guild:cave_exploration")
                .displayName(Component.literal("洞穴探索"))
                .unlockCondition(Conditions.flagSet("novice_trained"))
                
                .phase(PhaseBuilder.create("enter_cave")
                    .objective(ObjectiveBuilder.reachLocation(
                        new ResourceLocation("my_mod:cave_entrance"),
                        100, 64, 200, 10)
                        .display("到达洞穴入口"))
                    .thenGoTo("explore"))
                
                .phase(PhaseBuilder.create("explore")
                    .objective(ObjectiveBuilder.collect(Items.GOLD_INGOT, 5)
                        .display("收集 5 个金锭"))
                    .thenGoTo("complete"))
                
                .reward(reward -> reward
                    .item(Items.DIAMOND, 3)
                    .xp(200)
                    .flag("cave_explored"))
                
                .buildAndRegister()
        );
    }
    
    private void registerDialogues() {
        ArcQuestAPI.registerDialogueTree(
            DialogueTreeBuilder.create("adventure_guild:receptionist")
                .npc("接待员")
                
                .node("start")
                    // SayIf 条件分支（带 ID）
                    .sayIf(
                        Conditions.not(Conditions.flagSet("novice_trained")),
                        "欢迎来到冒险者公会！你想成为冒险者吗？",
                        "greeting_newbie")  // ✅ SayIf ID
                    
                    .sayIf(
                        Conditions.flagSet("novice_trained"),
                        "欢迎回来，经验丰富的冒险者！",
                        "greeting_veteran")  // ✅ SayIf ID
                    
                    // Choice 选项（带 ID）
                    .choiceIf(
                        Conditions.not(Conditions.flagSet("novice_trained")),
                        "我想接受训练",
                        "choice_training",  // ✅ Choice ID
                        c -> c.startQuest("adventure_guild:novice_training")
                            .goTo("training_accepted"))
                    
                    .choiceIf(
                        Conditions.allOf(
                            Conditions.flagSet("novice_trained"),
                            Conditions.not(Conditions.flagSet("cave_explored"))
                        ),
                        "有更高难度的任务吗？",
                        "choice_cave_quest",  // ✅ Choice ID
                        c -> c.startQuest("adventure_guild:cave_exploration")
                            .goTo("cave_accepted"))
                    
                    .choice("浏览商店", "choice_browse_shop", c -> c  // ✅ Choice ID
                        .openTrade("guild_shop")
                        .restoreToCurrentNode())
                    
                    .choice("再见", "choice_bye", c -> c.close())  // ✅ Choice ID
                
                .node("training_accepted")
                    .say("很好！先去击败一些怪物吧。", "training_accepted_text")  // ✅ Say ID
                    .choice("明白了", "choice_understood", c -> c.close())  // ✅ Choice ID
                
                .node("cave_accepted")
                    .say("小心！洞穴里很危险。", "cave_warning_text")  // ✅ Say ID
                    .choice("我会小心的", "choice_be_careful", c -> c.close())  // ✅ Choice ID
                
                .buildAndRegister()
        );
        
        // 注册 NPC 扩展
        ArcQuestAPI.registerDialogueExtension(
            new IEntityDialogueExtension<Villager>() {
                @Override
                public EntityType<Villager> getEntityType() {
                    return EntityType.VILLAGER;
                }
                
                @Override
                public boolean canInteractWith(Player player, Villager villager) {
                    return true;  // 允许所有玩家交互
                }
                
                @Override
                @Nullable
                public String getDialogueTreeId(ServerPlayer player, Villager villager, InteractionHand hand) {
                    return "adventure_guild:receptionist";
                }
                
                @Override
                public ProgressScope getProgressScope() {
                    return ProgressScope.DIALOGUE_TREE;  // 全局共享进度
                }
            }
        );
    }
    
    private void registerShops() {
        ArcQuestAPI.registerTradeShop(
            TradeShopBuilder.create("guild_shop")
                .displayName(Component.literal("公会商店"))
                .themeColor(0xFFFFD700)  // 金色主题
                
                .entry(TradeEntryBuilder.create("health_potion")
                    .displayName(Component.literal("生命药水"))
                    .costItem(Items.EMERALD, 2)
                    .rewardItem(Items.POTION, 1)
                    .cooldownGameDay())
                
                .entry(TradeEntryBuilder.create("iron_armor_set")
                    .displayName(Component.literal("铁甲套装"))
                    .visibleCondition(Conditions.flagSet("novice_trained"))
                    .costItem(Items.EMERALD, 15)
                    .rewardItem(Items.IRON_CHESTPLATE, 1)
                    .rewardItem(Items.IRON_LEGGINGS, 1)
                    .maxPurchases(1))
                
                .entry(TradeEntryBuilder.create("diamond_sword")
                    .displayName(Component.literal("钻石剑"))
                    .visibleCondition(Conditions.flagSet("cave_explored"))
                    .costItem(Items.EMERALD, 30)
                    .rewardItem(Items.DIAMOND_SWORD, 1)
                    .maxPurchases(1)
                    .cooldown(7 * 24 * 3600))  // 7 天冷却
                
                .buildAndRegister()
        );
    }
}
```

### C.2 事件监听器

```java
@Mod.EventBusSubscriber(modid = "adventure_guild", bus = Bus.FORGE)
public class GuildEventHandler {
    
    @SubscribeEvent
    public static void onQuestCompleted(QuestCompletedEvent event) {
        ServerPlayer player = event.getPlayer();
        ResourceLocation questId = event.getQuestId();
        
        if (questId.equals(new ResourceLocation("adventure_guild:novice_training"))) {
            // 播放升级音效
            player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
            
            // 发送祝贺消息
            player.sendSystemMessage(Component.literal(
                "§6§l恭喜！你完成了新手训练！"));
            
            // 解锁新对话选项
            player.sendSystemMessage(Component.literal(
                "§a现在可以找接待员接受更高难度的任务了。"));
        }
    }
    
    @SubscribeEvent
    public static void onTradePurchased(TradePurchasedSuccessEvent event) {  // ✅ 新名称
        ServerPlayer player = event.getPlayer();
        String entryId = event.getEntryId();
        
        if (entryId.equals("diamond_sword")) {
            // 购买稀有物品时全服公告
            player.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("§6§l" + player.getName().getString() + 
                                " §6购买了钻石剑！"), false);
        }
    }
    
    @SubscribeEvent
    public static void onNodeStarted(DialogueNodeStartedEvent event) {
        ServerPlayer player = event.getPlayer();
        String sayId = event.getSayId();  // ✅ Say ID
        
        // 根据 Say ID 播放不同音效
        if (sayId.equals("adventure_guild:greeting_newbie")) {
            player.playSound(SoundEvents.VILLAGER_TRADE, 1.0f, 1.2f);
        } else if (sayId.equals("adventure_guild:greeting_veteran")) {
            player.playSound(SoundEvents.VILLAGER_YES, 1.0f, 1.0f);
        }
    }
}
```

---

## D. 智能命名空间详解

### D.1 设计理念

**问题背景**：
- 作为 Lib 模组，需要支持附属模组使用自己的命名空间
- 主模组内部使用时希望代码简洁
- 避免强制添加命名空间导致的灵活性缺失

**解决方案**：智能解析逻辑
```java
public static Builder create(String id) {
    if (id.contains(":")) {
        // 用户已提供完整命名空间 → 直接使用
        return new Builder(ResourceLocation.tryParse(id));
    } else {
        // 用户只提供路径 → 自动补全默认命名空间
        return new Builder(
            ResourceLocation.fromNamespaceAndPath(Arc_quest.MOD_ID, id)
        );
    }
}
```

### D.2 使用场景对比

#### **场景1：主模组内部（简洁写法）**
```java
// ✅ 推荐：省略前缀，代码更简洁
QuestBuilder.create("epic_prologue")
// 结果：arc_quest:epic_prologue

DialogueTreeBuilder.create("village_elder")
// 结果：arc_quest:village_elder

TradeShopBuilder.create("blacksmith_shop")
// 结果：arc_quest:blacksmith_shop
```

#### **场景2：显式指定（清晰意图）**
```java
// ✅ 也可以显式写全，意图更明确
QuestBuilder.create("arc_quest:epic_prologue")
// 结果：arc_quest:epic_prologue
```

#### **场景3：附属模组（关键场景）**
```java
// ✅ 附属模组使用自己的命名空间
QuestBuilder.create("myaddon:custom_quest")
// 结果：myaddon:custom_quest

// ✅ 引用主模组资源
.requiresQuest("arc_quest:epic_prologue")
c -> c.openTrade("arc_quest:blacksmith_shop")
```

### D.3 全局 ID vs 局部 ID

| ID 类型 | 作用域 | 是否需要命名空间 | 示例 |
|---------|--------|-----------------|------|
| **任务 ID** | 全局唯一 | ✅ 需要 | `arc_quest:epic_prologue` |
| **对话树 ID** | 全局唯一 | ✅ 需要 | `arc_quest:village_elder` |
| **商店 ID** | 全局唯一 | ✅ 需要 | `arc_quest:blacksmith_shop` |
| **阶段 ID** | 任务内唯一 | ✅ 需要 | `arc_quest:gather_wood` |
| **Flag ID** | 全局唯一 | ✅ 需要 | `arc_quest:chapter1_unlocked` |
| **变量名** | 全局唯一 | ✅ 需要 | `arc_quest:village_reputation` |
| **节点 ID** | 对话树内唯一 | ❌ 不需要 | `start`, `intro_story` |
| **条目 ID** | 商店内唯一 | ❌ 不需要 | `iron_sword`, `diamond_sword` |
| **分类 ID** | 商店内唯一 | ❌ 不需要 | `weapons`, `armor` |

**规则总结**：
- ✅ **全局资源**（跨系统引用）必须带命名空间
- ❌ **局部资源**（仅在当前容器内使用）不需要命名空间

### D.4 最佳实践

#### **✅ 推荐做法**
```java
// 1. 主模组内部：省略前缀
QuestBuilder.create("my_quest")

// 2. 跨模组引用：显式写全
.requiresQuest("other_mod:their_quest")

// 3. 附属模组：使用自己的命名空间
QuestBuilder.create("myaddon:my_quest")

// 4. 局部 ID：保持简洁
.node("start")  // 无需 "arc_quest:start"
.entry(TradeEntryBuilder.create("item"))  // 无需 "arc_quest:item"
```

#### **❌ 避免做法**
```java
// 1. 不要给局部 ID 添加命名空间
.node("arc_quest:start")  // ❌ 错误！节点 ID 是局部的

// 2. 不要在引用时省略命名空间
.requiresQuest("epic_prologue")  // ⚠️ 虽然能工作，但意图不明确

// 3. 不要混用不同模组的命名空间
QuestBuilder.create("arc_quest:mod_a_quest")  // ❌ 混乱！
```

### D.5 向后兼容性

**旧代码仍然有效**：
```java
// 之前这样写的代码无需修改
QuestBuilder.create("quest_id")
// 仍然会被解析为 arc_quest:quest_id
```

**迁移建议**：
- 新项目：直接使用智能 API
- 旧项目：无需修改，自动兼容
- 附属模组：建议使用显式命名空间

---

**文档结束**

如需更多细节，请查阅源代码注释或提出具体问题。

🎉 **祝你开发愉快！**

---

## 📝 更新日志

### v1.0.0 (2026-04-22) - Say/Choice 强制 ID 重构

#### ✨ 新增功能

**对话系统**：
- ✅ **Say/Choice 强制 ID 绑定** - 所有 `say()` 和 `choice()` 方法必须提供 ID
- ✅ **智能命名空间解析** - 自动补全 `arc_quest:` 前缀，支持附属模组自定义命名空间
- ✅ **ClientDialogueCache** - 统一管理对话发包数据，客户端可访问 Say/Choice ID
- ✅ **个体化音效配置** - 每个 SayIf/Choice 可独立配置音效
- ✅ **DialogueNodeStartedEvent** - 新增事件，包含 sayId、displayText、sound
- ✅ **DialogueChoiceSelectedEvent** - 新增事件，包含 choiceId、choiceText

**交易系统**：
- ✅ **ClientTradeCache** - 统一管理交易发包数据，客户端可访问购买状态
- ✅ **TradePurchaseFailedEvent** - 新增事件，细分失败原因（资金不足/冷却中/限购已达）
- ✅ **TradeOpenedEvent 增强** - 添加 npc 字段（@Nullable，预留接口）
- ✅ **商店开关音效** - openSound/closeSound 配置

**任务系统**：
- ✅ **QuestStartedEvent** - 任务开始事件
- ✅ **QuestProgressChangedEvent** - 进度变化事件
- ✅ **QuestPhaseCompletedEvent** - 阶段完成事件
- ✅ **QuestFailedEvent** - 任务失败事件
- ✅ **ClientQuestCache 增强** - 任务 HUD 优化

#### 🔧 API 变更

**破坏性变更**：
- ❌ 移除 `.text(String)` - 替换为 `.say(String text, String sayId)`
- ❌ 移除 `.textIf(ICondition, String)` - 替换为 `.sayIf(ICondition, String text, String sayId)`
- ❌ 移除 `.choice(String, Consumer)` - 替换为 `.choice(String label, String choiceId, Consumer)`
- ❌ 移除 `.choiceIf(ICondition, String, Consumer)` - 替换为 `.choiceIf(ICondition, String label, String choiceId, Consumer)`
- ❌ 删除 `DialogueSayIfEvaluatedEvent` - 功能已被 `DialogueNodeStartedEvent` 覆盖
- 🔄 重命名 `TradeItemPurchasedEvent` → `TradePurchasedSuccessEvent`

**非破坏性变更**：
- ✅ `TradeOpenedEvent` 构造函数添加 `npc` 参数（向后兼容，传 null 即可）

#### 📚 文档更新

- ✅ 更新第 9-10 章 - 对话树构建与节点选项（添加 Say/Choice ID 示例）
- ✅ 新增第 10.5 章 - Say/Choice 强制 ID 规范（设计理念、最佳实践、迁移指南）
- ✅ 更新第 13 章 - 对话事件监听（添加 DialogueNodeStartedEvent/DialogueChoiceSelectedEvent 示例）
- ✅ 新增第 13.3 节 - ClientDialogueCache 使用说明
- ✅ 更新第 17 章 - 交易事件监听（添加 TradePurchaseFailedEvent 示例）
- ✅ 新增第 17.3 节 - ClientTradeCache 使用说明
- ✅ 更新第 19 章 - 事件系统总览（列出所有新增/删除/重命名事件）
- ✅ 更新附录 C - 完整示例项目（使用新的 Say/Choice ID API）

#### 🐛 Bug 修复

- 修复 HUD 渲染问题
- 修复商店可见条件与可购买条件分离逻辑
- 修复 Condition 重载冲突

#### 🎯 性能优化

- O(1) 目标追踪索引（任务系统）
- 增量网络同步（减少 90% 带宽占用）
- ClientCache 缓存机制（避免重复查询服务端）

---

### 迁移指南

**从旧版本迁移到新 API**：

1. **查找并替换所有 `.text()` 调用**：
   ```bash
   grep -rn "\.text(" src/main/java/
   ```
   
   ```java
   // 旧代码
   .text("Hello!")
   
   // 新代码
   .say("Hello!", "greeting_hello")
   ```

2. **查找并替换所有 `.choice()` 调用**：
   ```bash
   grep -rn "\.choice(" src/main/java/
   ```
   
   ```java
   // 旧代码
   .choice("Bye", c -> c.close())
   
   // 新代码
   .choice("Bye", "choice_bye", c -> c.close())
   ```

3. **更新事件监听器**：
   ```java
   // 旧代码
   @SubscribeEvent
   public void onTradePurchased(TradeItemPurchasedEvent event) { ... }
   
   // 新代码
   @SubscribeEvent
   public void onTradePurchased(TradePurchasedSuccessEvent event) { ... }
   ```

4. **测试编译**：
   ```bash
   ./gradlew compileJava
   ```

5. **运行游戏测试**：
   ```bash
   ./gradlew runClient
   ```

**预计工作量**：
- 小型项目（<100 处调用）：1-2 小时
- 中型项目（100-500 处调用）：半天
- 大型项目（>500 处调用）：1-2 天

---
