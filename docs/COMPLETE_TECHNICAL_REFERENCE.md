# Arc Quest 完整技术文档

> **版本**: 1.0.0  
> **平台**: Minecraft Forge 1.20.1  
> **Java**: 17+  
> **最后更新**: 2026-04-22  
> **文档类型**: 完整 API 参考与开发指南

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

Arc Quest 是一个**纯代码驱动**的 Minecraft RPG 模组，提供完整的任务、对话、交易和抽奖系统。

**关键优势**：
- ✅ **O(1) 目标追踪**: 哈希索引替代遍历，性能提升 10-250 倍
- ✅ **增量网络同步**: 仅发送变化数据，减少 90% 带宽占用
- ✅ **统一冷却管理**: 支持 GAME_TICK/GAME_DAY/REAL_TIME 三种模式
- ✅ **服务端权威**: 所有业务逻辑二次验证，防止作弊
- ✅ **Lib 模组架构**: 完善的 API 和事件系统，易于扩展
- ✅ **Ponder 引擎集成**: 可视化任务教程展示（2026-04-22 新增）
- ✅ **抽奖系统**: 完整随机奖励机制，支持稀有度/保底/限购（2026-04-22 新增）

### 1.2 模块结构

```
org.com.arc_quest/
├── api/                    # 公共 API（附属模组使用）
│   ├── ArcQuestAPI.java   # 统一入口
│   └── event/             # 事件类
├── quest/                  # 任务系统 (35%)
│   ├── api/               # 任务 API
│   ├── builder/           # Builder 模式
│   ├── registry/          # 注册表
│   ├── logic/             # 业务逻辑
│   ├── network/           # 网络包
│   └── capability/        # 数据存储
├── dialogue/               # 对话系统 (25%)
│   ├── api/               # 对话 API
│   ├── builder/           # 对话树构建器
│   ├── runtime/           # 运行时管理
│   └── extension/         # NPC 扩展
├── trade/                  # 交易系统 (15%)
│   ├── api/               # 交易 API
│   ├── builder/           # 商店构建器
│   └── runtime/           # 会话管理
├── gacha/                  # 抽奖系统 (15%) [2026-04-22 新增]
│   ├── api/               # 抽奖 API
│   ├── builder/           # 奖池构建器
│   ├── runtime/           # 会话管理
│   └── network/           # 网络包
└── client/                 # 客户端 UI (10%)
    ├── gui/               # 界面类
    └── render/            # 渲染工具（含 Ponder 集成）
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
    .description(Component.literal("通过实战提升战斗技巧，掌握基础武器使用。"))  // ✅ 新增：阶段描述
    
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
    
    // ✅ 新增：绑定 Ponder 情报场景
    .ponderScene(new ResourceLocation("my_mod:combat_tutorial"))
    
    .build();
```

**新增字段说明**:
- `.description(Component)`: 阶段的详细描述文本，显示在任务日志界面的阶段详情中
- `.ponderScene(ResourceLocation)`: 绑定的 Ponder 场景 ID，点击 "PHASE INTEL" 按钮时播放教学动画

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

### 6.1 ICondition 静态工厂方法 (推荐)

**2026-04-22 更新**: 引入 `ICondition` 接口的静态工厂方法，简化条件创建。

```java
// ✅ 推荐：使用静态工厂方法
ICondition.flagSet("unlocked_area")
ICondition.flagNotSet("failed_mission")
ICondition.questCompleted(new ResourceLocation("my_mod:prev_quest"))
ICondition.variableInRange("reputation", 10, 100)
ICondition.variableEquals("level", 5)
ICondition.variableGTE("strength", 10)

// 组合条件
ICondition.allOf(
    ICondition.questCompleted(new ResourceLocation("quest_a")),
    ICondition.flagSet("flag_b")
)

ICondition.anyOf(
    ICondition.variableGTE("strength", 10),
    ICondition.variableGTE("intelligence", 10)
)

ICondition.not(ICondition.flagSet("blocked"))
```

**优势**:
- ✅ 更简洁的 API（无需手动实例化具体类）
- ✅ 类型推断支持
- ✅ IDE 自动补全友好
- ✅ 统一的条件接口

### 6.2 IConditionWrapper 适配器 (跨系统复用)

**用途**: 在不同系统间复用条件逻辑（任务/对话/交易）

```java
// 在对话中使用任务完成条件
DialogueTreeBuilder.create("villager_dialogue")
    .node("start")
        .choiceIf(
            ICondition.questCompleted(new ResourceLocation("arc_quest:epic_prologue"))
                .asDialogueCondition(),  // ✅ 转换为对话条件
            "接受新任务",
            c -> c.startQuest("arc_quest:new_quest")
        )

// 在交易中使用 Flag 条件
TradeShopBuilder.create("vip_shop")
    .entry(TradeEntryBuilder.create("legendary_item")
        .visibleCondition(
            ICondition.flagSet("unlocked_vip")
                .asTradeCondition()  // ✅ 转换为交易条件
        )
    )
```

**工厂方法**:
```java
ICondition.asDialogueCondition(ICondition condition)  // 转为对话条件
ICondition.asTradeCondition(ICondition condition)     // 转为交易条件
```

### 6.3 旧版 Conditions 工具类 (已废弃)

**⚠️ 废弃说明**: `Conditions` 工具类的方法已被 `ICondition` 静态工厂替代，但仍保留向后兼容。

```java
// ❌ 旧版（仍可用，但不推荐）
Conditions.flagSet("unlocked_area")
Conditions.questCompleted("my_mod:prev_quest")
Conditions.variableInRange("reputation", 10, 100)

// ✅ 新版（推荐）
ICondition.flagSet("unlocked_area")
ICondition.questCompleted(new ResourceLocation("my_mod:prev_quest"))
ICondition.variableInRange("reputation", 10, 100)
```

**废弃的具体条件类**:
- `FlagCondition` → 使用 `ICondition.flagSet()`
- `QuestCompletedCondition` → 使用 `ICondition.questCompleted()`
- `VariableRangeCondition` → 使用 `ICondition.variableInRange()`

### 6.4 自定义条件

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

### 7.1 内置奖励类型

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

### 7.2 核心奖励类详解

#### 7.2.1 FlagReward - Flag 设置/清除

```java
// ✅ 推荐：使用静态工厂方法
FlagReward.set("quest_started")      // 设置 Flag
FlagReward.clear("temporary_flag")   // 清除 Flag

// 访问器方法
String flag = reward.getFlag();      // 获取 Flag 名称
boolean isSet = reward.isSet();      // 是否为设置操作（true=设置，false=清除）
```

**实现原理**：
```java
@Override
public void grant(ServerPlayer player) {
    IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
    if (cap == null) return;
    if (this.set) {
        cap.setFlag(this.flag);       // 设置 Flag
    } else {
        cap.removeFlag(this.flag);    // 移除 Flag
    }
}
```

**关键特性**：
- ✅ 已完整实现 `grant()` 方法（之前为空存根）
- ✅ 自动处理 null 检查
- ✅ 支持设置和清除两种操作

#### 7.2.2 VariableReward - 变量修改

```java
// ✅ 推荐：使用静态工厂方法
VariableReward.set("reputation", 100)         // 设置为固定值
VariableReward.add("kills", 5)                // 增加
// VariableReward.subtract("debt", 10)        // 减少（需手动构造）
// VariableReward.multiply("multiplier", 2)   // 乘法（需手动构造）

// 访问器方法
String name = reward.getVariableName();  // 获取变量名
Op op = reward.getOperation();           // 获取操作类型（SET/ADD/SUBTRACT/MULTIPLY）
int value = reward.getValue();           // 获取操作值

// 计算应用后的值
int current = 50;
int result = reward.apply(current);  // 根据 Op 类型计算结果
```

**实现原理**：
```java
@Override
public void grant(ServerPlayer player) {
    IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
    if (cap == null) return;
    int current = cap.getVariable(this.variableName);
    cap.setVariable(this.variableName, this.apply(current));
}

public int apply(int current) {
    return switch (this.operation) {
        case SET      -> this.value;              // 直接赋值
        case ADD      -> current + this.value;    // 加法
        case SUBTRACT -> current - this.value;    // 减法
        case MULTIPLY -> current * this.value;    // 乘法
    };
}
```

**操作类型枚举**：
```java
public enum Op { 
    SET,      // 设置为指定值
    ADD,      // 增加
    SUBTRACT, // 减少
    MULTIPLY  // 乘法
}
```

**关键特性**：
- ✅ 已完整实现 `grant()` 方法（之前为空存根）
- ✅ 支持四种运算操作（SET/ADD/SUBTRACT/MULTIPLY）
- ✅ 基于当前值进行计算，避免覆盖已有数据

#### 7.2.3 ItemReward - 物品奖励

```java
// 构造函数
ItemReward reward = new ItemReward(Items.DIAMOND, 5);

// ✅ 新增：访问器方法
Item item = reward.getItem();     // 获取物品类型
int count = reward.getCount();    // 获取数量
```

**实现原理**：
```java
@Override
public void grant(ServerPlayer player) {
    ItemStack stack = new ItemStack(this.item, this.count);
    boolean added = player.getInventory().add(stack);
    if (!added) {
        // 背包满则掉落在地
        player.drop(stack, false);
    }
    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 1.0F);
}
```

**关键特性**：
- ✅ 自动处理背包满的情况（掉落物品）
- ✅ 播放拾取音效
- ✅ 新增 `getItem()` 和 `getCount()` 访问器（用于查询和调试）

