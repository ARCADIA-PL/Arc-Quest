---
name: arcquest-addon-markers
description: Implement Arc Quest quest markers for Forge 1.20.1 using MarkSpec, MarkableObject targets, activation and trigger rules, tracking markers, custom target resolvers, presentation resolvers, and marker events. Excludes marker datapack JSON.
---

# Arc Quest Marker 附属

用于 Quest/Phase/Objective/Dialogue 标点、追踪 Phase 临时标点、自定义目标解析和客户端表现扩展。

## 必读

- [Marker 模型与目标类型](references/api.md)
- [Marker API 深入参考](references/api-reference.md)
- [生命周期、解析器与表现](references/runtime.md)
- [代码模式](references/patterns.md)
- [Marker 需求实现配方](references/scenario-recipes.md)

完整源码见 [ExampleMarkerContent.java](../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ExampleMarkerContent.java)、[ScenarioLayeredMarkerResolver.java](../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioLayeredMarkerResolver.java) 和并行 Quest 示例中的 tracking Marker。

## 工作流

1. 为 Marker 创建全局稳定 `id` 和附属翻译键。
2. 根据目标选择 `DimensionPos`、实体、结构或 `CustomResolver`，不要每 tick 自行全世界扫描。
3. 使用 `markRelatedObject` 添加一般 Marker；仅 Phase 的 `trackingMarker` 会随玩家聚焦 Phase 自动增删。
4. 事件型短时 Marker 使用 `MarkTriggers.withTrigger` 或 Builder 的 `markOn...` 方法。
5. 自定义目标在服务器端注册 `MarkTargetResolver`；标签、颜色和隐藏规则在客户端注册 `QuestMarkerPresentationRegistry`。

## 约束

- `MarkSpec.DEFAULT_MAX_DISTANCE` 为 512；构造器中非正距离回退到该值。
- `trackingMarker` 只允许连续 Marker，传入带事件 Trigger 的 Marker 会被拒绝。
- Resolver 可访问 `ServerLevel`，必须有搜索半径和成本边界；返回 null 表示本轮无法解析。
- `MarkTargetResolverRegistry` 是并发 Registry，但注册通常仍应在初始化阶段完成；运行时注销只用于明确的生命周期清理。
