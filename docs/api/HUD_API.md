# HUD系统 API 参考

**模块**: client/gui/  
**适用对象**: 开发者、外部AI学习  
**最后更新**: 2026-04-17

---

## 📚 目录

1. [HUD协调器](#hud协调器)
2. [任务追踪面板](#任务追踪面板)
3. [Toast通知系统](#toast通知系统)
4. [阶段推进提示](#阶段推进提示)
5. [分支选择提示](#分支选择提示)

---

## HUD协调器

### QuestHudOverlay - HUD协调器

**类型**: `class implements IGuiOverlay`  
**位置**: `org.com.arc_quest.client.gui.QuestHudOverlay`

**单例**:
```java
public static final QuestHudOverlay INSTANCE = new QuestHudOverlay();
```

**核心方法**:

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `render()` | GuiGraphics, float, int, int | void | 渲染所有HUD元素 |
| `showBranchChoiceToast()` | String | void | 显示分支选择提示 |
| `clearBranchChoiceToast()` | 无 | void | 清除分支提示 |
| `setTrackedQuest()` | String | void | 设置追踪任务 |

**渲染顺序**:
1. QuestTrackerPanel（右上角）
2. PhaseUpdateToast（左侧）
3. BranchChoiceToast（左侧）
4. QuestToastManager（顶部）

**时空冻结机制**:
```java
boolean isBlockingScreen = mc.screen instanceof QuestJournalScreen 
                        || mc.screen instanceof DialogueScreen;
// 模态界面打开时，HUD暂停动画但保持可见
```

---

## 任务追踪面板

### QuestTrackerPanel - 任务追踪面板

**位置**: `org.com.arc_quest.client.gui.QuestTrackerPanel`

**布局常量**:
```java
PANEL_WIDTH     = 175px
MARGIN_RIGHT    = 6px
MARGIN_TOP      = 30px
ACCENT_WIDTH    = 3px
TITLE_HEIGHT    = 14px
OBJ_ROW_HEIGHT  = 11px
PROGRESS_BAR_H  = 3px
```

**核心方法**:

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `render()` | GuiGraphics, int, int, float | void | 渲染面板 |
| `setTrackedQuest()` | String | void | 设置追踪任务 |
| `getTrackedQuestId()` | 无 | String | 获取追踪任务ID |
| `resetPanelAnimation()` | 无 | void | 重置动画 |

**动画参数**:
```java
DISMISS_DELAY      = 2000ms   // 完成/失败后停留时间
DISMISS_SLIDE_TIME = 500ms    // 滑出动画时间
TIME_WIPE_OUT      = 250ms    // 阶段切换擦除时间
TIME_WIPE_IN       = 350ms    // 阶段切换显示时间
```

**视觉特性**:
- 动态主题色（从任务定义提取）
- 图标支持（IconPosition.HUD_TRACKER）
- 进度条发光效果
- 平滑滑入/滑出动画

---

## Toast通知系统

### QuestToastManager - Toast管理器

**位置**: `org.com.arc_quest.client.gui.QuestToastManager`

**Toast类型枚举**:
```java
enum ToastType {
    QUEST_ACCEPTED(0x4FC3F7, "✦ QUEST ACCEPTED"),
    QUEST_COMPLETED(0x66FF66, "★ QUEST COMPLETED"),
    QUEST_FAILED(0xFF6666, "✘ QUEST FAILED"),
    PHASE_ADVANCED(0xFFCC44, "▸ PHASE ADVANCED"),
    OBJECTIVE_COMPLETE(0x88DDFF, "✔ OBJECTIVE DONE");
}
```

**核心方法**:

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `show()` | ToastType, String | void | 显示Toast |
| `show()` | ToastType, Component | void | 显示Toast |
| `clear()` | 无 | void | 清除所有Toast |
| `tick()` | 无 | void | 更新状态（每帧调用） |
| `render()` | GuiGraphics, int, int, boolean | void | 渲染队列 |
| `getPushDownOffset()` | 无 | int | 获取避让高度 |

**队列管理**:
- 最多同时显示3个Toast
- 新Toast从pendingQueue加入activeSlots
- 超时自动移除（ENTER + HOLD + EXIT）

---

## 阶段推进提示

### PhaseUpdateToast - 阶段推进提示

**位置**: `org.com.arc_quest.client.gui.PhaseUpdateToast`

**布局常量**:
```java
POPUP_W = 220px
POPUP_H = 36px
```

**动画时序**:
```java
PHASE_ENTER = 500ms   // 入场
PHASE_HOLD  = 2800ms  // 停留
PHASE_EXIT  = 400ms   // 退场
```

**核心方法**:

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `render()` | GuiGraphics, Font, int, int, float, boolean | boolean | 渲染弹窗 |

**返回值**: `true`表示继续存活，`false`表示应销毁

---

## 分支选择提示

### BranchChoiceToast - 分支选择提示

**位置**: `org.com.arc_quest.client.gui.BranchChoiceToast`

**布局常量**:
```java
POPUP_W = 220px
POPUP_H = 36px
```

**动画时序**:
```java
TIME_ENTER = 600ms
TIME_EXIT  = 400ms
```

**核心方法**:

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `render()` | GuiGraphics, int, int, float, float, boolean | boolean | 渲染弹窗 |
| `dismiss()` | 无 | void | 标记为待销毁 |
| `isExpired()` | 无 | boolean | 是否过期 |
| `getQuestId()` | 无 | String | 获取任务ID |

---

## 附录

### HUD布局示意图

```
┌───────────────────────────────────────┐
│  [Toast Manager]                      │  ← 顶部居中
│                                       │
│                                       │
│              [Tracker Panel]          │  ← 右上角
│                                       │
│  [Phase Toast]                        │  ← 左侧（动态堆叠）
│  [Branch Toast]                       │
│                                       │
└───────────────────────────────────────┘
```

### 性能优化要点

1. **早期退出**: 不可见时跳过渲染
2. **Scissor裁剪**: 只渲染可见区域
3. **顶点缓冲**: 批量绘制减少Draw Call
4. **动画状态缓存**: 避免重复计算
5. **时间步长限制**: dt最大100ms防止卡顿跳跃

---

**文档结束**

*本API参考涵盖HUD系统的所有公共接口和组件。*