### 7.3 自定义奖励

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

### 7.4 容错处理

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

### 11.2 CommandExecutor - 统一命令执行工具

**⚠️ 重要更新**：2026-04-22 后引入 `CommandExecutor` 工具类，统一所有命令执行逻辑。

#### 11.2.1 权限模型

```java
// ✅ 以服务器权限执行（op级别4）
// 适用于：奖励发放、交易购买等"系统赋予"场景
CommandExecutor.runAsServer(player, "/give {player} diamond 5");

// ✅ 以玩家自身权限执行
// 适用于：对话选项、玩家主动触发等场景
CommandExecutor.runAsPlayer(player, "/say 我完成了一个任务！");
```

**权限对比**：
| 方法 | 权限级别 | 适用场景 | 示例 |
|------|---------|---------|------|
| `runAsServer()` | OP 4 | 奖励/交易/系统赋予 | `/give`, `/effect`, `/summon` |
| `runAsPlayer()` | 玩家自身 | 对话/玩家触发 | `/say`, `/msg`, `/tell` |

#### 11.2.2 占位符替换

```java
// ✅ 自动替换 {player} 为玩家名称
String template = "/give {player} emerald 10";
CommandExecutor.runAsServer(player, template);
// 实际执行：/give Steve emerald 10

// ✅ 支持额外预处理
String customTemplate = session.replaceVariables("/tp {player} {x} {y} {z}");
CommandExecutor.runAsPlayer(player, customTemplate);
```

**实现原理**：
```java
public static String resolvePlaceholders(ServerPlayer player, String template) {
    return template.replace("{player}", player.getGameProfile().getName());
}

private static String stripSlash(String cmd) {
    return cmd.startsWith("/") ? cmd.substring(1) : cmd;
}
```

**关键特性**：
- ✅ 自动去除前缀 `/`（兼容带或不带斜杠的命令）
- ✅ 统一的 `{player}` 占位符替换
- ✅ 抑制输出（`.withSuppressedOutput()`）避免刷屏
- ✅ 替代原有的三处分散实现（`CommandReward`、`CommandTradeOffer`、`DialogueAction.RunCommand`）

#### 11.2.3 迁移指南

```java
// ❌ 旧版：分散的实现（已废弃）
// CommandReward.java
player.getServer().getCommands().performPrefixedCommand(
    player.createCommandSourceStack(), command);

// DialogueAction.RunCommand.java  
server.getCommands().performPrefixedCommand(
    source, command.replace("{player}", playerName));

// ✅ 新版：统一使用 CommandExecutor
CommandExecutor.runAsServer(player, command);  // 奖励/交易场景
CommandExecutor.runAsPlayer(player, command);  // 对话场景
```

### 11.3 自定义动作

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

### 11.4 动作链

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

## 13.5 QuestIntelPanel - Ponder 任务情报系统

**⚠️ 重要更新**：2026-04-22 后 `QuestIntelPanel` 完全重写，集成 Ponder 引擎提供高级可视化任务情报。

### 13.5.1 核心功能

```java
// ✅ 触发 Ponder 场景展示
ResourceLocation sceneId = new ResourceLocation("my_mod:quest_tutorial");
int themeColor = 0x4FC3F7;  // 青色主题

QuestIntelPanel.trigger(sceneId, themeColor);

// ✅ 关闭面板
QuestIntelPanel.dismiss();

// ✅ 检查是否活跃
boolean active = QuestIntelPanel.isActive();

// ✅ 检查场景是否存在
boolean exists = QuestIntelPanel.hasScene(sceneId);
```

**设计特点**：
- ✅ 完美移植 Genesis 的动画参数（进入/退出时间、缓动曲线）
- ✅ 16:9 宽屏比例（400×225 像素）
- ✅ 丝滑悬停放大效果（按钮交互反馈）
- ✅ 独立时间轴动画状态（enterTimer/exitTimer）
- ✅ 支持多场景滚动（scrollForward/scrollBack）

### 13.5.2 动画控制

```java
// 播放下一个场景
QuestIntelPanel.scrollForward();

// 播放上一个场景
QuestIntelPanel.scrollBack();

// 重播当前场景
QuestIntelPanel.replay();

// 暂停/继续
QuestIntelPanel.togglePause();
boolean paused = QuestIntelPanel.isPaused();
```

**动画常量**：
```java
private static final float ENTER_TIME = 0.7f;  // 进入动画时长
private static final float EXIT_TIME = 0.5f;   // 退出动画时长
```

### 13.5.3 与 PhaseDefinition 集成

**在任务阶段中绑定 Ponder 场景**：

```java
PhaseDefinition phase = PhaseBuilder.create("combat_training")
    .displayName(Component.literal("战斗训练"))
    .description(Component.literal("通过实战提升战斗技巧，掌握基础武器使用。"))
    
    // ✅ 绑定 Ponder 情报场景
    .ponderScene(new ResourceLocation("my_mod:combat_tutorial"))
    
    .objective(ObjectiveBuilder.kill(EntityType.ZOMBIE, 5)
        .display("击杀 5 只僵尸"))
    .build();
```

**客户端自动检测**：
```java
// 当玩家查看任务阶段时，如果有绑定的 Ponder 场景
if (QuestIntelPanel.hasScene(phase.getPonderSceneId())) {
    // 显示“查看教程”按钮
    // 点击后触发 QuestIntelPanel.trigger(sceneId, themeColor)
}
```

### 13.5.4 渲染架构

**关键技术点**：

1. **双光源照明系统**：
```java
private static final Vector3f DIFFUSE_0 = new Vector3f(-0.2F, 1.0F, 0.7F).normalize();
private static final Vector3f DIFFUSE_1 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
```

2. **动态缩放追踪**（用于点击事件检测）：
```java
private static float currentScale = 1.0f;
private static float currentDrawX = 0;
private static float currentDrawY = 0;
```

3. **悬停状态插值**（丝滑放大换色）：
```java
private static final Map<String, Float> buttonHoverStates = new HashMap<>();
```

4. **SuperRenderTypeBuffer 集成**：
```java
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer;
```

### 13.5.5 Ponder 场景注册

**创建自定义 Ponder 场景**：

```java
public class MyModPonderScenes {
    public static void register() {
        // 注册任务教程场景
        ArcQuestPonderSceneRegistry.register(
            new ResourceLocation("my_mod:quest_tutorial"),
            scene -> {
                // 配置场景内容
                scene.showBasePlate();
                scene.idle(10);
                
                // 添加演示元素
                scene.world().showSection(...);
                scene.overlay().showText(...);
            }
        );
    }
}
```

**场景编译**：
```java
List<PonderScene> scenes = PonderIndex.getSceneAccess().compile(sceneId);
if (scenes == null || scenes.isEmpty()) {
    Ponder.LOGGER.warn("[ArcQuest] No Ponder scenes for: {}", sceneId);
    return;
}
```

### 13.5.6 音效反馈

```java
// 打开面板时播放
Minecraft.getInstance().getSoundManager().play(
    SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, 1.0F)
);

// 关闭面板时播放
Minecraft.getInstance().getSoundManager().play(
    SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_OUT, 1.0F)
);
```

### 13.5.7 性能优化

**防抖机制**：
```java
private static long lastTime = 0;

public static void tick() {
    if (activeScenes == null || isClosing || isPaused) return;
    
    long currentTime = System.currentTimeMillis();
    if (currentTime - lastTime < 16) return;  // 限制 60 FPS
    lastTime = currentTime;
    
    PonderUI.ponderTicks++;
    activeScenes.get(sceneIndex).tick();
}
```

**资源清理**：
```java
public static void dismiss() {
    if (!isClosing && activeScenes != null) {
        isClosing = true;
        exitTimer = 0f;
        buttonHoverStates.clear();  // 清空悬停状态
        // ...
    }
}
```

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

### 16.1 ICooldownRecord 接口

**⚠️ 重要更新**：2026-04-22 后引入 `ICooldownRecord` 接口，统一三种系统的冷却记录访问契约。

```java
/**
 * 三时钟冷却快照接口。
 * <p>
 * 统一 DialogueProgressStore.Entry、GachaDataStore.CooldownEntry
 * 以及 TradeDataStore.TradeCooldownEntry 的公共访问契约，
 * 使 UnifiedCooldownManager 无需为每种记录类型单独重载。
 */
public interface ICooldownRecord {
    /** 真实时间戳（毫秒），来自 System.currentTimeMillis()。 */
    long realTime();
    
    /** 游戏总刻数（单调，不受 /time set 影响）。 */
    long gameTime();
    
    /** 当日刻数 [0, 24000]（受 /time set 影响）。 */
    long dayTime();
    
    /** 是否有有效记录（realTime > 0）。 */
    boolean exists();
}
```

**实现类**：
- `DialogueProgressStore.Entry` - 对话进度冷却记录
- `GachaDataStore.CooldownEntry` - 抽奖冷却记录
- `TradeDataStore.TradeCooldownEntry` - 交易冷却记录

**设计优势**：
- ✅ 消除代码重复（UnifiedCooldownManager 不再需要三个重载方法）
- ✅ 统一三时钟语义（realTime/gameTime/dayTime）
- ✅ 便于扩展新系统的冷却功能

### 16.2 限购配置

```java
TradeShopBuilder.create("limited_shop")
    .displayName(Component.literal("限购商店"))
    .entry(TradeEntryBuilder.create("rare_item")
        .maxPurchases(3)  // 最多购买 3 次
        .purchaseResetByCooldown())  // 冷却重置时清零计数
    .buildAndRegister();
```

