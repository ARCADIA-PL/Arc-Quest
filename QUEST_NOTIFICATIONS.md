# 左侧统一任务通知

任务、阶段、目标与收集通知共用原有左侧居中位置。右上角通知已取消，通知也不再推移追踪器。面板采用 180×32 基准尺寸，并在自适应 UI 比例上缩放到 90%；副标题使用正文字号的 90%。相较于过小的 70% 版本，正文增大约 29%、副标题增大约 54%，面板宽度仅增大约 16%。上行说明事件类型和所属任务，下行显示具体名称；保留暗底、细线与主题侧线。

## 调度规则

- 同时只绘制一条通知。普通事件按顺序播放，进入 250 ms、停留 3000 ms、退出 200 ms。
- 使用事件类型、任务 ID 和目标/阶段/条目 ID 去重，不以翻译后的名称识别任务。
- 200 ms 聚合等待内收集突发变化，同任务同类型事件在 600 ms 合并窗口中合为一条，显示“另有 N 项”。最多保留 64 组普通待播事件，30 秒活动时间后未播出的旧消息被淘汰。
- 完成或失败优先使同任务尚未播放的接取、阶段和目标提示失效，避免任务结束后继续播放过期进度。权威同步删除或放弃任务时，也清理其当前和排队中的普通消息。
- 等待阶段确认仅在进入该状态时短暂提示一次，按普通通知停留约 3 秒后淡出。重复进度同步、变量更新和全量同步不反复播放；确认完成后立即清理仍在显示或排队的提示。
- 阶段加入不再显示通知，也不提供配置界面开关。自动推进只显示阶段完成；手动切换既有并行阶段仍可显示阶段切换。任务历史继续记录阶段状态。
- 待选分支仍是状态驱动的持久待办，所有符合条件的并行阶段都可入列；空闲时每 5 秒轮换。
- 新事件可以打断普通待办展示；连续 3 条事件后给待办至少 2 秒展示，避免持续获得物品时待办始终不可见。
- F1、任务日志、追踪菜单、对话和任务横幅隐藏通知时，同时冻结活动时钟。离开会话清空状态。
- 初次全量同步只恢复当前待办，不回放历史通知。后续权威同步可补回已知活动任务漏收的完成/失败通知。

## 待办与配置

分支提示要求任务与阶段仍处于活动状态、目标满足服务器的完成判定，并存在当前可见的选项。NULL 说明目标不阻塞分支；动态目标数量使用运行时值；隐藏目标不会泄露到目标完成通知中。待办在阶段离开、任务结束或全量同步替换后清理。点击分支后保留提示，直到服务器状态确认；请求被拒绝时不会丢失待办。

配置仍使用 `arc_quest-toasts.toml`。为兼容现有配置，保留 `tracked_quest_notifications` 分组路径，但这些开关现在控制左侧统一队列，不限于正在追踪的任务。旧 `phase_added` 配置键仅作兼容，不论其值都不会显示阶段加入通知。保存配置或文件重载立即过滤已关闭的通知；重新开启时从最新状态恢复未解决分支，不重播普通历史消息或确认提示。

## 渲染与扩展入口

- `QuestNotificationOverlay` 是唯一绘制入口，注册 ID 为 `arc_quest:quest_toasts`。
- `arc_quest:quest_hud` 保留旧 API 与注册 ID，实际渲染为空。原阶段/分支调用通过兼容方法转入统一管理器。
- `ToastScheduler` 是独立于 Minecraft 的纯调度模型；`QuestToastManager` 负责客户端配置与文字载荷；`QuestToastLayout` 与 `QuestNotificationToast` 负责布局和动画。
- `QuestHistoryAndToastListener` 通过客户端状态事件接入通知。网络包只保留专用横幅触发，不再额外重复发出 Toast。
- 新普通事件使用 `QuestToastManager.show(type, questId, subjectId, title, detail)`；任务状态变化后使用 `replacePendingForQuest` 更新该任务的完整待办集合。
- 布局与裁剪文字缓存按通知版本更新；渲染期间不逐帧扫描任务、阶段或玩家物品栏。

## 验证入口

```powershell
./gradlew.bat --offline runData
./gradlew.bat --offline test build jarJar compileGameTestJava
./gradlew.bat --offline -PquestToastRuntimeAudit runClient
```

