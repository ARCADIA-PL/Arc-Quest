# 图鉴模式 Quest 实现与验收

历史记录起始日期：2026-10-02

**当前玩法已转为 v2 统一调查，以下按日期保留历史验收。** 新配置以 [统一调查使用说明](compendium-unified-quest-usage.md) 和 [可玩范例](compendium-quest-demos.md) 为准；早期独立 Research、提前点击消费等描述不能解释为新版规则。最新验收见本文末尾。

## 2026-10-02：旧版闭环（历史）

`QuestMode.COLLECTION` 保留正常的任务接取、真实 Phase、并行阶段、选择、手动推进、失败、放弃和奖励流程。图鉴使用三个独立层次：共享 Entry 定义、玩家永久发现/研究记录、具体 Phase 的 Binding 要求与本轮 Objective。

永久记录在没有接取任务时也可增长。登录背包只建立 COLLECT 发现事实，不制造拾取、制作或研究事件；管理员显式 reset 的条目不再接受旧背包或旧图鉴迁移回填。正常重复接取创建新的 Run ID 和行动计数，放弃与失败不会清空长期知识。显式 reset 则同时清除目标任务及关联条目记录，见末尾 reset 修复节。ALL/QUOTA、ALL/ANY 要求、可选要求、按不同 Entry 计数、接取时冻结候选和发现基线使用同一投影逻辑。

任务面板默认展示完整目录，支持分类、搜索、点击名称或空白打开居中次级详情、Guide 式比例图片和模态放大。二维生物头像和物品图标复用 Objective 图标提供器，Tag 在悬停期间固定当前候选。物品 Tooltip 复用奖励栏渲染，合法物品支持 JEI 左键配方、右键用途。点击浏览不改变追踪；条目聚焦与追踪器外观独立，追踪始终以具体 Binding 为单位，完成反馈约 1.2 秒后切下一条或隐藏，等待回报提示约 3.5 秒后退场。

玩家存档包含永久记录、最后一次终止任务的图鉴档案，以及早期尚有未领取条目奖励的归档。已完成或失败的任务结果读取本轮锁存事实，后续新知识只更新资料，不回填历史完成项。手动里程碑奖励在归档后仍可领取。旧版存档按可证明的主体/研究对应关系保守迁移，旧领取凭证限定在原任务内；无法映射的旧内容保留。

内容同步按玩家授权投影：未知任务、隐藏身份和锁定图片不通过原始 JSON 广播。服务端定义与客户端展示定义分开。记录增量与任务状态共用有序 revision；普通计数和已读改变不会重新压缩全部定义。当时网络协议升级到 19，客户端和服务端应使用相同平台的兼容版本；下方更早的验收记录属于协议 18 实现。当前 v2 协议为 20，见末尾本轮记录。

## 2026-10-02 奖励页签、2.5 基准与动画稳定复验

本节对应当时实现：任务面板基准为 **2.5**，覆盖下方更早验收中的 3.0／2.0；用户保存的字号倍率与最小逻辑视口约束继续生效。半透明条目档案保留，居中宽约 82%、上限 500 逻辑像素，高约 84%，短窗口仍保留阅读高度。例如 512×288 逻辑视口下档案为 420×242。

调查里程碑与章节完成奖励复用同一行奖励栏，通过「调查奖励／章节奖励」切换。当前实际阶段配置了奖励时，另外保留「阶段奖励」标签。调查节点旁显示未达成、领取或已领取；多节点使用原有横向滚动，节点领取资格与 JEI 授权继续读取生产缓存。分类筛选只影响奖励来源展示，条目自己的永久／本轮奖励仍在档案内。窄面板先缩小标签间距，必要时截断标题，保持字号及独立命中区域。

关闭跳变有两个原因：Minecraft 字体把 alpha 0～3 当作未指定透明度并改为不透明；原任务面板和阶段内容动画还会缩减正文宽度，导致文字重新换行和标本卡片重排。现在详情末帧不绘制但仍保留模态屏障，图标绘制前后隔离批次；底层顶部按钮持续绘制并按透明度淡出。任务面板与阶段内容使用固定排版宽度，仅平移，整体关闭期间逐帧验证目录宽度、卡片宽度和列数不变。

「调查中」悬停 0.2 秒后以约 0.12 秒交叉淡化、轻微文字位移和主题色底纹／细线切换为条目追踪入口；离开平滑恢复，提前点击仍只消费点击。牛、骨头的发现要求及煤炭的永久研究要求原本因没有本轮 ObjectiveEntry 被界面排除，现在使用与追踪控制器一致的精确资格。未完成记录可追踪，已完成发现、已满足门槛、隐藏、非激活和待回报条目继续保持原限制；不制造新的行动计数，也不会用另一个条目代替明确请求。

收藏书签、分类数量即时更新，但本次目录沿用刷新时的收藏排序快照。切换分类、修改搜索、任务投影更新或真正重开任务面板时，才重新应用收藏前排；各分组保持原相对顺序。

| 当时最终验收 | Forge 1.20.1 | NeoForge 1.21.1 |
| --- | --- | --- |
| JUnit 与完整构建 | 684 项，0 失败／错误／跳过；BUILD SUCCESSFUL | 690 项，0 失败／错误／跳过；BUILD SUCCESSFUL |
| 有 JEI 图鉴原生验收 | PASS，27 张截图 | PASS，27 张截图 |
| 无 JEI 图鉴原生验收 | PASS，26 张截图 | PASS，26 张截图 |
| 原生窗口／GUI／字号矩阵 | 10 组，通过 | 10 组，通过 |
| 奖励标签原生点击与横向条带 | 三个标签，默认／字号 2.0／小窗口三组通过 | 三个标签，默认／字号 2.0／小窗口三组通过 |
| 整体关闭与低透明度详情屏障 | stableExitLayout／lowAlphaExitSkipped 通过 | stableExitLayout／lowAlphaExitSkipped 通过 |
| 世界内真实 JEI | 12 类场景、42 次真实查询；完整验收 PASS | 12 类场景、42 次真实查询；完整验收 PASS |

相较前一轮新增 11 项 JUnit：延迟排序 2 项、悬停过渡与低 alpha 2 项、真实 Demo 记录追踪及精确资格 3 项、奖励来源与标签回退 4 项。总数还包含既有模组测试。原生验收实际点击书签、确认原地保留与刷新排序，逐帧观察追踪按钮从静止到过渡和稳定状态，并对实际主题色高亮细线采样像素；验证三个奖励标签和未达成／可领／已领取投影，切换时清除旧点击区域。

