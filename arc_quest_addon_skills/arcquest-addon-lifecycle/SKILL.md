---
name: arcquest-addon-lifecycle
description: Set up and register an Arc Quest 1.20.1 Forge code addon safely, including Gradle dependencies, mods.toml, MOD-bus registration events, registry freeze timing, IDs, and side isolation. Do not use for datapacks.
---

# Arc Quest 附属生命周期

用于搭建附属工程和选择正确注册时机。

## 工作流

1. 阅读 [依赖与元数据](references/setup.md)。
2. 阅读 [注册生命周期](references/registration.md)。
3. 需要完整工程边界时阅读 [工程和生命周期深入参考](references/api-reference.md)。
4. 需要多模块注册、可选依赖或 Builder 门面时阅读 [生命周期需求实现配方](references/scenario-recipes.md)。
5. 在附属 MOD 总线上订阅对应 `ArcQuestRegistrationEvent`，不要在静态初始化或服务器启动后注册业务定义。
6. 用附属 namespace 创建稳定 ID，先构建 Definition，再交给事件或 `ArcQuestAPI`。
7. 需要客户端扩展时放入独立客户端订阅类，并使用 `Dist.CLIENT` 隔离。

完整工程入口见 [ExampleArcQuestAddon.java](../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ExampleArcQuestAddon.java)，其中展示了按模块拆分注册监听器的方式。

## 不变量

- ArcQ 在自己的 `FMLCommonSetupEvent#enqueueWork` 中发布 Quest、Dialogue、Npc、Trade、Gacha、Guide 注册事件，随后冻结对应 Registry。
- 注册事件实现 `IModBusEvent`，必须监听 MOD 总线；普通运行时事件监听 Forge 总线。
- `buildAndRegister()` 仍受冻结时机约束。优先在注册事件回调内 `event.register(builder.build())`，使所有权更清楚。
- 代码定义优先于同 ID 数据包定义；重复代码 ID 通常抛 `IllegalStateException`。
- `ArcQuestAPI` 是稳定门面；直接使用公开 Registry 只在门面缺少对应查询或扩展能力时进行。

实现具体内容时再加载相应模块 Skill，不要从本 Skill 推断 Builder 参数。
