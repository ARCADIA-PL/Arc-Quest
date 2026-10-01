# ArcQ 服务端性能评估

审计日期：2026-10-01。范围：任务自动检测、进度同步、JEI 目录、商店快照、标记和玩家持久化。前半部分为源码工作量分析，末尾为已运行的有限服务端测试。**这不是多连接服务器压测，不代表支持人数或 TPS。** 简写源码路径相对于 `src/main/java/org/arcadia/arc_quest/`，行号对应本次读取。本轮未修改服务端生产逻辑；客户端修复及测量见 `SHOP_PERFORMANCE.md`。

## 优先结论

1. **JEI 周期刷新省了重复发包，没有省目录重建**：`integration/jei/network/JeiCatalogService.java:44-65` 在每 20 server ticks 的同一个回调内依次为全部有效订阅玩家收集、完整编码、散列目录，再判定是否发送。未订阅玩家不承担此成本；订阅租期 300 ticks。大量可见商品、历史任务、宽 tag、复杂条件会放大主线程计算和临时分配。
2. **原生需求刷新与标记刷新重复遍历目标**：`quest/tracking/QuestEventManager.java:175-243` 每玩家 20 ticks 先构建背包差量，再刷新所有 active quest 的需求并扫描到达位置目标；`quest/logic/ObjectiveRequiredCounts.java:15-29` 对所有 active phase 的目标分配新数组并计算需求，即使值没有变化。另一个每玩家 20 ticks 处理器会做超时、标记和 dirty persist。
3. **标签与收集事件仍有线性部分**：精确 ID 有全局索引，但 `quest/tracking/ObjectiveTypeIndex.java:57-65` 仍扫描该目标类型全部 tag refs；`quest/logic/profile/collection/CollectionObjectiveDispatcher.java:54-98` 在事件时扫描玩家 active quests，以及 collection 任务的全部定义 phase IDs 和 active phase objectives。
4. **每个进度事件可能重建活跃商店 UI 快照**：原生目标增量立即同步并 push Trade/Gacha；两种 UI 均在快照构建后才判断重复。大量目标同时命中同一事件、玩家保留商店活跃上下文时，放大明显。
5. **后台磁盘写入已有合并和容量限制，但主线程快照成本仍在**：`PlayerStateSessions` 全量 NBT 序列化和多次复制发生在主线程。普通 persist 的检查点异步、按玩家文件合并；登录读取、登出和 clone 必需写入会等待后台 Future，单次超时预算 5 秒。

## 触发频率与工作量

约定：P 为在线玩家数，J 为有效 JEI 订阅数，A 为玩家 active quests 数，O 为 active phase objectives 数，T 为该事件类型在全局定义中的 tag objectives 数，R 为精确 ID 匹配 refs 数，E 为目录条目数，B 为编码字节数。条件、数量修正器及第三方回调的执行成本须另计。每 20 ticks 仅在服务器达到 20 TPS 时约等于一秒。

| 路径 | 真实触发 / 主要成本 | 已有控制 | 风险与最小建议 |
|---|---|---|---|
| `QuestEventManager.java:175-243` | 每玩家 20 ticks；扫描主背包 items 和 cursor，求正增量；需求刷新和 reach-location 遍历。背包快照在取得 ArcQuestPlayer 数据之前执行（183-192）。 | 20-tick gate；位置目标索引按 quest/phase 缓存（204-210）；无全物品 registry 扫描。 | 可先判断是否存在 COLLECT/相关活动任务再构建快照；位置 xyz 可预解析。不能省略已有库存差量兜底语义。 |
| `ObjectiveRequiredCounts.java:15-35`；`ArcQuestNetwork.java:541-544` | 每轮约 O(A+O)，每 phase 分配 int[]、全部计算数量，变化才发单任务包。full/flags/state sync 也触发需求刷新。 | 结果未变不发包。 | 将静态数量和动态数量分开；同一 tick、同一玩家状态版本内共用需求投影。任意扩展回调仍需要安全的刷新兜底。 |
| `QuestEventManager.java:268-297`；`ObjectiveTypeIndex.java:57-65` | 每事件 collection 分派 + 全局候选查找；精确索引 refs R 与标签 refs T，再按玩家激活状态过滤。 | 精确 ID map；`ObjectiveItemResolver.matches:54-67` 用 live tag membership，不展开整个 tag 列表。 | 缓存 `(type,itemId,tagEpoch)` 的候选结果或建立反向 tag 索引；tags reload 必须失效。不能称当前所有匹配 O(1)。 |
| `CollectionObjectiveDispatcher.java:54-98` | 每事件先扫 A，再遍历 collection 定义的全部 phase IDs，过滤 active 后扫描 objectives。 | 非 ACTIVE/非 collection 任务早退；分派前再次检查可见、完成与重复计数规则。 | 建立玩家 collection binding 索引；在接受/完成/phase激活/可见性/tag变化失效。 |
| `QuestPhaseProgression.java:61-106,120-138` | 每个命中目标更新立即发 delta、Forge/event bus，之后检查本 phase 的目标至首个未完成项。 | 已达标目标通常提前退出；本次已算的 required 可复用。 | 在保留业务事件顺序的前提下合并同 tick 的 UI 重建/网络刷新；先测一个事件命中多目标的放大。 |
| `QuestDataTickHandler.java:48-112` | 每玩家 20 ticks：遍历 active quests 检查时限、expire/reconcile markers、dirty persist+sync。 | 无 dirty 则不 persist。 | 标记与需求投影共用当前数量结果；重计算应针对相关 active targets。 |

