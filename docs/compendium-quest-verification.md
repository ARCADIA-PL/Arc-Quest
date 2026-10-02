# 图鉴模式 Quest 实现与验收

日期：2026-10-02

## 已实现的闭环

`QuestMode.COLLECTION` 保留正常的任务接取、真实 Phase、并行阶段、选择、手动推进、失败、放弃和奖励流程。图鉴使用三个独立层次：共享 Entry 定义、玩家永久发现/研究记录、具体 Phase 的 Binding 要求与本轮 Objective。

永久记录在没有接取任务时也可增长。登录背包只建立 COLLECT 发现事实，不制造拾取、制作或研究事件。重复接取创建新的 Run ID 和行动计数，放弃与失败不会清空长期知识。ALL/QUOTA、ALL/ANY 要求、可选要求、按不同 Entry 计数、接取时冻结候选和发现基线使用同一投影逻辑。

任务面板内嵌目录与详情，支持分类、搜索、收纳、窄屏二级详情、Guide 式比例图片和模态放大。二维生物头像和物品图标复用 Objective 图标提供器，Tag 在悬停期间固定当前候选。物品 Tooltip 复用奖励栏渲染，合法物品支持 JEI 左键配方、右键用途。点击浏览不改变追踪；条目聚焦与追踪器外观独立，完成反馈约 1.2 秒后回到总览，等待回报提示约 3.5 秒后退场。

玩家存档包含永久记录与最后一次终止任务的图鉴档案。已完成或失败的任务结果读取本轮锁存事实，后续新知识只更新资料，不回填历史完成项。手动里程碑奖励在归档后仍可领取。旧版存档按可证明的主体/研究对应关系保守迁移，旧领取凭证限定在原任务内；无法映射的旧内容保留。

内容同步按玩家授权投影：未知任务、隐藏身份和锁定图片不通过原始 JSON 广播。服务端定义与客户端展示定义分开。记录增量与任务状态共用有序 revision；普通计数和已读改变不会重新压缩全部定义。网络协议升级到 18，客户端和服务端应使用相同版本。

## 自动与原生验证

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
