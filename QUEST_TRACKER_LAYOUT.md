# 任务追踪器 Overlay 与布局编辑

## 玩家入口

打开 Arc Quest 模组配置，在「常规」页第一行找到「任务追踪器」，点击「编辑」。游戏暂停菜单和模组列表中的 Arc Quest 配置均使用该入口。

- 拖动追踪器整体调整位置。
- 拖动右下角手柄等比例调整大小；命中区域为 16 GUI 像素。
- 鼠标悬停追踪器时滚轮每次缩放 5%；也可使用工具条的减号/加号。
- 方向键微调 1 GUI 像素，Shift + 方向键微调 10 像素。
- 「恢复默认」只修改当前预览。点击「保存」才持久化；「取消」或 Esc 放弃本次修改。

有正在追踪的任务时预览真实任务；主菜单或没有追踪任务时使用明确标注的示例，方便随时调整。预览不接受任务、不修改进度或追踪选择。工具条会选择遮挡预览最少的角落。

## 布局与保存

本地客户端配置文件：`config/arc_quest-tracker.toml`。

```toml
[quest_tracker]
position_x = 1.0
position_y = 0.0
scale = 1.0
```

`position_x` / `position_y` 为追踪器在屏幕可用空间中的比例（0～1），默认右上角。换分辨率或 Minecraft GUI 缩放后仍保持相对位置。`scale` 是原有自适应大小的倍数，支持 0.5～2.0；面板高度继续随内容变化，字体和进度条一起等比缩放，长内容自动适配屏幕边界。HUD 与编辑器使用同一 `TrackerLayout` 计算，通知推移仍作用于实际 HUD。

## 开发者

- `QuestTrackerPanel.INSTANCE` 直接实现 `IGuiOverlay`，在 `RegisterGuiOverlaysEvent` 正式注册为 `arc_quest:quest_tracker`。
- `arc_quest:quest_hud` 保留原有通知职责，不再持有或绘制追踪器。Tracker 自己从客户端追踪控制器读取目标与阶段，不依赖旧 Overlay 的绘制顺序。
- 默认绘制顺序是 tracker、quest_hud 通知、splash。原有 F1 隐藏、界面遮挡、任务切换、阶段过渡与离线会话清理继续生效。
- 编辑器使用单独 `QuestTrackerPanel` 实例，预览不会推进正在使用的 HUD 动画。
- 中英文本位于 `ArcQuestENLangProvider` / `ArcQuestZHLangProvider`；修改后先执行 `runData`，再构建以打包最新语言资源。

## 验证入口

```powershell
./gradlew.bat runData
./gradlew.bat test build jarJar compileGameTestJava
./gradlew.bat -PtrackerLayoutRuntimeAudit runClient
```

`TrackerLayoutTest` 覆盖分辨率、GUI 缩放、拖动反向映射、四角边界、等比缩放、长内容和非法配置。原生验收只在主菜单运行，不打开任何存档；从正式配置入口验证编辑、保存、取消、重置、重新打开、GUI 缩放和截图像素，并恢复原配置与选项。审计标记为 `[ARCQ_TRACKER_LAYOUT_AUDIT]`，截图在 `run/screenshots/tracker-layout/`。审计代码不进入发布 JAR。

## 本次验收（2026-10-01）

- `runData` 完成，中英语言资源已生成并打包。
- `test build jarJar` 通过：106 个测试类、495 个测试，0 失败、0 错误、0 跳过；其中新增 15 个布局测试。
- `-PtrackerLayoutRuntimeAudit runClient` 通过：正式 Overlay 单例注册、Forge 配置入口、拖动、手柄缩放、滚轮缩放、GUI 缩放后边界、取消不写入、保存 TOML、重新打开恢复、重置仅改草稿、Esc 放弃修改均通过。
- 四张原生截图验证了标题、目标文本及进度条像素；已人工检查 GUI 缩放后和保存重开后的画面。验收结束恢复配置原始字节与 GUI 选项，没有打开任何世界。
- 发布 `all.jar` 包含新 Overlay、编辑器、布局配置及中英文本，未包含 `TrackerLayoutClientAudit`。

日志：`build/tracker-layout-final-build.log`、`build/tracker-layout-client.log`；截图：`run/screenshots/tracker-layout/01-before-edit.png` ～ `04-reopened-saved.png`。本次原生验收覆盖主菜单编辑流程，未以游戏世界内实际任务验证动画。