证据文件位于各工作区的 `build/collection-ux3-build.log`、`build/collection-ux3-client-jei.log`、`build/collection-ux3-client-no-jei.log`。截图位于 `run/screenshots/collection-quest/`，新增 `09-survey-node-rewards`、`10-chapter-rewards` 和两个窄视口奖励场景；已检查半透明缩小详情、追踪高亮、奖励切换和固定排版关闭截图。

两端另外实际执行 `runClient -PjeiRuntimeAudit`，日志为 `build/collection-ux3-real-jei.log`，均包含 `ICON_MATRIX_PASS visits=12 actualQueries=42` 及最终完整 JEI PASS。覆盖正常任务目标、阶段／章节奖励、指南、商店、抽奖和图鉴 iconItem；实际左键配方、右键用途、返回原 Screen、图鉴图片层阻断与条目追踪保持均通过。未直接调用打开详情来替代真实目录入口。世界截图位于 `run/screenshots/jei-icons/with-jei/`。

菜单没有玩家连接：调查节点可领与已领物品通过生产 JEI 命中注册验证，未达成节点没有查询区域；阶段／章节物品依赖服务端授权 JEI 目录，菜单只验证实际标签和物品点击区域，不注入伪目录。无连接领取点击确认不改变运行与记录快照，不声称实际发奖。奖励计次及服务端生命周期未改动，沿用历史服务端 GameTest；本轮未新增多人压力测试。

## 2026-10-02 次级详情、收藏与拓扑追加验收（历史）

本轮保留用户选择的半透明档案底色（原 3/4 背景 alpha）。档案以任务主题色绘制顶边和轻微底色染色；进入 0.20 秒、退出 0.16 秒，透明度与 8 逻辑像素位移共同过渡。背景悬停、鼠标样式、Tooltip、键盘、滚轮、点击及 JEI 命中在整个模态生命周期中隔离，退出最后一帧之后才恢复目录。Esc、返回按钮及档案外空白左键均关闭详情；图片层优先退出，详情淡出时再次按 Esc 不会误关任务面板。

上一轮将任务面板的基准缩放从 3.0 改为 2.0，导致任务面板默认文字变小，当轮恢复为 3.0。用户保存的字号倍率及最小视口约束继续生效。对话、指南、商店的基准和默认配置没有改动；两端实际字号配置四项均为 1.0，不覆盖用户字号设置。本节的 3.0 与下方“UX 与条目奖励”中的 2.0 均为历史记录，当前 2.5 以最新验收节为准。

条目左上角书签可收藏/取消收藏；收藏按共享 Entry ID 保存于客户端，隔离单人存档、服务器和玩家。收藏稳定前排，当前章节存在可见收藏时自动出现“收藏”分类；取消最后一个收藏后移除分类并回到全部。加载后普通渲染只读取内存，只有切换范围或用户修改收藏才执行文件读写。

可行动且未完成条目的“调查中”区域悬停 0.2 秒后显示追踪按钮，点击只切换具体 Quest/Phase/Binding 的追踪焦点。悬停未达阈值的点击消费掉，不意外打开档案；已完成、未公开或非激活阶段不提供快速追踪。字号调整旁新增追踪器显示开关，保存于本地配置 `quest_tracker.enabled`，隐藏不取消任务追踪。

真正关闭任务面板后再次打开，清空所有已访问章节的搜索；窗口重排和从 JEI 返回继续保留当前搜索。Tag 文案统一使用 `tag.item.<namespace>.<path>`，路径中的斜杠转为点，补齐常见原版、Forge、`c` Tag 中英名称并提供未知 Tag 可读兜底。资源包翻译优先，服务端/JSON 导出的默认目标在显示时重新绑定 Tag 名称，作者自定义文案保持原样。

现代 `collectionSheet` 图鉴拓扑由真实 Phase 及其下属 Binding 构成，阶段流转线与条目隶属线区分，包含并行、汇合和选择关系。未到达阶段/未公开条目匿名显示，完全隐藏条目不入图；终止任务使用保存的本轮档案与完成门槛。条目详情区分永久记录、本轮调查和可选要求。物品与 Tag 图标沿用奖励 Tooltip 和 JEI 查询，动画、拖拽与相机移动时阻断查询，离屏节点不渲染。

| 本轮最终验收 | Forge 1.20.1 | NeoForge 1.21.1 |
| --- | --- | --- |
| JUnit 与完整构建 | 673 项，0 失败/错误/跳过；BUILD SUCCESSFUL | 679 项，0 失败/错误/跳过；BUILD SUCCESSFUL |
| 有 JEI 图鉴原生验收 | PASS，23 张截图 | PASS，23 张截图 |
| 无 JEI 图鉴原生验收 | PASS，22 张截图 | PASS，22 张截图 |
| 原生窗口／GUI／字号矩阵 | 10 组，通过 | 10 组，通过 |
| 世界内真实 JEI | 12 类场景，42 次实际查询；完整验收 PASS | 12 类场景，42 次实际查询；完整验收 PASS |
| 图鉴拓扑入口与详情 | 正式入口及兼容入口通过；夹具 1 个 Phase、43 个 Binding | 正式入口及兼容入口通过；夹具 1 个 Phase、43 个 Binding |

相较上一轮新增 22 项 JUnit：Tag 名称 5 项、收藏持久化与隔离 5 项、模态/悬停/搜索状态 5 项、真实阶段与条目拓扑 7 项。总数包含模组既有回归，不全部称为图鉴专项测试。本轮没有改动奖励计次或服务端任务生命周期，沿用前述服务端 GameTest 和编辑器验收。

原生菜单验收实际验证书签点击、收藏前排、最后收藏分类移除、悬停阈值与具体 Binding 追踪、追踪器仅切换显示、调整窗口保留搜索、模拟 JEI 挂起恢复以及真正重开清空搜索。详情验证进入/退出屏障、背景 hover/cursor/Tooltip/JEI 阻断、外部点击动画关闭和淡出期间第二次 Esc 隔离。拓扑测试覆盖并行、汇合、选择、未到达匿名、完全隐藏、可选/ANY 计数及终止档案。

世界内 JEI 验收使用可见卡片标题的真实鼠标路由；字号恢复后脚本依实际裁切范围滚动父面板，搜索修改后等待真实渲染再点击，详情动画稳定后才查询。两平台均实际左键查配方、右键查用途、返回原任务面板且条目追踪不变，图片层仍阻断底层查询。保持既有任务、奖励、指南、商店和抽奖的 42 次查询，未用直接打开详情或跳过查询代替验收。

最终证据文件在对应工作区：

