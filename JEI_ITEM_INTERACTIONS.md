# 物品图标交互

2026-10-01；Minecraft 1.20.1 / Forge 47.4.20 / JEI 15.56.0.205。

## 玩家行为

- 独立物品图标默认左键查询配方、右键查询用途；响应 JEI 实际绑定，改绑后跟随设置。
- 普通/简易/章节商店的商品和每个成本物品可以分别悬停，只有当前图标平滑放大到约 1.1 倍。命中区保持稳定，不随悬停缩放移动。
- 商店图标 Tooltip 使用原商店自绘外观，只显示当前物品名称与数量，不显示 JEI 按键提示。条目其他区域仍显示原交易详情并保留购买操作。
- Tag 在真实候选间轮换，悬停暂停；查询当前显示物品，返回后保留候选。多奖励和复合项逐个显示，不把多个 AND 材料误当成一组 OR 查询。
- 材料较多时在原展示区用小箭头或滚轮翻页，避免缩成无法点击的小点或直接丢弃末尾成本。常见 2–4 成本直接显示。
- 标签候选为空时保留成本文字和数量，不生成虚假物品查询。动态报价沿用原商店的 `previewCount` 展示约定；实际扣款与 JEI 目录数量仍以服务端快照为准，不在客户端执行服务端数量回调。
- 抽奖成本放在按钮上方的独立展示带；按钮仍执行抽奖，成本图标仅查询。等待结果、滚动动画及领奖确认期间继续禁用查询。
- 未安装 JEI 时保留 ArcQ 物品展示、自绘 Tooltip 和原业务操作，不要求安装 JEI 才能打开界面。

## 覆盖范围

| 区域 | 图标查询行为 |
| --- | --- |
| Objective 标准行与并行阶段卡片 | 沿用已实现的当前候选查询与浅灰细边框 |
| 阶段奖励、章节奖励、通用物品奖励 | 独立图标双键查询；文字、拖动与领取区域保持原行为 |
| OFFER 提交弹窗 | 查询当前显示的物品/Tag 候选；提交按钮保持原行为 |
| 任务历史 | JEI 来源材料及有到达记录的普通历史奖励可以查询 |
| 指南入口、页面物品图标 | 仅作者明确声明且玩家已解锁的关联；装饰书本不产生虚假物品关联 |
| 普通/简易/章节商店 | 商品、每项成本及复合子项各自查询，支持候选轮换 |
| 抽奖预览 | 奖品图标和独立成本图标查询，抽奖按钮不被接管 |

装饰封面、水印、提示框内的小图、尚未公开的收集条目和抽奖过程动画不是材料入口。经验等非物品成本继续使用说明，不凭装饰图片创造 JEI 配方。

## 实现约束

`JeiScreenIngredients.recordIcon` 显式允许图标使用主鼠标键，并将当帧显示的候选与服务器授权材料匹配。保留权威材料的数量和 NBT，不把渲染用改名堆栈写回真实配方。文字/行区域仍使用原 `record`，不抢占业务左键。

`TradeIngredientSlots` 为绘制、Tooltip 和查询共用候选与布局；`TradeIngredientSlotLayout` 计算多材料排布和分页。可见区裁切、资源代次、目录刷新、前景屏幕及关闭/暂挂状态继续参与命中判定。JEI 存在但目录暂不可用或鼠标已改绑时，商店本地物品区域也不会退化为购买点击。

验收代码只在 `src/gameTest`，通过显式参数启用。所有客户端验收仅操作隔离世界 `ArcQ JEI Verification`；Objective 视觉回归使用 `ArcQ Objective Icon Verification`。

## 复现

Gradle 串行运行；使用 Java 21 启动 Gradle、Java 17 toolchain 编译和启动游戏。

```powershell
$env:JAVA_HOME = 'C:/Program Files/Zulu/zulu-21'
.\gradlew.bat test build jarJar --console=plain
.\gradlew.bat -PjeiRuntimeAudit runClient --console=plain
.\gradlew.bat -PjeiRuntimeAudit -PwithoutJei runClient --console=plain
.\gradlew.bat -PobjectiveIconRuntimeAudit runClient --console=plain
.\gradlew.bat -PobjectiveIconRuntimeAudit -PwithoutJei runClient --console=plain
```

每个客户端验收必须有对应的 `PASS` 且没有 `FAIL`；Gradle 任务会强制检查这一条件。无需额外证书开关，不使用离线构建规避网络问题。

## 本轮验收结果

以下均为 2026-10-01 最终源码的实际运行结果，不将构建成功代替游戏内断言。

| 验收 | 结果与日志 |
| --- | --- |
| 全量 Java 测试与发布构建 | **439 tests，0 failures/errors/skipped**；`test build jarJar` 成功；`build/jei-item-interactions-build-final.log` |
| 有 JEI 的原生图标查询 | **11 场景、40 次左右键查询 PASS**；`build/jei-item-interactions-runtime-final.log` |
| 无 JEI 的原生界面 | **PASS**；任务日志、普通/简易商店及抽奖预览实际渲染，商品与双成本槽位存在并可独立悬停；`build/jei-item-interactions-nojei.log` |
| Objective 有 JEI 回归 | **PASS，12 张真实截图**；Tag 候选、OFFER、并行 CRAFT、改绑及返回、关闭淡出、字号弹窗遮挡；`build/jei-items-objective-regression.log` |
| Objective 无 JEI 回归 | **PASS，11 张真实截图**；图标、自绘提示、轮换与动画继续运行；`build/jei-items-objective-nojei.log` |
| 窄区域与大量成本 | 单元回归覆盖 30×20、20×20、20×30、12×12 及 37 项成本，验证分页完整、导航/图标互不重叠；不冒充额外游戏内场景 |
| 发布内容检查 | 主 JAR、含依赖 JAR 与源码 JAR 均包含本轮代码，无 JEI 本体、JUnit、`src/gameTest` 验收类；`build/jei-item-artifact-audit.json` |

40 次真实查询覆盖 Objective、阶段/章节奖励、OFFER 提交弹窗、任务历史来源、指南、抽奖奖品与独立成本，以及普通/简易/章节上下文中的商品和两个不同成本。每次断言实际 JEI 唯一 focus 对应当前物品，左键为 OUTPUT、右键为 INPUT，退出 JEI 返回同一屏幕实例。另检查商店单图标缩放、列表裁切后不可命中，以及购买/抽奖业务区域的左键事件不被 JEI 消费。通用运行验收继续覆盖权限撤回、两次内容重载、抽奖等待/滚动/确认状态隔离，实际服务器库存与经验不变。

截图使用游戏真实帧缓冲保存，已逐张查看 `run/screenshots/jei-icons/with-jei/` 的 7 张及 `without-jei/` 的 3 张。所选成本显示自己的物品名称与数量，商品与两项成本没有共享悬停缩放，抽奖成本区不与按钮相交；截图仅作为视觉复查，行为通过依据是运行时断言。截图和日志保留在忽略的本地运行/构建目录，不打入发布包。

本轮发布包 `build/libs/arc_quest-forge1.20.1-1.0.8-all.jar` 的 SHA-256：

```text
0d0a41bc8537aac78b057173ba588d1bac4ab8820fd37480391ccdfca4ec51e8
```
