---
name: arcquest-addon-development
description: Route and implement Java/Forge code addons for Arc Quest 1.20.1, including setup, registration, quests, dialogue/NPC, guides, trade/gacha, markers, player runtime, events, and client extension points. Do not use for datapack authoring.
---

# Arc Quest 代码附属开发

用于为 Arc Quest（简称 ArcQ）编写 Java 代码附属。仅覆盖 Minecraft 1.20.1 + Forge 47.4.20 + Mojang Official mappings + Java 17；不覆盖 JSON 数据包和 Web 编辑器。

## 开始前

1. 阅读 [开发基线与流程](references/architecture.md)。
2. 设计大型附属或评估 API 稳定性时阅读 [大型附属集成模式](references/integration-patterns.md)。
3. 确认目标附属实际依赖的 ArcQ 版本和公开符号，不从其他 Minecraft/加载器版本猜 API。
4. 使用 `arc_quest` 作为依赖模组 ID，但附属自己的 `ResourceLocation` 必须使用附属 namespace。
5. 代码内容通过 `ArcQuestRegistrationEvent` 在 MOD 总线注册；运行时事件通过 `MinecraftForge.EVENT_BUS` 监听。
6. 服务端是任务、对话、Guide、交易、抽奖和玩家状态的权威端。客户端代码必须隔离。

## 子 Skill 路由

- 工程依赖、`mods.toml`、注册时机、Registry 冻结：使用 `$arcquest-addon-lifecycle`。
- Quest、Phase、Objective、奖励、任务组和运行时推进：使用 `$arcquest-addon-quests`。
- 对话树、自定义 NPC、实体扩展、会话回调：使用 `$arcquest-addon-dialogue-npc`。
- Guide、页面、分类、分组、解锁和阅读进度：使用 `$arcquest-addon-guides`。
- 商店、报价、交易限制、抽奖池、保底：使用 `$arcquest-addon-trade-gacha`。
- Quest Marker、目标解析器、临时追踪标点：使用 `$arcquest-addon-markers`。
- 玩家 ArcQ 状态、flag、variable、条件、任务追踪：使用 `$arcquest-addon-player-runtime`。
- Forge 业务事件和客户端表现扩展点：使用 `$arcquest-addon-events-client`。

## 通用边界

- 优先使用 `ArcQuestAPI`、Builder、公开 Registry、Service 和 `api.event`；不要以 Mixin 作为首选扩展方式。
- 不直接调用 `freeze()`、`clearAll()`、`replaceDatapackSnapshot()` 或网络包 handler。
- 不在客户端构造服务端运行时对象，不在服务端公共路径引用 `net.minecraft.client`。
- 不直接修改 `QuestRuntimeData` 来模拟正常完成；使用 `QuestProgressHandler`，否则可能漏掉奖励、事件、标记和后续阶段。
- 所有 ID 使用完整 namespace；字符串 API 接受无 namespace 时通常会回退到 `arc_quest`，附属不应依赖该回退。
- 依赖 ArcQ 内部 HUD 类的代码不等同于稳定 API。只有本 Skill 明确列出的客户端 Registry 才按正式扩展点使用。

## 验证

至少运行附属 `compileJava`。涉及服务端运行流程时再运行开发服务端；涉及 HUD/Screen 时运行客户端并检查 GUI scale 2/3/4、窗口缩放和专用服务器类加载。
