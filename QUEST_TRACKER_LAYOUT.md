# 任务追踪器 Overlay 与布局编辑

## 玩家入口

打开 Arc Quest 模组配置，在「常规」页第一行找到「任务追踪器」，点击「编辑」。游戏暂停菜单和模组列表中的 Arc Quest 配置均使用该入口。

- 拖动追踪器整体调整位置。
- 拖动右下角手柄等比例调整大小；命中区域为 16 GUI 像素。
- 鼠标悬停追踪器时滚轮每次缩放 5%；也可使用工具条的减号/加号。
- 方向键微调 1 GUI 像素，Shift + 方向键微调 10 像素。
- 工具条直接选择「经典 / 专注 / 总览」；切换立即预览，点击「保存」才写入样式和布局。
- 「重置」恢复默认预览。点击「保存」才持久化；「取消」或 Esc 放弃本次修改。

有正在追踪的任务时预览真实任务；主菜单或没有追踪任务时使用明确标注的示例，方便随时调整。预览不接受任务、不修改进度或追踪选择。工具条会选择遮挡预览最少的角落。

## 样式设计

原有经典样式采用半透明深色底、左侧主题色强调条；标题和时限置顶，接着显示阶段、说明和逐项目标进度，并行任务再增加阶段树。它适合需要阅读说明的剧情任务，但目标或并行阶段多时会占据更多高度。

| 样式 | 使用侧重 | 信息与功能 |
| --- | --- | --- |
| 经典（默认） | 完整阅读与剧情任务 | 保留原有阶段说明、逐项目标、并行阶段树与动画 |
| 专注 | 战斗、探索时减少遮挡 | 轻量线条布局，优先显示最多 3 个未完成目标，完成后自动补位，并显示剩余目标提示 |
| 总览 | 同时推进多个阶段 | 分区卡片显示阶段完成数和进度，强调当前阶段，同时展示当前目标；超过可见阶段数时保留当前阶段并提示其余数量 |

样式只改变客户端呈现，不改变服务器完成条件、目标次序、追踪选择和任务进度。不同数量级的目标以各自完成比例统计，不直接相加物品数量；隐藏目标不泄露到摘要。位置与大小仍共用一套设置，切换样式不会强制恢复位置。

## 布局与保存

本地客户端配置文件：`config/arc_quest-tracker.toml`。

```toml
[quest_tracker]
position_x = 1.0
position_y = 0.0
scale = 1.0
style = "CLASSIC"
```

`position_x` / `position_y` 为追踪器在屏幕可用空间中的比例（0～1），默认右上角。换分辨率或 Minecraft GUI 缩放后仍保持相对位置。`scale` 是原有自适应大小的倍数，支持 0.5～2.0；面板高度继续随内容变化，字体和进度条一起等比缩放，长内容自动适配屏幕边界。HUD 与编辑器使用同一 `TrackerLayout` 计算。通知已统一到左侧，不再推移追踪器。

`style` 可取 `CLASSIC`、`FOCUS`、`OVERVIEW`，旧配置缺少此字段时默认经典。「重置」恢复经典样式和默认布局，但仍须保存才生效。

## 开发者

- `QuestTrackerPanel.INSTANCE` 直接实现 `LayeredDraw.Layer`，在 `RegisterGuiLayersEvent` 正式注册为 `arc_quest:quest_tracker`。
- `arc_quest:quest_hud` 保留旧 API 与注册 ID，但不再绘制任何通知或追踪器。Tracker 自己从客户端追踪控制器读取目标与阶段，不依赖旧 Overlay 的绘制顺序。
- 默认上层绘制顺序是 tracker、quest_hud 兼容空入口、quest_toasts 左侧统一通知、任务横幅、指南横幅、抽卡结果、指南弹窗。原有 F1 隐藏、界面遮挡、任务切换、阶段过渡与离线会话清理继续生效。
- 编辑器使用单独 `QuestTrackerPanel` 实例，预览不会推进正在使用的 HUD 动画。
- 新样式每 100 ms 更新只读进度快照；文本布局有界缓存，不扫描玩家背包。收集适配器的缓存也纳入当前条目局部进度，避免总完成数未变时条目数量停留在旧值。
- 中英文本位于 `ArcQuestENLangProvider` / `ArcQuestZHLangProvider`；修改后先执行 `runData`，再构建以打包最新语言资源。