### 16.3 冷却类型

| 方法 | 说明 | 示例 |
|------|------|------|
| `.cooldown(seconds)` | 现实时间秒 | `.cooldown(3600)` - 1 小时 |
| `.cooldownGameDay()` | 游戏日重置 | 每天一次 |
| `.cooldownGameTick(tick)` | 游戏刻重置 | `.cooldownGameTick(0)` - 每天早上 6 点 |
| 无调用 | 无冷却 | 默认值 |

### 16.4 冷却配置示例

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

### 16.5 UnifiedCooldownManager 简化 API

**⚠️ 重要更新**：引入 `ICooldownRecord` 后，`UnifiedCooldownManager` 的 API 大幅简化。

```java
// ✅ 新版：统一的判断方法（支持任意 ICooldownRecord 实现）
boolean onCooldown = UnifiedCooldownManager.isOnCooldown(
    record,          // ICooldownRecord 实现
    cooldownType,    // CooldownType 枚举
    cooldownValue    // 冷却时长
);

// ❌ 旧版：需要三个重载方法（已废弃）
// isOnCooldown(DialogueProgressStore.Entry, ...)
// isOnCooldown(GachaDataStore.CooldownEntry, ...)
// isOnCooldown(TradeDataStore.TradeCooldownEntry, ...)
```

**三时钟判断逻辑**：
```java
public static boolean isOnCooldown(ICooldownRecord record, 
                                    CooldownType type, 
                                    long value) {
    if (!record.exists()) return false;
    
    return switch (type) {
        case SECONDS -> {
            long elapsed = System.currentTimeMillis() - record.realTime();
            yield elapsed < value * 1000;
        }
        case GAME_DAY -> {
            // 使用 dayTime 而非 gameTime，支持 /time set
            long currentDayTime = Minecraft.getInstance().level.getDayTime() % 24000;
            yield currentDayTime < record.dayTime();
        }
        case GAME_TICK -> {
            long currentGameTime = Minecraft.getInstance().level.getGameTime();
            yield currentGameTime < record.gameTime();
        }
    };
}
```

**关键修复**：
- ✅ GAME_DAY 冷却现在正确使用 `dayTime`（受 `/time set` 影响）
- ✅ 消除了三处重复的判断逻辑
- ✅ 支持未来扩展新的冷却记录类型

### 16.6 废弃方法清理

以下方法已被标记为 `@Deprecated`，将在未来版本中移除：

```java
// ❌ 已废弃：直接使用 ICooldownRecord 接口
@Deprecated
public static boolean isOnCooldown(DialogueProgressStore.Entry entry, ...) {
    // 内部委托给统一方法
    return isOnCooldown((ICooldownRecord) entry, type, value);
}

// ❌ 已废弃
@Deprecated  
public static boolean isOnCooldown(GachaDataStore.CooldownEntry entry, ...) {
    return isOnCooldown((ICooldownRecord) entry, type, value);
}

// ❌ 已废弃
@Deprecated
public static boolean isOnCooldown(TradeDataStore.TradeCooldownEntry entry, ...) {
    return isOnCooldown((ICooldownRecord) entry, type, value);
}
```

**迁移建议**：
```java
// ❌ 旧版
boolean onCooldown = UnifiedCooldownManager.isOnCooldown(
    (DialogueProgressStore.Entry) record, type, value);

// ✅ 新版
boolean onCooldown = UnifiedCooldownManager.isOnCooldown(
    record, type, value);  // record 实现 ICooldownRecord 即可
```

### 16.7 自动刷新

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
            case CANNOT_AFFORD ->
                player.playSound(SoundEvents.VILLAGER_NO, 1.0f, 0.8f);
            case ON_COOLDOWN ->
                player.playSound(SoundEvents.NOTE_BLOCK_BASS, 1.0f, 0.5f);
            case MAX_DRAWS_REACHED ->
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
| **`GachaOpenedEvent`** | **FORGE** | **服务端** | **抽奖界面打开** |
| **`GachaPreDrawEvent`** | **FORGE** | **服务端** | **抽奖前（可取消）** |
| **`GachaDrawingEvent`** | **FORGE** | **客户端** | **抽奖动画中** |
| **`GachaDrawSuccessEvent`** | **FORGE** | **服务端** | **抽奖成功** |
| **`GachaDrawFailedEvent`** | **FORGE** | **服务端** | **抽奖失败（含原因）** |
| **`GachaPoolRefreshEvent`** | **FORGE** | **服务端** | **奖池刷新** |

**新增事件**：
- ✅ `QuestStartedEvent` - 任务开始
- ✅ `QuestProgressChangedEvent` - 进度变化
- ✅ `QuestPhaseCompletedEvent` - 阶段完成
- ✅ `QuestFailedEvent` - 任务失败
- ✅ `DialogueNodeStartedEvent` - 节点显示（替代 DialogueSayIfEvaluatedEvent）
- ✅ `DialogueChoiceSelectedEvent` - 选项选择
- ✅ `TradePurchaseFailedEvent` - 交易失败
- ✅ **`GachaOpenedEvent`** - 抽奖界面打开
- ✅ **`GachaPreDrawEvent`** - 抽奖前（支持取消和修改保底计数）
- ✅ **`GachaDrawingEvent`** - 抽奖动画中（客户端）
- ✅ **`GachaDrawSuccessEvent`** - 抽奖成功
- ✅ **`GachaDrawFailedEvent`** - 抽奖失败（包含详细 FailReason）
- ✅ **`GachaPoolRefreshEvent`** - 奖池刷新

**删除事件**：
- ❌ `DialogueSayIfEvaluatedEvent` - 功能已被 `DialogueNodeStartedEvent` 覆盖

**重命名事件**：
- 🔄 `TradeItemPurchasedEvent` → `TradePurchasedSuccessEvent`

### 19.2 抽奖事件详解

#### 19.2.1 GachaOpenedEvent

```java
@SubscribeEvent
public static void onGachaOpened(GachaEvents.OpenedEvent event) {
    ServerPlayer player = event.getPlayer();
    String shopId = event.getShopId();
    
    LOGGER.info("Player {} opened gacha shop: {}", 
        player.getName().getString(), shopId);
    
    // 播放特殊音效
    player.playSound(SoundEvents.ANVIL_USE, 1.0f, 1.0f);
}
```

**关键字段**：
- `player`: 打开界面的玩家
- `shopId`: 抽奖商店 ID

#### 19.2.2 GachaPreDrawEvent（可取消）

```java
@SubscribeEvent
public static void onPreDraw(GachaEvents.PreDrawEvent event) {
    ServerPlayer player = event.getPlayer();
    String shopId = event.getShopId();
    int pityCount = event.getPityCount();
    
    LOGGER.info("Player {} attempting to draw from {}, pity count: {}",
        player.getName().getString(), shopId, pityCount);
    
    // ✅ 可以取消抽奖
    if (player.experienceLevel < 10) {
        event.setCanceled(true);
        player.sendSystemMessage(Component.literal("§c需要至少 10 级才能抽奖！"));
        return;
    }
    
    // ✅ 可以修改保底计数
    if (pityCount >= 45 && pityCount < 50) {
        event.setPityCount(50);  // 强制触发保底
        player.sendSystemMessage(Component.literal("§6幸运加成！保底已激活！"));
    }
}
```

**关键字段**：
- `player`: 执行抽奖的玩家
- `shopId`: 抽奖商店 ID
- `pityCount`: 当前保底计数
- `canceled`: 是否取消（默认 false）

**重要特性**：
- ✅ 支持取消抽奖（`setCanceled(true)`）
- ✅ 支持修改保底计数（`setPityCount(int)`）
- ✅ 在服务端权威判断前触发

#### 19.2.3 GachaDrawingEvent（客户端）

```java
@SubscribeEvent
public static void onDrawing(GachaEvents.DrawingEvent event) {
    // 客户端事件，用于触发动画
    Minecraft mc = Minecraft.getInstance();
    
    // 播放滚动音效
    mc.getSoundManager().play(
        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
    );
    
    LOGGER.debug("Gacha rolling animation started");
}
```

**关键字段**：
- 无额外字段（纯客户端事件）

**用途**：
- 触发 HUD 滚动动画
- 播放滚动音效
- 客户端视觉效果

#### 19.2.4 GachaDrawSuccessEvent

```java
@SubscribeEvent
public static void onDrawSuccess(GachaEvents.DrawSuccessEvent event) {
    ServerPlayer player = event.getPlayer();
    String shopId = event.getShopId();
    Rarity rarity = event.getRarity();
    ItemStack reward = event.getReward();
    int pityCount = event.getPityCount();
    boolean triggeredPity = event.isTriggeredPity();
    
    LOGGER.info("Player {} drew {} ({}) from {}, pity triggered: {}",
        player.getName().getString(),
        reward.getDisplayName().getString(),
        rarity,
        shopId,
        triggeredPity);
    
    // 稀有物品全服公告
    if (rarity == Rarity.LEGENDARY) {
        player.getServer().getPlayerList().broadcastSystemMessage(
            Component.literal("§6玩家 " + player.getName().getString() + 
                            " §6抽到了传说物品：" + reward.getDisplayName().getString()),
            false
        );
    }
    
    // 播放庆祝音效
    player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
}
```

