# Arc Quest 模组文档

**版本**: 1.0.0  
**Minecraft**: 1.20.1 Forge  
**Java**: 17  
**最后更新**: 2026-04-17

---

## 📚 文档导航

### 🚀 快速开始

- [📘 项目概述与5分钟上手](guide/OVERVIEW.md) - 了解项目特性，快速创建第一个任务

### 🏗️ 架构设计

- [🏛️ 核心架构设计](guide/ARCHITECTURE.md) - 模块结构、数据流、设计模式、性能优化

### 📖 API参考

#### 任务系统
- [⚙️ 任务系统API](api/QUEST_API.md) - QuestDefinition, Builder API, Capability, ProgressHandler

#### 对话系统
- [💬 对话系统API](api/DIALOGUE_API.md) - DialogueTree, DialogueTreeBuilder, Session管理

#### 视觉系统
- [🎨 视觉系统API](api/VISUAL_API.md) - 立绘渲染器、图标渲染器、动画工具

#### HUD系统
- [📊 HUD系统API](api/HUD_API.md) - QuestHudOverlay, TrackerPanel, Toast通知

#### 网络同步
- [🌐 网络同步API](api/NETWORK_API.md) - 网络包、客户端缓存、增量同步策略

### 🔧 开发指南

- [⚙️ 任务系统详解](guide/QUEST_SYSTEM.md) - 任务生命周期、目标追踪、条件判断
- [💭 对话系统详解](guide/DIALOGUE_SYSTEM.md) - 对话树设计、NPC交互、动作执行
- [✨ 视觉系统详解](guide/VISUAL_SYSTEM.md) - 立绘动画、图标配置、主题色
- [❓ 常见问题](guide/FAQ.md) - 20+个常见问题解答

---

## 🎯 按角色查阅

### 👨‍💻 模组开发者

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
        .objective(ObjectiveBuilder.collect(Items.OAK_LOG, 5))
        .build())
    .reward(new ItemReward(Items.DIAMOND, 1))
    .buildAndRegister();

// 创建对话
DialogueTreeBuilder.create("villager")
    .npc("村民")
    .node("start")
        .say("你好！")
        .choice("接受任务", c -> c.startQuest("my_quest").close())
    .buildAndRegister();
```

---

### 🎨 UI设计师

**推荐阅读**:
- [视觉系统API](api/VISUAL_API.md) - 立绘和图标配置
- [HUD系统API](api/HUD_API.md) - UI布局和动画

**关键概念**:
- `SplashType` - 7种立绘类型
- `IconPosition` - 6种图标位置
- `QuestVisualConfig` - 统一视觉配置
- 动画缓动函数 - easeOutCubic, easeInQuartic等

---

### 🔧 系统管理员

**推荐阅读**:
- [命令系统](guide/OVERVIEW.md#管理员命令) - 10+个管理命令

**常用命令**:
```bash
# 给予任务
/quest give @p arc_quest:my_quest

# 强制完成
/quest complete @p arc_quest:my_quest

# 重置任务
/quest reset @p arc_quest:my_quest

# 查看注册表
/quest registry

# 重载数据
/quest reload
```

---

### 🤖 外部AI学习

**完整学习路径**:
1. [项目概述](guide/OVERVIEW.md) - 整体了解
2. [核心架构设计](guide/ARCHITECTURE.md) - 架构理解
3. [任务系统API](api/QUEST_API.md) - 核心功能
4. [对话系统API](api/DIALOGUE_API.md) - 扩展功能
5. [视觉系统API](api/VISUAL_API.md) - 视觉表现
6. [HUD系统API](api/HUD_API.md) - 用户界面
7. [网络同步API](api/NETWORK_API.md) - 数据同步

**关键设计模式**:
- Builder模式 - 链式调用构建对象
- 单例模式 - 全局唯一实例
- 观察者模式 - 事件驱动
- 策略模式 - 可扩展接口
- 记录模式 - 不可变数据结构

---

## 📂 文档结构

```
docs/
├── README.md                  # 本文档（总索引）
├── api/                       # API参考文档
│   ├── QUEST_API.md          # 任务系统API
│   ├── DIALOGUE_API.md       # 对话系统API
│   ├── VISUAL_API.md         # 视觉系统API
│   ├── HUD_API.md            # HUD系统API
│   └── NETWORK_API.md        # 网络同步API
└── guide/                     # 开发指南
    ├── OVERVIEW.md           # 项目概述
    ├── ARCHITECTURE.md       # 核心架构
    ├── QUEST_SYSTEM.md       # 任务系统详解 ✅
    ├── DIALOGUE_SYSTEM.md    # 对话系统详解 ✅
    ├── VISUAL_SYSTEM.md      # 视觉系统详解 ✅
    └── FAQ.md                # 常见问题 ✅
```

---

## 🔗 历史文档

以下旧文档已拆分到上述模块化文档中，保留仅供参考：

- `API_REFERENCE.md` (1593行) → 拆分为5个API文档
- `PROJECT_DOCUMENTATION.md` (1486行) → 拆分为架构设计和开发指南

---

## 📝 文档维护

### 更新原则

1. **代码即文档** - 优先通过清晰的代码表达意图
2. **精简注释** - 只保留有意义的JavaDoc
3. **模块化** - 每个文档聚焦单一主题
4. **示例驱动** - 提供可运行的代码示例
5. **及时更新** - 代码变更后同步更新文档

### 贡献文档

欢迎提交Pull Request改进文档：
- 修正错误
- 补充示例
- 优化表述
- 添加新章节

---

## 🆘 获取帮助

### 遇到问题？

1. 查阅 [常见问题](guide/FAQ.md)
2. 查看相关API文档
3. 阅读核心架构设计
4. 提交Issue

### 联系方式

- GitHub Issues: [提交问题](https://github.com/your-repo/Arc-Quest/issues)
- Discord: [加入社区](https://discord.gg/your-server)

---

## 📄 许可证

本项目采用 MIT 许可证。

---

**最后更新**: 2026-04-17  
**文档版本**: 1.0.0

*祝使用愉快！🎉*