- 两端构建：`build/collection-ux2-build.log`。
- Forge 图鉴：`build/collection-ux2-forge-client-jei.log`、`build/collection-ux2-forge-client-no-jei.log`。
- NeoForge 图鉴：`build/collection-ux2-client-jei.log`、`build/collection-ux2-no-jei.log`。
- 两端世界内查询：`build/collection-ux2-real-jei.log`，包含 `ICON_MATRIX_PASS visits=12 actualQueries=42` 及完整 JEI PASS。
- 截图：`run/screenshots/collection-quest/` 与 `run/screenshots/jei-icons/with-jei/`。已人工检查半透明详情、小窗口、收藏、悬停追踪与拓扑截图。

菜单夹具没有玩家连接，不声称实际发奖或完整原生物品 Tooltip 绘制；真实查询和返回由世界内验证，奖励领取仍依据上一轮服务端验收。未新增多人压力测试。

## 2026-10-02 本轮 UX 与条目奖励追加验收

最终采用完整目录与居中次级详情：点击卡片名称或空白打开资料，物品图标仍执行 JEI；Esc 依次退出图片、详情、任务面板。任务列表标题与进度按整体两行测量，预留悬停缩放和徽标宽度。任务面板基准物理缩放由 3.0 调整为 2.0，继续应用字号与最小可用视口约束。卡片高度 80、分类一行分页；详情使用窗口全高减边距，顶栏固定追踪与返回按钮。目录与详情滑条都支持轨道点击、拖动及松开，正文与滑条的 gutter 分离。

搜索仍使用 EditBox 输入行为，文字、选区与光标由 FadingSearchBox 统一应用面板透明度。搜索聚焦只阻止 JEI 键盘查询，物品鼠标查询可用并在准备查询时解除搜索焦点。正式原生 JEI Pre 按键事件验证配方键不会抢走搜索输入。补齐 7 类官方 Objective 翻译及条目奖励文案。

追踪始终选择具体 Binding。同 Quest/Phase/Run 的服务端回传保留手动焦点；首次或登录恢复选择稳定的未完成可见条目。完成反馈 1200ms 后切下一 Binding 或隐藏，不回总览；等待回报 3500ms 后退场。重新接取隔离旧 Run。

条目奖励按用户确认规则落盘：发现与永久研究每玩家/Entry/rewardId 永久一次，调查完成按 Quest/run/phase/binding/rewardId 每轮独立；默认 MANUAL，可显式 AUTO。连续多轮未领取的调查奖励保存并标注往期，精确旧 runId 领取后清理早期归档。Java、JSON、网页编辑器和三个 Demo 都支持，网络协议为 19。隐藏奖励凭证和锁定载荷经接收玩家授权裁剪；回调前记录领取凭证，异常不重放整包。

| 本轮实际验收 | Forge 1.20.1 | NeoForge 1.21.1 |
| --- | --- | --- |
| 最终 JUnit 与完整构建 | 651 项，0 失败/错误/跳过；BUILD SUCCESSFUL | 657 项，0 失败/错误/跳过；BUILD SUCCESSFUL |
| 服务端原生 GameTest | 27 个 required 全通过 | 28 个 required 全通过 |
| 有 JEI 图鉴原生验收 | PASS，18 张截图 | PASS，18 张截图 |
| 最终半透明底色、无 JEI 原生验收 | PASS，17 张截图 | PASS，17 张截图 |
| 世界内真实 JEI | 12 类场景、42 次实际查询；搜索 Pre 按键通过 | 12 类场景、42 次实际查询；搜索 Pre 按键通过 |
| 网页编辑器 | 69/69、Vite 构建通过 | 三个共用编辑器文件逐字节一致 |

相较前期新增 24 项 JUnit 与 4 项服务端 GameTest，总数还包含既有模组回归，不能都称为图鉴专用测试。4 项新增 GameTest 验证永久共享奖励、每轮归档与重复领取、多轮欠奖及玩家存档往返、AUTO 事件/重入/异常处理。测试使用无网络连接的合成 ServerPlayer，新手指南预先解锁以隔离无连接的自动指南下发，未改变正式奖励执行路径。

客户端场景矩阵是每次实际启动在运行中改变原生窗口、GUI 尺寸和任务字号，使用生产 getUiScale，不使用假视口：

| 窗口 | GUI 尺寸 | 任务字号 |
| --- | --- | --- |
| 1280×720 | 1、2、3、4 | 1.0 |
| 1280×720 | 3 | 0.75、1.25、1.5、2.0 |
| 960×540 | 3 | 1.0 |
| 1920×1080 | 3 | 1.0 |

每组验证真实卡片入口、模态高度、gutter、背景点击阻断及 Esc 返回；另验证目录/长详情的真实滑条拖动、图片放大、隐藏条目不出现在目录、Tag 悬停候选与 JEI 命中一致、关闭期间停止输入。菜单夹具验证永久手动/永久已领取/本轮锁定/往期可领取四条奖励投影、两个按钮精确 sourceRun、实际 hover 栈及三个奖励物品 JEI 命中。菜单没有玩家连接，因此不声称菜单按钮实际发奖或完整物品 Tooltip 绘制；真实发奖由服务端 GameTest 验证，真实 JEI 查询与返回由世界内验收验证。

本轮先比较了半透明与不透明档案底色，用户最终选择半透明，已恢复原 3/4 底色 alpha，并在有/无 JEI 的已验证交互基础上重新执行最终无 JEI 全场景。保持背景压暗、正确绘制层级与关闭淡出，不将半透明本身视为排版缺陷。已人工检查 GUI 3 目录、详情、奖励、长资料、大字号和小窗口原生截图。

本轮日志均在对应工作区：`build/collection-ux-final-junit.log`、`collection-ux-gametest.log`、`collection-ux-client-jei.log`、`collection-ux-client-no-jei.log`、`collection-ux-real-jei.log`。编辑器日志在 Forge 工作区 `build/collection-ux-editor-tests.log` 与 `collection-ux-editor-build.log`。截图位于 `run/screenshots/collection-quest/`，最终半透明详情为 `02-secondary-details-no-jei.png`，奖励为 `04b-entry-rewards-previous-run-no-jei.png`；有 JEI 的 18 张包含搜索选区及 Tag 场景，无 JEI 的 17 张不包含 Tag 查询截图。

COLLECT 丢捡重复计数仍属独立方案，未改变现有累计语义。详见 [计数与来源方案](collect-counting-and-provenance.md)：建议新内容增加 HOLDING 当前持有模式，高价值重复奖励使用现有 OFFER/DELIVER 消耗样本；累计获得且跨容器/玩家严格去重需另建完整来源账本。