**关键字段**：
- `player`: 抽奖的玩家
- `shopId`: 抽奖商店 ID
- `rarity`: 物品稀有度（COMMON/RARE/EPIC/LEGENDARY）
- `reward`: 获得的物品堆栈
- `pityCount`: 抽奖后的保底计数
- `triggeredPity`: 是否触发了保底

#### 19.2.5 GachaDrawFailedEvent

```java
@SubscribeEvent
public static void onDrawFailed(GachaEvents.DrawFailedEvent event) {
    ServerPlayer player = event.getPlayer();
    String shopId = event.getShopId();
    FailReason reason = event.getFailReason();
    
    LOGGER.warn("Player {} failed to draw from {}: {}",
        player.getName().getString(),
        shopId,
        reason);
    
    // 根据失败原因发送不同提示
    switch (reason) {
        case MAX_DRAWS_REACHED:
            player.sendSystemMessage(Component.literal("§c已达到抽奖次数上限！"));
            break;
        case ON_COOLDOWN:
            player.sendSystemMessage(Component.literal("§c冷却中，请稍后再试！"));
            break;
        case CONDITION_NOT_MET:
            player.sendSystemMessage(Component.literal("§c未满足前置条件！"));
            break;
        case CANNOT_AFFORD:
            player.sendSystemMessage(Component.literal("§c资源不足！"));
            break;
        case NOT_VISIBLE:
            player.sendSystemMessage(Component.literal("§c该奖池项当前不可见！"));
            break;
        default:
            player.sendSystemMessage(Component.literal("§c抽奖失败！"));
            break;
    }
}
```

**关键字段**：
- `player`: 尝试抽奖的玩家
- `shopId`: 抽奖商店 ID
- `failReason`: 失败原因枚举

**FailReason 枚举**：
```java
public enum FailReason {
    PURCHASE_LIMIT_REACHED,  // 达到限购次数
    ON_COOLDOWN,             // 冷却中
    CONDITION_NOT_MET,       // 条件不满足
    INSUFFICIENT_FUNDS,      // 资源不足
    SHOP_NOT_FOUND           // 商店不存在
}
```

#### 19.2.6 GachaPoolRefreshEvent

```java
@SubscribeEvent
public static void onPoolRefresh(GachaEvents.PoolRefreshEvent event) {
    ServerPlayer player = event.getPlayer();
    String shopId = event.getShopId();
    
    LOGGER.info("Gacha pool refreshed for player {} in shop {}",
        player.getName().getString(), shopId);
    
    // 发送通知
    player.sendSystemMessage(Component.literal("§a奖池已刷新！"));
}
```

**关键字段**：
- `player`: 关联的玩家
- `shopId`: 抽奖商店 ID

**触发时机**：
- 冷却重置时自动刷新奖池
- 手动调用 `/gacha reset` 命令

### 19.3 事件订阅

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
├── gacha          # 抽奖管理子系统 (2026-04-22 新增)
│   ├── open       # 打开抽奖界面
│   ├── list       # 列出抽奖商店
│   ├── debug      # 调试抽奖数据
│   └── reset      # 重置抽奖进度
│       ├── shop   # 重置指定商店
│       └── all    # 重置所有抽奖数据
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

### 20.5 抽奖管理命令 (2026-04-22 新增)

#### `/arcquest gacha open <player> <shop_id>`
打开抽奖界面。

**示例**：
```bash
/arcquest gacha open @p daily_gacha
```

**权限检查**：需要权限等级 ≥ 2

---

#### `/arcquest gacha list [player]`
列出玩家的抽奖历史记录。

**示例**：
```bash
# 查看自己的抽奖记录
/arcquest gacha list

# 查看指定玩家的记录
/arcquest gacha list Steve
```

**输出格式**：
```
=== Player Steve's Gacha History ===
  daily_gacha: 5 draws, pity counter: 23
  weapon_crate: 2 draws, pity counter: 8
```

---

#### `/arcquest gacha debug <player> <shop_id>`
显示抽奖的详细调试信息（定义 + 运行时状态）。

**示例**：
```bash
/arcquest gacha debug @p daily_gacha
```

**输出内容**：
- 商店定义（maxDraws、cooldownType、pityThreshold等）
- 玩家当前状态（drawCount、pityCounter、cooldownTimestamp）
- 奖池配置（稀有度分布、权重总和）

---

#### `/arcquest gacha reset <player> [shop_id|all]`
重置抽奖进度（清空抽奖次数、冷却记录、保底计数）。

**示例**：
```bash
# 重置指定商店
/arcquest gacha reset @p daily_gacha

# 重置所有抽奖数据
/arcquest gacha reset @p all
```

**重置内容**：
- ✅ 抽奖次数计数器（gachaDrawCounts）
- ✅ 冷却时间戳（cooldownRecords）
- ✅ 保底计数器（gachaPityCounters）

**权限要求**：需要权限等级 ≥ 2

**操作日志**：
```java
LOGGER.info("Admin {} reset gacha data for player {} [{}]",
    admin.getName().getString(),
    target.getName().getString(),
    shopId != null ? shopId : "ALL"
);
```

---

### 20.6 命令翻译键规范

所有命令反馈文本必须使用翻译键，禁止硬编码。

**翻译键命名规范**：
```
arc_quest.commands.<subsystem>.<action>.<key>
```

**示例**：
```properties
# 中文 (ArcQuestZHLangProvider)
arc_quest.commands.gacha.reset.success=已重置玩家 {0} 的抽奖数据 [{1}]
arc_quest.commands.gacha.reset.all=已重置玩家 {0} 的所有抽奖数据
arc_quest.commands.gacha.open.no_permission=你没有权限打开抽奖界面

# 英文 (ArcQuestENLangProvider)
arc_quest.commands.gacha.reset.success=Reset gacha data for player {0} [{1}]
arc_quest.commands.gacha.reset.all=Reset all gacha data for player {0}
arc_quest.commands.gacha.open.no_permission=You don't have permission to open gacha interface
```

**Data Generator 自动更新**：
```java
// ArcQuestZHLangProvider.java
add("arc_quest.commands.gacha.reset.success", 
    "已重置玩家 {0} 的抽奖数据 [{1}]");
add("arc_quest.commands.gacha.reset.all", 
    "已重置玩家 {0} 的所有抽奖数据");

// ArcQuestENLangProvider.java
add("arc_quest.commands.gacha.reset.success", 
    "Reset gacha data for player {0} [{1}]");
add("arc_quest.commands.gacha.reset.all", 
    "Reset all gacha data for player {0}");
```

**运行 Data Generator**：
```bash
./gradlew runData
```

---

## 21. 网络通信

### 21.1 网络架构

**C2S 数据包**（客户端 → 服务端）：
- `C2SRequestQuestActionPacket`: 任务操作请求
- `C2SDialogueChoicePacket`: 对话选择
- `C2SRequestTradePacket`: 商店交互
- `C2SDrawGachaPacket`: 抽奖请求 (2026-04-22 新增)

**S2C 数据包**（服务端 → 客户端）：
- `S2CSyncFullDataPacket`: 全量数据同步
- `S2CDeltaProgressPacket`: 增量进度同步
- `S2CSyncQuestStatePacket`: 任务状态同步
- `S2COpenDialoguePacket`: 打开对话
- `S2COpenTradePacket`: 打开商店
- `S2COpenGachaPacket`: 打开抽奖界面 (2026-04-22 新增)
- `S2CDrawResultPacket`: 抽奖结果 (2026-04-22 新增)
- `S2CSyncMarkersPacket`: 同步世界标记 (2026-04-22 新增)

### 21.2 S2CSyncMarkersPacket - 世界标记系统 (2026-04-22 新增)

**功能概述**：
用于服务端向客户端同步任务相关的3D世界标记（Waypoints），支持添加、移除和清空操作。

**三种操作类型**：
```java
public static final byte OP_CLEAR  = 0;  // 清空所有标记
public static final byte OP_ADD    = 1;  // 批量添加标记
public static final byte OP_REMOVE = 2;  // 移除单个标记
```

#### 21.2.1 数据结构

**MarkerEntry 结构**：
```java
public record MarkerEntry(
    String id,       // 唯一标识符
    String type,     // 标记类型（QUEST/TARGET/CUSTOM等）
    double x,        // X坐标
    double y,        // Y坐标
    double z,        // Z坐标
    String label,    // 显示文本
    int color        // ARGB颜色值
) {}
```

#### 21.2.2 使用示例

**清空所有标记**：
```java
// 服务端发送
ArcQuestNetwork.sendToPlayer(new S2CSyncMarkersPacket(), player);

// 客户端处理
QuestMarkerManager.INSTANCE.clear();
```

**批量添加标记**：
```java
// 构建标记列表
List<MarkerEntry> markers = List.of(
    new MarkerEntry(
        "quest_main_target",
        "QUEST",
        100.5, 64.0, 200.3,
        "前往村庄",
        0xFFFF0000  // 红色
    ),
    new MarkerEntry(
        "quest_secondary",
        "TARGET",
        150.0, 70.0, 250.0,
        "收集木材",
        0xFF00FF00  // 绿色
    )
);

// 服务端发送
ArcQuestNetwork.sendToPlayer(new S2CSyncMarkersPacket(markers), player);

// 客户端处理
for (MarkerEntry e : pkt.entries) {
    QuestMarkerType type;
    try {
        type = QuestMarkerType.valueOf(e.type);
    } catch (IllegalArgumentException ex) {
        type = QuestMarkerType.CUSTOM;  // 容错处理
    }
    
    QuestMarkerData data = new QuestMarkerData.Builder(
            e.id, e.x, e.y, e.z, e.label)
            .type(type)
            .color(e.color)
            .build();
    
    QuestMarkerManager.INSTANCE.add(data);
}
```

