# 商店成本交互与渲染性能

2026-10-01，Minecraft 1.20.1 / Forge 47.4.20 / JEI 15.56.0.205。服务端多人任务评估见 `ARCQ_SERVER_PERFORMANCE.md`。

## 布局和交互修复

用户截图中的回退来自 `ff7e1148`：成本使用等宽网格铺满整条成本带，一个成本也会跑到中间，数量变成图标角标，原来的成本名称消失。本次对照 `28627ff4` 的 `TradeListPanel` 和 `TradeGridPanel` 恢复标题下面靠左、按内容自然宽度排列的成本行。

- 普通商店：原商品左侧位置、标题层级、购买按钮保持；成本基线为条目 `cy + 26`。
- 简易网格：保留原商品位置、标题和成本的两行结构，成本文字基线为 `textY + font.lineHeight + 4`。
- 成本显示小图标、完整数量和名称 / Tag 描述，以 `+` 紧凑连接；不足时分页，单项过长只截名称，优先保留数量。原区域内的小箭头或滚轮可访问其余成本。
- **整个成本带均不显示 Tooltip**，包括图标、名称、数量、加号、空隙和翻页箭头。移入时立即清掉上一商品的浮层，避免残留或回退成整条商品提示。
- 商品和其他业务区域继续使用原商店详情提示；成本图标保留轻微独立缩放、左键配方 / 右键用途、Tag 悬停暂停及当前候选查询。命中区不随缩放改变，不增加 JEI 快捷键文字。
- 无 JEI 时成本仍可阅读和分页，商品、购买流程保持可用。

核心实现：`client/hud/shop/TradeIngredientSlots.java`、`TradeIngredientSlotLayout.java`、`TradeTooltipRenderer.java`，路径相对于 `src/main/java/org/arcadia/arc_quest/`。

## 掉帧原因与无损优化

1. **旧网格渲染所有条目，包括屏幕外物品。** 即使只能看到十几到几十项，也遍历全部条目进行状态解析、背景/文字绘制和物品绘制。本次在当前动画坐标下，按背景、内容、最大悬停缩放和 8 像素余量裁切完全离屏项；保留飞入、回弹和关闭动画经过视口的条目。仍有轻量 O(N) 动画计算，昂贵绘制主要随可见数量增长。
2. **商店索引冷缓存是重复线性查找。** 旧 `ClientTradeCache.getGlobalIndex` 每遇到新条目从头扫描，仅缓存这一个 ID，连续查 N 项会做 O(N²) 工作；每次 authority 更新又清掉索引。本次按不可变商店定义一次 O(N) 建全表，之后 O(1) 查询；状态数值刷新保留索引，定义替换、临时 presentation、删除和显式失效均正确更新。
3. **材料状态和排版有重复工作。** 只处理当前/前一帧活跃状态，离屏候选暂停；成本布局缓存跟随内容、尺寸、字号和资源代次失效。已删除不用的物品专属 Tooltip 分支及悬停多余堆栈复制。
4. **不是稳定界面每个图标都做离屏 FBO。** `ObjectiveIconAlpha` 原有 >=254/255 的不透明快速路径会直接画原生物品。半透明过渡仍保留原透明合成；本次没有替换模型、附魔光效、动态纹理或自定义物品 renderer，也没有缓存成静态图片。新增计数仅在显式性能验收开关下启用。
5. 同时修复旧网格开场时间单位混用：归一化 `[0,1]` 被当成秒，约第 40 项后的条目无法完成飞入。现在统一换算秒数，600 项的首、中、末项均能结束开场。

**普通列表原本已有可见行裁切**，不能把网格结果套用到所有商店。列表的测试数据、父任务面板背景、复杂自定义物品和其他模组仍需分别看；本轮未证明用户所有场景的整机掉帧都由 ArcQ 导致。

## 测量方法和边界