## 前期完整实现验证（追加验收见上文）

两个工作区的最终构建、自动测试和原生客户端验收均已通过。日志位于各工作区的 `build/collection-*.log`；截图位于 `run/screenshots/collection-quest/`。

| 验证项 | Forge 1.20.1 | NeoForge 1.21.1 |
| --- | --- | --- |
| JUnit | 627 通过，0 失败/跳过 | 633 通过，0 失败/跳过 |
| 服务端原生 GameTest | 23 个 required 全通过 | 24 个 required 全通过 |
| 完整构建 | 通过 | 通过 |
| 图鉴客户端：有 JEI | PASS，6 张截图 | PASS，6 张截图 |
| 图鉴客户端：无 JEI | PASS，5 张截图 | PASS，5 张截图 |
| 世界内真实 JEI 查询 | 12 个场景、42 次查询 | 12 个场景、42 次查询 |
| 网页编辑器 | 67/67 测试、Vite 构建通过 | 相同编辑器实现 |

NeoForge 多一个注册表谓词移植 GameTest；两分支的图鉴领域 GameTest 都包含 9 个生命周期/记录用例和 3 个里程碑奖励用例。全部 GameTest 的总数还包含已有 JEI/目标一致性与服务端性能测试，不把 23/24 项全部称为图鉴专用测试。

两分支均通过原生 JEI 验收。图鉴场景检查显式 iconItem、正确 OUTPUT/INPUT focus、查询后返回原任务面板、图片模态阻断底层查询、追踪与库存/经验不变；原有任务、奖励、指南、商店、抽奖场景继续通过。

有/无 JEI 的图鉴原生验收分别生成 6/5 张截图，覆盖目录收纳、资料展开、图片放大、Tag 当前候选、窄屏与关闭动画。原生追踪器实际绘制总览、聚焦、短暂完成和待确认四种状态，并验证计时与状态恢复。菜单验收使用明确公开的测试定义和受控缓存，不会打开玩家存档；真实配方/用途验收则在游戏世界中运行。

服务端 GameTest 使用真实注册表和 Tag、ServerPlayer 及正常任务入口；测试玩家不连接真实客户端。它们验证真实事件、手动回报、配额、重接新轮次、归档领取、防重复奖励、Tag 提交实际消耗和记录资格。补充用例覆盖自动/手动阶段奖励回调重入、里程碑回调重入、奖励单项异常后的收尾与无法完成的新发现任务接取拒绝。JUnit 覆盖 JSON/Builder、跨玩家内容权限、存档/网络往返、热重载缺失绑定、布局、图片适应和计时。编辑器测试覆盖稳定引用、配额、旧配置迁移、第三方 Objective 往返和图片字段。

### 实际执行命令与证据

在对应分支的项目目录执行：

```powershell
.\gradlew.bat test runGameTestServer build -PjeiServerAudit --console=plain
.\gradlew.bat runClient -PcollectionRuntimeAudit --console=plain
.\gradlew.bat runClient -PjeiRuntimeAudit --console=plain
.\gradlew.bat runClient -PcollectionRuntimeAudit -PwithoutJei --console=plain
```

Forge 随后修改了 Demo 测试的项目目录定位，并补跑 `test build -PjeiServerAudit`，日志为 `build/collection-final-junit.log`；未修改服务端逻辑，因此没有重复服务端原生测试。JUnit 表格数量来自最终 `build/test-results/test/TEST-*.xml`。

| 证据文件 | 确认结果 |
| --- | --- |
| `build/collection-final-verification.log` | BUILD SUCCESSFUL 与 23/24 个 required GameTest 全通过 |
| `build/collection-client-audit-jei.log` | `ARCQ_COLLECTION_CLIENT_AUDIT PASS`，6 张截图 |
| `build/collection-client-audit-no-jei.log` | 同一验收入口 PASS，5 张截图 |
| `build/collection-real-jei-client-audit.log` | `ICON_MATRIX_PASS visits=12 actualQueries=42` 及最终 JEI PASS |

原生客户端运行与同目录编译严格串行，避免 Gradle 清理类输出破坏游戏延迟加载。两分支的 JSON Demo 内容一致；数据包格式分别为 15 和 48，生成器按 `gradle.properties` 自动选择。

构建产物：

- Forge：`build/libs/arc_quest-forge1.20.1-1.0.8-all.jar`。
- NeoForge：`build/libs/arc_quest-neoforge1.21.1-1.0.8.jar`。

## 图鉴分类与 HUD 细化复验

2026-10-02 追加优化：新增内置「图鉴」任务列表分类 `arc_quest:collection`（`QuestCategory.COLLECTION`），Java/JSON 三个 Demo 使用一致分类和主题色。任务列表分组与 `QuestMode.COLLECTION` 相互独立，不覆盖作者配置的其他分类。

标本卡片改为最小宽度 92、高度 88 逻辑像素，图标、名称和状态分行展示；使用低对比底色、短选中标记、细追踪标记和搜索焦点线。装饰使用固定数量的 GUI 图元，未增加物品渲染轮次，目录仍只绘制可见行。

右侧详情由原约 57% 收窄至约 34%，限制为 206～260 逻辑像素；配图最大高度由 150 降至 112，保留点击放大。窄屏使用全宽二级详情，工具栏限制返回按钮宽度并截断标题，正文保持原字号与自动换行。任务列表及旧图鉴详情的进度翻译修正为传入两个参数，避免显示原始 `%s`。

本轮只改客户端呈现、分类与 Demo 配置。双分支重新运行 JUnit 与完整构建；原生游戏串行执行有/无 JEI 图鉴验收。Forge 另复验世界内 12 类场景、42 次真实 JEI 查询，覆盖图鉴 `iconItem`、配方/用途、图片模态阻断、返回原面板与追踪保持。领域逻辑和网页编辑器未改动，沿用前述服务端 GameTest 与编辑器验收。

| 本轮证据文件 | Forge 1.20.1 | NeoForge 1.21.1 |
| --- | --- | --- |
| `build/collection-art-final-build.log` | BUILD SUCCESSFUL，627 JUnit，0 失败/错误/跳过 | BUILD SUCCESSFUL，633 JUnit，0 失败/错误/跳过 |
| `build/collection-art-final-client-jei.log` | PASS，6 张截图 | PASS，6 张截图 |
| `build/collection-art-final-client-no-jei.log` | PASS，5 张截图 | PASS，5 张截图 |
| `build/collection-art-final-real-jei.log` | PASS，12 类场景、42 次真实查询 | 本轮未重复，前述完整实现验收已通过 |

