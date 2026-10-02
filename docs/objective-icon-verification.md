# Objective ICON 实现与验收记录

使用手册见 [OBJECTIVE_ICONS.md](../OBJECTIVE_ICONS.md)，原始设计见 [objective-icon-design.md](objective-icon-design.md)，目标匹配与数量兼容性见 [objective-count-semantics.md](objective-count-semantics.md)。

## 2026-10-01：显式图标独立 JEI 查询

有效 `iconItem(...)` 现在同时是独立 JEI 查询入口，`iconTexture(ItemLike)` 和 JSON ITEM 模式同样适用。图标查询当前配置物品，与 Objective 是否有物品目标、实际目标是什么、ArcQ 材料目录是否为空无关。普通目标行、进度、提交与任务材料目录保持原来的真实目标语义。AUTO、TEXTURE、PROVIDER 沿用已有查询规则。隐藏、裁剪、关闭/模态交互限制、资源代次和当前目标身份检查继续生效。

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| Java 回归及发布构建 | 480 tests，0 failures/errors/skipped；`test build jarJar` 成功 | `build/explicit-icon-jei-verified.log`、`build/test-results/test/TEST-*.xml` |
| 原生客户端，有 JEI | PASS；保留原 11 次，新增 8 次，总共 19 次真实查询/同面板返回，12 张截图 | `build/explicit-icon-jei-verified.log` |
| 原生客户端，无 JEI | PASS；11 张截图，显式物品图标与原有显示/交互回归通过，构建正常退出 | `build/explicit-icon-jei-absent-verified.log` |
| 显式图标独立查询 | KILL 物品别名、CUSTOM 方块图标、COLLECT 钻石配工作台图标、并行 KILL 图标均通过左右键查询 | `build/explicit-icon-jei-verified.log`：`actualQueries=19 emptyCatalogQueries=8` |

新增八次查询在客户端渲染线程同步作用域内，临时将材料目录两处数据引用替换为空；实际生产命中表和 JEI 输入必须仍能查询精确物品及 OUTPUT/INPUT 方向。普通行同时必须拒绝材料查询并放行左键。作用域正常和异常退出均恢复原引用，权限开关、目录 revision 和监听器不变。COLLECT 的普通行仍为钻石，KILL/CUSTOM 行没有被添加虚构需求。

验证基于当前工作区，包括会话开始已有的六个图标/头像/示例文件修改；本批没有修改或暂存这些文件，已逐一核对 SHA-256 保持不变。最初两次普通构建遇到 MCPRepo 元数据 TLS EOF，导致 Minecraft 传递依赖缺失。成功验证仅在该进程的 `http.nonProxyHosts` 原列表追加 `piston-meta.mojang.com` 和 `launchermeta.mojang.com`，未修改项目/用户代理、证书设置，未使用 offline。Windows 下直接用 Zulu 21 调用 `gradle/wrapper/gradle-wrapper.jar` 的 `org.gradle.wrapper.GradleWrapperMain`，避免批处理将参数中的竖线解释为管道。

## 2026-10-01：显式原生物品图标（初版记录）

以下保留初版验收；其中装饰物品的查询限制已由上面的独立查询规则替代。

新增 `iconTexture(ItemLike)`、`iconItem(ItemLike/String/ResourceLocation)` 和 `ObjectiveIcons.item(...)`。Item/Block 使用默认物品栏模型；声明式 ITEM 配置只保存物品注册 ID。旧纹理方法及 ObjectiveIconSpec 四参数构造器保留，目标检测和 JEI 授权材料语义保持独立。网页编辑器同步支持 ITEM 模式，使用说明见根目录手册。

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| Java 回归及发布构建 | 476 tests，0 failures/errors/skipped；`test build jarJar` 成功 | `build/objective-icon-item-api-final.log`、`build/test-results/test/TEST-*.xml` |
| 网页编辑器 | 图标定向 17/17、完整测试 50/50；Vite 构建成功 | `node --test test/objective-icon.test.js`、`node --test`、`build/objective-icon-item-editor-build.log` |
| 原生客户端，有 JEI | PASS，12 张截图、11 次真实查询/返回；显式物品 CRAFT 保留左右键与改绑行为；扩展后的验收画廊已调整列数避免标签覆盖图标 | `build/objective-icon-item-api-final.log`、`build/objective-icon-item-gallery-final.log` |
| 原生客户端，无 JEI | PASS，11 张截图；物品别名、方块模型、无效 ID 无回退 | `build/objective-icon-item-without-jei.log` |

