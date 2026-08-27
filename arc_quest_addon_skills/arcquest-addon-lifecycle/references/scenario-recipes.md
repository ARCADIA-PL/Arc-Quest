# 生命周期需求实现配方

## 需求：大型附属按模块汇总注册

### 设计判断

注册事件决定 Registry 所有权和冻结顺序。章节可以按业务拆分，但 Quest、Dialogue、NPC、Trade、Gacha、Guide 必须回到各自事件，不要把所有内容塞进 Quest listener。

### ArcQ 实现路径

```text
MOD 构造器
  -> 注册 ArcQuestRegistrationEvent.Quest listener
  -> 注册 ArcQuestRegistrationEvent.Dialogue listener
  -> 注册 ArcQuestRegistrationEvent.Npc listener
  -> 注册 ArcQuestRegistrationEvent.Trade listener
  -> 注册 ArcQuestRegistrationEvent.Gacha listener
  -> 注册 ArcQuestRegistrationEvent.Guide listener
```

完整入口：[ExampleArcQuestAddon.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ExampleArcQuestAddon.java)。它保持 listener 很薄，每个业务类只暴露对应 `register` 方法。

### 常见错误

- 把 registration event 放在默认 Forge bus，导致从不触发；
- 在静态初始化块直接调用 `ArcQuestAPI.register*`；
- 捕获 event 对象并在服务器启动后使用；
- 一个 Quest Setup 越权注册 Dialogue，隐式依赖当前 freeze 顺序；
- 附属自己调用 `freeze()` 或 `clearAll()`。

## 需求：统一所有任务的默认配置

### 设计判断

当任务数量很大时，重复主题色、分类、音效和 fallback 会导致漂移。适合建立返回原始 `QuestBuilder` 的薄门面，不适合自建一套不可互操作 Builder。

### 实现配方

```text
AddonQuestBuilders.create(questId)
  -> QuestBuilder.create(questId)
  -> category/default visuals/abandonable
  -> return QuestBuilder
```

调用方继续 `.phase(...)`、`.completionPolicy(...)` 和 `.build()`。门面不读取玩家、不注册 Definition、不保存可变全局状态。

## 需求：可选集成其他模组

### 设计判断

若 ArcQ API 类型直接出现在附属 common 类签名中，ArcQ 就是硬依赖。只有通过独立兼容类、运行时存在性检查且 JVM 不会提前验证目标类型时，才可能做软依赖。

### 实现配方

- ArcQ 为核心功能：`mods.toml` 声明 `mandatory=true`，直接注册；
- ArcQ 为可选功能：把所有 ArcQ import 放进独立兼容包，通过 `ModList#get().isLoaded("arc_quest")` 后再触发加载；
- 客户端可选联动：兼容类还要使用 `Dist.CLIENT` 隔离；
- 不用 `try/catch ClassNotFoundException` 包住已经出现在方法签名中的 ArcQ 类型。

## 需求：客户端表现扩展但专服可加载

### ArcQ 实现路径

```text
common MOD class
  -> 不 import net.minecraft.client
client subscriber (Dist.CLIENT, MOD bus)
  -> FMLClientSetupEvent#enqueueWork
  -> 注册 DialogueOverlay/Marker presentation/Journal tab
```

参考 [ExampleClientExtensions.java](../../arcquest-addon-development/assets/example-addon/src/main/java/com/example/arcqaddon/ExampleClientExtensions.java)。

## 需求：注册失败时给出可定位错误

注册期属于启动阶段，重复 ID、引用缺失和非法 Builder 参数应快速失败。日志至少带模块、Definition ID 和注册事件类型；不要捕获异常后继续 freeze，因为客户端同步会得到不完整 Registry。

## 验证清单

- Forge `47.4.20`、Java 17、ArcQ 依赖版本一致；
- MOD bus 与 Forge bus 未混用；
- 六类 Definition 在对应事件注册；
- common 类无客户端类型；
- 所有 ID 使用附属 namespace；
- `compileJava` 后启动专服检查 Registry freeze；
- 启动客户端检查定义同步和客户端扩展只注册一次。