原生截图统一为 1280×720；验收等待有效 framebuffer 和进入动画接近完成再采样，关闭动画仍在低 alpha 期间采样。已人工检查宽屏展开与窄屏详情，确认目录空间扩大、图文裁切和按钮布局正确。截图位于 `run/screenshots/collection-quest/`，例如 `01-expanded-jei.png` 与 `05-narrow-details-jei.png`。

## 使用入口

内置 Demo：

```mcfunction
/arcquest quest give @s arc_quest:field_compendium_demo
/arcquest quest give @s arc_quest:renewable_survey_demo
/arcquest quest give @s arc_quest:parallel_expedition_demo
```

分别对应单章共享知识、多候选配额重复委托、真实双并行章节调查。可安装 JSON 数据包位于 `docs/examples/collection/collection-demo-pack/`，三个任务使用 `arc_quest_examples` 命名空间，能够与内置任务同时加载。详情见 [可玩范例](compendium-quest-demos.md)。

## 验证边界

自动测试与原生渲染/交互验收覆盖上述功能链路；尚未做两台真实客户端长时间联机、复杂整合包压力测试或所有第三方图标/媒体提供器遍历，因此不把这些验收称为完整多人负载认证。图文首版支持文字和本地图片，未扩展到图鉴内嵌 Ponder、视频或团队共享图鉴。

里程碑在调用奖励前登记领取凭证，单项异常保留凭证并记录日志，继续后续奖励和正常归档，避免回调重入再次发放。该行为已测试；它没有把任意第三方奖励副作用和玩家数据磁盘保存做成跨崩溃原子事务。异常奖励的补偿应依据日志处理，不自动重放整包。

旧存档只有已完成任务 ID 而没有发现进度或领取事实时，迁移不会编造这些数据。修改已发布稳定 ID 或删除正在运行任务的候选可能需要作者主动迁移；缺失冻结绑定保持未完成，避免热重载静默发奖。

## 2026-10-03：永久进度、领奖反馈与头像恢复

本轮明确区分公开身份、永久发现/研究和本轮调查：默认公开的僵尸档案在未发现时显示「已公开 · 未发现」，永久研究和本轮要求放在长正文之前。荒野手册首次击败登记发现，累计五次完成永久研究，而本轮要求是击败三次并提交腐肉；达到本轮要求不等于解锁三枚铁粒研究奖励。

奖励入口增加可领取红点与明确的「领取奖励」按钮。红点只表示当前可手动领取，不表示普通完成或已经领取。领取凭证同步更新奖励和红点；同一内容 epoch 的授权定义替换保留次级档案、浏览焦点、阅读位置和动画，不重新打开面板。真实数据包重载、任务类型/状态变化及移除任务仍重置上下文。领取不发 Toast，历史记录仍保留。此阶段曾验证单 Quest reset 保留共享记录的旧行为，该语义已被末尾 reset 修复覆盖；正常重复接取仍保留知识。content 是资料块及公开时机，不是另一组任务。

| 本轮最终证据 | Forge 1.20.1 | NeoForge 1.21.1 |
| --- | --- | --- |
| `build/collection-portrait-build.log` | 完整 build 成功，709 JUnit，0 失败/错误/跳过 | 完整 build 成功，716 JUnit，0 失败/错误/跳过 |
| `build/collection-portrait-client-jei.log` | PASS，33 张截图，79 Mob | PASS，33 张截图，82 Mob |
| `build/collection-portrait-client-no-jei.log` | PASS，32 张截图，79 Mob | PASS，32 张截图，82 Mob |

新增原生审计经生产授权投影与客户端编译，验证公开未发现、永久 3/5 与本轮 3/3 分离、锁定物品未泄漏、可领取红点的实际像素、真实领奖入口，以及服务端确认凭证更新后的详情滚动/动画保持。36 个僵尸脸部像素与原始 PNG 的 RGB 容差为 2；头像与父级透明度只混合一次，外部 shader tint 不染色。全 Mob 清单来自原版 EntityType 泛型及 Mob 继承关系，逐项检查 available、非物品头像和真实 GPU 像素，重载后旧 handle 无效、新头像恢复。

截图 `11-public-undiscovered`、`12-lifetime-three-of-five`、`13-visible-primary-claim`、`14-claim-refresh-no-flicker` 在 `run/screenshots/collection-quest/`；`15-restored-hud-portrait-tint` 与 `16-vanilla-mob-portrait-atlas` 在 `run/collection-client-audit/`，分别有 `-jei.png` 和 `-no-jei.png`。头像总览暂用 1920×1080 / GUI 2，审计退出还原窗口与配置。

本轮图形验收在无世界菜单中调用同一生产追踪器 `renderPanel(preview=true)`，禁止示例 fallback；清会话后先由图鉴 HUD 自己请求头像，下一帧 HUD 边界准备，检查实际头像槽位。它覆盖恢复数据与生产绘制链，不声称实测真实存档登录、overlay 事件派发或多人负载。领奖回执夹具验证客户端刷新，不声称完成本轮实际联网发奖；领域单元回归另验证真实 Demo KILL1..5、奖励资格、授权导出/编译和永久领取凭证，既有服务端 GameTest 结果按前述记录使用。

## 2026-10-03：显式 reset 后图鉴从零开始

本节覆盖前述单任务 reset 保留永久知识的旧语义。`/arcquest quest reset <player> <questId>` 清除目标任务的运行状态、完成/失败状态、全部图鉴归档和相关条目的发现、研究、已读、已解锁/已领取奖励凭证。重新 give 后本轮 Objective 与记录型要求都从零开始，发现后/研究后公开的内容及奖励权限重新锁定；默认公开的基础名称仍按配置可见。普通重复接取、放弃和失败继续保留长期知识。

条目按 `entryId` 共享，因此其他任务引用的同一份档案知识也归零；其他任务的运行状态、行动计数、已锁存完成结果和归档保留。不关联条目的记录保留。reset 也清除目标任务的旧迁移标记及旧奖励凭证，但保留持久化的服务端重置标记以阻止旧背包、旧图鉴数据在恢复时重新填入进度；真正的新事件仍可建立发现、推进研究并重新获得奖励。空条目作为增量清零记录发送，旧 revision 的记录增量不能恢复旧进度；服务端重置标记不会下发客户端。

新增/调整回归：Demo 全部八个 Binding 及所有关联 Entry 清零；奖励、已读、档案权限回退；不关联记录及其他任务锁存/归档保留；增量清零、持久化/复制与旧 revision；库存恢复不回填而新事件可重建；真实注册表的字符串 reset 和旧主体映射；旧图鉴迁移不回填。服务端新增 GameTest 直接执行真实 reset/give 命令，重置前保留煤炭样本，重置后调用生产库存发现入口并验证零状态；重新发生真实事件后再次发放绿宝石与铁粒，重复领取仍拒绝。

