# 任务系统 API 参考

**模块**: quest/  
**适用对象**: 开发者、外部AI学习  
**最后更新**: 2026-04-20

---

## 📚 目录

1. [数据结构层](#数据结构层)
2. [Builder API层](#builder-api层)
3. [注册表层](#注册表层)
4. [Capability层](#capability层)
5. [逻辑处理层](#逻辑处理层)
6. [追踪系统](#追踪系统)
7. [条件系统](#条件系统)
8. [奖励系统](#奖励系统)

---

## 数据结构层

### QuestDefinition - 任务定义

**类型**: `record` (不可变)  
**位置**: `org.com.arc_quest.quest.api.QuestDefinition`

**字段**:
```java
private final String questId;                          // 任务ID（唯一标识）
private final QuestCategory category;                  // 任务分类
private final Component displayName;                   // 显示名称（支持翻译）
private final Component description;                   // 描述文本
private final LinkedHashMap<String, PhaseDefinition> phases;  // 阶段映射
private final List<ICondition> unlockConditions;       // 解锁条件
private final List<IReward> completionRewards;         // 完成奖励
private final QuestVisualConfig visualConfig;          // 视觉配置
private final int sortOrder;                           // 排序权重
private final ResourceLocation icon;                   // 默认图标
private final int themeColor;                          // 主题色（ARGB）
private final Set<String> flagsToSetOnComplete;        // 完成时设置的flag
```

**构造方法**:
```java
public QuestDefinition(
    String questId,
    QuestCategory category,
    Component displayName,
    Component description,
    LinkedHashMap<String, PhaseDefinition> phases,
    List<ICondition> unlockConditions,
    List<IReward> completionRewards,
    QuestVisualConfig visualConfig,
    int sortOrder,
    ResourceLocation icon,
    int themeColor,
    Set<String> flagsToSetOnComplete
)
```

**核心方法**:

| 方法 | 返回类型 | 说明 |
|------|---------|------|
| `getQuestId()` | String | 获取任务ID |
| `getCategory()` | QuestCategory | 获取任务分类 |
| `getDisplayName()` | Component | 获取显示名称 |
| `getDescription()` | Component | 获取描述 |
| `getAllPhases()` | List\<PhaseDefinition\> | 获取所有阶段（按插入顺序） |
| `getPhase(String phaseId)` | PhaseDefinition | 根据ID获取阶段 |
| `getFirstPhaseId()` | String | 获取第一个阶段的ID |
| `hasPhase(String phaseId)` | boolean | 检查阶段是否存在 |
| `getUnlockConditions()` | List\<ICondition\> | 获取解锁条件列表 |
| `getCompletionRewards()` | List\<IReward\> | 获取完成奖励列表 |
| `getVisualConfig()` | QuestVisualConfig | 获取视觉配置 |
| `getThemeColor()` | int | 获取主题色（默认0xFFFFFFFF） |
| `getIconFor(IconPosition pos)` | Optional\<ResourceLocation\> | 获取指定位置的图标 |
| `getSplashConfig(SplashType type)` | Optional\<VisualAsset\> | 获取指定类型的立绘 |
| `getFlagsToSetOnComplete()` | Set\<String\> | 获取完成时设置的flag集合 |

**使用示例**:
```java
QuestDefinition quest = QuestRegistry.get(ResourceLocation.tryParse("arc_quest:epic_prologue"));
if (quest != null) {
    String name = quest.getDisplayName().getString();
    int color = quest.getThemeColor();
    quest.getSplashConfig(SplashType.QUEST_ACQUIRED).ifPresent(asset -> {
        LOGGER.info("Acquisition splash: {}", asset.texture());
    });
}
```

---

### PhaseDefinition - 阶段定义

**类型**: `record` (不可变)  
**位置**: `org.com.arc_quest.quest.api.PhaseDefinition`

**字段**:
```java
private final String phaseId;                          // 阶段ID
private final Component displayName;                   // 显示名称
private final List<ObjectiveEntry> objectives;         // 目标列表
private final List<PhaseTransition> transitions;       // 跳转规则
private final List<ChoiceOption> choices;              // 选择分支
private final QuestVisualConfig visualConfig;          // 视觉配置
private final Set<String> flagsToSetOnEnter;           // 进入时设置的flag
private final Set<String> flagsToSetOnComplete;        // 完成时设置的flag
```

**核心方法**:

| 方法 | 返回类型 | 说明 |
|------|---------|------|
| `getPhaseId()` | String | 获取阶段ID |
| `getDisplayName()` | Component | 获取显示名称 |
| `getObjectives()` | List\<ObjectiveEntry\> | 获取目标列表 |
| `getTransitions()` | List\<PhaseTransition\> | 获取跳转规则 |
| `getChoices()` | List\<ChoiceOption\> | 获取选择分支 |
| `getVisualConfig()` | QuestVisualConfig | 获取视觉配置 |
| `hasChoices()` | boolean | 是否有选择分支 |
| `getFlagsToSetOnEnter()` | Set\<String\> | 获取进入时设置的flag |
| `getFlagsToSetOnComplete()` | Set\<String\> | 获取完成时设置的flag |
| `getNextPhaseIfCompleted()` | Optional\<String\> | 获取完成后的下一阶段 |

**使用示例**:
```java
PhaseDefinition phase = quest.getPhase("gather_wood");
if (phase != null) {
    List<ObjectiveEntry> objectives = phase.getObjectives();
    for (ObjectiveEntry obj : objectives) {
        LOGGER.info("Objective: {} / {}", obj.getDisplayText(), obj.getRequiredCount());
    }
}
```

---

### ObjectiveEntry - 目标条目

**类型**: `record` (不可变)  
**位置**: `org.com.arc_quest.quest.api.ObjectiveEntry`

**字段**:
```java
private final ObjectiveType type;                      // 目标类型
private final Component displayText;                   // 显示文本
private final int requiredCount;                       // 需求数量
private final Map<String, Object> properties;          // 额外属性
```

**核心方法**:

| 方法 | 返回类型 | 说明 |
|------|---------|------|
| `getType()` | ObjectiveType | 获取目标类型 |
| `getDisplayText()` | Component | 获取显示文本 |
| `getRequiredCount()` | int | 获取需求数量 |
| `getProperty(String key)` | Optional\<Object\> | 获取属性值 |

**目标类型枚举** (`ObjectiveType`):
- `COLLECT` - 收集物品
- `KILL` - 击杀实体
- `CRAFT` - 合成物品
- `INTERACT` - 与方块交互
- `VISIT` - 到达位置

---

### QuestRuntimeData - 运行时数据

**类型**: `class` (可变，存储在玩家Capability中)  
**位置**: `org.com.arc_quest.quest.capability.QuestRuntimeData`

**字段**:
```java
private String questId;                                // 任务ID
private QuestState state;                              // 当前状态
private String currentPhaseId;                         // 当前阶段ID
private int[] progress;                                // 目标进度数组
private Set<String> flags;                             // 标志位集合
private Map<String, Integer> variables;                // 变量映射
private long lastUpdateTime;                           // 最后更新时间
```

**核心方法**:

| 方法 | 返回类型 | 说明 |
|------|---------|------|
| `getQuestId()` | String | 获取任务ID |
| `getState()` | QuestState | 获取当前状态 |
| `getCurrentPhaseId()` | String | 获取当前阶段ID |
| `getObjectiveProgress(int index)` | int | 获取指定目标的进度 |
| `getAllProgress()` | int[] | 获取所有目标进度 |
| `updateProgress(int index, int amount)` | void | 更新目标进度 |
| `isFlagSet(String flag)` | boolean | 检查flag是否设置 |
| `setFlag(String flag)` | void | 设置flag |
| `clearFlag(String flag)` | void | 清除flag |
| `getVariable(String key)` | int | 获取变量值 |
| `setVariable(String key, int value)` | void | 设置变量值 |
| `writeToNetwork(FriendlyByteBuf buf)` | void | 序列化到网络包 |
| `static readFromNetwork(FriendlyByteBuf buf)` | QuestRuntimeData | 从网络包反序列化 |
| `writeToNBT(CompoundTag tag)` | void | 序列化到NBT |
| `static readFromNBT(CompoundTag tag)` | QuestRuntimeData | 从NBT反序列化 |

**状态枚举** (`QuestState`):
- `ACTIVE` - 进行中
- `COMPLETED` - 已完成
- `FAILED` - 已失败

---

### QuestVisualConfig - 视觉配置

**类型**: `record` (不可变)  
**位置**: `org.com.arc_quest.quest.api.QuestVisualConfig`

**字段**:
```java
private final EnumMap<SplashType, VisualAsset> splashConfigs;  // 立绘配置
private final EnumMap<IconPosition, VisualAsset> iconConfigs;  // 图标配置
private final int themeColor;                                  // 主题色
```

**核心方法**:

| 方法 | 返回类型 | 说明 |
|------|---------|------|
| `getSplashConfig(SplashType type)` | Optional\<VisualAsset\> | 获取立绘配置 |
| `getIconConfig(IconPosition pos)` | Optional\<VisualAsset\> | 获取图标配置 |
| `getThemeColor()` | int | 获取主题色 |
| `hasSplash(SplashType type)` | boolean | 是否有立绘配置 |
| `hasIcon(IconPosition pos)` | boolean | 是否有图标配置 |
| `static builder()` | Builder | 创建Builder |

**Builder API**:
```java
QuestVisualConfig config = QuestVisualConfig.builder()
    .splash(SplashType.QUEST_ACQUIRED, texture, 1.2f)
    .icon(IconPosition.HUD_TRACKER, iconTexture)
    .themeColor(0xFFFFD700)
    .build();
```

---

### VisualAsset - 视觉资源

**类型**: `record` (不可变)  
**位置**: `org.com.arc_quest.quest.api.VisualAsset`

**字段**:
```java
private final ResourceLocation texture;   // 纹理路径
private final float scale;                // 缩放比例（默认1.0）
private final float offsetX;              // X轴偏移（像素）
private final float offsetY;              // Y轴偏移（像素）
private final int tintColor;              // 染色颜色（ARGB，0xFFFFFFFF表示不染色）
private final boolean enabled;            // 是否启用
```

**构造方法**:
```java
// 简化构造（常用）
public VisualAsset(ResourceLocation texture, float scale)

// 完整构造
public VisualAsset(
    ResourceLocation texture,
    float scale,
    float offsetX,
    float offsetY,
    int tintColor,
    boolean enabled
)
```

---

## Builder API层 (quest/builder/)

### QuestBuilder - 任务构建器

**位置**: `org.com.arc_quest.quest.builder.QuestBuilder`

**静态方法**:
```java
public static QuestBuilder create(String questId)
```

**实例方法** (链式调用):

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `category()` | QuestCategory | QuestBuilder | 设置任务分类 |
| `displayName()` | Component | QuestBuilder | 设置显示名称 |
| `description()` | Component | QuestBuilder | 设置描述 |
| `icon()` | ResourceLocation | QuestBuilder | 设置默认图标 |
| `sortOrder()` | int | QuestBuilder | 设置排序权重 |
| `phase()` | PhaseDefinition | QuestBuilder | 添加阶段 |
| `unlockCondition()` | ICondition | QuestBuilder | 添加解锁条件 |
| `reward()` | IReward | QuestBuilder | 添加完成奖励 |
| `acquisitionSplash()` | ResourceLocation, float | QuestBuilder | 设置获得任务立绘 |
| `completionSplash()` | ResourceLocation, float | QuestBuilder | 设置完成任务立绘 |
| `listIcon()` | ResourceLocation | QuestBuilder | 设置列表图标 |
| `themeColor()` | ChatFormatting/int | QuestBuilder | 设置主题色 |
| `setFlagOnComplete()` | String | QuestBuilder | 设置完成时的flag |
| `buildAndRegister()` | 无 | QuestDefinition | 构建并注册任务 |

**使用示例**:
```java
QuestBuilder.create("my_quest")
    .category(QuestCategory.ADVENTURE)
    .displayName(Component.translatable("my.quest.title"))
    .description(Component.translatable("my.quest.desc"))
    .icon(ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/icons/my_quest.png"))
    .sortOrder(100)
    
    // 视觉配置
    .acquisitionSplash(
        ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/splash/acquire.png"),
        1.2f
    )
    .themeColor(ChatFormatting.GOLD)
    
    // 阶段
    .phase(PhaseBuilder.create("step1")
        .displayName(Component.literal("第一步"))
        .objective(ObjectiveBuilder.collect(Items.OAK_LOG, 10))
        .thenGoTo("step2")
        .build())
    
    // 奖励
    .reward(new ItemReward(Items.DIAMOND, 1))
    .setFlagOnComplete("my_quest_done")
    
    .buildAndRegister();
```

---

### PhaseBuilder - 阶段构建器

**位置**: `org.com.arc_quest.quest.builder.PhaseBuilder`

**静态方法**:
```java
public static PhaseBuilder create(String phaseId)
```

**实例方法**:

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `displayName()` | Component | PhaseBuilder | 设置显示名称 |
| `objective()` | ObjectiveEntry | PhaseBuilder | 添加目标 |
| `thenGoTo()` | String | PhaseBuilder | 设置无条件跳转 |
| `thenGoToIf()` | String, ICondition | PhaseBuilder | 设置条件跳转 |
| `choice()` | Component, String, String | PhaseBuilder | 添加选择分支 |
| `startSplash()` | ResourceLocation, float | PhaseBuilder | 设置阶段开始立绘 |
| `completeSplash()` | ResourceLocation, float | PhaseBuilder | 设置阶段完成立绘 |
| `labelIcon()` | ResourceLocation | PhaseBuilder | 设置标签图标 |
| `trackerIcon()` | ResourceLocation, float, float, float | PhaseBuilder | 设置追踪图标 |
| `setFlagOnEnter()` | String | PhaseBuilder | 设置进入时的flag |
| `setFlagOnComplete()` | String | PhaseBuilder | 设置完成时的flag |
| `build()` | 无 | PhaseDefinition | 构建阶段 |

**使用示例**:
```java
PhaseBuilder.create("gather_wood")
    .displayName(Component.translatable("phase.gather_wood"))
    
    // 目标
    .objective(ObjectiveBuilder.collect(Items.OAK_LOG, 5)
        .display(Component.literal("收集橡木原木")))
    
    // 跳转
    .thenGoToIf("talk_villager", new FlagSetCondition("met_villager"))
    .thenGoTo("default_next")
    
    // 选择分支
    .choice(Component.literal("选择A"), "flag_a", "path_a")
    .choice(Component.literal("选择B"), "flag_b", "path_b")
    
    // 视觉
    .startSplash(texture, 1.1f)
    .labelIcon(iconTexture)
    
    // Flag
    .setFlagOnEnter("phase_started")
    .setFlagOnComplete("phase_done")
    
    .build();
```

---

### ObjectiveBuilder - 目标构建器

**位置**: `org.com.arc_quest.quest.builder.ObjectiveBuilder`

**静态工厂方法**:

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `collect()` | ItemLike, int | ObjectiveBuilder | 创建收集目标 |
| `kill()` | EntityType<?>, int | ObjectiveBuilder | 创建击杀目标 |
| `craft()` | ItemLike, int | ObjectiveBuilder | 创建合成目标 |
| `interact()` | Block, int | ObjectiveBuilder | 创建交互目标 |
| `visit()` | double, double, double, double | ObjectiveBuilder | 创建访问目标 |

**实例方法**:

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `display()` | Component | ObjectiveBuilder | 设置显示文本 |
| `build()` | 无 | ObjectiveEntry | 构建目标 |

**使用示例**:
```java
// 收集目标
ObjectiveBuilder.collect(Items.OAK_LOG, 10)
    .display(Component.translatable("objective.collect_logs"))
    .build();

// 击杀目标
ObjectiveBuilder.kill(EntityType.ZOMBIE, 5)
    .display(Component.literal("击杀僵尸"))
    .build();

// 访问目标
ObjectiveBuilder.visit(100.5, 64.0, 200.5, 10.0)
    .display(Component.literal("前往指定地点"))
    .build();
```

---

## 注册表层

### QuestRegistry - 任务注册表

**类型**: 单例模式  
**位置**: `org.com.arc_quest.quest.registry.QuestRegistry`

**静态方法**:

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `register()` | QuestDefinition | void | 注册任务 |
| `get()` | ResourceLocation | QuestDefinition | 根据ID获取任务 |
| `getAll()` | 无 | Collection\<QuestDefinition\> | 获取所有任务 |
| `getByCategory()` | QuestCategory | List\<QuestDefinition\> | 按分类获取任务 |
| `contains()` | ResourceLocation | boolean | 检查任务是否存在 |
| `clear()` | 无 | void | 清空注册表（仅用于reload） |

**使用示例**:
```java
// 注册任务
QuestDefinition quest = QuestBuilder.create("test").build();
QuestRegistry.register(quest);

// 查询任务
QuestDefinition found = QuestRegistry.get(ResourceLocation.tryParse("arc_quest:test"));
if (found != null) {
    LOGGER.info("Found quest: {}", found.getDisplayName().getString());
}

// 遍历所有任务
for (QuestDefinition q : QuestRegistry.getAll()) {
    LOGGER.info("Quest: {} ({})", q.getQuestId(), q.getCategory());
}
```

---

## Capability层 (quest/capability/)

### IQuestCapability - 能力接口

**类型**: `interface`  
**位置**: `org.com.arc_quest.quest.capability.IQuestCapability`

**方法**:

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `getActiveQuests()` | 无 | Map\<String, QuestRuntimeData\> | 获取所有激活的任务 |
| `getCompletedQuests()` | 无 | Set\<String\> | 获取已完成的任务ID集合 |
| `getFailedQuests()` | 无 | Set\<String\> | 获取已失败的任务ID集合 |
| `getQuestData()` | String | QuestRuntimeData | 获取指定任务的数据 |
| `addQuest()` | String | void | 添加新任务 |
| `removeQuest()` | String | void | 移除任务 |
| `completeQuest()` | String | void | 完成任务 |
| `failQuest()` | String | void | 失败任务 |
| `advancePhase()` | String, String | void | 推进阶段 |
| `updateObjectiveProgress()` | String, int, int | void | 更新目标进度 |
| `setFlag()` | String | void | 设置全局flag |
| `clearFlag()` | String | void | 清除全局flag |
| `isFlagSet()` | String | boolean | 检查flag |
| `setVariable()` | String, int | void | 设置变量 |
| `getVariable()` | String | int | 获取变量 |
| `resetAll()` | 无 | void | 重置所有数据 |

---

### QuestCapabilityImpl - 能力实现

**位置**: `org.com.arc_quest.quest.capability.QuestCapabilityImpl`

实现`IQuestCapability`接口，管理玩家的任务数据。

**内部存储**:
```java
private final Map<String, QuestRuntimeData> activeQuests = new HashMap<>();
private final Set<String> completedQuests = new HashSet<>();
private final Set<String> failedQuests = new HashSet<>();
private final Set<String> globalFlags = new HashSet<>();
private final Map<String, Integer> globalVariables = new HashMap<>();
```

---

## 逻辑处理层

### QuestProgressHandler - 进度处理器

**类型**: 单例模式  
**位置**: `org.com.arc_quest.quest.logic.QuestProgressHandler`

**核心方法**:

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `INSTANCE` | - | QuestProgressHandler | 单例实例 |
| `onObjectiveCompleted()` | ServerPlayer, String, int | void | 目标完成回调 |
| `checkPhaseCompletion()` | ServerPlayer, String | void | 检查阶段完成 |
| `advanceToNextPhase()` | ServerPlayer, String, String | void | 推进到下一阶段 |
| `completeQuest()` | ServerPlayer, String | void | 完成任务 |
| `distributeRewards()` | ServerPlayer, QuestDefinition | void | 发放奖励 |
| `handleBranchChoice()` | ServerPlayer, String, int | void | 处理分支选择 |

**工作流程**:
```
目标完成
  ↓
onObjectiveCompleted(player, questId, objectiveIndex)
  ↓
检查阶段是否完成
  ↓
checkPhaseCompletion(player, questId)
  ↓
查找下一个阶段（检查transitions）
  ↓
advanceToNextPhase(player, questId, nextPhaseId)
  ↓
如果没有下一阶段 → completeQuest(player, questId)
  ↓
发放奖励 + 设置flags
```

---

## 追踪系统

### ObjectiveTracker - 目标追踪器

**位置**: `org.com.arc_quest.quest.tracking.ObjectiveTracker`

**职责**: 监听游戏事件，自动更新任务目标进度

**监听的事件**:
- `ItemPickupEvent` - 物品拾取（COLLECT目标）
- `LivingDeathEvent` - 实体死亡（KILL目标）
- `ItemCraftedEvent` - 物品合成（CRAFT目标）
- `BlockEvent.BreakEvent` - 方块破坏（INTERACT目标）
- `PlayerTickEvent` - 玩家移动（VISIT目标）

**注册方法**:
```java
ObjectiveTracker.registerListeners();  // 在FMLCommonSetupEvent中调用
```

---

### TrackedObjective - 被追踪的目标

**类型**: `record`  
**位置**: `org.com.arc_quest.quest.tracking.TrackedObjective`

**字段**:
```java
private final String questId;
private final String phaseId;
private final int objectiveIndex;
private final ObjectiveType type;
private final Map<String, Object> properties;
```

---

## 条件系统

### ICondition - 条件接口

**类型**: `interface`  
**位置**: `org.com.arc_quest.quest.api.ICondition`

**方法**:
```java
boolean test(ServerPlayer player, QuestRuntimeData data);
```

**实现类**:

| 类名 | 说明 | 使用场景 |
|------|------|----------|
| `FlagSetCondition` | 检查flag是否设置 | 任务前置条件 |
| `FlagNotSetCondition` | 检查flag未设置 | 互斥任务 |
| `QuestCompletedCondition` | 检查任务是否完成 | 任务链 |
| `VariableCondition` | 检查变量值 | 动态条件 |

**使用示例**:
```java
// 检查flag
new FlagSetCondition("talked_to_villager")

// 检查任务完成
new QuestCompletedCondition("arc_quest:prologue")

// 检查变量
new VariableCondition("reputation", op -> op >= 50)
```

---

## 奖励系统

### IReward - 奖励接口

**类型**: `interface`  
**位置**: `org.com.arc_quest.quest.api.IReward`

**方法**:
```java
void grant(ServerPlayer player, QuestDefinition quest);
```

**实现类**:

| 类名 | 说明 | 构造参数 |
|------|------|----------|
| `ItemReward` | 给予物品 | ItemLike, count |
| `FlagReward` | 设置flag | String flagName |
| `CommandReward` | 执行命令 | String command |
| `VariableReward` | 设置变量 | String key, int value |

**使用示例**:
```java
new ItemReward(Items.DIAMOND, 5)
new FlagReward("quest_completed")
new CommandReward("/give @p experience_bottle 10")
new VariableReward("reputation", 100)
```

---

## 附录

### 枚举类型

#### QuestCategory - 任务分类
```java
enum QuestCategory {
    MAIN,        // 主线任务
    SIDE,        // 支线任务
    ADVENTURE,   // 冒险任务
    ARCHON,      // 传说任务
    DAILY,       // 日常任务
    HIDDEN       // 隐藏任务
}
```

#### QuestState - 任务状态
```java
enum QuestState {
    ACTIVE,      // 进行中
    COMPLETED,   // 已完成
    FAILED       // 已失败
}
```

#### ObjectiveType - 目标类型
```java
enum ObjectiveType {
    COLLECT,     // 收集物品
    KILL,        // 击杀实体
    CRAFT,       // 合成物品
    INTERACT,    // 与方块交互
    VISIT        // 到达位置
}
```

---

**文档结束**

*本API参考涵盖任务系统的所有公共接口、数据结构和使用方法。*
