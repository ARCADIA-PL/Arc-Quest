# Marker 需求实现配方

## 需求：标记固定地点或跨维度地点

### 选择目标

- 当前上下文维度：`MarkableObject.Pos` 或 `BlockPosition`；
- 明确维度：`DimensionPos(ResourceKey<Level>, x, y, z)`；
- 不要用字符串 label 或地图显示名推断维度。

固定地点适合城门、祭坛和入口。位置在发布后变化时应更新定义版本或通过 resolver 动态解析，不要让附属与 ArcQ 同时维护两份坐标。

## 需求：并行 Phase 只显示当前焦点的导航

使用 `PhaseBuilder#trackingMarker`。每个 Phase 使用独立 Marker ID：

```text
追踪 Quest + 聚焦 market
  -> market tracking Marker 存在
切换到 workshop
  -> 移除 market tracking Marker
  -> 创建 workshop tracking Marker
```

其他 Phase 仍保持 active。完整示例：[ScenarioParallelQuestContent.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioParallelQuestContent.java)。

## 需求：无论是否追踪都显示相关目标

使用 `markRelatedObject`。它由 Quest/Phase/Objective activation 决定，与玩家当前焦点无关。

不要让 related Marker 与 tracking Marker 共用 ID，否则一方 remove 会覆盖另一方。若目标只应在事件发生后短暂显示，使用 `markOnAccept/Enter/Complete/Advance` 或 `MarkTriggers.withTrigger`。

## 需求：标记指定 NPC 或剧情实体

优先级：

```text
稳定实体 UUID
  > ArcQ NPC ID
  > 附属 actor 持久身份
  > EntityType + 附加 predicate
  > 最后才是最近同类型实体
```

仅按 `EntityType` 会命中同类普通实体。自定义 resolver 应校验 actor ID/tag/capability，再按距离选最近匹配对象。

完整实现：[ScenarioLayeredMarkerResolver.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioLayeredMarkerResolver.java)。

## 需求：目标实体暂时卸载时仍提供导航

### 分层解析

```text
当前有界范围内的匹配实体
  -> 返回坐标 + entityId + UUID + attachPoint
否则读取附属最后已知纯坐标缓存
  -> 返回 position target
否则
  -> null
```

缓存键包含 player UUID 和业务 actor ID，值只存坐标与维度，不保存 Entity/Level。示例限制总容量，并在玩家登出和服务器停止时清理。

缓存回退只用于导航，不证明实体仍存在；真正交互仍由服务端重新验证目标。

## 需求：标记最近结构而不是“像建筑的方块”

使用 `StructureNearest(TagKey<Structure>, radius)` 或实体优先、结构回退目标。结构 tag 是数据驱动的稳定身份；扫描木头、叶子、石砖等方块无法区分自然生成、玩家建筑和结构装饰。

结构搜索半径必须有上限，不能为了 512 格 HUD 可见距离就在每次 refresh 强制加载 512 格全部区块。

## 需求：自定义 resolver 参数

`CustomResolver` 的字符串参数是不可信输入：

- 必填 key 非空；
- actor ID 长度有限；
- ResourceLocation 使用安全解析；
- 数字 clamp 到业务范围；
- 未知参数明确拒绝或忽略；
- 失败返回 null，不返回 `0,0,0`；
- 相同错误按签名限频日志。

[ScenarioLayeredMarkerResolver.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioLayeredMarkerResolver.java) 展示 `actor_id` 和 `radius` 校验。

## 需求：客户端改标签、颜色或隐藏规则

使用 `QuestMarkerPresentationRegistry`：

- label resolver 只匹配附属 Marker，未命中返回 null；
- color resolver 返回附属颜色或 null；
- visibility filter 未命中返回 true；
- 高频回调只读取 Marker 快照，不搜索世界。

参考 [ExampleClientExtensions.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ExampleClientExtensions.java)。翻译标签写入 `MarkSpec.translated` 或 resolver 返回的 `Component`，不要硬编码不可本地化文本。

## 需求：特殊骑乘或自定义相机下修正投影

注册 `MarkerCameraBasisRegistry` provider。无法处理当前相机时返回 null，让后续 provider/default 接管。返回向量必须有限、非零、近似正交；每帧不分配大型对象，也不修改 Camera。

## 需求：同步到外部地图

监听 Marker changed/snapshot 事件，以稳定 markerId 做 upsert/remove。不要按 label 匹配，因为翻译和动态文本会变化。外部地图有自己的可见距离策略，不应反向扩大服务端 resolver 搜索成本。

## 验证清单

- 目标存在、消失、卸载、重新出现；
- 多个同类型实体只命中业务 actor；
- 跨维度坐标与维度 ID 正确；
- 并行 Phase 切换只替换 tracking Marker；
- related 与 tracking ID 不冲突；
- resolver 半径、参数长度和缓存容量有界；
- 玩家登出、服务器停止清理缓存；
- 远距离显示与搜索半径分别验证；
- GUI scale、离屏/屏内和特殊相机表现正常。