两种客户端配置均有 `ITEM_OVERRIDE_PASS`。实际画廊验证 `iconTexture(Items.ROTTEN_FLESH)` 覆盖 KILL 头像、`iconItem(Blocks.CHEST)` 使用箱子的原生物品栏模型、未知物品 ID 不回退为任务目标。Java 回归覆盖标准 JSON 往返、服务端/客户端定义编译、旧构造器兼容、隐藏目标、AIR、字段互斥、物品堆栈隔离和真实目标不变；没有用装饰物品添加虚假 JEI 材料。原有透明度、关闭动画、字号弹窗遮挡与资源重载验收继续通过。

本批发布构建的 `build/libs/arc_quest-forge1.20.1-1.0.8-all.jar` SHA-256：`E82D6BA17CE5713DC265295DF9029072534A81B6E4843A9CCEC077FC47A73D70`。以下保留此前图标系统验收，旧测试数量不代表本批结果。

## 既有图标系统验收（历史）

| 检查 | 最终结果 | 证据 |
| --- | --- | --- |
| Java | 427/427通过，0失败/错误/跳过 | `build/test-results/test/TEST-*.xml` |
| Web | 此前基线45/45通过，Vite构建成功；本次未改Web | 编辑器 `npm test` / `npm run build` |
| 独立服务器、无JEI | 此前基线9/9通过；本次修改限于客户端 | `build/objective-icons-server-audit.log` |
| 最终客户端、有JEI | PASS，12张截图及像素断言，11次真实JEI查询与返回 | `build/objective-icons-jei-mouse-runtime.log` |
| 最终客户端、无JEI | PASS，11张截图及像素断言，包含OFFER/DELIVER默认图标 | `build/objective-icons-jei-mouse-without-jei.log` |
| 发布构建 | `test build jarJar` 成功，39秒；有/无JEI客户端分别1分2秒/48秒通过 | `build/objective-icons-jei-mouse-build.log` 及上述客户端日志，均无临时`-D`或`--offline`参数 |

最终两次客户端均包含OFFER/DELIVER默认物品图标、OFFER Tag多候选、奖励栏Tooltip复用、字号弹窗深度隔离、图标整体透明度、软边纹理、嵌套绘制与附魔缓冲修复。有JEI运行同时验证真实查询与返回；两组策略截图均显示OFFER绿宝石、DELIVER钻石以及轮换到第2/40个候选的OFFER Tag。已复查原生日志单阶段和并行卡片的浅灰细边框。检查普通JAR和all JAR：图标类、牛猪资源、两个专用shader配置均存在，客户端审计及GameTest类未打包。

本次鼠标修复新增11次真实查询：单阶段Tag用途，CRAFT默认左键配方/右键用途、鼠标改绑与键盘改绑，OFFER默认左/右键，以及并行CRAFT默认左/右键。每次断言JEI实际focus的OUTPUT/INPUT方向、当前候选、原Journal父界面及渲染后原路返回。业务行旁的左键Pre事件保持未消费，包含OFFER；这项检查不执行提交、不消耗物品。临时键位在finally中完整恢复，不保存用户按键配置。

发布产物位于 `build/libs/arc_quest-forge1.20.1-1.0.8-all.jar`，另有普通JAR和sources JAR。Web独立页面位于 `arc_quest_editor_modular/dist/index.html`。

## 环境与可复现入口