两端执行 `.\gradlew.bat test runGameTestServer build -PjeiServerAudit --offline --console=plain`，完整构建成功。Forge 1.20.1 为 714 项 JUnit 与 28 项必需 GameTest；NeoForge 1.21.1 为 721 项 JUnit 与 29 项必需 GameTest，均无失败。两端 JUnit 均无错误或跳过，日志为各工作区 `build/collection-reset-verification.log`。新增服务端测试 `resetAndGiveCommandsClearCollectionFactsAndRewardsWithoutReimportingHeldSamples` 通过实际指令与生产库存发现入口验证重置后零状态、旧 Run ID 失效、真实新事件推进以及实际绿宝石/铁粒重新发放和同轮防重复领取。

本轮服务端 GameTest 使用真实服务器及无客户端连接的测试玩家；库存恢复经过存档往返后调用生产恢复入口。它验证服务端状态、指令和发奖路径，不代表本轮重新实测图形客户端、真实存档登录或多人负载。

## 2026-10-03：v2 统一调查与奖励交付

本节覆盖上文的旧独立 Research 玩法。新 Entry 只有 Discover、资料与 Outcome；Binding 引用当前 Phase 的 Objectives，是唯一调查计数。牛与骨头只收录，僵尸击败三次并提交腐肉后取得解剖成果，不再另杀五次。首次铁粒与本轮绿宝石按各自来源领取；普通发现 Binding 不自动产生成果。

两端保留原平台存档和事件 API，网络协议同步升级为 **20**。世界 SavedData 保存完整 JSON 或不可变 Java 版本工厂引用，当前 run 固定规则、Tag 成员与门槛。旧版本 run 继续使用隔离兼容执行器；无法恢复的原版本安全暂停，不套用新版回调。客户端仅获授权展示，执行载荷、交付 token 与 Tag 内部完成标记不下发。

| 完整服务端验证 | Forge 1.20.1 | NeoForge 1.21.1 |
| --- | --- | --- |
| `build/collection-unified-server-final.log` | BUILD SUCCESSFUL | BUILD SUCCESSFUL |
| JUnit | 801 项，0 失败／错误／跳过 | 809 项，0 失败／错误／跳过 |
| 必需真实 GameTest | 41 项全部通过 | 42 项全部通过 |

新增真实服务端场景使用有实际能力／附件的 ServerPlayer，调用生产事件、指令与领奖服务，并检查真实世界日志、玩家 `.dat` 与 ArcQ 检查点。它们覆盖一次合成不回填下一阶段、一次位置采样统一结算、reset / resetall 的零状态及新事件、旧成果不重播、共享知识与独立本轮报酬、重复冷却和 Tag 提交。测试玩家没有网络连接；存储 Accessor 与原版保存路径实际执行，正常联网玩家的 PlayerList Invoker 分支及真实客户端存档重登未单独实测。

奖励交付的八项 GameTest 覆盖：满背包欠奖保留并归档、真实存档重载后的七十颗钻石恰好两块交付、reset 撤销旧授权、首次日志不可写后资格保留且修复可重试、超大 AUTO 和 AIR 奖励在 claim 前拒绝、原领取检查点丢失后 AUTO 从持久授权恢复、自定义奖励嵌套领取不覆盖日志、回调提供者失联不阻塞已准备物品或其他奖励。预期异常夹具会输出 PERSISTENCE／QUEST_PROGRESS 错误，测试报告的失败数仍为零。新协议不从旧无授权标记的 journal 推认领取。

生命周期实测校正了一个前期假设：非重复任务放弃后已进入失败终态，生产接取服务返回 `ALREADY_COMPLETED_NOT_REPEATABLE`；重复任务同路径返回 `COLLECTION_REPEAT_COOLDOWN`。同一真实回归检查两种精确码、没有半接取活跃 run、没有额外绿宝石，以及管理员 reset 后可重新接取。实现保留原 CoreRule，没有为了通过测试改成可重接，也没有对合法的一次性报酬强加重复成本要求。

纯测试另验证：定义哈希与版本保存、完整原始条件／命令载荷、成果来源和迁移世代、未完成旧研究不回填新轮、奖励预览白名单、旧节点改名的欠奖来源、真实三个 JSON Demo 编译，以及 1000 次 Tag 已冻结解析不再次调用候选 resolver。Neo 增加只有回执但没有进度 payload 的附件保存回归。

性能记录仍使用真实服务器中的合成玩家对象，没有网络连接。最大目录用例为 4 个对象 × 每人 16 个任务 × 每任务 8 个目标，统计投影与编码；另有 threshold 回调数量检查。这能说明热路径与重复计算边界，不能当作真实多人 P95／P99 tick、同步带宽或整合包长期帧时间认证。奖励领取为可靠交付增加同步检查点、journal 与玩家文件核查 I/O；高频经济服务器仍需独立压测。任意指令、外部经济与自定义回调执行前记录已尝试，失败不自动重放，不承诺通用跨系统严格一次。

原生客户端矩阵与最终复验已完成，具体证据见下节；服务端结果与图形结果分别列出，不互相替代。

## 2026-10-03：客户端最终验收与证据边界

客户端验收在 Forge 1.20.1 与 NeoForge 1.21.1 分别执行有 JEI、无 JEI 两轮，使用生产任务面板、条目详情、追踪器、命中区域和关闭动画；没有把旧截图或单元测试当作原生通过。v2 场景已经覆盖发现与成果分离、同 Entry 多调查、调查／档案切换、首次／本轮奖励分页、奖励领取后稳定刷新、匿名线索追踪、冻结 Tag、快速追踪点击、收藏排序延迟、GUI 3／小窗口／大字号、拓扑视图和拖动滚动条。

审计运行于 title screen / noWorld，通过测试桥构造生产缓存与 GUI，而不是联网玩家会话。JEI 轮次确认模组被加载并走 ArcQ 的授权命中路径；标题界面没有真实 RecipeManager，因此没有声称本轮打开了 JEI 的实际配方／用途 Screen。登录头像场景模拟同步追踪快照清理后的客户端重建，没有声称完成真实玩家登录。奖励刷新使用隔离的回执夹具；真实奖励资格、库存、journal 和存档恢复由上面的服务端 GameTest 验证。

