# Marker 生命周期、Resolver 与表现

## 普通标点和追踪标点

- `markRelatedObject`：属于 Quest/Phase/Objective/Dialogue Definition 的普通 Marker，由激活/停用条件与业务生命周期协调。
- `trackingMarker`：只属于 Phase；仅当该 Quest 正在追踪且玩家聚焦该 Phase 时存在，切换追踪或聚焦后自动移除/重建。

因此需要“只有玩家正在追踪此阶段才显示”的目标必须用 `PhaseBuilder.trackingMarker(...)`，而不是给普通 Marker 再手工监听追踪事件删除。

## 自定义 Resolver

注册：

```text
ArcQuestAPI.registerMarkTargetResolver(
        "example_addon:nearest_machine",
        (player, level, target) -> {
            BlockPos found = findNearestLoadedMachine(
                    level, player.blockPosition(), target.args());
            return found == null ? null : ResolvedMarkTarget.position(
                    found.getX() + 0.5,
                    found.getY() + 1.0,
                    found.getZ() + 0.5,
                    level.dimension().location().toString());
        });
```

Definition：

```text
new MarkableObject.CustomResolver(
        "example_addon:nearest_machine",
        Map.of("radius", "128"));
```

Resolver 返回 null 表示本轮无法解析。参数来自 Definition/网络边界时按不可信字符串解析，限制数量、长度、数值和允许键。

性能要求：

- 只搜索已加载或明确有界区域；
- 不递归加载区块；
- 不每 refresh 扫描整个维度；
- 可缓存时使用玩家/维度/目标参数键，并定义 TTL、容量和卸载清理；
- 只返回坐标/实体标识，不长期保存 Entity/Level 强引用。

`MarkTargetResolverRegistry.unregister` 存在，但一般只用于明确的运行时卸载。普通附属在一次性初始化注册即可。

## `ResolvedMarkTarget`

可返回位置，或完整实体绑定字段：坐标、dimension 字符串、entity ID、UUID、GUID 和 attach point。固定位置使用 `ResolvedMarkTarget.position(...)`。移动实体需要有效实体绑定，且 `MarkSpec.trackMovingEntity` 为 true。

## 客户端表现 Registry

`QuestMarkerPresentationRegistry` 支持：

- `registerLabelResolver`：返回 `Component` 或 null；
- `registerColorResolver`：返回 ARGB `Integer` 或 null；
- `registerVisibilityFilter`：返回是否显示。

按 marker ID、quest ID 或 style hint 快速过滤。Registry 没有通用 unregister，不要每次 Screen 打开重复注册。只在客户端隔离入口调用。

`MarkerCameraBasisRegistry` 可提供第三方相机的 basis，适合改变视角/镜头系统。provider 每帧调用，不能执行世界扫描。

## 事件

`QuestMarkerChangedEvent` 提供本次 upsert 和 remove；`QuestMarkersRefreshedEvent` 提供 Quest、active Phase 数和 Marker 数。事件用于观察或附属联动，不要在监听器中无条件再次 refresh 形成递归。