**移除单个标记**：
```java
// 服务端发送
ArcQuestNetwork.sendToPlayer(
    new S2CSyncMarkersPacket("quest_main_target"), 
    player
);

// 客户端处理
QuestMarkerManager.INSTANCE.remove(pkt.removeId);
```

#### 21.2.3 编码/解码

**编码逻辑**：
```java
public static void encode(S2CSyncMarkersPacket pkt, FriendlyByteBuf buf) {
    buf.writeByte(pkt.op);
    if (pkt.op == OP_ADD) {
        buf.writeInt(pkt.entries.size());
        for (MarkerEntry e : pkt.entries) {
            buf.writeUtf(e.id);
            buf.writeUtf(e.type);
            buf.writeDouble(e.x);
            buf.writeDouble(e.y);
            buf.writeDouble(e.z);
            buf.writeUtf(e.label);
            buf.writeInt(e.color);
        }
    } else if (pkt.op == OP_REMOVE) {
        buf.writeUtf(pkt.removeId);
    }
    // OP_CLEAR 无需额外数据
}
```

**解码逻辑**：
```java
public static S2CSyncMarkersPacket decode(FriendlyByteBuf buf) {
    byte op = buf.readByte();
    if (op == OP_ADD) {
        int count = buf.readInt();
        List<MarkerEntry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new MarkerEntry(
                    buf.readUtf(), buf.readUtf(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readUtf(), buf.readInt()));
        }
        return new S2CSyncMarkersPacket(list);
    } else if (op == OP_REMOVE) {
        return new S2CSyncMarkersPacket(buf.readUtf());
    }
    return new S2CSyncMarkersPacket();  // OP_CLEAR
}
```

#### 21.2.4 与任务系统集成

**自动同步标记**：
```java
// 当任务阶段变更时，自动更新标记
@SubscribeEvent
public static void onPhaseChanged(QuestPhaseChangedEvent event) {
    ServerPlayer player = event.getPlayer();
    String newPhase = event.getNewPhaseId();
    
    // 从 PhaseDefinition 中读取标记配置
    PhaseDefinition phase = quest.getPhase(newPhase);
    if (phase.hasMarker()) {
        MarkerConfig config = phase.getMarker();
        
        // 发送新标记
        MarkerEntry entry = new MarkerEntry(
            event.getQuestId().toString(),
            "QUEST",
            config.x(), config.y(), config.z(),
            config.label(),
            config.color()
        );
        
        ArcQuestNetwork.sendToPlayer(
            new S2CSyncMarkersPacket(List.of(entry)),
            player
        );
    }
}
```

**清除已完成任务的标记**：
```java
@SubscribeEvent
public static void onQuestCompleted(QuestCompletedEvent event) {
    ServerPlayer player = event.getPlayer();
    String questId = event.getQuestId().toString();
    
    // 移除该任务的标记
    ArcQuestNetwork.sendToPlayer(
        new S2CSyncMarkersPacket(questId),
        player
    );
}
```

#### 21.2.5 性能优化

**批量操作减少网络包**：
```java
// ❌ 低效：多次发送单个标记
for (Marker marker : markers) {
    ArcQuestNetwork.sendToPlayer(
        new S2CSyncMarkersPacket(List.of(marker)),
        player
    );
}

// ✅ 高效：一次性发送所有标记
ArcQuestNetwork.sendToPlayer(
    new S2CSyncMarkersPacket(markers),
    player
);
```

**防抖机制**：
```java
// 避免频繁更新同一标记
private static final Map<String, Long> lastUpdateTimes = new HashMap<>();

public static void updateMarker(String id, MarkerEntry entry) {
    long now = System.currentTimeMillis();
    Long lastTime = lastUpdateTimes.get(id);
    
    if (lastTime != null && now - lastTime < 1000) {
        return;  // 1秒内不重复更新
    }
    
    lastUpdateTimes.put(id, now);
    // 发送更新...
}
```

### 21.3 同步策略

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

### 21.4 客户端缓存

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

### 23.1 O(1) 目标追踪

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

### 23.2 增量网络同步

**全量同步**：~5KB（登录时）
**增量同步**：~50B（进度更新）

**带宽节省**：90%+

### 23.3 UI 渲染优化

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

**Ponder 场景渲染优化**（2026-04-22 新增）：
- ✅ **双光源照明系统**: 避免单光源导致的阴影过暗
- ✅ **动态缩放追踪**: 记录 currentScale/currentDrawX/currentDrawY 用于点击事件检测
- ✅ **悬停状态插值**: HashMap 缓存按钮悬停状态，实现丝滑放大换色
- ✅ **投影计算缓存**: 世界坐标到屏幕坐标的转换结果缓存，减少重复计算

**标记系统投影优化**（2026-04-22 新增）：
- ✅ **批量操作**: S2CSyncMarkersPacket 支持一次性发送多个标记，减少网络包数量
- ✅ **防抖机制**: 短时间内多次标记更新合并为一次发送
- ✅ **距离剔除**: 只渲染玩家视野范围内的标记，减少渲染开销

### 23.4 缓存机制

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

**客户端缓存线程安全**：
- ✅ ClientTradeCache 使用 ConcurrentHashMap 保证并发读取安全
- ✅ GachaRegistry 使用 CopyOnWriteArrayList 支持并发遍历
- ✅ QuestCapability 关键方法使用 synchronized 保证原子性

---

## 24. 最佳实践

### 24.1 使用 Builder 模式

```java
// ✅ 推荐
QuestBuilder.create("quest_id")
    .category(QuestCategory.MAIN)
    .phase(phase -> phase.objective(obj -> obj.count(5)))
    .buildAndRegister();

// ❌ 避免
new QuestDefinition(...);  // 参数过多
```

### 24.2 条件判断做空值检查

```java
// ✅ 安全
.visibleCondition((player, ...) -> {
    if (player == null) return false;
    return player.getHealth() > 10.0f;
})

// ❌ 危险（NPE）
.visibleCondition((player, ...) -> player.getHealth() > 10.0f)
```

### 24.3 服务端验证

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

### 24.4 国际化支持

```java
// ✅ 使用翻译键
Component.translatable("arc_quest.quest.my_quest.name")

// ❌ 硬编码
Component.literal("My Quest")
```

### 24.5 错误处理

```java
// 奖励发放容错
try {
    reward.grant(player);
} catch (Exception e) {
    LOGGER.error("Reward failed: {}", e.getMessage(), e);
    // 继续发放其他奖励
}
```

### 24.6 抽奖系统设计模式（2026-04-22 新增）

**服务端权威架构**：
```java
// ✅ 所有抽奖逻辑在服务端执行
@SubscribeEvent
public static void onDrawGacha(C2SDrawGachaPacket event) {
    ServerPlayer player = event.getPlayer();
    synchronized (player) {  // 原子操作
        // 1. 检查条件（限购/冷却/前置条件）
        // 2. 扣除成本
        // 3. 执行抽奖算法
        // 4. 发放奖励
        // 5. 触发事件
    }
}
```

**GachaSession 临时对象**：
```java
// ✅ 每次请求创建新的会话对象，避免状态污染
GachaSession session = new GachaSession(player, shopId);
session.checkAndResetDraws();  // 重置逻辑
session.incrementDrawCount();  // 计数递增
```

**Ponder 场景设计规范**：
```java
// ✅ 每个任务阶段绑定独立的 Ponder 场景
PhaseDefinition phase = PhaseBuilder.create()
    .ponderSceneId(new ResourceLocation("my_mod:quest_tutorial"))
    .description(Component.translatable("quest.phase.description"))
    .build();

// ✅ 场景注册在 ModClientEvents.onRegisterScenes 中完成
@SubscribeEvent
public static void onRegisterScenes(RegisterScenesEvent event) {
    event.register(new ResourceLocation("my_mod:quest_tutorial"), scene -> {
        // 定义场景元素和动画
    });
}
```

---

## 25. 抽奖系统 (Gacha System)

### 25.1 核心概念

抽奖系统是 Arc Quest 提供的一个**随机奖励获取机制**,允许玩家通过消耗资源从奖池中抽取物品。

**关键特性**:
- ✅ **服务端权威架构**: 所有抽奖逻辑在服务端执行,客户端仅负责渲染
- ✅ **稀有度系统**: 支持 Common/Rare/Epic/Legendary 等多级稀有度配置
- ✅ **保底机制**: 连续未中大奖时自动提升概率,确保公平性
- ✅ **限购与冷却**: 支持每日/每周/自定义时间间隔的抽奖限制
- ✅ **完整事件系统**: 7个Forge事件覆盖抽奖全流程
- ✅ **三阶段HUD**: 预览 → 滚动动画 → 结算展示的流畅体验

**与商店系统的对比**:
| 特性 | 商店系统 | 抽奖系统 |
|------|---------|----------|
| 确定性 | 确定获得指定物品 | 随机抽取奖池物品 |
| 限购粒度 | 商品级 (`TradeEntry`) | 商店级 (`GachaShopDefinition`) |
| 会话管理 | `TradeSession` (多商品) | `GachaSession` (单奖池) |
| 网络包 | `C2SRequestTradePacket` (Action枚举) | `C2SDrawGachaPacket` (单一操作) |
| 客户端缓存 | `ClientTradeCache` (数组支持多商品) | `ClientGachaCache` (单值) |