- 本机 i7-12650H、约 16 GB RAM、RTX 4060 Laptop；游戏日志确认使用 NVIDIA OpenGL renderer。Gradle 用 Java 21，游戏/编译 toolchain 为 Java 17。
- 原生 Minecraft 客户端、原项目运行模组集合、隔离世界 `ArcQ JEI Verification`，不购买商品，不改玩家库存或经验。
- 相同 fixtures：12 / 120 / 600 条目，商品为铁剑，成本为 5 绿宝石 + 2 钻石。普通列表 / 简易网格，真实 framebuffer 854×480 / 1920×1080，GUI scale 2。临时移除窗口装饰以避免 Windows 把 1080 高度裁成 1055；成功或失败均恢复原窗口。
- 每场景至少预热 2 秒和 20 帧，采样 120 帧；上限 8 秒且至少 5 帧。测量原生 `Screen.render` 的调用耗时与 render thread 分配量，记录 mean/p50/p95/max。截图和 JEI 交互不计入采样。
- 旧版存在开场 bug，因此前后均由 probe 强制完成网格动画，仅比较稳态。**不测开场/关闭动画性能，不把 CPU 调用耗时当 GPU 时间或全游戏 FPS。** 原生渲染调用可能包含驱动等待。
- Before 为 `7c046480` 已构建的生产 classes，运行新 audit 时明确 `-x compileJava -x processResources`；After 为当前生产源码。旧 classes 在首次编译前保存于 `build/arcq-performance/main-before-7c046480/`。
- 本次前后也包含用户要求的布局修复。窄网格恢复名称后会分页，屏幕内每项材料槽位数由 3 变为 2；它是完整修复前后对比，**不能把全部耗时差单独归因于裁切**。普通列表的双成本仍同时显示。
- `visibleEntries/visibleItemSlots` 是裁切后可交互范围；带安全余量的实际绘制调用可能略多。`guiFlushesPerFrame` 只计透明合成层里的两处显式 flush，不是 Minecraft 全部 flush。Before 没有计数 API，明确记为 unavailable。

## 最终客户端测量（120 帧 / 场景）

下表为完整修复前后的单次有限运行。毫秒为 Screen.render 调用耗时，KiB 为该调用内线程分配；不是整机 FPS、GPU 计时或常驻内存。场景执行顺序和 JIT/GC 会影响小差异，不用 12 与 120 的微小波动反推算法复杂度。

| 分辨率 | 布局 / 总条目 | 可见条目 | 可见槽位（前 → 后） | mean ms（前 → 后） | p95 ms（前 → 后） | KiB/帧（前 → 后） |
| --- | --- | ---: | --- | --- | --- | --- |
| 854x480 | list / 12 | 3 | 7 → 7 | 1.089 → 1.119 | 1.535 → 1.595 | 91.313 → 112.206 |
| 854x480 | list / 120 | 3 | 7 → 7 | 1.166 → 0.876 | 1.755 → 1.178 | 86.895 → 91.342 |
| 854x480 | list / 600 | 3 | 7 → 7 | 0.852 → 0.711 | 1.239 → 0.962 | 86.606 → 91.191 |
| 854x480 | grid / 12 | 12 | 36 → 24 | 1.308 → 1.022 | 1.756 → 1.268 | 218.188 → 197.668 |
| 854x480 | grid / 120 | 18 | 54 → 36 | 9.601 → 1.419 | 12.633 → 1.740 | 1919.295 → 292.984 |
| 854x480 | grid / 600 | 18 | 54 → 36 | 48.024 → 1.473 | 56.137 → 1.748 | 9426.773 → 292.984 |
| 1920x1080 | list / 12 | 7 | 21 → 21 | 1.178 → 1.061 | 1.641 → 1.229 | 157.695 → 166.070 |
| 1920x1080 | list / 120 | 7 | 21 → 21 | 1.012 → 1.100 | 1.230 → 1.387 | 157.773 → 166.292 |
| 1920x1080 | list / 600 | 7 | 21 → 21 | 1.023 → 1.050 | 1.181 → 1.210 | 157.773 → 166.289 |
| 1920x1080 | grid / 12 | 12 | 36 → 24 | 1.101 → 0.973 | 1.484 → 1.202 | 214.766 → 194.195 |
| 1920x1080 | grid / 120 | 60 | 180 → 120 | 9.525 → 5.848 | 10.309 → 9.491 | 1988.282 → 1119.438 |
| 1920x1080 | grid / 600 | 60 | 180 → 120 | 46.753 → 5.035 | 58.422 → 5.398 | 9525.781 → 1132.938 |

