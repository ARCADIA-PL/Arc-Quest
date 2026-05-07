# Arc Quest Modular Editor Structure

## 目录职责

- `index.html`：入口页面
- `styles/editor.css`：统一样式
- `scripts/core/`：状态、工具、工厂、校验、DOM
- `scripts/renderers/`：树、右栏、状态、事件绑定、中间调度
- `scripts/editors/`：Quest / Visual / Phase / Objective / Reward / Raw 各编辑器

## 当前特点

- 保留单文件版 `arc_quest_datapack_editor.html`
- 新增模块化版 `arc_quest_editor_modular/index.html`
- 后续建议只在模块化版上继续开发
