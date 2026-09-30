# Objective ICON 实现与验收记录

使用手册见 [OBJECTIVE_ICONS.md](../OBJECTIVE_ICONS.md)，原始设计见 [objective-icon-design.md](objective-icon-design.md)，目标匹配与数量兼容性见 [objective-count-semantics.md](objective-count-semantics.md)。

## 验收结果

| 检查 | 最终结果 | 证据 |
| --- | --- | --- |
| Java | 424/424通过，0失败/错误/跳过 | `build/test-results/test/TEST-*.xml` |
| Web | 45/45通过，Vite单文件构建成功 | 编辑器 `npm test` / `npm run build` |
| 独立服务器、无JEI | 9/9通过 | `build/objective-icons-server-audit.log` |
| 最终客户端、有JEI | PASS，6张截图 | `build/objective-icons-release-verification.log` |
| 最终客户端、无JEI | PASS，5张截图 | `build/objective-icons-client-without-jei-final.log` |
| 发布构建 | `test build jarJar runClient` 成功 | `build/objective-icons-release-verification.log` |

最终两次客户端均包含猪灵取景修复；有JEI运行也确认新界面初始化时不再发送0尺寸GUI属性。检查普通JAR和all JAR：图标类、牛猪资源均存在，客户端审计及GameTest类未打包。

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

本机缓存完整，但ForgeGradle远程证书预检不稳定，因此实际Gradle命令额外使用 `--offline -Dnet.minecraftforge.gradle.check.certs=false`。该选项只作用于这次离线命令，没有修改仓库、系统信任库或在线依赖下载设置。正常网络环境使用上面的普通命令即可。

## 自动验证覆盖

| 层级 | 覆盖 |
| --- | --- |
| Java单元测试 | 配置/JSON往返、旧构造与复制、编辑器历史、候选匹配、Tag索引、有效数量与网络/NBT、帧选择、轮播暂停、行几何、头像UV/缓存/资源校验、JEI候选权限与NBT语义 |
| Web编辑器 | 图标表单、嵌套字段、导入导出、复制、未知配置保留、错误路径及既有编辑器回归 |
| 真实独立服务器 | 9项GameTest，其中新增4项覆盖加载后的Tag索引、有效数量/JEI/网络一致、collection计数模式、提交匹配和只读预览 |
| 客户端有JEI | 原生单阶段与三并行阶段、候选轮换/焦点暂停、自绘Tooltip一致、当前候选命中、真实改绑鼠标查询和返回原Journal |
| 客户端无JEI | 同一原生图标、阶段布局、轮播、Tooltip及资源重载流程；检查JEI目录未启用 |
| 二维头像 | 六种静态正面头颅、牛角/脸和猪脸/鼻分层UV；真实资源重载后旧handle失效并重绘全部8项 |
| 显示策略 | COLLECT、CRAFT、多候选Tag、显式纹理、NONE、资源缺失、隐藏目标、未知provider；无图标不保留图标列 |
| 只读性 | 查看、JEI查询和重载前后玩家库存与经验一致 |

客户端必须出现 `[ARCQ_OBJECTIVE_ICON_AUDIT] PASS`，不能仅凭Minecraft正常退出判断成功。Gradle额外检查 `run/logs/latest.log`，缺少PASS或出现FAIL都会令任务失败。客户端自身有300秒超时并正常关闭。

## 运行产物与截图

完整日志在 `build/objective-icons-*.log`，单元测试报告在 `build/reports/tests/test/index.html`。真实帧缓冲截图保存在：

```text
run/screenshots/objective-icons/with-jei/
run/screenshots/objective-icons/without-jei/
```

每组包含 `single-scale1-focused-tooltip.png`、`parallel-scale2.png`、`portraits-scale2.png`、`policies-scale2-tag-rotated.png`、`portraits-after-resource-reload.png`；有JEI时另有 `single-scale1-jei-return.png`。这些文件在忽略的运行目录中，不打入模组包。

视觉复查确认固定正面、牛猪UV及重载后画面一致；根据实测补了长Tooltip换行和屏幕边界限制，以及猪灵耳尖取景范围。并行卡片采用紧凑两行布局，保持原有卡片滚动、拖动和提交入口。

## 明确范围

- 内置头像为骷髅、凋灵骷髅、僵尸、苦力怕、猪灵、末影龙、牛和猪；其他实体由作者注册头像或提供资源包规则后显示，否则无图。
- CRAFT延续原有 `PlayerEvent.ItemCraftedEvent`，不自动识别任意模组机器的生产事件。
- 第三方资源包若重排实体UV，需要提供对应规则；本次检查项目当前加载的纹理及真实资源重载。
- 历史界面使用保留的有效数量快照；无快照时明确回退定义值，不重建已经丢失的动态历史数据。
- 协议升级为16，联机双方需要使用匹配的ArcQ版本。