## JEI 目录

- `JeiCatalogProviders.java:23-41` 每次复制 provider list、以 TreeMap 构造全量排序输出；provider 原子发布、出错撤销，保证可见性隔离。这部分不能用跨玩家的完整目录共享替代。
- `quest/QuestJeiCatalogProvider.java:25-37,73-105` 只遍历玩家 active/completed/failed 已知任务，**不遍历所有 registry quest**；随后扫描各已知任务的定义 phases 并过滤已知/可见项，重新构建标题、说明、奖励和目标展示。历史积累也会影响成本。
- `trade/TradeJeiCatalogProvider.java:28-35,47-76` 遍历全部 trade registry shops，检查章节可见性和 canOpen，再遍历可开商店所有 entries；每条重新计算 visible、展示 costs/rewards、次数/冷却/资格。商店未实际打开也可能有可见 JEI 目录。
- `gacha/GachaJeiCatalogProvider.java:33-51` 遍历全部 pool registry，逐 pool 检查可见性、计算当前概率快照，再逐结果构建说明和奖励。概率快照含玩家保底状态，不能跨玩家直接复用。
- `guide/GuideJeiCatalogProvider.java:25-39,57-65` 只遍历已解锁 guide，association 去重、逐项构建；tag association 每轮展开为全体 ItemStack 候选。`ObjectiveItemResolver.java:26-47` 展示侧每次亦展开 tag 并排序。
- `JeiCatalogCodec.java:16-19,23-42,70-81` 上限 16 MiB、100,000 entries、每槽最多 65,536 alternatives；每次完整 NBT 编码并复制到 byte[]。这些是拒绝过大数据的安全上限，**不是实时性能预算**。
- `JeiCatalogService.java:68-81` 发送前再次散列，同一回调按 64 KiB 分片并复制、立即发送全部分片；16 MiB 最大目录可有 256 片。网络层压缩不改变 collect/encode 已在 server callback 内发生的事实。
- 最小改进顺序：先加 collect/encode/hash 时间、条目/字节和条件回调次数观测；再按订阅者错峰并限制每 tick 重建/发片预算；复用 tagEpoch/contentEpoch 下不含玩家秘密的静态候选数据。玩家授权、动态条件和 addon callback 不能凭不完整 dirty key 无限缓存，需明确失效和兜底刷新。

## 商店 UI 与同步

- `ArcQuestNetwork.java:550-562,634-643` 每次 delta 同步尝试 push 当前 Trade/Gacha 上下文；`TradeScreenService.java:49-69` 和 `GachaScreenOpener.java:115-137` 无 active context 早退。Trade 上下文 TTL 是 20,000ms（37），push 会延长（68-69）。不要把这条路径描述成每个玩家一直计算所有商店。
- `TradeScreenService.java:133-158,174-215` 构建全店 10 组数组，逐商品 reset-if-needed、visibility 和 canPurchase，然后 `LAST_SENT` 去重。`TradeEntryStateResolver.java:67-108` 的 canPurchase 再次执行 visibility，当前快照循环会重复评估可见性。**这里 canPurchase 是条件/限购/冷却资格，不扫描普通交易的物品成本**；实际交易 affordability 另算。
- `GachaScreenOpener.java:151-155,216-268` 先 resolveSnapshot 后去重；资格允许时遍历 draw costs 的 canAfford，不足时 buildShortfallLines 再检查 costs。Item cost 会按 inventory slots 计数。
- `TradeAutoRefreshListener.java:41-44,57-109` 的 1,000ms 防抖是客户端自动请求路径，不是服务器所有 push 的防抖。不要据此宣称服务端 UI 重建已限一秒一次。
- 最小建议：在一次快照内复用 visibility decision；同 tick 多次进度事件只安排一次活跃 UI 重建；购买/抽奖结果仍需保证即时可见。最终判定必须保持服务端真实业务状态验证。