---

### 25.2 Builder API 详解

#### 25.2.1 GachaShopBuilder 基础用法

```java
GachaShopDefinition shop = GachaShopBuilder.create("daily_gacha")
    .displayName(Component.translatable("gacha.daily_gacha.name"))
    .description(Component.translatable("gacha.daily_gacha.desc"))
    .themeColor(0xFFD700)  // 金色主题
    .drawCostItem(Items.EMERALD, 5)  // 每次抽奖消耗5个绿宝石
    .maxDraws(10)  // 最多抽奖10次(0表示无限制)
    .cooldownGameDay()  // 每天重置限购计数
    
    // 添加奖池条目
    .item(GachaItemBuilder.create("common_iron")
        .rarity(Rarity.COMMON)  // 普通稀有度
        .item(Items.IRON_INGOT, 1, 3)  // 1-3个铁锭
        .weight(50))  // 权重50
    
    .item(GachaItemBuilder.create("rare_diamond")
        .rarity(Rarity.RARE)
        .item(Items.DIAMOND, 1, 2)
        .weight(20))
    
    .item(GachaItemBuilder.create("epic_netherite")
        .rarity(Rarity.EPIC)
        .item(Items.NETHERITE_INGOT, 1)
        .weight(5)
        .pityTrigger(true))  // 触发保底计数
    
    .buildAndRegister();
```

**参数说明**:
- `create(String shopId)`: **智能命名空间解析**
  - 如果包含 `:` (如 `"my_mod:shop"`),直接使用
  - 如果不包含 `:` (如 `"shop"`),自动添加 `arc_quest:` 前缀
- `.drawCostItem(Item, count)`: 设置抽奖成本
- `.maxDraws(int)`: 最大抽奖次数(0=无限制)
- `.cooldown(seconds)`: 现实时间冷却(秒)
- `.cooldownGameDay()`: 游戏日重置(每天凌晨6点)
- `.cooldownGameTick(tick)`: 游戏刻重置(0-23999)
- `.item(GachaItemBuilder)`: 添加奖池条目(自动build)
- `.buildAndRegister()`: 构建并注册到系统

#### 25.2.2 GachaItemBuilder 配置

```java
GachaItemBuilder.create("legendary_sword")
    .rarity(Rarity.LEGENDARY)  // 传说稀有度
    .item(Items.NETHERITE_SWORD, 1)  // 固定数量
    .item(Items.ENCHANTED_GOLDEN_APPLE, 1, 3)  // 随机数量 1-3
    .weight(2)  // 权重(越低越稀有)
    .pityTrigger(true)  // 计入保底计数
    .visibleCondition(ICondition.flagSet("unlocked_legendary"))  // 可见条件
    .build();
```

**稀有度枚举**:
```java
public enum Rarity {
    COMMON(0xFFFFFF, "Common"),      // 白色
    UNCOMMON(0x00FF00, "Uncommon"),  // 绿色
    RARE(0x0088FF, "Rare"),          // 蓝色
    EPIC(0xAA00FF, "Epic"),          // 紫色
    LEGENDARY(0xFFAA00, "Legendary"); // 金色
}
```

**权重系统**:
- 总权重 = 所有条目权重之和
- 单个条目概率 = 条目权重 / 总权重
- 示例: 权重50的条目在总权重100时概率为50%

#### 25.2.3 保底机制配置

```java
GachaShopBuilder.create("pity_gacha")
    .pityThreshold(50)  // 保底阈值:50次未中EPIC及以上必出
    .pityResetOnTrigger(true)  // 触发保底后重置计数
    .pityItems(Rarity.EPIC, Rarity.LEGENDARY)  // 保底判定稀有度
    
    .item(GachaItemBuilder.create("common_item")
        .rarity(Rarity.COMMON)
        .weight(80)
        .pityTrigger(false))  // 不计入保底
    
    .item(GachaItemBuilder.create("epic_item")
        .rarity(Rarity.EPIC)
        .weight(5)
        .pityTrigger(true))  // ✅ 计入保底
    
    .buildAndRegister();
```

**保底工作原理**:
1. 每次抽奖未中 `pityItems` 指定的稀有度时,保底计数+1
2. 当计数达到 `pityThreshold` 时,下次抽奖**必定**获得EPIC或LEGENDARY
3. 触发保底后,计数重置为0(如果 `pityResetOnTrigger=true`)

---

### 25.3 网络通信

#### 25.3.1 C2SDrawGachaPacket (客户端 → 服务端)

**用途**: 请求执行一次抽奖

**字段**:
```java
public class C2SDrawGachaPacket {
    private final String shopId;  // 抽奖商店ID
}
```

**服务端处理流程**:
```java
public static void handle(C2SDrawGachaPacket packet, Supplier<NetworkEvent.Context> ctx) {
    ctx.get().enqueueWork(() -> {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        
        // 1. 创建会话(对标商店系统)
        GachaSession session = new GachaSession(player, packet.shopId());
        
        // 2. 前置检查(服务端权威)
        synchronized (session) {
            // 2.1 检查限购
            if (!session.canDraw()) {
                triggerFailedEvent(player, packet.shopId(), FailReason.PURCHASE_LIMIT_REACHED);
                return;
            }
            
            // 2.2 检查冷却
            if (session.isOnCooldown()) {
                triggerFailedEvent(player, packet.shopId(), FailReason.COOLDOWN_ACTIVE);
                return;
            }
            
            // 2.3 检查成本
            if (!session.hasEnoughCost()) {
                triggerFailedEvent(player, packet.shopId(), FailReason.INSUFFICIENT_FUNDS);
                return;
            }
            
            // 3. 执行抽奖(原子操作)
            DrawResult result = session.executeDraw();
            
            // 4. 扣除成本
            session.deductCost();
            
            // 5. 增加抽奖计数
            session.incrementDrawCount();
            
            // 6. 发放奖励
            result.grantRewards(player);
            
            // 7. 发送结果到客户端
            ArcQuestNetwork.sendToPlayer(new S2CDrawResultPacket(result), player);
            
            // 8. 刷新客户端数据(更新冷却/限购状态)
            ArcQuestNetwork.sendToPlayer(
                S2COpenGachaPacket.buildSnapshot(player, packet.shopId()), 
                player
            );
            
            // 9. 触发成功事件
            MinecraftForge.EVENT_BUS.post(new GachaEvents.PostDrawEvent(player, packet.shopId(), result));
        }
    });
}
```

**关键点**:
- ✅ 使用 `synchronized` 保证原子性(防止并发抽奖)
- ✅ 遵循**先检查限购,再检查冷却**的优先级
- ✅ 抽奖后立即刷新客户端数据(修复时序竞态)
- ✅ 失败时触发 `DrawFailedEvent` 而非静默失败

#### 25.3.2 S2COpenGachaPacket (服务端 → 客户端)

**用途**: 打开抽奖界面,同步完整数据

**字段**:
```java
public class S2COpenGachaPacket {
    private final String shopId;
    private final Component displayName;
    private final Component description;
    private final int themeColor;
    private final ItemStack costItem;
    private final int maxDraws;
    private final int currentDraws;
    private final long cooldownTimestamp;
    private final int cooldownType;  // CooldownType枚举ordinal
    private final List<GachaItemData> items;  // 奖池条目列表
    private final int pityCounter;  // 当前保底计数
    private final int pityThreshold;  // 保底阈值
}
```

**客户端处理**:
```java
public static void handle(S2COpenGachaPacket packet, Supplier<NetworkEvent.Context> ctx) {
    ctx.get().enqueueWork(() -> {
        ClientGachaCache.INSTANCE.updateGachaSession(packet);
        
        // 打开GUI
        Minecraft.getInstance().setScreen(new GachaScreen(packet));
    });
}
```

#### 25.3.3 S2CDrawResultPacket (服务端 → 客户端)

**用途**: 通知客户端抽奖结果,触发动画

**字段**:
```java
public class S2CDrawResultPacket {
    private final GachaItemData wonItem;  // 中奖物品
    private final int actualCount;  // 实际数量(随机范围)
    private final boolean pityTriggered;  // 是否触发保底
    private final int remainingDraws;  // 剩余抽奖次数
    private final long cooldownRemaining;  // 冷却剩余时间(毫秒)
}
```

**客户端处理**:
```java
public static void handle(S2CDrawResultPacket packet, Supplier<NetworkEvent.Context> ctx) {
    ctx.get().enqueueWork(() -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof GachaScreen gachaScreen) {
            // 触发第二阶段:滚动动画
            gachaScreen.triggerRollingAnimation(packet);
        }
    });
}
```

**三阶段HUD流程**:
1. **Phase 1 - Preview**: `GachaPreviewPanel` 显示奖池预览
2. **Phase 2 - Rolling**: `GachaRollerPanel` 播放滚动动画(由S2CDrawResultPacket触发)
3. **Phase 3 - Result**: `GachaResultRenderer` 展示最终结果和确认按钮

---

### 25.4 客户端缓存 (ClientGachaCache)

#### 25.4.1 核心API