- Minecraft 1.20.1、Forge 47.4.20、JEI 15.56.0.205，来自仓库固定依赖。
- Windows / PowerShell、Gradle 8.8。构建启动器为 Zulu 21，编译和游戏运行使用项目指定的 JDK 17 toolchain。
- 客户端包含项目原有渲染模组，窗口854×480，实际GUI scale 1 / 2；此尺寸检查窄卡片、文字滚动及提示边界。
- 客户端验收只操作隔离存档 **ArcQ Objective Icon Verification**；入口核对精确目录名，使用真实注册任务、服务器接受/并行自动进入、真实同步和任务日志。
- 验收入口均为opt-in，`src/gameTest` 不进入生产JAR。

```powershell
./gradlew.bat test build jarJar
./gradlew.bat -PobjectiveIconRuntimeAudit runClient
./gradlew.bat -PobjectiveIconRuntimeAudit -PwithoutJei runClient
./gradlew.bat -PwithoutJei -PjeiServerAudit runGameTestServer

Set-Location arc_quest_editor_modular
npm ci --no-audit --no-fund
npm test
npm run build
```

首次运行客户端验收前，需要创建上述精确命名的隔离世界。本次从先前的专用JEI验收存档复制得到，没有使用或修改其他用户世界。不要同时设置 `objectiveIconRuntimeAudit` 和旧的 `jeiRuntimeAudit`，它们有不同的隔离世界。

早期验收曾临时使用 `--offline -Dnet.minecraftforge.gradle.check.certs=false`。后续已把仅跳过 ForgeGradle 附加站点预检的属性写入项目 `gradle.properties`，普通构建无需再手动附加该参数；真正HTTPS下载仍保留证书链与主机名验证，没有修改系统信任库或用户代理。本次最终构建和两轮客户端均未传临时`-D`、未使用`--offline`，对应日志未出现证书预检失败或SSL握手异常。具体诊断、边界及重新启用方法见 [网络预检说明](../gradle/NETWORK.md)。

## 自动验证覆盖

| 层级 | 覆盖 |
| --- | --- |
| Java单元测试 | 配置/JSON往返、旧构造与复制、编辑器历史、候选匹配、Tag索引、有效数量与网络/NBT、帧选择、轮播暂停、行几何、头像UV/缓存/资源校验、JEI候选权限与NBT语义 |
| Web编辑器 | 图标表单、嵌套字段、导入导出、复制、未知配置保留、错误路径及既有编辑器回归 |
| 真实独立服务器 | 9项GameTest，其中新增4项覆盖加载后的Tag索引、有效数量/JEI/网络一致、collection计数模式、提交匹配和只读预览 |
| 客户端有JEI | 原生单阶段与三并行阶段、候选轮换/焦点暂停、自绘Tooltip一致、当前候选命中；11次默认左右键/改绑鼠标及键盘查询、实际focus方向、业务行左键透传、返回原Journal |
| 客户端无JEI | 同一原生图标、阶段布局、轮播、Tooltip及资源重载流程；检查JEI目录未启用 |
| 二维头像 | 六种静态正面头颅、牛角/脸和猪脸/鼻分层UV；真实资源重载后旧handle失效并重绘全部8项 |
| 图标透明度 | 普通物品、3D物品、附魔物品、图片、多层牛头像、头颅、半透明RGBA条带、嵌套provider；比较1/.5/.05/0/恢复1的真实像素，验证软边和内外透明度相乘 |
| 原生关闭动画 | 真实接受的最小任务、原生目标行与onClose，保持中间帧后检测图标像素及精简Tooltip的淡出保留 |
| 字号弹窗 | 原生日志作为预览，在正文、滑条、按钮区域注入高Z父级文字与物品；真实弹窗截图与无干扰对照逐像素比较 |
| 显示策略 | COLLECT、CRAFT、OFFER、DELIVER、多候选Tag（含OFFER）、显式纹理、NONE、资源缺失、隐藏目标、未知provider；无图标不保留图标列 |
| 只读性 | 查看、JEI查询和重载前后玩家库存与经验一致 |

