# 事件与客户端需求实现配方

## 需求：选择正确事件而不是注入 Manager

| 需求 | 事件 |
| --- | --- |
| Quest 接受后初始化附属状态 | `QuestAcceptedEvent` |
| Objective 进度遥测 | `QuestProgressChangedEvent` |
| 并行 Phase 新激活 | `QuestPhaseActivatedEvent` |
| 明确 Phase 完成 | `QuestPhaseCompletedEvent` |
| Quest 完成/失败/放弃 | 对应 Quest lifecycle event |
| 对话开始、节点、选择、结束 | `api.event.dialogue` 对应事件 |
| Guide 解锁、seen、页进度 | `GuideEvents` |
| Trade/Gacha 决策 | Attempt/Pre 可取消事件 |
| Trade/Gacha 结果 | Success/Post/Failed 事件 |

事件 listener 保持“过滤 side 与 ID → 提取纯参数 → 委托 service”。不要保存 event、Session 或 Entity 供异步使用。

## 需求：并行阶段驱动可见性缓存

只监听 `QuestPhaseChangedEvent` 会漏掉 fan-out 的多个激活。使用 `QuestPhaseActivatedEvent` 与 `QuestPhaseCompletedEvent`，按明确 phaseId 重算。

完整代码：[ScenarioEventDrivenVisibility.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioEventDrivenVisibility.java)。

## 需求：追踪变更只在服务端执行业务

`TrackedQuestChangedEvent` 可能双端发布：

```text
if (!event.isServerSide() || !event.isAuthoritative()) return
```

服务端可更新附属持久状态；客户端只做动画、音效和提示。使用 revision 去除旧事件，不按到达时间猜顺序。

## 需求：监听商店打开推进任务

监听 `TradeOpenedEvent`，匹配 shopId 后调用 Quest adapter。该事件表示服务端已打开商店；购买任务应改监听购买成功事件。

完整代码：[ScenarioCommerceQuestBridge.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioCommerceQuestBridge.java)。

## 需求：Guide 最终页奖励

监听 `GuideEvents.ProgressChanged` 并检查 `hasReachedFinalPage()`；服务端持久化 claimed flag 后发奖励。不要把 `GuideCompletedEvent` 当作逐页完成证明。

完整代码：[ScenarioGuideRewardHandler.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ScenarioGuideRewardHandler.java)。

## 需求：对话界面添加画像或剧情装饰

使用 `DialogueOverlayRegistry`，而不是 Mixin `DialogueScreen#render`。Overlay：

- 客户端初始化时注册一次；
- 按 dialogueId 快速过滤；
- 每帧只读取 Screen/客户端快照；
- push/pop pose 并恢复渲染状态；
- 不遮挡 choice、history 和设置按钮；
- Screen 关闭后不再渲染。

完整代码：[ExampleClientExtensions.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ExampleClientExtensions.java)。

## 需求：按附属状态隐藏 Journal 页签

使用 `JournalTabVisibilityRegistry`。predicate 只影响 UI，不是服务端权限。服务端仍要验证 Quest 操作和数据请求。

predicate 高频执行，应读取已同步的轻量客户端状态，不扫描 Registry、磁盘或世界实体。未命中附属规则时返回允许，让其他扩展继续处理。

## 需求：自定义 Marker 标签、颜色和可见性

使用 `QuestMarkerPresentationRegistry`：

- 先按附属 markerId 前缀或 style hint 过滤；
- label/color 未命中返回 null；
- visibility 未命中返回 true；
- 不修改 Marker 快照；
- 不在每帧查询服务端状态。

[ExampleClientExtensions.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ExampleClientExtensions.java) 提供标签和颜色 resolver。

## 需求：特殊相机修正 Marker 投影

注册 `MarkerCameraBasisRegistry` provider。只处理确认支持的相机/骑乘状态，无法处理返回 null。向量必须有限且非退化；每帧避免创建大型集合。

## 需求：数据包重载后刷新附属缓存

`ArcQuestReloadEvents.Prepared` 可能在资源准备线程，只观察纯 diagnostics/snapshot，不访问世界。`Completed` 且 `isApplied()` 后才原子替换附属 Definition 查询缓存；失败时保留旧缓存。

## 需求：阻止交易或对话开始

只修改明确可取消事件：

- Dialogue 使用 `DialogueStartingEvent` 的公开取消语义；
- Trade/Gacha 使用 Attempt/Pre 事件；
- 先匹配附属 ID，再校验权限和状态；
- 返回可诊断但不泄露内部信息的原因；
- 不在取消 callback 内启动同类操作，避免递归。

## 验证清单

- registration event 在 MOD bus，runtime event 在 Forge bus；
- 双端事件有 side/authoritative 过滤；
- fan-out 使用 Activated 而非只依赖 Changed；
- 奖励 listener 具有服务端持久去重；
- 可取消事件取消后无成本和奖励副作用；
- 客户端 Registry 只注册一次；
- Overlay/Marker resolver 在 GUI scale 2/3/4 和小窗口安全；
- 专服不加载 `net.minecraft.client`；
- reload 失败不会清空仍有效缓存。
