---
name: arcquest-addon-events-client
description: Integrate with Arc Quest Forge runtime events and supported client extension registries in 1.20.1, covering event buses, payload semantics, cancellation, side checks, dialogue overlays, journal filters, marker presentation, and camera basis providers.
---

# Arc Quest 事件与客户端扩展

用于订阅 ArcQ 业务事件，或通过正式客户端 Registry 扩展对话、任务日志和 Marker 表现。

## 必读

- [事件目录与事件总线](references/events.md)
- [事件与客户端扩展深入参考](references/api-reference.md)
- [客户端扩展点](references/client.md)
- [订阅模式与副作用规则](references/patterns.md)
- [事件与客户端需求实现配方](references/scenario-recipes.md)

完整源码见 [ExampleClientExtensions.java](../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ExampleClientExtensions.java) 和 [ExamplePlayerEvents.java](../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ExamplePlayerEvents.java)。

## 事件总线

- `ArcQuestRegistrationEvent.*`：MOD 总线，注册期。
- `api.event` 下的 Quest、Dialogue、Guide、Trade、Gacha、Player、Reload、NPC 事件：`MinecraftForge.EVENT_BUS`。
- `TrackedQuestChangedEvent` 可能在两侧发布，必须检查 `isClientSide/isServerSide` 和 `isAuthoritative`。
- 可取消事件只有类本身明确暴露取消语义时才修改；不要假设所有 Forge `Event` 都可取消。

## 客户端边界

- `DialogueOverlayRegistry`、`JournalTabVisibilityRegistry`、`MarkerCameraBasisRegistry`、`QuestMarkerPresentationRegistry` 只能从客户端隔离入口注册。
- Overlay 每帧运行，不执行磁盘 I/O、网络等待、Registry 扫描或世界全量查询。
- Registry 当前没有通用 unregister；避免在 Screen 每次打开时重复注册，使用一次性客户端初始化。
- 直接 Mixin ArcQ Screen/HUD 属于高兼容风险方案，仅在正式扩展点无法表达需求时评估。
