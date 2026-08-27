# Marker 代码模式

## 固定地点

明确维度时使用 `DimensionPos`。只使用 `Pos` 会按当前解析维度解释，跨维度 Quest 容易产生错误目标。

## 最近 NPC

有 ArcQ NPC ID 时使用 `EntityByNpcId`；只有实体类型时使用 `EntityByTypeNearest`。不要用“附近同类型第一个实体”替代具有唯一剧情身份的 NPC。

## 最近自然目标

“真正的树木”不能仅凭附近某个原木方块判定，因为建筑也会使用原木。应使用自定义 resolver，组合自然生成上下文、叶片连通、树干结构、位置和可接受区块范围，并把搜索做成有界缓存。Skill 不假设 ArcQ 内置 resolver 能理解附属对“自然树木”的业务定义。

## 追踪 Phase 临时目标

```text
PhaseBuilder.create("find_machine")
        .trackingMarker(MarkSpec.translated(
                "example_addon:find_machine",
                "marker.example_addon.find_machine",
                new MarkableObject.CustomResolver(
                        "example_addon:nearest_machine", Map.of("radius", "128"))));
```

切换到该 Phase 时协调器创建，切离、任务终止或追踪其他 Quest 时移除。无需附属维护玩家 marker 集合。

## 常见错误

- Marker ID 没有 namespace 或在多个 Definition 中碰撞。
- 固定位置未指定维度。
- tracking marker 带非 continuous trigger。
- resolver 强制加载大量区块。
- 客户端 presentation resolver 返回硬编码中文。
- 每帧/每 Screen 打开重复注册表现 resolver。

## 最低验证

- 同维度、跨维度、目标不存在和目标实体移除。
- 追踪 Quest/Phase 切换、重进存档和任务完成。
- 远近距离、屏内/离屏、GUI scale 2/3/4。
- resolver 半径边界、未加载区块和缓存清理。