原生通知验收只在标题界面运行，不打开存档。它使用固定动画时间和实际帧缓冲验证位置、GUI 缩放、文字边界、透明端点和兼容空入口。审计代码不会进入发布 JAR；结果以 `[ARCQ_QUEST_TOAST_AUDIT] PASS` 为准。

## 首轮统一通知验收（2026-10-02）

- `runData` 成功；中英文各补齐 14 种事件前缀、合并文案和统一通知配置说明。
- `test build jarJar compileGameTestJava` 成功：112 个测试类、559 个测试，0 失败、0 错误、0 跳过。本轮新增 40 个调度、布局与状态规则测试。
- `-PquestToastRuntimeAudit runClient` 成功，保存 12 张实际帧缓冲截图。四档 GUI 缩放下均保持左侧居中；右上角没有通知像素；长标题和上下文没有越界。
- 进入起点、低透明度和过期通知不绘制；退出中间帧的文字与底板一起淡出；60 秒的持久待办仍然可见。
- 旧 Overlay 单独调用为空绘制，和新入口同时调用也不产生重复绘制。原生验收恢复窗口、GUI 选项与界面，没有打开世界。

日志为 `build/quest-toasts-build.log`、`build/quest-toasts-final-build.log`、`build/quest-toasts-client.log`；截图位于 `run/screenshots/quest-toasts/`。首轮原生验收覆盖通知渲染，事件队列和状态条件由单元测试覆盖；未进行世界内多人长时压力测试。

## 缩小通知与减少重复提醒验收（2026-10-02）

- `runData` 成功；中英文同步移除阶段加入的配置项，确认提示文案改为短暂显示一次。
- `test build jarJar compileGameTestJava` 成功：113 个测试类、566 个测试，0 失败、0 错误、0 跳过。新增 7 个测试覆盖确认状态进入、正常过期、同步不恢复常驻确认、按阶段清理、阶段加入禁用与缩小后的布局。
- `-PquestToastRuntimeAudit runClient` 成功，`[ARCQ_QUEST_TOAST_AUDIT] PASS`；本轮保存 14 张原生帧缓冲截图，覆盖四档实际 GUI 缩放、长文本、进入和退出透明度、旧入口空绘制与重复绘制检测。
- 四档 GUI 缩放下通知仍位于左侧垂直中央，宽度小于屏幕的三分之一、高度小于十分之一。当时的面板缩放为 70%；后续可读性调整覆盖同名截图。`before-compacting.png` 保留最初版本对照。
- 确认提示在普通时间正常显示，60 秒时已消失；分支提示在 60 秒时仍保留。阶段加入在旧配置下也不能启用。验收恢复窗口、GUI 选项与界面，没有打开世界或写入配置。
- 发布包 `build/libs/arc_quest-forge1.20.1-1.0.8-all.jar` 已检查：包含更新后的布局、通知策略与中英文资源，没有原生审计类，配置界面的阶段加入翻译键已移除。

本轮日志为 `build/quest-toasts-compact-data.log`、`build/quest-toasts-compact-build.log`、`build/quest-toasts-compact-client.log`；截图位于 `run/screenshots/quest-toasts/`。本轮未进行世界内多人长时压力测试。

## 可读性调整验收（2026-10-02）

- 整体缩放从 70% 调整到 90%，副标题从正文字号的 75% 调整到 90%；面板基准宽度从 200 收至 180，优先增大文字。确认提示的停留时间与阶段加入禁用规则保持不变。
- `test build jarJar compileGameTestJava` 成功：113 个测试类、566 个测试，0 失败、0 错误、0 跳过。
- `-PquestToastRuntimeAudit runClient` 成功，`[ARCQ_QUEST_TOAST_AUDIT] PASS`；14 个场景通过四档 GUI 缩放、长文本边界、两行不重叠、透明度与过期规则验证。
- 1280×960 帧缓冲下通知约 432×77 像素，正文行高约 21.6 像素、副标题约 19.4 像素；四档 GUI 缩放保持一致。相较过小版本，文字分别增大约 29% 与 54%，通知宽度仅增大约 16%。
- 发布包中的 `QuestToastLayout` 已检查为 `WIDTH=180`、`HEIGHT=32`、`SIZE_FACTOR=0.9`、`SUBTITLE_SCALE=0.9`，不包含原生审计类。

日志为 `build/quest-toasts-readable-build.log`、`build/quest-toasts-readable-client.log`。`run/screenshots/quest-toasts/02-scale2-long-text.png` 显示本轮效果；`before-readability.png` 保留过小版本对照。