## HUD Overlay 梳理

| Overlay ID | 内容 | 本次处理 |
| --- | --- | --- |
| `arc_quest:quest_tracker` | 任务追踪器 | 保持独立注册，增加可选样式 |
| `arc_quest:quest_hud` | 旧通知 API 兼容入口 | 保留注册 ID，实际渲染为空 |
| `arc_quest:quest_toasts` | 任务、阶段、集合通知和待办 | 唯一左侧通知入口，由 `QuestNotificationOverlay` 接管实际绘制 |
| `arc_quest:quest_splash` | 任务启动、阶段等横幅 | 已注册，删除旧入口重复绘制 |
| `arc_quest:guide_splash` | 指南横幅 | 已注册，删除旧入口重复绘制 |
| `arc_quest:gacha_result` | 抽卡结果 | 已注册，清理逐 Overlay 回调及抽卡 Screen 内的重复绘制 |
| `arc_quest:guide_popup` | 指南弹窗 | `GuidePopupOverlay` 直接实现 `LayeredDraw.Layer`，新增注册 |
| `arc_quest:quest_markers` | 任务标记屏幕层 | 原本已注册在准星之后，维持原有关系 |

常驻 HUD 由 NeoForge 的注册层驱动。原 `RenderGuiLayerEvent.Post` 会随着每个 Overlay 触发，不能把所有 HUD 的绘制放在其中；该重复入口已移除。需要覆盖现有 Screen 的横幅、抽卡结果和指南弹窗，仍在 `ScreenEvent.Render.Post` 绘制，与无 Screen 时的注册入口互斥。

光标请求在一次 HUD 帧内汇总，各渲染器用同步作用域收集；可取消的 GUI Pre 事件只清理请求，不跨事件持有嵌套计数。GUI 被取消或渲染异常时，不会导致后续界面的手型状态无法恢复。Screen 内的任务详情、对话、商店、Ponder 面板，以及世界空间内容继续由所属界面或世界渲染流程绘制，不额外注册全局 HUD。

## 验证入口

```powershell
./gradlew.bat runData
./gradlew.bat test build jarJar compileGameTestJava
./gradlew.bat -PtrackerLayoutRuntimeAudit runClient
```

`TrackerLayoutTest` 覆盖分辨率、GUI 缩放、拖动反向映射、四角边界、等比缩放、长内容和非法配置。原生验收只在主菜单运行，不打开任何存档；从正式配置入口验证编辑、保存、取消、重置、重新打开、GUI 缩放和截图像素，并恢复原配置与选项。审计标记为 `[ARCQ_TRACKER_LAYOUT_AUDIT]`，截图在 `run/screenshots/tracker-layout/`。审计代码不进入发布 JAR。

## 本次验收（2026-10-01）

- `runData` 完成，中英语言资源已生成并打包。
- `test build jarJar compileGameTestJava` 通过：109 个测试类、519 个测试，0 失败、0 错误、0 跳过；本轮新增 16 个样式数据/运行时适配测试及 8 个 HUD 光标生命周期测试。
- `-PtrackerLayoutRuntimeAudit runClient` 通过：8 个正式 Overlay 的 ID、单例唯一性和顺序，NeoForge 配置入口、拖动、手柄缩放、滚轮缩放、GUI 缩放后边界、取消不写入、保存 TOML、重新打开恢复、重置仅改草稿、Esc 放弃修改均通过。
- 三种样式的立即切换、取消不保存、样式与布局一同保存、重开恢复、重置仍为草稿均通过。六张原生截图分别验证了文字、专注细轨和总览阶段进度条；人工检查了新样式和 GUI 缩放后的工具条。
- 验收结束恢复原样式、配置原始字节与 GUI 选项，没有打开任何世界。发布 `all.jar` 包含新样式、Overlay、编辑器、配置及中英文本，未包含 `TrackerLayoutClientAudit`。

日志：`build/tracker-styles-build.log`、`build/tracker-styles-client.log`；截图：`run/screenshots/tracker-layout/01-before-edit.png` ～ `06-reopened-saved-focus.png`；实机裁切对比：`tracker-styles-comparison.png`。本次原生验收覆盖主菜单编辑流程与 Overlay 注册，未进行世界内多人长时运行或动画验收。
