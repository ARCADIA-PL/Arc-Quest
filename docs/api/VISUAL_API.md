# 视觉系统 API 参考

**模块**: client/gui/render/  
**适用对象**: 开发者、外部AI学习  
**最后更新**: 2026-04-17

---

## 📚 目录

1. [渲染器层](#渲染器层)
2. [动画工具](#动画工具)
3. [渲染工具](#渲染工具)
4. [枚举类型](#枚举类型)

---

## 渲染器层 (client/gui/render/)

### QuestSplashRenderer - 立绘渲染器

**位置**: `org.com.arc_quest.client.gui.render.QuestSplashRenderer`

**职责**: 渲染全屏立绘动画

**动画流程**:
```
入场 (600ms, easeOutQuint) 
  ↓
停留 (2500ms, 呼吸效果)
  ↓
退场 (500ms, easeInQuart)
```

**核心方法**:

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `trigger()` | QuestDefinition, SplashType, ResourceLocation | void | 触发展示立绘 |
| `render()` | GuiGraphics, float, int, int | void | 每帧渲染 |
| `isActive()` | 无 | boolean | 是否有活跃立绘 |
| `clear()` | 无 | void | 清除队列 |

**使用示例**:
```java
// 由ClientQuestEvents自动调用
ClientQuestEvents.handleVisualTrigger(quest, SplashType.QUEST_ACQUIRED, null);
```

**内部状态机**:
```java
enum SplashState {
    ENTERING,   // 入场
    HOLDING,    // 停留
    EXITING,    // 退场
    IDLE        // 空闲
}
```

---

### QuestIconRenderer - 图标渲染器

**位置**: `org.com.arc_quest.client.gui.render.QuestIconRenderer`

**核心方法**:

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `renderIcon()` | GuiGraphics, ResourceLocation, int, int, int, int | void | 渲染图标 |
| `renderIcon()` | GuiGraphics, VisualAsset, int, int, int, int | void | 带配置的图标渲染 |

**使用示例**:
```java
// 简单渲染
QuestIconRenderer.renderIcon(g, iconTexture, x, y, 16, 16);

// 带缩放和染色
VisualAsset asset = new VisualAsset(texture, 1.2f, 0, 0, 0xFFFF0000, true);
QuestIconRenderer.renderIcon(g, asset, x, y, 20, 20);
```

---

### QuestSplashOverlay - 立绘覆盖层

**位置**: `org.com.arc_quest.client.gui.render.QuestSplashOverlay`

**职责**: 管理立绘的全局显示（单例）

**核心方法**:

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `INSTANCE` | - | QuestSplashOverlay | 单例实例 |
| `triggerSplash()` | QuestDefinition, SplashType | void | 触发立绘 |
| `render()` | GuiGraphics, float, int, int | void | 渲染当前立绘 |

---

## 动画工具 (client/gui/)

### QuestAnimUtil - 动画工具集

**位置**: `org.com.arc_quest.client.gui.QuestAnimUtil`

**插值方法**:

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `lerp()` | float, float, float, float | float | 指数平滑插值 |
| `step()` | float, float, float, float | float | 线性步进 |

**缓动函数**:

| 方法 | 公式 | 用途 |
|------|------|------|
| `easeOutCubic()` | `1-(1-t)³` | 快速入场 |
| `easeInCubic()` | `t³` | 慢速入场 |
| `easeInQuartic()` | `t⁴` | 快速退场 |
| `easeOutQuintic()` | `1-(1-t)⁵` | 平滑入场 |
| `easeInSextic()` | `t⁶` | Alpha衰减 |
| `smoothStep()` | `t²(3-2t)` | S形曲线 |
| `easeOutBack()` | 回弹公式 | 弹性效果 |

**颜色工具**:

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `withAlpha()` | int, int | int | 合并透明度 |
| `lerpColor()` | int, int, float | int | 颜色插值 |

**绘制工具**:

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `drawFrame()` | GuiGraphics, ... | void | 绘制边框矩形 |
| `drawAccentPanel()` | GuiGraphics, ... | void | 绘制强调条面板 |
| `drawProgressBar()` | GuiGraphics, ... | void | 绘制进度条 |
| `drawProgressBarGlow()` | GuiGraphics, ... | void | 绘制发光进度条 |

---

## 渲染工具 (client/gui/)

### QuestRenderUtil - 渲染工具集

**位置**: `org.com.arc_quest.client.gui.QuestRenderUtil`

**面板绘制**:

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `drawGlassPanel()` | GuiGraphics, ... | void | 磨砂玻璃面板 |
| `drawToastPanel()` | GuiGraphics, ... | void | Toast面板 |

**文本渲染**:

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `drawDualText()` | GuiGraphics, ... | void | 双行文本 |
| `drawTextWithLine()` | GuiGraphics, ... | void | 带装饰线文本 |

**Scissor辅助**:

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `applyDynamicScissor()` | GuiGraphics, ... | void | 动态裁剪区域 |

**RenderSystem封装**:

| 方法 | 返回 | 说明 |
|------|------|------|
| `enableBlendNoDepth()` | void | 启用混合+禁用深度 |
| `disableBlendWithDepth()` | void | 禁用混合+启用深度 |

---

## 枚举类型

### SplashType - 立绘类型

```java
enum SplashType {
    QUEST_DETAIL,      // 任务详情
    QUEST_ACQUIRED,    // 获得任务
    PHASE_START,       // 阶段开始
    PHASE_COMPLETE,    // 阶段完成
    QUEST_COMPLETED,   // 任务完成
    DIALOGUE_START,    // 对话开始
    DIALOGUE_END       // 对话结束
}
```

### IconPosition - 图标位置

```java
enum IconPosition {
    QUEST_LIST,           // 任务列表
    QUEST_TITLE,          // 任务标题
    QUEST_DETAIL_PANEL,   // 详情面板
    PHASE_LABEL,          // 阶段标签
    HUD_TRACKER,          // HUD追踪
    DIALOGUE_NPC_AVATAR   // 对话头像
}
```

---

## 附录

### 视觉配置使用示例

```java
QuestVisualConfig config = QuestVisualConfig.builder()
    // 添加立绘
    .splash(SplashType.QUEST_ACQUIRED, texture, 1.2f)
    .splash(SplashType.PHASE_START, texture, 1.1f)
    
    // 添加图标
    .icon(IconPosition.QUEST_LIST, iconTexture)
    .icon(IconPosition.PHASE_LABEL, iconTexture)
    
    // 设置主题色
    .themeColor(0xFFFFD700)  // 金色
    
    .build();
```

### 性能优化要点

1. **批量绘制**: 使用顶点缓冲减少Draw Call
2. **早期退出**: 不可见时跳过渲染
3. **纹理缓存**: 避免重复加载ResourceLocation
4. **Scissor裁剪**: 只渲染可见区域
5. **时间步长限制**: dt最大100ms防止卡顿跳跃

---

**文档结束**

*本API参考涵盖视觉系统的所有公共接口、渲染器和工具类。*
