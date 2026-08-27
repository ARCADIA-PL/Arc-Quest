# Marker 模型与目标类型

## `MarkSpec`

`MarkSpec` 是不可变 record：

```text
new MarkSpec(
        id,
        target,
        activateWhen,
        deactivateWhen,
        markerType,
        priority,
        maxDistance,
        refreshTicks,
        trackMovingEntity,
        oneShot,
        styleHints);
```

- `id`：Definition 内稳定 ID，不能为空。
- `target`：`MarkableObject`。
- `activateWhen` / `deactivateWhen`：空值分别回退 always/never。
- `markerType`：空值回退 `QUEST_OBJECTIVE`。
- `priority`：最小 0。
- `maxDistance`：非正值回退 `DEFAULT_MAX_DISTANCE = 512`。
- `refreshTicks`：非正值回退 20。
- `trackMovingEntity`：实体目标是否持续更新。
- `oneShot`：一次性消费。
- `styleHints`：不可变字符串 Map；网络有数量和长度边界。

快捷工厂：

```text
MarkSpec mark = MarkSpec.translated(
        "example_addon:chief_marker",
        "marker.example_addon.chief",
        new MarkableObject.EntityByNpcId("example_addon:chief", 128));
```

`translated` 写入 `styleHints["labelKey"]`。附属 Marker 应使用翻译键，不要把本地化文本硬编码到持久数据。

## `MarkableObject`

- `Pos(x,y,z)`：当前解析维度中的固定坐标。
- `DimensionPos(ResourceKey<Level>, x,y,z)`：明确维度坐标。
- `BlockPosition(BlockPos)`：当前维度方块坐标。
- `EntityByUuid(UUID)`：精确实体。
- `EntityByTypeNearest(EntityType, searchRadius)`：最近实体。
- `EntityByTypeThenStructure(...)`：先实体，后结构标签回退。
- `EntityByNpcId(npcId, searchRadius)`：按 ArcQ NPC ID。
- `StructureNearest(TagKey<Structure>, searchRadius, ...)`：最近结构，可固定 Y 或表面 Y。
- `CustomResolver(resolverId, args)`：附属服务端 resolver。

搜索半径必须大于 0。结构目标基于结构注册/标签，不代表“任意看起来像建筑的方块集合”。

## 激活条件

`MarkActivations`：

- `always()` / `never()`；
- `questActive(questId)`；
- `flagSet(flag)` / `flagNotSet(flag)`；
- `and(...)` / `or(...)` / `not(...)`。

条件在服务端 Marker 协调时计算。复杂业务状态可转成 ArcQ flag，再组合激活条件，避免 Marker 刷新路径调用昂贵外部 API。

## Trigger

`MarkTrigger` 包含：`CONTINUOUS`、Quest 接受、Phase 进入/完成/推进、Objective 完成、Dialogue 节点进入和 Choice 选择。

`MarkTriggers.withTrigger(spec, trigger, durationTicks)` 生成事件触发标点，默认持续 100 ticks。`forDialogueNode` 附加目标节点 ID。

Phase 的 `trackingMarker` 只接受 continuous Marker；带事件 Trigger 的 spec 会被拒绝。
