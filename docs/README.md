# Arc Quest 模组文档

**版本**: 1.0.0  
**Minecraft**: 1.20.1 Forge  
**Java**: 17  
**最后更新**: 2026-04-20

---

## 文档导航

### 快速开始

- [项目概述与5分钟上手](guide/OVERVIEW.md) - 了解项目特性，快速创建第一个任务

### 架构设计

- [核心架构设计](guide/ARCHITECTURE.md) - 模块结构、数据流、设计模式、性能优化

### API参考

#### 任务系统
- [任务系统API](api/QUEST_API.md) - QuestDefinition, Builder API, Capability, ProgressHandler

#### 对话系统
- [对话系统API](api/DIALOGUE_API.md) - DialogueTree, DialogueTreeBuilder, Session管理

#### 视觉系统
- [视觉系统API](api/VISUAL_API.md) - 立绘渲染器、图标渲染器、动画工具

#### HUD系统
- [HUD系统API](api/HUD_API.md) - QuestHudOverlay, TrackerPanel, Toast通知

#### 网络同步
- [网络同步API](api/NETWORK_API.md) - 网络包、客户端缓存、增量同步策略

### 开发指南

- [任务系统详解](guide/QUEST_SYSTEM.md) - 任务生命周期、目标追踪、条件判断
- [对话系统详解](guide/DIALOGUE_SYSTEM.md) - 对话树设计、NPC交互、自定义条件、时间冷却
- [视觉系统详解](guide/VISUAL_SYSTEM.md) - 立绘动画、图标配置、主题色
- [常见问题](guide/FAQ.md) - 常见问题解答

---

## 按角色查阅

### 模组开发者

**推荐阅读顺序**:
1. [项目概述](guide/OVERVIEW.md) - 了解项目
2. [任务系统API](api/QUEST_API.md) - 学习如何定义任务
3. [对话系统API](api/DIALOGUE_API.md) - 学习如何创建对话
4. [核心架构设计](guide/ARCHITECTURE.md) - 理解底层原理

**常用代码示例**:

```java
// 创建任务
QuestBuilder.create("my_quest")
    .category(QuestCategory.ADVENTURE)
    .displayName(Component.translatable("quest.title"))
    .phase(PhaseBuilder.create("step1")
        .displayName("收集木材")
        .objective(ObjectiveBuilder.collect(Items.OAK_LOG, 5)
            .display("收集5个橡木原木"))
        .thenGoTo("step2")
        .build())
    .reward(new ItemReward(Items.DIAMOND, 1))
    .buildAndRegister();

// 创建对话
DialogueTreeBuilder.create("villager")
    .npc("村民")
    .node("start")
        .say("你好！")
        .choice("接受任务", c -> c.startQuest("arc_quest:my_quest").close())
    .buildAndRegister();
```

---

### 系统管理员

**推荐阅读**:
- [命令系统](guide/OVERVIEW.md#管理员命令)

**常用命令**:
```bash
# 给予任务
/arcquest give @p arc_quest:my_quest

# 强制完成
/arcquest complete @p arc_quest:my_quest

# 重置任务
/arcquest reset @p arc_quest:my_quest

# 查看注册表
/arcquest registry

# 重载数据
/arcquest reload

# 打开对话
/arcquest dialogue @p test_villager
```

---

## 文档结构

```
docs/
├── README.md                  # 本文档（总索引）
├── api/                       # API参考文档
│   ├── QUEST_API.md          # 任务系统API
│   ├── DIALOGUE_API.md       # 对话系统API
│   ├── VISUAL_API.md         # 视觉系统API
│   ├── HUD_API.md            # HUD系统API
│   └── NETWORK_API.md        # 网络同步API
├── guide/                     # 开发指南
│   ├── OVERVIEW.md           # 项目概述
│   ├── ARCHITECTURE.md       # 核心架构
│   ├── QUEST_SYSTEM.md       # 任务系统详解
│   ├── DIALOGUE_SYSTEM.md    # 对话系统详解
│   ├── VISUAL_SYSTEM.md      # 视觉系统详解
│   └── FAQ.md                # 常见问题
└── summary/                   # 综合参考
    ├── API_REFERENCE.md      # 完整API参考
    └── PROJECT_DOCUMENTATION.md  # 项目技术文档
```

---

## 核心枚举类型速查

### QuestCategory（任务分类）
| 值 | ID | 默认主题色 | 说明 |
|----|-----|----------|------|
| `ARCHON` | archon | 0xFFD700 (金色) | 主线/开拓任务 |
| `COMPANION` | companion | 0x00BFFF (天蓝) | 同行任务 |
| `DAILY` | daily | 0x90EE90 (浅绿) | 日常委托 |
| `ADVENTURE` | adventure | 0xDDA0DD (浅紫) | 冒险任务 |
| `EVENT` | event | 0xFF6347 (番茄红) | 活动任务 |

### QuestState（任务状态）
| 值 | 说明 | 是否终态 |
|----|------|---------|
| `LOCKED` | 条件未满足，不可见/不可接 | 否 |
| `AVAILABLE` | 条件满足，可以接取 | 否 |
| `ACTIVE` | 进行中 | 否 |
| `COMPLETED` | 已完成 | 是 |
| `FAILED` | 已失败 | 是 |

### ObjectiveType（目标类型）
| 值 | 说明 | 是否累计计数 |
|----|------|------------|
| `KILL` | 击杀指定实体 | 是 |
| `COLLECT` | 收集/持有指定物品 | 是 |
| `TALK` | 与NPC对话 | 否 |
| `INTERACT` | 右键交互方块/实体 | 否 |
| `REACH_LOCATION` | 抵达指定区域 | 否 |
| `DELIVER` | 提交物品给NPC | 是 |
| `CRAFT` | 制作指定物品 | 否 |
| `CUSTOM` | 纯代码自定义检测 | 否 |

---

## 历史文档

以下旧文档已拆分到上述模块化文档中，保留仅供参考：

- `summary/API_REFERENCE.md` → 完整API参考
- `summary/PROJECT_DOCUMENTATION.md` → 项目技术文档

---

## 文档维护

### 更新原则

1. **代码即文档** - 优先通过清晰的代码表达意图
2. **精简注释** - 只保留有意义的JavaDoc
3. **模块化** - 每个文档聚焦单一主题
4. **示例驱动** - 提供可运行的代码示例
5. **及时更新** - 代码变更后同步更新文档

---

## 获取帮助

1. 查阅 [常见问题](guide/FAQ.md)
2. 查看相关API文档
3. 阅读核心架构设计
4. 提交Issue

---

## 许可证

本项目采用 MIT 许可证。

---

**最后更新**: 2026-04-20  
**文档版本**: 2.0.0