收尾的严格头像像素断言发现奖励条带的真实渲染问题：两次 push 裁剪只有一次 pop，后续界面被残留的裁剪区域遮住。两端已移除重复裁剪，并处理空分页退出时的释放；每次生产任务面板渲染退出后新增 `GL_SCISSOR_TEST=false` 检查，原头像像素、颜色及资源重载断言继续保留。没有绕过像素检查或将裁剪问题归因于头像缺失。

客户端额外修复同一稳定奖励 ID 的当前轮预告与往期欠奖查询：JEI 从已披露的各来源物品中匹配，未解锁的神秘奖不授权；已领取的披露奖励继续可查。编辑器中键改 Map 键后切换普通值会清理旧编辑状态，实际鼠标／键盘回归验证原键和其他列表不受污染。

四轮最终日志均有 `[ARCQ_COLLECTION_CLIENT_AUDIT] PASS`，没有 FAIL，并以 `BUILD SUCCESSFUL` 结束：

| 原生 GUI 环境 | 最终 PASS 时间（2026-10-03） | 场景截图 | 原版 Mob 头像逐像素覆盖 |
| --- | --- | --- | --- |
| Forge 1.20.1＋JEI | 07:31:09 | 38 | 79／79 |
| Forge 1.20.1 无 JEI | 07:32:28 | 37 | 79／79 |
| NeoForge 1.21.1＋JEI | 07:33:44 | 38 | 82／82 |
| NeoForge 1.21.1 无 JEI | 07:34:59 | 37 | 82／82 |

命令为 `./gradlew.bat runClient -PcollectionRuntimeAudit --offline --console=plain`，无 JEI 轮加 `-PwithoutJei`。日志分别在两端 `build/collection-unified-jei-audit.log` 和 `build/collection-unified-no-jei-audit.log`；38／37 是最终日志记录的场景截图次数，旧截图可能仍留在目录，不以目录文件数量冒充本轮数量。

每轮检查十种窗口／字号场景、三种奖励页签及三个奖励布局场景。新增 v2 夹具含三个 Entry、四个 Binding；首次奖励三页、本轮报酬两页，通过实际悬停读到十四个物品组件。960×540／GUI 3 中确认奖励全页可达、正文独立滚动及滑条拖动／释放；关闭期间改变生产缓存，仍保持内容、主题、成果、追踪和布局快照。模拟追踪重建后由真实 HUD 自行准备头像，核查实际头像可见像素、原图三十六个色样、调用方色调隔离、半透明和资源重载旧句柄失效。

主代理另用 `view_image` 检查两端小窗口档案／奖励、奖励刷新、恢复追踪器及原版头像图集，保留用户指定的半透明设计，未见奖励／按钮越界或残留裁剪。截图在两端 `run/screenshots/collection-quest` 和 `run/collection-client-audit`，其中 `25-unified-small-window-reward-and-archive-*.png`、`22-unified-reward-refresh-stable-*.png`、`15-restored-hud-portrait-tint-*.png` 是本轮关键场景。

最后客户端生产修复之后执行 `./gradlew.bat test build -PjeiServerAudit --offline --console=plain`：

| 最终单测与构建 | Forge 1.20.1 | NeoForge 1.21.1 |
| --- | --- | --- |
| JUnit XML 汇总 | 804，0 失败／错误／跳过 | 812，0 失败／错误／跳过 |
| `build/collection-unified-client-final.log` | BUILD SUCCESSFUL | BUILD SUCCESSFUL |

相对服务端 gate 增加两项 JEI 多来源／神秘奖回归与一项编辑器状态回归，服务端 GameTest 没有重跑；最后改动限于客户端。两端重新运行数据包生成器，1.20.1 为 pack_format 15、1.21.1 为 48，JSON 与 PNG 结果未产生差异。

本轮代码分三批本地提交，文档作为独立收尾批次；未推送远端：

| 批次 | Forge master | NeoForge 1.21.1 |
| --- | --- | --- |
| 统一调查、规则冻结、Demo | `ac90e506` | `b5a003b3` |
| 奖励恢复、生命周期及平台适配 | `55cd0ddb` | `5942661f` |
| 统一调查界面、编辑器及客户端审计 | `b7e49a59` | `925de831` |

Forge 工作区的原有 `CollectionFieldDemos.java` 主题色 `ChatFormatting.GRAY` 与对应 import 保留在工作树，未纳入本轮提交，未复制到 Neo 工作区。

## 2026-10-04：分类与条目配置排序

目录卡片采用「收藏 → 分类 sortOrder → 分类声明顺序 → 条目 sortOrder → 当前 Phase 首次 Binding 声明顺序」。分类同分仍保持完整分组；详情内的 Binding 单独排序，不改变卡片位置。新增三参 `category(id, name, sortOrder)`，原两参默认值保持加入时的声明位置。排序定义按不可变引用缓存，收藏立即更新标记，实际刷新才重新采集排序快照；同视图刷新尽量保留顶部条目及行内滚动位置。

两端定向执行 `CollectionCatalogOrderTest`、`CollectionInteractionStateTest`、`UnifiedCollectionDefinitionTest`、`CollectionContentDisclosureTest`、`UnifiedCollectionDemoPackTest`。其中新增 9 项覆盖分类分组、负数／整数极值、声明同分、收藏延迟、调查与卡片排序分离、定义缓存失效、滚动锚点、Builder 默认兼容及匿名条目经过实际同步编解码后仍保留排序值。

| 本轮验证 | Forge 1.20.1（Java 17） | NeoForge 1.21.1（Java 21） |
| --- | --- | --- |
| 定向 JUnit XML 汇总 | 41，0 失败／错误／跳过 | 41，0 失败／错误／跳过 |
| assemble | BUILD SUCCESSFUL | BUILD SUCCESSFUL |
| verifyReleaseJars | 普通／all 包生产映射检查通过 | 不适用 Forge 重混淆检查 |
| 实现与测试提交 | `0eb0d7f0` | `07baaa70` |

最终日志为两端 `build/collection-catalog-order-verification.log`。命令均使用 `-PjeiServerAudit --offline --console=plain`；Forge 同次执行 `assemble verifyReleaseJars`，Neo 同次执行 `assemble`。首次 Forge 定向运行暴露 Demo 测试依赖其他测试先加载内置分类的问题，现已在本套测试中显式初始化分类和 Objective 句柄，并补充实际校验错误的诊断；没有修改 Demo 的玩法或排序值。

两份作者文档的 10 个 JSON 代码块均可解析，本地文件链接与章节锚点有效。排序只在客户端副本应用，未修改服务端冻结定义、计数、完成和结算顺序，也未增加同步字段；网络协议仍为 20。本轮未重跑原生 GUI 审计、真实服务器 GameTest 或多人压测，前轮证据不作为本轮验收结果。Forge 原有 Demo 的 GRAY 主题改动仍留在工作树，没有提交或同步到 Neo。