客户端必须出现 `[ARCQ_OBJECTIVE_ICON_AUDIT] PASS`，不能仅凭Minecraft正常退出判断成功。Gradle额外检查 `run/logs/latest.log`，缺少PASS或出现FAIL都会令任务失败。客户端自身有300秒超时并正常关闭。

## 运行产物与截图

完整日志在 `build/objective-icons-*.log`，单元测试报告在 `build/reports/tests/test/index.html`。真实帧缓冲截图保存在：

```text
run/screenshots/objective-icons/with-jei/
run/screenshots/objective-icons/without-jei/
```

每组包含 `single-scale1-focused-tooltip.png`、`parallel-scale2.png`、`portraits-scale2.png`、`policies-scale2-tag-rotated.png`、`portraits-after-resource-reload.png`；有JEI时另有 `single-scale1-jei-return.png`。新增 `alpha-group-opacity.png`、`native-journal-alpha1.png`、`native-journal-closing-half.png`、`journal-high-z-control.png`、`modal-clean.png`、`modal-high-z.png`。这些文件在忽略的运行目录中，不打入模组包。

视觉复查确认固定正面、牛猪UV及重载后画面一致；根据实测补了长Tooltip换行和屏幕边界限制，以及猪灵耳尖取景范围。并行卡片采用紧凑两行布局，保持原有卡片滚动、拖动和提交入口。

## 本次界面修正

- 修复JEI监听层统一忽略左键的问题：独立Objective图标的命中区域显式允许左键查询，默认左键配方、右键用途，且遵循JEI当前绑定。目标文字/进度区域及其他业务控件仍保留原有左键行为，避免把提交、购买或拖动变成查询。
- OFFER与DELIVER在AUTO策略下直接显示目标物品，无需作者额外配置；复用COLLECT/CRAFT的真实候选解析、Tag轮播、Tooltip和JEI命中。显式NONE、图片和自定义provider继续优先于默认图标。
- Objective Tooltip 使用与奖励物品相同的 `renderTooltipLayout`，包括背景、侧边、物品小图、定位及动画；保留名称和必要的Tag一行，不显示JEI快捷键、候选序号或重复目标说明。此前新增的固定锚点小卡片与图标底色已删除；按后续样式要求，图标保留与进度条底轨相同透明度的1像素浅灰框，悬停时仅边框平滑提亮为主题色。鼠标移出或开始关闭时保留最后请求，直到淡出完成。
- `ArcQuestTextConfigScreen` 在父预览结束后提交缓冲、隔离深度、分批提交遮罩与控件；Journal、Dialogue、Trade、GuideList、Guide共用此修复。两种环境中，正文/滑条/按钮三个像素检查均为 `leaked=0`，干扰差异最大为2/255，符合面板原有轻微透明度。
- 图标整体淡出使用独立混合shader，普通物品与附魔层分开缓冲；多层头像、半透明图片和嵌套provider透明度均保留。原生日志关闭中间帧的图标亮度约为完整帧的48.4%；Tag Tooltip严格两行，普通物品Tooltip严格一行。资源重载后继续验证图标、shader与缓存重建。

详细回归入口、像素对照方法与边界见 [客户端回归说明](../src/gameTest/OBJECTIVE_ICON_AUDIT.md)。实际截图已人工复查；测试不修改字号配置，只操作指定隔离世界。

## 2026-10-01 扩展回归

2026-10-01 扩展其他界面的物品图标交互后，再次运行 Objective 有/无 JEI 回归，分别 **PASS（12/11 张截图）**。日志为 `build/jei-items-objective-regression.log` 与 `build/jei-items-objective-nojei.log`。两种环境的字号弹窗 body/slider/buttons 检查均为 `leaked=0`；有 JEI 时默认左右键、改绑、单阶段 OFFER、并行 CRAFT 和同实例返回继续通过。本轮商店/奖励等新增交互另有 [独立验收记录](../JEI_ITEM_INTERACTIONS.md)，不混入 Objective 的截图和查询计数。