```java
public class ClientGachaCache {
    private static final ClientGachaCache INSTANCE = new ClientGachaCache();
    
    // 获取单例
    public static ClientGachaCache getInstance() { return INSTANCE; }
    
    // 更新会话数据(由S2COpenGachaPacket调用)
    public void updateGachaSession(S2COpenGachaPacket packet) { ... }
    
    // 检查是否在冷却中
    public boolean isOnCooldown(String shopId) { ... }
    
    // 获取冷却倒计时文本(如 "23:45:12")
    public String getCooldownText(String shopId) { ... }
    
    // 判断是否可以抽奖(综合检查:冷却/限购/成本)
    public boolean canDraw(String shopId) { ... }
    
    // 获取剩余抽奖次数(-1表示无限制)
    public int getRemainingDraws(String shopId) { ... }
    
    // 获取当前保底计数
    public int getPityCounter(String shopId) { ... }
    
    // 获取奖池条目列表
    public List<GachaItemData> getItems(String shopId) { ... }
}
```

#### 25.4.2 使用示例

**在GUI中检查按钮状态**:
```java
public class GachaPreviewPanel {
    private void renderButton(GuiGraphics g, int x, int y) {
        ClientGachaCache cache = ClientGachaCache.getInstance();
        boolean onCooldown = cache.isOnCooldown(currentShopId);
        boolean hasLimit = cache.getRemainingDraws(currentShopId) == 0;
        boolean canAfford = hasEnoughCost();
        
        boolean enabled = !onCooldown && !hasLimit && canAfford;
        
        // 渲染按钮(禁用状态灰色显示)
        drawButton(g, x, y, enabled ? themeColor : 0x666666);
        
        // Tooltip显示详细信息
        if (isHovered) {
            List<Component> tooltip = new ArrayList<>();
            if (onCooldown) {
                tooltip.add(Component.literal("§c冷却中: " + cache.getCooldownText(currentShopId)));
            } else if (hasLimit) {
                tooltip.add(Component.literal("§c已达到限购上限"));
            } else if (!canAfford) {
                tooltip.add(Component.literal("§c成本不足"));
            } else {
                tooltip.add(Component.literal("§a点击抽奖"));
            }
            g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }
}
```

**查询保底进度**:
```java
ClientGachaCache cache = ClientGachaCache.getInstance();
int pityCounter = cache.getPityCounter("arc_quest:daily_gacha");
int pityThreshold = 50;  // 从配置读取
float progress = (float) pityCounter / pityThreshold;

// 渲染进度条
int barW = 100;
int fillW = (int) (barW * progress);
g.fill(x, y, x + barW, y + 3, 0x333333);  // 背景
g.fill(x, y, x + fillW, y + 3, 0xFFAA00);  // 填充(金色)
```

---

### 25.5 事件系统

#### 25.5.1 可用事件

| 事件类 | 触发时机 | 关键字段 | 用途 |
|--------|---------|----------|------|
| `OpenedEvent` | 界面打开 | player, shopId | 监听抽奖界面开启 |
| `PreDrawEvent` | 抽奖前 | player, shopId, **可取消** | 拦截抽奖/修改保底计数 |
| `DrawingEvent` | 动画播放中 | player, shopId, wonItem | 监听滚动动画开始 |
| `PostDrawEvent` | 抽奖成功 | player, shopId, result | 监听抽奖完成 |
| `DrawFailedEvent` | 抽奖失败 | player, shopId, **FailReason** | 监听失败原因 |
| `PityEarlyTriggerEvent` | 保底提前触发 | player, shopId, pityCounter, pityThreshold | 监听保底激活(含进度百分比) |
| `DrawLimitResetEvent` | 限购重置 | player, shopId, previousDrawCount, ResetReason | 监听到期重置(COOLDOWN_EXPIRED/CUSTOM_CONDITION) |
| `PoolRefreshEvent` | 奖池刷新 | player, shopId, oldWeights, newWeights | 监听动态权重变化 |

**FailReason 枚举**:
```java
public enum FailReason {
    NOT_VISIBLE,              // 可见性条件不满足(GachaItem.visibleCondition)
    CONDITION_NOT_MET,        // 前置条件不满足(任务/等级等)
    ON_COOLDOWN,              // 冷却中
    MAX_DRAWS_REACHED,        // 达到抽奖次数上限
    CANNOT_AFFORD,            // 无法支付成本
    UNKNOWN                   // 未知错误(兜底)
}
```

#### 25.5.2 事件监听示例

```java
@Mod.EventBusSubscriber(modid = "my_addon", bus = Bus.FORGE)
public class GachaEventHandler {
    
    @SubscribeEvent
    public static void onGachaOpened(GachaEvents.OpenedEvent event) {
        ServerPlayer player = event.getPlayer();
        String shopId = event.getShopId();
        
        LOGGER.info("Player {} opened gacha: {}", 
            player.getName().getString(), shopId);
        
        // 播放打开音效
        player.playSound(SoundEvents.ANVIL_USE, 1.0f, 1.0f);
    }
    
    @SubscribeEvent
    public static void onPreDraw(GachaEvents.PreDrawEvent event) {
        ServerPlayer player = event.getPlayer();
        String shopId = event.getShopId();
        
        // 示例: VIP玩家保底计数减半
        if (player.getTags().contains("vip")) {
            int currentPity = event.getPityCounter();
            event.setPityCounter(currentPity / 2);
            LOGGER.info("VIP player {} pity counter halved: {} → {}", 
                player.getName().getString(), currentPity, currentPity / 2);
        }
        
        // 示例: 特殊日期禁止抽奖
        if (isSpecialEventDay()) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("§c活动期间暂停抽奖!"));
        }
    }
    
    @SubscribeEvent
    public static void onPostDraw(GachaEvents.PostDrawEvent event) {
        ServerPlayer player = event.getPlayer();
        String shopId = event.getShopId();
        DrawResult result = event.getResult();
        
        LOGGER.info("Player {} drew {} x{} from {}", 
            player.getName().getString(),
            result.getItem().getItemStack().getHoverName().getString(),
            result.getActualCount(),
            shopId);
        
        // 全服公告传说物品
        if (result.getItem().getRarity() == Rarity.LEGENDARY) {
            player.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("§6§l[传说] " + player.getName().getString() + 
                                " §6抽中了 " + result.getItem().getItemStack().getHoverName().getString() + "!"),
                false
            );
        }
    }
    
    @SubscribeEvent
    public static void onDrawFailed(GachaEvents.DrawFailedEvent event) {
        ServerPlayer player = event.getPlayer();
        String shopId = event.getShopId();
        FailReason reason = event.getReason();
        
        LOGGER.warn("Gacha failed for player {}: reason={}", 
            player.getName().getString(), reason);
        
        // 根据失败原因播放不同音效
        switch (reason) {
            case CANNOT_AFFORD ->
                player.playSound(SoundEvents.VILLAGER_NO, 1.0f, 0.8f);
            case ON_COOLDOWN ->
                player.playSound(SoundEvents.NOTE_BLOCK_BASS, 1.0f, 0.5f);
            case MAX_DRAWS_REACHED ->
                player.playSound(SoundEvents.NOTE_BLOCK_HAT, 1.0f, 1.2f);
            case NOT_VISIBLE ->
                player.playSound(SoundEvents.NOTE_BLOCK_PLING, 1.0f, 0.6f);
            default -> {}
        }
    }
    
    @SubscribeEvent
    public static void onPityEarlyTrigger(GachaEvents.PityEarlyTriggerEvent event) {
        ServerPlayer player = event.getPlayer();
        String shopId = event.getShopId();
        int currentPityCounter = event.getCurrentPityCounter();
        int pityThreshold = event.getPityThreshold();
        boolean willResetPity = event.willResetPity();
        
        // 计算保底进度百分比
        int progressPercent = event.getPityProgressPercent();
        
        player.sendSystemMessage(Component.literal(
            "§6§l保底提前触发! 进度: " + progressPercent + "% (" + currentPityCounter + "/" + pityThreshold + ")"));
        
        if (willResetPity) {
            player.sendSystemMessage(Component.literal("§e保底进度已重置"));
        }
        
        // 播放特殊音效
        player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.5f);
    }
    
    @SubscribeEvent
    public static void onDrawLimitReset(GachaEvents.DrawLimitResetEvent event) {
        ServerPlayer player = event.getPlayer();
        String shopId = event.getShopId();
        int previousDrawCount = event.getPreviousDrawCount();
        GachaEvents.DrawLimitResetEvent.ResetReason reason = event.getReason();
        
        String reasonText = switch (reason) {
            case COOLDOWN_EXPIRED -> "冷却过期";
            case CUSTOM_CONDITION -> "自定义条件满足";
        };
        
        LOGGER.info("Gacha limit reset for player {}: {} → 0 (原因: {})", 
            player.getName().getString(), previousDrawCount, reasonText);
        
        // 发送通知
        player.sendSystemMessage(Component.literal(
            "§a抽奖限购已重置! 你现在可以再次抽奖了。"));
    }
}
```

---

### 25.6 示例代码

#### 25.6.1 示范商店配置