## 主线程与持久化 I/O

- 默认 repository 是 `ArcQuestPlayerManager.java:14-15` 的 Capability repository。`CapabilityArcQuestPlayerRepository.java:42-50` 正常写 capability 快照，capability 缺失时才 fallback legacy SavedData；不可根据 `QuestSyncCoordinator` 注释把它说成总是立即写独立 SavedData 文件。
- `QuestSyncCoordinator.java:52-70` dirty 后才 persist；QUEST_STATE 的 fallback（76-77）实际发 full data。full sync 在 `ArcQuestNetwork.java:482-489` 会 refreshAll 并 syncMarkers，普通进度即时 delta 与后续 dirty 全量同步要在实测时分别计数。
- `PlayerStateSessions.java:152-177` 主线程全量 serializeNBT，主存储和 checkpoint 各传一份 copy；`CheckpointWriteQueue.java:131-136` 再计算 NBT size 并 copy，磁盘异步不代表这部分分配异步。
- `ArcQuestPlayerCheckpointStore.java:21-23` 普通 schedule 使用 2 秒延迟、1024 个 pending、64 MiB 估算 NBT 字节上限；`CheckpointWriteQueue.java:171-193` 同 path 合并更新，单线程后台（144-148）、最多 3 次写尝试（35,212）；版本水位缓存最多 4096（36,155-159）。
- `PlayerStateSessions.java:53-55,92-101,104-120` 登录检查点读取、登出 persistAndUnload、clone 强制持久化走必须成功的同步屏障；`ArcQuestPlayerCheckpointStore.java:30-47,134-136` 主线程等待 Future.get，单次超时 5 秒。这是 I/O 故障/慢盘情况下的停顿风险，**不是正常每次 objective update 的同步磁盘写入**。
- 最小建议：先测 NBT size、序列化/复制时间、pending bytes/count、合并率和 flush 等待；只对脏版本创建一次不可变快照，保持跨线程隔离与失败恢复约束。不能为了性能直接删除登出/交付的 durability barrier。

## 标记与多人边界

- `QuestMarkerReconciliationService.java:23-69` 每轮遍历 active quests/phases/objectives；即使 objectives 没有 marks，也会在 59 计算 required；另扫描 markers 去除过期绑定（115-141）。
- `QuestMarkerRuntimeManager.java:30-55` 有每 marker refreshTicks gate、非移动且已存在则早退，结果相同不 upsert。因此不能把每 20 ticks reconciliation 等同于每次都做所有空间查找。
- `NearestBlockTagMarkerResolver.java:18-21,33-46` 自定义 nearest block tag 的水平半径默认 48/最多 64，垂直默认 16/最多 32；仅查询已加载 chunk，但未命中时仍可能扫描大量 block positions。这类任务应单独测，优先更长刷新间隔、负结果缓存或分 tick 搜索。
- 当前源码搜索没有发现内置 team/party progression 管理器。`QuestEventManager.java:328-336` 的 `broadcastExplore` 是遍历调用者传入 UUID set、找在线玩家并逐个处理；复杂度按收件人数乘每人事件路径计，不能自动推断为每事件广播全服或完整队伍系统。

## 有限验收与真实压测的区分

已新增独立 `src/gameTest/java/org/arcadia/arc_quest/performance/ArcQuestServerPerformanceGameTests.java`。它在 GameTest 测试服务器世界创建少量 `ServerPlayer` 对象、真实 ArcQuestPlayer 会话和任务定义，调用真实需求刷新/Quest JEI provider+codec 路径；用数量修正器回调计数和目录字节量验证规模，报告纳秒但不以硬件相关耗时作成功阈值。对象不加入 PlayerList、没有真实客户端连接，因此不能验证网络发送、真实 ServerTick 全链或多连接 TPS。它只测 quest provider，**不包含 trade/gacha/guide provider、目录调度器、网络分片发送或磁盘吞吐**。正式多玩家测试应由主代理用真实服务器/客户端进行。

运行入口：`./gradlew.bat -PjeiServerAudit runGameTestServer --console=plain`，工作目录 `build/jei-gametest`。必须使用已有 `jeiServerAudit` 参数，将 Embeddium/Oculus 等客户端渲染依赖排除出服务端运行时。batch 为 `arc_quest.performance`，template 为已有 `jei_empty`。两个方法为：

