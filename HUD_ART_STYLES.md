# ArcQ HUD 美术风格

在 **模组列表 → Arc Quest → 配置 → 通用 → HUD 美术风格** 切换。点击后立即生效并保存；重置通用设置会恢复默认样式。

也可以编辑 `config/arc_quest-client.toml`：

```toml
[appearance]
hud_art_style = "DEFAULT" # DEFAULT / ORE / MODERNA / CYBERPUNK / WYRMHEART
```

- `DEFAULT`：原有 ArcQ 美术风格，也是新安装和旧配置迁移时的默认值。
- `ORE`：参考 [Ore UI](https://katorly.dev/OreUI/zh-CN/) 的像素直角、明暗斜面与按钮厚底，融合 ArcQ 原有的深色透明底。颜色由 ArcQ 原有主题、状态和稀有度决定，不统一替换成 Ore 的绿色。
- `MODERNA`：使用 [Jorge Avila 的 Moderna Graphical Interface](https://opengameart.org/content/moderna-graphical-interface) 的真实金属边框、雕花角饰与水晶纹理，配合暖黑色底和主题色镶边。主按钮的水晶已去色，可随任务、分类与品质色染色；场景面板继续使用深色透明底。
- `CYBERPUNK`：参考 [alddesign/cyberpunk-css](https://alddesign.github.io/cyberpunk-css/demo/) 的切角、底部凹口、偏移强调线与分段进度条。采用深色面板和所属内容的鲜明主题色；悬停与焦点高亮保留文字稳定可读，不强制换成黄色或青色。
- `WYRMHEART`：使用 [Wyrmheart 的 RPG Game UI](https://opengameart.org/content/rpg-game-ui) 原始 PSD 的哥特雕花角饰、旧金属边条、凹陷输入框、切角按钮和圆形徽章。大面板显示雕花，小控件使用细边框，近正方形卡片使用原作的物品槽边框。按钮材质和边条已去除原作固定红色，跟随主题、分类、品质和状态色；场景容器仍保留深色透明底。

点击配置值按上述顺序循环，最后回到 DEFAULT。已有配置的 DEFAULT / ORE 值继续有效，新安装仍默认 DEFAULT。

场景上的追踪器、通知、悬浮提示、对话，以及任务日志、教程、商店的外层面板保留各自原有的背景透明度；阅读区保持深色底与轻微主题色染色。配置主面板、内容卡片、按钮和输入框使用所选风格的实体表面，保持清晰的操作层级。主题色也进入普通卡片底色、斜面高光、选中与悬停状态、次级按钮、输入框焦点和进度轨道。正文保留可读性与内容自带的颜色。

覆盖任务日志、图鉴、历史、故事、提交、追踪器与追踪菜单、通知与展示卡片、教程列表与阅读器、商店与快捷商店、抽奖、对话与交付、任务标记、配置页面及任务编辑器。追踪器的 CLASSIC / FOCUS / OVERVIEW 是内容布局，独立于这里的全局美术风格。

样式切换只改变 ArcQ 的装饰绘制；布局、命中区域、滚动、任务状态、网络协议继续使用现有逻辑。物品模型、头像、教程图片、Ponder 场景和内容自带的富文本颜色保留。原版与其他模组的界面、原生物品提示框不在此选项范围内。

## 绘制实现

`client/hud/style/HudDraw` 是 ArcQ 的显式绘制入口。默认分支透传原有参数；其他风格通过 `HudStyleRenderer` 分发到 `OreUiRenderer`、`ModernaUiRenderer`、`CyberpunkUiRenderer`、`WyrmheartUiRenderer`。主面板、卡片、输入框、按钮、进度条和标记拥有专用绘制；有色装饰和文字保留原始 RGB。容器和控件显式传入所属界面的主题色，中性材质以不同比例混入该颜色；主按钮根据文字对比度压暗原始主题色。没有全局拦截 `GuiGraphics`，没有引入浏览器、CSS 运行时或新的字体资源。

新增 HUD 请通过 `HudDraw` 绘制基础图元，并对容器和交互控件使用对应的语义绘制方法。透明容器使用 `glassSurface` / `glassFrame`：背景 ARGB 已包含原有透明度和动画，边缘动画透明度单独传入，不能再乘一次背景透明度或用实体斜面打底。实体控件使用 `surface` 或 `HudStyleRenderer` 对应方法，显式传入动画透明度与主题色。判断是否启用皮肤使用 `HudDraw.isStyled()`；`isOre()` 仅用于 ORE 专属的兼容调色。物品和媒体继续使用原有渲染路径。

Moderna 的素材已合并为一张 128×128 的透明图集，八片边框绘制不覆盖中心；Cyberpunk 的透明切角面由互不重叠的区域组成，避免叠加后背景变实。两种风格的装饰限制在原控件边界内，按钮按下仅移动表面和文字，不移动命中区域。

Wyrmheart 使用一张 256×256 的透明图集。边条与角饰只绘制在控件边界内，玻璃背景只填充一次；输入框采用八片边框，按钮采用固定端帽与可伸展中段，避免文字和端部装饰被横向拉长。圆形徽章中心使用实时标记颜色。原始 800×600 PSD 与作者预览不进入运行 JAR。

## 验证

```powershell
.\gradlew.bat test runData --offline
.\gradlew.bat runClient -PhudStyleRuntimeAudit --offline
```

原生验收在独立客户端的标题画面执行，无需打开世界。它通过实际配置按钮切换样式，读取保存后的 TOML，检查重置，比较恢复默认后的配置页像素以及默认基础图元与原生绘制的像素。两档 GUI 缩放下捕获控件、追踪器、任务日志、图鉴、教程、商店、快捷商店、对话和抽奖的实际游戏截图。额外的 `materials` 与 `glass-hud` 截图使用明暗相间的背景：逐像素比较完整、半透明和完全消失三个动画阶段的背景混合结果，并检查四种主题色在卡片、次级按钮、输入框与进度条上的实际像素。

截图目录：`run/screenshots/hud-style/`。日志标记：`[ARCQ_HUD_STYLE_AUDIT] PASS`。测试夹具仅位于 `gameTest` source set，不进入发布 JAR。

新增风格还包含 `geometry` 原生检查：1 像素、小尺寸与常规控件的边界，标记和方向箭头，以及零透明度、负透明度和 NaN 时不得残留可见像素。

参考版本为 [katorlys/OreUI](https://github.com/katorlys/OreUI) 的 `dd210df8f9c792c86e032d86a4a25e65be915cbc`。调色表的 MPL-2.0 声明、许可证和来源信息位于 `OrePalette.java` 与 `META-INF/licenses/oreui/`；其余绘制为适配 Minecraft 原生 GUI 的实现。

Moderna 美术作者为 **Jorge Avila**，按 **CC BY 3.0** 使用，授权全文、来源与修改说明随 JAR 放在 `META-INF/licenses/moderna/`。图集由原始 PSD 提取、裁剪、缩放、提亮金属与去色水晶，保留透明通道；`tools/extract_moderna.py` 可从原始 PSD 重建，不是游戏运行时依赖。

Cyberpunk CSS 作者为 **github/alddesign**，参考版本 `v1.0.0-alpha.3`，**MIT** 许可和来源说明位于 `META-INF/licenses/cyberpunk-css/`。未分发网站字体、演示图片或品牌资源。两份来源文件的 SHA-256 也记录在对应 NOTICE 中。

RPG Game UI 美术作者为 **Wyrmheart**，按 **CC0 1.0** 使用，可免费用于个人和商业项目。授权全文、来源与修改说明随 JAR 放在 `META-INF/licenses/wyrmheart/`。`tools/extract_wyrmheart.py` 从 `art_sources/hud/wyrmheart-rpg-game-ui/interface.psd` 校验原始 SHA-256 后提取、缩放、提亮金属和去色按钮，保留透明通道；Pillow 和 psd-tools 仅供素材重建使用。
