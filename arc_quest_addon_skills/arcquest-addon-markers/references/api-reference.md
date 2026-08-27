# Marker API 深入参考

## `MarkSpec`

字段语义：

| 字段 | 含义 |
| --- | --- |
| `id` | 全局稳定 Marker ID；不能为空 |
| `target` | `MarkableObject` 目标描述 |
| `activateWhen` | 连续 Marker 激活条件 |
| `deactivateWhen` | 连续 Marker 停用条件 |
| `markerType` | Objective/NPC/其他表现分类 |
| `priority` | 多 Marker 排序优先级，负值归零 |
| `maxDistance` | 默认 `512`，非正值回退默认 |
| `refreshTicks` | 重新解析动态目标的间隔，非正值回退 `20` |
| `trackMovingEntity` | 是否随实体刷新位置 |
| `oneShot` | 是否消费后不再出现 |
| `styleHints` | 有界字符串表现提示 |

`MarkSpec.of(id, target)` 创建默认连续 Objective Marker。`MarkSpec.translated` 将 `labelKey` 写入 style hints。附属 Marker 必须提供翻译键，不能把临时中文/英文硬编码成稳定 ID。

## `MarkableObject`

### 固定坐标

- `Pos(x,y,z)`：当前上下文维度坐标；
- `DimensionPos(ResourceKey<Level>, x,y,z)`：明确维度；
- `BlockPosition(BlockPos)`：当前上下文维度方块位置。

跨维度剧情优先 `DimensionPos`。不要假设服务端玩家永远在 overworld。

### 实体目标

- `EntityByUuid(UUID)`：稳定实体实例；
- `EntityByTypeNearest(EntityType, searchRadius)`：最近指定类型；
- `EntityByNpcId(...)`：按 ArcQ NPC 标识；
- `EntityByTypeThenStructure(...)`：实体优先、结构回退。

实体类型不足以识别剧情 actor。若同类型实体很多，使用 NPC ID、UUID、附属 capability/tag 或 custom resolver 二次验证。

### 结构目标

- `StructureNearest(TagKey<Structure>, searchRadius)`；
- `EntityByTypeThenStructure` 的结构回退。

结构 tag 比扫描建筑方块可靠。结构搜索半径必须有界，且不应在每 tick 强制加载远端区块。

### 自定义解析

`CustomResolver(resolverId, Map<String,String> parameters)` 把纯字符串参数交给 `MarkTargetResolverRegistry` 中的 resolver。resolverId 是代码注册 ID，不是翻译键。

参数视为不可信输入：

- required key 必须存在且非空；
- ResourceLocation 用安全解析；
- 数字限制上下界；
- 参数数量、单值长度和搜索半径设上限；
- 未知 key 可拒绝或明确忽略；
- 解析失败返回 null 并限频记录上下文。

## `MarkTargetResolver`

签名：

```text
ResolvedMarkTarget resolve(ServerPlayer player,
                           ServerLevel level,
                           MarkableObject.CustomResolver target)
```

resolver 运行在服务端，可以读取当前 `ServerLevel`，但必须：

- 在主线程执行世界访问；
- 只搜索有界范围；
- 不同步读磁盘、HTTP 或数据库；
- 不保存 Level/Entity/Player 强引用；
- 找不到时返回 null；
- 动态目标成本与 `refreshTicks` 匹配。

注册：

```text
ArcQuestAPI.registerMarkTargetResolver(String resolverId, MarkTargetResolver resolver)
```

Registry 支持 unregister，但普通附属应在注册期一次性注册。热卸载并不是 Forge 常规生命周期。

## `ResolvedMarkTarget`

包含：坐标、dimension 字符串、entityId、entityUuid、entityGuid 和 `EntityAttachPoint`。

固定位置使用 `ResolvedMarkTarget.position(x,y,z,dimension)`。移动实体应返回实体身份和合适 attach point，使客户端可在同步间隔内跟随。

不要用 `0,0,0` 表示没找到，它会制造错误 Marker；返回 null。

## Marker 所属层级

### Quest

- `QuestBuilder#markRelatedObject`：Quest active 条件下的一般 Marker；
- `markOnAccept`：接受时短时 Marker。

### Phase

- `markRelatedObject`：Phase active 相关 Marker；
- `markOnEnter/Complete/Advance`：生命周期短时 Marker；
- `trackingMarker`：只有玩家当前聚焦该 Phase 时存在。

### Objective

- `markRelatedObject`：Objective 未完成/满足 activation 时；
- `markOnComplete`：完成短时 Marker。

### Dialogue

- 树级 `markRelatedObject`；
- 节点 `markOnNodeEnter`。

## `relatedMarks` 与 `trackingMarks`

`relatedMarks` 是定义层关联 Marker，是否出现由 Quest/Phase/Objective activation 和 trigger 决定；它不要求玩家追踪该 Phase。

`trackingMarks` 是 Phase 的临时焦点 Marker：

```text
追踪 Quest + 聚焦 Phase A
  -> 创建 Phase A tracking markers
切换焦点到 Phase B
  -> 删除 Phase A tracking markers
  -> 创建 Phase B tracking markers
取消追踪/Quest 完成/Phase 失活
  -> 删除当前 tracking markers
```

两者不能用同一 ID，否则 upsert/remove 会互相覆盖。

## 触发型 Marker

`MarkTriggers.withTrigger` 或 Builder 的 `markOn...` 把 Marker 绑定到生命周期事件并设置持续 tick。触发型 Marker 不应传给 `trackingMarker`，因为 tracking 需要连续创建/删除语义。

持续时间要有上限，避免断线、重登或事件洪泛留下大量 one-shot 状态。

## 客户端表现

`QuestMarkerPresentationRegistry` 是正式表现扩展点：

- `registerLabelResolver`：返回非 null `Component` 即命中；
- `registerColorResolver`：返回非 null ARGB/RGB 值即命中；
- `registerVisibilityFilter`：任一 filter 返回 false 就隐藏。

resolver/filter 每帧或高频调用：只读 Marker 快照，不做 Registry 全扫描、磁盘 I/O、网络等待或世界实体搜索。注册顺序影响“第一个非 null”结果，附属只拦截自己的 markerId/style hint。

`MarkerCameraBasisRegistry` 允许特殊相机/骑乘视角提供 basis。Provider 返回 null 让下一个 provider/fallback 接管；向量必须归一化且非退化。

## 事件和同步

- `QuestMarkerChangedEvent`：服务端 upsert/remove 差量；
- `QuestMarkersRefreshedEvent`：某 Quest 完成一轮刷新；
- 客户端 snapshot 事件用于外部地图同步。

不要在 Changed event 中再次无条件刷新同一 Quest，避免递归。外部地图集成使用稳定 markerId 做 upsert/remove，不按 label 匹配。

## 缓存和性能

自定义 resolver 缓存应明确：

- key：player UUID + resolver 业务目标 + dimension；
- value：纯坐标/UUID，不保存实体对象；
- 容量：按玩家和目标数量有界；
- 失效：实体移除、维度变化、Quest 结束、玩家离线、服务器停止；
- 回退：缓存不可用时重新有限搜索或结构定位。

缓存坐标只能作为导航回退，不应证明剧情实体真实存在。
