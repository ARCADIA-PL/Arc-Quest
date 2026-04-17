# Arc Quest 项目概述

**版本**: 1.0.0  
**Minecraft**: 1.20.1 Forge  
**Java**: 17  
**最后更新**: 2026-04-17

---

## 📋 快速开始

### 项目简介

Arc Quest 是一个纯代码驱动的Minecraft任务系统模组，采用Builder模式定义任务和对话，支持多语言国际化、动态立绘渲染、图标系统和主题色定制。

### 核心特性

- ✅ **纯代码驱动** - 无JSON配置，所有任务通过Java Builder API定义
- ✅ **高可扩展性** - EnumMap存储视觉配置，新增类型无需修改现有代码
- ✅ **多语言支持** - DataGen自动生成中英文翻译文件
- ✅ **视觉系统** - 立绘动画、图标渲染、主题色定制
- ✅ **对话系统** - 分支对话树、条件判断、动作执行
- ✅ **网络同步** - 客户端-服务端数据实时同步
- ✅ **管理员命令** - 10+个调试和管理命令

### 技术栈

- Minecraft 1.20.1
- Forge 47.x
- Java 17
- Gradle 8.8

---

## 🚀 5分钟快速上手

### 1. 创建第一个任务

```java
QuestBuilder.create("my_first_quest")
    .category(QuestCategory.ADVENTURE)
    .displayName(Component.translatable("quest.my_first.title"))
    .description(Component.translatable("quest.my_first.desc"))
    
    // 添加阶段
    .phase(PhaseBuilder.create("step1")
        .displayName(Component.literal("第一步"))
        .objective(ObjectiveBuilder.collect(Items.OAK_LOG, 5)
            .display(Component.literal("收集5个橡木原木")))
        .thenGoTo("step2")
        .build())
    
    .phase(PhaseBuilder.create("step2")
        .displayName(Component.literal("第二步"))
        .objective(ObjectiveBuilder.kill(EntityType.ZOMBIE, 3)
            .display(Component.literal("击杀3只僵尸")))
        .build())
    
    // 奖励
    .reward(new ItemReward(Items.DIAMOND, 1))
    
    .buildAndRegister();
```

### 2. 创建对话树

```java
DialogueTreeBuilder.create("villager_greeting")
    .npc("村民")

    .node("start")
        .say("你好，冒险者！")
        .choice("接受任务", c -> c.startQuest("my_first_quest").close())
        .choice("离开", c -> c.close())

    .buildAndRegister();
```

### 3. 运行游戏测试

```bash
./gradlew runClient
```

使用命令给予任务：
```
/quest give @p arc_quest:my_first_quest
```

---

## 📂 模块结构

```
org.com.arc_quest/
├── client/                    # 客户端模块
│   ├── events/               # 客户端事件监听
│   └── gui/                  # GUI界面和渲染器
│       ├── render/           # 立绘和图标渲染器
│       ├── QuestTrackerPanel.java    # 任务追踪面板
│       ├── QuestHudOverlay.java      # HUD协调器
│       ├── QuestJournalScreen.java   # 任务日志界面
│       └── DialogueScreen.java       # 对话界面
├── command/                  # 管理员命令
├── data/                     # DataGen数据生成
├── dialogue/                 # 对话系统
│   ├── api/                  # 对话数据结构
│   ├── builder/              # 流式构建器
│   ├── network/              # 对话网络包
│   ├── registry/             # 对话注册表
│   └── runtime/              # 对话运行时管理
├── quest/                    # 任务系统（核心）
│   ├── api/                  # 任务数据结构
│   ├── builder/              # Builder API
│   ├── capability/           # Forge Capability
│   ├── condition/            # 条件判断
│   ├── event/                # 事件总线
│   ├── logic/                # 任务逻辑处理
│   ├── network/              # 任务网络包
│   ├── registry/             # 任务注册表
│   ├── reward/               # 奖励系统
│   └── tracking/             # 目标追踪
└── npc/                      # NPC交互
```

---

## 🔗 相关文档

### API参考
- [📘 任务系统API](api/QUEST_API.md)
- [💬 对话系统API](api/DIALOGUE_API.md)
- [🎨 视觉系统API](api/VISUAL_API.md)
- [📊 HUD系统API](api/HUD_API.md)
- [🌐 网络同步API](api/NETWORK_API.md)

### 开发指南
- [🏗️ 核心架构设计](guide/ARCHITECTURE.md)
- [⚙️ 任务系统详解](guide/QUEST_SYSTEM.md)
- [💭 对话系统详解](guide/DIALOGUE_SYSTEM.md)
- [✨ 视觉系统详解](guide/VISUAL_SYSTEM.md)
- [❓ 常见问题](guide/FAQ.md)

---

## 🎯 设计原则

### 1. 纯代码驱动

所有任务和对话通过Java代码定义，无需JSON配置文件。

**优势**:
- 编译时类型检查
- IDE自动补全
- 重构安全
- 性能更好（无反射解析）

### 2. Builder模式

使用链式调用构建复杂对象。

**示例**:
```java
QuestBuilder.create("id")
    .category(...)
    .displayName(...)
    .phase(...)
    .reward(...)
    .buildAndRegister();
```

### 3. 不可变数据结构

核心数据使用Java `record`，保证线程安全。

```java
public record QuestDefinition(
    String questId,
    QuestCategory category,
    Component displayName,
    ...
) {}
```

### 4. 高性能优化

- **FastUtil集合**: 避免装箱开销
- **增量同步**: 减少90%网络带宽
- **脏标记防抖**: 减少99%磁盘I/O
- **顶点缓冲**: 批量绘制减少Draw Call

---

## 🛠️ 开发环境设置

### 前置要求

- JDK 17+
- Gradle 8.8+
- IDE推荐：IntelliJ IDEA

### 克隆项目

```bash
git clone <repository-url>
cd "Arc Quest"
```

### 导入IDE

**IntelliJ IDEA**:
1. File → Open → 选择项目根目录
2. 等待Gradle同步完成
3. Run → Edit Configurations → 添加 `runClient` 和 `runServer`

### 常用命令

```bash
# 运行客户端
./gradlew runClient

# 运行服务端
./gradlew runServer

# 构建Mod
./gradlew build

# 清理构建
./gradlew clean

# 生成语言文件
./gradlew runData
```

---

## 📝 贡献指南

### 代码规范

1. **命名**: 使用camelCase，类名PascalCase
2. **注释**: 只保留有意义的JavaDoc，删除冗余注释
3. **Stream API**: 热点路径禁用，使用传统for循环
4. **集合**: 高性能场景使用FastUtil

### 提交规范

```
feat: 添加新功能
fix: 修复bug
docs: 文档更新
style: 代码格式
refactor: 重构
perf: 性能优化
test: 测试
chore: 构建/工具
```

---

## 📄 许可证

本项目采用 MIT 许可证。

---

**文档结束**

*欢迎使用 Arc Quest 模组！如有问题请查阅相关文档或提交Issue。*
