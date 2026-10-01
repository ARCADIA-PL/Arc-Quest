# 1.21.1 分支对齐记录

2026-10-02，目标分支 `1.21.1`，原起点 `7511ef7a`，业务源 `master@c8d9b715`。
以 `2427b309` 为业务增量基线，并补回此前两个平台分支之间的业务差异。

## 平台与依赖

- Minecraft 1.21.1、Java 21、ModDevGradle 2.0.74、Gradle Wrapper 8.11.1。
- NeoForge 21.1.251；发布包最低要求 21.1.238。
- JEI **19.51.0.418 正式版**，使用 common-api / neoforge-api，客户端可选范围 `[19.51.0.418,20)`。范围内的其他版本不等于全部实测。
- 保留 Ponder / Flywheel 的 NeoForge 制品与嵌套依赖，保留拼音搜索及追踪菜单 Tab 修复。

JEI 选择依据：

- [作者正式发布 ufHUqt9b](https://modrinth.com/mod/jei/version/ufHUqt9b) 对应 19.51.0.418，release，Minecraft 1.21 / 1.21.1，NeoForge，发布时间 2026-09-02。
- [发布时的 GitHub 提交](https://github.com/mezz/JustEnoughItems/commit/19032e46fa8bc09b7c9408c540996052115de746) 与 [1.21.1 源码分支](https://github.com/mezz/JustEnoughItems/tree/1.21.1)。
- [作者 Maven 制品](https://maven.blamejared.com/mezz/jei/jei-1.21.1-neoforge/19.51.0.418/jei-1.21.1-neoforge-19.51.0.418.jar) 的 SHA-512 与作者发布文件一致；实际元数据要求 NeoForge 21.1.238 以上。
- [CurseForge 筛选页](https://www.curseforge.com/minecraft/mc-mods/jei/files/all?version=1.21.1&gameVersionTypeId=6&pageSize=20) 在本环境返回 HTTP 403，未将其记为直接核验通过。
- Maven 最新的 19.57.0.450 为 Beta，先用于扩展兼容性验收；最终固定上述正式版。

## 同步与版本适配

同步 JEI 分类与权限目录、所有已有物品查询入口、Objective ICON、Tag 轮换、二维头像、商店布局/成本交互/轮廓描边、渲染性能改进、字号面板遮挡、关闭动画、追踪器布局编辑器及 CLASSIC / FOCUS / OVERVIEW、左侧统一通知、数据/任务/对话/交易/抽奖重构、编辑器校验与语言资源。

网络使用 NeoForge payload / StreamCodec / IPayloadContext，协议版本 17，专服保持客户端隔离。JEI 目录与对话冻结文本携带真实注册表上下文，物品完整保留 components，包括名称、描述、损耗、附魔和头颅 PROFILE；物品匹配与 ICON 缓存同样使用完整组件。

玩家持久化保留 NeoForge attachment。抽卡回执位于 `neoforge:attachments/arc_quest:player_data/DeliveredDrawReceipts`，独立于 Snapshot；保存、清空或恢复进度不会伪造或覆盖背包交付回执，仅有回执的附件也会落盘，死亡克隆复制回执。

额外修复默认注册表的缺失 ID 校验、原版 predicate 的 RegistryOps 上下文、NPC 扩展与商店刷新监听器的 MOD 总线、512 格默认标记范围、任务放弃字段别名、有界编辑器扫描、WebSocket 生命周期。

新追踪器外观概念稿尚未进入 master，实现范围沿用上述三个已落地样式。

## 验证与复现

全量单元测试 572 项、0 失败/错误/跳过；Web 编辑器 50 项通过，Vite 单文件构建成功。原生验收记录以 `build/alignment-*.log` 的 PASS 标记与服务端 required tests 计数为准，不以进程正常退出替代业务验收。

最终验收结果：

| 验证 | 结果 | 本地证据 |
| --- | --- | --- |
| 正式 JEI 编译、测试与发布构建 | 572 项测试通过；`build` / `sourcesJar` 成功 | `build/alignment-release-build.log`、`build/alignment-final-build.log`、`build/test-results/test/` |
| Web 编辑器 | 50 项测试通过；Vite 单文件构建成功 | `build/alignment-web-tests.log` |
| 正式 JEI 原生客户端 | 5 个公开分类、GUI scale 1/2、40 次实际查询、导航、权限撤销、两轮 reload、抽奖交互通过 | `build/alignment-jei-release-runtime.log` |
| 正式 JEI Objective ICON | 12 张截图、19 次查询及 8 次空目录查询；单/并行、Tag、二维头像、显式 iconItem、关闭淡出、设置面板遮挡通过 | `build/alignment-icons-release-runtime.log` |
| 无 JEI 原生客户端 | 任务、列表/网格商店、抽奖正常；成本 tooltip 不出现；无 JEI 查询及交易副作用 | `build/alignment-client-no-jei.log` |
| 无 JEI Objective ICON | 11 张截图；渲染与交互正常，实际 JEI 查询为 0 | `build/alignment-icons-no-jei.log` |
| 正式 JEI 专服 | 12 项 required GameTest 全部通过 | `build/alignment-server-release-jei.log` |
| 无 JEI 专服 | 12 项 required GameTest 全部通过 | `build/alignment-server-no-jei.log` |
| 正式 JEI 商店交互与性能 | 12 场景、8 次实际查询、0 项 UX 违例；商品/成本细白轮廓像素验证通过 | `build/alignment-shop-release-runtime.log`、`run/reports/shop-performance/neoforge-release.csv` |
| 左侧通知原生 HUD | 14 张截图、实际 GUI scales 1/2/3/4；右上角为空、确认过期、阶段加入禁用、淡出及单次渲染通过 | `build/alignment-toast-runtime-2.log` |
| 追踪器原生 HUD 与编辑器 | 6 张截图；8 个 layer ID、顺序与单例、3 种样式、拖动/缩放/保存/取消/恢复/重置通过 | `build/alignment-tracker-runtime.log` |
| 语言数据生成 | 中英数据生成成功，内容与源码一致 | `build/alignment-data.log` |
| 最终发布包审计 | 正式 JEI 范围正确；包含 Ponder / Flywheel、图标 shaders 和中英语言；主包及源码包均不含开发验收类 | `build/libs/` |

通知与追踪器的原生 HUD 验收在初选 JEI 19.57.0.450 Beta 环境完成；之后仅更改 JEI 依赖固定版本，没有修改 HUD 实现。表内明确注明“正式 JEI”的客户端、ICON、商店及专服验收均已在最终 19.51.0.418 环境重跑。

本机商店测量只统计 CPU 提交时间，不能直接当作完整帧耗时或多人服务器容量结论。1920×1080 下，120 与 600 总条目的网格均有 60 个可见条目，CPU 均值分别为 6.44 / 5.97 ms，P95 为 8.08 / 7.05 ms，分配约 2 MB/帧；列表均值约 1.16 ms。成本主要随可见内容增长，密集网格仍有优化空间。未悬停时所有 12 场景每帧离屏描边、GUI flush 和渲染目标分配计数均为 0；悬停描边只对当前物品执行。

```powershell
.\gradlew.bat test build compileGameTestJava
.\gradlew.bat runGameTestServer -PjeiServerAudit
.\gradlew.bat runGameTestServer -PjeiServerAudit -PwithoutJei
.\gradlew.bat runClient -PquestToastRuntimeAudit
.\gradlew.bat runClient -PtrackerLayoutRuntimeAudit
.\gradlew.bat runClient -PobjectiveIconRuntimeAudit
.\gradlew.bat runClient -PobjectiveIconRuntimeAudit -PwithoutJei
.\gradlew.bat runClient -PjeiRuntimeAudit
.\gradlew.bat runClient -PjeiRuntimeAudit -PwithoutJei
.\gradlew.bat runClient -PshopPerformanceAudit
.\gradlew.bat runData
.\gradlew.bat build sourcesJar
```

本机既有 Java 代理访问 NeoForge Maven 间歇 TLS 失败时，本轮命令临时附加 `-Dhttp.nonProxyHosts=maven.neoforged.net`，直接访问该主机并保留证书校验；没有关闭证书校验或修改全局代理配置。

世界内客户端验收要求两个一次性存档目录：`run/saves/ArcQ Objective Icon Verification` 与 `run/saves/ArcQ JEI Verification`。本轮来自成功运行的 1.21.1 GameTest 世界副本，未使用原工作区的玩家存档。通知与追踪器原生验收只在标题界面运行，不打开存档。

发布包 `build/libs/arc_quest-neoforge1.21.1-1.0.8.jar` 嵌套 Ponder / Flywheel，不包含 JEI 本体、JUnit、GameTest 或原生审计类。JEI 需要单独安装。

最终主包 SHA-256：`d55a1c2617053210f660cff94398b8bc05e9d57b8d2cd8e495f343834e66f9f8`。

本轮分三批本地提交：编辑器、NeoForge 主体移植、正式版依赖与原生验收/说明。未推送远程。原 `D:/Arc Quest-master` 的 master 工作区及玩家存档保持原状；成品、验收日志和性能 CSV 导出到其忽略目录 `build/neoforge-1.21.1/`，便于取用。