```java
public class DemoGachaShops {
    
    public static void registerAll() {
        registerDailySupplies();
        registerWeaponCrate();
        registerLegendaryVault();
    }
    
    /**
     * 每日补给箱 - 低成本高频次
     */
    private static void registerDailySupplies() {
        GachaShopBuilder.create("daily_supplies")
            .displayName(Component.translatable("gacha.daily_supplies.name"))
            .description(Component.translatable("gacha.daily_supplies.desc"))
            .themeColor(0x00FF00)  // 绿色主题
            .drawCostItem(Items.EMERALD, 1)  // 1个绿宝石
            .maxDraws(5)  // 每天5次
            .cooldownGameDay()  // 每天重置
            .pityThreshold(20)  // 20次保底
            .pityResetOnTrigger(true)
            
            // 普通物品(70%概率)
            .item(GachaItemBuilder.create("bread")
                .rarity(Rarity.COMMON)
                .item(Items.BREAD, 3, 8)
                .weight(40))
            
            .item(GachaItemBuilder.create("torch")
                .rarity(Rarity.COMMON)
                .item(Items.TORCH, 8, 16)
                .weight(30))
            
            // 稀有物品(25%概率)
            .item(GachaItemBuilder.create("iron_ingot")
                .rarity(Rarity.RARE)
                .item(Items.IRON_INGOT, 2, 5)
                .weight(15)
                .pityTrigger(true))
            
            .item(GachaItemBuilder.create("gold_ingot")
                .rarity(Rarity.RARE)
                .item(Items.GOLD_INGOT, 1, 3)
                .weight(10)
                .pityTrigger(true))
            
            // 史诗物品(5%概率,保底目标)
            .item(GachaItemBuilder.create("diamond")
                .rarity(Rarity.EPIC)
                .item(Items.DIAMOND, 1, 2)
                .weight(5)
                .pityTrigger(true))
            
            .buildAndRegister();
    }
    
    /**
     * 武器宝箱 - 中成本高回报
     */
    private static void registerWeaponCrate() {
        GachaShopBuilder.create("weapon_crate")
            .displayName(Component.translatable("gacha.weapon_crate.name"))
            .themeColor(0xFF4444)  // 红色主题
            .drawCostItem(Items.DIAMOND, 3)
            .maxDraws(3)
            .cooldown(86400)  // 24小时现实时间
            .pityThreshold(10)
            
            .item(GachaItemBuilder.create("stone_sword")
                .rarity(Rarity.COMMON)
                .item(Items.STONE_SWORD, 1)
                .weight(50))
            
            .item(GachaItemBuilder.create("iron_sword")
                .rarity(Rarity.UNCOMMON)
                .item(Items.IRON_SWORD, 1)
                .weight(30))
            
            .item(GachaItemBuilder.create("diamond_sword")
                .rarity(Rarity.RARE)
                .item(Items.DIAMOND_SWORD, 1)
                .weight(15)
                .pityTrigger(true))
            
            .item(GachaItemBuilder.create("netherite_sword")
                .rarity(Rarity.LEGENDARY)
                .item(Items.NETHERITE_SWORD, 1)
                .weight(5)
                .pityTrigger(true))
            
            .buildAndRegister();
    }
    
    /**
     * 传说宝库 - 高门槛极高回报
     */
    private static void registerLegendaryVault() {
        GachaShopBuilder.create("legendary_vault")
            .displayName(Component.translatable("gacha.legendary_vault.name"))
            .themeColor(0xFFD700)  // 金色主题
            .drawCostItem(Items.NETHERITE_INGOT, 1)
            .maxDraws(1)  // 每周1次
            .cooldown(7 * 86400)  // 7天
            .pityThreshold(5)
            .visibleCondition(ICondition.flagSet("unlocked_legendary_vault"))
            
            .item(GachaItemBuilder.create("enchanted_golden_apple")
                .rarity(Rarity.EPIC)
                .item(Items.ENCHANTED_GOLDEN_APPLE, 1)
                .weight(40)
                .pityTrigger(true))
            
            .item(GachaItemBuilder.create("totem_of_undying")
                .rarity(Rarity.EPIC)
                .item(Items.TOTEM_OF_UNDYING, 1)
                .weight(30)
                .pityTrigger(true))
            
            .item(GachaItemBuilder.create("elytra")
                .rarity(Rarity.LEGENDARY)
                .item(Items.ELYTRA, 1)
                .weight(20)
                .pityTrigger(true))
            
            .item(GachaItemBuilder.create("dragon_egg")
                .rarity(Rarity.LEGENDARY)
                .item(Items.DRAGON_EGG, 1)
                .weight(10)
                .pityTrigger(true))
            
            .buildAndRegister();
    }
}
```

#### 25.6.2 命令系统扩展

附属模组可以通过以下方式注册自己的抽奖命令:

```java
@Mod.EventBusSubscriber(modid = "my_addon", bus = Bus.FORGE)
public class MyAddonGachaCommands {
    
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        
        dispatcher.register(
            Commands.literal("mygacha")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("open")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("shop_id", StringArgument.string())
                            .executes(ctx -> {
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                String shopId = StringArgument.getString(ctx, "shop_id");
                                
                                // 打开抽奖界面
                                ArcQuestNetwork.sendToPlayer(
                                    S2COpenGachaPacket.buildSnapshot(target, shopId),
                                    target
                                );
                                
                                ctx.getSource().sendSuccess(
                                    () -> Component.literal("Opened gacha for " + target.getName().getString()),
                                    true
                                );
                                return 1;
                            })
                        )
                    )
                )
        );
    }
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

### 抽奖系统 (新增)

```java
// 创建抽奖商店
GachaShopBuilder.create("daily_gacha")
    .displayName(Component.translatable("gacha.daily.name"))
    .drawCostItem(Items.EMERALD, 5)  // 每次消耗5绿宝石
    .maxDraws(10)  // 最多10次
    .cooldownGameDay()  // 每天重置
    .pityThreshold(50)  // 50次保底
    
    // 添加奖池条目
    .item(GachaItemBuilder.create("common_iron")
        .rarity(Rarity.COMMON)
        .item(Items.IRON_INGOT, 1, 3)  // 随机1-3个
        .weight(50))
    
    .item(GachaItemBuilder.create("rare_diamond")
        .rarity(Rarity.RARE)
        .item(Items.DIAMOND, 1, 2)
        .weight(20)
        .pityTrigger(true))  // 计入保底
    
    .buildAndRegister();

// 监听事件
@SubscribeEvent
public void onPostDraw(GachaEvents.PostDrawEvent event) {
    ServerPlayer player = event.getPlayer();
    DrawResult result = event.getResult();
    
    if (result.getItem().getRarity() == Rarity.LEGENDARY) {
        // 全服公告传说物品
        player.getServer().getPlayerList().broadcastSystemMessage(
            Component.literal("§6§l" + player.getName().getString() + 
                            " §6抽中了传说物品!"), false);
    }
}

// 客户端查询API
ClientGachaCache cache = ClientGachaCache.getInstance();
boolean canDraw = cache.canDraw("arc_quest:daily_gacha");
int remaining = cache.getRemainingDraws("arc_quest:daily_gacha");
String cooldownText = cache.getCooldownText("arc_quest:daily_gacha");
int pityCounter = cache.getPityCounter("arc_quest:daily_gacha");
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

### E. Arc vs FTB 最终同步策略对照（2026-04 决议）

> 目标：在“模组可维护成本”前提下，固化最终同步策略，避免过度工程化。

#### E.1 总体结论

- Arc 不追求 100% 复制 FTB Quests 内部实现。
- 最终采用：
  - **Quest / Dialogue：事件驱动为主**
  - **Trade / Gacha：事件推送主导 + 0.5s 兜底轮询**
- 原则：优先玩家体感与开发效率，其次再追求架构纯度。

#### E.2 对照表（Arc vs FTB）

| 维度 | Arc Quest（当前） | FTB Quests（1.20.1/dev） | 执行口径 |
|------|------------------|---------------------------|----------|
| Quest 初始化 | 全量+状态同步 | `SyncQuestsMessage -> RequestTeamDataMessage -> SyncTeamDataMessage` | 都是“先建基线” |
| Quest 运行态 | 任务动作/进度事件推送 + 服务端节流检查 | TeamData 变更事件消息推送 | 继续事件驱动 |
| Quest 周期拉取 | 无客户端高频轮询 | 无客户端高频轮询 | 保持现状 |
| Dialogue 同步 | `OPEN/UPDATE/CLOSE` 会话事件推送 | 无一一对应模块 | 保持事件驱动 |
| Trade 同步 | push-first + HUD 每10tick兜底 sync | 无原生 Trade 对照 | 保留兜底策略 |
| Gacha 同步 | push-first + HUD 每10tick兜底 sync | 无原生 Gacha 对照 | 保留兜底策略 |

#### E.3 模块级最终策略（必须遵守）

1. **Quest**
   - 禁止增加客户端周期轮询。
   - 新增同步点优先挂在“任务状态变更事件”上。

2. **Dialogue**
   - 仅允许会话事件（start/choice/auto/restore/close）触发更新。
   - 禁止引入 HUD/tick 轮询拉会话状态。

3. **Trade / Gacha**
   - 保留每 10 tick（0.5s）兜底同步，仅在活跃界面生效。
   - 服务端必须做变化检测（无变化不下发）。

#### E.4 为什么不继续提高到“全域纯事件驱动”

- 作为模组，Trade/Gacha 的高频交互容错优先级更高。
- 0.5s 兜底可显著降低“单次漏推送”导致的玩家体感问题。
- 继续深改的边际收益低于内容开发收益。

#### E.5 触发式重启治理（而非常态推进）

仅在出现以下任一条件时，重启同步专项：

1. 连续两个版本出现“状态不同步”玩家反馈（同模块同类型）；
2. 新模块接入导致现有同步链路回退；
3. 服务端日志显示长期异常（`sync_request` 高但 `sync_sent`/`result` 失衡）。

平时策略：**冻结同步架构，优先推进内容。**

---

**文档结束**

如需更多细节，请查阅源代码注释或提出具体问题。

🎉 **祝你开发愉快！**
---