600 条网格的 mean 在 1080p 从 46.753 ms 降至 5.035 ms，约减少 89.2%；同场景分配从约 9.30 MiB/帧降至 1.11 MiB/帧。普通列表 1080p / 600 条为 1.023 → 1.050 ms，未测到明显提升，恢复成本文字后的分配略增；不能宣称所有商店都获得同倍数加速。

最终所有稳态场景的 `offscreenPasses`、透明合成显式 `guiFlushes` 和 `targetAllocations` 均为 0；常态物品仍走原生不透明渲染。1080p 大网格有 60 项命中区、120 个当前页物品槽，但安全余量保留邻接项，实际物品调用为 144 次/帧。

600 条是溢出视口的压力样本。简易网格沿用原有居中排列，本次没有新增整店滚动；它不代表 600 条能同时被阅读或操作。普通列表保持自己的滚动和可见行裁切。

Before 完整运行日志为 `build/shop-performance-before-final.log`，After 最终构建、448 项单测与客户端日志为 `build/shop-performance-after-final.log`。两次 12 场景均完成；After 的成本提示违规数为 0，8 次真实成本左右键查询通过，返回原屏幕且库存/经验未变。前一版未缓存排版的中间结果另存 `after-inline-uncached.csv`，不混入最终表格。

## 复现与结果文件

```powershell
$env:JAVA_HOME = 'C:/Program Files/Zulu/zulu-21'
./gradlew.bat test build jarJar --console=plain
./gradlew.bat -PshopPerformanceAudit -PshopPerformanceLabel=after runClient --console=plain
./gradlew.bat -PjeiServerAudit runGameTestServer --console=plain
./gradlew.bat -PjeiRuntimeAudit runClient --console=plain
./gradlew.bat -PjeiRuntimeAudit -PwithoutJei runClient --console=plain
```

Before 只能在确实仍使用旧 classes 时运行，不能在新源码已经编译后仅改 label 假装基线。客户端 audit 的 Gradle 后置检查强制要求 PASS 且不存在 FAIL。服务器另外检查 `All 11 required tests passed`，避免 Forge 启动失败却返回零状态造成误判。

原始 CSV：`run/reports/shop-performance/before.csv`、`after.csv`。真实截图：`run/screenshots/shop-performance/{before,after}/`。第一轮带窗口装饰的试跑未完成 1080p，因此不纳入最终对比。验收、fixtures 和计时 probe 在 `src/gameTest`，不打进发布 JAR。

## 最终验收

| 项目 | 实际结果 | 日志 |
| --- | --- | --- |
| 单元测试和发布构建 | 448 tests；0 failures / errors / skipped；`test build jarJar` 成功 | `build/shop-performance-after-final.log` |
| 前后原生性能场景 | 各 12 场景完成；After 零成本 Tooltip 违规、8 次真实左右查询、库存/经验不变 | `build/shop-performance-before-final.log`、`build/shop-performance-after-final.log` |
| 完整有 JEI 交互 | 11 场景、40 次真实查询；普通/简易/章节商店、第二成本分页查询、返回第一页、裁切与业务点击隔离；两次内容重载 | `build/shop-interactions-jei-verified.log` |
| 无 JEI 原生界面 | PASS；list/grid 两项成本逐个悬停，窄 grid 真实箭头翻页并返回首页；任务日志和抽奖同样通过，库存/经验不变 | `build/shop-interactions-nojei-final.log` |
| 服务端真实实现路径 | All 11 required tests passed，包含两档目标数量刷新和任务 JEI 目录测试；不是多人网络压测 | `build/shop-performance-server-final.log` |
| 发布包检查 | 主 JAR、含依赖 JAR、源码 JAR 均未混入 JEI 本体、JUnit、性能 fixtures / 客户端 probe / GameTest 类 | `build/shop-performance-artifact-audit.json` |

交互回归使用原屏幕真实分页箭头；只读等待实际入场动画结束后获取目标坐标，没有强制推进动画。初次旧 harness 只等 8 帧、在移动中取点导致失败，修正验收时序后的完整运行才计为通过。性能 probe 强制稳态的设计仅用于上述稳态基准，不用于这套交互回归。

最终含依赖发布包 `build/libs/arc_quest-forge1.20.1-1.0.8-all.jar` 的 SHA-256：

```text
997ff4742e94cb6ed040422047dd330ded543460b4030eb2904cd093a78a079e
```