## 明确范围

- 内置头像为骷髅、凋灵骷髅、僵尸、苦力怕、猪灵、末影龙、牛和猪；其他实体由作者注册头像或提供资源包规则后显示，否则无图。
- CRAFT延续原有 `PlayerEvent.ItemCraftedEvent`，不自动识别任意模组机器的生产事件。
- 第三方资源包若重排实体UV，需要提供对应规则；本次检查项目当前加载的纹理及真实资源重载。
- 历史界面使用保留的有效数量快照；无快照时明确回退定义值，不重建已经丢失的动态历史数据。
- 协议升级为16，联机双方需要使用匹配的ArcQ版本。

## 2026-10-03：全部原版 Mob 与追踪器头像

Forge 1.20.1 完整覆盖 79 种 Mob：6 种头颅来源 + 73 份纹理规则；NeoForge 1.21.1 覆盖 82 种：6 + 76，额外包含犰狳、沼骸和旋风人。巨人、幻术师、僵尸马等指令生物计入覆盖。玩家动态皮肤、盔甲架和技术实体不属于 Mob 范围。72 份共同纹理规则一致；蝙蝠按两版本实际不同的模型/纹理分别适配。

有原版头颅的六种继续从固定正面的头颅几何生成静态二维图像；其余按各自原始纹理的头部 UV 组合。马、驴、骡、骆驼、鱼与海豚采用可见眼睛的头部侧脸；鹦鹉及新版蝙蝠组合真实眼颊，凋灵只取主头，女巫缩小无效留白。适配规则中的 sourceModel/appearanceNote 注解记录代表外观和来源。适配以 EntityType 的稳定代表外观为单位，不创建生物、不绘制全身模型、不用刷怪蛋或物品模型充数。

追踪器登录恢复缺图的原因是头颅请求只进入待生成队列，原先仅 Journal 处理队列。现在正式追踪器与布局预览在 pose/scissor 之前消费队列。颜色修复包含两层：二维头像隔离调用方 shader tint；头颅生成使用专用 `objective_head_portrait` 无光照 shader。原版 emissive entity vertex shader 仍有 minecraft_mix_light，旧原生截图的脸部 RGB 被压至约 0.616 倍，FULL_BRIGHT 并不能消除方向光。新 shader 保留原色、深度和正确透明混合，不改变头颅取景。

最终双端 `test build` 通过：709 / 716 项 JUnit，0 失败/错误/跳过。两端各自有/无 JEI 原生图鉴审计均 PASS，33 / 32 张截图；全部 79 / 82 种 Mob 真实资源可用、非物品头像、逐格 GPU 像素可见。另验资源重载使全部旧头像 handle 失效，并经生产 HUD 恢复。僵尸头颅额头、眼睛、嘴和脸颊共 36 个像素逐点匹配原始 PNG，RGB 容差 2；头颅与牛纹理的白色/带 tint 调用方像素相同，半透明结果匹配原色与背景中点，状态退出恢复。

最终日志：`build/collection-portrait-build.log`、`build/collection-portrait-client-jei.log`、`build/collection-portrait-client-no-jei.log`。实际总览在 `run/collection-client-audit/16-vanilla-mob-portrait-atlas-{jei,no-jei}.png`，色彩对照在同目录 `15-restored-hud-portrait-tint-{jei,no-jei}.png`。菜单验收调用生产绘制链并验证缓存恢复，不代表本轮实测世界 overlay dispatch 或真实存档登录。

其他模组不会自动识别未知贴图中的脸：资源包每 EntityType 提供一次 `arc_quest/objective_icons/entities/<entity>.json`，即可让所有 AUTO KILL 目标、图鉴和追踪器共享二维图片/裁切规则；Java 使用 RegisterObjectiveIconsEvent 的头像注册入口。详细配置见 [Objective 图标使用说明](../OBJECTIVE_ICONS.md)。
