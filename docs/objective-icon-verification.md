# Objective ICON 实现与验收记录

使用手册见 [OBJECTIVE_ICONS.md](../OBJECTIVE_ICONS.md)，原始设计见 [objective-icon-design.md](objective-icon-design.md)，目标匹配与数量兼容性见 [objective-count-semantics.md](objective-count-semantics.md)。

## 验收结果

| 检查 | 最终结果 | 证据 |
| --- | --- | --- |
| Java | 427/427通过，0失败/错误/跳过 | `build/test-results/test/TEST-*.xml` |
| Web | 此前基线45/45通过，Vite构建成功；本次未改Web | 编辑器 `npm test` / `npm run build` |
| 独立服务器、无JEI | 此前基线9/9通过；本次修改限于客户端 | `build/objective-icons-server-audit.log` |
| 最终客户端、有JEI | PASS，12张截图及像素断言，包含OFFER/DELIVER默认图标 | `build/objective-icons-defaults-final.log` |
| 最终客户端、无JEI | PASS，11张截图及像素断言，包含OFFER/DELIVER默认图标 | `build/objective-icons-defaults-without-jei.log` |
| 发布构建 | `test build jarJar runClient` 成功，1分38秒；无JEI客户端59秒通过 | 上述两份日志，均无临时`-D`或`--offline`参数 |

最终两次客户端均包含OFFER/DELIVER默认物品图标、OFFER Tag多候选、奖励栏Tooltip复用、字号弹窗深度隔离、图标整体透明度、软边纹理、嵌套绘制与附魔缓冲修复。有JEI运行同时验证真实查询与返回；两组策略截图均显示OFFER绿宝石、DELIVER钻石以及轮换到第2/40个候选的OFFER Tag。已复查原生日志单阶段和并行卡片的浅灰细边框。检查普通JAR和all JAR：图标类、牛猪资源、两个专用shader配置均存在，客户端审计及GameTest类未打包。

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
| 客户端有JEI | 原生单阶段与三并行阶段、候选轮换/焦点暂停、自绘Tooltip一致、当前候选命中、真实改绑鼠标查询和返回原Journal |
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

- OFFER与DELIVER在AUTO策略下直接显示目标物品，无需作者额外配置；复用COLLECT/CRAFT的真实候选解析、Tag轮播、Tooltip和JEI命中。显式NONE、图片和自定义provider继续优先于默认图标。
- Objective Tooltip 使用与奖励物品相同的 `renderTooltipLayout`，包括背景、侧边、物品小图、定位及动画；保留名称和必要的Tag一行，不显示JEI快捷键、候选序号或重复目标说明。此前新增的固定锚点小卡片与图标底色已删除；按后续样式要求，图标保留与进度条底轨相同透明度的1像素浅灰框，悬停时仅边框平滑提亮为主题色。鼠标移出或开始关闭时保留最后请求，直到淡出完成。
- `ArcQuestTextConfigScreen` 在父预览结束后提交缓冲、隔离深度、分批提交遮罩与控件；Journal、Dialogue、Trade、GuideList、Guide共用此修复。两种环境中，正文/滑条/按钮三个像素检查均为 `leaked=0`，干扰差异最大为2/255，符合面板原有轻微透明度。
- 图标整体淡出使用独立混合shader，普通物品与附魔层分开缓冲；多层头像、半透明图片和嵌套provider透明度均保留。原生日志关闭中间帧的图标亮度约为完整帧的48.4%；Tag Tooltip严格两行，普通物品Tooltip严格一行。资源重载后继续验证图标、shader与缓存重建。

详细回归入口、像素对照方法与边界见 [客户端回归说明](../src/gameTest/OBJECTIVE_ICON_AUDIT.md)。实际截图已人工复查；测试不修改字号配置，只操作指定隔离世界。

## 明确范围

- 内置头像为骷髅、凋灵骷髅、僵尸、苦力怕、猪灵、末影龙、牛和猪；其他实体由作者注册头像或提供资源包规则后显示，否则无图。
- CRAFT延续原有 `PlayerEvent.ItemCraftedEvent`，不自动识别任意模组机器的生产事件。
- 第三方资源包若重排实体UV，需要提供对应规则；本次检查项目当前加载的纹理及真实资源重载。
- 历史界面使用保留的有效数量快照；无快照时明确回退定义值，不重建已经丢失的动态历史数据。
- 协议升级为16，联机双方需要使用匹配的ArcQ版本。