## 2026-10-04：图鉴文本与中英 Demo

当前图鉴任务、分类、阶段、条目、发现／调查目标、成果、公开线索、档案正文和图片图注使用客户端翻译组件。Builder 新增 `category(id, QuestText[, sortOrder])`、`text(blockId, QuestText)` 和 `image(..., QuestText)`，原 String 重载继续表示字面文字。完整 Component 树经过授权披露、实际同步 codec、客户端编译和冻结存档后仍保留嵌套参数、fallback、追加文字与样式；JSON 文本新增 `component` 模式，旧 `literal`／`translatable` 文档格式继续兼容。

网络协议升级为 **21**；服务端和客户端必须同时更新。自定义奖励可以覆写 `IReward.describeComponent()` 提供翻译展示；同步后的自定义奖励预览不具备客户端发放权限。任务／图鉴奖励、JEI 说明和旧版图鉴界面统一使用组件展示。搜索、目录、列表、标题和奖励缓存随 Language 变化失效，详情描述使用带样式的换行；拓扑节点状态与阶段叙事标题同样本地化。

三条 Java／JSON Demo 使用 `arc_quest.collection.demo.*` 下的 **127** 个作者文案键，中英非空值齐全。包括 6 项只用于旧研究迁移的目标文字，三份安装 JSON 的 64 个目标均明确配置翻译文本。生成器拒绝缺失目标文案或非空字面文案，两端重跑后 6 个产物逐字节不变，数据包格式分别保持 15／48。当前内置 Demo 版本为 `collection-v4`；`FrozenCollectionFieldDemosV3` 保留原 v3 的规则和字面文字，v1／v2／v3 历史工厂继续服务已有存档。

首次完整 gate 执行 `test assemble`，另在 Forge 执行 `verifyReleaseJars`，两端均加 `-PjeiServerAudit --offline --console=plain`：Forge 839 项、NeoForge 847 项，失败／错误／跳过均为 0，普通／all Forge 包生产映射检查通过。日志保存在各工作区 `build/collection-i18n-test-build-initial.log`。最后追加的历史快照组件修正单独记录于本节后续验收结果。

两端本轮实际执行 `runClient -PcollectionRuntimeAudit -PjeiServerAudit --offline --console=plain`，均出现 `LOCALIZATION_NATIVE` 与 `[ARCQ_COLLECTION_CLIENT_AUDIT] PASS`，并以 `BUILD SUCCESSFUL` 结束：

| 原生 GUI 环境 | PASS 时间（2026-10-04） | 本轮截图次数 | 新增语言场景 |
| --- | --- | --- | --- |
| Forge 1.20.1／Java 17＋JEI | 20:40:28 | 46 | 8 |
| NeoForge 1.21.1／Java 21＋JEI | 20:43:21 | 46 | 8 |

新增场景加载实际 `minecraft`／`arc_quest` 的 `en_us`、`zh_cn` 资源，在同一个生产任务界面通过 `Language.inject` 切换文本表；不修改玩家 options，不将其等同于完整语言资源重载。隔离 fixture 经过真实披露 → `DatapackContentCodec` → 客户端编译。实际检查标题缓存、分类按钮宽度、条目标题 framebuffer 像素、同定义／同进度的搜索结果失效、档案正文和图注布局与可见像素、Objective 渲染入口、奖励领取按钮，以及 960×540 窗口边界；结束和失败路径恢复语言、缓存、档案、浏览状态、窗口与滚动位置。

日志为两端 `build/collection-i18n-client-audit.log`；截图为 `run/screenshots/collection-quest/26-localized-en-catalog-jei.png` 至 `33-localized-zh-small-window-jei.png`。主代理通过 `view_image` 实看 Forge 英文图注／中文小窗口、Neo 中文目录／档案正文，确认翻译、样式与半透明效果正常。原生审计继续覆盖已有奖励分页、动画、收藏、追踪和头像场景；运行于 title screen / noWorld，未声称真实联机配方 Screen、多人压测或服务端实际发奖，本轮没有重跑无 JEI 客户端或服务端 GameTest。

外部 AI 文档和完整 Java／JSON 范例同步改为翻译键，附带资源包的 19 个键均有中英文。两端作者手册的 8 个 JSON 代码块可解析，内嵌完整示例与独立文件一致，35 个本地文件链接有效；说明了客户端语言资源分发、Component JSON 和自定义奖励展示。Forge 用户原有 GRAY 主题及 import 保留在工作树，没有同步到 Neo。

收尾补齐历史文本快照：新的 Quest／Phase 名称、Objective 和 Entry 事件详情保存完整组件 JSON，旧 String 字段继续提供兼容回退；组件解码结果缓存，返回副本，字段改变时重新解析。两套历史 HUD、Objective 通知和奖励解锁通知使用组件，通知触发、领取静默策略、筛选、去重和内部 ID 不变。旧历史只有字面字符串时保留原记录；损坏的组件字段回退到该字段的旧文字，不丢弃整个文件或其他记录。

新增 4 项历史回归经过实际录入 → JSON 保存 → 加载 → 任务定义移除 → 同组件切换中英，并覆盖旧日志／损坏快照、缓存更新与副本隔离、语言切换后的去重和内部 ID。历史存储补充之后执行两端最终全量 `test assemble`，Forge 同次执行 `verifyReleaseJars`，参数仍为 `-PjeiServerAudit --offline --console=plain`：

| 最终完整 gate | Forge 1.20.1 | NeoForge 1.21.1 |
| --- | --- | --- |
| JUnit XML 汇总 | 843，0 失败／错误／跳过 | 851，0 失败／错误／跳过 |
| assemble | BUILD SUCCESSFUL | BUILD SUCCESSFUL |
| 生产包映射 | 普通／all 包通过 | 不适用 Forge 重混淆检查 |

最终日志为两端 `build/collection-i18n-test-build.log`。历史快照保存／加载使用实际 JSON JUnit 验收；上述原生 GUI 审计先于最后的历史存储补充执行，未重放这些新历史事件。所有最终生产源码都经过最后的全量测试和打包。

代码分两批本地提交，作者文档、外部 AI 示例与语言资源作为第三批；未推送远端：

| 代码批次 | Forge master | NeoForge 1.21.1 |
| --- | --- | --- |
| HUD 国际化、完整文本同步、历史快照 | `e0452801` | `4ffdf821` |
| 双语 Demo v4、旧 v3 工厂、原生语言审计 | `09365f72` | `d336a646` |
