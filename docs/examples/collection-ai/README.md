# ArcQ 外部 AI 图鉴任务双语范例

使用当前 ArcQ Forge 1.20.1／NeoForge 1.21.1；客户端与服务端需同步更新到网络协议 21。完整规则、注册窗口和验收流程见 [作者手册](../../compendium-ai-authoring-guide.md)。

## 安装任务

选择一种入口，两者使用相同 Quest／Entry ID，不要同时注册：

- JSON：复制 [zombie_survey.json](zombie_survey.json) 至 `<游戏实例或服务端工作目录>/arc_quest/datapack/quests/ai_codex/zombie_survey.json`，重载后使用 `/arcquest quest give @s ai_codex:zombie_survey`。
- Java：复制 [ExternalAiCodexExample.java](ExternalAiCodexExample.java) 至附属模组对应包目录，在 ArcQ 的 `ArcQuestRegistrationEvent.Quest` 注册窗口调用 `register()`；保留该类的不可变版本工厂。

现代图鉴 JSON 的可玩入口使用 ArcQ 外置内容目录；普通存档数据包的限制见作者手册 §3.1。

## 安装客户端语言资源

任务示例中的可见文字全部是翻译键，必须同时提供客户端资源。把 [resource-pack](resource-pack) 目录完整复制为 `<游戏实例>/resourcepacks/arcq-ai-codex-example/` 并在资源包设置启用，目录结构必须是：

```text
resourcepacks/arcq-ai-codex-example/
├─ pack.mcmeta
└─ assets/ai_codex/lang/
   ├─ zh_cn.json
   └─ en_us.json
```

该资源包的 `pack_format` 为 15，并声明支持格式 15～34，覆盖 Minecraft 1.20.1 与 1.21.1。若打入附属模组，复制 `assets/ai_codex/lang` 到该模组的 `src/main/resources/assets/ai_codex/lang`，不需要再安装独立资源包。语言资源由每名玩家客户端加载；单独放在服务器的外置任务目录不会自动分发。

[中文资源](resource-pack/assets/ai_codex/lang/zh_cn.json) 与 [英文资源](resource-pack/assets/ai_codex/lang/en_us.json) 使用相同键集合。切换游戏语言检查任务／阶段名称描述、分类、条目、发现与调查目标、成果、正文和图片说明。图片复用 ArcQ 内置 `field_notes.png`，不需要复制额外贴图。

新增文本用 `QuestText.translatable(...)` 或 JSON `mode: translatable`，同时补齐两份语言文件；不要在服务端调用 `getString()` 后把中文传给玩家。Java 的 String 重载仍是原样文本，键名必须显式包装为 `QuestText`。复杂组件支持 JSON `mode: component`；写法见作者手册 §7.5。