1. `boundedThresholdRefreshKeepsPlayersIsolated`：两档规模 `(玩家状态, 每人任务, 每任务目标) = (1,4,4)` 和 `(4,16,8)`，首次刷新后重复三轮。检查不同玩家的动态需求独立、未变不要求重发、业务进度保持 0；首次每个目标只评估一次，重复轮允许优化减少回调但不能放大到每目标多次。
2. `boundedQuestCatalogRebuildStaysStableAndPlayerSpecific`：相同规模、每轮重复三次；目标交替使用真实 logs tag 和 apple，玩家等级不同。检查全部条目存在、动态数量隔离、tag 候选被实际展开、codec 可读、重复完整字节稳定、业务 NBT 未变。

日志前缀 `[ArcQuestServerPerformance]`：规模、初始/重复回调数、初始/重复时间；catalog 另分 projection/encoding 时间和每轮总字节。初始与重复数据只是有界诊断样本，JIT、GC、机器和共享测试运行均影响耗时；没有稳定采样分布前不要把它当 p95/TPS 或容量基准。fixture 使用随机 UUID，finally 清理测试玩家会话/JEI 已知历史并恢复原 registry。

### 2026-10-01 实际运行结果

日志 `build/shop-performance-server-final.log` 明确出现 **All 11 required tests passed**，包含新增的两个性能路径测试；不能只凭 Gradle SUCCESSFUL 判定 GameTest 成功。首次遗漏 `-PjeiServerAudit` 导致客户端专用依赖加载失败，该次未执行测试，不纳入结果。

机器：i7-12650H、约 16 GB 内存；Gradle launcher Java 21、游戏 toolchain Java 17、MC 1.20.1 / Forge 47.4.20。以下耗时为首次调用之后 **3 轮总时间除以 3**，不是可靠的 p95，也未测网络和磁盘。

| 玩家状态 × 每人任务 × 每任务目标 | 总目标 | 未变需求的重复回调数（3 轮） | 每轮需求刷新 | 每轮任务目录编码字节 | 每轮目录投影＋编码 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 1 × 4 × 4 | 16 | 48 | 0.0128 ms | 24,593 | 2.5761 ms |
| 4 × 16 × 8 | 512 | 1,536 | 0.1533 ms | 787,528 | 20.5310 ms |

目录每轮按玩家重新收集并编码；目标一半为 `minecraft:logs` tag，一半为 apple，数量回调使用玩家等级。重复目录回调同样为 48 / 1,536 次，未因字节一致而免除。787,528 是四份编码数据的合计，**不是每秒网络流量**：真实服务已有摘要去重，未变化的数据不会每秒全部发出。

测试验证：不同玩家需求没有串线、完整目录重复编码稳定、Tag 实际展开、业务 NBT 不变，最终恢复 registry 并清理会话。该小样本支持“目录构建是优先优化点”，不能按线性倍数推导服务器可容纳人数。真实容量还取决于在线订阅数、历史任务、全部 provider、条件回调、世界实体和其他模组。

### 后续优化顺序

1. JEI：记录每轮 collect/encode/hash、字节及分配；按订阅者错峰，将静态 Tag 候选按内容代次缓存，给主线程重建与发送设置每 tick 预算。保留按玩家授权隔离和动态条件兜底。
2. 任务：区分静态/动态数量，同 tick 共享需求投影；将同一玩家多个进度 delta 触发的 Trade/Gacha 刷新合并。保留业务事件次序及购买后的即时反馈。
3. 事件索引：缓存类型＋物品＋tagEpoch 的候选，减少全局 Tag ref 和 collection 活动阶段扫描。任务和资源更新时必须失效。
4. 持久化与位置搜索：先观测 NBT 大小、快照复制、检查点等待、空间搜索时长，再优化；保留落盘屏障、失败恢复和已加载区块限制。

真实多人验收建议分 1/4/8/16 个真实连接逐档运行，每档固定任务/目标/历史数量；区分无 JEI、已订阅、开商店和集中获得物品。预热后记录 tick p50/p95/max、GC、目录分配、网络字节、checkpoint 队列与事务正确性；不得把此处的四个服务器对象称为四个在线玩家压测。

验收应覆盖：同一内容重复刷新仍正确；玩家状态不同不会目录串线；无 JEI 客户端不订阅；多目标命中事件后进度和 packet 数；大 tag/大量历史/大商店；实际持久化在独立测试目录中无丢失。应报告输入规模、JVM/硬件、p50/p95/max tick、GC/分配、网络 bytes、checkpoint queue，不给未经测量的容量承诺。
